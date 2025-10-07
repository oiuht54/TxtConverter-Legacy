package TartarusCore.TxtConverter;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle; // <<< ИМПОРТИРУЕМ StageStyle

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class MainController {

    private static final String OUTPUT_DIR_NAME = "_ConvertedToTxt";
    private final Map<String, String> presets = new LinkedHashMap<>();
    private final Map<String, String> ignoredFolderPresets = new LinkedHashMap<>();

    private Stage stage;
    private double xOffset = 0;
    private double yOffset = 0;

    private List<Path> allFoundFiles = new ArrayList<>();
    private Set<Path> filesSelectedForMerge = new HashSet<>();

    @FXML private TextField sourceDirField;
    @FXML private TextField extensionsField;
    @FXML private ComboBox<String> presetComboBox;
    @FXML private TextArea logArea;
    @FXML private HBox titleBar;
    @FXML private CheckBox generateStructureFileCheckbox;
    @FXML private TextField ignoredFoldersField;
    @FXML private CheckBox generateMergedFileCheckbox;
    @FXML private Button rescanBtn;
    @FXML private Button selectFilesBtn;
    @FXML private Button convertBtn;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void initialize() {
        setupPresets();
        setupPresetListener();
        setupWindowDrag();

        generateMergedFileCheckbox.setSelected(true);
        generateStructureFileCheckbox.setSelected(false);
        updateButtonStates();

        log("Приложение готово к работе.");
    }

    private void setupPresets() {
        presets.put("Вручную", "");
        presets.put("Godot Engine", "gd, tscn, tres, gdshader, godot");
        presets.put("Unity Engine", "cs, shader, cginc, txt, json, xml, asmdef, asset, inputactions");
        presets.put("Java (Maven/Gradle)", "java, xml, properties, fxml, gradle, groovy");
        presets.put("Web Frontend", "html, css, js, ts, scss, json");

        ignoredFolderPresets.put("Вручную", "");
        ignoredFolderPresets.put("Godot Engine", ".godot, export_presets");
        ignoredFolderPresets.put("Unity Engine", "Library, Temp, obj, bin, ProjectSettings, Logs, UserSettings");
        ignoredFolderPresets.put("Java (Maven/Gradle)", "target, .idea, build");
        ignoredFolderPresets.put("Web Frontend", "node_modules, dist, build");

        presetComboBox.getItems().addAll(presets.keySet());
        presetComboBox.getSelectionModel().select("Unity Engine");
    }

    private void setupPresetListener() {
        presetComboBox.getSelectionModel().selectedItemProperty().addListener((options, oldValue, newValue) -> {
            if (newValue != null) {
                extensionsField.setText(presets.get(newValue));
                ignoredFoldersField.setText(ignoredFolderPresets.get(newValue));
                log("Выбран пресет '" + newValue + "'. Настройки обновлены.");
                if (sourceDirField.getText() != null && !sourceDirField.getText().isBlank()) {
                    handleRescan();
                }
            }
        });
        String defaultPreset = presetComboBox.getSelectionModel().getSelectedItem();
        extensionsField.setText(presets.get(defaultPreset));
        ignoredFoldersField.setText(ignoredFolderPresets.get(defaultPreset));
    }

    @FXML
    private void handleSelectFiles() {
        if (allFoundFiles.isEmpty()) {
            log("Сначала необходимо просканировать проект. Измените настройки и нажмите 'Пересканировать'.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("selection-dialog.fxml"));
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Выбор файлов для _MergedOutput.txt");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(stage);

            // >>> ИЗМЕНЕНИЕ: Применяем кастомный стиль окна <<<
            dialogStage.initStyle(StageStyle.UNDECORATED);

            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
            dialogStage.setScene(scene);

            SelectionController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            // Передаем относительный путь для более чистого отображения
            controller.initData(allFoundFiles, filesSelectedForMerge, Paths.get(sourceDirField.getText()));

            dialogStage.showAndWait();

            controller.getSelectedFiles().ifPresent(selected -> {
                this.filesSelectedForMerge = selected;
                log("Список файлов для единого отчета обновлен. Выбрано: " + selected.size() + " из " + allFoundFiles.size());
            });

        } catch (IOException e) {
            log("КРИТИЧЕСКАЯ ОШИБКА: Не удалось открыть окно выбора файлов.");
            e.printStackTrace();
        }
    }

    // Остальной код MainController.java остается без изменений
    private void scanProject() {
        String sourceDirPath = sourceDirField.getText();
        if (sourceDirPath == null || sourceDirPath.isBlank()) {
            log("Ошибка: Сначала выберите папку с исходниками!");
            return;
        }

        Platform.runLater(() -> {
            log("Сканирование проекта...");
            updateButtonStates();
        });

        List<String> extensions = getExtensions();
        List<String> ignoredFolders = getIgnoredFolders();
        Path sourcePath = Paths.get(sourceDirPath);
        Path outputPath = sourcePath.resolve(OUTPUT_DIR_NAME);

        try (var stream = Files.walk(sourcePath)) {
            List<Path> foundFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.startsWith(outputPath))
                    .filter(path -> {
                        if (ignoredFolders.isEmpty()) return true;
                        for (Path part : sourcePath.relativize(path)) {
                            if (ignoredFolders.contains(part.toString().toLowerCase())) return false;
                        }
                        return true;
                    })
                    .filter(p -> {
                        String fileName = p.getFileName().toString().toLowerCase();
                        if (fileName.endsWith(".md")) return true;
                        int dotIndex = fileName.lastIndexOf('.');
                        if (dotIndex > 0) {
                            String extension = fileName.substring(dotIndex + 1);
                            return extensions.contains(extension);
                        }
                        return extensions.contains(fileName);
                    })
                    .sorted()
                    .collect(Collectors.toList());

            Platform.runLater(() -> {
                allFoundFiles = foundFiles;
                filesSelectedForMerge = new HashSet<>(allFoundFiles);
                log("Сканирование завершено. Найдено файлов: " + allFoundFiles.size());
                updateButtonStates();
            });

        } catch (IOException e) {
            log("ОШИБКА СКАНИРОВАНИЯ: " + e.getMessage());
        }
    }

    @FXML private void handleRescan() { new Thread(this::scanProject).start(); }
    @FXML private void handleConvert() {
        if (allFoundFiles.isEmpty()) {
            log("Ошибка: Файлы для конвертации не найдены. Проверьте путь к папке и настройки.");
            return;
        }
        boolean generateStructureFile = generateStructureFileCheckbox.isSelected();
        boolean generateMergedFile = generateMergedFileCheckbox.isSelected();
        new Thread(() -> convertFilesInBackground(sourceDirField.getText(), allFoundFiles, filesSelectedForMerge, generateStructureFile, generateMergedFile)).start();
    }
    private void convertFilesInBackground(String sourceDirPath, List<Path> filesToProcess, Set<Path> selectedForMerge, boolean generateStructureFile, boolean generateMergedFile) {
        Platform.runLater(() -> logArea.clear());
        log("Начало конвертации...");
        log("Всего файлов для обработки: " + filesToProcess.size());
        if (generateMergedFile) log("Файлов для включения в единый отчет: " + selectedForMerge.size());
        Path sourcePath = Paths.get(sourceDirPath);
        Path outputPath = sourcePath.resolve(OUTPUT_DIR_NAME);
        Map<Path, Path> processedFilesMap = new LinkedHashMap<>();
        try {
            prepareOutputDirectory(outputPath);
            for (Path sourceFile : filesToProcess) {
                String sourceFileName = sourceFile.getFileName().toString();
                String destFileName = sourceFileName.toLowerCase().endsWith(".md") ? sourceFileName : sourceFileName + ".txt";
                Path destFile = outputPath.resolve(destFileName);
                Files.copy(sourceFile, destFile, StandardCopyOption.REPLACE_EXISTING);
                log("Скопировано: " + sourcePath.relativize(sourceFile));
                processedFilesMap.put(sourceFile, destFile);
            }
            if (generateStructureFile && !filesToProcess.isEmpty()) {
                List<Path> relativePaths = filesToProcess.stream().map(sourcePath::relativize).collect(Collectors.toList());
                generateStructureReport(outputPath, sourcePath.getFileName().toString(), relativePaths);
            }
            if (generateMergedFile && !processedFilesMap.isEmpty()) {
                generateMergedFile(outputPath, processedFilesMap, selectedForMerge);
            }
            log("\n====================\nКОНВЕРТАЦИЯ ЗАВЕРШЕНА\n====================");
            log("Все файлы сохранены в папке: " + outputPath);
        } catch (IOException e) {
            log("КРИТИЧЕСКАЯ ОШИБКА: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private void generateMergedFile(Path outputPath, Map<Path, Path> processedFilesMap, Set<Path> selectedForMerge) throws IOException {
        log("Создание единого файла...");
        Path mergedFile = outputPath.resolve("_MergedOutput.txt");
        StringBuilder mergedContent = new StringBuilder();
        mergedContent.append("Единый файл с полным кодом необходимого проекта, сгенерированный TxtConverter\n");
        mergedContent.append("Дата генерации: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
        for (Map.Entry<Path, Path> entry : processedFilesMap.entrySet()) {
            Path originalPath = entry.getKey();
            Path destinationPath = entry.getValue();
            mergedContent.append("======================================================================\n");
            mergedContent.append("--- FILE: ").append(originalPath.getFileName()).append(" ---\n");
            mergedContent.append("======================================================================\n\n");
            if (selectedForMerge.contains(originalPath)) {
                try {
                    String content = Files.readString(destinationPath, StandardCharsets.UTF_8);
                    mergedContent.append(content).append("\n\n");
                } catch (IOException e) {
                    mergedContent.append("!!! ОШИБКА ЧТЕНИЯ ФАЙЛА: ").append(e.getMessage()).append(" !!!\n\n");
                }
            } else {
                mergedContent.append("(Содержимое файла опущено для краткости. При необходимости ИИ-ассистент обязан запросить его полный код)\n\n");
            }
        }
        Files.writeString(mergedFile, mergedContent.toString(), StandardCharsets.UTF_8);
        log("Единый файл успешно создан: " + mergedFile.getFileName());
    }
    @FXML private void handleSelectSource() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Выберите папку проекта");
        File selectedDirectory = directoryChooser.showDialog(getStage());
        if (selectedDirectory != null) {
            sourceDirField.setText(selectedDirectory.getAbsolutePath());
            log("Выбрана папка с исходниками: " + selectedDirectory.getAbsolutePath());
            handleRescan();
        }
    }
    private void updateButtonStates() {
        boolean isScanned = !allFoundFiles.isEmpty();
        boolean isDirSelected = sourceDirField.getText() != null && !sourceDirField.getText().isBlank();
        rescanBtn.setDisable(!isDirSelected);
        selectFilesBtn.setDisable(!isScanned);
        convertBtn.setDisable(!isScanned);
    }
    private List<String> getIgnoredFolders() {
        String rawText = ignoredFoldersField.getText();
        if (rawText == null || rawText.isBlank()) return Collections.emptyList();
        return Arrays.stream(rawText.split(",")).map(String::trim).map(String::toLowerCase).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }
    private List<String> getExtensions() {
        String rawText = extensionsField.getText();
        if (rawText == null || rawText.isBlank()) return Collections.emptyList();
        return Arrays.stream(rawText.split(",")).map(String::trim).map(String::toLowerCase).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }
    private void setupWindowDrag() {
        titleBar.setOnMousePressed(event -> { xOffset = event.getSceneX(); yOffset = event.getSceneY(); });
        titleBar.setOnMouseDragged(event -> { stage.setX(event.getScreenX() - xOffset); stage.setY(event.getScreenY() - yOffset); });
    }
    @FXML private void handleMinimize() { if (stage != null) stage.setIconified(true); }
    @FXML private void handleClose() { Platform.exit(); }
    private void log(String message) { Platform.runLater(() -> logArea.appendText(message + "\n")); }
    private Stage getStage() { return (stage != null) ? stage : (Stage) sourceDirField.getScene().getWindow(); }
    private void prepareOutputDirectory(Path outputPath) throws IOException {
        if (Files.exists(outputPath)) {
            try (var stream = Files.walk(outputPath)) { stream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete); }
        }
        Files.createDirectories(outputPath);
    }
    private void generateStructureReport(Path outputPath, String rootDirName, List<Path> relativePaths) throws IOException {
        log("Создание файла структуры...");
        Path reportFile = outputPath.resolve("_FileStructure.md");
        StringBuilder reportContent = new StringBuilder("# Структура скопированных файлов\n\n");
        reportContent.append("```\n").append(rootDirName).append("\n");
        for (Path path : relativePaths) {
            StringBuilder prefix = new StringBuilder();
            if (path.getParent() != null) { for (int i = 0; i < path.getNameCount() - 1; i++) prefix.append("│   "); }
            reportContent.append(prefix).append("├── ").append(path.getFileName()).append("\n");
        }
        reportContent.append("```\n");
        Files.writeString(reportFile, reportContent.toString(), StandardCharsets.UTF_8);
        log("Файл структуры успешно создан: " + reportFile.getFileName());
    }
}