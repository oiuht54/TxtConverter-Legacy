package TartarusCore.TxtConverter;

import java.util.prefs.Preferences;

/**
 * Класс для управления сохранением состояния приложения (настроек).
 * Использует стандартный Java Preferences API.
 */
public class PreferenceManager {

    private static PreferenceManager instance;
    private final Preferences prefs;

    private PreferenceManager() {
        prefs = Preferences.userNodeForPackage(PreferenceManager.class);
    }

    public static PreferenceManager getInstance() {
        if (instance == null) {
            instance = new PreferenceManager();
        }
        return instance;
    }

    public void saveString(String key, String value) {
        if (value == null) prefs.remove(key);
        else prefs.put(key, value);
    }

    public String getString(String key, String def) {
        return prefs.get(key, def);
    }

    public void saveBoolean(String key, boolean value) {
        prefs.putBoolean(key, value);
    }

    public boolean getBoolean(String key, boolean def) {
        return prefs.getBoolean(key, def);
    }

    public void saveCompressionLevel(CompressionLevel level) {
        if (level != null) {
            prefs.put(ProjectConstants.PREF_COMPRESSION, level.name());
        }
    }

    public CompressionLevel getCompressionLevel() {
        String name = prefs.get(ProjectConstants.PREF_COMPRESSION, CompressionLevel.SMART.name());
        try {
            return CompressionLevel.valueOf(name);
        } catch (IllegalArgumentException e) {
            return CompressionLevel.SMART;
        }
    }
}