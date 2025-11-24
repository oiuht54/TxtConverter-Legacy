package TartarusCore.TxtConverter;

/**
 * Централизованное хранилище констант проекта.
 */
public class ProjectConstants {
    // Файловая система
    public static final String OUTPUT_DIR_NAME = "_ConvertedToTxt";
    public static final String REPORT_STRUCTURE_FILE = "_FileStructure.md";
    public static final String MERGED_FILE_SUFFIX = "_Full_Source_code.txt";

    // Настройки (Preferences Keys)
    public static final String PREF_APP_LANGUAGE = "app_language";
    public static final String PREF_LAST_DIR = "last_source_dir";
    public static final String PREF_LAST_PRESET = "last_preset";
    public static final String PREF_GEN_STRUCTURE = "gen_structure";
    public static final String PREF_COMPACT_MODE = "compact_mode";
    public static final String PREF_GEN_MERGED = "gen_merged";
    public static final String PREF_COMPRESSION = "compression_level";

    // Приватный конструктор
    private ProjectConstants() {}
}