package TartarusCore.TxtConverter;

/**
 * Централизованное хранилище констант проекта.
 * Позволяет легко менять имена файлов и ключи настроек в одном месте.
 */
public class ProjectConstants {
    // Файловая система
    public static final String OUTPUT_DIR_NAME = "_ConvertedToTxt";
    public static final String REPORT_STRUCTURE_FILE = "_FileStructure.md";
    public static final String MERGED_FILE_SUFFIX = "_Full_Source_code.txt";

    // Настройки
    public static final String PREF_APP_LANGUAGE = "app_language";

    // Приватный конструктор, чтобы нельзя было создать экземпляр
    private ProjectConstants() {}
}