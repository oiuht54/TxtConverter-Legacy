package TartarusCore.TxtConverter;

import javafx.concurrent.Task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Задача для сканирования директории проекта.
 * Возвращает список найденных файлов, соответствующих критериям.
 */
public class FileScannerTask extends Task<List<Path>> {

    private final String sourceDirPath;
    private final String outputDirName;
    private final List<String> extensions;
    private final List<String> ignoredFolders;

    public FileScannerTask(String sourceDirPath, String outputDirName, List<String> extensions, List<String> ignoredFolders) {
        this.sourceDirPath = sourceDirPath;
        this.outputDirName = outputDirName;
        this.extensions = extensions;
        this.ignoredFolders = ignoredFolders;
    }

    @Override
    protected List<Path> call() throws Exception {
        updateMessage("Сканирование файлов...");
        Path sourcePath = Paths.get(sourceDirPath);
        Path outputPath = sourcePath.resolve(outputDirName);

        try (var stream = Files.walk(sourcePath)) {
            List<Path> foundFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.startsWith(outputPath)) // Игнорируем нашу выходную папку
                    .filter(path -> {
                        // Проверка игнорируемых папок
                        if (ignoredFolders.isEmpty()) return true;
                        for (Path part : sourcePath.relativize(path)) {
                            if (ignoredFolders.contains(part.toString().toLowerCase())) return false;
                        }
                        return true;
                    })
                    .filter(p -> {
                        // Проверка расширений
                        String fileName = p.getFileName().toString().toLowerCase();
                        if (fileName.endsWith(".md")) return true; // Markdown всегда берем
                        int dotIndex = fileName.lastIndexOf('.');
                        if (dotIndex > 0) {
                            String extension = fileName.substring(dotIndex + 1);
                            return extensions.contains(extension);
                        }
                        // Обработка файлов без расширения (например, Dockerfile), если они есть в списке
                        return extensions.contains(fileName);
                    })
                    .sorted()
                    .collect(Collectors.toList());

            updateMessage("Сканирование завершено.");
            return foundFiles;
        }
    }
}