package TartarusCore.TxtConverter;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.util.Objects;

public class TxtConverterApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        // Устанавливаем иконку приложения
        setAppIcon(stage);

        if (LanguageManager.getInstance().isLanguageSelected()) {
            openMainView(stage);
        } else {
            openLanguageSelector(stage);
        }
    }

    // Выносим установку иконки в отдельный метод, чтобы избежать дублирования
    public static void setAppIcon(Stage stage) {
        try {
            // Загружаем иконку из ресурсов
            Image icon = new Image(Objects.requireNonNull(TxtConverterApp.class.getResourceAsStream("icon.png")));
            stage.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("Warning: Could not load application icon.");
        }
    }

    private void openMainView(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 600, 500);
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());

        stage.initStyle(StageStyle.UNDECORATED);
        MainController controller = fxmlLoader.getController();
        controller.setStage(stage);

        stage.setTitle("TXT File Converter");
        stage.setMinWidth(600);
        stage.setMinHeight(500);
        stage.setScene(scene);
        stage.show();
    }

    private void openLanguageSelector(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("language-selector.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 400, 280);
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());

        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle("Select Language");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}