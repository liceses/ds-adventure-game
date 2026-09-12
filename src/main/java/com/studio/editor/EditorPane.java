package com.studio.editor;

import com.studio.model.GameOption;
import com.studio.model.GameProject;
import com.studio.model.GameScene;
import com.studio.model.NodeType;
import com.studio.model.StoryNode;
import com.studio.parser.ParserException;
import com.studio.parser.ScriptParser;
import com.studio.parser.ScriptWriter;
import com.studio.reader.ReaderView;
import com.studio.ui.Ui;
import com.studio.util.AppConfig;
import com.studio.util.Logs;
import com.studio.util.MapAssets;
import com.studio.util.MapTemplateFactory;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCombination;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 编辑器主面板（MVC 中的 View+Controller 中枢，实现 {@link EditorHub}）。
 *
 * 布局：
 * <pre>
 *   ┌─ 菜单栏（文件/编辑/场景/视图/运行/帮助）─────┐
 *   ├─ 场景工具条（场景下拉/增删改/预览/缩放）──────┤
 *   ├─ 左：层级树 │ 中：画布(悬浮工具箱) │ 右：检查器 ├
 *   ├─ 状态栏（消息 / 坐标 / 缩放 / 保存状态）───────┤
 *   └──────────────────────────────────────────┘
 * </pre>
 */
public class EditorPane extends BorderPane implements EditorHub {

    private final Stage stage;
    private final AppConfig config;

    // ---- 模型状态 ----
    private GameProject project;
    private GameScene currentScene;
    private StoryNode selectedNode;
    private boolean dirty = false;

    /**
     * 「新增节点模板」：右键“添加节点”/工具箱生成新节点时按它复制初值
     * （类型、尺寸、文本、信号、槽…）。默认是普通文本节点，可在右侧检查器里改。
     */
    private StoryNode newNodeTemplate = NodeType.createDefault(NodeType.TEXT.code(), 0, 0);

    // ---- 视图 ----
    private final EditorCanvas canvas = new EditorCanvas(this);
    private final EditorPanels.SceneTreePanel treePanel = new EditorPanels.SceneTreePanel(this);
    private final EditorPanels.InspectorPanel inspector = new EditorPanels.InspectorPanel(this);

    private final Label mapTitle = new Label();
    private final Label dirtyFlag = new Label();
    private final ComboBox<String> sceneBox = new ComboBox<>();
    private final Label statusMsg = new Label();
    private final Label statusCoords = new Label();
    private final Label statusZoom = new Label();
    private final StackPane emptyOverlay = new StackPane();

    private final CheckMenuItem gridItem = new CheckMenuItem("显示网格");

    // =====================================================================
    // 撤销 / 恢复（栈结构：栈顶 = 最近一次操作“之前”的工程快照）
    // =====================================================================

    /** 最多保留多少步历史（避免大工程占用过多内存） */
    private static final int MAX_UNDO = 80;

    private final java.util.ArrayDeque<Snapshot> undoStack = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<Snapshot> redoStack = new java.util.ArrayDeque<>();
    /** 恢复快照期间为 true */
    private boolean restoring = false;
    private MenuItem undoItem;
    private MenuItem redoItem;

    /** 一次可撤销操作的状态快照（含当前场景名与选中节点，恢复后视图能回到原处） */
    private record Snapshot(String label, GameProject project, String sceneName, String nodeId) { }
    private boolean skipSceneBoxListener = false;
    private boolean shortcutsInstalled = false;

    // =====================================================================
    // 构造与界面搭建
    // =====================================================================

    public EditorPane(Stage stage, AppConfig config) {
        this.stage = stage;
        this.config = config == null ? AppConfig.loadDefault() : config;

        MenuBar menuBar = buildMenuBar();
        HBox sceneBar = buildSceneBar();
        setTop(new VBox(menuBar, sceneBar));

        SplitPane split = new SplitPane();
        split.getItems().addAll(treePanel, buildCenter(), inspector);
        split.setDividerPositions(0.19, 0.79);
        setCenter(split);

        setBottom(buildStatusBar());
        refreshAll(true);

        // 场景挂上后安装全局快捷键（Delete 删节点等；避免被菜单/焦点吞掉）
        sceneProperty().addListener((o, a, sc) -> {
            if (sc != null) installShortcuts(sc);
        });
    }

    /** 全局快捷键：Delete 删除选中节点、Ctrl+S/O/R/E/D 与缩放 */
    private void installShortcuts(Scene sc) {
        if (shortcutsInstalled) return;
        shortcutsInstalled = true;
        // 点击界面任意位置都收起右键菜单（以前必须点一下画布才会消失）
        sc.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            canvas.hideOpenMenu();
            treePanel.hideOpenMenu();
        });
        sc.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            javafx.scene.Node focus = sc.getFocusOwner();
            boolean typing = focus instanceof javafx.scene.control.TextInputControl;
            if (e.getCode() == javafx.scene.input.KeyCode.DELETE && !typing) {
                deleteSelectedNode();
                e.consume();
                return;
            }
            if (!e.isControlDown()) return;
            // 在输入框里打字时，Ctrl+Z/Y 交给输入框自己的撤销，不抢
            if (!typing) {
                switch (e.getCode()) {
                    case Z -> {
                        if (e.isShiftDown()) redo(); else undo();
                        e.consume();
                        return;
                    }
                    case Y -> { redo(); e.consume(); return; }
                    case UP -> { moveSelectedLayer(+1); e.consume(); return; }
                    case DOWN -> { moveSelectedLayer(-1); e.consume(); return; }
                    default -> { }
                }
            }
            switch (e.getCode()) {
                case S -> { saveMap(); e.consume(); }
                case O -> { openMapDialog(); e.consume(); }
                case R -> { previewInPlayer(); e.consume(); }
                case E -> {
                    if (selectedNode != null) openNodeDialog(selectedNode);
                    e.consume();
                }
                case D -> { duplicateSelected(); e.consume(); }
                case EQUALS, PLUS, ADD -> { canvas.zoomIn(); e.consume(); }
                case MINUS, SUBTRACT -> { canvas.zoomOut(); e.consume(); }
                case DIGIT0, NUMPAD0 -> { canvas.fitZoom(); e.consume(); }
                default -> { }
            }
        });
    }

    // =====================================================================
    // 菜单栏
    // =====================================================================

    private MenuBar buildMenuBar() {
        // ---------- 文件 ----------
        Menu fileMenu = new Menu("文件(F)");
        MenuItem open = item("打开地图文件夹…", e -> openMapDialog());
        open.setAccelerator(KeyCombination.keyCombination("Ctrl+O"));

        MenuItem fresh = item("新建地图…", e -> createMapDialog(false));
        MenuItem demo = item("新建示例地图（含插件演示）…", e -> createMapDialog(true));
        MenuItem branch = item("打开分支剧情示例（含 2048）…", e -> openBranchDemoMap());
        MenuItem saveRoom = item("打开存档演示地图（3 槽存档台）…", e -> openSaveRoomDemoMap());
        MenuItem signalLab = item("打开信号演示地图（信号/槽+逻辑层）…", e -> openSignalLabMap());
        MenuItem varDemo = item("打开存档变量演示地图（变量/表达式/插件）…", e -> openVarDemoMap());
        MenuItem logicDemo = item("打开逻辑门演示地图（双开关控制三盏灯）…", e -> openLogicGateDemoMap());
        MenuItem breakout = item("打开打砖块演示地图（breakout 插件）…", e -> openBreakoutDemoMap());

        MenuItem save = item("保存地图 (Ctrl+S)", e -> saveMap());
        save.setAccelerator(KeyCombination.keyCombination("Ctrl+S"));

        MenuItem export = item("导出为独立文件夹…", e -> exportMap());
        MenuItem del = item("删除当前地图…", e -> deleteMap());

        MenuItem exit = item("退出", e -> requestExit());
        fileMenu.getItems().addAll(open, fresh, demo, branch, saveRoom, signalLab, varDemo, logicDemo, breakout,
                new SeparatorMenuItem(), save, export, del, new SeparatorMenuItem(), exit);

        // ---------- 编辑 ----------
        Menu editMenu = new Menu("编辑(E)");
        MenuItem editNode = item("编辑选中节点属性…", e -> {
            if (selectedNode != null) openNodeDialog(selectedNode);
            else notify("未选中节点");
        });
        editNode.setAccelerator(KeyCombination.keyCombination("Ctrl+E"));
        MenuItem dupNode = item("复制选中节点", e -> duplicateSelected());
        MenuItem delNode = item("删除选中节点 (Del)", e -> deleteSelectedNode());
        delNode.setAccelerator(KeyCombination.keyCombination("Delete"));
        MenuItem layerUp = item("⬆ 上移一层（向顶层）", e -> moveSelectedLayer(+1));
        layerUp.setAccelerator(KeyCombination.keyCombination("Ctrl+Up"));
        MenuItem layerDown = item("⬇ 下移一层（向底层）", e -> moveSelectedLayer(-1));
        layerDown.setAccelerator(KeyCombination.keyCombination("Ctrl+Down"));
        undoItem = item("撤销 (Ctrl+Z)", e -> undo());
        undoItem.setAccelerator(KeyCombination.keyCombination("Ctrl+Z"));
        redoItem = item("重做 (Ctrl+Y)", e -> redo());
        redoItem.setAccelerator(KeyCombination.keyCombination("Ctrl+Y"));
        updateUndoState();
        editMenu.getItems().addAll(undoItem, redoItem, new SeparatorMenuItem(),
                editNode, dupNode, delNode, new SeparatorMenuItem(), layerUp, layerDown);

        // ---------- 场景 ----------
        Menu sceneMenu = new Menu("场景(S)");
        sceneMenu.getItems().addAll(
                item("新增场景…", e -> createSceneViaTree()),
                item("重命名当前场景…", e -> {
                    if (currentScene != null) renameSceneViaTree(currentScene);
                }),
                item("删除当前场景…", e -> deleteSceneViaTree()),
                new SeparatorMenuItem(),
                item("地图全局设置 [option]…", e -> NodeDialogs.showOptionDialog(this)),
                item("🧩 场景属性（属性 / 节点 / 信号槽）…", e -> SceneInspectorDialog.show(this, currentScene)));

        // ---------- 视图 ----------
        Menu viewMenu = new Menu("视图(V)");
        MenuItem zi = item("放大", e -> canvas.zoomIn());
        zi.setAccelerator(KeyCombination.keyCombination("Ctrl+="));
        MenuItem zo = item("缩小", e -> canvas.zoomOut());
        zo.setAccelerator(KeyCombination.keyCombination("Ctrl+-"));
        MenuItem zf = item("适应窗口", e -> canvas.fitZoom());
        zf.setAccelerator(KeyCombination.keyCombination("Ctrl+0"));
        gridItem.setSelected(true);
        gridItem.setOnAction(e -> canvas.setGridVisible(gridItem.isSelected()));
        CheckMenuItem toolboxItem = new CheckMenuItem("显示工具箱");
        toolboxItem.setSelected(canvas.isToolboxVisible());
        toolboxItem.setOnAction(e -> canvas.setToolboxVisible(toolboxItem.isSelected()));
        viewMenu.getItems().addAll(zi, zo, zf, new SeparatorMenuItem(), gridItem, toolboxItem);

        // ---------- 运行 ----------
        Menu runMenu = new Menu("运行(R)");
        MenuItem preview = item("在播放器中测试 ▶", e -> previewInPlayer());
        preview.setAccelerator(KeyCombination.keyCombination("Ctrl+R"));
        runMenu.getItems().add(preview);

        // ---------- 帮助 ----------
        Menu helpMenu = new Menu("帮助(H)");
        helpMenu.getItems().addAll(
                item("关于…", e -> Ui.info(stage, "关于 剧情编辑器",
                        "剧情编辑器 Studio / 播放器 Player v1.0\n"
                        + "JDK 21 + JavaFX 21 · 纯 Java 无 FXML\n\n"
                        + "地图 = 文件夹(scenario.txt + resources/)，\n"
                        + "支持 [option]/[场景]/{节点} 双向读写与插件化事件。")),
                item("📖 使用帮助（脚本 / 样式 / 信号槽 / 快捷键）…", e -> HelpDialogs.show(stage)),
                item("脚本语法速查…", e -> Ui.info(stage, "scenario.txt 语法速查",
                        syntaxHelp())));

        MenuBar bar = new MenuBar(fileMenu, editMenu, sceneMenu, viewMenu, runMenu, helpMenu);
        return bar;
    }

    private static MenuItem item(String text, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        MenuItem i = new MenuItem(text);
        i.setOnAction(action);
        return i;
    }

    // =====================================================================
    // 撤销 / 恢复
    // =====================================================================

    /**
     * 记录一步可撤销操作。<b>必须在真正修改工程之前调用</b>：
     * 它把“当前状态”压入撤销栈，之后用户按 Ctrl+Z 就能回到这里。
     *
     * @param label 操作名（显示在菜单与提示里，如“添加节点”“移动节点”）
     */
    @Override
    public void pushUndo(String label) {
        if (restoring || project == null) return;
        undoStack.push(new Snapshot(label, project.copy(),
                currentScene == null ? null : currentScene.getName(),
                selectedNode == null ? null : selectedNode.getId()));
        while (undoStack.size() > MAX_UNDO) undoStack.removeLast();
        redoStack.clear();
        updateUndoState();
    }

    @Override
    public void undo() {
        if (project == null) return;
        if (undoStack.isEmpty()) {
            notify("没有可撤销的操作");
            return;
        }
        Snapshot s = undoStack.pop();
        redoStack.push(new Snapshot(s.label(), project.copy(), currentSceneName(), selectedNodeId()));
        restoreSnapshot(s);
        updateUndoState();
        notify("已撤销：" + s.label() + "（还可撤销 " + undoStack.size() + " 步）");
    }

    @Override
    public void redo() {
        if (project == null) return;
        if (redoStack.isEmpty()) {
            notify("没有可重做的操作");
            return;
        }
        Snapshot s = redoStack.pop();
        undoStack.push(new Snapshot(s.label(), project.copy(), currentSceneName(), selectedNodeId()));
        restoreSnapshot(s);
        updateUndoState();
        notify("已重做：" + s.label());
    }

    private String currentSceneName() { return currentScene == null ? null : currentScene.getName(); }

    private String selectedNodeId() { return selectedNode == null ? null : selectedNode.getId(); }

    /** 把快照内容写回当前工程（保持 project / option 对象引用不变，界面各处都在用它们） */
    private void restoreSnapshot(Snapshot s) {
        restoring = true;
        try {
            project.option().copyFrom(s.project().option());
            project.scenes().clear();
            for (java.util.Map.Entry<String, GameScene> e : s.project().scenes().entrySet()) {
                project.scenes().put(e.getKey(), e.getValue().copy()); // 再拷一份，快照可重复使用
            }
            GameScene sc = s.sceneName() == null ? null : project.getScene(s.sceneName());
            if (sc == null) sc = project.firstScene();
            currentScene = sc;
            selectedNode = null;
            if (sc != null && s.nodeId() != null) {
                for (StoryNode n : sc.nodes()) {
                    if (s.nodeId().equals(n.getId())) { selectedNode = n; break; }
                }
            }
            sceneStructureChanged();
            if (sc != null) switchScene(sc.getName());
            canvas.select(selectedNode);
            setDirty();
        } finally {
            restoring = false;
        }
    }

    private void updateUndoState() {
        if (undoItem != null) {
            undoItem.setDisable(undoStack.isEmpty());
            undoItem.setText(undoStack.isEmpty() ? "撤销 (Ctrl+Z)"
                    : "撤销 " + undoStack.peek().label() + " (Ctrl+Z)");
        }
        if (redoItem != null) {
            redoItem.setDisable(redoStack.isEmpty());
            redoItem.setText(redoStack.isEmpty() ? "重做 (Ctrl+Y)"
                    : "重做 " + redoStack.peek().label() + " (Ctrl+Y)");
        }
    }

    /** 选中节点上移/下移一层（编辑菜单与 Ctrl+↑ / Ctrl+↓） */
    private void moveSelectedLayer(int delta) {
        if (currentScene == null || selectedNode == null) {
            notify("未选中节点");
            return;
        }
        int i = currentScene.nodes().indexOf(selectedNode);
        if (i < 0) return;
        int target = i + delta;
        if (target < 0 || target >= currentScene.nodes().size()) {
            notify(delta > 0 ? "已经是最上层了" : "已经是最底层了");
            return;
        }
        pushUndo(delta > 0 ? "上移一层" : "下移一层");
        currentScene.moveTo(selectedNode, target);
        nodesLayerChanged();
    }

    private static String syntaxHelp() {
        return """
                # 注释行
                [option]              ← 全局设置段
                initialScene = Start
                background = #0d0f1c
                volume = 0.8
                savevar = 金币 | int | 0   ← 存档变量（可多行，编辑器里可增删）

                [Start]               ← 场景段（[场景名]）
                event = minesweeper   ← 场景级属性：进入场景触发的插件
                {
                type = bg             ← 节点块 { }：type/x/y/path/text/style/...
                x = 0
                y = 0
                text = <<<            ← 多行文本(Heredoc)
                第一行
                <<<
                }

                节点属性速查：
                type: bg背景|char立绘|text文本|textbox文本框|name人物名|dialog对话|button按钮|music音乐
                x / y / width / height / index(层级，0=最底层)
                path(图片) / audio(音频) / text(富文本)
                multiline(文本框多行) / bind(文本框绑定的存档变量)
                style(内联CSS) / event(插件) / action(按钮动作) / target(跳转场景)
                visible / fontSize / align / opacity
                """;
    }

    // =====================================================================
    // 场景工具条
    // =====================================================================

    private HBox buildSceneBar() {
        HBox bar = new HBox(8);
        bar.getStyleClass().add("scene-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        Label mapLabel = new Label("地图:");
        mapLabel.getStyleClass().add("field-label");
        dirtyFlag.setText("");
        dirtyFlag.getStyleClass().add("dirty-flag");
        mapTitle.setText("（未打开）");

        Label sceneLabel = new Label("场景:");
        sceneLabel.getStyleClass().add("field-label");

        sceneBox.setPrefWidth(190);
        sceneBox.setOnAction(e -> {
            if (skipSceneBoxListener) return;
            String name = sceneBox.getSelectionModel().getSelectedItem();
            if (name != null && !name.equals(currentScene == null ? null : currentScene.getName())) {
                switchScene(name);
            }
        });

        Button addScene = new Button("＋");
        addScene.getStyleClass().add("tool-button");
        addScene.setOnAction(e -> createSceneViaTree());
        addScene.setTooltip(new javafx.scene.control.Tooltip("新增场景"));

        Button renameScene = new Button("✏️");
        renameScene.getStyleClass().add("tool-button");
        renameScene.setOnAction(e -> {
            if (currentScene != null) renameSceneViaTree(currentScene);
        });
        renameScene.setTooltip(new javafx.scene.control.Tooltip("重命名当前场景"));

        Button delScene = new Button("－");
        delScene.getStyleClass().add("tool-button");
        delScene.setOnAction(e -> deleteSceneViaTree());
        delScene.setTooltip(new javafx.scene.control.Tooltip("删除当前场景"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button play = new Button("▶ 播放测试");
        play.getStyleClass().add("tool-button");
        play.setOnAction(e -> previewInPlayer());
        play.setTooltip(new javafx.scene.control.Tooltip("用读取器打开当前地图预览 (Ctrl+R)"));

        Label zoomLabel = new Label();
        zoomLabel.getStyleClass().add("field-label");
        statusZoom.textProperty().addListener((o, a, b) ->
                zoomLabel.setText("缩放 " + b + "%"));

        bar.getChildren().addAll(mapLabel, mapTitle, dirtyFlag, new javafx.scene.control.Separator(),
                sceneLabel, sceneBox, addScene, renameScene, delScene, spacer, play, zoomLabel);
        return bar;
    }

    // =====================================================================
    // 中间区（画布 + 空状态遮罩）与状态栏
    // =====================================================================

    private StackPane buildCenter() {
        Label hint = new Label("📂 尚未打开任何地图\n\n"
                + "① 文件 → 打开地图文件夹（选择含 scenario.txt 的目录）\n"
                + "② 文件 → 新建地图（自动生成标准 AVG 模板）\n"
                + "③ 也可直接播放 maps/demo_map 示例（运行播放器模式）");
        hint.setStyle("-fx-text-fill: #9fa3c6; -fx-font-size: 15px; -fx-alignment: center;");
        hint.setWrapText(true);
        hint.setMaxWidth(480);

        Button openBtn = new Button("打开地图文件夹…");
        openBtn.getStyleClass().add("tool-button");
        Button newBtn = new Button("新建地图…");
        newBtn.getStyleClass().add("tool-button");
        openBtn.setOnAction(e -> openMapDialog());
        newBtn.setOnAction(e -> createMapDialog(false));

        VBox box = new VBox(16, hint, new HBox(12, openBtn, newBtn));
        box.setAlignment(Pos.CENTER);
        emptyOverlay.getChildren().add(box);
        emptyOverlay.setPickOnBounds(true);

        StackPane center = new StackPane();
        center.getChildren().addAll(canvas, emptyOverlay);
        return center;
    }

    private HBox buildStatusBar() {
        HBox bar = new HBox(16);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        statusMsg.setText("就绪");

        statusCoords.setText("X:-  Y:-");
        statusCoords.setPrefWidth(150);
        statusZoom.setText("缩放 -");
        statusZoom.setPrefWidth(90);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getChildren().addAll(statusMsg, spacer, statusCoords, statusZoom);
        return bar;
    }

    // =====================================================================
    // EditorHub 实现：状态与提示
    // =====================================================================

    @Override public GameProject project() { return project; }
    @Override public GameScene scene() { return currentScene; }
    @Override public StoryNode selectedNode() { return selectedNode; }

    @Override
    public void notify(String message) {
        statusMsg.setText(message);
        Logs.info(message);
    }

    @Override
    public void setStatusCoords(double x, double y) {
        statusCoords.setText("X:" + (int) x + "  Y:" + (int) y);
    }

    @Override
    public void setStatusZoom(double zoom) {
        statusZoom.setText((int) Math.round(zoom) + "%");
    }

    @Override
    public void setDirty() {
        dirty = true;
        dirtyFlag.setText(" ●未保存");
    }

    private void markSaved() {
        dirty = false;
        dirtyFlag.setText("");
    }

    // =====================================================================
    // EditorHub：选择 / 场景切换
    // =====================================================================

    @Override
    public void selectNode(StoryNode node) {
        selectedNode = node;
        if (node != null && currentScene != null && !currentScene.contains(node)) {
            // 保证选中节点属于当前场景（层级树跨场景选择时）
            for (GameScene s : project.scenes().values()) {
                if (s.contains(node)) {
                    switchScene(s.getName());
                    break;
                }
            }
        }
        canvas.select(node);
        refreshInspector();
        if (node != null) notify("已选中 " + node.getType().display() + " @" + node.getId());
    }

    @Override
    public void refreshInspector() {
        inspector.refresh();
    }

    @Override
    public void switchScene(String name) {
        if (project == null || !project.hasScene(name)) return;
        currentScene = project.getScene(name);
        if (selectedNode != null && !currentScene.contains(selectedNode)) {
            selectedNode = null;
        }
        canvas.showScene(currentScene);
        treePanel.refresh(currentScene);
        updateSceneBox();
        refreshInspector();
        notify("切换到场景 [" + name + "]（节点数: " + currentScene.nodes().size() + "）");
    }

    @Override
    public void sceneStructureChanged() {
        treePanel.refresh(currentScene);
        updateSceneBox();
        refreshInspector();
    }

    private void updateSceneBox() {
        skipSceneBoxListener = true;
        try {
            sceneBox.getItems().clear();
            if (project != null) {
                sceneBox.getItems().addAll(project.scenes().keySet());
                if (currentScene != null) sceneBox.getSelectionModel().select(currentScene.getName());
            }
        } finally {
            skipSceneBoxListener = false;
        }
    }

    // =====================================================================
    // EditorHub：节点编辑
    // =====================================================================

    @Override
    public void openNodeDialog(StoryNode node) {
        if (node == null) return;
        selectedNode = node;
        // 属性窗口是“边改边生效”的（取消时自己会还原），所以在打开前先记一步快照
        pushUndo("修改节点属性");
        NodeDialogs.showNodeDialog(this, node);
        canvas.select(node);
    }

    @Override
    public void createNodeAt(String typeCode, double x, double y) {
        if (currentScene == null || project == null) return;
        // ① 取模板并复制：新节点带着模板的状态（只影响新节点，不改动已有节点）
        StoryNode node = newNodeTemplate().copy();
        NodeType want = NodeType.from(typeCode);
        // ② 模板类型 ≠ 菜单所选类型 → 换类型，并补上该类型的默认尺寸/默认文本，
        //    避免“文本模板的尺寸”被套到立绘/背景等其它类型上
        if (node.getType() != want) {
            node.setTypeCode(want.code());
            node.setWidth(want.defaultWidth());
            node.setHeight(want.defaultHeight());
            node.setText(defaultTextOf(want));
            if (want != NodeType.TEXTBOX) {
                // “多行 / 绑定变量”是文本框专属属性，不带到其它类型上
                node.setMultiline(false);
                node.setBind("");
            }
        }
        // ③ 位置 = 右键（或拖放落点）坐标
        node.setX(x);
        node.setY(y);
        // ④ 生成当前场景内不重复的新 id
        node.setId(nextNodeId(want.display()));
        addNode(node);
    }

    /** 参考 {@link NodeType#createDefault} 的默认文本（图片/音乐类节点没有默认文字） */
    private static String defaultTextOf(NodeType t) {
        switch (t) {
            case TEXT: return "双击或右键编辑文字…";
            case TEXTBOX: return "请输入…";
            case NAME: return "角色名";
            case DIALOG: return "「在这里输入对话内容……」";
            case BUTTON: return "按钮";
            default: return "";
        }
    }

    /** 生成当前场景内不重复的节点 id：显示名_序号（如 文本框_1） */
    private String nextNodeId(String prefix) {
        int n = 1;
        String id = prefix + "_" + n;
        while (idTaken(id)) id = prefix + "_" + (++n);
        return id;
    }

    private boolean idTaken(String id) {
        if (currentScene == null) return false;
        for (StoryNode n : currentScene.nodes()) {
            if (id.equals(n.getId())) return true;
        }
        return false;
    }

    @Override
    public void addNode(StoryNode node) {
        if (currentScene == null) return;
        // 同场景 id 去重
        if (!node.getId().isBlank()) {
            String base = node.getId();
            int k = 2;
            while (currentScene.nodes().stream().anyMatch(n -> n.getId().equals(node.getId()))) {
                node.setId(base + "_" + (k++));
            }
        }
        pushUndo("添加节点");
        currentScene.addNode(node);
        selectedNode = node;
        canvas.refreshScene();
        treePanel.refresh(currentScene);
        refreshInspector();
        setDirty();
        notify("已添加 " + node.getType().display() + " 节点 @("
                + (int) node.getX() + "," + (int) node.getY() + ")");
    }

    @Override
    public void deleteNode(StoryNode node) {
        if (node == null || currentScene == null || !currentScene.contains(node)) return;
        pushUndo("删除节点");
        currentScene.removeNode(node);
        if (selectedNode == node) selectedNode = null;
        canvas.refreshScene();
        treePanel.refresh(currentScene);
        refreshInspector();
        setDirty();
        notify("已删除 " + node.getType().display() + " 节点");
    }

    @Override
    public void nodeChanged(StoryNode node) {
        if (node == null) return;
        // 尺寸变化 → 同步包装尺寸；随后刷新视觉
        canvas.refreshNodeVisual(node);
        setDirty();
    }

    // ---------- 新增节点模板 ----------

    @Override
    public StoryNode newNodeTemplate() {
        if (newNodeTemplate == null) {
            newNodeTemplate = NodeType.createDefault(NodeType.TEXT.code(), 0, 0);
        }
        return newNodeTemplate;
    }

    /**
     * 替换新增节点模板。注意：模板只是“以后新建节点”的初值来源，
     * 不属于场景内容，因此这里不标记工程为未保存（也不刷新画布）；
     * 检查器里的模板摘要由调用方（EditorPanels）自行刷新。
     */
    @Override
    public void setNewNodeTemplate(StoryNode node) {
        if (node == null) return;
        newNodeTemplate = node;
    }

    @Override
    public void nodesLayerChanged() {
        canvas.refreshScene();
        treePanel.refresh(currentScene);
        setDirty();
        notify("层级已调整（文件顺序 = 画面层级）");
    }

    @Override
    public void optionChanged() {
        canvas.refreshBackground();
        setDirty();
    }

    // =====================================================================
    // 层级树的场景动作
    // =====================================================================

    @Override
    public void createSceneViaTree() {
        if (project == null) return;
        Optional<String> name = Ui.askText(stage, "新增场景", null, "场景名称：",
                project.uniqueSceneName("新场景"));
        if (name.isEmpty() || name.get().isBlank()) return;
        pushUndo("新增场景");
        String n = project.uniqueSceneName(name.get().trim());
        project.addScene(n);
        setDirty();
        currentScene = project.getScene(n);
        sceneStructureChanged();
        switchScene(n);
        notify("新增场景 [" + n + "]");
    }

    @Override
    public void renameSceneViaTree(GameScene scene) {
        if (scene == null || project == null) return;
        Optional<String> name = Ui.askText(stage, "重命名场景", null,
                "将 \"" + scene.getName() + "\" 重命名为：", scene.getName());
        if (name.isEmpty() || name.get().isBlank()) return;
        pushUndo("重命名场景");
        if (project.renameScene(scene.getName(), name.get().trim())) {
            setDirty();
            sceneStructureChanged();
            notify("场景已重命名为 [" + name.get().trim() + "]（跳转目标同步更新）");
        } else {
            Ui.warn(stage, "重命名失败", "名称已被占用或场景不存在。");
        }
    }

    @Override
    public void deleteSceneViaTree() {
        if (project == null || currentScene == null) return;
        if (project.scenes().size() <= 1) {
            Ui.warn(stage, "无法删除", "至少需要保留一个场景。");
            return;
        }
        String name = currentScene.getName();
        if (!Ui.confirm(stage, "删除场景",
                "确定删除场景 [" + name + "] 及其全部节点吗？", "该操作不可撤销。")) {
            return;
        }
        pushUndo("删除场景");
        project.removeScene(name);
        setDirty();
        selectedNode = null;
        GameScene fallback = project.firstScene();
        currentScene = fallback;
        sceneStructureChanged();
        switchScene(fallback.getName());
        notify("已删除场景 [" + name + "]");
    }

    private void duplicateSelected() {
        if (selectedNode == null) {
            notify("未选中节点");
            return;
        }
        StoryNode c = selectedNode.copy();
        c.setX(c.getX() + 26);
        c.setY(c.getY() + 26);
        addNode(c);
    }

    private void deleteSelectedNode() {
        if (selectedNode == null) return;
        deleteNode(selectedNode);
    }

    // =====================================================================
    // 文件操作
    // =====================================================================

    /** 打开指定文件夹中的 scenario.txt */
    public void openMap(File folder) {
        if (!confirmSaveChanges()) return;
        File scenario = new File(folder, "scenario.txt");
        if (!scenario.isFile()) {
            Ui.warn(stage, "无法打开",
                    "所选文件夹中没有 scenario.txt：\n" + folder.getAbsolutePath()
                    + "\n\n请选择地图文件夹，或用 文件→新建地图 生成。");
            return;
        }
        try {
            List<String> warnings = new ArrayList<>();
            GameProject p = ScriptParser.parse(scenario, warnings);
            p.setRootDir(folder);
            project = p;
            selectedNode = null;
            GameScene init = project.initialScene();
            currentScene = init != null ? init : project.firstScene();
            markSaved();
            config.set("editor.last.map", folder.getAbsolutePath());
            config.save();
            refreshAll(false);
            if (!warnings.isEmpty()) {
                Ui.showWarnings(stage, "脚本解析提示（已按宽容模式继续）", warnings);
            }
            notify("已打开地图: " + folder.getAbsolutePath());
        } catch (ParserException e) {
            Ui.error(stage, "打开失败", e.getMessage(), null);
        }
    }

    private void openMapDialog() {
        File initial = null;
        String last = config.get("editor.last.map");
        if (last != null) {
            File f = new File(last);
            if (f.isDirectory()) initial = f;
        }
        File dir = Ui.chooseDirectory(stage, "选择地图文件夹（内含 scenario.txt）", initial);
        if (dir == null) return;
        openMap(dir);
    }

    /** 生成并打开“分支剧情 + 2048”示例地图（含剧情分支与多结局） */
    private void openBranchDemoMap() {
        try {
            File dir = new File(System.getProperty("user.dir"),
                    com.studio.util.BranchMapFactory.DEFAULT_FOLDER);
            if (!new File(dir, "scenario.txt").isFile()) {
                com.studio.util.BranchMapFactory.createMap(dir);
                notify("已生成分支示例地图: " + dir.getAbsolutePath());
            }
            openMap(dir);
        } catch (IOException e) {
            Ui.error(stage, "生成示例失败", e.getMessage(), e);
        }
    }

    /** 生成并打开“信号实验室”示例地图（信号/槽 + 逻辑层演示） */
    private void openSignalLabMap() {
        try {
            File dir = new File(System.getProperty("user.dir"),
                    com.studio.util.SignalLabMapFactory.DEFAULT_FOLDER);
            if (!new File(dir, "scenario.txt").isFile()) {
                com.studio.util.SignalLabMapFactory.createMap(dir);
                notify("已生成信号演示地图: " + dir.getAbsolutePath());
            }
            openMap(dir);
        } catch (IOException e) {
            Ui.error(stage, "生成示例失败", e.getMessage(), e);
        }
    }

    /** 生成并打开“逻辑门”示例地图（两个开关控制三盏灯：灯3 = 灯1 且 灯2） */
    private void openLogicGateDemoMap() {
        try {
            File dir = new File(System.getProperty("user.dir"),
                    com.studio.util.LogicGateDemoMapFactory.DEFAULT_FOLDER);
            if (!new File(dir, "scenario.txt").isFile()) {
                com.studio.util.LogicGateDemoMapFactory.createMap(dir);
                notify("已生成逻辑门演示地图: " + dir.getAbsolutePath());
            }
            openMap(dir);
        } catch (IOException e) {
            Ui.error(stage, "生成示例失败", e.getMessage(), e);
        }
    }
    /** 生成并打开“存档变量 / 表达式 / @plugin”示例地图 */
    private void openVarDemoMap() {
        try {
            File dir = new File(System.getProperty("user.dir"),
                    com.studio.util.VarDemoMapFactory.DEFAULT_FOLDER);
            if (!new File(dir, "scenario.txt").isFile()) {
                com.studio.util.VarDemoMapFactory.createMap(dir);
                notify("已生成存档变量演示地图: " + dir.getAbsolutePath());
            }
            openMap(dir);
        } catch (IOException e) {
            Ui.error(stage, "生成示例失败", e.getMessage(), e);
        }
    }

    /** 生成并打开“存档实验室”示例地图（3 槽存档台：保存/读取/删除） */
    private void openSaveRoomDemoMap() {
        try {
            File dir = new File(System.getProperty("user.dir"),
                    com.studio.util.SaveRoomMapFactory.DEFAULT_FOLDER);
            if (!new File(dir, "scenario.txt").isFile()) {
                com.studio.util.SaveRoomMapFactory.createMap(dir);
                notify("已生成存档演示地图: " + dir.getAbsolutePath());
            }
            openMap(dir);
        } catch (IOException e) {
            Ui.error(stage, "生成示例失败", e.getMessage(), e);
        }
    }

    /** 生成并打开“打砖块”示例地图（场景事件与按钮事件两种触发方式） */
    private void openBreakoutDemoMap() {
        try {
            File dir = new File(System.getProperty("user.dir"),
                    com.studio.util.BreakoutMapFactory.DEFAULT_FOLDER);
            if (!new File(dir, "scenario.txt").isFile()) {
                com.studio.util.BreakoutMapFactory.createMap(dir);
                notify("已生成打砖块演示地图: " + dir.getAbsolutePath());
            }
            openMap(dir);
        } catch (IOException e) {
            Ui.error(stage, "生成示例失败", e.getMessage(), e);
        }
    }

    /** 新建地图（demoFlow=true 时生成含插件演示的示例地图） */
    private void createMapDialog(boolean demoFlow) {
        if (!confirmSaveChanges()) return;
        File parent = Ui.chooseDirectory(stage, "选择新地图的存放位置", currentParentDir());
        if (parent == null) return;
        Optional<String> name = Ui.askText(stage, "新建地图",
                demoFlow ? "将生成“含扫雷插件演示”的示例地图" : "将生成标准 AVG 初始界面模板",
                "地图文件夹名称（地图名）：", "新地图_" + System.currentTimeMillis() % 100000);
        if (name.isEmpty() || name.get().isBlank()) return;
        File dir = new File(parent, name.get().trim());
        try {
            if (dir.exists()) {
                Ui.warn(stage, "已存在", "目标文件夹已存在：" + dir.getAbsolutePath());
                return;
            }
            GameProject p = MapTemplateFactory.createMap(dir, demoFlow);
            project = p;
            currentScene = project.initialScene();
            selectedNode = null;
            markSaved();
            config.set("editor.last.map", dir.getAbsolutePath());
            config.save();
            refreshAll(false);
            notify("已创建并保存新地图: " + dir.getAbsolutePath());
        } catch (IOException e) {
            Ui.error(stage, "新建失败", e.getMessage(), e);
        }
    }

    private File currentParentDir() {
        if (project != null && project.rootDir() != null && project.rootDir().getParentFile() != null) {
            return project.rootDir().getParentFile();
        }
        String last = config.get("editor.last.map");
        if (last != null) {
            File f = new File(last);
            if (f.getParentFile() != null) return f.getParentFile();
        }
        return new File(System.getProperty("user.dir"));
    }

    /** 保存当前地图的 scenario.txt */
    public boolean saveMap() {
        if (project == null) return false;
        try {
            ScriptWriter.write(project.scenarioFile(), project);
            markSaved();
            notify("已保存 → " + project.scenarioFile().getAbsolutePath());
            return true;
        } catch (IOException e) {
            Ui.error(stage, "保存失败", "写入 scenario.txt 失败: " + e.getMessage(), e);
            return false;
        }
    }

    /** 导出为独立文件夹（结构: 地图名/resources + scenario.txt） */
    private void exportMap() {
        if (project == null) return;
        if (dirty && !confirmSaveChanges()) return;
        if (dirty) saveMap(); // 若用户选择保存则已保存；此处兜底
        File parent = Ui.chooseDirectory(stage, "选择导出位置（将创建 \"" + project.name() + "\" 文件夹）",
                currentParentDir());
        if (parent == null) return;
        File target = new File(parent, project.name());
        try {
            if (target.exists()) {
                if (!Ui.confirm(stage, "目标已存在",
                        "\"" + target.getAbsolutePath() + "\" 已存在，是否覆盖？",
                        "覆盖会删除该文件夹内的全部内容。")) {
                    return;
                }
                deleteRecursively(target);
            }
            if (!target.mkdirs()) throw new IOException("无法创建导出目录: " + target);

            // 1) scenario.txt（重新序列化，保证最新）
            File srcScenario = project.scenarioFile();
            if (!srcScenario.exists()) ScriptWriter.write(srcScenario, project);
            Files.copy(srcScenario.toPath(), target.toPath().resolve("scenario.txt"),
                    StandardCopyOption.REPLACE_EXISTING);

            // 2) resources 目录整体拷贝
            File srcRes = project.resourcesDir();
            File dstRes = new File(target, "resources");
            if (srcRes.isDirectory()) copyTree(srcRes, dstRes);

            // 3) 引用缺失的图片按节点尺寸兜底生成
            MapAssets.synthesizeMissing(project, dstRes);

            Ui.info(stage, "导出成功",
                    "已导出独立地图到:\n" + target.getAbsolutePath()
                    + "\n\n内部结构:\n├─ scenario.txt\n└─ resources/（素材）");
            notify("导出完成: " + target.getAbsolutePath());
        } catch (IOException e) {
            Ui.error(stage, "导出失败", e.getMessage(), e);
        }
    }

    /** 删除当前打开的地图文件夹 */
    private void deleteMap() {
        if (project == null || project.rootDir() == null) return;
        File root = project.rootDir();
        if (!Ui.confirm(stage, "删除地图",
                "确定删除当前地图吗？", "将永久删除整个文件夹（不可恢复）：\n" + root.getAbsolutePath())) {
            return;
        }
        try {
            deleteRecursively(root);
        } catch (IOException e) {
            Ui.error(stage, "删除失败", e.getMessage(), e);
            return;
        }
        project = null;
        currentScene = null;
        selectedNode = null;
        markSaved();
        config.set("editor.last.map", "");
        config.save();
        refreshAll(true);
        notify("已删除地图并回到空工作区");
    }

    private static void deleteRecursively(File root) throws IOException {
        if (!root.exists()) return;
        try (Stream<Path> walk = Files.walk(root.toPath())) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); }
                        catch (IOException e) { throw new java.io.UncheckedIOException(e); }
                    });
        } catch (java.io.UncheckedIOException e) {
            throw new IOException("删除文件失败: " + (e.getCause() == null ? e : e.getCause()));
        }
    }

    private static void copyTree(File src, File dst) throws IOException {
        try (Stream<Path> walk = Files.walk(src.toPath())) {
            for (Path p : walk.toList()) {
                Path rel = src.toPath().relativize(p);
                Path target = dst.toPath().resolve(rel);
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** 在读取器窗口中预览当前地图 */
    private void previewInPlayer() {
        if (project == null || project.rootDir() == null) {
            Ui.warn(stage, "无法预览", "请先打开或新建一张地图。");
            return;
        }
        ReaderView.openPreview(project.rootDir(), config);
    }

    // =====================================================================
    // 统一刷新 / 退出
    // =====================================================================

    private void refreshAll(boolean emptyState) {
        emptyOverlay.setVisible(project == null);
        emptyOverlay.setManaged(project == null);
        mapTitle.setText(project == null ? "（未打开）" : project.name());
        if (project == null) {
            currentScene = null;
            selectedNode = null;
            canvas.showScene(null);
        } else {
            // 打开地图后要把“初始场景”真正画到画布上：
            // 以前只刷新了底色，左侧树虽然选中了初始场景，画布却是空的。
            if (currentScene == null || !project.scenes().containsKey(currentScene.getName())) {
                GameScene init = project.initialScene();
                currentScene = init != null ? init : project.firstScene();
            }
            canvas.showScene(currentScene);
        }
        treePanel.refresh(currentScene);
        updateSceneBox();
        refreshInspector();
        canvas.refreshBackground();
        dirtyFlag.setText("");
    }

    /**
     * 关闭/切换前的保存确认。
     *
     * @return true 表示可以继续（保存过/不保存/无改动）；false 表示用户取消
     */
    public boolean confirmSaveChanges() {
        if (project == null || !dirty) return true;
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("未保存的更改");
        a.setHeaderText("当前地图有未保存的修改：\n" + (project.name()));
        a.setContentText("是否先保存 scenario.txt？");
        a.getDialogPane().getStylesheets().addAll(
                Ui.class.getResource("/styles/studio.css").toExternalForm());
        ButtonType save = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
        ButtonType noSave = new ButtonType("不保存", ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        a.getButtonTypes().setAll(save, noSave, cancel);
        var r = a.showAndWait().orElse(cancel);
        if (r == save) return saveMap();
        return r != cancel;
    }

    /** 请求退出（由关闭按钮/菜单触发） */
    public void requestExit() {
        if (confirmSaveChanges()) {
            stage.close();
        }
    }

    /** 是否允许关闭窗口 */
    public boolean canClose() {
        return project == null || !dirty || confirmSaveChanges();
    }
}
