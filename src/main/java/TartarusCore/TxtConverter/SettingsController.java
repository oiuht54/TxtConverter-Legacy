package TartarusCore.TxtConverter;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.Locale;

public class SettingsController {

    @FXML private HBox titleBar;
    @FXML private Label lblTitle;
    @FXML private Label lblLanguage;
    @FXML private ComboBox<Locale> languageCombo;
    @FXML private Button btnClose;

    private Stage stage;
    private double xOffset = 0;
    private double yOffset = 0;

    @FXML
    public void initialize() {
        setupWindowDrag();
        setupLanguageCombo();
        updateTexts(); // Первичная установка текстов

        // Подписываемся на изменения языка, чтобы окно настроек тоже переводилось мгновенно
        LanguageManager.getInstance().addListener(this::updateTexts);
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    private void setupLanguageCombo() {
        languageCombo.getItems().addAll(Locale.ENGLISH, new Locale("ru"));

        // Конвертер для красивого отображения в списке
        languageCombo.setConverter(new StringConverter<Locale>() {
            @Override
            public String toString(Locale object) {
                if (object == null) return "";
                return object.getLanguage().equals("ru") ? "Русский" : "English";
            }

            @Override
            public Locale fromString(String string) {
                return null; // Не используется
            }
        });

        // Устанавливаем текущее значение
        languageCombo.setValue(LanguageManager.getInstance().getCurrentLocale());

        // Обработка выбора
        languageCombo.setOnAction(e -> {
            Locale selected = languageCombo.getSelectionModel().getSelectedItem();
            if (selected != null && !selected.equals(LanguageManager.getInstance().getCurrentLocale())) {
                LanguageManager.getInstance().setLocale(selected);
            }
        });
    }

    private void updateTexts() {
        LanguageManager lm = LanguageManager.getInstance();
        lblTitle.setText(lm.getString("ui.settings"));
        // lblLanguage оставляем двуязычным для понятности
        btnClose.setText("OK");
    }

    private void setupWindowDrag() {
        titleBar.setOnMousePressed(event -> { xOffset = event.getSceneX(); yOffset = event.getSceneY(); });
        titleBar.setOnMouseDragged(event -> { stage.setX(event.getScreenX() - xOffset); stage.setY(event.getScreenY() - yOffset); });
    }

    @FXML
    private void handleClose() {
        stage.close();
    }
}