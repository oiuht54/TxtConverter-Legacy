package TartarusCore.TxtConverter;

import javafx.concurrent.Task;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Задача для сканирования директории проекта.
 */
public class FileScannerTask extends Task<List<Path>> {

    private final String sourceDirPath;
    private final List<String> extensions;
    private final List<String> ignoredFolders;
    private final String loadingMsg; // Локализованное сообщение

    public FileScannerTask(String sourceDirPath, List<String> extensions, List<String> ignoredFolders) {
        this.sourceDirPath = sourceDirPath;
        this.extensions = extensions;
        this.ignoredFolders = ignoredFolders;
        // Получаем строку один раз при создании, чтобы безопасно использовать в другом потоке
        this.loadingMsg = LanguageManager.getInstance().getString("ui.status_scanning");
    }

    @Override
    protected List<Path> call() throws Exception {
        updateMessage(loadingMsg);
        Path sourcePath = Paths.get(sourceDirPath);
        Path outputPath = sourcePath.resolve(ProjectConstants.OUTPUT_DIR_NAME);

        try (var stream = Files.walk(sourcePath)) {
            List<Path> foundFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.startsWith(outputPath)) // Игнорируем нашу выходную папку
                    .filter(path -> {
                        if (ignoredFolders.isEmpty()) return true;
                        for (Path part : sourcePath.relativize(path)) {
                            if (ignoredFolders.contains(part.toString().toLowerCase())) return false;
                        }
                        return true;
                    })
                    .filter(p -> {
                        String fileName = p.getFileName().toString().toLowerCase();
                        if (fileName.endsWith(".md")) return true;
                        int dotIndex = fileName.lastIndexOf('.');
                        if (dotIndex > 0) {
                            String extension = fileName.substring(dotIndex + 1);
                            return extensions.contains(extension);
                        }
                        return extensions.contains(fileName);
                    })
                    .sorted()
                    .collect(Collectors.toList());

            return foundFiles;
        }
    }
}