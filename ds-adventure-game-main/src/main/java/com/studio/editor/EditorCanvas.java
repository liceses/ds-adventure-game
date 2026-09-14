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
    /** 最近一次选中的节点：缩放/适应窗口变化后要保证它仍然在可视区里 */
    private StoryNode keepVisible;
    /** 本次选中是“在画布上直接点的”，这一轮不允许自动挪画面（见 selectFromCanvas） */
    private StoryNode suppressScrollOnce;

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
            if (hit != null) selectFromCanvas(hit);   // 右键点的节点也在眼皮底下，同样不挪画面
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
        // 重建包装会丢掉高亮：如果当前选中节点就在这一幕，顺手补回来
        StoryNode sel = hub.selectedNode();
        if (sel != null && scene != null && scene.contains(sel)) select(sel);
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
        // 注意：这里刻意<b>不</b>监听 layoutBounds 去“自动再对齐一次” ——
        // 布局什么时候变是不确定的（改属性、换文本、缩放），而每次对齐都会挪动画布，
        // 用户看到的就是“明明没动它，画面自己跳”。滚进可视区只由选中那一刻的
        // select() + ensureVisibleSoon() 负责，且有固定的收敛条件。
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

    /**
     * 选中某个节点：画布上给出<b>看得见的高亮圈</b>，并把它滚进可视区。
     *
     * <p>两点都是给“在左侧层级树里点节点”用的：① 只给 wrapper 加 {@code .selected} 边框是不够的 ——
     * 内容层铺满整个 wrapper，会把边框盖住（选中了却看不出来）；所以高亮用单独一层
     * {@code selection-ring} 画在内容上面。② 画布放大/平移过时节点可能在可视区外，
     * 这里用最小平移量把它滚进来。</p>
     */
    public void select(StoryNode node) {
        for (Map.Entry<StoryNode, Pane> e : wrapperMap.entrySet()) {
            boolean sel = e.getKey() == node;
            if (sel) e.getValue().getStyleClass().add("selected");
            else e.getValue().getStyleClass().remove("selected");
            // 高亮圈：只有选中那个显示（放在内容之上，任何节点类型都看得见）
            for (javafx.scene.Node child : e.getValue().getChildren()) {
                if (child.getStyleClass().contains("selection-ring")) child.setVisible(sel);
            }
        }
        // 在画布上直接点中的节点<b>不</b>自动挪画面 —— 它本来就在眼皮底下，
        // 再去“对齐”只会把用户脚下的画面拖走（点一下画面就跳，像在抖）。
        if (node != null && node != suppressScrollOnce) {
            ensureVisible(node);
            // 刚换场景/刚重建视图时节点视图还没走布局，此刻算出来的矩形不准
            // （宽度接近 0 或还是旧尺寸），于是“滚进可视区”会误判成“不用动”。
            // 所以布局跑完后再对齐，并且没对上就再试几帧 —— 在左侧栏点节点时，
            // 无论画布放大/平移成什么样，节点最终都会真的出现在眼前。
            ensureVisibleSoon(node, 6);
        }
        keepVisible = node;   // 缩放/适应窗口变化后再对齐一次，保证点树里的节点总能看见
    }

    /**
     * 画布上点选节点：节点已经在可视区里（鼠标点的就是它），所以这一轮不允许自动对齐。
     *
     * <p>{@code hub.selectNode} 里会同步回调 {@link #select(StoryNode)}，所以这个标记只在
     * 这次调用期间有效，不依赖时间窗口。</p>
     */
    private void selectFromCanvas(StoryNode node) {
        suppressScrollOnce = node;
        try {
            hub.selectNode(node);
        } finally {
            suppressScrollOnce = null;
        }
    }

    /** 分几帧把节点滚进可视区（布局尺寸/缩放还会变，单次对齐不一定算得准） */
    private void ensureVisibleSoon(StoryNode node, int attemptsLeft) {
        javafx.application.Platform.runLater(() -> {
            if (hub.selectedNode() != node || node == suppressScrollOnce) return;   // 期间又选了别的，就别抢了
            double beforeX = panX;
            double beforeY = panY;
            ensureVisible(node);
            boolean moved = Math.abs(panX - beforeX) > 0.01 || Math.abs(panY - beforeY) > 0.01;
            // 只有“这一帧真的还在动”才继续追：一旦对齐到位（平移量不再变化）就立刻收手。
            // 否则像幕布这种比可视区还大的节点会被反复对齐，看起来就是画面在抖。
            if (moved && attemptsLeft > 1) {
                javafx.animation.PauseTransition wait =
                        new javafx.animation.PauseTransition(javafx.util.Duration.millis(60));
                wait.setOnFinished(e -> ensureVisibleSoon(node, attemptsLeft - 1));
                wait.play();
            }
        });
    }

    /**
     * 用最小的平移量把节点滚进当前可视区（已经是可见的就什么都不做）。
     *
     * <p><b>必须是不动点</b>：同一个节点连续调用两次，第二次不能再动 —— 否则“每帧对齐一次”
     * 就会变成来回拉锯（节点比可视区大时，先对齐左边、再对齐右边、再对齐左边…），
     * 用户看到的就是屏幕在抖。所以单轴上“装不下”时只对齐左边/上边，不再看右边/下边。</p>
     */
    public void ensureVisible(StoryNode node) {
        javafx.geometry.Bounds box = nodeSceneBounds(node);
        javafx.geometry.Bounds view = viewportSceneBounds();
        if (box == null || view == null) return;
        double pad = 24;                                   // 留一点边，别贴着边线
        double dx = axisDelta(box.getMinX(), box.getMaxX(), view.getMinX(), view.getMaxX(), pad);
        double dy = axisDelta(box.getMinY(), box.getMaxY(), view.getMinY(), view.getMaxY(), pad);
        if (dx == 0 && dy == 0) return;
        panX += dx;                                        // translate 在缩放之外，单位就是屏幕像素
        panY += dy;
        // 这里刻意不走 clampPan()：那个夹取是给“鼠标拖动”留余地的（不许把地图拖太远），
        // 而“把节点滚进可视区”必须真的能做到 —— 放大后节点在画布角落时，
        // 需要的平移量会比拖动允许的上限更大。ensureVisible 只平移“最小的一步”，
        // 且目标节点本来就在画布内，所以不会把地图甩出屏幕。
        applyPan();
    }

    /**
     * 单轴上的对齐量（不动点）。{@code bMin..bMax} 是目标矩形，{@code vMin..vMax} 是可视区。
     *
     * <p>目标比可视区还大（减去两边留白也装不下）时，只能“尽量看见”，此时统一对齐左边/上边：
     * 这样反复调用的结果一致，不会出现“这次靠左、下次靠右”的拉锯。</p>
     */
    private static double axisDelta(double bMin, double bMax, double vMin, double vMax, double pad) {
        double usable = (vMax - vMin) - pad * 2;
        double size = bMax - bMin;
        if (size >= usable) {
            // 装不下：如果它已经把可视区盖住了，那它已经是“尽量看得见”，不要再动；
            // 否则把左边/上边对齐进来。两种情况下反复调用结果都一致（不会左右拉锯）。
            if (bMin <= vMin + pad && bMax >= vMax - pad) return 0;
            return (vMin + pad) - bMin;
        }
        if (bMin < vMin + pad) return (vMin + pad) - bMin;
        if (bMax > vMax - pad) return (vMax - pad) - bMax;
        return 0;
    }

    /** 节点在屏幕坐标里的矩形（含缩放与平移）；不在当前场景里返回 null */
    public javafx.geometry.Bounds nodeSceneBounds(StoryNode node) {
        Pane w = wrapperMap.get(node);
        if (w == null) return null;
        return w.localToScene(w.getBoundsInLocal());
    }

    /** 可视区在屏幕坐标里的矩形（画布区域，含缩放后的实际可见范围） */
    public javafx.geometry.Bounds viewportSceneBounds() {
        return localToScene(getBoundsInLocal());
    }

    /** 这个节点现在是不是完整落在可视区里（探针/状态栏用） */
    public boolean isNodeFullyVisible(StoryNode node) {
        javafx.geometry.Bounds box = nodeSceneBounds(node);
        javafx.geometry.Bounds view = viewportSceneBounds();
        if (box == null || view == null) return false;
        return box.getMinX() >= view.getMinX() - 1 && box.getMaxX() <= view.getMaxX() + 1
                && box.getMinY() >= view.getMinY() - 1 && box.getMaxY() <= view.getMaxY() + 1;
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

    /** 按屏幕像素平移视窗（供键盘/探针/后续快捷键使用，带夹取） */
    public void panBy(double dx, double dy) {
        panX += dx;
        panY += dy;
        clampPan();
        applyPan();
    }

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
        // 适应窗口会让缩放变化，之前选中的节点可能又跑到可视区外 —— 再对齐一次。
        // 注意要延后一帧：刚改完缩放时节点的 localToScene 还是旧缩放下算出来的矩形，
        // 拿它去对齐会算出一个离谱的平移量（画面会莫名其妙跳一下）。
        if (keepVisible != null) ensureVisibleSoon(keepVisible, 3);
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

        // 选中圈：单独一层、放在内容<b>之后</b>（也就是画在内容上面）。
        // 以前选中样式只是给 wrapper 加 .selected 边框，而内容层铺满整个 wrapper，
        // 会把那 2px 边框整个盖住 —— 于是“点左侧栏选中了节点，画布上却看不出任何变化”。
        // 这里用一层鼠标穿透的描边方框，任何节点类型、任何底色都能看见；
        // 样式写在内联 CSS 里，不依赖 studio.css 是否加载。
        Region ring = new Region();
        ring.getStyleClass().add("selection-ring");
        ring.setMouseTransparent(true);
        ring.setPrefSize(w, h);
        ring.setMinSize(w, h);
        ring.setMaxSize(w, h);
        ring.setVisible(false);
        ring.setStyle("-fx-background-color: rgba(255,207,92,0.10);"
                + "-fx-border-color: #ffcf5c; -fx-border-width: 2; -fx-border-radius: 6;");
        wrapper.getChildren().add(ring);

        attachInteractions(wrapper, node);
        return wrapper;
    }

    /** 节点在画布上是不是“看得见地选中了”：高亮圈必须显示、且必须是最后画的那一层（在内容之上） */
    public boolean isNodeHighlighted(StoryNode node) {
        Pane w = wrapperMap.get(node);
        if (w == null) return false;
        java.util.List<javafx.scene.Node> children = w.getChildren();
        for (int i = 0; i < children.size(); i++) {
            if (!children.get(i).getStyleClass().contains("selection-ring")) continue;
            return children.get(i).isVisible() && i == children.size() - 1;
        }
        return false;
    }

    /** 本次拖动是否已经记录过撤销快照（避免拖动过程中压入几十步） */
    private boolean dragUndoPushed = false;

    private void attachInteractions(Pane wrapper, StoryNode node) {
        wrapper.setOnMousePressed(e -> {//单击选择节点；任意按下先收起右键菜单
            hideOpenMenu();
            dragUndoPushed = false;
            if (e.getButton() != MouseButton.PRIMARY) return;
            selectFromCanvas(node);
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
                NodeType.BACKGROUND, NodeType.NAME, NodeType.DIALOG, NodeType.TOAST, NodeType.TEXTBOX}) {
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
