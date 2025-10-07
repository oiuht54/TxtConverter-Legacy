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
    // Этот Map - ключ к определению, является ли узел файлом или категорией
    private final Map<CheckBoxTreeItem<String>, Path> itemToPathMap = new HashMap<>();

    @FXML
    public void initialize() {
        setupWindowDrag();

        // >>> ГЛАВНОЕ ИСПРАВЛЕНИЕ: Используем кастомную "фабрику ячеек" <<<
        fileTreeView.setCellFactory(tv -> new CheckBoxTreeCell<String>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); // Сначала вызываем родительскую реализацию

                // Очищаем ячейку, если она пустая
                if (empty || item == null) {
                    getStyleClass().remove("category-tree-item");
                    setText(null);
                    setGraphic(null);
                    return;
                }

                CheckBoxTreeItem<String> treeItem = (CheckBoxTreeItem<String>) getTreeItem();

                // Проверяем, является ли этот узел категорией (т.е. его нет в нашей карте файлов)
                if (!itemToPathMap.containsKey(treeItem)) {
                    // ЭТО КАТЕГОРИЯ
                    getStyleClass().add("category-tree-item"); // Применяем CSS-стиль "bubble"
                    setGraphic(null); // Прячем чекбокс
                    setText(item); // Отображаем чистый текст
                } else {
                    // ЭТО ФАЙЛ
                    getStyleClass().remove("category-tree-item");
                    // Текст и чекбокс уже установлены вызовом super.updateItem(),
                    // так что здесь ничего делать не нужно.
                }
            }
        });
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void initData(List<Path> allFiles, Set<Path> initiallySelected, Path rootPath) {
        CheckBoxTreeItem<String> rootItem = new CheckBoxTreeItem<>("Files");
        fileTreeView.setRoot(rootItem);
        fileTreeView.setShowRoot(false);

        Map<String, List<Path>> groupedByExtension = allFiles.stream()
                .collect(Collectors.groupingBy(this::getFileExtension, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<Path>> entry : groupedByExtension.entrySet()) {
            String extension = entry.getKey();
            List<Path> filesInGroup = entry.getValue();

            // Создаем узел-категорию. БЕЗ ХАКОВ С ТЕКСТОМ.
            String categoryName = extension + " (" + filesInGroup.size() + " files)";
            CheckBoxTreeItem<String> categoryItem = new CheckBoxTreeItem<>(categoryName);
            categoryItem.setExpanded(true);
            rootItem.getChildren().add(categoryItem);

            for (Path file : filesInGroup) {
                String displayPath = rootPath.relativize(file).toString();
                CheckBoxTreeItem<String> fileItem = new CheckBoxTreeItem<>(displayPath);
                fileItem.setSelected(initiallySelected.contains(file));
                categoryItem.getChildren().add(fileItem);

                // Добавляем в карту ТОЛЬКО узлы-файлы
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

    @FXML private void handleSelectAll() { itemToPathMap.keySet().forEach(item -> item.setSelected(true)); }
    @FXML private void handleDeselectAll() { itemToPathMap.keySet().forEach(item -> item.setSelected(false)); }
    @FXML private void handleConfirm() {
        Set<Path> selectedFiles = new HashSet<>();
        itemToPathMap.forEach((item, path) -> {
            if (item.isSelected()) {
                selectedFiles.add(path);
            }
        });
        result = Optional.of(selectedFiles);
        dialogStage.close();
    }
    @FXML private void handleCancel() {
        result = Optional.empty();
        dialogStage.close();
    }
    public Optional<Set<Path>> getSelectedFiles() { return result; }
}