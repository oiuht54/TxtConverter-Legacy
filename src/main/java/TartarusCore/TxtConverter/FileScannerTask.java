package TartarusCore.TxtConverter;

import javafx.concurrent.Task;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Задача для сканирования директории проекта.
 * ОПТИМИЗИРОВАНО: Использует walkFileTree для пропуска игнорируемых поддеревьев (node_modules и т.д.).
 */
public class FileScannerTask extends Task<List<Path>> {

    private final String sourceDirPath;
    private final List<String> extensions;
    private final List<String> ignoredFolders;
    private final String loadingMsg;

    public FileScannerTask(String sourceDirPath, List<String> extensions, List<String> ignoredFolders) {
        this.sourceDirPath = sourceDirPath;
        this.extensions = extensions;
        this.ignoredFolders = ignoredFolders;
        this.loadingMsg = LanguageManager.getInstance().getString("ui.status_scanning");
    }

    @Override
    protected List<Path> call() throws Exception {
        updateMessage(loadingMsg);
        Path sourcePath = Paths.get(sourceDirPath);
        Path outputDir = sourcePath.resolve(ProjectConstants.OUTPUT_DIR_NAME);
        List<Path> foundFiles = new ArrayList<>();

        Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                // 1. Игнорируем нашу выходную папку
                if (dir.equals(outputDir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                // 2. Проверяем имя папки на наличие в списке игнорируемых
                // Это позволяет НЕ заходить внутрь node_modules, .git и т.д.
                String dirName = dir.getFileName().toString().toLowerCase();

                // Пропускаем скрытые папки (кроме текущей корневой, если она скрытая)
                if (!dir.equals(sourcePath) && dirName.startsWith(".") && !dirName.equals(".gitignore")) {
                    // Дополнительная проверка: если папка .git или .idea, скипаем сразу.
                    // Но если пользователь явно не добавил их в игнор, логика ниже сработает.
                    // По умолчанию считаем, что скрытые папки часто системные, но доверимся списку ignoredFolders.
                }

                if (ignoredFolders.contains(dirName)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString().toLowerCase();

                // Всегда берем .md файлы (полезно для документации), если это не жестко запрещено
                boolean isMatch = false;
                if (fileName.endsWith(".md")) {
                    isMatch = true;
                } else {
                    // Проверка расширения
                    int dotIndex = fileName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        String ext = fileName.substring(dotIndex + 1);
                        if (extensions.contains(ext)) {
                            isMatch = true;
                        }
                    } else if (extensions.contains(fileName)) {
                        // Файлы без расширения (например Makefile, Dockerfile)
                        isMatch = true;
                    }
                }

                if (isMatch) {
                    foundFiles.add(file);
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                // Игнорируем ошибки доступа к файлам
                return FileVisitResult.CONTINUE;
            }
        });

        // Сортируем результат для красоты
        foundFiles.sort(Path::compareTo);
        return foundFiles;
    }
}