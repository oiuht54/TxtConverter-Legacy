package TartarusCore.TxtConverter;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.util.Locale;

public class LanguageSelectorController {

    @FXML private Button btnEn;
    @FXML private Button btnRu;

    @FXML
    private void handleEn() {
        selectLanguage(Locale.ENGLISH);
    }

    @FXML
    private void handleRu() {
        selectLanguage(new Locale("ru"));
    }

    private void selectLanguage(Locale locale) {
        LanguageManager.getInstance().setLocale(locale);
        openMainWindow();
    }

    private void openMainWindow() {
        try {
            Stage currentStage = (Stage) btnEn.getScene().getWindow();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("main-view.fxml"));
            Parent root = loader.load();

            MainController controller = loader.getController();

            Stage mainStage = new Stage();
            controller.setStage(mainStage);

            // >>> ВАЖНО: Устанавливаем иконку для нового окна <<<
            TxtConverterApp.setAppIcon(mainStage);

            mainStage.initStyle(StageStyle.UNDECORATED);
            mainStage.setTitle("TXT Converter");
            mainStage.setMinWidth(600);
            mainStage.setMinHeight(500);

            Scene scene = new Scene(root, 600, 500);
            scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
            mainStage.setScene(scene);

            mainStage.show();

            if (currentStage != null) {
                currentStage.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}