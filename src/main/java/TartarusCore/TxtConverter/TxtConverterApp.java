package TartarusCore.TxtConverter;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle; // <<< ИМПОРТИРУЕМ StageStyle

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
        Scene scene = new Scene(fxmlLoader.load(), 600, 450); // Немного увеличим высоту для заголовка
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());

        // >>> ГЛАВНОЕ ИЗМЕНЕНИЕ: Убираем стандартную рамку окна <<<
        stage.initStyle(StageStyle.UNDECORATED);

        // Передаем stage в контроллер, чтобы он мог управлять окном
        MainController controller = fxmlLoader.getController();
        controller.setStage(stage);

        stage.setTitle("TXT File Converter");
        // Минимальные размеры теперь не так актуальны, но оставим
        stage.setMinWidth(600);
        stage.setMinHeight(450);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}