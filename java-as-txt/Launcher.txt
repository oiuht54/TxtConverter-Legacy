package TartarusCore.TxtConverter;

/**
 * Этот класс-запускатор является обходным путем для проблем,
 * возникающих при запуске JavaFX приложений из "fat jar".
 * Он служит единственной, явной точкой входа для приложения.
 */
public class Launcher {
    public static void main(String[] args) {
        TxtConverterApp.main(args);
    }
}