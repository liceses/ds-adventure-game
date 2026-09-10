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
    private boolean skipSceneBoxListener = false;

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

        MenuItem save = item("保存地图 (Ctrl+S)", e -> saveMap());
        save.setAccelerator(KeyCombination.keyCombination("Ctrl+S"));

        MenuItem export = item("导出为独立文件夹…", e -> exportMap());
        MenuItem del = item("删除当前地图…", e -> deleteMap());

        MenuItem exit = item("退出", e -> requestExit());
        fileMenu.getItems().addAll(open, fresh, demo, branch, saveRoom, new SeparatorMenuItem(), save,
                export, del, new SeparatorMenuItem(), exit);

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
        editMenu.getItems().addAll(editNode, dupNode, delNode);

        // ---------- 场景 ----------
        Menu sceneMenu = new Menu("场景(S)");
        sceneMenu.getItems().addAll(
                item("新增场景…", e -> createSceneViaTree()),
                item("重命名当前场景…", e -> {
                    if (currentScene != null) renameSceneViaTree(currentScene);
                }),
                item("删除当前场景…", e -> deleteSceneViaTree()),
                new SeparatorMenuItem(),
                item("地图全局设置 [option]…", e -> NodeDialogs.showOptionDialog(this)));

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

    private static String syntaxHelp() {
        return """
                # 注释行
                [option]              ← 全局设置段
                initialScene = Start
                background = #0d0f1c
                volume = 0.8

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
                type: bg背景|char立绘|text文本|name人物名|dialog对话|button按钮|music音乐
                x / y / width / height / path(图片) / audio(音频) / text(富文本)
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
        NodeDialogs.showNodeDialog(this, node);
        canvas.select(node);
    }

    @Override
    public void createNodeAt(String typeCode, double x, double y) {
        if (currentScene == null || project == null) return;
        StoryNode node = NodeType.createDefault(typeCode, x, y);
        addNode(node);
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
