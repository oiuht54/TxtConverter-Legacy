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
 * Использует локализацию и константы.
 */
public class ConverterTask extends Task<Void> {

    private final String sourceDirPath;
    private final List<Path> filesToProcess;
    private final Set<Path> filesSelectedForMerge;
    private final List<String> ignoredFolders;
    private final boolean generateStructureFile;
    private final boolean generateMergedFile;

    private final ResourceBundle bundle;

    public ConverterTask(String sourceDirPath, List<Path> filesToProcess,
                         Set<Path> filesSelectedForMerge,
                         List<String> ignoredFolders,
                         boolean generateStructureFile, boolean generateMergedFile) {
        this.sourceDirPath = sourceDirPath;
        this.filesToProcess = filesToProcess;
        this.filesSelectedForMerge = filesSelectedForMerge;
        this.ignoredFolders = ignoredFolders;
        this.generateStructureFile = generateStructureFile;
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

        // --- 1. Обработка файлов (копирование) ---
        for (Path sourceFile : filesToProcess) {
            if (isCancelled()) break;

            processedCount++;
            updateProgress(processedCount, totalFiles);
            updateMessage(String.format(loc("task.processing"), sourceFile.getFileName()));

            String sourceFileName = sourceFile.getFileName().toString();
            String destFileName = sourceFileName.toLowerCase().endsWith(".md") ? sourceFileName : sourceFileName + ".txt";
            Path destFile = outputPath.resolve(destFileName);

            Files.copy(sourceFile, destFile, StandardCopyOption.REPLACE_EXISTING);
            processedFilesMap.put(sourceFile, destFile);
        }

        // --- 2. Генерация карты структуры (Новая логика) ---
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

    /**
     * Создает подробный отчет о структуре проекта с рекурсивным обходом.
     */
    private void generateDeepStructureReport(Path outputPath, Path rootPath) throws IOException {
        Path reportFile = outputPath.resolve(ProjectConstants.REPORT_STRUCTURE_FILE);
        StringBuilder report = new StringBuilder();

        // Заголовок
        report.append(loc("report.structure_header")).append("\n");
        report.append(String.format(loc("report.generated_date"),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))).append("\n\n");

        // Легенда
        report.append("### Legend / Легенда:\n");
        report.append("- `[ M ]` Merged: Full content included in report.\n");
        report.append("- `[ S ]` Stub: File included as a stub (content omitted).\n");
        report.append("- `[ - ]` Ignored: Not included in report (wrong extension or binary).\n\n");

        report.append("```text\n");
        report.append("[ROOT] ").append(rootPath.getFileName()).append("\n");

        // Рекурсивный запуск
        Set<Path> processedSet = new HashSet<>(filesToProcess);

        walkDirectoryTree(rootPath, "", report, processedSet);

        report.append("```\n");
        Files.writeString(reportFile, report.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Рекурсивный метод для построения ASCII-дерева.
     */
    private void walkDirectoryTree(Path currentDir, String prefix, StringBuilder sb, Set<Path> processedSet) {
        List<Path> elements;
        try (Stream<Path> stream = Files.list(currentDir)) {
            elements = stream
                    .filter(p -> shouldIncludeInStructure(p, currentDir))
                    .sorted((p1, p2) -> {
                        // Папки сверху, файлы снизу
                        boolean d1 = Files.isDirectory(p1);
                        boolean d2 = Files.isDirectory(p2);
                        if (d1 && !d2) return -1;
                        if (!d1 && d2) return 1;
                        return p1.getFileName().compareTo(p2.getFileName());
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            sb.append(prefix).append("└── !!! Access Denied !!!\n");
            return;
        }

        for (int i = 0; i < elements.size(); i++) {
            Path path = elements.get(i);
            boolean isLast = (i == elements.size() - 1);
            String connector = isLast ? "└── " : "├── ";
            String childPrefix = prefix + (isLast ? "    " : "│   ");

            if (Files.isDirectory(path)) {
                // Заменили эмодзи на [DIR]
                sb.append(prefix).append(connector).append("[DIR] ").append(path.getFileName()).append("\n");
                walkDirectoryTree(path, childPrefix, sb, processedSet);
            } else {
                String size = formatSize(path);
                String status = getFileStatus(path, processedSet);
                // Заменили эмодзи на [FILE]
                // Пример: ├── [FILE] Main.java (12 KB) [ M ]
                sb.append(prefix).append(connector)
                        .append("[FILE] ").append(path.getFileName())
                        .append(" (").append(size).append(") ")
                        .append(status).append("\n");
            }
        }
    }

    private boolean shouldIncludeInStructure(Path path, Path rootOfWalk) {
        String name = path.getFileName().toString();
        // Игнорируем выходную папку самого конвертера
        if (name.equals(ProjectConstants.OUTPUT_DIR_NAME)) return false;

        // >>> Игнорируем файлы .import (Godot) <<<
        if (name.endsWith(".import")) return false;

        // Игнорируем скрытые файлы (опционально, но обычно полезно)
        if (name.startsWith(".") && !name.equals(".gitignore")) return false;

        // Проверяем список игнорируемых папок
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
            return "[ M ]"; // Merged Full
        } else if (processedSet.contains(path)) {
            return "[ S ]"; // Stub
        } else {
            return "[ - ]"; // Ignored
        }
    }

    private void generateMergedFile(Path outputPath, Map<Path, Path> processedFilesMap) throws IOException {
        String projectName = Paths.get(sourceDirPath).getFileName().toString();
        String outputFileName = "_" + projectName + ProjectConstants.MERGED_FILE_SUFFIX;

        Path mergedFile = outputPath.resolve(outputFileName);
        StringBuilder mergedContent = new StringBuilder();

        mergedContent.append(String.format(loc("report.merged_header"), projectName)).append("\n");
        mergedContent.append(String.format(loc("report.generated_date"),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))).append("\n");

        for (Map.Entry<Path, Path> entry : processedFilesMap.entrySet()) {
            Path originalPath = entry.getKey();
            Path destinationPath = entry.getValue();
            String fileName = originalPath.getFileName().toString();

            mergedContent.append("\n--- ").append(String.format(loc("report.file_header"), fileName)).append(" ---\n");

            if (filesSelectedForMerge.contains(originalPath)) {
                try {
                    String content = Files.readString(destinationPath, StandardCharsets.UTF_8);
                    mergedContent.append(content).append("\n");
                } catch (IOException e) {
                    mergedContent.append(String.format(loc("report.read_error"), e.getMessage())).append("\n");
                }
            } else {
                mergedContent.append(loc("report.omitted")).append("\n\n");
            }
        }
        Files.writeString(mergedFile, mergedContent.toString(), StandardCharsets.UTF_8);
    }
}