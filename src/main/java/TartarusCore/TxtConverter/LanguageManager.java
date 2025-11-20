package TartarusCore.TxtConverter;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * Singleton класс для управления локализацией.
 * Отвечает за сохранение выбора пользователя и загрузку ресурсов в правильной кодировке.
 */
public class LanguageManager {

    private static LanguageManager instance;
    private ResourceBundle bundle;
    private Locale currentLocale;
    private final Preferences prefs;

    private final List<Runnable> listeners = new ArrayList<>();

    private LanguageManager() {
        prefs = Preferences.userNodeForPackage(LanguageManager.class);
        String langTag = prefs.get(ProjectConstants.PREF_APP_LANGUAGE, null);

        if (langTag != null) {
            setLocale(Locale.forLanguageTag(langTag));
        }
    }

    public static LanguageManager getInstance() {
        if (instance == null) {
            instance = new LanguageManager();
        }
        return instance;
    }

    public boolean isLanguageSelected() {
        return currentLocale != null;
    }

    public Locale getCurrentLocale() {
        return currentLocale;
    }

    public ResourceBundle getBundle() {
        return bundle;
    }

    public void setLocale(Locale locale) {
        this.currentLocale = locale;
        // ВАЖНОЕ ИЗМЕНЕНИЕ: Передаем UTF8Control, чтобы корректно читать кириллицу
        this.bundle = ResourceBundle.getBundle("TartarusCore.TxtConverter.messages", locale, new UTF8Control());
        prefs.put(ProjectConstants.PREF_APP_LANGUAGE, locale.toLanguageTag());
        notifyListeners();
    }

    public String getString(String key) {
        if (bundle == null) return "!!!" + key + "!!!";
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return "???" + key + "???";
        }
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    /**
     * Внутренний класс для принудительного чтения properties в UTF-8.
     * Это решает проблему вопросительных знаков на Windows.
     */
    private static class UTF8Control extends ResourceBundle.Control {
        @Override
        public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
                throws IllegalAccessException, InstantiationException, IOException {

            String bundleName = toBundleName(baseName, locale);
            String resourceName = toResourceName(bundleName, "properties");
            ResourceBundle bundle = null;
            InputStream stream = null;

            if (reload) {
                URL url = loader.getResource(resourceName);
                if (url != null) {
                    URLConnection connection = url.openConnection();
                    if (connection != null) {
                        connection.setUseCaches(false);
                        stream = connection.getInputStream();
                    }
                }
            } else {
                stream = loader.getResourceAsStream(resourceName);
            }

            if (stream != null) {
                try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    bundle = new PropertyResourceBundle(reader);
                } finally {
                    stream.close();
                }
            }
            return bundle;
        }
    }
}