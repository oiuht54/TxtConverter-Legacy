package TartarusCore.TxtConverter;

import javafx.concurrent.Task;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Задача для копирования файлов и генерации отчетов.
 *
 * V3 Update:
 * - MAXIMUM Compression теперь удаляет отступы (для безопасных языков)
 * - Удаляет блочные и инлайн комментарии.
 * - Structure в режиме MAX использует плоский список (Flat List) для максимальной экономии.
 */
public class ConverterTask extends Task<Void> {

    private final String sourceDirPath;
    private final List<Path> filesToProcess;
    private final Set<Path> filesSelectedForMerge;
    private final List<String> ignoredFolders;
    private final boolean generateStructureFile;
    private final boolean compactMode;
    private final CompressionLevel compressionLevel;
    private final boolean generateMergedFile;

    private final ResourceBundle bundle;

    private static final int COLLAPSE_THRESHOLD = 5;

    // Паттерн для удаления блочных комментариев /* ... */
    private static final Pattern BLOCK_COMMENT_PATTERN = Pattern.compile("/\\*[\\s\\S]*?\\*/");

    public ConverterTask(String sourceDirPath, List<Path> filesToProcess,
                         Set<Path> filesSelectedForMerge,
                         List<String> ignoredFolders,
                         boolean generateStructureFile,
                         boolean compactMode,
                         CompressionLevel compressionLevel,
                         boolean generateMergedFile) {
        this.sourceDirPath = sourceDirPath;
        this.filesToProcess = filesToProcess;
        this.filesSelectedForMerge = filesSelectedForMerge;
        this.ignoredFolders = ignoredFolders;
        this.generateStructureFile = generateStructureFile;
        this.compactMode = compactMode;
        this.compressionLevel = compressionLevel;
        this.generateMergedFile = generateMergedFile;
        this.bundle = LanguageManager.getInstance().getBundle();
    }

    private String loc(String key) {
        return bundle.getString(key);
    }

    @Override
    protected Void call() throws Exception {
        updateMessage(loc("task.preparing"));
        Path sourcePath = Paths.get(sourceDirPath);
        Path outputPath = sourcePath.resolve(ProjectConstants.OUTPUT_DIR_NAME);

        prepareOutputDirectory(outputPath);

        Map<Path, Path> processedFilesMap = new LinkedHashMap<>();
        int totalFiles = filesToProcess.size();
        int processedCount = 0;

        for (Path sourceFile : filesToProcess) {
            if (isCancelled()) break;

            processedCount++;
            updateProgress(processedCount, totalFiles);
            updateMessage(String.format(loc("task.processing"), sourceFile.getFileName()));

            String sourceFileName = sourceFile.getFileName().toString();
            String destFileName = sourceFileName.toLowerCase().endsWith(".md") ? sourceFileName : sourceFileName + ".txt";
            Path destFile = outputPath.resolve(destFileName);

            // СЖАТИЕ КОДА
            if (compressionLevel != CompressionLevel.NONE && !sourceFileName.toLowerCase().endsWith(".md")) {
                try {
                    String content = Files.readString(sourceFile, StandardCharsets.UTF_8);
                    // Передаем путь, чтобы определить расширение файла
                    String compressedContent = applyCompression(content, sourceFile);
                    Files.writeString(destFile, compressedContent, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    Files.copy(sourceFile, destFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                Files.copy(sourceFile, destFile, StandardCopyOption.REPLACE_EXISTING);
            }

            processedFilesMap.put(sourceFile, destFile);
        }

        if (generateStructureFile) {
            updateMessage(loc("task.generating_structure"));
            generateDeepStructureReport(outputPath, sourcePath);
        }

        if (generateMergedFile && !processedFilesMap.isEmpty()) {
            updateMessage(loc("task.merging"));
            generateMergedFile(outputPath, processedFilesMap);
        }

        updateMessage(loc("task.done"));
        updateProgress(1, 1);
        return null;
    }

    // --- ЛОГИКА СЖАТИЯ КОДА ---

    private String applyCompression(String content, Path file) {
        if (compressionLevel == CompressionLevel.MAXIMUM) {
            return compressMax(content, file);
        } else if (compressionLevel == CompressionLevel.SMART) {
            return compressSmart(content);
        }
        return content;
    }

    private String compressSmart(String content) {
        // Просто удаляем лишние пустые строки (оставляем абзацы)
        return content.replaceAll("(\\r?\\n){3,}", "\n\n").trim();
    }

    private String compressMax(String content, Path file) {
        // 1. Удаляем блочные комментарии (Java, C#, CSS, TS)
        // Внимание: Это Regex, он может ошибиться внутри строки, но для задачи "Dump" это приемлемый риск
        content = BLOCK_COMMENT_PATTERN.matcher(content).replaceAll("");

        String[] lines = content.split("\\R");
        StringBuilder sb = new StringBuilder(content.length() / 2);

        boolean isSensitive = isWhitespaceSensitive(file);

        for (String line : lines) {
            String trimmed = line.trim();

            // 2. Удаляем пустые строки
            if (trimmed.isEmpty()) continue;

            // 3. Удаляем строки, начинающиеся с комментариев
            if (trimmed.startsWith("//") || trimmed.startsWith("#")) continue;

            // 4. Удаляем инлайн комментарии (грубо)
            // Ищем // или #, но пытаемся не трогать http://
            line = removeInlineComment(line);

            // Если после удаления комментария строка опустела
            if (line.trim().isEmpty()) continue;

            // 5. Обработка отступов
            if (isSensitive) {
                // Для Python/GDScript: Оставляем отступ слева, удаляем справа
                sb.append(stripTrailing(line)).append("\n");
            } else {
                // Для остальных: Полный trim (удаляем отступ слева)
                // Это превращает код в "плоский" список команд, экономя кучу токенов
                sb.append(line.trim()).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private String removeInlineComment(String line) {
        // Простая эвристика: если есть //, обрезаем.
        // Исключение: http:// или https:// (часто в строках)
        int doubleSlash = line.indexOf("//");
        if (doubleSlash != -1) {
            // Проверяем, не является ли это частью URL
            if (doubleSlash > 0 && line.charAt(doubleSlash - 1) == ':') {
                // Похоже на URL, оставляем как есть (или ищем следующий //)
            } else {
                return line.substring(0, doubleSlash);
            }
        }

        // Для Godot/Python комментарии через #
        int hash = line.indexOf("#");
        if (hash != -1) {
            // Исключаем цветовые коды (примерно) или макросы
            // Но для агрессивного сжатия просто режем
            return line.substring(0, hash);
        }

        return line;
    }

    private boolean isWhitespaceSensitive(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".gd") ||
                name.endsWith(".py") ||
                name.endsWith(".yaml") ||
                name.endsWith(".yml");
    }

    private String stripTrailing(String str) {
        int len = str.length();
        while ((len > 0) && (str.charAt(len - 1) <= ' ')) {
            len--;
        }
        return str.substring(0, len);
    }

    // --- ОСТАЛЬНОЕ ---

    private void prepareOutputDirectory(Path outputPath) throws IOException {
        if (Files.exists(outputPath)) {
            try (var stream = Files.walk(outputPath)) {
                stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
        Files.createDirectories(outputPath);
    }

    private void generateDeepStructureReport(Path outputPath, Path rootPath) throws IOException {
        Path reportFile = outputPath.resolve(ProjectConstants.REPORT_STRUCTURE_FILE);
        StringBuilder report = new StringBuilder();

        report.append(loc("report.structure_header")).append("\n");
        report.append(String.format(loc("report.generated_date"),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))).append("\n\n");

        if (compressionLevel == CompressionLevel.NONE) {
            report.append("### Legend / Легенда:\n");
            report.append("- `[ M ]` Merged: Full content included.\n");
            report.append("- `[ S ]` Stub: File included as a stub.\n\n");
            report.append("```text\n");
        } else {
            // Для сжатых режимов не нужна легенда и блок кода (экономим токены)
            if (compressionLevel == CompressionLevel.MAXIMUM) {
                report.append("(Flat Structure Mode)\n");
            } else {
                report.append("(Compact Tree Mode)\n");
            }
        }

        // Корень
        if (compressionLevel != CompressionLevel.MAXIMUM) {
            report.append(compressionLevel == CompressionLevel.SMART ? rootPath.getFileName() + "/" : "[ROOT] " + rootPath.getFileName()).append("\n");
        }

        Set<Path> processedSet = new HashSet<>(filesToProcess);

        // Если MAX - используем плоский список, иначе дерево
        if (compressionLevel == CompressionLevel.MAXIMUM) {
            generateFlatStructure(rootPath, report, processedSet);
        } else {
            boolean simpleTree = (compressionLevel == CompressionLevel.SMART);
            walkDirectoryTree(rootPath, "", report, processedSet, simpleTree);
        }

        if (compressionLevel == CompressionLevel.NONE) report.append("```\n");

        Files.writeString(reportFile, report.toString(), StandardCharsets.UTF_8);
    }

    // Новая логика для MAX структуры: Плоский список путей
    // dir/subdir/file.ext
    private void generateFlatStructure(Path currentDir, StringBuilder sb, Set<Path> processedSet) {
        try (Stream<Path> stream = Files.walk(currentDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> shouldIncludeInStructure(p, currentDir))
                    .forEach(p -> {
                        // Если compactMode включен, показываем только обработанные
                        if (compactMode && !processedSet.contains(p)) return;

                        // Получаем относительный путь
                        String relPath = currentDir.relativize(p).toString().replace('\\', '/');

                        if (processedSet.contains(p)) {
                            sb.append(relPath).append("\n");
                        } else {
                            // Игнорируемые файлы в плоском списке (если Compact выключен)
                            sb.append(relPath).append(" [ignore]\n");
                        }
                    });
        } catch (IOException e) {
            sb.append("Error generating flat structure");
        }
    }

    private void walkDirectoryTree(Path currentDir, String prefix, StringBuilder sb, Set<Path> processedSet, boolean simpleTree) {
        List<Path> allChildren;
        try (Stream<Path> stream = Files.list(currentDir)) {
            allChildren = stream.filter(p -> shouldIncludeInStructure(p, currentDir)).collect(Collectors.toList());
        } catch (IOException e) { return; }

        List<Path> nodesToShow = new ArrayList<>();
        List<Path> filesToCollapse = new ArrayList<>();

        for (Path child : allChildren) {
            if (Files.isDirectory(child)) nodesToShow.add(child);
            else {
                if (processedSet.contains(child)) nodesToShow.add(child);
                else if (!compactMode) filesToCollapse.add(child);
            }
        }

        if (!compactMode && !filesToCollapse.isEmpty() && filesToCollapse.size() <= COLLAPSE_THRESHOLD) {
            nodesToShow.addAll(filesToCollapse);
            filesToCollapse.clear();
        }

        nodesToShow.sort((p1, p2) -> {
            boolean d1 = Files.isDirectory(p1);
            boolean d2 = Files.isDirectory(p2);
            if (d1 && !d2) return -1;
            if (!d1 && d2) return 1;
            return p1.getFileName().compareTo(p2.getFileName());
        });

        int totalItems = nodesToShow.size() + (filesToCollapse.isEmpty() ? 0 : 1);
        int currentIndex = 0;

        for (Path path : nodesToShow) {
            boolean isLast = (currentIndex == totalItems - 1);
            printNode(path, prefix, isLast, sb, processedSet, simpleTree);
            currentIndex++;
        }

        if (!filesToCollapse.isEmpty()) {
            Map<String, Long> extStats = filesToCollapse.stream().map(this::getExtension)
                    .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
            String statsStr = extStats.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(3).map(e -> e.getKey() + "(" + e.getValue() + ")").collect(Collectors.joining(", "));

            if (simpleTree) {
                sb.append(prefix).append("  ... (").append(filesToCollapse.size()).append(": ").append(statsStr).append(")\n");
            } else {
                String connector = "└── ";
                sb.append(prefix).append(connector).append("[ ... ").append(filesToCollapse.size()).append(" ignored: ").append(statsStr).append(" ... ]\n");
            }
        }
    }

    private void printNode(Path path, String prefix, boolean isLast, StringBuilder sb, Set<Path> processedSet, boolean simpleTree) {
        if (simpleTree) {
            String currentIndent = prefix + "  ";
            if (Files.isDirectory(path)) {
                sb.append(currentIndent).append(path.getFileName()).append("/\n");
                walkDirectoryTree(path, currentIndent, sb, processedSet, true);
            } else {
                sb.append(currentIndent).append(path.getFileName()).append("\n");
            }
        } else {
            String connector = isLast ? "└── " : "├── ";
            String childPrefix = prefix + (isLast ? "    " : "│   ");

            if (Files.isDirectory(path)) {
                sb.append(prefix).append(connector).append("[DIR] ").append(path.getFileName()).append("\n");
                walkDirectoryTree(path, childPrefix, sb, processedSet, false);
            } else {
                String size = formatSize(path);
                String status = getFileStatus(path, processedSet);
                sb.append(prefix).append(connector)
                        .append("[FILE] ").append(path.getFileName())
                        .append(" (").append(size).append(") ")
                        .append(status).append("\n");
            }
        }
    }

    private void generateMergedFile(Path outputPath, Map<Path, Path> processedFilesMap) throws IOException {
        String projectName = Paths.get(sourceDirPath).getFileName().toString();
        String outputFileName = "_" + projectName + ProjectConstants.MERGED_FILE_SUFFIX;

        Path mergedFile = outputPath.resolve(outputFileName);
        StringBuilder mergedContent = new StringBuilder();

        if (compressionLevel != CompressionLevel.NONE) {
            mergedContent.append("# Project: ").append(projectName).append("\n\n");
        } else {
            mergedContent.append(String.format(loc("report.merged_header"), projectName)).append("\n");
            mergedContent.append(String.format(loc("report.generated_date"),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))).append("\n");
        }

        for (Map.Entry<Path, Path> entry : processedFilesMap.entrySet()) {
            Path originalPath = entry.getKey();
            Path destinationPath = entry.getValue();
            String fileName = originalPath.getFileName().toString();

            if (compressionLevel != CompressionLevel.NONE) {
                mergedContent.append("\n>>> ").append(fileName).append("\n");
            } else {
                mergedContent.append("\n--- ").append(String.format(loc("report.file_header"), fileName)).append(" ---\n");
            }

            if (filesSelectedForMerge.contains(originalPath)) {
                try {
                    String content = Files.readString(destinationPath, StandardCharsets.UTF_8);
                    mergedContent.append(content).append("\n");
                } catch (IOException e) {
                    mergedContent.append("!!! Error: ").append(e.getMessage()).append("\n");
                }
            } else {
                mergedContent.append("(Stub)\n\n");
            }
        }
        Files.writeString(mergedFile, mergedContent.toString(), StandardCharsets.UTF_8);
    }

    private String getExtension(Path path) {
        String name = path.getFileName().toString();
        int lastDot = name.lastIndexOf('.');
        return (lastDot > 0) ? name.substring(lastDot) : "no-ext";
    }

    private boolean shouldIncludeInStructure(Path path, Path rootOfWalk) {
        String name = path.getFileName().toString();
        if (name.equals(ProjectConstants.OUTPUT_DIR_NAME)) return false;
        if (name.endsWith(".import") || name.endsWith(".tmp") || name.endsWith(".uid")) return false;
        if (name.startsWith(".") && !name.equals(".gitignore")) return false;

        if (Files.isDirectory(path)) {
            if (ignoredFolders.contains(name.toLowerCase())) return false;
        }
        return true;
    }

    private String formatSize(Path path) {
        try {
            long bytes = Files.size(path);
            if (bytes < 1024) return bytes + " B";
            return (bytes / 1024) + " KB";
        } catch (IOException e) {
            return "?";
        }
    }

    private String getFileStatus(Path path, Set<Path> processedSet) {
        if (filesSelectedForMerge.contains(path)) {
            return "[ M ]";
        } else if (processedSet.contains(path)) {
            return "[ S ]";
        } else {
            return "[ - ]";
        }
    }
}