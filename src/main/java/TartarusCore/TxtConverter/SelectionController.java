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

        // Настраиваем фабрику ячеек для отображения чекбоксов
        fileTreeView.setCellFactory(tv -> new CheckBoxTreeCell<String>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); // Важно вызвать родителя

                if (empty || item == null) {
                    getStyleClass().remove("category-tree-item");
                    setText(null);
                    setGraphic(null);
                    return;
                }

                CheckBoxTreeItem<String> treeItem = (CheckBoxTreeItem<String>) getTreeItem();

                // Проверяем, является ли элемент категорией (его нет в мапе путей)
                if (!itemToPathMap.containsKey(treeItem)) {
                    // Это категория
                    if (!getStyleClass().contains("category-tree-item")) {
                        getStyleClass().add("category-tree-item");
                    }
                    // МЫ УБРАЛИ setGraphic(null), чтобы чекбокс остался видимым.
                    // CheckBoxTreeItem автоматически обрабатывает логику выбора детей.
                } else {
                    // Это файл
                    getStyleClass().remove("category-tree-item");
                }
            }
        });
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void initData(List<Path> allFiles, Set<Path> initiallySelected, Path rootPath) {
        CheckBoxTreeItem<String> rootItem = new CheckBoxTreeItem<>("Files");
        // Корневой элемент должен быть "независимым" или раскрытым, но так как мы его скрываем, это не важно.
        // Важно, чтобы категории работали правильно.
        rootItem.setExpanded(true);

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
            categoryItem.setExpanded(true); // Разворачиваем категорию по умолчанию

            rootItem.getChildren().add(categoryItem);

            // Добавляем файлы в категорию
            for (Path file : filesInGroup) {
                String displayPath = rootPath.relativize(file).toString();
                CheckBoxTreeItem<String> fileItem = new CheckBoxTreeItem<>(displayPath);

                // Устанавливаем начальное состояние.
                // ВАЖНО: CheckBoxTreeItem автоматически обновит состояние родителя (categoryItem),
                // если мы добавляем в него детей с уже установленным состоянием.
                fileItem.setSelected(initiallySelected.contains(file));

                categoryItem.getChildren().add(fileItem);

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
        // Выбираем корневой элемент, это должно каскадно выбрать всё, если root связан.
        // Но так как root скрыт и мы работаем с itemToPathMap, надежнее пройтись по root children.
        if (fileTreeView.getRoot() != null) {
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

        // Проходим только по мапе файлов.
        // Состояние категорий нас не волнует при сборе, оно служит только для UI удобства.
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