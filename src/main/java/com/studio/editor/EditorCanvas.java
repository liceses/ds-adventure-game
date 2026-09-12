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
import javafx.scene.control.Menu;
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
 *   <li>空白处右键：➕ 添加节点（按类型子菜单）/ ⬆ 上移一层 / ⬇ 下移一层 / 🗑 删除节点 / 🧹 清空场景。</li>
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

    // ---- 缩放与平移（视窗）----
    private double zoom = 0.9;
    private boolean autoFit = true;
    /** 平移量：放大后用来把地图的不同区域拖进可视范围（右键拖动 / 中键拖动） */
    private double panX = 0, panY = 0;
    /** 正在平移 */
    private boolean panning = false;
    /** 这一次右键操作已经变成“拖动”，因此不要再弹右键菜单 */
    private boolean suppressMenuOnce = false;
    private double panStartSceneX, panStartSceneY;
    private double panStartPanX, panStartPanY;
    /** 超过这个像素才算“拖动”，否则仍按“右键单击=弹菜单”处理 */
    private static final double PAN_THRESHOLD = 4.0;
    /** 拖到边缘时额外留出的余量（像素，取下面两个比例/下限中的较大者） */
    private static final double PAN_MARGIN_MIN = 180.0;
    private static final double PAN_MARGIN_RATIO = 0.35;

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

        boardHost.widthProperty().addListener((o, a, b) -> { applyFitIfAuto(); clampPan(); });
        boardHost.heightProperty().addListener((o, a, b) -> { applyFitIfAuto(); clampPan(); });
        boardHost.setOnScroll(this::onScrollZoom);
        // ===== 按住右键（或中键）拖动 = 平移视窗 =====
        // 放大之后地图会比可视区域大，四周看不到；这里用“右键拖动”把它拖进来。
        // 注意与右键菜单的区分：按下后移动超过阈值才算拖动，此时不再弹菜单（suppressMenuOnce）。
        boardHost.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() == MouseButton.SECONDARY || e.getButton() == MouseButton.MIDDLE) {
                panning = false;
                suppressMenuOnce = false;
                panStartSceneX = e.getSceneX();
                panStartSceneY = e.getSceneY();
                panStartPanX = panX;
                panStartPanY = panY;
            }
        });
        boardHost.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!e.isSecondaryButtonDown() && !e.isMiddleButtonDown()) return;
            double dx = e.getSceneX() - panStartSceneX;
            double dy = e.getSceneY() - panStartSceneY;
            if (!panning && Math.hypot(dx, dy) < PAN_THRESHOLD) return;   // 还没到“拖动”的程度
            panning = true;
            suppressMenuOnce = true;      // 拖动过程中/之后都不弹右键菜单
            hideOpenMenu();
            panX = panStartPanX + dx;
            panY = panStartPanY + dy;
            clampPan();
            applyPan();
            boardHost.setCursor(Cursor.CLOSED_HAND);
            e.consume();
        });
        boardHost.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (e.getButton() != MouseButton.SECONDARY && e.getButton() != MouseButton.MIDDLE) return;
            boolean wasPanning = panning;
            panning = false;
            boardHost.setCursor(Cursor.DEFAULT);
            if (wasPanning) {
                hub.notify("已平移视窗（右键拖动；Ctrl+0 可复位）");
                e.consume();
            }
        });
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
        // ===== 统一的右键菜单 =====
        // 原来“空白处”与“节点上”是两个不同菜单，而且节点上的菜单会被这里的处理器覆盖，
        // 结果无论在哪右键看到的都是空白菜单。现在合并成一个：
        // 先按逻辑坐标命中测试，右键落在某个节点上就先选中它，再统一构建菜单
        //（背景节点不参与命中，避免整屏背景把“空白处”吃掉）。
        board.setOnContextMenuRequested(e -> {
            if (hub.scene() == null) return;
            // 刚刚用右键拖动平移过视窗 → 这一次不弹菜单（否则一拖完就弹出来挡住画面）
            if (suppressMenuOnce) {
                suppressMenuOnce = false;
                e.consume();
                return;
            }
            Point2D p = toLogical(e.getSceneX(), e.getSceneY());
            // 右键位置夹在画布范围内（新节点生成在右键处）
            final double x = Math.max(0, Math.min(CW, p.getX()));
            final double y = Math.max(0, Math.min(CH, p.getY()));

            StoryNode hit = hitTestNode(p.getX(), p.getY());
            if (hit != null) hub.selectNode(hit);
            final StoryNode sel = hit != null ? hit : hub.selectedNode();

            ContextMenu menu = new ContextMenu();

            // 1) 添加节点（子菜单：按类型列出全部 NodeType；文案 = 图标 + 显示名）
            Menu addMenu = new Menu("➕ 添加节点");
            for (NodeType t : NodeType.values()) {
                MenuItem add = new MenuItem(t.icon() + " " + t.display());
                add.setOnAction(ev -> hub.createNodeAt(t.code(), x, y));
                addMenu.getItems().add(add);
            }
            menu.getItems().add(addMenu);

            // 2) 节点相关操作（合并原“节点右键菜单”的全部功能）
            menu.getItems().add(new SeparatorMenuItem());

            MenuItem edit = new MenuItem("✏️ 编辑属性…");
            edit.setOnAction(ev -> {
                if (sel != null) hub.openNodeDialog(sel);
            });
            edit.setDisable(sel == null);
            menu.getItems().add(edit);

            MenuItem copy = new MenuItem("📋 复制节点（Ctrl+D）");
            copy.setOnAction(ev -> {
                if (sel == null) return;
                StoryNode c = sel.copy();
                c.setX(Math.max(0, Math.min(CW, c.getX() + 26)));
                c.setY(Math.max(0, Math.min(CH, c.getY() + 26)));
                hub.addNode(c);
            });
            copy.setDisable(sel == null);
            menu.getItems().add(copy);

            // 个性化节点：把当前调好的节点存成模板，之后可以在「个性化节点」窗口里套用到新节点
            MenuItem asPreset = new MenuItem("⭐ 添加为个性化节点…");
            asPreset.setOnAction(ev -> {
                if (sel == null) return;
                hub.selectNode(sel);
                EditorActions.addSelectedNodeAsPreset(hub);
            });
            asPreset.setDisable(sel == null);
            menu.getItems().add(asPreset);

            MenuItem presetList = new MenuItem("⭐ 个性化节点列表…");
            presetList.setOnAction(ev -> NodePresetDialog.showFrom(hub));
            menu.getItems().add(presetList);

            menu.getItems().add(new SeparatorMenuItem());

            // 2) 上移一层（向画面顶层）
            MenuItem up = new MenuItem("⬆ 上移一层");
            up.setOnAction(ev -> {
                if (sel == null) return;
                if (hub.scene() != null && hub.scene().bringForward(sel)) {
                    hub.pushUndo("上移一层");
                    hub.nodesLayerChanged();
                } else {
                    hub.notify("已经是最上层");
                }
            });
            up.setDisable(sel == null);
            menu.getItems().add(up);

            // 3) 下移一层（向画面底层）
            MenuItem down = new MenuItem("⬇ 下移一层");
            down.setOnAction(ev -> {
                if (sel == null) return;
                if (hub.scene() != null && hub.scene().sendBackward(sel)) {
                    hub.pushUndo("下移一层");
                    hub.nodesLayerChanged();
                } else {
                    hub.notify("已经是最底层");
                }
            });
            down.setDisable(sel == null);
            menu.getItems().add(down);

            // 4) 删除选中节点
            MenuItem del = new MenuItem("🗑 删除节点");
            del.setOnAction(ev -> {
                if (sel != null) hub.deleteNode(sel);
            });
            del.setDisable(sel == null);
            menu.getItems().add(del);

            // 5) 清空本场景全部节点（保持原有确认弹窗）
            MenuItem clear = new MenuItem("🧹 清空本场景全部节点");
            clear.setOnAction(ev -> {
                if (hub.scene() != null) {
                    if (com.studio.ui.Ui.confirm(null, "清空场景",
                            "确定清空场景 [" + hub.scene().getName() + "] 的所有节点吗？", "该操作不可撤销。")) {
                        hub.pushUndo("清空场景节点");
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

        // 画布区域自己裁剪：放大后画布会超出这块区域，如果不裁剪就会盖到左右两侧的面板上
        //（裁掉之后画布区域就是一个“窗口”，配合右键拖动平移来看地图的各个角落）
        javafx.scene.shape.Rectangle canvasClip = new javafx.scene.shape.Rectangle();
        canvasClip.widthProperty().bind(widthProperty());
        canvasClip.heightProperty().bind(heightProperty());
        setClip(canvasClip);
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

    /** 收起当前右键菜单（EditorPane 的全局“点击任意处收起弹层”会调用） */
    public void hideOpenMenu() {
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
            javafx.scene.Node content = wrapper.getChildren().get(0);
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

    // ---- 缩放 / 平移 ----
    public void zoomIn() { setZoomManual(zoom * 1.15); }
    public void zoomOut() { setZoomManual(zoom / 1.15); }
    public void fitZoom() { autoFit = true; panX = 0; panY = 0; applyFitIfAuto(); applyPan(); }
    public double zoom() { return zoom; }
    public double panX() { return panX; }
    public double panY() { return panY; }

    /** 复位平移（不清缩放）；视图菜单「复位视窗」用 */
    public void resetPan() { panX = 0; panY = 0; applyPan(); }

    private void setZoomManual(double z) {
        autoFit = false;
        zoom = Math.max(0.2, Math.min(4.0, z));
        applyZoom();
        clampPan();
        applyPan();
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

    /**
     * 平移视窗：把缩放后的画布整体位移，让放大后看不见的四周能拖进可视范围。
     * <p>与缩放的先后顺序无关 —— JavaFX 的 translate 在缩放之外生效，所以这里的像素就是屏幕像素。</p>
     */
    private void applyPan() {
        boardHost.setTranslateX(panX);
        boardHost.setTranslateY(panY);
    }

    /**
     * 夹住平移量：最多让画布边缘越过可视区边缘一段距离（
     * {@link #PAN_MARGIN_MIN} 与可视区 35% 中的较大者）。
     *
     * <p>为什么要“越过”而不是刚好对齐：刚好对齐时，把地图上边缘拖到可视区上边缘之后就再也拖不动了，
     * 想让地图停在画面偏下的位置（方便对照其它面板、或者边看边改）就没有余地。
     * 现在留出足够余量，四周都能继续拖一段；同时仍然保证“怎么拖都不会把地图完全甩出屏幕”。</p>
     */
    private void clampPan() {
        double viewW = boardHost.getWidth();
        double viewH = boardHost.getHeight();
        if (viewW <= 0 || viewH <= 0) { panX = 0; panY = 0; return; }
        double scaledW = CW * zoom;
        double scaledH = CH * zoom;
        double marginX = Math.max(PAN_MARGIN_MIN, viewW * PAN_MARGIN_RATIO);
        double marginY = Math.max(PAN_MARGIN_MIN, viewH * PAN_MARGIN_RATIO);
        double maxX = Math.max(0, (scaledW - viewW) / 2.0) + marginX;
        double maxY = Math.max(0, (scaledH - viewH) / 2.0) + marginY;
        panX = Math.max(-maxX, Math.min(maxX, panX));
        panY = Math.max(-maxY, Math.min(maxY, panY));
    }

    /** 当前可见区域（用于探针/状态栏判断“看得到哪一块”） */
    public javafx.geometry.Bounds visibleBoardRectInScene() {
        return board.localToScene(board.getBoundsInLocal());
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

    /** 本次拖动是否已经记录过撤销快照（避免拖动过程中压入几十步） */
    private boolean dragUndoPushed = false;

    private void attachInteractions(Pane wrapper, StoryNode node) {
        wrapper.setOnMousePressed(e -> {//单击选择节点；任意按下先收起右键菜单
            hideOpenMenu();
            dragUndoPushed = false;
            if (e.getButton() != MouseButton.PRIMARY) return;
            hub.selectNode(node);
        });
        wrapper.setOnMouseDragged(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            // 一次拖动只记一步撤销（单纯点选不会产生历史）
            if (!dragUndoPushed) {
                hub.pushUndo("移动节点");
                dragUndoPushed = true;
            }
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
        // 右键统一由 board 的菜单处理（见下方 board.setOnContextMenuRequested）：
        // 这里不再单独挂菜单，避免两个处理器先后触发、后者把前者覆盖掉。
    }

    private double clamp(double orig, double v) {
        return Math.max(0, v);
    }

    /**
     * 逻辑坐标命中测试：返回该点上最上层的节点（后加入的在上层）。
     * <p>背景节点（bg）通常是整屏铺底，不参与命中，这样“点空白处右键”仍然成立。</p>
     */
    private StoryNode hitTestNode(double lx, double ly) {
        com.studio.model.GameScene scene = hub.scene();
        if (scene == null) return null;
        java.util.List<StoryNode> nodes = scene.nodes();   // 列表顺序 = 层级顺序（后面的在上层）
        // 从上往下找，第一个命中的就是“点到的那个”
        for (int i = nodes.size() - 1; i >= 0; i--) {
            StoryNode n = nodes.get(i);
            if (!n.isVisible() || n.getType() == NodeType.BACKGROUND) continue;
            double w = Math.max(2, n.getWidth());
            double h = Math.max(2, n.getHeight());
            if (lx >= n.getX() && lx <= n.getX() + w && ly >= n.getY() && ly <= n.getY() + h) {
                return n;
            }
        }
        return null;
    }

    // =====================================================================
    // 工具箱：常驻拖放（悬停弹出已废弃，需要时用 视图→显示工具箱 开关）
    // =====================================================================

    private void buildPaletteItems() {
        Label title = new Label("🎨 工具箱 · 拖到画布");
        title.getStyleClass().add("palette-title");
        palette.getChildren().add(title);
        for (NodeType t : new NodeType[]{NodeType.TEXT, NodeType.CHARACTER, NodeType.BUTTON,
                NodeType.BACKGROUND, NodeType.NAME, NodeType.DIALOG, NodeType.MUSIC,NodeType.TEXTBOX}) {
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
