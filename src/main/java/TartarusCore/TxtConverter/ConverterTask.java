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

/**
 * Задача для копирования файлов и генерации отчетов.
 * Использует локализацию и константы.
 */
public class ConverterTask extends Task<Void> {

    private final String sourceDirPath;
    private final List<Path> filesToProcess;
    private final Set<Path> filesSelectedForMerge;
    private final boolean generateStructureFile;
    private final boolean generateMergedFile;

    // Сохраняем bundle при создании задачи, чтобы смена языка посередине процесса не ломала отчет
    private final ResourceBundle bundle;

    public ConverterTask(String sourceDirPath, List<Path> filesToProcess,
                         Set<Path> filesSelectedForMerge, boolean generateStructureFile, boolean generateMergedFile) {
        this.sourceDirPath = sourceDirPath;
        this.filesToProcess = filesToProcess;
        this.filesSelectedForMerge = filesSelectedForMerge;
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

        if (generateStructureFile && !filesToProcess.isEmpty()) {
            updateMessage(loc("task.generating_structure"));
            List<Path> relativePaths = filesToProcess.stream().map(sourcePath::relativize).collect(Collectors.toList());
            generateStructureReport(outputPath, sourcePath.getFileName().toString(), relativePaths);
        }

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

    private void generateStructureReport(Path outputPath, String rootDirName, List<Path> relativePaths) throws IOException {
        Path reportFile = outputPath.resolve(ProjectConstants.REPORT_STRUCTURE_FILE);
        StringBuilder reportContent = new StringBuilder(loc("report.structure_header") + "\n\n");
        reportContent.append("```\n").append(rootDirName).append("\n");

        for (Path path : relativePaths) {
            StringBuilder prefix = new StringBuilder();
            if (path.getParent() != null) {
                for (int i = 0; i < path.getNameCount() - 1; i++) prefix.append("│   ");
            }
            reportContent.append(prefix).append("├── ").append(path.getFileName()).append("\n");
        }
        reportContent.append("```\n");
        Files.writeString(reportFile, reportContent.toString(), StandardCharsets.UTF_8);
    }

    private void generateMergedFile(Path outputPath, Map<Path, Path> processedFilesMap) throws IOException {
        String projectName = Paths.get(sourceDirPath).getFileName().toString();
        // Формируем имя файла: _ProjectName_Full_Source_code.txt
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