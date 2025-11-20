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
 */
public class ConverterTask extends Task<Void> {

    private final String sourceDirPath;
    private final String outputDirName;
    private final List<Path> filesToProcess;
    private final Set<Path> filesSelectedForMerge;
    private final boolean generateStructureFile;
    private final boolean generateMergedFile;

    public ConverterTask(String sourceDirPath, String outputDirName, List<Path> filesToProcess,
                         Set<Path> filesSelectedForMerge, boolean generateStructureFile, boolean generateMergedFile) {
        this.sourceDirPath = sourceDirPath;
        this.outputDirName = outputDirName;
        this.filesToProcess = filesToProcess;
        this.filesSelectedForMerge = filesSelectedForMerge;
        this.generateStructureFile = generateStructureFile;
        this.generateMergedFile = generateMergedFile;
    }

    @Override
    protected Void call() throws Exception {
        updateMessage("Подготовка к конвертации...");
        Path sourcePath = Paths.get(sourceDirPath);
        Path outputPath = sourcePath.resolve(outputDirName);

        // Очистка/создание директории
        prepareOutputDirectory(outputPath);

        Map<Path, Path> processedFilesMap = new LinkedHashMap<>();
        int totalFiles = filesToProcess.size();
        int processedCount = 0;

        // Копирование файлов
        for (Path sourceFile : filesToProcess) {
            if (isCancelled()) break;

            processedCount++;
            updateProgress(processedCount, totalFiles);
            updateMessage("Обработка: " + sourceFile.getFileName());

            String sourceFileName = sourceFile.getFileName().toString();
            String destFileName = sourceFileName.toLowerCase().endsWith(".md") ? sourceFileName : sourceFileName + ".txt";
            Path destFile = outputPath.resolve(destFileName);

            Files.copy(sourceFile, destFile, StandardCopyOption.REPLACE_EXISTING);
            processedFilesMap.put(sourceFile, destFile);
        }

        // Генерация структуры
        if (generateStructureFile && !filesToProcess.isEmpty()) {
            updateMessage("Генерация файла структуры...");
            List<Path> relativePaths = filesToProcess.stream().map(sourcePath::relativize).collect(Collectors.toList());
            generateStructureReport(outputPath, sourcePath.getFileName().toString(), relativePaths);
        }

        // Генерация единого файла
        if (generateMergedFile && !processedFilesMap.isEmpty()) {
            updateMessage("Сборка единого файла...");
            generateMergedFile(outputPath, processedFilesMap);
        }

        updateMessage("Готово!");
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
        Path reportFile = outputPath.resolve("_FileStructure.md");
        StringBuilder reportContent = new StringBuilder("# Структура скопированных файлов\n\n");
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
        // Получаем имя проекта из корневой папки
        String projectName = Paths.get(sourceDirPath).getFileName().toString();
        // Добавляем нижнее подчеркивание в начало имени файла для сортировки
        String outputFileName = "_" + projectName + "_Full_Source_code.txt";

        Path mergedFile = outputPath.resolve(outputFileName);
        StringBuilder mergedContent = new StringBuilder();

        // Заголовок с именем проекта
        mergedContent.append("Единый файл с полным кодом проекта (").append(projectName).append(")\n");
        mergedContent.append("Дата генерации: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");

        for (Map.Entry<Path, Path> entry : processedFilesMap.entrySet()) {
            Path originalPath = entry.getKey();
            Path destinationPath = entry.getValue();
            String fileName = originalPath.getFileName().toString();

            // Минималистичный заголовок
            mergedContent.append("\n--- FILE: ").append(fileName).append(" ---\n");

            if (filesSelectedForMerge.contains(originalPath)) {
                try {
                    String content = Files.readString(destinationPath, StandardCharsets.UTF_8);
                    mergedContent.append(content).append("\n");
                } catch (IOException e) {
                    mergedContent.append("!!! ОШИБКА ЧТЕНИЯ: ").append(e.getMessage()).append(" !!!\n");
                }
            } else {
                // Важная инструкция для LLM
                mergedContent.append("(Содержимое файла опущено для краткости. При необходимости ИИ-ассистент обязан запросить его полный код)\n\n");
            }
        }
        Files.writeString(mergedFile, mergedContent.toString(), StandardCharsets.UTF_8);
    }
}