package TartarusCore.TxtConverter;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class SelectionController {

    private enum ViewMode {
        BY_TYPE("По типам (Расширения)"),
        BY_FOLDER("По папкам (Файловая система)");

        private final String label;
        ViewMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    @FXML private TreeView<String> fileTreeView;
    @FXML private HBox titleBar;
    @FXML private ComboBox<ViewMode> viewModeComboBox;
    @FXML private Label infoLabel;

    private Stage dialogStage;
    private double xOffset = 0;
    private double yOffset = 0;

    private List<Path> allFiles;
    private Set<Path> selectedFilesSet;
    private Path rootPath;

    // Маппинг для связывания визуальных элементов с реальными файлами
    private final Map<CheckBoxTreeItem<String>, Path> activeTreeItemToPathMap = new HashMap<>();

    // Флаг для предотвращения бесконечных циклов событий (когда родитель меняет дитя, а дитя - родителя)
    private boolean isUpdatingProgrammatically = false;

    private Optional<Set<Path>> result = Optional.empty();

    @FXML
    public void initialize() {
        setupWindowDrag();
        setupTreeView();
        setupViewModeCombo();
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void initData(List<Path> allFiles, Set<Path> initiallySelected, Path rootPath) {
        this.rootPath = rootPath.toAbsolutePath().normalize();
        this.allFiles = allFiles.stream()
                .map(p -> p.toAbsolutePath().normalize())
                .collect(Collectors.toList());

        this.selectedFilesSet = new HashSet<>();
        for (Path p : initiallySelected) {
            this.selectedFilesSet.add(p.toAbsolutePath().normalize());
        }

        viewModeComboBox.getSelectionModel().select(ViewMode.BY_TYPE);

        if (fileTreeView.getRoot() == null) {
            refreshTree();
        }
        updateInfoLabel();
    }

    private void setupTreeView() {
        fileTreeView.setCellFactory(tv -> new CheckBoxTreeCell<String>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    getStyleClass().remove("category-tree-item");
                } else {
                    CheckBoxTreeItem<String> treeItem = (CheckBoxTreeItem<String>) getTreeItem();
                    boolean isFile = activeTreeItemToPathMap.containsKey(treeItem);

                    if (!isFile) {
                        if (!getStyleClass().contains("category-tree-item")) {
                            getStyleClass().add("category-tree-item");
                        }
                    } else {
                        getStyleClass().remove("category-tree-item");
                    }
                }
            }
        });
    }

    private void setupViewModeCombo() {
        viewModeComboBox.getItems().addAll(ViewMode.values());
        viewModeComboBox.setOnAction(e -> {
            snapshotSelection();
            refreshTree();
        });
    }

    private void snapshotSelection() {
        if (activeTreeItemToPathMap.isEmpty()) return;
        // Обновление selectedFilesSet происходит в реальном времени через listeners,
        // но на всякий случай синхронизируем перед сменой вида
        updateInfoLabel();
    }

    private void refreshTree() {
        ViewMode mode = viewModeComboBox.getValue();
        if (mode == null) return;

        activeTreeItemToPathMap.clear();

        // Скрытый корень
        CheckBoxTreeItem<String> rootItem = new CheckBoxTreeItem<>("Root");
        rootItem.setExpanded(true);
        rootItem.setIndependent(true); // Отключаем стандартную логику JavaFX

        try {
            if (mode == ViewMode.BY_TYPE) {
                buildTypeTree(rootItem);
            } else {
                buildDirectoryTree(rootItem);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // После построения дерева нужно один раз прогнать пересчет состояний снизу вверх,
        // чтобы папки загорелись, если файлы внутри выбраны
        recalculateStructure(rootItem);

        fileTreeView.setRoot(rootItem);
        fileTreeView.setShowRoot(false);
    }

    // --- ПОСТРОЕНИЕ: ПО ТИПАМ ---
    private void buildTypeTree(CheckBoxTreeItem<String> rootItem) {
        Map<String, List<Path>> grouped = allFiles.stream()
                .collect(Collectors.groupingBy(this::getFileExtension, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<Path>> entry : grouped.entrySet()) {
            String ext = entry.getKey();
            List<Path> files = entry.getValue();

            CheckBoxTreeItem<String> categoryItem = createItem(ext + " (" + files.size() + ")", null);
            categoryItem.setExpanded(true);
            rootItem.getChildren().add(categoryItem);

            for (Path file : files) {
                String name = rootPath.relativize(file).toString();
                CheckBoxTreeItem<String> fileItem = createItem(name, file);
                categoryItem.getChildren().add(fileItem);
            }
        }
    }

    // --- ПОСТРОЕНИЕ: ПО ПАПКАМ ---
    private void buildDirectoryTree(CheckBoxTreeItem<String> rootItem) {
        Map<String, CheckBoxTreeItem<String>> folderCache = new HashMap<>();

        for (Path file : allFiles) {
            Path relative = rootPath.relativize(file);
            Path parent = relative.getParent();

            CheckBoxTreeItem<String> currentDirNode = rootItem;

            if (parent != null) {
                StringBuilder pathBuilder = new StringBuilder();
                for (Path part : parent) {
                    if (pathBuilder.length() > 0) pathBuilder.append("/");
                    pathBuilder.append(part.toString());
                    String currentKey = pathBuilder.toString();

                    if (folderCache.containsKey(currentKey)) {
                        currentDirNode = folderCache.get(currentKey);
                    } else {
                        CheckBoxTreeItem<String> newFolder = createItem(part.toString(), null);
                        newFolder.setExpanded(true);
                        currentDirNode.getChildren().add(newFolder);
                        folderCache.put(currentKey, newFolder);
                        currentDirNode = newFolder;
                    }
                }
            }

            CheckBoxTreeItem<String> fileItem = createItem(file.getFileName().toString(), file);
            currentDirNode.getChildren().add(fileItem);
        }
    }

    // --- УНИВЕРСАЛЬНОЕ СОЗДАНИЕ ЭЛЕМЕНТА С ЛОГИКОЙ ---
    private CheckBoxTreeItem<String> createItem(String label, Path file) {
        CheckBoxTreeItem<String> item = new CheckBoxTreeItem<>(label);
        item.setIndependent(true); // ВАЖНО: Мы управляем логикой сами

        if (file != null) {
            // Это файл
            boolean isSelected = selectedFilesSet.contains(file);
            item.setSelected(isSelected);
            activeTreeItemToPathMap.put(item, file);
        }

        // Слушатель изменений
        item.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (isUpdatingProgrammatically) return;

            try {
                isUpdatingProgrammatically = true;

                // 1. Если это файл - обновляем Set
                if (file != null) {
                    if (newVal) selectedFilesSet.add(file);
                    else selectedFilesSet.remove(file);
                    updateInfoLabel();
                }

                // 2. Логика СВЕРХУ-ВНИЗ (Родитель -> Дети)
                // Если мы кликнули папку, применяем состояние ко всем детям
                if (!item.isLeaf()) {
                    setChildrenStateRecursively(item, newVal);
                }

                // 3. Логика СНИЗУ-ВВЕРХ (Ребенок -> Родитель)
                // Обновляем состояние родителя на основе соседей
                if (item.getParent() != null) {
                    updateParentState((CheckBoxTreeItem<String>) item.getParent());
                }

            } finally {
                isUpdatingProgrammatically = false;
            }
        });

        // Слушатель для indeterminate состояния (нужен для корректной обработки кликов по "минусу")
        item.indeterminateProperty().addListener((obs, oldVal, newVal) -> {
            if (isUpdatingProgrammatically) return;
            // Обычно здесь ничего делать не нужно, JavaFX сам переключает на selected/unselected при клике
        });

        return item;
    }

    // --- РЕКУРСИВНЫЕ ОПЕРАЦИИ ---

    // Установить состояние всем потомкам
    private void setChildrenStateRecursively(CheckBoxTreeItem<String> item, boolean isSelected) {
        for (TreeItem<String> child : item.getChildren()) {
            CheckBoxTreeItem<String> cbChild = (CheckBoxTreeItem<String>) child;
            cbChild.setIndeterminate(false);
            cbChild.setSelected(isSelected); // Триггернет слушатель ребенка, но рекурсия вверх будет остановлена флагом (частично) или проверкой

            // Если это файл - обновляем сет (так как слушатель ребенка может быть заблокирован флагом в сложном кейсе, 
            // но в данном коде флаг один на всё, поэтому рекурсивный вызов метода setChildrenStateRecursively безопасен, 
            // так как он вызывается внутри блока isUpdatingProgrammatically основного триггера).
            // НО! Листенеры детей НЕ сработают, потому что isUpdatingProgrammatically = true.
            // Поэтому обновляем данные файлов вручную здесь:
            if (activeTreeItemToPathMap.containsKey(cbChild)) {
                Path p = activeTreeItemToPathMap.get(cbChild);
                if (isSelected) selectedFilesSet.add(p);
                else selectedFilesSet.remove(p);
            }

            if (!cbChild.isLeaf()) {
                setChildrenStateRecursively(cbChild, isSelected);
            }
        }
        updateInfoLabel();
    }

    // Обновить состояние родителя на основе детей
    private void updateParentState(CheckBoxTreeItem<String> parent) {
        if (parent == null || parent == fileTreeView.getRoot()) return;

        boolean allSelected = true;
        boolean allUnselected = true;

        for (TreeItem<String> child : parent.getChildren()) {
            CheckBoxTreeItem<String> cbChild = (CheckBoxTreeItem<String>) child;

            if (cbChild.isIndeterminate()) {
                allSelected = false;
                allUnselected = false;
                break;
            }

            if (cbChild.isSelected()) {
                allUnselected = false;
            } else {
                allSelected = false;
            }
        }

        if (allSelected) {
            parent.setIndeterminate(false);
            parent.setSelected(true);
        } else if (allUnselected) {
            parent.setIndeterminate(false);
            parent.setSelected(false);
        } else {
            parent.setIndeterminate(true);
            parent.setSelected(false); // Визуально галочка снята, но стоит минус
        }

        // Идем дальше вверх
        updateParentState((CheckBoxTreeItem<String>) parent.getParent());
    }

    // Начальный проход после построения дерева, чтобы выставить статусы папкам
    private void recalculateStructure(CheckBoxTreeItem<String> item) {
        if (item.isLeaf()) return;

        for (TreeItem<String> child : item.getChildren()) {
            recalculateStructure((CheckBoxTreeItem<String>) child);
        }

        // После того как дети посчитались, считаем себя (если мы не root)
        if (item.getParent() != null) { // Не вызываем для самого root
            // Логика идентична updateParentState, но применяется к "себе" на основе детей
            boolean allSelected = true;
            boolean allUnselected = true;
            for (TreeItem<String> child : item.getChildren()) {
                CheckBoxTreeItem<String> cbChild = (CheckBoxTreeItem<String>) child;
                if (cbChild.isIndeterminate()) {
                    allSelected = false; allUnselected = false; break;
                }
                if (cbChild.isSelected()) allUnselected = false;
                else allSelected = false;
            }

            isUpdatingProgrammatically = true;
            if (allSelected) {
                item.setIndeterminate(false);
                item.setSelected(true);
            } else if (allUnselected) {
                item.setIndeterminate(false);
                item.setSelected(false);
            } else {
                item.setIndeterminate(true);
                item.setSelected(false);
            }
            isUpdatingProgrammatically = false;
        }
    }

    private String getFileExtension(Path path) {
        String name = path.getFileName().toString();
        int lastIndexOf = name.lastIndexOf(".");
        return (lastIndexOf == -1) ? "No Extension" : name.substring(lastIndexOf);
    }

    private void updateInfoLabel() {
        if (selectedFilesSet != null) {
            infoLabel.setText("Выбрано: " + selectedFilesSet.size() + " из " + allFiles.size());
        }
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
            isUpdatingProgrammatically = true;
            CheckBoxTreeItem<String> root = (CheckBoxTreeItem<String>) fileTreeView.getRoot();
            // Ставим галочку на рут, рекурсия пойдет вниз через наш ручной метод, 
            // так как листенера на рут нет (он скрыт), вызовем вручную
            setChildrenStateRecursively(root, true);
            isUpdatingProgrammatically = false;
            // Обновляем визуал рута (хотя он скрыт, но для порядка)
            recalculateStructure(root);
        }
    }

    @FXML
    private void handleDeselectAll() {
        if (fileTreeView.getRoot() != null) {
            isUpdatingProgrammatically = true;
            CheckBoxTreeItem<String> root = (CheckBoxTreeItem<String>) fileTreeView.getRoot();
            setChildrenStateRecursively(root, false);
            isUpdatingProgrammatically = false;
            recalculateStructure(root);
        }
    }

    @FXML
    private void handleConfirm() {
        result = Optional.of(new HashSet<>(selectedFilesSet));
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