package com.studio.editor;

import com.studio.model.GameScene;
import com.studio.model.NodeType;
import com.studio.model.StoryNode;
import com.studio.ui.FxAssets;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Map;

/**
 * 编辑器画布（中心区域）。
 *
 * <ul>
 *   <li>逻辑画布 1280×720 + 网格背景，随区域自适应缩放（Ctrl+滚轮 手动缩放）；</li>
 *   <li>鼠标靠近<b>左边缘</b>自动弹出悬浮工具箱；按住工具项拖到画布即新建节点；</li>
 *   <li>节点按住可拖动改坐标，单击选中（金色描边），双击或右键菜单打开属性编辑；</li>
 *   <li>空白处右键可从菜单“在此处添加…”，或清除场景。</li>
 * </ul>
 */
public class EditorCanvas extends StackPane {

    public static final double CW = 1280.0;
    public static final double CH = 720.0;

    private final EditorHub hub;

    // ---- 底板 ----
    private final StackPane boardHost = new StackPane();
    private final Pane board = new Pane();                 // 逻辑内容层（节点）
    private final Canvas gridCanvas = new Canvas(CW, CH);  // 网格层（节点下层）
    private final StackPane boardStack = new StackPane();  // grid + board 合成

    // ---- 悬浮层：常驻工具箱 + 坐标提示 ----
    private final StackPane overlay = new StackPane();
    private final VBox palette = new VBox(6);              // 常驻工具箱（可经 视图→显示工具箱 开关）
    private boolean toolboxVisible = true;
    private ContextMenu openMenu;                          // 正在显示的右键菜单（空白点击自动收起）

    // ---- 工具箱拖放状态 ----
    private boolean ghostActive = false;
    private String ghostType;
    private Label ghost;
    private double pressSceneX, pressSceneY;
    private boolean dragCycleEnded = false;
    private final EventHandler<MouseEvent> dragMover = this::onGhostMove;
    private final EventHandler<MouseEvent> dragFinisher = this::onGhostRelease;
    private boolean sceneFiltersOn = false;

    // ---- 节点视图缓存 ----
    private final Map<StoryNode, Pane> wrapperMap = new HashMap<>();

    // ---- 缩放 ----
    private double zoom = 0.9;
    private boolean autoFit = true;

    private final Label coordHint = new Label();

    public EditorCanvas(EditorHub hub) {
        this.hub = hub;
        getStyleClass().add("canvas-scroll");

        gridCanvas.setVisible(true);
        boardStack.getChildren().addAll(gridCanvas, board);
        boardStack.setPrefSize(CW, CH);
        // 防止节点越界露出画布区域
        boardStack.setClip(new javafx.scene.shape.Rectangle(CW, CH));
        boardHost.getChildren().add(boardStack);
        boardHost.setAlignment(Pos.CENTER);

        boardHost.widthProperty().addListener((o, a, b) -> applyFitIfAuto());
        boardHost.heightProperty().addListener((o, a, b) -> applyFitIfAuto());
        boardHost.setOnScroll(this::onScrollZoom);
        boardHost.setOnMouseMoved(e -> {
            Point2D p = toLogical(e.getSceneX(), e.getSceneY());
            coordHint.setText("X=" + fmt(p.getX()) + "  Y=" + fmt(p.getY()));
            coordHint.setVisible(hub.scene() != null);
            hub.setStatusCoords(p.getX(), p.getY());
        });

        // ===== 空白区行为：点击取消选中 + 关闭残留菜单 / 右键“在此处添加” =====
        board.setOnMousePressed(e -> {
            hideOpenMenu();
            if (e.getTarget() == board && e.getButton() == MouseButton.PRIMARY) {
                hub.selectNode(null);
            }
        });
        board.setOnContextMenuRequested(e -> {
            if (hub.scene() == null) return;
            Point2D p = toLogical(e.getSceneX(), e.getSceneY());
            ContextMenu menu = new ContextMenu();
            for (NodeType t : new NodeType[]{NodeType.TEXT, NodeType.CHARACTER, NodeType.BUTTON,
                    NodeType.DIALOG, NodeType.NAME, NodeType.BACKGROUND, NodeType.MUSIC}) {
                MenuItem add = new MenuItem(t.icon() + " 添加" + t.display() + "节点");
                final double x = Math.clamp(p.getX(), 0, CW);
                final double y = Math.clamp(p.getY(), 0, CH);
                add.setOnAction(ev -> hub.createNodeAt(t.code(), x, y));
                menu.getItems().add(add);
            }
            MenuItem del = new MenuItem("X 删除选中节点");
            del.setOnAction(event -> {hub.deleteNode(hub.selectedNode());});
            menu.getItems().add(del);
            menu.getItems().add(new SeparatorMenuItem());
            MenuItem clear = new MenuItem("🧹 清空本场景全部节点");
            clear.setOnAction(ev -> {
                if (hub.scene() != null) {
                    if (com.studio.ui.Ui.confirm(null, "清空场景",
                            "确定清空场景 [" + hub.scene().getName() + "] 的所有节点吗？", "该操作不可撤销。")) {
                        hub.scene().nodes().clear();
                        hub.setDirty();
                        hub.nodesLayerChanged();
                        hub.selectNode(null);
                        hub.notify("已清空场景节点");
                    }
                }
            });
            menu.getItems().add(clear);
            openMenu(menu, board, e.getScreenX(), e.getScreenY());
        });

        // ===== 悬浮层布局（工具箱常驻画布左上）=====
        overlay.setPickOnBounds(false);

        palette.getStyleClass().add("palette");
        palette.setPrefWidth(140);
        palette.setMinWidth(140);
        palette.setMaxWidth(140);
        palette.setMaxHeight(Region.USE_PREF_SIZE); // 高度自适应内容，避免盖满画布
        palette.setOpacity(1);
        buildPaletteItems();

        coordHint.setPickOnBounds(false);
        coordHint.setTextFill(Color.rgb(255, 215, 106));
        coordHint.setStyle("-fx-font-size: 11px;");
        coordHint.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE); // 避免被拉满整层
        coordHint.setPrefWidth(150);

        getChildren().addAll(boardHost, overlay);
        overlay.getChildren().addAll(palette, coordHint);
        StackPane.setAlignment(palette, Pos.TOP_LEFT);
        StackPane.setAlignment(coordHint, Pos.BOTTOM_LEFT);
        palette.setTranslateX(0);
        palette.setTranslateY(84);
        coordHint.setTranslateX(14);
        coordHint.setTranslateY(-8);
    }

    // =====================================================================
    // 右键菜单管理：同一时间只保留一个；点击画布空白/其它区域自动收起
    // =====================================================================

    private void openMenu(ContextMenu menu, Node anchor, double screenX, double screenY) {
        hideOpenMenu();
        openMenu = menu;
        menu.show(anchor, screenX, screenY);
        // 保险：菜单项被选择或菜单失焦后自动清理引用
        menu.setOnHidden(e -> { if (openMenu == menu) openMenu = null; });
    }

    private void hideOpenMenu() {
        if (openMenu != null) {
            ContextMenu m = openMenu;
            openMenu = null;
            m.hide();
        }
    }

    /** 显示/隐藏常驻工具箱（视图菜单联动） */
    public void setToolboxVisible(boolean show) {
        toolboxVisible = show;
        palette.setVisible(show);
        palette.setManaged(show);
    }

    public boolean isToolboxVisible() { return toolboxVisible; }

    // =====================================================================
    // 对外控制（由 EditorPane / 菜单调用）
    // =====================================================================

    public void showScene(GameScene scene) {
        board.getChildren().clear();
        wrapperMap.clear();
        FxAssets.clearCache();
        if (scene != null) {
            for (StoryNode node : scene.nodes()) {
                addWrapper(node);
            }
        }
        refreshBackground();
    }

    /** 结构变化（增删/换序）后整体重建视图 */
    public void refreshScene() {
        GameScene scene = hub.scene();
        if (scene == null) return;
        StoryNode sel = hub.selectedNode();
        board.getChildren().clear();
        wrapperMap.clear();
        for (StoryNode node : scene.nodes()) {
            addWrapper(node);
        }
        select(sel);
        refreshBackground();
    }

    private void addWrapper(StoryNode node) {
        Pane w = createWrapper(node);
        board.getChildren().add(w);
        wrapperMap.put(node, w);
    }

    /** 单节点属性变化 → 刷新外观与几何（保留选中态与拖动绑定） */
    public void refreshNodeVisual(StoryNode node) {
        Pane wrapper = wrapperMap.get(node);
        if (wrapper == null) return;
        double w = Math.max(2, node.getWidth());
        double h = Math.max(2, node.getHeight());
        // 几何变化 → 同步包装尺寸与坐标
        wrapper.setPrefSize(w, h);
        wrapper.setMinSize(w, h);
        wrapper.setMaxSize(w, h);
        wrapper.setLayoutX(node.getX());
        wrapper.setLayoutY(node.getY());
        // 只替换内部“内容层”的视觉，避免子节点越积越多
        if (!wrapper.getChildren().isEmpty()) {
            javafx.scene.Node content = wrapper.getChildren().getFirst();
            if (content instanceof StackPane sp) {
                Region view = EditorNodeViews.create(
                        hub.project() == null ? null : hub.project().rootDir(), node);
                sp.getChildren().setAll(view);
            }
        }
    }

    public void select(StoryNode node) {
        for (Map.Entry<StoryNode, Pane> e : wrapperMap.entrySet()) {
            boolean sel = e.getKey() == node;
            if (sel) e.getValue().getStyleClass().add("selected");
            else e.getValue().getStyleClass().remove("selected");
        }
    }

    public void refreshBackground() {
        String bg = "#171822";
        if (hub.project() != null && hub.project().option().background() != null) {
            bg = hub.project().option().background();
        }
        boardStack.setStyle("-fx-background-color: " + bg + ";");
        drawGrid();
    }

    public void setGridVisible(boolean on) { gridCanvas.setVisible(on); }

    // ---- 缩放 ----
    public void zoomIn() { setZoomManual(zoom * 1.15); }
    public void zoomOut() { setZoomManual(zoom / 1.15); }
    public void fitZoom() { autoFit = true; applyFitIfAuto(); }
    public double zoom() { return zoom; }

    private void setZoomManual(double z) {
        autoFit = false;
        zoom = Math.clamp(z, 0.2, 4.0);
        applyZoom();
    }

    private void applyFitIfAuto() {
        if (!autoFit) return;
        double w = boardHost.getWidth();
        double h = boardHost.getHeight();
        if (w <= 0 || h <= 0) return;
        zoom = Math.max(0.15, Math.min(1.2, Math.min(w / CW, h / CH)));
        applyZoom();
    }

    private void applyZoom() {
        boardHost.setScaleX(zoom);
        boardHost.setScaleY(zoom);
        hub.setStatusZoom(zoom * 100);
    }

    private void onScrollZoom(ScrollEvent e) {
        if (!e.isControlDown()) return;
        e.consume();
        setZoomManual(e.getDeltaY() > 0 ? zoom * 1.12 : zoom / 1.12);
    }

    // =====================================================================
    // 坐标 / 网格
    // =====================================================================

    private Point2D toLogical(double sceneX, double sceneY) {
        return boardStack.sceneToLocal(sceneX, sceneY);
    }

    private void drawGrid() {
        GraphicsContext g = gridCanvas.getGraphicsContext2D();
        g.clearRect(0, 0, CW, CH);
        g.setStroke(Color.rgb(255, 255, 255, 0.045));
        for (double x = 0; x <= CW; x += 40) g.strokeLine(x, 0, x, CH);
        for (double y = 0; y <= CH; y += 40) g.strokeLine(0, y, CW, y);
        g.setStroke(Color.rgb(255, 255, 255, 0.10));
        for (double x = 0; x <= CW; x += 200) g.strokeLine(x, 0, x, CH);
        for (double y = 0; y <= CH; y += 200) g.strokeLine(0, y, CW, y);
    }

    private static String fmt(double v) {
        return String.valueOf(Math.round(v));
    }

    // =====================================================================
    // 节点包装
    // =====================================================================

    private Pane createWrapper(StoryNode node) {
        double w = Math.max(2, node.getWidth());
        double h = Math.max(2, node.getHeight());
        Region view = EditorNodeViews.create(hub.project() == null ? null : hub.project().rootDir(), node);

        Pane wrapper = new Pane();
        wrapper.setPrefSize(w, h);
        wrapper.setMinSize(w, h);
        wrapper.setMaxSize(w, h);
        wrapper.setLayoutX(node.getX());
        wrapper.setLayoutY(node.getY());
        wrapper.getStyleClass().add("canvas-node");

        StackPane content = new StackPane(view);
        content.setAlignment(Pos.TOP_LEFT);
        content.setPickOnBounds(false);
        wrapper.getChildren().add(content);

        attachInteractions(wrapper, node);
        return wrapper;
    }

    private void attachInteractions(Pane wrapper, StoryNode node) {
        wrapper.setOnMousePressed(e -> {//单击选择节点；任意按下先收起右键菜单
            hideOpenMenu();
            if (e.getButton() != MouseButton.PRIMARY) return;
            hub.selectNode(node);
        });
        wrapper.setOnMouseDragged(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            Point2D p = toLogical(e.getSceneX(), e.getSceneY());
            node.setX(clamp(node.getX(), p.getX() - node.getWidth() / 2.0));
            node.setY(clamp(node.getY(), p.getY() - node.getHeight() / 2.0));
            wrapper.setLayoutX(node.getX());
            wrapper.setLayoutY(node.getY());
            wrapper.setCursor(Cursor.MOVE);
            hub.setStatusCoords(node.getX(), node.getY());
        });
        wrapper.setOnMouseReleased(e -> {
            wrapper.setCursor(Cursor.DEFAULT);
            hub.setDirty();
            hub.refreshInspector();
        });
        wrapper.setOnMouseClicked(e -> {//双击打开节点详情
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                hub.openNodeDialog(node);
            }
        });
        wrapper.setOnContextMenuRequested(e -> {
            hub.selectNode(node);
            ContextMenu menu = new ContextMenu();
            MenuItem edit = new MenuItem("✏️ 编辑属性…");
            edit.setOnAction(ev -> hub.openNodeDialog(node));
            MenuItem copy = new MenuItem("📋 复制节点");
            copy.setOnAction(ev -> {
                StoryNode c = node.copy();
                c.setX(clamp(c.getX(), c.getX() + 26));
                c.setY(clamp(c.getY(), c.getY() + 26));
                hub.addNode(c);
            });
            MenuItem del = new MenuItem("🗑 删除节点");
            del.setOnAction(ev -> hub.deleteNode(node));
            MenuItem up = new MenuItem("⬆ 上移一层");
            up.setOnAction(ev -> {
                if (hub.scene() != null && hub.scene().bringForward(node)) hub.nodesLayerChanged();
            });
            MenuItem down = new MenuItem("⬇ 下移一层");
            down.setOnAction(ev -> {
                if (hub.scene() != null && hub.scene().sendBackward(node)) hub.nodesLayerChanged();
            });
            menu.getItems().addAll(edit, new SeparatorMenuItem(), copy, del,
                    new SeparatorMenuItem(), up, down);
            openMenu(menu, wrapper, e.getScreenX(), e.getScreenY());
        });
    }

    private double clamp(double orig, double v) {
        return Math.max(0, v);
    }

    // =====================================================================
    // 工具箱：常驻拖放（悬停弹出已废弃，需要时用 视图→显示工具箱 开关）
    // =====================================================================

    private void buildPaletteItems() {
        Label title = new Label("🎨 工具箱 · 拖到画布");
        title.getStyleClass().add("palette-title");
        palette.getChildren().add(title);
        for (NodeType t : new NodeType[]{NodeType.TEXT, NodeType.CHARACTER, NodeType.BUTTON,
                NodeType.BACKGROUND, NodeType.NAME, NodeType.DIALOG, NodeType.MUSIC}) {
            palette.getChildren().add(makePaletteItem(t));
        }
    }

    /** 每个工具项 = 自定义 Region（避免 Button 的 ActionEvent 干扰拖放） */
    private Region makePaletteItem(NodeType type) {
        Label text = new Label(type.icon() + "  " + type.display() + "节点");
        StackPane item = new StackPane(text);
        item.getStyleClass().add("palette-item");
        item.setPrefWidth(120);
        item.setMinHeight(34);
        item.setPickOnBounds(true); // 确保空白区也可按下/悬停
        item.setAlignment(Pos.CENTER_LEFT);

        item.setOnMousePressed(e -> {
            hideOpenMenu();
            if (e.getButton() != MouseButton.PRIMARY) return;
            pressSceneX = e.getSceneX();
            pressSceneY = e.getSceneY();
            ghostType = type.code();
            dragCycleEnded = false;
            installSceneDragFilters();
            e.consume();
        });
        item.setOnMouseReleased(e -> {
            removeSceneDragFilters();
            if (dragCycleEnded) return; // 拖放流程已由场景过滤器处理，避免重复生成
            if (hub.scene() != null) {
                double cx = Math.max(0, (boardHost.getWidth() / Math.max(zoom, 0.01) - type.defaultWidth()) / 2);
                double cy = Math.max(0, (boardHost.getHeight() / Math.max(zoom, 0.01) - type.defaultHeight()) / 2);
                hub.createNodeAt(type.code(), cx, cy);
            }
            e.consume();
        });
        return item;
    }

    private void installSceneDragFilters() {
        if (sceneFiltersOn || getScene() == null) return;
        sceneFiltersOn = true;
        getScene().addEventFilter(MouseEvent.MOUSE_DRAGGED, dragMover);
        getScene().addEventFilter(MouseEvent.MOUSE_RELEASED, dragFinisher);
    }

    private void removeSceneDragFilters() {
        if (!sceneFiltersOn || getScene() == null) return;
        sceneFiltersOn = false;
        getScene().removeEventFilter(MouseEvent.MOUSE_DRAGGED, dragMover);
        getScene().removeEventFilter(MouseEvent.MOUSE_RELEASED, dragFinisher);
    }

    /** 拖动阈值内先移动；超过阈值创建“幽灵”并进入拖放模式 */
    private void onGhostMove(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY) return;
        if (!ghostActive) {
            double dx = e.getSceneX() - pressSceneX;
            double dy = e.getSceneY() - pressSceneY;
            if (Math.hypot(dx, dy) < 6) return; // 尚未达到拖动阈值
            ghostActive = true;
            NodeType t = NodeType.from(ghostType);
            ghost = new Label(t.icon() + " " + t.display());
            ghost.getStyleClass().add("palette-item");
            ghost.setPickOnBounds(true);
            ghost.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            StackPane.setAlignment(ghost, Pos.TOP_LEFT); // 幽灵按绝对坐标跟随鼠标
            overlay.getChildren().add(ghost);
        }
        Point2D local = overlay.sceneToLocal(e.getSceneX(), e.getSceneY());
        ghost.setTranslateX(local.getX() + 6);
        ghost.setTranslateY(local.getY() + 6);
    }

    private void onGhostRelease(MouseEvent e) {
        if (ghostActive) {
            Point2D p = toLogical(e.getSceneX(), e.getSceneY());
            finishGhostDrop(p.getX(), p.getY());
            dragCycleEnded = true; // 拖放已完成，阻止按钮自身的“单击生成”重复执行
            removeSceneDragFilters();
        } else {
            // 未发生拖动：不占用本周期，让按钮自身的 MOUSE_RELEASED 处理“单击放置”
            cancelGhost();
            removeSceneDragFilters();
        }
    }

    private void finishGhostDrop(double logicalX, double logicalY) {
        String type = ghostType;
        cancelGhost();
        if (hub.scene() == null || type == null) return;
        double x = Math.max(0, Math.min(CW, logicalX));
        double y = Math.max(0, Math.min(CH, logicalY));
        hub.createNodeAt(type, x, y);
    }

    private void cancelGhost() {
        ghostActive = false;
        if (ghost != null) {
            overlay.getChildren().remove(ghost);
            ghost = null;
        }
        ghostType = null;
    }
}
