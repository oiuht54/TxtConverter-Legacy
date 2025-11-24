package TartarusCore.TxtConverter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Класс, отвечающий за управление пресетами и авто-определение типа проекта.
 * Вынесен из MainController для разгрузки кода.
 */
public class PresetManager {

    private static PresetManager instance;

    private final Map<String, String> presets = new LinkedHashMap<>();
    private final Map<String, String> ignoredFolderPresets = new LinkedHashMap<>();

    private PresetManager() {
        setupPresets();
    }

    public static PresetManager getInstance() {
        if (instance == null) {
            instance = new PresetManager();
        }
        return instance;
    }

    private void setupPresets() {
        presets.put("Manual", "");

        // GameDev
        presets.put("Godot Engine", "gd, tscn, tres, gdshader, godot");
        presets.put("Unity Engine", "cs, shader, cginc, txt, json, xml, asmdef, asset, inputactions");

        // .NET
        presets.put("C# (.NET / Visual Studio)", "cs, csproj, sln, xaml, config, json, cshtml, razor, sql, xml, props, targets");

        // Java
        presets.put("Java (Maven/Gradle)", "java, xml, properties, fxml, gradle, groovy");

        // Web
        presets.put("Web (JavaScript / Classic)", "js, mjs, html, css, json");
        presets.put("Web (TypeScript / React)", "ts, tsx, jsx, html, css, scss, less, json, vue, svelte");

        // Python
        presets.put("Python", "py, requirements.txt, yaml, yml, json");

        // --- Ignored Folders ---

        ignoredFolderPresets.put("Manual", "");
        ignoredFolderPresets.put("Godot Engine", ".godot, export_presets, .import");
        ignoredFolderPresets.put("Unity Engine", "Library, Temp, obj, bin, ProjectSettings, Logs, UserSettings, .vs, .idea");

        ignoredFolderPresets.put("C# (.NET / Visual Studio)", "bin, obj, .vs, packages, TestResults, .git, .idea, .vscode");

        ignoredFolderPresets.put("Java (Maven/Gradle)", "target, .idea, build, .settings, bin, out");

        String webIgnored = "node_modules, dist, build, .next, .nuxt, coverage, .git, .vscode, .idea";
        ignoredFolderPresets.put("Web (JavaScript / Classic)", webIgnored);
        ignoredFolderPresets.put("Web (TypeScript / React)", webIgnored);

        ignoredFolderPresets.put("Python", "__pycache__, venv, env, .venv, .git, .idea, .vscode, build, dist, egg-info");
    }

    public Set<String> getPresetNames() {
        return presets.keySet();
    }

    public String getExtensionsFor(String presetName) {
        return presets.getOrDefault(presetName, "");
    }

    public String getIgnoredFoldersFor(String presetName) {
        return ignoredFolderPresets.getOrDefault(presetName, "");
    }

    public boolean hasPreset(String presetName) {
        return presets.containsKey(presetName);
    }

    /**
     * Пытается определить тип проекта по содержимому корневой папки.
     * @param root Путь к папке проекта
     * @return Название пресета или null, если не определено
     */
    public String autoDetectPreset(Path root) {
        // 1. Godot
        if (Files.exists(root.resolve("project.godot"))) return "Godot Engine";

        // 2. Unity
        if (Files.exists(root.resolve("Assets")) && Files.exists(root.resolve("ProjectSettings"))) return "Unity Engine";

        // 3. C# / .NET (Generic)
        // Unity check handles Unity projects. Here we catch generic .NET
        try (Stream<Path> entries = Files.list(root)) {
            boolean isDotNet = entries.anyMatch(p -> {
                String name = p.getFileName().toString().toLowerCase();
                return name.endsWith(".sln") || name.endsWith(".csproj");
            });
            if (isDotNet) return "C# (.NET / Visual Studio)";
        } catch (IOException e) {
            // ignore access errors
        }

        // 4. Java (Maven/Gradle)
        if (Files.exists(root.resolve("pom.xml")) ||
                Files.exists(root.resolve("build.gradle")) ||
                Files.exists(root.resolve("build.gradle.kts"))) {
            return "Java (Maven/Gradle)";
        }

        // 5. Python
        if (Files.exists(root.resolve("requirements.txt")) ||
                Files.exists(root.resolve("pyproject.toml")) ||
                Files.exists(root.resolve("venv")) ||
                Files.exists(root.resolve(".venv"))) {
            return "Python";
        }

        // 6. Web (Complex check)
        if (Files.exists(root.resolve("package.json"))) {
            // Если есть package.json, это веб. Но какой?
            // Проверяем признаки TypeScript
            if (Files.exists(root.resolve("tsconfig.json")) ||
                    Files.exists(root.resolve("vite.config.ts"))) {
                return "Web (TypeScript / React)";
            }
            return "Web (JavaScript / Classic)";
        }

        return null; // Ничего не нашли
    }
}