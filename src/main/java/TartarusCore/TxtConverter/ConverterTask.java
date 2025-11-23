package TartarusCore.TxtConverter;

import javafx.concurrent.Task;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Задача для копирования файлов и генерации отчетов.
 * - Compact Mode: показывает ТОЛЬКО конвертируемые файлы.
 * - Smart Token Compression: убирает визуальный шум (ASCII, пустые строки) для экономии токенов.
 * - Игнорируются: .import, .tmp, .uid
 */
public class ConverterTask extends Task<Void> {

    private final String sourceDirPath;
    private final List<Path> filesToProcess;
    private final Set<Path> filesSelectedForMerge;
    private final List<String> ignoredFolders;
    private final boolean generateStructureFile;
    private final boolean compactMode;
    private final boolean smartCompression; // <--- NEW FLAG
    private final boolean generateMergedFile;

    private final ResourceBundle bundle;

    private static final int COLLAPSE_THRESHOLD = 5;

    public ConverterTask(String sourceDirPath, List<Path> filesToProcess,
                         Set<Path> filesSelectedForMerge,
                         List<String> ignoredFolders,
                         boolean generateStructureFile,
                         boolean compactMode,
                         boolean smartCompression,
                         boolean generateMergedFile) {
        this.sourceDirPath = sourceDirPath;
        this.filesToProcess = filesToProcess;
        this.filesSelectedForMerge = filesSelectedForMerge;
        this.ignoredFolders = ignoredFolders;
        this.generateStructureFile = generateStructureFile;
        this.compactMode = compactMode;
        this.smartCompression = smartCompression;
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

        // --- 1. Обработка файлов ---
        for (Path sourceFile : filesToProcess) {
            if (isCancelled()) break;

            processedCount++;
            updateProgress(processedCount, totalFiles);
            updateMessage(String.format(loc("task.processing"), sourceFile.getFileName()));

            String sourceFileName = sourceFile.getFileName().toString();
            String destFileName = sourceFileName.toLowerCase().endsWith(".md") ? sourceFileName : sourceFileName + ".txt";
            Path destFile = outputPath.resolve(destFileName);

            // Если включено умное сжатие - обрабатываем контент перед записью
            if (smartCompression && !sourceFileName.toLowerCase().endsWith(".md")) { // Markdown лучше не трогать, чтобы не ломать форматирование
                String content = Files.readString(sourceFile, StandardCharsets.UTF_8);
                String compressedContent = compressCode(content);
                Files.writeString(destFile, compressedContent, StandardCharsets.UTF_8);
            } else {
                Files.copy(sourceFile, destFile, StandardCopyOption.REPLACE_EXISTING);
            }

            processedFilesMap.put(sourceFile, destFile);
        }

        // --- 2. Генерация карты структуры ---
        if (generateStructureFile) {
            updateMessage(loc("task.generating_structure"));
            generateDeepStructureReport(outputPath, sourcePath);
        }

        // --- 3. Генерация объединенного файла ---
        if (generateMergedFile && !processedFilesMap.isEmpty()) {
            updateMessage(loc("task.merging"));
            generateMergedFile(outputPath, processedFilesMap);
        }

        updateMessage(loc("task.done"));
        updateProgress(1, 1);
        return null;
    }

    // Метод для удаления лишних пустых строк (оставляет максимум одну пустую строку подряд)
    private String compressCode(String content) {
        return content.replaceAll("(\\r?\\n){3,}", "\n\n").trim();
    }

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

        if (!smartCompression) {
            // Стандартная легенда
            report.append("### Legend / Легенда:\n");
            report.append("- `[ M ]` Merged: Full content included.\n");
            report.append("- `[ S ]` Stub: File included as a stub.\n");

            if (compactMode) {
                report.append("- `(Hidden)`: Ignored files are hidden in Compact Mode.\n");
            } else {
                report.append("- `[ - ]` Ignored: Not included in report.\n");
                report.append("- `[ ... ]` Collapsed: Group of ignored files.\n");
            }
        } else {
            // Упрощенная легенда для режима сжатия
            report.append("(Smart Compression Enabled: Visual noise removed)\n");
            if (compactMode) report.append("(Compact Mode: Only relevant files shown)\n");
        }

        report.append("\n");

        if (!smartCompression) report.append("```text\n");

        // Корень
        report.append(smartCompression ? rootPath.getFileName() + "/" : "[ROOT] " + rootPath.getFileName()).append("\n");

        Set<Path> processedSet = new HashSet<>(filesToProcess);
        walkDirectoryTree(rootPath, "", report, processedSet);

        if (!smartCompression) report.append("```\n");

        Files.writeString(reportFile, report.toString(), StandardCharsets.UTF_8);
    }

    private void walkDirectoryTree(Path currentDir, String prefix, StringBuilder sb, Set<Path> processedSet) {
        List<Path> allChildren;
        try (Stream<Path> stream = Files.list(currentDir)) {
            allChildren = stream
                    .filter(p -> shouldIncludeInStructure(p, currentDir))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            sb.append(prefix).append(smartCompression ? "  ACCESS DENIED\n" : "└── !!! Access Denied !!!\n");
            return;
        }

        List<Path> nodesToShow = new ArrayList<>();
        List<Path> filesToCollapse = new ArrayList<>();

        for (Path child : allChildren) {
            if (Files.isDirectory(child)) {
                nodesToShow.add(child);
            } else {
                boolean isProcessed = processedSet.contains(child);

                if (isProcessed) {
                    nodesToShow.add(child);
                } else {
                    if (!compactMode) {
                        filesToCollapse.add(child);
                    }
                }
            }
        }

        // Логика отображения игнорируемых (только если выключен CompactMode)
        if (!compactMode && !filesToCollapse.isEmpty()) {
            if (filesToCollapse.size() <= COLLAPSE_THRESHOLD) {
                nodesToShow.addAll(filesToCollapse);
                filesToCollapse.clear();
            }
        }

        // Сортировка: Папки -> Файлы
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
            printNode(path, prefix, isLast, sb, processedSet);
            currentIndex++;
        }

        // Печать строки Summary (ТОЛЬКО если !compactMode и есть хвост)
        if (!filesToCollapse.isEmpty()) {
            Map<String, Long> extStats = filesToCollapse.stream()
                    .map(this::getExtension)
                    .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

            String statsStr = extStats.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(3)
                    .map(e -> e.getKey() + "(" + e.getValue() + ")")
                    .collect(Collectors.joining(", "));

            if (smartCompression) {
                // Сжатый вид summary (просто отступ, без ASCII)
                sb.append(prefix).append("  ... (").append(filesToCollapse.size()).append(" ignored: ").append(statsStr).append(")\n");
            } else {
                // Стандартный ASCII вид
                String connector = "└── ";
                sb.append(prefix).append(connector)
                        .append("[ ... ").append(filesToCollapse.size())
                        .append(" ignored files: ").append(statsStr)
                        .append(" ... ]\n");
            }
        }
    }

    private void printNode(Path path, String prefix, boolean isLast, StringBuilder sb, Set<Path> processedSet) {
        // >>> ЛОГИКА ОТОБРАЖЕНИЯ (SMART vs NORMAL) <<<

        if (smartCompression) {
            // == РЕЖИМ СЖАТИЯ ==
            // Используем только отступы (2 пробела), без труб
            String currentIndent = prefix + "  ";

            if (Files.isDirectory(path)) {
                sb.append(currentIndent).append(path.getFileName()).append("/\n");
                // Для рекурсии просто увеличиваем отступ
                walkDirectoryTree(path, currentIndent, sb, processedSet);
            } else {
                sb.append(currentIndent).append(path.getFileName()).append("\n");
            }

        } else {
            // == ОБЫЧНЫЙ РЕЖИМ (ASCII ART) ==
            String connector = isLast ? "└── " : "├── ";
            String childPrefix = prefix + (isLast ? "    " : "│   ");

            if (Files.isDirectory(path)) {
                sb.append(prefix).append(connector).append("[DIR] ").append(path.getFileName()).append("\n");
                walkDirectoryTree(path, childPrefix, sb, processedSet);
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

    private String getExtension(Path path) {
        String name = path.getFileName().toString();
        int lastDot = name.lastIndexOf('.');
        return (lastDot > 0) ? name.substring(lastDot) : "no-ext";
    }

    private boolean shouldIncludeInStructure(Path path, Path rootOfWalk) {
        String name = path.getFileName().toString();
        if (name.equals(ProjectConstants.OUTPUT_DIR_NAME)) return false;

        // Игнорируем технические файлы
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

    private void generateMergedFile(Path outputPath, Map<Path, Path> processedFilesMap) throws IOException {
        String projectName = Paths.get(sourceDirPath).getFileName().toString();
        String outputFileName = "_" + projectName + ProjectConstants.MERGED_FILE_SUFFIX;

        Path mergedFile = outputPath.resolve(outputFileName);
        StringBuilder mergedContent = new StringBuilder();

        if (smartCompression) {
            // Минималистичный заголовок
            mergedContent.append("# Project: ").append(projectName).append("\n");
            mergedContent.append("# Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n\n");
        } else {
            mergedContent.append(String.format(loc("report.merged_header"), projectName)).append("\n");
            mergedContent.append(String.format(loc("report.generated_date"),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))).append("\n");
        }

        for (Map.Entry<Path, Path> entry : processedFilesMap.entrySet()) {
            Path originalPath = entry.getKey();
            Path destinationPath = entry.getValue();
            String fileName = originalPath.getFileName().toString();

            // >>> Сжатые разделители <<<
            if (smartCompression) {
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
}