package TartarusCore.TxtConverter;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class MainController {

    private Stage stage;
    private double xOffset = 0;
    private double yOffset = 0;
    private List<Path> allFoundFiles = new ArrayList<>();
    private Set<Path> filesSelectedForMerge = new HashSet<>();

    // UI Elements
    @FXML private VBox rootBox;
    @FXML private Label lblTitle;
    @FXML private Label lblSourceDir;
    @FXML private Label lblPreset;
    @FXML private Label lblExtensions;
    @FXML private Label lblIgnored;
    @FXML private Label lblLog;
    @FXML private HBox titleBar;

    @FXML private TextField sourceDirField;
    @FXML private Button selectSourceBtn;
    @FXML private TextField extensionsField;
    @FXML private ComboBox<String> presetComboBox;
    @FXML private TextArea logArea;

    @FXML private CheckBox generateStructureFileCheckbox;
    @FXML private CheckBox compactStructureCheckbox;

    @FXML private Label lblCompression;
    @FXML private ComboBox<CompressionLevel> compressionComboBox;

    @FXML private TextField ignoredFoldersField;
    @FXML private CheckBox generateMergedFileCheckbox;

    @FXML private Button rescanBtn;
    @FXML private Button selectFilesBtn;
    @FXML private Button convertBtn;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;

    public void setStage(Stage stage) { this.stage = stage; }

    @FXML
    public void initialize() {
        setupPresetsUI();
        setupPresetListener();
        setupWindowDrag();
        setupCompressionCombo();
        setupDragAndDrop();

        compactStructureCheckbox.disableProperty().bind(generateStructureFileCheckbox.selectedProperty().not());

        loadPreferences();

        LanguageManager.getInstance().addListener(this::updateTexts);
        updateTexts();

        updateButtonStates();
        log(LanguageManager.getInstance().getString("log.app_ready"));
    }

    private void setupDragAndDrop() {
        if (rootBox == null) return;

        rootBox.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                List<File> files = event.getDragboard().getFiles();
                if (files.size() == 1 && files.get(0).isDirectory()) {
                    event.acceptTransferModes(TransferMode.COPY);
                }
            }
            event.consume();
        });

        rootBox.setOnDragDropped(event -> {
            boolean success = false;
            if (event.getDragboard().hasFiles()) {
                List<File> files = event.getDragboard().getFiles();
                if (files.size() == 1 && files.get(0).isDirectory()) {
                    File folder = files.get(0);
                    setSourceDirectory(folder);
                    success = true;
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void loadPreferences() {
        PreferenceManager prefs = PreferenceManager.getInstance();

        String lastDir = prefs.getString(ProjectConstants.PREF_LAST_DIR, "");
        if (!lastDir.isEmpty() && Files.exists(Paths.get(lastDir))) {
            sourceDirField.setText(lastDir);
        }

        String lastPreset = prefs.getString(ProjectConstants.PREF_LAST_PRESET, "Unity Engine");
        // Проверяем через менеджер, существует ли такой пресет (вдруг мы его удалили)
        if (PresetManager.getInstance().hasPreset(lastPreset)) {
            presetComboBox.getSelectionModel().select(lastPreset);
        } else {
            // Если сохраненного пресета нет в базе, выбираем первый доступный или Manual
            presetComboBox.getSelectionModel().selectFirst();
        }

        generateStructureFileCheckbox.setSelected(prefs.getBoolean(ProjectConstants.PREF_GEN_STRUCTURE, false));
        compactStructureCheckbox.setSelected(prefs.getBoolean(ProjectConstants.PREF_COMPACT_MODE, true));
        generateMergedFileCheckbox.setSelected(prefs.getBoolean(ProjectConstants.PREF_GEN_MERGED, true));
        compressionComboBox.setValue(prefs.getCompressionLevel());

        if (!sourceDirField.getText().isEmpty()) {
            updateMergedCheckboxText();
        }
    }

    private void savePreferences() {
        PreferenceManager prefs = PreferenceManager.getInstance();
        prefs.saveString(ProjectConstants.PREF_LAST_DIR, sourceDirField.getText());
        prefs.saveString(ProjectConstants.PREF_LAST_PRESET, presetComboBox.getValue());
        prefs.saveBoolean(ProjectConstants.PREF_GEN_STRUCTURE, generateStructureFileCheckbox.isSelected());
        prefs.saveBoolean(ProjectConstants.PREF_COMPACT_MODE, compactStructureCheckbox.isSelected());
        prefs.saveBoolean(ProjectConstants.PREF_GEN_MERGED, generateMergedFileCheckbox.isSelected());
        prefs.saveCompressionLevel(compressionComboBox.getValue());
    }

    private void setupCompressionCombo() {
        compressionComboBox.getItems().addAll(CompressionLevel.values());
        if (compressionComboBox.getValue() == null) {
            compressionComboBox.setValue(CompressionLevel.SMART);
        }

        compressionComboBox.setConverter(new StringConverter<CompressionLevel>() {
            @Override
            public String toString(CompressionLevel object) {
                if (object == null) return "";
                LanguageManager lm = LanguageManager.getInstance();
                switch (object) {
                    case NONE: return lm.getString("ui.comp_none");
                    case SMART: return lm.getString("ui.comp_smart");
                    case MAXIMUM: return lm.getString("ui.comp_max");
                    default: return object.name();
                }
            }
            @Override
            public CompressionLevel fromString(String string) { return null; }
        });
    }

    private void updateTexts() {
        LanguageManager lm = LanguageManager.getInstance();

        lblTitle.setText(lm.getString("app.title"));
        lblSourceDir.setText(lm.getString("ui.source_dir"));
        selectSourceBtn.setText(lm.getString("ui.choose_btn"));
        lblPreset.setText(lm.getString("ui.preset"));
        lblExtensions.setText(lm.getString("ui.extensions"));
        extensionsField.setPromptText(lm.getString("ui.extensions_prompt"));
        lblIgnored.setText(lm.getString("ui.ignored"));
        ignoredFoldersField.setPromptText(lm.getString("ui.ignored_prompt"));
        rescanBtn.setText(lm.getString("ui.rescan_btn"));

        String structFileName = ProjectConstants.REPORT_STRUCTURE_FILE;
        generateStructureFileCheckbox.setText(String.format(lm.getString("ui.structure_cb"), structFileName));
        compactStructureCheckbox.setText(lm.getString("ui.compact_structure_cb"));

        lblCompression.setText(lm.getString("ui.compression_label"));
        CompressionLevel current = compressionComboBox.getValue();
        compressionComboBox.setConverter(compressionComboBox.getConverter());
        compressionComboBox.setValue(current);

        updateMergedCheckboxText();

        selectFilesBtn.setText(lm.getString("ui.select_files_btn"));
        convertBtn.setText(lm.getString("ui.convert_btn"));
        lblLog.setText(lm.getString("ui.log_label"));

        if (!statusLabel.textProperty().isBound()) {
            statusLabel.setText(lm.getString("ui.status_ready"));
        }
    }

    private void updateMergedCheckboxText() {
        LanguageManager lm = LanguageManager.getInstance();
        String fileName;
        if (sourceDirField.getText() != null && !sourceDirField.getText().isBlank()) {
            String projectName = Paths.get(sourceDirField.getText()).getFileName().toString();
            fileName = "_" + projectName + ProjectConstants.MERGED_FILE_SUFFIX;
        } else {
            fileName = "_MergedOutput.txt";
        }
        generateMergedFileCheckbox.setText(String.format(lm.getString("ui.merged_cb"), fileName));
    }

    private void setupPresetsUI() {
        presetComboBox.getItems().addAll(PresetManager.getInstance().getPresetNames());
    }

    private void setupPresetListener() {
        presetComboBox.getSelectionModel().selectedItemProperty().addListener((options, oldValue, newValue) -> {
            if (newValue != null) {
                // Если пресет Manual, мы НЕ перезаписываем поля, чтобы не стереть пользовательский ввод
                boolean isManual = "Manual".equals(newValue);
                if (!isManual) {
                    PresetManager pm = PresetManager.getInstance();
                    extensionsField.setText(pm.getExtensionsFor(newValue));
                    ignoredFoldersField.setText(pm.getIgnoredFoldersFor(newValue));
                }

                String msg = String.format(LanguageManager.getInstance().getString("log.preset_selected"), newValue);
                log(msg);

                if (sourceDirField.getText() != null && !sourceDirField.getText().isBlank()) {
                    handleRescan();
                }
            }
        });
    }

    private void setupWindowDrag() {
        titleBar.setOnMousePressed(event -> { xOffset = event.getSceneX(); yOffset = event.getSceneY(); });
        titleBar.setOnMouseDragged(event -> { stage.setX(event.getScreenX() - xOffset); stage.setY(event.getScreenY() - yOffset); });
    }

    @FXML private void handleSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("settings-view.fxml"));
            Stage settingsStage = new Stage();
            settingsStage.initModality(Modality.APPLICATION_MODAL);
            settingsStage.initOwner(stage);
            settingsStage.initStyle(StageStyle.UNDECORATED);

            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
            settingsStage.setScene(scene);

            SettingsController controller = loader.getController();
            controller.setStage(settingsStage);

            settingsStage.showAndWait();
        } catch (IOException e) {
            log("UI ERROR: " + e.getMessage());
        }
    }

    @FXML private void handleSelectSource() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(LanguageManager.getInstance().getString("ui.source_dir"));

        String currentPath = sourceDirField.getText();
        if (currentPath != null && !currentPath.isEmpty()) {
            File initialDir = new File(currentPath);
            if (initialDir.exists() && initialDir.isDirectory()) {
                directoryChooser.setInitialDirectory(initialDir);
            }
        }

        File selectedDirectory = directoryChooser.showDialog(getStage());
        if (selectedDirectory != null) {
            setSourceDirectory(selectedDirectory);
        }
    }

    private void setSourceDirectory(File dir) {
        sourceDirField.setText(dir.getAbsolutePath());
        log(String.format(LanguageManager.getInstance().getString("log.dir_selected"), dir.getAbsolutePath()));

        // Попытка авто-определения пресета через Manager
        String detectedPreset = PresetManager.getInstance().autoDetectPreset(dir.toPath());
        if (detectedPreset != null) {
            log("🤖 Auto-detected project type: " + detectedPreset);
            presetComboBox.getSelectionModel().select(detectedPreset);
        } else {
            // Если не определили, просто запускаем сканирование с текущим пресетом
            handleRescan();
        }
    }

    @FXML private void handleRescan() {
        String sourceDirPath = sourceDirField.getText();
        if (sourceDirPath == null || sourceDirPath.isBlank()) {
            log(LanguageManager.getInstance().getString("log.error_no_dir"));
            return;
        }

        setUiBlocked(true);
        log(LanguageManager.getInstance().getString("log.scanning_start"));

        FileScannerTask scannerTask = new FileScannerTask(
                sourceDirPath,
                getExtensions(),
                getIgnoredFolders()
        );

        statusLabel.textProperty().bind(scannerTask.messageProperty());

        scannerTask.setOnSucceeded(e -> {
            allFoundFiles = scannerTask.getValue();
            filesSelectedForMerge = new HashSet<>(allFoundFiles);

            updateMergedCheckboxText();

            log(String.format(LanguageManager.getInstance().getString("log.scan_complete"), allFoundFiles.size()));
            setUiBlocked(false);
            statusLabel.textProperty().unbind();
            statusLabel.setText(LanguageManager.getInstance().getString("ui.status_waiting"));
            updateButtonStates();
        });

        scannerTask.setOnFailed(e -> {
            log(String.format(LanguageManager.getInstance().getString("log.scan_error"), scannerTask.getException().getMessage()));
            setUiBlocked(false);
            statusLabel.textProperty().unbind();
            statusLabel.setText(LanguageManager.getInstance().getString("ui.status_error"));
        });

        new Thread(scannerTask).start();
    }

    @FXML private void handleSelectFiles() {
        if (allFoundFiles.isEmpty()) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("selection-dialog.fxml"));
            Stage dialogStage = new Stage();
            dialogStage.setTitle(LanguageManager.getInstance().getString("ui.select_files_btn"));
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
                log(String.format(LanguageManager.getInstance().getString("log.files_selected"), selected.size(), allFoundFiles.size()));
            });

        } catch (IOException e) {
            log("UI ERROR: " + e.getMessage());
        }
    }

    @FXML private void handleConvert() {
        if (allFoundFiles.isEmpty()) {
            log(LanguageManager.getInstance().getString("log.no_files"));
            return;
        }

        setUiBlocked(true);
        logArea.clear();
        log(LanguageManager.getInstance().getString("log.conversion_start"));

        ConverterTask converterTask = new ConverterTask(
                sourceDirField.getText(),
                allFoundFiles,
                filesSelectedForMerge,
                getIgnoredFolders(),
                generateStructureFileCheckbox.isSelected(),
                compactStructureCheckbox.isSelected(),
                compressionComboBox.getValue(),
                generateMergedFileCheckbox.isSelected()
        );

        progressBar.progressProperty().bind(converterTask.progressProperty());
        statusLabel.textProperty().bind(converterTask.messageProperty());

        converterTask.setOnSucceeded(e -> {
            log("\n====================\n" + LanguageManager.getInstance().getString("log.conversion_success") + "\n====================");
            log(String.format(LanguageManager.getInstance().getString("log.result_path"), Paths.get(sourceDirField.getText(), ProjectConstants.OUTPUT_DIR_NAME)));
            setUiBlocked(false);
            progressBar.progressProperty().unbind();
            progressBar.setProgress(1.0);
            statusLabel.textProperty().unbind();
            statusLabel.setText(LanguageManager.getInstance().getString("ui.status_done"));
        });

        converterTask.setOnFailed(e -> {
            log(String.format(LanguageManager.getInstance().getString("log.conversion_error"), converterTask.getException().getMessage()));
            converterTask.getException().printStackTrace();
            setUiBlocked(false);
            progressBar.progressProperty().unbind();
            progressBar.setProgress(0.0);
            statusLabel.textProperty().unbind();
            statusLabel.setText(LanguageManager.getInstance().getString("ui.status_error"));
        });

        new Thread(converterTask).start();
    }

    private void setUiBlocked(boolean blocked) {
        rescanBtn.setDisable(blocked);
        selectFilesBtn.setDisable(blocked);
        convertBtn.setDisable(blocked);
        selectSourceBtn.setDisable(blocked);
        presetComboBox.setDisable(blocked);
        compressionComboBox.setDisable(blocked);
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

    @FXML private void handleClose() {
        savePreferences();
        Platform.exit();
    }

    private void log(String message) {
        Platform.runLater(() -> logArea.appendText(message + "\n"));
    }

    private Stage getStage() { return (stage != null) ? stage : (Stage) sourceDirField.getScene().getWindow(); }
}