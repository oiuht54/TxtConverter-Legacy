package TartarusCore.TxtConverter;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class SelectionController {

    @FXML private TreeView<String> fileTreeView;
    @FXML private HBox titleBar;

    private Stage dialogStage;
    private double xOffset = 0;
    private double yOffset = 0;
    private Optional<Set<Path>> result = Optional.empty();

    // Храним связь только для ФАЙЛОВ. Категории в эту карту не попадают.
    private final Map<CheckBoxTreeItem<String>, Path> itemToPathMap = new HashMap<>();

    @FXML
    public void initialize() {
        setupWindowDrag();

        // Настраиваем фабрику ячеек.
        // Мы используем стандартное поведение CheckBoxTreeCell, но добавляем CSS класс для категорий.
        fileTreeView.setCellFactory(tv -> new CheckBoxTreeCell<String>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); // Обязательно вызываем super, чтобы нарисовался чекбокс и текст

                if (empty || item == null) {
                    getStyleClass().remove("category-tree-item");
                    setText(null);
                    setGraphic(null);
                    return;
                }

                CheckBoxTreeItem<String> treeItem = (CheckBoxTreeItem<String>) getTreeItem();

                // Если элемента нет в карте путей файлов — значит это категория
                if (!itemToPathMap.containsKey(treeItem)) {
                    if (!getStyleClass().contains("category-tree-item")) {
                        getStyleClass().add("category-tree-item");
                    }
                    // ВАЖНО: Мы НЕ вызываем setGraphic(null), поэтому чекбокс рисуется автоматически.
                } else {
                    getStyleClass().remove("category-tree-item");
                }
            }
        });
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void initData(List<Path> allFiles, Set<Path> initiallySelected, Path rootPath) {
        // Корневой элемент (скрыт, но управляет всем)
        CheckBoxTreeItem<String> rootItem = new CheckBoxTreeItem<>("Files");
        rootItem.setExpanded(true);
        rootItem.setSelected(true); // По умолчанию пытаемся выбрать всё

        fileTreeView.setRoot(rootItem);
        fileTreeView.setShowRoot(false);

        // Группируем файлы по расширениям
        Map<String, List<Path>> groupedByExtension = allFiles.stream()
                .collect(Collectors.groupingBy(this::getFileExtension, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<Path>> entry : groupedByExtension.entrySet()) {
            String extension = entry.getKey();
            List<Path> filesInGroup = entry.getValue();

            // Создаем элемент категории
            String categoryName = extension + " (" + filesInGroup.size() + " files)";
            CheckBoxTreeItem<String> categoryItem = new CheckBoxTreeItem<>(categoryName);
            categoryItem.setExpanded(true); // Категории развернуты по умолчанию

            // Добавляем категорию в корень
            rootItem.getChildren().add(categoryItem);

            // Добавляем файлы в категорию
            for (Path file : filesInGroup) {
                String displayPath = rootPath.relativize(file).toString();
                CheckBoxTreeItem<String> fileItem = new CheckBoxTreeItem<>(displayPath);

                // Сначала добавляем ребенка в родителя
                categoryItem.getChildren().add(fileItem);

                // И только ПОТОМ устанавливаем состояние.
                // Это гарантирует, что родитель (categoryItem) "поймает" событие изменения
                // и сам проставит себе галочку, если все дети выбраны.
                boolean isSelected = initiallySelected.contains(file);
                fileItem.setSelected(isSelected);

                // Сохраняем связь элемента с реальным путем к файлу
                itemToPathMap.put(fileItem, file);
            }
        }
    }

    private String getFileExtension(Path path) {
        String name = path.getFileName().toString();
        int lastIndexOf = name.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return "No Extension";
        }
        return name.substring(lastIndexOf);
    }

    private void setupWindowDrag() {
        titleBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        titleBar.setOnMouseDragged(event -> {
            dialogStage.setX(event.getScreenX() - xOffset);
            dialogStage.setY(event.getScreenY() - yOffset);
        });
    }

    @FXML
    private void handleSelectAll() {
        if (fileTreeView.getRoot() != null) {
            // CheckBoxTreeItem в JavaFX распространяет состояние вниз по иерархии
            ((CheckBoxTreeItem<String>)fileTreeView.getRoot()).setSelected(true);
        }
    }

    @FXML
    private void handleDeselectAll() {
        if (fileTreeView.getRoot() != null) {
            ((CheckBoxTreeItem<String>)fileTreeView.getRoot()).setSelected(false);
        }
    }

    @FXML
    private void handleConfirm() {
        Set<Path> selectedFiles = new HashSet<>();

        // Собираем только те элементы, которые являются файлами (есть в map)
        itemToPathMap.forEach((item, path) -> {
            if (item.isSelected()) {
                selectedFiles.add(path);
            }
        });

        result = Optional.of(selectedFiles);
        dialogStage.close();
    }

    @FXML
    private void handleCancel() {
        result = Optional.empty();
        dialogStage.close();
    }

    public Optional<Set<Path>> getSelectedFiles() {
        return result;
    }
}