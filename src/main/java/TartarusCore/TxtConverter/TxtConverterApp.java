package TartarusCore.TxtConverter;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.net.URL;

public class TxtConverterApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        URL fxmlLocation = getClass().getResource("main-view.fxml");
        if (fxmlLocation == null) {
            System.err.println("Критическая ошибка: FXML файл 'main-view.fxml' не найден.");
            System.err.println("Проверь, что он лежит в папке src/main/resources/TartarusCore/TxtConverter/");
            return;
        }

        FXMLLoader fxmlLoader = new FXMLLoader(fxmlLocation);
        Scene scene = new Scene(fxmlLoader.load(), 600, 500); // Немного увеличим высоту для новых элементов
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

    public static void main(String[] args) {
        launch(args);
    }
}