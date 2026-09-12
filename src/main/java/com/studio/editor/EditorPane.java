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

    /** 拖放地图文件夹时的虚线描边样式（不改布局，只加一圈边框） */
    private static final String DROP_STYLE =
            "-fx-border-color: #ffd76a; -fx-border-width: 2; -fx-border-style: segments(6, 6) line-cap round;";
    /** 是否正在拖放（用来只设置/清除一次样式） */
    private boolean dropActive = false;

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
        installFolderDrop();   // 支持把地图文件夹直接拖进窗口打开
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
        // 以前这里有五六个“打开某某演示地图”，菜单越堆越长。现在统一成「打开地图…」列表窗口：
        // 扫描编辑器默认地图文件夹，把每张图的场景/节点数、修改时间列出来，双击即开；
        // 新建地图与生成演示地图也都收进了那个窗口。
        MenuItem open = item("📂 打开地图…（默认文件夹里的地图列表）", e -> MapBrowserDialog.show(this));
        open.setAccelerator(KeyCombination.keyCombination("Ctrl+O"));
        MenuItem openOther = item("📁 打开其它文件夹…（系统选择框）", e -> openMapFromChooser());

        MenuItem fresh = item("新建地图…", e -> createMapDialog(false));
        MenuItem demo = item("新建示例地图（含插件演示）…", e -> createMapDialog(true));

        MenuItem save = item("保存地图 (Ctrl+S)", e -> saveMap());
        save.setAccelerator(KeyCombination.keyCombination("Ctrl+S"));

        MenuItem export = item("导出为独立文件夹…", e -> exportMap());
        MenuItem del = item("删除当前地图…", e -> deleteMap());

        MenuItem exit = item("退出", e -> requestExit());
        fileMenu.getItems().addAll(open, openOther, new SeparatorMenuItem(), fresh, demo,
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
        MenuItem presetFromNode = item("⭐ 把选中节点添加为个性化节点…", e -> {
            NodePresetStore.Preset made = EditorActions.addSelectedNodeAsPreset(this);
            if (made != null) refreshInspector();
        });
        MenuItem presetFromTpl = item("⭐ 把当前「新增节点模板」存为个性化节点…", e -> {
            NodePresetStore.Preset made = EditorActions.addTemplateAsPreset(this);
            if (made != null) refreshInspector();
        });
        MenuItem presetList = item("⭐ 个性化节点列表…（应用到新建节点）", e -> NodePresetDialog.show(this, stage));
        editMenu.getItems().addAll(undoItem, redoItem, new SeparatorMenuItem(),
                editNode, dupNode, delNode, new SeparatorMenuItem(), layerUp, layerDown,
                new SeparatorMenuItem(), presetFromNode, presetFromTpl, presetList,
                new SeparatorMenuItem(),
                item("🗂 外部编辑（复制当前地图到临时目录并打开）…", e -> externalEditStart()),
                item("📥 导入外部更改（覆盖原地图）…", e -> externalEditImport()),
                item("📂 打开外部编辑目录…", e -> {
            File session = ExternalEdit.sessionDir(config);
            if (session == null) notify("还没有外部编辑目录（先点「🗂 外部编辑」）");
            else ExternalEdit.openInExplorer(session);
        }));

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
        MenuItem zp = item("↔ 复位视窗（右键拖动可平移）", e -> canvas.resetPan());
        gridItem.setSelected(true);
        gridItem.setOnAction(e -> canvas.setGridVisible(gridItem.isSelected()));
        CheckMenuItem toolboxItem = new CheckMenuItem("显示工具箱");
        toolboxItem.setSelected(canvas.isToolboxVisible());
        toolboxItem.setOnAction(e -> canvas.setToolboxVisible(toolboxItem.isSelected()));
        viewMenu.getItems().addAll(zi, zo, zf, zp, new SeparatorMenuItem(), gridItem, toolboxItem);

        // ---------- 运行 ----------
        Menu runMenu = new Menu("运行(R)");
        MenuItem preview = item("在播放器中测试 ▶", e -> previewInPlayer());
        preview.setAccelerator(KeyCombination.keyCombination("Ctrl+R"));
        runMenu.getItems().add(preview);

        // ---------- 帮助 ----------
        Menu helpMenu = new Menu("帮助(H)");
        helpMenu.getItems().addAll(
                item("关于…", e -> Ui.info(stage, "关于 剧情编辑器 / 播放器", aboutText())),
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

    @Override
    public java.util.List<com.studio.plugin.builtin.PluginInfo> pluginCatalog() {
        return pluginCatalogDetailed().items();
    }

    @Override
    public PluginCatalog.Catalog pluginCatalogDetailed() {
        File projectDir = new File(System.getProperty("user.dir"));
        return PluginCatalog.load(projectDir, currentMapDir());
    }

    // =====================================================================
    // 关于
    // =====================================================================

    /**
     * 「帮助 → 关于」的内容：<b>每次打开都按当前环境重新生成</b>，
     * 所以不会出现“说明里写的版本/功能早就过时”的情况。
     */
    private String aboutText() {
        File mapsRoot = mapsRoot();
        PluginCatalog.Catalog cat = pluginCatalogDetailed();
        int builtin = com.studio.plugin.builtin.BuiltinCatalog.all().size();
        int external = Math.max(0, cat.items().size() - builtin);
        File session = ExternalEdit.sessionDir(config);
        File map = currentMapDir();

        StringBuilder sb = new StringBuilder();
        sb.append("剧情编辑器 Studio × 剧情播放器 Player\n");
        sb.append("纯 Java + JavaFX（无 FXML、无第三方 UI 库）\n\n");

        sb.append("运行环境：").append(runtimeInfo()).append('\n');
        sb.append("工程根目录：").append(System.getProperty("user.dir")).append('\n');
        sb.append("默认地图文件夹：").append(mapsRoot.getAbsolutePath())
                .append("（").append(MapBrowserDialog.countMaps(mapsRoot)).append(" 张地图）\n");
        sb.append("当前地图：").append(map == null ? "（未打开）" : map.getAbsolutePath()).append('\n');
        sb.append("个性化节点模板：").append(NodePresetStore.file().getName())
                .append("（").append(NodePresetStore.load().size()).append(" 条）\n");
        sb.append("外部编辑目录：").append(ExternalEdit.rootDir().getName())
                .append(session == null ? "（当前没有进行中的工作副本）" : "（进行中：" + session.getName() + "）").append('\n');
        sb.append('\n');

        sb.append("插件：可用 ").append(cat.items().size()).append(" 个（自带 ").append(builtin)
                .append(" + 外部 ").append(external).append("）\n");
        if (!cat.problems().isEmpty()) {
            sb.append("　　　注意：有 ").append(cat.problems().size())
                    .append(" 个注册项不能用于 @plugin 槽（事件插件请用节点的 event 属性）\n");
        }
        sb.append("　　　插入方式：节点属性窗口 → 槽 → 「自带插件」下拉框 → ＋ 插入插件槽\n");
        sb.append('\n');

        sb.append("地图格式：[option] / [场景名] / { 节点属性 }，双向读写；\n");
        sb.append("地图 = 文件夹（scenario.txt + resources/ + saves/）；\n");
        sb.append("支持：存档变量与表达式、信号/槽、@plugin 插件、场景进入/离开自动信号、\n");
        sb.append("　　　插件事件嵌入主舞台、导出独立文件夹、撤销/重做（80 步）。\n\n");

        sb.append("想知道怎么用：帮助 → 📖 使用帮助（脚本语法 / 内联样式 / 信号·槽·插件 / 快捷键）。\n");
        sb.append("更新记录见工程根目录的 Changelog.md。");
        return sb.toString();
    }

    /** JVM 与 JavaFX 版本（拿不到就写“未知”，不要瞎猜） */
    private static String runtimeInfo() {
        String java = System.getProperty("java.version", "?");
        String vendor = System.getProperty("java.vendor", "");
        String fx = "未知";
        try {
            Package p = javafx.stage.Stage.class.getPackage();
            String v = p == null ? null : p.getImplementationVersion();
            if (v != null && !v.isBlank()) fx = v;
        } catch (RuntimeException ignored) {
            // 保持“未知”
        }
        return "JDK " + java + (vendor.isBlank() ? "" : "（" + vendor + "）") + " · JavaFX " + fx;
    }

    // =====================================================================
    // 供「打开地图」列表窗口使用的小接口
    // =====================================================================

    /** 对话框宿主窗口 */
    public Stage stageForDialog() { return stage; }

    /** 当前打开的地图目录（未打开返回 null） */
    public File currentMapDir() { return project == null ? null : project.rootDir(); }

    /**
     * 编辑器默认地图文件夹：{@code config.ini} 的 {@code editor.maps.dir}，
     * 相对路径按运行目录解析，默认 {@code maps/}。不存在会自动建。
     */
    public File mapsRoot() {
        String configured = config.get("editor.maps.dir");
        File dir = configured == null || configured.isBlank() ? null : new File(configured);
        if (dir == null) dir = new File(System.getProperty("user.dir"), "maps");
        else if (!dir.isAbsolute()) dir = new File(System.getProperty("user.dir"), configured);
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    /** 走系统选择框打开任意文件夹里的地图（列表窗口里的「打开其它文件夹…」） */
    public void openMapFromChooser() { openMapDialog(); }

    // =====================================================================
    // 把地图文件夹直接拖进编辑器窗口就能打开
    // =====================================================================

    /** 安装拖放：接受“含 scenario.txt 的文件夹”，也接受 scenario.txt（或任意地图文件）本身 */
    private void installFolderDrop() {
        setOnDragOver(e -> {
            File map = mapFromDragboard(e.getDragboard());
            if (map == null) {
                e.acceptTransferModes(javafx.scene.input.TransferMode.NONE);
                clearDropHint();
                return;
            }
            e.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            if (!dropActive) {
                dropActive = true;
                setStyle(DROP_STYLE);
                notify("📂 松手即打开地图：" + map.getName() + "（" + map.getAbsolutePath() + "）");
            }
            e.consume();
        });
        setOnDragExited(e -> clearDropHint());
        setOnDragDropped(e -> {
            File map = mapFromDragboard(e.getDragboard());
            clearDropHint();
            if (map == null) {
                // 常见情况：拖进来的是“装了很多地图的上层目录” —— 告诉用户去列表窗口挑
                int many = 0;
                List<File> files = e.getDragboard() == null ? null : e.getDragboard().getFiles();
                if (files != null) {
                    for (File f : files) {
                        File dir = f.isDirectory() ? f : f.getParentFile();
                        if (dir != null) many = Math.max(many, mapSubdirs(dir).size());
                    }
                }
                if (many > 1) {
                    notify("这个文件夹里有 " + many + " 张地图，请用「📂 打开地图…」列表挑一张（现在打开的列表窗口里选）");
                    MapBrowserDialog.show(this);
                } else {
                    notify("拖进来的不是地图文件夹（文件夹里需要有 scenario.txt）");
                }
                e.setDropCompleted(false);
                return;
            }
            notify("已打开拖入的地图：" + map.getAbsolutePath());
            openMap(map);
            e.setDropCompleted(true);
            e.consume();
        });
    }

    /** 拖放时给整个编辑区加一圈虚线描边（不改布局，松手/离开自动去掉） */
    private void clearDropHint() {
        if (!dropActive) return;
        dropActive = false;
        setStyle("");
    }

    /**
     * 从拖放数据里解析出地图文件夹：
     * <ul>
     *   <li>拖进来一个文件夹且里面有 scenario.txt → 用它；</li>
     *   <li>文件夹里只有一层子文件夹含 scenario.txt（拖了 maps 这类上层目录）→ 用那个子文件夹；</li>
     *   <li>直接拖 scenario.txt（或任何文件）→ 用它所在目录。</li>
     * </ul>
     * 都不满足返回 null。
     */
    public static File mapFromDragboard(javafx.scene.input.Dragboard db) {
        if (db == null || !db.hasFiles()) return null;
        return mapFromFiles(db.getFiles());
    }

    /** 拖放解析的实际逻辑（单独拆出来，便于探针直接测） */
    public static File mapFromFiles(List<File> files) {
        if (files == null || files.isEmpty()) return null;
        for (File f : files) {
            if (f == null) continue;
            File dir = f.isDirectory() ? f : f.getParentFile();
            if (dir == null) continue;
            if (new File(dir, "scenario.txt").isFile()) return dir;
            // 拖进来的是上层目录（例如整个 maps/）：里面只有一个地图时直接用；
            // 有多个时不猜 —— 交给调用方提示用户去列表窗口里挑
            List<File> candidates = mapSubdirs(dir);
            if (candidates.size() == 1) return candidates.get(0);
        }
        return null;
    }

    /** 某个目录下所有“含 scenario.txt”的直接子目录 */
    public static List<File> mapSubdirs(File dir) {
        List<File> out = new ArrayList<>();
        if (dir == null || !dir.isDirectory()) return out;
        File[] subs = dir.listFiles(File::isDirectory);
        if (subs != null) {
            for (File sub : subs) {
                if (new File(sub, "scenario.txt").isFile()) out.add(sub);
            }
        }
        return out;
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

    /** 按缩写生成并打开演示地图（列表窗口里的「生成演示地图」菜单） */
    public void generateDemoMap(String key) {
        if (key == null) return;
        switch (key) {
            case "branch" -> openBranchDemoMap();
            case "signallab" -> openSignalLabMap();
            case "logicgate" -> openLogicGateDemoMap();
            case "vardemo" -> openVarDemoMap();
            case "saveroom" -> openSaveRoomDemoMap();
            case "breakout" -> openBreakoutDemoMap();
            default -> notify("未知的演示地图：" + key);
        }
    }

    // =====================================================================
    // 外部编辑：复制到临时目录 → 用别的工具改 → 导回（覆盖原地图）
    // =====================================================================

    /** 「🗂 外部编辑」：开始一次外部编辑（已有未导入的副本时先问怎么办） */
    private void externalEditStart() {
        File source = currentMapDir();
        if (source == null || !source.isDirectory()) {
            Ui.warn(stage, "无法外部编辑", "请先打开或新建一张地图。");
            return;
        }
        File session = ExternalEdit.sessionDir(config);
        File sessionSource = ExternalEdit.sourceOf(session);
        if (session != null && sessionSource != null && sessionSource.equals(source)) {
            int changed = ExternalEdit.countChangedFiles(source, session);
            if (changed > 0) {
                ExternalEdit.Next next = ExternalEdit.askNext(stage, source, session, changed);
                if (next == ExternalEdit.Next.IMPORT) {
                    importExternalChanges(source, session);
                } else if (next == ExternalEdit.Next.RESTART) {
                    startExternalSession(source);
                }
                return;
            }
            // 有副本但没改过：直接打开，省得再复制一份
            notify("工作副本没有变化，直接打开：" + session.getName());
            ExternalEdit.openInExplorer(session);
            askImportAfterEdit(source, session, 0);
            return;
        }
        startExternalSession(source);
    }

    /** 复制一份并打开目录，然后问“改完要不要导入” */
    private void startExternalSession(File source) {
        try {
            File session = ExternalEdit.beginSession(source, config);
            ExternalEdit.openInExplorer(session);
            notify("已复制到外部编辑目录：" + session.getAbsolutePath());
            askImportAfterEdit(source, session, 0);
        } catch (IOException e) {
            Ui.error(stage, "外部编辑失败", e.getMessage(), e);
        }
    }

    /** 提示“编辑完成后是否导入更改”（点【现在导入】立刻导，否则等改完再点菜单项） */
    private void askImportAfterEdit(File source, File session, int changed) {
        boolean now = Ui.confirm(stage, "外部编辑",
                "已把当前地图复制到：\n" + session.getAbsolutePath(),
                "已在资源管理器中打开该目录，你可以在那里直接改 scenario.txt 等文件。\n"
                        + "改完回到编辑器点「📥 导入外部更改」即可覆盖原地图（导入前会自动备份）。\n\n"
                        + "现在就导入吗？（还没改就点「取消」，之后随时可以从菜单导入）");
        if (now) importExternalChanges(source, session);
    }

    /** 「📥 导入外部更改」：确认后覆盖原地图并重新加载 */
    private void externalEditImport() {
        File source = currentMapDir();
        File session = ExternalEdit.sessionDir(config);
        if (session == null) {
            Ui.info(stage, "还没有外部编辑目录",
                    "先点「🗂 外部编辑（复制当前地图到临时目录并打开）」，改完再回来导入。");
            return;
        }
        File sessionSource = ExternalEdit.sourceOf(session);
        if (source == null || sessionSource == null) {
            Ui.warn(stage, "无法导入", "工作副本没有记录来源地图：" + session.getAbsolutePath());
            return;
        }
        if (!sessionSource.equals(source)) {
            boolean go = Ui.confirm(stage, "来源不一致",
                    "这份工作副本来自：" + sessionSource.getAbsolutePath(),
                    "当前打开的是：" + source.getAbsolutePath() + "\n\n要切换到来源地图再导入吗？");
            if (!go) return;
            source = sessionSource;
        }
        importExternalChanges(source, session);
    }

    private void importExternalChanges(File source, File session) {
        List<String> changed = ExternalEdit.changedFiles(source, session);
        if (changed.isEmpty()) {
            Ui.info(stage, "没有检测到更改",
                    "工作副本与原地图内容完全一致，无需导入。\n\n副本目录：" + session.getAbsolutePath());
            return;
        }
        StringBuilder list = new StringBuilder();
        int shown = 0;
        for (String c : changed) {
            list.append("\n  · ").append(c);
            if (++shown >= 12) {
                list.append("\n  …（共 ").append(changed.size()).append(" 个文件）");
                break;
            }
        }
        boolean ok = Ui.confirm(stage, "导入外部更改",
                "检测到 " + changed.size() + " 个文件有改动：" + list,
                "导入会用工作副本覆盖原地图：\n" + source.getAbsolutePath()
                        + "\n\n（原地图会先自动备份到副本目录的「" + ExternalEdit.BACKUP + "」，导入后编辑器会重新加载地图）\n\n确定导入吗？");
        if (!ok) return;
        try {
            File backup = ExternalEdit.importChanges(source, session);
            ExternalEdit.endSession(config);
            openMap(source);     // 重新加载，画布与树都刷新
            notify("已导入 " + changed.size() + " 个文件的更改"
                    + (backup == null ? "" : "（原地图已备份到 " + backup.getName() + "）"));
        } catch (IOException e) {
            Ui.error(stage, "导入失败", e.getMessage(), e);
        }
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

    /** 新建地图（demoFlow=true 时生成含插件演示的示例地图）。列表窗口的「新建地图」也会调用 */
    void createMapDialog(boolean demoFlow) {
        if (!confirmSaveChanges()) return;
        File parent = Ui.chooseDirectory(stage, "选择新地图的存放位置", currentParentDir());
        if (parent == null) return;
        Optional<String> name = Ui.askText(stage, "新建地图",
                demoFlow ? "将生成“含扫雷插件演示”的示例地图" : "将生成标准 AVG 初始界面模板",
                "地图文件夹名称（地图名）：", "map_" + System.currentTimeMillis() % 100000);
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

    /** 保存当前地图的 scenario.txt（手写注释会被保住，见 {@link CommentPreserver}） */
    public boolean saveMap() {
        if (project == null) return false;
        try {
            File target = project.scenarioFile();
            String fresh = ScriptWriter.serialize(project);
            // 旧文件里手写的注释按“挂在哪个场景/节点/属性上”合并回来；合并结果还要重解析校验一次结构
            CommentPreserver.Result merged = CommentPreserver.merge(target, fresh);
            String text = fresh;
            if (merged.kept() > 0 && CommentPreserver.sameStructure(project.rootDir(), fresh, merged.text())) {
                text = merged.text();
            } else if (merged.kept() > 0) {
                Logs.warn("[注释保留] 合并结果结构不一致，本次按无注释版本保存（地图内容不受影响）");
            }
            java.nio.file.Path t = target.toPath();
            java.nio.file.Path tmp = t.resolveSibling(target.getName() + ".tmp");
            java.nio.file.Files.writeString(tmp, text, java.nio.charset.StandardCharsets.UTF_8);
            java.nio.file.Files.move(tmp, t, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            markSaved();
            String extra = merged.kept() > 0 ? "（保留了 " + merged.kept() + " 处手写注释）" : "";
            notify("已保存 → " + target.getAbsolutePath() + extra);
            if (merged.lost() > 0) notify("有 " + merged.lost() + " 处注释没有落点：" + merged.notes().get(0));
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
