package TartarusCore.TxtConverter;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox; // <<< НОВЫЙ ИМПОРТ
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.stream.Collectors;

public class MainController {

    private static final String OUTPUT_DIR_NAME = "_ConvertedToTxt";
    private final Map<String, String> presets = new LinkedHashMap<>();

    // >>> НОВЫЕ ПЕРЕМЕННЫЕ ДЛЯ УПРАВЛЕНИЯ ОКНОМ <<<
    private Stage stage;
    private double xOffset = 0;
    private double yOffset = 0;

    // Элементы интерфейса
    @FXML private TextField sourceDirField;
    @FXML private TextField extensionsField;
    @FXML private ComboBox<String> presetComboBox;
    @FXML private TextArea logArea;
    @FXML private HBox titleBar; // <<< Связь с нашей панелью-заголовком

    // Передаем Stage из главного класса
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void initialize() {
        setupPresets();
        setupPresetListener();
        setupWindowDrag(); // <<< Включаем перетаскивание окна
        log("Приложение готово к работе.");
    }

    // >>> НОВЫЙ МЕТОД: Настройка перетаскивания окна <<<
    private void setupWindowDrag() {
        titleBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        titleBar.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });
    }

    // >>> НОВЫЕ МЕТОДЫ: Обработчики кнопок окна <<<
    @FXML
    private void handleMinimize() {
        if (stage != null) {
            stage.setIconified(true);
        }
    }

    @FXML
    private void handleClose() {
        Platform.exit();
    }

    private void setupPresets() {
        presets.put("Вручную", "");
        presets.put("Godot Engine", "gd, tscn, tres, gdshader, godot");
        presets.put("Java (Maven/Gradle)", "java, xml, properties, fxml, gradle, groovy");
        presets.put("Web Frontend", "html, css, js, ts, scss, json");

        presetComboBox.getItems().addAll(presets.keySet());
        presetComboBox.getSelectionModel().select("Godot Engine");
    }

    private void setupPresetListener() {
        presetComboBox.getSelectionModel().selectedItemProperty().addListener((options, oldValue, newValue) -> {
            if (newValue != null && !newValue.equals("Вручную")) {
                extensionsField.setText(presets.get(newValue));
                log("Выбран пресет '" + newValue + "'. Расширения установлены.");
            }
        });
        extensionsField.setText(presets.get(presetComboBox.getSelectionModel().getSelectedItem()));
    }

    @FXML
    private void handleSelectSource() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Выберите папку проекта");
        File selectedDirectory = directoryChooser.showDialog(getStage());

        if (selectedDirectory != null) {
            sourceDirField.setText(selectedDirectory.getAbsolutePath());
            log("Выбрана папка с исходниками: " + selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    private void handleConvert() {
        String sourceDirPath = sourceDirField.getText();
        if (sourceDirPath == null || sourceDirPath.isBlank()) {
            log("Ошибка: Не выбрана папка с исходниками!");
            return;
        }

        List<String> extensions = getExtensions();
        if (extensions.isEmpty()) {
            log("Ошибка: Не указаны расширения для конвертации!");
            return;
        }

        new Thread(() -> convertFilesInBackground(sourceDirPath, extensions)).start();
    }

    private void convertFilesInBackground(String sourceDirPath, List<String> extensions) {
        Platform.runLater(() -> logArea.clear());
        log("Начало конвертации...");
        Path sourcePath = Paths.get(sourceDirPath);
        Path outputPath = sourcePath.resolve(OUTPUT_DIR_NAME);

        try {
            prepareOutputDirectory(outputPath);
            try (var stream = Files.walk(sourcePath)) {
                List<Path> filesToConvert = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> !p.startsWith(outputPath))
                        .filter(p -> {
                            String fileName = p.getFileName().toString();
                            int dotIndex = fileName.lastIndexOf('.');
                            if (dotIndex > 0) {
                                String extension = fileName.substring(dotIndex + 1);
                                return extensions.contains(extension.toLowerCase());
                            }
                            return extensions.contains(fileName.toLowerCase());
                        })
                        .collect(Collectors.toList());

                if (filesToConvert.isEmpty()) {
                    log("Файлы с указанными расширениями не найдены.");
                    log("Проверьте список расширений и выбранную папку.");
                    return;
                }

                log("Найдено файлов для конвертации: " + filesToConvert.size());
                for (Path sourceFile : filesToConvert) {
                    Path destFile = outputPath.resolve(sourceFile.getFileName().toString() + ".txt");
                    Files.copy(sourceFile, destFile, StandardCopyOption.REPLACE_EXISTING);
                    log("Скопировано: " + sourceFile.getFileName() + " -> " + destFile.getFileName());
                }

                log("\n====================\nКОНВЕРТАЦИЯ ЗАВЕРШЕНА\n====================");
                log("Все файлы сохранены в папке: " + outputPath);

            }
        } catch (IOException e) {
            log("КРИТИЧЕСКАЯ ОШИБКА: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void prepareOutputDirectory(Path outputPath) throws IOException {
        if (Files.exists(outputPath)) {
            log("Очистка старой папки: " + outputPath);
            try (var stream = Files.walk(outputPath)) {
                stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
        Files.createDirectories(outputPath);
        log("Папка для результатов готова: " + outputPath);
    }

    private List<String> getExtensions() {
        String rawText = extensionsField.getText();
        if (rawText == null || rawText.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(rawText.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private void log(String message) {
        Platform.runLater(() -> logArea.appendText(message + "\n"));
    }

    // Метод getStage() теперь либо использует переданный stage, либо ищет его сам
    private Stage getStage() {
        if (stage != null) {
            return stage;
        }
        return (Stage) sourceDirField.getScene().getWindow();
    }
}