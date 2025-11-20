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
import javafx.stage.StageStyle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class MainController {

    private static final String OUTPUT_DIR_NAME = "_ConvertedToTxt";
    private final Map<String, String> presets = new LinkedHashMap<>();
    private final Map<String, String> ignoredFolderPresets = new LinkedHashMap<>();

    private Stage stage;
    private double xOffset = 0;
    private double yOffset = 0;

    // Данные состояния
    private List<Path> allFoundFiles = new ArrayList<>();
    private Set<Path> filesSelectedForMerge = new HashSet<>();

    @FXML private TextField sourceDirField;
    @FXML private Button selectSourceBtn;
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

    // Новые элементы UI
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;

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

    // --- UI SETUP & PRESETS ---

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

    private void setupWindowDrag() {
        titleBar.setOnMousePressed(event -> { xOffset = event.getSceneX(); yOffset = event.getSceneY(); });
        titleBar.setOnMouseDragged(event -> { stage.setX(event.getScreenX() - xOffset); stage.setY(event.getScreenY() - yOffset); });
    }

    // --- EVENT HANDLERS ---

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

    @FXML private void handleRescan() {
        String sourceDirPath = sourceDirField.getText();
        if (sourceDirPath == null || sourceDirPath.isBlank()) {
            log("Ошибка: Папка не выбрана.");
            return;
        }

        setUiBlocked(true);
        log("Запуск сканирования...");

        FileScannerTask scannerTask = new FileScannerTask(
                sourceDirPath,
                OUTPUT_DIR_NAME,
                getExtensions(),
                getIgnoredFolders()
        );

        // Привязка UI к задаче
        statusLabel.textProperty().bind(scannerTask.messageProperty());

        scannerTask.setOnSucceeded(e -> {
            allFoundFiles = scannerTask.getValue();
            // По умолчанию выбираем все файлы для слияния
            filesSelectedForMerge = new HashSet<>(allFoundFiles);

            // Обновляем UI: показываем пользователю новое имя файла с нижним подчеркиванием
            String projectName = Paths.get(sourceDirPath).getFileName().toString();
            String outputFileName = "_" + projectName + "_Full_Source_code.txt";
            generateMergedFileCheckbox.setText("Создавать единый файл (" + outputFileName + ")");

            log("Сканирование завершено. Найдено файлов: " + allFoundFiles.size());
            setUiBlocked(false);
            statusLabel.textProperty().unbind();
            statusLabel.setText("Ожидание действий");
            updateButtonStates();
        });

        scannerTask.setOnFailed(e -> {
            log("ОШИБКА СКАНИРОВАНИЯ: " + scannerTask.getException().getMessage());
            setUiBlocked(false);
            statusLabel.textProperty().unbind();
            statusLabel.setText("Ошибка");
        });

        new Thread(scannerTask).start();
    }

    @FXML private void handleSelectFiles() {
        if (allFoundFiles.isEmpty()) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("selection-dialog.fxml"));
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Выбор файлов");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(stage);
            dialogStage.initStyle(StageStyle.UNDECORATED);

            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
            dialogStage.setScene(scene);

            SelectionController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.initData(allFoundFiles, filesSelectedForMerge, Paths.get(sourceDirField.getText()));

            dialogStage.showAndWait();

            controller.getSelectedFiles().ifPresent(selected -> {
                this.filesSelectedForMerge = selected;
                log("Выбрано для отчета: " + selected.size() + " из " + allFoundFiles.size());
            });

        } catch (IOException e) {
            log("КРИТИЧЕСКАЯ ОШИБКА UI: " + e.getMessage());
        }
    }

    @FXML private void handleConvert() {
        if (allFoundFiles.isEmpty()) {
            log("Нет файлов для обработки.");
            return;
        }

        setUiBlocked(true);
        logArea.clear();
        log("Начало конвертации...");

        ConverterTask converterTask = new ConverterTask(
                sourceDirField.getText(),
                OUTPUT_DIR_NAME,
                allFoundFiles,
                filesSelectedForMerge,
                generateStructureFileCheckbox.isSelected(),
                generateMergedFileCheckbox.isSelected()
        );

        // Привязка прогресса
        progressBar.progressProperty().bind(converterTask.progressProperty());
        statusLabel.textProperty().bind(converterTask.messageProperty());

        converterTask.setOnSucceeded(e -> {
            log("\n====================\nКОНВЕРТАЦИЯ УСПЕШНА\n====================");
            log("Результат в папке: " + Paths.get(sourceDirField.getText(), OUTPUT_DIR_NAME));
            setUiBlocked(false);
            progressBar.progressProperty().unbind();
            progressBar.setProgress(1.0);
            statusLabel.textProperty().unbind();
            statusLabel.setText("Готово");
        });

        converterTask.setOnFailed(e -> {
            log("КРИТИЧЕСКАЯ ОШИБКА КОНВЕРТАЦИИ: " + converterTask.getException().getMessage());
            converterTask.getException().printStackTrace();
            setUiBlocked(false);
            progressBar.progressProperty().unbind();
            progressBar.setProgress(0.0);
            statusLabel.textProperty().unbind();
            statusLabel.setText("Ошибка");
        });

        new Thread(converterTask).start();
    }

    // --- HELPERS ---

    private void setUiBlocked(boolean blocked) {
        rescanBtn.setDisable(blocked);
        selectFilesBtn.setDisable(blocked);
        convertBtn.setDisable(blocked);
        selectSourceBtn.setDisable(blocked);
        presetComboBox.setDisable(blocked);
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

    @FXML private void handleMinimize() { if (stage != null) stage.setIconified(true); }
    @FXML private void handleClose() { Platform.exit(); }

    private void log(String message) {
        Platform.runLater(() -> logArea.appendText(message + "\n"));
    }

    private Stage getStage() { return (stage != null) ? stage : (Stage) sourceDirField.getScene().getWindow(); }
}