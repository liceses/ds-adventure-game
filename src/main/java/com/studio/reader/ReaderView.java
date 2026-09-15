package com.studio.reader;

import com.studio.flow.AutoSignals;
import com.studio.flow.Expr;
import com.studio.flow.FlowContext;
import com.studio.flow.FlowHost;
import com.studio.flow.FlowVariables;
import com.studio.flow.LogicLoader;
import com.studio.flow.PluginRuntime;
import com.studio.flow.SignalBus;
import com.studio.flow.SignalEvent;
import com.studio.flow.SignalDef;
import com.studio.model.GameProject;
import com.studio.model.GameScene;
import com.studio.model.NodeType;
import com.studio.model.SaveVarDef;
import com.studio.model.StoryNode;
import com.studio.parser.ParserException;
import com.studio.parser.ScriptParser;
import com.studio.plugin.GamePlugin;
import com.studio.plugin.PluginLoader;
import com.studio.saves.GameSaveManager;
import com.studio.saves.SaveData;
import com.studio.saves.SaveHook;
import com.studio.saves.SavePortal;
import com.studio.ui.FxAnim;
import com.studio.ui.FxAssets;
import com.studio.ui.RichText;
import com.studio.ui.Ui;
import com.studio.util.AppConfig;
import com.studio.util.Logs;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 剧情读取器核心 —— 轻量级剧情引擎。
 *
 * <p>职责：</p>
 * <ol>
 *   <li>加载地图 {@code scenario.txt}（复用 {@link ScriptParser}），按逻辑分辨率
 *       1280x720 布局，随舞台自适应等比缩放（信箱式）；</li>
 *   <li>按节点类型渲染：背景图 / 立绘(保持比例) / 名字牌 / 富文本对话 / 按钮 /
 *       <b>系统提示条(toast)</b>；支持 伪类 hover/pressed 动画 与 打字机逐字显示；</li>
 *   <li>音频统一走 {@code @plugin(audio)}（bgm/se 通道），音量取自 [option]；</li>
 *   <li>遇到场景级 / 节点级 {@code event} 属性时，经 {@link PluginLoader} 动态加载
 *       外部插件，并把插件 Parent “嵌入”主舞台中央（见 pluginLayer），
 *       上方提供统一的【返回】标题栏 —— 浏览器标签页式无缝跳转。</li>
 * </ol>
 */
public class ReaderView extends BorderPane implements SavePortal, FlowHost {

    /** 逻辑画布尺寸（与 MapTemplateFactory 一致） */
    public static final double CW = 1280.0, CH = 720.0;

    private static final String DEFAULT_TEXT_COLOR = "#f2f3ff";

    private static final Pattern TEXT_FILL = Pattern.compile("-fx-text-fill\\s*:\\s*([^;]+)");

    // ---- 外部依赖 ----
    private final Stage stage;
    private final AppConfig config;
    private File mapDir;
    private GameProject project;
    private PluginLoader pluginLoader;

    // ---- 状态 ----
    private GameScene scene;
    private String sceneName;
    private boolean turbo = false;          // 逐字显示加速分支（“加速”按钮动作已移除，恒为 false）
    private double typeSpeed = 14;          // ms/字符
    private double volume = 0.8;
    /** 全局音量倍率（标题页设置项）：乘在地图 [option] volume 之上 */
    private double volumeScale = 1.0;
    /** ESC 回调（标题页/宿主用来弹"回到标题"菜单） */
    private Runnable escHandler;

    // ---- 视图 ----
    private final StackPane mainStack = new StackPane();
    private final Pane board = new Pane();          // 逻辑坐标画布
    private final Group scaledBoard = new Group(board); // 等比缩放组
    private final StackPane boardHost = new StackPane();
    private final VBox toastHost = new VBox(8);     // 右上角提示
    private final StackPane pluginLayer = new StackPane(); // 插件嵌入层（自动铺满主舞台）
    private final BorderPane pluginContent = new BorderPane();
    private Label pluginTitle;
    private Button pluginBack;

    // ---- 对话段落 & 逐字显示（每个对话面板独立状态）----
    private final java.util.ArrayList<DialogParagraph> dialogs = new java.util.ArrayList<>();

    /** 一段对话框的显示状态：支持“--- ”分隔的多段台词逐段切换 */
    private static final class DialogParagraph {
        final StoryNode node;           // 所属节点（取“对话结束后跳转”的 target）
        final TextFlow flow;            // 内容流
        final String[] paragraphs;      // 按独立一行 --- 分隔的段落
        final double baseSize;
        final String color;
        final boolean typeOn;           // 是否启用逐字显示
        int index;                      // 当前段落
        boolean busy;                   // 正在逐字
        Timeline timer;
        DialogParagraph(StoryNode node, TextFlow flow, String[] paragraphs, double baseSize,
                        String color, boolean typeOn) {
            this.node = node;
            this.flow = flow;
            this.paragraphs = paragraphs;
            this.baseSize = baseSize;
            this.color = color;
            this.typeOn = typeOn;
        }
    }

    // ---- 音频 ----
    /** 音频通道（bgm / se / 自定义）：统一由 @plugin(audio) 驱动 */
    private final java.util.LinkedHashMap<String, MediaPlayer> audioChannels = new java.util.LinkedHashMap<>();
    /** 每个节点当前挂着的视频播放器（节点视图重建时要 dispose，避免泄漏） */
    private final java.util.LinkedHashMap<String, MediaPlayer> videoPlayers = new java.util.LinkedHashMap<>();

    // ---- 插件 ----
    private String previousScene;        // 进入插件前正在展示的场景（插件【返回】的目标）
    private String pluginReturnScene;    // 由场景级/按钮级事件临时指定的返回目标
    /** 当前运行中的事件插件：返回剧情 / 被替换 / 关闭播放器时都要回调它的 onDetach */
    private GamePlugin activePlugin;
    private String activePluginId = "";
    /** 小游戏路由（来自触发场景的 mg.* 属性） */
    private String mgMode = "";
    private String mgOnWin = "";
    private String mgOnLose = "";
    private String mgWith = "";
    private String mgLoop = "";
    /** 当前场景是否是一个结局场景（{@code ending = <id>}）；结局后停止推进对话 */
    private boolean endingReached;
    private com.studio.plugin.MiniGameResult pendingResult;
    /**
     * 上一拍的「舞台」节点（背景 / 立绘）与其签名。
     * <p>场景写 {@code stage = keep} 时（编译器给同一幕的连续拍点标注），下一拍只重建
     * 内容节点（对话 / 名牌 / 按钮），舞台节点按签名复用 —— 同 id 同签名直接沿用同一个视图，
     * 不重建也不重播入场动画，因此<b>点一句台词不会再整屏闪一次</b>。</p>
     */
    private final java.util.LinkedHashMap<String, Node> stageViews = new java.util.LinkedHashMap<>();
    private final java.util.LinkedHashMap<String, String> stageSignatures = new java.util.LinkedHashMap<>();
    private final java.util.LinkedHashMap<String, StoryNode> stageNodes = new java.util.LinkedHashMap<>();
    /**
     * 本拍的内容节点（对话 / 名牌 / 按钮 / 横幅 / 文本）。
     * <p>{@code stage = keep} 时按这个列表精确移除上一拍的内容 —— 不能只按 {@code nodeViews}（那个按 id 索引，
     * 而内容节点常常没有 id），否则上一拍的对话框会留在板上，越叠越多（表现为台词叠字）。</p>
     */
    private final java.util.ArrayList<Node> contentViews = new java.util.ArrayList<>();
    /**
     * 场景自动信号嵌套深度：槽里 {@code goto} 会同步递归进入下一幕并再次发「场景进入」——
     * 这是<b>链式逻辑拍点</b>（如「自动存档 → goto 下一幕」）赖以推进的正常行为，必须放行。
     * 只用深度上限兜住真正的自环/互环死循环。
     */
    private static final int MAX_AUTO_SIGNAL_DEPTH = 64;
    private int autoSignalDepth = 0;
    private boolean suppressSceneEvent;  // 从插件返回时避免重复触发场景事件

    // ---- 存档 ----
    private GameSaveManager saveManager;
    private final java.util.ArrayList<SaveHook> saveHooks = new java.util.ArrayList<>();

    // ---- 信号/槽 与逻辑层 ----
    private LogicLoader logicLoader;
    private SignalBus signalBus;
    /** 调试控制台浮层（懒创建；开关与快捷键见 [option] console / consoleKey） */
    private ConsoleView console;
    private final FlowVariables flowVars = new FlowVariables();
    /** 节点 id → 视图（供槽动作即时重绘） */
    private final Map<String, Node> nodeViews = new LinkedHashMap<>();
    private boolean keyListenerInstalled = false;

    // =====================================================================
    // 构造 / 启动
    // =====================================================================

    public ReaderView(Stage stage, File mapDir, AppConfig config) {
        this.stage = stage;
        this.mapDir = mapDir;
        this.config = config == null ? AppConfig.loadDefault() : config;
        buildUi();
    }

    /** 在新窗口预览地图（编辑器“在播放器中测试”用） */
    public static ReaderView openPreview(File mapFolder, AppConfig config) {
        Stage s = new Stage();
        ReaderView rv = new ReaderView(s, mapFolder, config);
        Scene sc = new Scene(rv);
        addStyles(sc);
        s.setTitle("剧情播放器（预览） — " + mapFolder.getName());
        s.setScene(sc);
        s.show();
        rv.start();
        return rv;
    }

    private static void addStyles(Scene scene) {
        var css = ReaderView.class.getResource("/styles/player.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        // 调试控制台自己的深色样式（否则 JavaFX 默认控件是浅色的，和游戏界面不搭）
        var consoleCss = ReaderView.class.getResource("/styles/console.css");
        if (consoleCss != null) scene.getStylesheets().add(consoleCss.toExternalForm());
    }

    private void buildUi() {
        getStyleClass().add("player-root");

        // 画布底板（始终以逻辑分辨率布局，整体缩放适配窗口）
        board.setPrefSize(CW, CH);
        board.setClip(new javafx.scene.shape.Rectangle(CW, CH));
        boardHost.getChildren().add(scaledBoard);

        // 监听区域变化做信箱式等比缩放
        boardHost.widthProperty().addListener((o, a, b) -> rescale());
        boardHost.heightProperty().addListener((o, a, b) -> rescale());
        boardHost.setAlignment(Pos.CENTER);

        // 右上角提示条（纯信息展示：整层对鼠标透明，绝不拦截画布/按钮的点击）
        toastHost.setAlignment(Pos.TOP_RIGHT);
        toastHost.setPadding(new Insets(14));
        toastHost.setMouseTransparent(true);
        toastHost.setPickOnBounds(false);
        StackPane.setAlignment(toastHost, Pos.TOP_RIGHT);

        // 插件嵌入层（默认隐藏）
        pluginLayer.setVisible(false);
        pluginLayer.setManaged(false);
        pluginLayer.setStyle("-fx-background-color: #0b0c14;");

        pluginTitle = new Label();
        pluginBack = new Button("← 返回剧情");
        pluginBack.getStyleClass().add("back-btn");
        pluginBack.setOnAction(e -> leavePlugin());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(10, pluginTitle, spacer, pluginBack);
        bar.getStyleClass().add("plugin-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        pluginContent.setTop(bar);
        pluginLayer.getChildren().add(pluginContent);

        mainStack.getChildren().addAll(boardHost, pluginLayer, toastHost);
        setCenter(mainStack);
    }

    private void rescale() {
        double w = boardHost.getWidth();
        double h = boardHost.getHeight();
        if (w <= 0 || h <= 0) return;
        double s = Math.min(w / CW, h / CH);
        s = Math.max(0.15, Math.min(2.0, s));
        scaledBoard.setScaleX(s);
        scaledBoard.setScaleY(s);
    }

    // =====================================================================
    // 启动入口
    // =====================================================================

    public void start() {
        stage.setOnCloseRequest(e -> shutdown());
        if (mapDir == null || !mapDir.isDirectory() || !new File(mapDir, "scenario.txt").exists()) {
            Ui.error(stage, "地图无效", "无法加载地图文件夹：" + (mapDir == null ? "(空)" : mapDir.getAbsolutePath())
                    + "\n请检查 config.ini 中 map.folder 或使用编辑器导出地图。", null);
            stage.close();
            return;
        }
        try {
            List<String> warnings = new java.util.ArrayList<>();
            project = ScriptParser.parse(new File(mapDir, "scenario.txt"), warnings);
            if (!warnings.isEmpty()) {
                Ui.showWarnings(stage, "脚本解析提示", warnings);
            }
            pluginLoader = PluginLoader.fromConfig(config);
            saveManager = new GameSaveManager(mapDir);
            // 信号/槽：逻辑类加载器（工程根/logic 与 地图/logic）+ 信号总线
            logicLoader = new LogicLoader(new File(System.getProperty("user.dir")), mapDir);
            signalBus = new SignalBus(this, logicLoader);
            // 引擎自身也是存档参与者：把运行期变量（地图/节点/属性覆盖）写入存档，读档时恢复
            saveHooks.add(new SaveHook() {
                @Override
                public void onEngineSave(SaveData data) {
                    flowVars.writeTo(data);
                }

                @Override
                public void onEngineLoad(SaveData data) {
                    flowVars.readFrom(data);
                }
            });
            volume = clamp(project.option().volume(), 0, 1);
            // 存档变量初值：工程加载完成后按 [option] 的声明补齐（只补“当前不存在”的键，
            // 因此不会覆盖掉读档/逻辑层写过的值；读档路径见 loadFrom）
            flowVars.applyDefaults(project.option().saveVars());
            double cfgSpeed = config.getDouble("typewriter.speed", 14);
            double mapSpeed = project.option().typewriterSpeed();
            typeSpeed = mapSpeed > 0 ? mapSpeed : cfgSpeed;
            installGlobalKeyListener();

            GameScene initial = project.initialScene();
            if (initial == null) {
                Ui.error(stage, "无可播放场景", "脚本中没有任何场景，请用编辑器编辑。", null);
                stage.close();
                return;
            }
            stage.setTitle("剧情播放器 — " + mapDir.getName());
            renderScene(initial.getName(), true, true);
        } catch (ParserException e) {
            Ui.error(stage, "脚本解析失败", e.getMessage() + "\n\n（相关警告见控制台）", null);
            stage.close();
        }
    }

    /** 关闭播放器：释放计时器/媒体/插件等一切后台资源 */
    private void shutdown() {
        clearDialogs();
        // 事件插件（GamePlugin）：以前这里漏了 onDetach，插件停不掉自己的线程/计时器
        detachActivePlugin("关闭播放器");
        // 槽插件（SlotPlugin）：统一回调 onDetach 并清空实例
        try {
            if (signalBus != null) signalBus.plugins().shutdown();
        } catch (RuntimeException e) {
            Logs.warn("[Plugin] 关闭槽插件时出错：" + e.getMessage());
        }
        stopAllAudio();
        disposeAllVideos();
    }

    /**
     * 回调当前事件插件的 {@link GamePlugin#onDetach()} 并清空记录。
     * <p>“回到剧情”“切换到另一个插件”“关闭播放器”三种情况都会走到这里，
     * 插件可以放心地在 onDetach 里停线程、停计时器、释放媒体。</p>
     */
    private void detachActivePlugin(String reason) {
        GamePlugin p = activePlugin;
        String id = activePluginId;
        activePlugin = null;
        activePluginId = "";
        if (p == null) return;
        try {
            p.onDetach();
            Logs.plugin(id, "onDetach 已回调（" + reason + "）");
        } catch (Throwable t) {
            Logs.error("[Plugin] onDetach 异常: " + id, t);
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // =====================================================================
    // 场景渲染
    // =====================================================================

    /**
     * 渲染一个场景。
     *
     * @param animate    是否播放入场动画
     * @param fireEvent  是否触发该场景的 event 插件
     */
    private void renderScene(String name, boolean animate, boolean fireEvent) {
        clearDialogs();
        GameScene target = project.getScene(name);
        if (target == null) {
            Logs.warn("场景不存在: " + name);
            toast("场景不存在: " + name);
            return;
        }
        // 记录“渲染前所在场景”——场景级插件触发时，返回按钮应回到这里
        String cameFrom = this.sceneName;
        boolean switched = cameFrom != null && !cameFrom.isBlank() && !cameFrom.equals(name);
        // ① 离开旧场景：先发「场景离开」（此时旧场景的节点还在，槽仍能改它们；参数带 to=即将进入的场景）
        if (switched) {
            LinkedHashMap<String, Object> p = new LinkedHashMap<>();
            p.put("scene", cameFrom);
            p.put("to", name);
            emitAutoSignal(AutoSignals.LEAVE, p, "旧场景 " + cameFrom + " → " + name);
        }
        this.sceneName = name;
        this.scene = target;
        // 注：自动信号「场景进入」在本幕渲染完成后发送一次（见下方 emitAutoSignal）

        // 本幕是否承接上一幕的舞台（脚本写 stage = keep）：有上一拍舞台时才谈得上复用
        boolean keepStage = "keep".equalsIgnoreCase(sceneProp(target, "stage")) && !stageViews.isEmpty();

        java.util.LinkedHashMap<String, Node> nextStageViews = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, String> nextStageSignatures = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, StoryNode> nextStageNodes = new java.util.LinkedHashMap<>();

        if (!keepStage) {
            board.getChildren().clear();
            nodeViews.clear();
            contentViews.clear();
            disposeAllVideos();
            FxAssets.clearCache();
            stageViews.clear();
            stageSignatures.clear();
            stageNodes.clear();
        } else {
            // 精确移除上一拍的「内容节点」（含没有 id 的），舞台节点先留在板上复用或走退场
            for (Node gone : new java.util.ArrayList<>(contentViews)) {
                board.getChildren().remove(gone);
            }
            contentViews.clear();
            for (String id : new java.util.ArrayList<>(nodeViews.keySet())) {
                if (!stageViews.containsKey(id)) nodeViews.remove(id);
            }
            FxAssets.clearCache();
        }

        // 背景底色来自 [option]
        String bg = project.option().background();
        board.setStyle("-fx-background-color: " + bg + ";");

        playSceneMusic(scene);

        int index = 0;
        for (StoryNode node : scene.nodes()) {
            applyStoredOverrides(node); // 读档/上次逻辑改动过的属性优先生效
            String nid = node.getId();
            boolean stageNode = isStageNode(node);
            String sig = stageSignature(node);

            // 舞台节点且签名完全一致 → 直接复用上一拍的视图：不重建、不重播入场
            if (keepStage && stageNode && !nid.isBlank() && stageViews.containsKey(nid)
                    && sig.equals(stageSignatures.get(nid))) {
                Node kept = stageViews.get(nid);
                if (kept != null) {
                    // 复用视图本身，只平滑更新不影响复用的属性（说话者高亮 = 不透明度变化）
                    double want = clamp(node.getOpacity(), 0.05, 1.0);
                    if (Math.abs(kept.getOpacity() - want) > 0.01) {
                        FadeTransition hi = new FadeTransition(Duration.millis(180), kept);
                        hi.setToValue(want);
                        hi.play();
                    }
                    kept.setVisible(node.isVisible());
                    nodeViews.put(nid, kept);
                    nextStageViews.put(nid, kept);
                    nextStageSignatures.put(nid, sig);
                    nextStageNodes.put(nid, node);
                    index++;
                    continue;
                }
            }

            Node view = buildNode(node);
            if (view == null) continue;
            view.setLayoutX(node.getX());
            view.setLayoutY(node.getY());
            view.setOpacity(clamp(node.getOpacity(), 0.05, 1.0));
            view.setVisible(node.isVisible());
            board.getChildren().add(view);
            if (!nid.isBlank()) nodeViews.put(nid, view);
            installMouseSignals(view, node);
            applyViewProps(view, node);

            if (stageNode && !nid.isBlank()) {
                nextStageViews.put(nid, view);
                nextStageSignatures.put(nid, sig);
                nextStageNodes.put(nid, node);
                Node old = keepStage ? stageViews.get(nid) : null;
                if (old != null && old != view) {
                    crossFadeOut(old, 220);            // 同 id 换图（例如换表情）：旧图淡出
                } else if (keepStage && animate && node.needsVisual()) {
                    // 本拍新登场的立绘：按站位从左/右滑入、居中淡入（含轻微放大）
                    stageEntrance(view, node, 40);
                }
            }

            if (!stageNode) {
                contentViews.add(view);
            }
            if (!keepStage && animate && node.needsVisual()) {
                FxAnim.entrance(view, 60 + index * 70, 380);
            }
            index++;
        }

        // 本拍不再出现的舞台节点：播退场动画后移除（不阻塞本拍内容）
        if (keepStage) {
            for (java.util.Map.Entry<String, Node> e : new java.util.ArrayList<>(stageViews.entrySet())) {
                if (nextStageViews.containsKey(e.getKey())) continue;
                Node gone = e.getValue();
                if (gone != null) stageExit(gone, stageNodes.get(e.getKey()));
            }
            // 舞台节点层级归位：背景在下、立绘依次在上，内容节点保持最上
            int layer = 0;
            for (Node kept : nextStageViews.values()) {
                board.getChildren().remove(kept);
                board.getChildren().add(layer++, kept);
            }
        }
        stageViews.clear();
        stageViews.putAll(nextStageViews);
        stageSignatures.clear();
        stageSignatures.putAll(nextStageSignatures);
        stageNodes.clear();
        stageNodes.putAll(nextStageNodes);

        // 承接上一幕时不整屏淡入（否则每点一句全屏闪一次）；换幕/开场仍保留整屏淡入
        if (animate && !keepStage) {
            board.setOpacity(0);
            FadeTransition ft = new FadeTransition(Duration.millis(280), board);
            ft.setToValue(1.0);
            ft.play();
        }

        // 场景事件：进入场景时动态加载外部插件并嵌入
        if (fireEvent && !suppressSceneEvent && scene.event() != null && !scene.event().isBlank()) {
            String id = scene.event().trim();
            pluginReturnScene = cameFrom != null ? cameFrom : sceneName; // 场景级事件：返回进入前的场景
            mgMode = sceneProp(scene, "mg.mode");
            mgOnWin = sceneProp(scene, "mg.onWin");
            mgOnLose = sceneProp(scene, "mg.onLose");
            mgWith = sceneProp(scene, "mg.with");
        mgLoop = sceneProp(scene, "mg.loop");
            pendingResult = null;
            runPlugin(id, "场景事件 [" + sceneName + "]");
        }
        suppressSceneEvent = false;

        // @ending：结局场景停住推进（与 event 无关，任何场景都可标结局）
        String ending = sceneProp(scene, "ending");
        endingReached = ending != null && !ending.isBlank();
        if (endingReached) {
            Logs.info("[Flow] 抵达结局：" + ending);
            toast("已抵达结局：" + ending);
            // 结局 BGM：按结局 id 选类别（bad_collapse/busy → bad；true/temp → ending；local → warm）
            String cat = com.studio.util.Bgm.categoryForEnding(ending);
            String rel = cat.isEmpty() ? "" : com.studio.util.Bgm.relPath(cat);
            if (!rel.isEmpty()) {
                playAudioChannel("bgm", rel, true, clamp(masterVolume() * 0.85, 0, 1));
                Logs.info("[Bgm] 结局音乐 [" + cat + "] " + rel);
            }
        }

        // ② 进入新场景：发「场景进入」信号（节点已经建好，槽可以立刻改它们的属性；参数带 from=上一幕）
        LinkedHashMap<String, Object> enterParams = new LinkedHashMap<>();
        enterParams.put("scene", name);
        enterParams.put("from", switched ? cameFrom : "");
        emitAutoSignal(AutoSignals.ENTER, enterParams, "进入 " + name
                + (switched ? "（来自 " + cameFrom + "）" : ""));

        // ③ 把“离开”也告诉新的一幕：新场景的槽可以订阅「上一个场景离开」对刚走的那一幕做反应
        if (switched) {
            LinkedHashMap<String, Object> prev = new LinkedHashMap<>();
            prev.put("scene", cameFrom);
            prev.put("to", name);
            emitAutoSignal(AutoSignals.PREV_LEAVE, prev, cameFrom + " 已离开（本幕 " + name + " 收到）");
        }
    }

    /** 舞台节点 = 背景 / 立绘（{@code stage = keep} 时被复用的那类节点） */
    private static boolean isStageNode(StoryNode node) {
        return node != null && (node.getType() == NodeType.BACKGROUND || node.getType() == NodeType.CHARACTER);
    }

    /**
     * 舞台签名：这些属性都没变就可以沿用同一个视图。
     * <p>刻意<b>不含</b> {@code opacity} / {@code visible} —— 说话者高亮只改不透明度，
     * 若把它们算进签名，每换一次说话人就会重建立绘（反而闪）；把它们排除后，
     * 复用的视图会平滑淡到新不透明度（见下面的复用分支）。</p>
     */
    private static String stageSignature(StoryNode node) {
        return node.getType() + "|" + node.getPath() + "|" + node.getX() + "|" + node.getY() + "|"
                + node.getWidth() + "|" + node.getHeight() + "|" + node.getStyle();
    }

    /** 旧视图淡出后移除（换表情/换图时叠一层交叉淡变） */
    private void crossFadeOut(Node oldView, int ms) {
        if (oldView == null) return;
        FadeTransition out = new FadeTransition(Duration.millis(ms), oldView);
        out.setToValue(0);
        out.setOnFinished(e -> board.getChildren().remove(oldView));
        out.play();
    }

    /**
     * 立绘/背景入场：立绘按站位从左/右滑入（居中则淡入 + 轻微放大），背景统一淡入。
     * 时长与缓动与整体演出节奏对齐（约 260ms，EASE_OUT）。
     */
    private void stageEntrance(Node view, StoryNode node, int delayMs) {
        double dx = 0;
        if (node.getType() == NodeType.CHARACTER) {
            if (node.getX() < 420) dx = -70;
            else if (node.getX() > 700) dx = 70;
        }
        double targetOpacity = clamp(node.getOpacity(), 0.05, 1.0);
        view.setOpacity(0);
        javafx.animation.ParallelTransition anim = new javafx.animation.ParallelTransition();
        FadeTransition fade = new FadeTransition(Duration.millis(260), view);
        fade.setToValue(targetOpacity);
        anim.getChildren().add(fade);
        if (dx != 0) {
            view.setTranslateX(dx);
            TranslateTransition slide = new TranslateTransition(Duration.millis(260), view);
            slide.setToX(0);
            slide.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
            anim.getChildren().add(slide);
        } else if (node.getType() == NodeType.CHARACTER) {
            view.setScaleX(0.96);
            view.setScaleY(0.96);
            ScaleTransition pop = new ScaleTransition(Duration.millis(260), view);
            pop.setToX(1.0);
            pop.setToY(1.0);
            pop.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
            anim.getChildren().add(pop);
        }
        anim.setDelay(Duration.millis(Math.max(0, delayMs)));
        anim.play();
    }

    /** 立绘/背景退场：淡出并按站位方向滑走，然后从板上移除 */
    private void stageExit(Node view, StoryNode node) {
        if (view == null) return;
        double dx = 0;
        if (node != null && node.getType() == NodeType.CHARACTER) {
            if (node.getX() < 420) dx = -60;
            else if (node.getX() > 700) dx = 60;
        }
        javafx.animation.ParallelTransition anim = new javafx.animation.ParallelTransition();
        FadeTransition fade = new FadeTransition(Duration.millis(220), view);
        fade.setToValue(0);
        anim.getChildren().add(fade);
        if (dx != 0) {
            TranslateTransition slide = new TranslateTransition(Duration.millis(220), view);
            slide.setToX(dx);
            anim.getChildren().add(slide);
        }
        anim.setOnFinished(e -> board.getChildren().remove(view));
        anim.play();
    }

    /**
     * 发送引擎自动信号（{@link AutoSignals}：场景进入 / 场景离开 / 上一个场景离开）。
     *
     * <p>地图里不用声明 {@code signal = ...}，直接在场景头写
     * {@code slot = 场景进入 | …} 就能在进入这一场景时自动跑一段逻辑 ——
     * 这是“条件内容 / 自动演出 / 自动存档 / 起定时器”最常用的挂载点。</p>
     *
     * <p>防重入：槽里如果再 {@code goto} 别的场景，会递归走到这里，
     * 这种情况下不再重复发出自动信号（避免无限循环），只记一条日志。</p>
     *
     * @param def    自动信号定义（规范名 + 别名）
     * @param params 传给槽的参数（{@code scene} / {@code from} / {@code to}…，槽里用 {@code @param(名)} 取）
     * @param note   日志里的一句说明
     */
    private void emitAutoSignal(AutoSignals.Def def, LinkedHashMap<String, Object> params, String note) {
        if (signalBus == null || def == null) return;
        if (autoSignalDepth >= MAX_AUTO_SIGNAL_DEPTH) {
            Logs.warn("[Flow] 自动信号嵌套过深（" + autoSignalDepth + " 层），跳过「" + def.name()
                    + "」（" + params.get("scene") + "）——请检查脚本里是否有场景互相 goto 成环");
            return;
        }
        autoSignalDepth++;
        try {
            // 规范名 + 别名一起发：地图里写「上一幕离开」这种别名也能收到
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>(params);
            signalBus.emitFromScene(def.name(), copy);
            for (String alias : def.aliases()) {
                signalBus.emitFromScene(alias, new LinkedHashMap<>(params));
            }
            Logs.info("[Flow] 场景信号「" + def.name() + "」" + (note == null || note.isBlank() ? "" : "： " + note));
        } catch (RuntimeException e) {
            Logs.warn("[Flow] 场景信号「" + def.name() + "」执行出错：" + e.getMessage());
        } finally {
            autoSignalDepth--;
        }
    }

    /**
     * 场景音频：以前这里会找 MUSIC 节点/节点 audio 属性来自动播 BGM。
     * <p>现在音频统一走 {@code @plugin(audio)}，这张地图想放 BGM 就在场景头写：</p>
     * <pre>
     * slot = 场景进入 | @plugin(audio) | loop | resources/audio/theme.wav | bgm
     * </pre>
     * <p>老地图残留的 {@code audio} / {@code type = music} 会在解析时给出迁移提示，
     * 但不再自动播放（属性会被原样保留在脚本里，不会丢内容）。</p>
     */
    private void playSceneMusic(GameScene s) {
        if (s == null) return;
        for (StoryNode n : s.nodes()) {
            if (n.extras().containsKey("audio")) {
                Logs.warn("[Flow] 节点「" + n.getId() + "」的 audio 属性已废弃（不再播放）："
                        + "请改用槽 @plugin(audio) | loop | 路径 | bgm");
            }
            if (n.extras().containsKey("type") || n.extras().containsKey("类型")) {
                Logs.warn("[Flow] 节点「" + n.getId() + "」用了已废弃的节点类型，请改用 toast 等现有类型");
            }
        }
    }

    // =====================================================================
    // 节点构建（按类型渲染）
    // =====================================================================

    private Node buildNode(StoryNode node) {
        double w = node.getWidth() > 0 ? node.getWidth() : CW;
        double h = node.getHeight() > 0 ? node.getHeight() : CH;
        double fs = node.getFontSize() > 0 ? node.getFontSize()
                : (node.getType() == NodeType.DIALOG ? 21 : 17);

        // 该节点设了视频素材 → 用视频播放器渲染它（可当“会动的背景图”用）
        if (node.getVideo() != null && !node.getVideo().isBlank()) {
            return buildVideoView(node, w, h);
        }
        return switch (node.getType()) {
            case BACKGROUND -> buildImageView(node, w, h, false, true);
            case CHARACTER -> buildImageView(node, w, h, true, false);
            case DIALOG -> buildDialog(node, w, h, fs);
            case NAME -> buildRichLabel(node, w, h, fs, "#ffe2a6");
            case TEXT -> buildRichLabel(node, w, h, fs, DEFAULT_TEXT_COLOR);
            case TEXTBOX -> buildTextBox(node, w, h, fs);
            case BUTTON -> buildButton(node);
            case TOAST -> buildToast(node, w, h);
        };
    }

    /**
     * 系统提示条（系统提示节点）：带<b>自带默认样式</b>的小圆角条，放在屏幕角上（新建时默认右上角）。
     *
     * <p>样式：深色半透明底 + 淡蓝描边 + 圆角 + 投影 + 白字，不依赖任何美术素材；
     * 工程师在节点 style 里写的内联样式会叠加在默认样式之后（可覆盖颜色/边框等）。
     * 它就是普通节点：显不显示看 {@code visible}，要淡入淡出可以在槽里 set opacity，
     * 或者给节点写 {@code transition = opacity:300ms}。</p>
     */
    private Node buildToast(StoryNode node, double w, double h) {
        Label label = new Label(node.getText() == null || node.getText().isBlank() ? "提示" : node.getText());
        label.setWrapText(true);
        label.setMaxWidth(Math.max(40, w - 28));
        label.setTextFill(javafx.scene.paint.Color.WHITE);
        label.setStyle("-fx-font-size: " + (node.getFontSize() > 0 ? StoryNode.trimDouble(node.getFontSize()) : "18") + "px;"
                + "-fx-text-alignment: " + ("center".equalsIgnoreCase(node.getAlign()) ? "center" : "left") + ";");
        StackPane box = new StackPane(label);
        box.setAlignment(alignmentOf(node.getAlign()));
        box.setStyle("-fx-background-color: rgba(14,18,32,0.88);"
                + "-fx-background-radius: 12;"
                + "-fx-border-color: rgba(120,170,255,0.45); -fx-border-radius: 12; -fx-border-width: 1;"
                + "-fx-padding: 9 18 9 18;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.55), 14, 0.2, 0, 4);"
                + (node.getStyle() == null ? "" : node.getStyle()));
        StackPane holder = new StackPane(box);
        holder.setPrefSize(Math.max(2, w), Math.max(2, h));
        holder.setMinSize(Math.max(2, w), Math.max(2, h));
        holder.setMaxSize(Math.max(2, w), Math.max(2, h));
        holder.setAlignment(Pos.CENTER);
        return holder;
    }

    private static Pos alignmentOf(String align) {
        if (align == null) return Pos.CENTER_LEFT;
        return switch (align.toLowerCase(java.util.Locale.ROOT)) {
            case "center" -> Pos.CENTER;
            case "right" -> Pos.CENTER_RIGHT;
            default -> Pos.CENTER_LEFT;
        };
    }

    /**
     * 视频节点视图：MediaView 填满节点框（背景类节点按拉伸填充，其它按等比适配）。
     * <p>素材缺失/格式不支持时退化为占位块（🎬 + 路径），不会让剧情崩掉。</p>
     */
    private Node buildVideoView(StoryNode node, double w, double h) {
        String rel = node.getVideo();
        File f = resolveAsset(rel);
        boolean fill = node.getType() == NodeType.BACKGROUND;
        if (f == null || !f.isFile()) {
            return videoPlaceholder(node, w, h, "找不到视频: " + rel);
        }
        try {
            Media media = new Media(f.toURI().toString());
            MediaPlayer player = new MediaPlayer(media);
            boolean loop = StoryNode.parseBoolSafe(videoProp(node.getId(), "videoloop", "true"), true);
            player.setCycleCount(loop ? MediaPlayer.INDEFINITE : 1);
            player.setVolume(clamp(StoryNode.parseDoubleSafe(
                    videoProp(node.getId(), "videovolume", String.valueOf(masterVolume())), masterVolume()), 0, 1));
            MediaView mv = new MediaView(player);
            mv.setFitWidth(w);
            mv.setFitHeight(h);
            mv.setPreserveRatio(!fill);
            applyStyle(mv, node.getStyle());
            player.setOnError(() -> Logs.warn("[Video] 播放失败 " + rel + "：" + player.getError()));
            player.play();
            disposeVideo(node.getId());
            videoPlayers.put(node.getId(), player);
            return mv;
        } catch (RuntimeException e) {
            Logs.warn("[Video] 无法加载视频 " + rel + "（" + e.getMessage() + "）");
            return videoPlaceholder(node, w, h, "视频无法播放: " + rel);
        }
    }

    /** 视频占位块：素材缺失或格式不支持时显示，保证画面结构不变 */
    private Node videoPlaceholder(StoryNode node, double w, double h, String msg) {
        javafx.scene.control.Label lb = new javafx.scene.control.Label("🎬 " + msg);
        lb.setTextFill(javafx.scene.paint.Color.rgb(255, 215, 106));
        lb.setStyle("-fx-background-color: rgba(24,26,42,0.9); -fx-padding: 6 12 6 12;"
                + "-fx-background-radius: 10; -fx-border-color: rgba(255,215,106,0.5); -fx-border-radius: 10;");
        StackPane box = new StackPane(lb);
        box.setPrefSize(w, h);
        box.setMinSize(w, h);
        box.setMaxSize(w, h);
        if (node.getType() != NodeType.BACKGROUND) applyStyle(box, node.getStyle());
        return box;
    }

    /** 把相对路径解析成地图目录下的文件（也支持绝对路径） */
    /**
     * 解析素材路径：<b>地图目录优先 → classpath 回退</b>（与图片一致）。
     *
     * <p>classpath 里的音频若打成 jar（{@code jar:file:…}），{@code Media} 无法直接播放，
     * 因此把该资源释放成临时文件再交给播放器。都找不到时返回地图内路径，由调用方按缺失处理。</p>
     */
    private File resolveAsset(String rel) {
        if (rel == null || rel.isBlank()) return null;
        File f = new File(rel);
        if (f.isAbsolute()) return f;
        String cp = rel.replace('\\', '/');
        File inMap = mapDir == null ? f : new File(mapDir, cp);
        if (inMap.isFile()) return inMap;
        java.net.URL url = ReaderView.class.getClassLoader().getResource(cp);
        if (url == null) return inMap;
        try {
            if ("file".equalsIgnoreCase(url.getProtocol())) {
                return new File(url.toURI());
            }
            File tmp = File.createTempFile("dsa-asset-", "-" + new File(cp).getName());
            tmp.deleteOnExit();
            try (java.io.InputStream in = url.openStream()) {
                java.nio.file.Files.copy(in, tmp.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return tmp;
        } catch (Exception e) {
            Logs.warn("[Audio] classpath 资源解析失败 " + rel + "：" + e.getMessage());
            return inMap;
        }
    }

    /** 读节点的运行时属性覆盖（视频控制用） */
    private String videoProp(String nodeId, String prop, String def) {
        return flowVars.prop(nodeId, prop, def);
    }

    private void disposeVideo(String nodeId) {
        MediaPlayer old = videoPlayers.remove(nodeId);
        if (old != null) {
            try {
                old.stop();
                old.dispose();
            } catch (RuntimeException ignored) {
                // 忽略
            }
        }
    }

    private void disposeAllVideos() {
        for (MediaPlayer p : new java.util.ArrayList<>(videoPlayers.values())) {
            try {
                p.stop();
                p.dispose();
            } catch (RuntimeException ignored) {
                // 忽略
            }
        }
        videoPlayers.clear();
    }

    // =====================================================================
    // 音频通道：插件（@plugin(audio)）与旧节点 audio 属性共用
    // =====================================================================

    /** 播放到某个通道；channel 为空时默认 bgm（循环）或 se（一次性） */
    public void playAudioChannel(String channel, String relPath, boolean loop, double vol) {
        String ch = (channel == null || channel.isBlank()) ? (loop ? "bgm" : "se") : channel.trim();
        if (relPath == null || relPath.isBlank()) {
            stopAudioChannel(ch);
            return;
        }
        File f = resolveAsset(relPath);
        if (f == null || !f.isFile()) {
            // 缺失静默失败（只记日志）：剧本引用的素材可能尚未到位，不该打断剧情
            Logs.info("[Audio] 音频未就位，跳过: " + relPath + "（通道 " + ch + "）");
            return;
        }
        runOnUiThread(() -> {
            stopAudioChannel(ch);
            try {
                Media media = new Media(f.toURI().toString());
                MediaPlayer p = new MediaPlayer(media);
                p.setCycleCount(loop ? MediaPlayer.INDEFINITE : 1);
                p.setVolume(clamp(vol, 0, 1));
                p.setOnError(() -> {
                    Logs.warn("[Audio] 播放失败 " + relPath + "：" + p.getError());
                    p.dispose();
                    audioChannels.remove(ch, p);
                });
                if (!loop) {
                    // 一次性音效（SE）播完即释放 —— SE 用独立通道并发，不释放会累积声部
                    p.setOnEndOfMedia(() -> {
                        p.stop();
                        p.dispose();
                        audioChannels.remove(ch, p);
                    });
                }
                p.play();
                audioChannels.put(ch, p);
            } catch (RuntimeException e) {
                Logs.warn("[Audio] 无法播放 " + relPath + "（" + e.getMessage() + "）");
            }
        });
    }

    public void stopAudioChannel(String channel) {
        String ch = channel == null || channel.isBlank() ? "bgm" : channel.trim();
        MediaPlayer p = audioChannels.remove(ch);
        if (p != null) {
            try {
                p.stop();
                p.dispose();
            } catch (RuntimeException ignored) {
                // 忽略
            }
        }
    }

    public boolean pauseAudioChannel(String channel, boolean pause) {
        MediaPlayer p = audioChannels.get(channel == null || channel.isBlank() ? "bgm" : channel.trim());
        if (p == null) return false;
        try {
            if (pause) p.pause(); else p.play();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public boolean setAudioChannelVolume(String channel, double vol) {
        MediaPlayer p = audioChannels.get(channel == null || channel.isBlank() ? "bgm" : channel.trim());
        if (p == null) return false;
        p.setVolume(clamp(vol, 0, 1));
        return true;
    }

    /** 当前有哪些音频通道在放（探针/调试用） */
    public java.util.Set<String> audioChannelNames() {
        return new java.util.LinkedHashSet<>(audioChannels.keySet());
    }

    /** 某节点是否挂着视频播放器（探针/调试用） */
    public boolean hasVideoPlayer(String nodeId) {
        return videoPlayers.containsKey(nodeId);
    }

    private ImageView buildImageView(StoryNode node, double w, double h,
                                     boolean preserveRatio, boolean stretch) {
        Image img = FxAssets.loadRooted(mapDir, node.getPath(), w, h, node.getId());
        ImageView iv = new ImageView(img);
        if (stretch) {
            iv.setFitWidth(w);
            iv.setFitHeight(h);
            iv.setPreserveRatio(false);
        } else {
            iv.setPreserveRatio(true);
            iv.setFitWidth(w);
            if (preserveRatio && h > 0) {
                // 同时约束高度（比例适配框内），JavaFX 会在双约束下取较小缩放
                iv.setFitHeight(h);
            }
        }
        applyStyle(iv, node.getStyle());
        return iv;
    }

    /** 普通富文本标签（名字/文本）：容器固定 w×h，文字按 width 自动换行 */
    private Node buildRichLabel(StoryNode node, double w, double h, double fs, String defColor) {
        StackPane box = new StackPane();
        box.setPrefSize(w, h);
        box.setMinSize(w, h);
        box.setMaxSize(w, h);
        String color = cssColor(node.getStyle(), defColor);
        TextFlow flow = RichText.flow(resolveText(node, node.getText()), fs, color);
        flow.setMaxWidth(Math.max(30, w - 24));
        flow.setTextAlignment(alignment(node.getAlign()));
        box.getChildren().add(flow);
        box.setMouseTransparent(true);
        applyStyle(box, node.getStyle());
        return box;
    }

    /**
     * 对话面板：半透明圆角底板 + 内部富文本。
     * <ul>
     *   <li>支持<b>多段台词</b>：text 中用独立一行 {@code ---} 分隔，点击面板自动切到下一段；</li>
     *   <li>逐字显示（typewriter）默认开启，可在节点属性中关闭；打字中点击 = 立即显示全文。</li>
     * </ul>
     */
    private Node buildDialog(StoryNode node, double w, double h, double fs) {
        String base = "-fx-background-color: rgba(13,14,28,0.78);"
                + "-fx-background-radius: 16px;"
                + "-fx-padding: 16px 26px 16px 26px;";
        String color = cssColor(node.getStyle(), DEFAULT_TEXT_COLOR);

        StackPane panel = new StackPane();
        panel.setPrefSize(w, h);
        panel.setMinSize(w, h);
        panel.setMaxSize(w, h);
        applyStyle(panel, base + node.getStyle());
        panel.getStyleClass().add("dialog-panel");



        TextFlow flow = RichText.flow("", fs, color);
        flow.setMaxWidth(Math.max(40, w - 56));
        flow.setTextAlignment(alignment(node.getAlign()));
        panel.getChildren().add(flow);

        DialogParagraph st = new DialogParagraph(node, flow, splitParagraphs(resolveText(node, node.getText())),
                fs, color, node.typewriterEffective());
        dialogs.add(st);

        // 点击对话面板：打字中 → 显示全文；已显示完 → 切到下一段台词
        panel.setOnMouseClicked(e -> onDialogClicked(st));
        FxAnim.makeHoverable(panel, 1.01, 0.99);
        showParagraph(st, 0);
        return panel;
    }

    /** 点击对话框：逐字中=显示全文；否则切下一段；已是最后一段 → 跳转“下一个场景” */
    // =====================================================================
    // UI 全局音（由播放器硬编码，不写进剧本；见《素材交接-剧情侧答复》§二）
    //   se_click  = 点击推进对话 / se_hover = 选项悬停 / se_select = 选项确认
    // 短音效用 AudioClip（低延迟、可并发），素材缺失一律静默跳过。
    // =====================================================================
    private final java.util.Map<String, javafx.scene.media.AudioClip> uiClips = new java.util.HashMap<>();
    private final java.util.Set<String> uiSoundMissing = new java.util.HashSet<>();
    private long lastHoverSoundAt = 0;

    /** 播放 UI 全局音；首次成功加载时记一条日志（便于实测核对） */
    private void playUiSound(String id) {
        if (uiSoundMissing.contains(id)) return;
        try {
            javafx.scene.media.AudioClip clip = uiClips.get(id);
            if (clip == null) {
                File f = resolveAsset("assets/sounds/" + id + ".wav");
                if (f == null || !f.isFile()) {
                    uiSoundMissing.add(id);
                    Logs.info("[Audio] UI 音效未就位，跳过: " + id);
                    return;
                }
                clip = new javafx.scene.media.AudioClip(f.toURI().toString());
                uiClips.put(id, clip);
                Logs.info("[Audio] UI 音效已就绪: " + id);
            }
            clip.play(clamp(masterVolume(), 0, 1));
        } catch (RuntimeException e) {
            uiSoundMissing.add(id);
            Logs.warn("[Audio] UI 音效不可用 " + id + "：" + e.getMessage());
        }
    }

    /** 悬停音限流：鼠标划过一串选项时不要连发 */
    private void playHoverSound() {
        long now = System.currentTimeMillis();
        if (now - lastHoverSoundAt < 60) return;
        lastHoverSoundAt = now;
        playUiSound("se_hover");
    }

    private void onDialogClicked(DialogParagraph st) {
        if (endingReached) return;   // 结局场景：不再推进
        playUiSound("se_click");
        if (st.busy) {
            finishParagraph(st);
            return;
        }
        if (st.index + 1 < st.paragraphs.length) {
            showParagraph(st, st.index + 1);
            return;
        }
        // 对话结束：跳转到节点 target 设置的“下一个场景”（没有则用场景 next）
        String next = st.node.getTarget() == null ? "" : st.node.getTarget().trim();
        if (next.isEmpty() && scene != null && scene.next() != null) {
            next = scene.next().trim();
        }
        if (next.isEmpty()) {
            toast("对话结束（可在节点属性里设置“下一个场景”）");
            return;
        }
        if (project == null || !project.hasScene(next)) {
            toast("对话结束：目标场景不存在 " + next);
            return;
        }
        Logs.info("[Player] 对话结束 → 跳转场景 [" + next + "]");
        renderScene(next, true, true);
    }

    private Button buildButton(StoryNode node) {
        Button btn = new Button(node.getText());
        btn.setPrefSize(node.getWidth() > 0 ? node.getWidth() : 140,
                node.getHeight() > 0 ? node.getHeight() : 52);
        btn.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        btn.getStyleClass().add("story-btn");
        applyStyle(btn, node.getStyle());
        btn.setOnAction(e -> {
            playUiSound("se_select");
            onStoryAction(node);
        });
        btn.setOnMouseEntered(e -> playHoverSound());
        FxAnim.makeInteractive(btn);
        return btn;
    }

    /**
     * 文本框节点：玩家可输入的输入控件。
     * <ul>
     *   <li>{@code multiline = false} → 单行 {@link TextField}；{@code true} → 多行 {@link TextArea}；</li>
     *   <li><b>初始内容</b>：绑定的存档变量已存在（读档/逻辑层写入过）时取变量当前值，
     *       否则用节点的 {@code text}——每次重建都重新取值，不缓存旧值；</li>
     *   <li><b>写回变量</b>：绑定非空时监听输入内容，按 [option] 声明类型写入存档变量
     *       （{@code setTyped}）。只“控件 → 变量”单向同步，绝不回写控件，避免死循环。</li>
     * </ul>
     * style/fontSize/visible/opacity/align 与其它节点类型保持一致的语义。
     */
    private Node buildTextBox(StoryNode node, double w, double h, double fs) {
        String bind = node.getBind() == null ? "" : node.getBind().trim();
        // 初始内容：绑定变量已存在（读档/逻辑层写过）→ 变量值优先；否则用节点 text（同样过表达式）
        String init;
        if (!bind.isEmpty() && flowVars.has(bind)) {
            init = flowVars.get(bind, node.getText());
        } else {
            init = resolveText(node, node.getText());
        }

        TextInputControl input = node.isMultiline() ? new TextArea() : new TextField();
        input.setText(init);
        if (input instanceof TextArea area) {
            area.setWrapText(true);
        }
        // 对齐：TextField 有 alignment 属性（Pos）；TextArea 在 JavaFX 中没有对应 API，
        //       多行时的对齐只能靠节点自身的 style 控制，这里不做处理
        if (input instanceof TextField field) {
            field.setAlignment(pos(node.getAlign()));
        }
        // 与编辑器预览/对话框一致的输入框外观；节点自身的 style 放在后面，可覆盖默认值
        String base = "-fx-background-color: rgba(12,14,26,0.85);"
                + "-fx-background-radius: 8px;"
                + "-fx-border-color: rgba(140,180,255,0.55); -fx-border-radius: 8px;"
                // TextArea 的底板由内部的 .content 绘制（modena 皮肤），需覆盖它的外观颜色变量，
                // 否则多行输入框会是系统默认亮色底
                + (input instanceof TextArea ? "-fx-control-inner-background: rgba(12,14,26,0.85);" : "")
                + "-fx-text-fill: " + cssColor(node.getStyle(), DEFAULT_TEXT_COLOR) + ";"
                + "-fx-prompt-text-fill: rgba(160,168,200,0.85);"
                + "-fx-font-size: " + StoryNode.trimDouble(fs) + "px;"
                + "-fx-padding: 6px 10px 6px 10px;";
        applyStyle(input, base + (node.getStyle() == null ? "" : node.getStyle()));
        input.setPrefSize(w, h);
        input.setMinSize(w, h);
        input.setMaxSize(w, h);

        if (!bind.isEmpty()) {
            input.setPromptText("→ 绑定变量: " + bind);
            // 用户输入 → 变量（setTyped 按 [option] 里声明的类型强制转换）；
            // 不在监听器里回写控件文本，因此不存在自触发死循环
            input.textProperty().addListener((obs, oldText, newText) -> {
                flowVars.setTyped(bind, newText == null ? "" : newText, saveVarDefs());
            });
        }
        return input;
    }

    private void applyStyle(Node node, String style) {
        if (style != null && !style.isBlank()) {
            node.setStyle(style);
        }
    }

    private static String cssColor(String style, String def) {
        if (style != null) {
            Matcher m = TEXT_FILL.matcher(style);
            if (m.find()) return m.group(1).trim();
        }
        return def;
    }

    // ---------- 文本里的表达式替换（@var / @node / @int …） ----------

    /**
     * 把文本里的表达式求值成实际文字：
     * {@code @var(玩家名)} / {@code @node(属性名)} / {@code @node(节点id,属性名)} /
     * {@code @int(x)} 等，其余文字原样保留。
     * <p>不含 {@code @} 的纯文本直接返回（{@link Expr#resolve} 内部也会原样返回），
     * 对话的 {@code ---} 分段与打字机都作用在“替换之后”的文本上。</p>
     */
    private String resolveText(StoryNode node, String text) {
        if (text == null) return "";
        if (text.indexOf('@') < 0) return text;
        return Expr.resolve(text, scopeOf(node));
    }

    /**
     * 文本渲染用的表达式作用域：只读存档变量与节点属性，
     * 没有事件参数（{@code @param} 恒为空串）。
     */
    private Expr.Scope scopeOf(StoryNode node) {
        return new Expr.Scope() {
            @Override
            public String var(String name, String def) {
                return flowVars.get(name, def);
            }

            @Override
            public void setVar(String name, String value) {
                flowVars.setTyped(name, value, saveVarDefs());
            }

            @Override
            public String nodeProp(String nodeId, String prop) {
                String id = (nodeId == null || nodeId.isBlank())
                        ? (node == null ? "" : node.getId()) : nodeId;
                if (id.isBlank()) return "";
                String v = property(id, prop);          // 先取引擎属性/逻辑层改过的覆盖值
                if (v == null || v.isEmpty()) {
                    StoryNode n = node(id);             // 再退回节点模型里的静态属性
                    if (n != null) v = SignalBus.nodeStaticProp(n, prop);
                }
                return v == null ? "" : v;
            }

            @Override
            public String sourceNodeId() {
                return node == null ? "" : node.getId();
            }

            @Override
            public String param(String name) {
                return "";                              // 文本渲染没有事件参数
            }
        };
    }

    private static javafx.scene.text.TextAlignment alignment(String align) {
        return switch (align == null ? "" : align.toLowerCase()) {
            case "center" -> javafx.scene.text.TextAlignment.CENTER;
            case "right" -> javafx.scene.text.TextAlignment.RIGHT;
            default -> javafx.scene.text.TextAlignment.LEFT;
        };
    }

    /** 同上，但用于控件（TextField 的 alignment 是 {@link Pos}） */
    private static Pos pos(String align) {
        return switch (align == null ? "" : align.toLowerCase()) {
            case "center" -> Pos.CENTER;
            case "right" -> Pos.CENTER_RIGHT;
            default -> Pos.CENTER_LEFT;
        };
    }

    // =====================================================================
    // 对话段落引擎：--- 分隔 + 逐字显示
    // =====================================================================

    /** 把整段 text 按“独立一行的 --- ”切成多段台词（渲染层行为，不改动脚本存储） */
    static String[] splitParagraphs(String text) {
        if (text == null || text.isBlank()) return new String[]{" "};
        String[] rawLines = text.split("\n", -1);
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String rawLine : rawLines) {
            if (rawLine.strip().equals("---")) {
                flushParagraph(out, cur);
                cur.setLength(0);
            } else {
                if (cur.length() > 0) cur.append('\n');
                cur.append(rawLine);
            }
        }
        flushParagraph(out, cur);
        if (out.isEmpty()) out.add(" ");
        return out.toArray(new String[0]);
    }

    private static void flushParagraph(java.util.ArrayList<String> out, StringBuilder cur) {
        String s = cur.toString();
        while (s.startsWith("\n")) s = s.substring(1);
        while (s.endsWith("\n")) s = s.substring(0, s.length() - 1);
        if (!s.isBlank()) out.add(s);
    }

    /** 显示第 idx 段：开启逐字则打字机揭示，否则整段立即显示 */
    private void showParagraph(DialogParagraph st, int idx) {
        if (idx < 0 || idx >= st.paragraphs.length) return;
        st.index = idx;
        // 把“当前段落序号”写入变量：地图逻辑层可据此实现轮流高亮等效果
        if (st.node != null && !st.node.getId().isBlank()) {
            flowVars.set("对话段." + st.node.getId(), String.valueOf(idx));
        }
        if (st.timer != null) {
            st.timer.stop();
            st.timer = null;
        }
        st.busy = false;
        String para = st.paragraphs[idx];
        if (!st.typeOn || para.isBlank()) {
            setParagraphFlow(st, -1);
            return;
        }
        final int total = RichText.visibleLength(para);
        final int[] revealed = {0};
        // 标点停顿：句读（，、；：）停约 80ms、句末（。！？…—）停约 160ms —— 打字节奏更接近"有人在说话"
        final String plainPara = RichText.plain(para);
        final int[] pauseTicks = {0};
        st.busy = true;
        st.timer = new Timeline(new KeyFrame(Duration.millis(40), e -> {
            if (!st.busy) {
                if (st.timer != null) st.timer.stop();
                return;
            }
            if (pauseTicks[0] > 0) {
                pauseTicks[0]--;
                return;
            }
            double ms = (turbo ? Math.max(1.2, typeSpeed * 0.12) : typeSpeed);
            int step = Math.max(1, (int) Math.ceil(40.0 / ms));
            revealed[0] = Math.min(total, revealed[0] + step);
            setParagraphFlow(st, revealed[0]);
            if (revealed[0] >= total) {
                st.busy = false;
                st.timer.stop();
                st.timer = null;
                return;
            }
            if (!turbo && revealed[0] > 0 && revealed[0] <= plainPara.length()) {
                char just = plainPara.charAt(revealed[0] - 1);
                if ("，、；：".indexOf(just) >= 0) {
                    pauseTicks[0] = 2;
                } else if ("。！？…—".indexOf(just) >= 0) {
                    pauseTicks[0] = 4;
                }
            }
        }));
        st.timer.setCycleCount(Timeline.INDEFINITE);
        st.timer.play();
    }

    /** 把段落按揭示字数渲染进 TextFlow（-1 = 全文） */
    private void setParagraphFlow(DialogParagraph st, int count) {
        TextFlow fresh = RichText.toFlow(st.paragraphs[st.index], st.baseSize, st.color, count);
        st.flow.getChildren().setAll(fresh.getChildren());
    }

    /** 立即显示当前段全文（跳过/点击时调用） */
    private void finishParagraph(DialogParagraph st) {
        if (st.timer != null) {
            st.timer.stop();
            st.timer = null;
        }
        st.busy = false;
        setParagraphFlow(st, -1);
    }

    /** 结束当前场景全部对话框的逐字动画 */
    private void clearDialogs() {
        for (DialogParagraph st : dialogs) {
            if (st.timer != null) {
                st.timer.stop();
                st.timer = null;
            }
            st.busy = false;
        }
        dialogs.clear();
    }

    // =====================================================================
    // 按钮动作（存档/读档/target/event …）
    // 已移除：skip（跳过台词）、speed（加速）——旧地图里残留这两个动作会走 default 分支
    // =====================================================================

    private void onStoryAction(StoryNode node) {
        String action = node.getAction() == null ? "" : node.getAction().trim();
        switch (action) {
            case "save" -> performSave(node);
            case "load" -> performLoad(node);
            case "target" -> {
                String target = node.getTarget() == null ? "" : node.getTarget().trim();
                if (!project.hasScene(target)) {
                    Ui.warn(stage, "无法跳转", "目标场景不存在: " + target);
                } else {
                    renderScene(target, true, true);
                }
            }
            case "event" -> {
                String id = node.getEvent() == null ? "" : node.getEvent().trim();
                if (id.isEmpty()) {
                    toast("按钮事件为空");
                } else {
                    pluginReturnScene = sceneName; // 按钮事件：返回当前剧情场景
                    runPlugin(id, "按钮事件 [" + node.getId() + "]");
                }
            }
            default -> {
                if (action.isBlank()) {
                    // 没有 action：该节点只靠“信号/槽”驱动，引擎保持安静
                    Logs.info("[Player] 节点 " + node.getId() + " 无 action（交由信号/槽处理）");
                } else {
                    Logs.info("[Player] 节点 " + node.getId() + " 动作<" + action + ">：占位（无绑定行为，仅记录日志）");
                    toast("动作 " + action + "（占位）");
                }
            }
        }
    }

    // =====================================================================
    // 存档 / 读档核心（SavePortal 门户 + 按钮动作共用）
    // =====================================================================

    /** 按钮/节点 target 作为存档文件名；留空用 slot1 */
    private String saveFileOf(StoryNode node) {
        String t = node.getTarget() == null ? "" : node.getTarget().trim();
        return t.isEmpty() ? "slot1" : t;
    }

    // ---------- SavePortal 实现（插件/地图工程师后端使用） ----------

    @Override
    public GameSaveManager manager() {
        return saveManager;
    }

    @Override
    public String currentScene() {
        return sceneName;
    }

    /** 把当前进度写入槽位：内置 scene 变量 + 全部 SaveHook 补充变量 */
    @Override
    public boolean saveTo(String slot) {
        if (saveManager == null) return false;
        String name;
        try {
            name = GameSaveManager.normalizeName(slot);
        } catch (java.io.IOException e) {
            Logs.warn("非法存档槽名: " + slot);
            return false;
        }
        SaveData data = new SaveData();
        data.put("scene", sceneName == null ? "" : sceneName);
        for (SaveHook hook : saveHooks) {
            try {
                hook.onEngineSave(data);
            } catch (RuntimeException e) {
                Logs.warn("SaveHook.onEngineSave 异常: " + e);
            }
        }
        try {
            saveManager.write(name, data);
            Logs.info("[Player] 存档完成: " + name + " 变量数=" + data.keys().size());
            return true;
        } catch (java.io.IOException e) {
            Logs.error("存档写入失败: " + name, e);
            return false;
        }
    }

    /** 读取槽位并应用：SaveHook 还原 → 跳转存档场景 → （若在插件层内）自动退出插件 */
    @Override
    public boolean loadFrom(String slot) {
        if (saveManager == null) return false;
        String name;
        try {
            name = GameSaveManager.normalizeName(slot);
        } catch (java.io.IOException e) {
            return false;
        }
        SaveData data = saveManager.read(name);
        if (data == null) {
            Logs.info("读档失败：存档不存在 " + name);
            return false;
        }
        for (SaveHook hook : saveHooks) {
            try {
                hook.onEngineLoad(data);
            } catch (RuntimeException e) {
                Logs.warn("SaveHook.onEngineLoad 异常: " + e);
            }
        }
        // 顺序保证：SaveHook 里已先 readFrom(存档)，这里再用 [option] 声明补齐缺失的初值
        // —— 存档里已有的值优先，只有存档中没有的变量才会拿到初值
        if (project != null) flowVars.applyDefaults(project.option().saveVars());
        String scene = data.getString("scene", "");
        if (scene.isBlank()) {
            return true; // 无 scene 变量也视为读取成功（变量由 SaveHook 消费）
        }
        if (!project.hasScene(scene)) {
            Logs.warn("存档中的场景不存在: " + scene);
            return false;
        }
        Logs.info("[Player] 读档完成: " + name + " 目标场景=" + scene);
        renderScene(scene, true, false);
        exitPluginIfShown(); // 由存档界面触发的读取：落地后自动关掉插件层
        return true;
    }

    /** 插件层若正打开，则就地收起（保留刚渲染出的目标场景） */
    private void exitPluginIfShown() {
        if (!pluginMode()) return;
        pluginLayer.setVisible(false);
        pluginLayer.setManaged(false);
        pluginContent.setCenter(null);
        previousScene = null;
        pluginReturnScene = null;
        suppressSceneEvent = true;
        // 这也是“插件被移出主舞台”的一种：从存档界面读档时同样要回调 onDetach
        detachActivePlugin("读档收起插件层");
    }

    // ---------- 故事按钮动作（save/load） ----------

    private void performSave(StoryNode node) {
        String name = saveFileOf(node);
        boolean ok = saveTo(name);
        toast(ok ? "已存档 → saves/" + name : "存档失败（详见控制台）");
    }

    private void performLoad(StoryNode node) {
        String name = saveFileOf(node);
        boolean ok = loadFrom(name);
        if (ok) {
            String scene = null;
            try {
                SaveData d = manager().read(name);
                if (d != null) scene = d.getString("scene", "");
            } catch (Exception ignored) { }
            toast((scene == null || scene.isBlank())
                    ? "已读档 saves/" + name
                    : "读档 → 场景 [" + scene + "]");
        } else {
            toast("读档失败：saves/" + name + "（不存在或场景无效）");
        }
    }


    // =====================================================================
    // 插件调用与界面包装（浏览器标签页式嵌入）
    // =====================================================================

    /** 调度插件：加载 → execute → 若提供嵌入视图则包装进主舞台 */
    private void runPlugin(String eventId, String source) {
        if (pluginMode()) return;
        try {
            GamePlugin plugin = pluginLoader.load(eventId);
            // 上一个插件（不同实例）先走 onDetach：避免旧插件的线程/计时器继续跑
            if (activePlugin != null && activePlugin != plugin) {
                detachActivePlugin("切换到插件 " + eventId);
            }
            activePlugin = plugin;
            activePluginId = eventId;
            // 插件若同时实现 SaveHook，则自动注册参与后续 存档/读档
            if (plugin instanceof SaveHook h && !saveHooks.contains(h)) {
                saveHooks.add(h);
                Logs.plugin(eventId, "已注册 SaveHook: " + plugin.displayName());
            }
            Map<String, Object> params = new HashMap<>();
            params.put(GamePlugin.PARAM_MAP_FOLDER, mapDir);
            params.put(GamePlugin.PARAM_PLUGIN_ID, eventId);
            params.put(GamePlugin.PARAM_EMBEDDED, Boolean.TRUE);
            params.put(GamePlugin.PARAM_HOST_STAGE, stage);
            params.put(GamePlugin.PARAM_BACK_CALLBACK, (Runnable) this::leavePlugin);
            params.put(GamePlugin.PARAM_SAVES, this); // SavePortal：存档管理/读写任意槽位
            params.put(GamePlugin.PARAM_RESULT_SINK,
                    (java.util.function.Consumer<com.studio.plugin.MiniGameResult>) this::onMiniGameResult);
            params.put(GamePlugin.PARAM_FLAGS, miniGameFlags());
            // 逐动作音效通道：插件调 se("se_xxx") → 引擎按 assets/sounds/se_xxx.wav 播放（缺失静默）
            com.studio.plugin.kit.Se.attach(params);
            params.put(GamePlugin.PARAM_SE,
                    (java.util.function.Consumer<String>) id -> {
                        if (id == null || id.isBlank()) return;
                        playAudioChannel("se_" + id, "assets/sounds/" + id + ".wav", false,
                                clamp(masterVolume() * 0.9, 0, 1));
                    });

            Logs.plugin(eventId, "触发来源: " + source);
            plugin.execute(stage, params);

            Parent view = plugin.createEmbeddedView(params);
            if (view != null) {
                embedPlugin(plugin, eventId, view);
            } else {
                Logs.plugin(eventId, "未提供嵌入视图，按窗口模式运行（插件自行管理窗口）");
            }
        } catch (PluginLoader.PluginException e) {
            Ui.warn(stage, "插件加载失败", e.getMessage());
        } catch (RuntimeException e) {
            Logs.error("插件运行异常: " + eventId, e);
            Ui.error(stage, "插件运行异常", "插件 " + eventId + " 抛出异常：" + e, e.getCause());
        }
    }

    /** 图标 id 与底图 id 不一致的少数情况（美术侧命名） */
    private static String iconIdOf(String eventId) {
        if (eventId == null) return "";
        return "minesweeper".equalsIgnoreCase(eventId) ? "mine" : eventId;
    }

    private boolean pluginMode() {
        return pluginLayer.isVisible();
    }

    /** 把插件 Parent 放入主舞台中央的嵌入层，顶部生成“返回”标题栏 */
    private void embedPlugin(GamePlugin plugin, String eventId, Parent view) {
        clearDialogs();
        // 防御：同一时刻只允许托管一个插件（正常路径已由 runPlugin 的“替换即 detach”保证）
        if (activePlugin != null && activePlugin != plugin) {
            detachActivePlugin("切换到插件 " + eventId);
        }
        activePlugin = plugin;
        activePluginId = eventId;
        // 返回目标优先级：本次事件的指定场景 ＞ 当前展示场景
        previousScene = (pluginReturnScene != null && project.hasScene(pluginReturnScene))
                ? pluginReturnScene : sceneName;
        pluginReturnScene = null;
        pluginTitle.setText(plugin.displayName());

        pluginContent.setCenter(null);
        // 统一小游戏外壳：底图 mg_<事件id> + 共用外框 + 图标标题行（插件的 UI 不变）
        javafx.scene.Parent wrapped = com.studio.plugin.kit.GameShell.wrap(
                mapDir, eventId, iconIdOf(eventId), plugin.displayName(), view);
        pluginContent.setCenter(wrapped);
        pluginLayer.setVisible(true);
        pluginLayer.setManaged(true);
        com.studio.plugin.kit.Se.play("se_start");   // 通用开始音（9 个游戏共用）

        // 小游戏 BGM：独立通道（bgm_plugin），离开小游戏即停，不影响剧情 BGM
        String battle = com.studio.util.Bgm.relPath("battle");
        if (!battle.isEmpty()) {
            playAudioChannel("bgm_plugin", battle, true, clamp(masterVolume() * 0.7, 0, 1));
            Logs.info("[Bgm] 小游戏音乐 [battle] " + battle);
        } else {
            Logs.info("[Bgm] 无小游戏音乐可用（battle 类为空）");
        }
    }

    /** 点击【返回】：移除嵌入层，回到进入插件前的场景 */
    private void leavePlugin() {
        if (!pluginMode()) return;
        pluginLayer.setVisible(false);
        pluginLayer.setManaged(false);
        pluginContent.setCenter(null);
        com.studio.plugin.kit.Se.detach();
        stopAudioChannel("bgm_plugin");   // 小游戏 BGM 收掉
        detachActivePlugin("返回剧情");   // 插件从主舞台移除 → 回调 onDetach

        // —— 小游戏结果路由（脚本 @minigame 的 mg.onWin / mg.onLose）——
        String onWin = mgOnWin;
        String onLose = mgOnLose;
        String mode = mgMode;
        String loop = mgLoop;
        com.studio.plugin.MiniGameResult result = pendingResult;
        pendingResult = null;
        mgMode = "";
        mgOnWin = "";
        mgOnLose = "";
        mgWith = "";
        mgLoop = "";

        boolean hasRoute = (onWin != null && !onWin.isBlank()) || (onLose != null && !onLose.isBlank());
        if (hasRoute) {
            if (result == null) {
                Logs.plugin(activePluginId, "未回传结果 → 按胜利处理并继续剧情");
            } else {
                Logs.plugin(activePluginId, "结果回传: " + result);
            }
            boolean win = (result == null) || result.win();
            com.studio.plugin.kit.Se.play(win ? "se_win" : "se_lose");   // 通用胜负音

            // mode:retry —— 失败重入 loop: 标签，并自增 retry_count（引擎侧自增，保证"重试不丢剧情进度"）
            if (!win && "retry".equalsIgnoreCase(mode) && loop != null && !loop.isBlank()) {
                int retries = flowVars.getInt("retry_count", 0) + 1;
                flowVars.set("retry_count", String.valueOf(retries));
                Logs.plugin(activePluginId, "调度模式 retry：第 " + retries + " 次重试 → 重入 " + loop);
                if (project.hasScene(loop)) {
                    suppressSceneEvent = false;
                    renderScene(loop, true, true);
                    return;
                }
                Logs.warn("[Flow] retry 的重入场景不存在：" + loop);
            }
            if (!win && "ending".equalsIgnoreCase(mode)) {
                // 结局模式：失败即走 onLose 的 Bad End 段（段末有 @ending 会停住），不提供重试
                Logs.plugin(activePluginId, "调度模式 ending：失败进入结局段 → " + onLose);
            }

            String target = win ? onWin : onLose;
            if (target != null && !target.isBlank() && project.hasScene(target)) {
                suppressSceneEvent = false;
                renderScene(target, true, true);
                return;
            }
            if (target != null && !target.isBlank()) {
                Logs.warn("[Flow] 小游戏结果目标场景不存在: " + target);
            }
        }

        suppressSceneEvent = true; // 防止场景事件再次把玩家拉回插件
        if (previousScene != null && project.hasScene(previousScene)) {
            renderScene(previousScene, false, false);
        }
    }

    /** 读场景自定义属性（mg.* 等；不存在返回空串） */
    private static String sceneProp(com.studio.model.GameScene s, String key) {
        if (s == null) return "";
        String v = s.prop(key);
        return v == null ? "" : v;
    }

    /** 组装 {@code @minigame with:} 列出的 flag 当前值，供小游戏做难度/形态 */
    private java.util.Map<String, String> miniGameFlags() {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        if (mgWith != null) {
            for (String rawFlag : mgWith.split(",")) {
                String n = rawFlag.trim();
                if (!n.isEmpty()) m.put(n, flowVars.get(n, "0"));
            }
        }
        return m;
    }

    /** 小游戏结果回传口：记录结果，待插件收起时按 mg.onWin / mg.onLose 路由 */
    private void onMiniGameResult(com.studio.plugin.MiniGameResult result) {
        pendingResult = result == null ? com.studio.plugin.MiniGameResult.win(0) : result;
        Logs.plugin(activePluginId, "收到小游戏结果: " + pendingResult);
    }

    // =====================================================================
    // 信号 / 槽 引擎侧（FlowHost 实现）
    // =====================================================================

    // =====================================================================
    // 控制台（调试浮层）
    // =====================================================================

    /** 由编辑器「在播放器中测试」设置：{@code -Dstudio.console=1} → 不论地图开关都能开控制台 */
    public static final String CONSOLE_FORCE_PROP = "studio.console";

    /**
     * 控制台是否允许打开：地图 {@code [option] console = true}，或由编辑器播放测试强制允许。
     * <p>旧地图没有这一行 → 默认关闭（向后兼容）。</p>
     */
    public boolean consoleAllowed() {
        String forced = System.getProperty(CONSOLE_FORCE_PROP, "");
        if ("1".equals(forced) || "true".equalsIgnoreCase(forced)) return true;
        return project != null && project.option() != null && project.option().consoleEnabled();
    }

    public boolean consoleVisible() {
        return console != null && console.isVisible();
    }

    /** 快捷键处理：开/关控制台；开着时 Esc 也能关 */
    private void onConsoleKeyPressed(KeyEvent e) {
        if (consoleVisible()) {
            if (e.getCode() == KeyCode.ESCAPE || matchesConsoleKey(e)) {
                hideConsole();
                e.consume();
            }
            return;
        }
        if (matchesConsoleKey(e)) {
            if (consoleAllowed()) {
                showConsole();
                e.consume();
            }
            // 没启用时不动它：这个键可能被剧情当成按键信号用
        }
    }

    /** 打开控制台（未启用时给出提示） */
    public void showConsole() {
        if (!consoleAllowed()) {
            toast("控制台未启用：在地图 [option] 里写 console = true 后重开");
            return;
        }
        if (console == null) {
            console = new ConsoleView(new ConsoleHost(), project == null ? null : project.option());
            // 内置一组「引擎」扩展变量：控制台的「扩展变量」按钮一按就有内容，也方便报 bug 时截图
            ConsoleRegistry.registerEnvProvider("引擎", this::engineEnvVars);
        }
        // 兜底：万一节点被从场景图里摘掉过（例如外部代码清理过 mainStack），这里补回去
        if (console.getParent() == null) mainStack.getChildren().add(console);
        console.setVisible(true);
        console.setManaged(true);
        console.refreshAll();          // 重新打开时刷新场景/变量/存档，避免看到旧数据
        console.focusInput();
        Logs.info("[Console] 控制台已打开（快捷键 " + consoleKeyText() + "）");
    }

    public void hideConsole() {
        if (console != null) console.close();
    }

    public void toggleConsole() {
        if (consoleVisible()) hideConsole(); else showConsole();
    }

    /** 控制台浮层（没创建过返回 null）—— 供探针/自动化使用 */
    public ConsoleView consoleView() { return console; }

    /** 当前地图工程（控制台/插件/探针读场景列表与 [option] 用） */
    public GameProject project() { return project; }

    /** 内置「引擎」扩展变量（控制台 env 指令 / 扩展变量按钮显示这些只读信息） */
    private Map<String, String> engineEnvVars() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("平台", System.getProperty("os.name", "?") + " " + System.getProperty("os.version", ""));
        m.put("Java", System.getProperty("java.version", "?"));
        m.put("地图", project == null ? "-" : project.name());
        m.put("地图目录", mapDir == null ? "-" : mapDir.getAbsolutePath());
        m.put("当前场景", scene == null ? "-" : scene.getName());
        m.put("场景数", project == null ? "0" : String.valueOf(project.scenes().size()));
        m.put("当前场景节点数", scene == null ? "0" : String.valueOf(scene.nodes().size()));
        FlowVariables vars = variables();
        m.put("变量数", vars == null ? "0" : String.valueOf(vars.mapVars().size()));
        m.put("控制台", consoleAllowed() ? "允许（快捷键 " + consoleKeyText() + "）" : "未启用");
        return m;
    }

    private String consoleKeyText() {
        return project == null || project.option() == null ? "`" : project.option().consoleKey();
    }

    /**
     * 按键是否匹配 {@code [option] consoleKey}。
     * <p>支持三类写法：单字符（{@code `} {@code ~} {@code /} {@code ;} …）、
     * 键名（{@code F1}…{@code F12} / {@code ESCAPE} / {@code TAB} / {@code BACK_QUOTE} …）、
     * 以及中文常用称呼（如 {@code 波浪键}）。</p>
     */
    public boolean matchesConsoleKey(KeyEvent e) {
        String spec = consoleKeyText();
        if (spec == null || spec.isBlank()) spec = "`";
        spec = spec.trim();

        String text = e.getText();
        if (spec.length() == 1) {
            char want = spec.charAt(0);
            if (text != null && !text.isEmpty() && text.charAt(0) == want) return true;
            // ` 与 ~ 是同一个物理键（Shift 差异），都认
            if (want == '`' || want == '~') return e.getCode() == KeyCode.BACK_QUOTE;
        }
        String norm = spec.toUpperCase(java.util.Locale.ROOT).replace(' ', '_').replace('-', '_');
        switch (norm) {
            case "`", "~", "BACKTICK", "BACK_QUOTE", "TILDE", "波浪键", "反引号" -> {
                if (e.getCode() == KeyCode.BACK_QUOTE) return true;
            }
            case "ESC", "ESCAPE", "退出键" -> {
                if (e.getCode() == KeyCode.ESCAPE) return true;
            }
            default -> { }
        }
        try {
            if (e.getCode() == KeyCode.valueOf(norm)) return true;
        } catch (IllegalArgumentException ignored) {
            // 不是键名，忽略
        }
        return false;
    }

    /** 控制台需要的宿主能力（场景列表 / 变量 / 存档 / 插件调用） */
    private final class ConsoleHost implements ConsoleRegistry.Context {

        @Override public String currentScene() { return scene == null ? null : scene.getName(); }

        @Override public List<String> scenes() {
            return project == null ? List.of() : new ArrayList<>(project.scenes().keySet());
        }

        @Override public boolean gotoScene(String name) {
            if (name == null || project == null || !project.hasScene(name.trim())) return false;
            runOnUiThread(() -> ReaderView.this.gotoScene(name.trim()));
            return true;
        }

        @Override public Map<String, String> variables() {
            FlowVariables vars = ReaderView.this.variables();
            return vars == null ? Map.of() : new LinkedHashMap<>(vars.mapVars());
        }

        @Override public void setVariable(String name, String value) {
            if (name == null || name.isBlank()) return;
            FlowVariables vars = ReaderView.this.variables();
            if (vars == null) return;
            List<com.studio.model.SaveVarDef> defs = saveVarDefs();
            if (defs != null && !defs.isEmpty()) vars.setTyped(name, value, defs);
            else vars.set(name, value);
        }

        @Override public int refreshAfterVariableChange() {
            if (Platform.isFxApplicationThread()) return ReaderView.this.refreshExpressionViews();
            Platform.runLater(ReaderView.this::refreshExpressionViews);
            return 0;
        }

        @Override public boolean emitSignal(String name, Map<String, Object> params) {
            if (!Platform.isFxApplicationThread()) {
                Platform.runLater(() -> ReaderView.this.emitConsoleSignal(name, params));
                return true;
            }
            return ReaderView.this.emitConsoleSignal(name, params);
        }

        @Override public List<String> saves() {
            GameSaveManager m = ReaderView.this.saves();
            return m == null ? List.of() : new ArrayList<>(m.listSaveFiles());
        }

        @Override public boolean save(String slot) { return saveSlot(slot); }

        @Override public boolean load(String slot) { return loadSlot(slot); }

        @Override public String invokePlugin(String idOrClass, List<String> args) {
            PluginRuntime rt = signalBus == null ? null : signalBus.plugins();
            if (rt == null) return "（插件运行时未就绪）";
            String[] in = args == null ? new String[0] : args.toArray(new String[0]);
            SignalEvent ev = SignalEvent.of("控制台", "console", "console",
                    scene == null ? "" : scene.getName(), new LinkedHashMap<>());
            String[] out = rt.execute(idOrClass, new FlowContext(ReaderView.this), ev, in);
            if (out == null) return "（插件没返回结果，或插件不存在：" + idOrClass + "）";
            return String.join(" | ", out);
        }

        @Override public void print(String line) {
            if (console != null) console.println(line);
        }
    }
    /** 全局键盘监听：把按键信号按“场景信号定义 + 各节点按键信号定义”分发 */
    private void installGlobalKeyListener() {
        Scene sc = stage.getScene();
        if (sc == null || keyListenerInstalled) return;
        keyListenerInstalled = true;
        // 控制台快捷键要最先看到按键（注册顺序 = 过滤器执行顺序）
        sc.addEventFilter(KeyEvent.KEY_PRESSED, this::onConsoleKeyPressed);
        sc.addEventFilter(KeyEvent.KEY_PRESSED, e -> dispatchKey(e, "press"));
        sc.addEventFilter(KeyEvent.KEY_RELEASED, e -> dispatchKey(e, "release"));
    }

    private void dispatchKey(KeyEvent e, String phase) {
        if (project == null || scene == null || pluginMode()) return;
        // 控制台开着时不派发按键信号：否则在控制台输入框里打字会触发剧情按键信号
        if (consoleVisible()) return;
        String code = e.getCode() == null ? "" : e.getCode().name();
        // ESC：交给宿主弹「继续 / 回到标题 / 退出」菜单（优先于剧本里的键盘信号）
        if ("ESCAPE".equals(code) && "press".equals(phase) && escHandler != null) {
            escHandler.run();
            return;
        }
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("key", code);
        params.put("keyText", e.getText());
        params.put("phase", phase);
        // 场景级键盘信号
        for (SignalDef def : scene.signals()) {
            if (def.getKind() != SignalDef.Kind.KEY) continue;
            if (!def.getKey().equalsIgnoreCase(code)) continue;
            if (!def.getKeyPhase().equalsIgnoreCase(phase)) continue;
            signalBus.emitFromScene(def.getName(), new LinkedHashMap<>(params));
        }
        // 节点级键盘信号（信号被关掉的节点不参与）
        for (StoryNode n : scene.nodes()) {
            if (!n.isSignalsEnabled()) continue;
            for (SignalDef def : n.signals()) {
                if (def.getKind() != SignalDef.Kind.KEY) continue;
                if (!def.getKey().equalsIgnoreCase(code)) continue;
                if (!def.getKeyPhase().equalsIgnoreCase(phase)) continue;
                signalBus.emitFromNode(n, def.getName(), new LinkedHashMap<>(params));
            }
        }
    }

    /** 给节点视图挂鼠标信号（click / release），与按钮自身 action 互不影响。
     *  使用事件过滤器：即使控件（如 Button）在冒泡阶段 consume 了事件也能收到。 */
    private void installMouseSignals(Node view, StoryNode node) {
        if (node.signals().isEmpty()) return;
        if (!node.isSignalsEnabled()) {   // 信号总开关关着：不挂任何鼠标信号
            Logs.info("[Flow] 节点「" + node.getId() + "」的信号已被关闭（signalsEnabled=false），跳过安装鼠标信号");
            return;
        }
        for (SignalDef def : node.signals()) {
            if (def.getKind() != SignalDef.Kind.MOUSE) continue;
            if ("release".equalsIgnoreCase(def.getMouse())) {
                view.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
                    if (pluginMode()) return;
                    LinkedHashMap<String, Object> p = new LinkedHashMap<>();
                    p.put("button", e.getButton() == null ? "" : e.getButton().name());
                    signalBus.emitFromNode(node, def.getName(), p);
                });
            } else {
                view.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> {
                    if (pluginMode()) return;
                    LinkedHashMap<String, Object> p = new LinkedHashMap<>();
                    p.put("button", e.getButton() == null ? "" : e.getButton().name());
                    p.put("clickCount", e.getClickCount());
                    signalBus.emitFromNode(node, def.getName(), p);
                });
            }
        }
    }

    /** 进入场景时把存档中记录的属性覆盖重新应用到模型 */
    private void applyStoredOverrides(StoryNode node) {
        if (node.getId().isBlank()) return;
        Map<String, String> props = flowVars.propsOf(node.getId());
        for (Map.Entry<String, String> e : props.entrySet()) {
            applyPropToModel(node, e.getKey(), e.getValue());
        }
    }

    /** 修改模型属性（不改渲染）；返回是否有变化 */
    private static void applyPropToModel(StoryNode n, String prop, String value) {
        switch (prop == null ? "" : prop) {
            case "style" -> n.setStyle(value);
            case "text" -> n.setText(value);
            case "path" -> n.setPath(value);
            case "video" -> n.setVideo(value);
            case "visible" -> n.setVisible(StoryNode.parseBoolSafe(value, true));
            case "signalsEnabled" -> n.setSignalsEnabled(StoryNode.parseBoolSafe(value, true));
            case "slotsEnabled" -> n.setSlotsEnabled(StoryNode.parseBoolSafe(value, true));
            case "opacity" -> n.setOpacity(StoryNode.parseDoubleSafe(value, 1.0));
            case "x" -> n.setX(StoryNode.parseDoubleSafe(value, n.getX()));
            case "y" -> n.setY(StoryNode.parseDoubleSafe(value, n.getY()));
            case "width" -> n.setWidth(StoryNode.parseDoubleSafe(value, n.getWidth()));
            case "height" -> n.setHeight(StoryNode.parseDoubleSafe(value, n.getHeight()));
            case "fontSize" -> n.setFontSize(StoryNode.parseDoubleSafe(value, 0));
            case "align" -> n.setAlign(value);
            case "event" -> n.setEvent(value);
            case "action" -> n.setAction(value);
            case "target" -> n.setTarget(value);
            case "scale" -> { /* 视图层属性：由 applyViewProps / setProperty 处理，模型不存 */ }
            case "rotation" -> { /* 同上 */ }
            case "videoloop", "videovolume", "videopause" -> { /* 视频控制属性：只影响播放器，模型不存 */ }
            default -> Logs.warn("[Flow] 不支持的属性名: " + prop);
        }
    }

    /**
     * 变量改动后重绘当前场景里「文本带 {@code @表达式}」的节点（控制台改完变量立刻能看到效果）。
     * <p>对话节点不在此列：它有自己的逐字/分段状态，重绘会打断正在展开的台词。</p>
     * <p>若地图是用<b>槽</b>把变量映射到节点的（如
     * {@code slot = 点灯 | set | 亮灯11_1 | visible | value=@var(灯1亮)}），还要用控制台的 {@code emit 点灯} 跑一遍槽。</p>
     */
    public int refreshExpressionViews() {
        if (scene == null) return 0;
        int n = 0;
        for (StoryNode node : new ArrayList<>(scene.nodes())) {
            String text = node.getText();
            if (text == null || text.indexOf('@') < 0) continue;
            if (node.getType() == NodeType.DIALOG) continue;
            refreshNodeView(node);
            n++;
        }
        return n;
    }

    /** 控制台用：往当前场景发一个信号，跑地图自己定义/订阅的槽 */
    public boolean emitConsoleSignal(String name, Map<String, Object> params) {
        if (signalBus == null || name == null || name.isBlank()) return false;
        LinkedHashMap<String, Object> p = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
        signalBus.emitFromScene(name.trim(), p);
        return true;
    }

    /** 即时重绘某节点（槽/逻辑层改属性后立即生效） */
    private void refreshNodeView(StoryNode node) {
        if (node == null || node.getId().isBlank()) return;
        Node old = nodeViews.get(node.getId());
        if (old == null) return;
        int idx = board.getChildren().indexOf(old);
        if (idx < 0) return;
        Node fresh = buildNode(node);
        if (fresh == null) return;
        fresh.setLayoutX(node.getX());
        fresh.setLayoutY(node.getY());
        fresh.setOpacity(clamp(node.getOpacity(), 0.05, 1.0));
        fresh.setVisible(node.isVisible());
        installMouseSignals(fresh, node);
        applyViewProps(fresh, node);
        board.getChildren().set(idx, fresh);
        nodeViews.put(node.getId(), fresh);
    }

    // ---------- FlowHost 实现 ----------

    @Override
    public GameScene scene() { return scene; }

    @Override
    public StoryNode node(String id) {
        if (id == null || id.isBlank() || scene == null) return null;
        for (StoryNode n : scene.nodes()) {
            if (id.equals(n.getId())) return n;
        }
        return null;
    }

    @Override
    public String property(String nodeId, String prop) {
        StoryNode n = node(nodeId);
        if (n == null) return "";
        return switch (prop == null ? "" : prop) {
            case "style" -> n.getStyle();
            case "text" -> n.getText();
            case "path" -> n.getPath();
            case "video" -> n.getVideo();
            case "videoloop" -> videoProp(nodeId, "videoloop", "true");
            case "videovolume" -> videoProp(nodeId, "videovolume", String.valueOf(masterVolume()));
            case "videopause" -> videoProp(nodeId, "videopause", "false");
            case "visible" -> String.valueOf(n.isVisible());
            case "signalsEnabled" -> String.valueOf(n.isSignalsEnabled());
            case "slotsEnabled" -> String.valueOf(n.isSlotsEnabled());
            case "opacity" -> StoryNode.trimDouble(n.getOpacity());
            case "x" -> StoryNode.trimDouble(n.getX());
            case "y" -> StoryNode.trimDouble(n.getY());
            case "width" -> StoryNode.trimDouble(n.getWidth());
            case "height" -> StoryNode.trimDouble(n.getHeight());
            case "fontSize" -> StoryNode.trimDouble(n.getFontSize());
            case "align" -> n.getAlign();
            case "event" -> n.getEvent();
            case "action" -> n.getAction();
            case "target" -> n.getTarget();
            case "scale" -> flowVars.prop(nodeId, "scale", "1");
            case "rotation" -> flowVars.prop(nodeId, "rotation", "0");
            default -> "";
        };
    }

    /** 把“视图层属性”（缩放/旋转）应用到节点视图（渲染后/重绘后调用） */
    private void applyViewProps(Node view, StoryNode node) {
        if (view == null || node == null || node.getId().isBlank()) return;
        String scale = flowVars.prop(node.getId(), "scale", "");
        if (!scale.isEmpty()) {
            double s = StoryNode.parseDoubleSafe(scale, 1.0);
            view.setScaleX(s);
            view.setScaleY(s);
        }
        String rot = flowVars.prop(node.getId(), "rotation", "");
        if (!rot.isEmpty()) {
            view.setRotate(StoryNode.parseDoubleSafe(rot, 0));
        }
        // 视频控制属性：读档/重绘后把“暂停/音量”重新应用到播放器
        if (videoPlayers.containsKey(node.getId())) {
            String pause = flowVars.prop(node.getId(), "videopause", "");
            if (!pause.isEmpty()) applyVideoControl(node.getId(), "videopause", pause);
            String vol = flowVars.prop(node.getId(), "videovolume", "");
            if (!vol.isEmpty()) applyVideoControl(node.getId(), "videovolume", vol);
        }
    }

    /** 视频控制属性：直接作用于已挂上的播放器，不需要重建节点视图 */
    private boolean applyVideoControl(String nodeId, String prop, String value) {
        MediaPlayer p = videoPlayers.get(nodeId);
        boolean handled = false;
        switch (prop) {
            case "videoloop" -> {
                if (p != null) p.setCycleCount(StoryNode.parseBoolSafe(value, true) ? MediaPlayer.INDEFINITE : 1);
                handled = true;
            }
            case "videovolume" -> {
                if (p != null) p.setVolume(clamp(StoryNode.parseDoubleSafe(value, masterVolume()), 0, 1));
                handled = true;
            }
            case "videopause" -> {
                if (p != null) {
                    if (StoryNode.parseBoolSafe(value, false)) p.pause(); else p.play();
                }
                handled = true;
            }
            default -> { }
        }
        if (handled) flowVars.recordProp(nodeId, prop, value);   // 随存档保存、重绘时重放
        return handled;
    }

    @Override
    public void setProperty(String nodeId, String prop, String value) {
        StoryNode n = node(nodeId);
        if (n == null) {
            presetProperty(nodeId, prop, value);
            return;
        }
        // 节点上配置了默认过渡（如 transition = scale/opacity:300ms）时，自动走动画分支
        String def = n.getTransition();
        if (!def.isEmpty() && isTransitionProp(def, prop)) {
            setPropertyAnimated(nodeId, prop, value, def);
            return;
        }
        // 视图层属性（缩放/旋转）：不落在模型上，直接作用于视图并记录覆盖
        if ("scale".equals(prop) || "rotation".equals(prop)) {
            applyViewOnly(nodeId, prop, value);
            return;
        }
        // 视频控制属性（循环/音量/暂停）：只作用于播放器，不重建节点视图
        if ("videoloop".equals(prop) || "videovolume".equals(prop) || "videopause".equals(prop)) {
            applyVideoControl(nodeId, prop, value);
            Logs.info("[Flow] 视频 " + nodeId + "." + prop + " ← " + value);
            return;
        }
        applyPropToModel(n, prop, value);
        refreshNodeView(n);
        flowVars.recordProp(nodeId, prop, value); // 随存档保存，读档后自动恢复
        Logs.info("[Flow] 节点 " + nodeId + "." + prop + " ← " + value);
    }

    /**
     * 目标节点不在当前场景时的 {@code set} 处理：<b>给别的场景“预设属性”</b>。
     *
     * <p>典型用法（条件分支常用）：在选项那一幕就把后面某一幕的节点可见性/文本设好，
     * 等那一幕渲染时 {@code applyStoredOverrides()} 会把它重放出来 —— 这样就不需要在
     * 「进入场景」时再跑一次逻辑。只要这个节点 id 确实是本工程里的节点，就记成属性覆盖；
     * 完全不存在的 id 才告警，避免脚本静默失效。</p>
     *
     * <p>视频控制类属性（videoloop/videovolume/videopause）需要活的播放器，非当前场景无法生效，
     * 因此只记日志不记录覆盖。</p>
     */
    private void presetProperty(String nodeId, String prop, String value) {
        String p = prop == null ? "" : prop;
        if ("videoloop".equals(p) || "videovolume".equals(p) || "videopause".equals(p)) {
            Logs.warn("[Flow] 视频控制属性只能作用于当前场景的节点: " + nodeId + "." + p);
            return;
        }
        if (!isKnownNode(nodeId)) {
            Logs.warn("[Flow] setProperty 找不到节点: " + nodeId);
            return;
        }
        flowVars.recordProp(nodeId, p, value);
        Logs.info("[Flow] 预设 " + nodeId + "." + p + " ← " + value + "（该节点在别的场景，渲染时生效）");
    }

    /** 这个 id 是否是本工程里真实存在的节点（任意场景） */
    private boolean isKnownNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank() || project == null) return false;
        for (GameScene s : project.scenes().values()) {
            for (StoryNode n : s.nodes()) {
                if (nodeId.equals(n.getId())) return true;
            }
        }
        return false;
    }

    /** 带过渡动画地设置属性：transitionSpec 形如 {@code scale/opacity:300ms} */
    @Override
    public void setPropertyAnimated(String nodeId, String prop, String value, String transitionSpec) {
        StoryNode n = node(nodeId);
        if (n == null) {
            presetProperty(nodeId, prop, value);   // 别的场景的节点：动画没法跑，但覆盖照样记
            return;
        }
        Node view = nodeViews.get(nodeId);
        boolean animatable = isTransitionProp(transitionSpec, prop) && view != null
                && (switch (prop) {
                    case "scale", "opacity", "rotation", "x", "y" -> true;
                    default -> false;
                });
        if (!animatable) {
            setProperty(nodeId, prop, value);
            return;
        }
        double ms = parseTransitionMs(transitionSpec, 300);
        applyPropToModel(n, prop, value);          // 模型先行，重绘/读档以最终值为准
        flowVars.recordProp(nodeId, prop, value);
        animateViewProp(view, prop, value, ms);
        Logs.info("[Flow] 节点 " + nodeId + "." + prop + " ← " + value + "（过渡 " + ms + "ms）");
    }

    /** 直接把“视图层属性”应用到视图并记录（scale / rotation） */
    private void applyViewOnly(String nodeId, String prop, String value) {
        Node v = nodeViews.get(nodeId);
        if ("scale".equals(prop)) {
            double s = StoryNode.parseDoubleSafe(value, 1.0);
            if (v != null) {
                v.setScaleX(s);
                v.setScaleY(s);
            }
            flowVars.recordProp(nodeId, "scale", StoryNode.trimDouble(s));
        } else {
            double r = StoryNode.parseDoubleSafe(value, 0);
            if (v != null) v.setRotate(r);
            flowVars.recordProp(nodeId, "rotation", StoryNode.trimDouble(r));
        }
        Logs.info("[Flow] 节点 " + nodeId + "." + prop + " ← " + value);
    }

    /** 对视图属性做简单补间动画 */
    private void animateViewProp(Node view, String prop, String value, double ms) {
        javafx.animation.KeyValue kv;
        switch (prop) {
            case "scale" -> {
                double s = StoryNode.parseDoubleSafe(value, view.getScaleX());
                javafx.animation.Timeline t = new javafx.animation.Timeline(
                        new javafx.animation.KeyFrame(Duration.millis(ms),
                                new javafx.animation.KeyValue(view.scaleXProperty(), s, javafx.animation.Interpolator.EASE_BOTH),
                                new javafx.animation.KeyValue(view.scaleYProperty(), s, javafx.animation.Interpolator.EASE_BOTH)));
                t.play();
                return;
            }
            case "opacity" -> kv = new javafx.animation.KeyValue(view.opacityProperty(),
                    clamp(StoryNode.parseDoubleSafe(value, view.getOpacity()), 0, 1),
                    javafx.animation.Interpolator.EASE_BOTH);
            case "rotation" -> kv = new javafx.animation.KeyValue(view.rotateProperty(),
                    StoryNode.parseDoubleSafe(value, view.getRotate()), javafx.animation.Interpolator.EASE_BOTH);
            case "x" -> kv = new javafx.animation.KeyValue(view.layoutXProperty(),
                    StoryNode.parseDoubleSafe(value, view.getLayoutX()), javafx.animation.Interpolator.EASE_BOTH);
            case "y" -> kv = new javafx.animation.KeyValue(view.layoutYProperty(),
                    StoryNode.parseDoubleSafe(value, view.getLayoutY()), javafx.animation.Interpolator.EASE_BOTH);
            default -> {
                return;
            }
        }
        javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(Duration.millis(ms), kv));
        timeline.play();
    }

    /** transitionSpec 里是否包含某属性：{@code scale/opacity:300ms} → scale、opacity */
    static boolean isTransitionProp(String spec, String prop) {
        if (spec == null || spec.isBlank()) return false;
        String head = spec.trim();
        int colon = head.lastIndexOf(':');
        if (colon > 0) head = head.substring(0, colon);
        for (String p : head.split("[/,;|\\s]+")) {
            if (p.trim().equalsIgnoreCase(prop)) return true;
        }
        return false;
    }

    /** 解析过渡时长：{@code scale:250ms} / {@code scale/opacity:300} / 无 → 默认值 */
    static double parseTransitionMs(String spec, double def) {
        if (spec == null) return def;
        java.util.regex.Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*ms", Pattern.CASE_INSENSITIVE)
                .matcher(spec);
        if (m.find()) return Double.parseDouble(m.group(1));
        java.util.regex.Matcher m2 = Pattern.compile(":\\s*(\\d+(?:\\.\\d+)?)\\s*$").matcher(spec.trim());
        if (m2.find()) return Double.parseDouble(m2.group(1));
        return def;
    }

    @Override
    public void emit(String targetId, String signalName, Map<String, Object> params) {
        if (signalBus == null) return;
        if (targetId == null || targetId.isBlank()) {
            signalBus.emitFromScene(signalName, params);
            return;
        }
        StoryNode n = node(targetId);
        if (n == null) {
            Logs.warn("[Flow] emit 目标节点不存在: " + targetId);
            return;
        }
        signalBus.emitFromNode(n, signalName, params);
    }

    @Override
    public void gotoScene(String name) {
        if (name == null || name.isBlank() || project == null) return;
        if (!project.hasScene(name)) {
            Logs.warn("[Flow] 目标场景不存在: " + name);
            return;
        }
        renderScene(name, true, true);
    }

    @Override
    public boolean saveSlot(String slot) { return saveTo(slot); }

    @Override
    public boolean loadSlot(String slot) { return loadFrom(slot); }

    @Override
    public GameSaveManager saves() { return saveManager; }

    @Override
    public FlowVariables variables() { return flowVars; }

    @Override
    public void log(String message) { Logs.info("[Flow] " + message); }

    // ---------- FlowHost：插件支持（工程/地图目录、UI 线程、存档变量声明） ----------

    /** 工程根目录 = config.ini 所在目录（与 AppConfig / LogicLoader 一致取 user.dir） */
    @Override
    public File projectDir() {
        return new File(System.getProperty("user.dir"));
    }

    /** 当前已加载的地图文件夹（解析后的工程根即地图目录）；未知返回 null */
    /** 主音量：地图 [option] volume × 全局倍率（插件播放音频/视频时作为默认音量） */
    @Override
    public double masterVolume() {
        double base = project == null ? 0.8 : project.option().volume();
        return clamp(base * volumeScale, 0, 1);
    }

    /** 设置全局音量倍率（标题页设置）；立即作用到正在播放的音频通道 */
    public void setMasterVolumeScale(double scale) {
        this.volumeScale = clamp(scale, 0, 1);
        double v = masterVolume();
        for (MediaPlayer p : audioChannels.values()) {
            try {
                p.setVolume(v);
            } catch (RuntimeException ignored) {
                // 单通道失败不影响其它
            }
        }
        Logs.info("[Audio] 全局音量倍率 = " + String.format("%.2f", volumeScale)
                + "（实际 " + String.format("%.2f", v) + "）");
    }

    /** 当前全局音量倍率 */
    public double masterVolumeScale() { return volumeScale; }

    /** 设置打字机速度（毫秒/字符，越小越快）；下一段台词生效 */
    public void setTypewriterSpeed(double msPerChar) {
        this.typeSpeed = Math.max(1.0, msPerChar);
    }

    /** 当前打字机速度（毫秒/字符） */
    public double typewriterSpeed() { return typeSpeed; }

    /** 注册 ESC 回调（标题页"回到标题"菜单用） */
    public void setOnEscape(Runnable handler) { this.escHandler = handler; }

    /** 回到标题前清理：停打字机、停音频、摘掉正在嵌入的小游戏 */
    public void disposeForTitle() {
        for (DialogParagraph d : dialogs) {
            if (d.timer != null) d.timer.stop();
            d.timer = null;
            d.busy = false;
        }
        stopAllAudio();
        detachActivePlugin("回到标题");
        escHandler = null;   // 回到标题后旧实例不再响应 ESC（键盘过滤器仍挂在 Scene 上）
    }

    // ---------- FlowHost 的音频通道（插件用；实现见下方 playAudioChannel 等） ----------

    @Override
    public void playAudio(String channel, String path, boolean loop, double volume) {
        playAudioChannel(channel, path, loop, volume);
    }

    @Override
    public void stopAudio(String channel) {
        stopAudioChannel(channel);
    }

    @Override
    public void stopAllAudio() {
        for (String ch : new java.util.ArrayList<>(audioChannels.keySet())) stopAudioChannel(ch);
    }

    @Override
    public boolean pauseAudio(String channel, boolean pause) {
        return pauseAudioChannel(channel, pause);
    }

    @Override
    public boolean setAudioVolume(String channel, double volume) {
        return setAudioChannelVolume(channel, volume);
    }

    @Override
    public File mapDir() {
        if (project != null && project.rootDir() != null) return project.rootDir();
        return mapDir;
    }

    @Override
    public boolean isUiThread() {
        return Platform.isFxApplicationThread();
    }

    /**
     * 请求退出游戏（自带插件 {@code @plugin(quit)} 的落地实现）。
     * <p>先做一次统一收尾（停媒体/停插件/存钩子），再关闭播放器窗口；
     * 若播放器是唯一窗口，JavaFX 会随之结束进程；编辑器里的预览窗口只关掉预览本身。</p>
     */
    @Override
    public boolean requestQuit() {
        Logs.info("[Player] 收到退出游戏请求（@plugin(quit)）");
        runOnUiThread(() -> {
            try {
                shutdown();
            } catch (RuntimeException e) {
                Logs.warn("[Player] 退出前清理出错：" + e.getMessage());
            }
            if (stage != null) stage.close();
        });
        return true;
    }

    /**
     * 播放特效预设（自带插件 {@code @plugin(fx)} 用）。
     * <p>规格串形如 {@code shake:8:400}（预设名:参数:参数…）。支持的预设：
     * {@code shake} 抖屏、{@code flash} 闪白、{@code pulse} 心跳缩放、
     * {@code fadein}/{@code fadeout} 淡入淡出、{@code slidein}/{@code slideout} 滑入滑出、
     * {@code zoom} 缩放、{@code rotate} 旋转、{@code bounce} 弹跳。
     * 返回 false 表示这个预设宿主没实现，插件会自己退化成改属性。</p>
     */
    @Override
    public boolean animate(String nodeId, String spec) {
        if (spec == null || spec.isBlank()) return false;
        String[] parts = spec.split(":");
        String preset = parts[0].trim().toLowerCase(java.util.Locale.ROOT);
        String p1 = parts.length > 1 ? parts[1].trim() : "";
        String p2 = parts.length > 2 ? parts[2].trim() : "";
        double d1 = parseNum(p1, 0);
        double d2 = parseNum(p2, 400);
        Node view = nodeViews.get(nodeId);
        // flash 打在整块画布上（闪白是“屏幕级”的效果，不依赖具体节点）
        if ("flash".equals(preset) || "闪白".equals(preset)) {
            double dur = d1 > 0 ? d1 * 1000 : (d2 > 0 ? d2 : 220);
            runOnUiThread(() -> flashBoard(dur));
            return true;
        }
        if (view == null) return false;   // 目标不在当前场景 → 交给插件退化处理
        switch (preset) {
            case "shake", "抖屏", "震动" -> {
                double power = d1 > 0 ? d1 : 8;
                double dur = d2 > 0 ? d2 : 400;
                runOnUiThread(() -> {
                    TranslateTransition t1 = new TranslateTransition(Duration.millis(dur / 4), view);
                    t1.setByX(power);
                    TranslateTransition t2 = new TranslateTransition(Duration.millis(dur / 4), view);
                    t2.setByX(-2 * power);
                    TranslateTransition t3 = new TranslateTransition(Duration.millis(dur / 4), view);
                    t3.setByX(2 * power);
                    TranslateTransition t4 = new TranslateTransition(Duration.millis(dur / 4), view);
                    t4.setByX(-power);
                    // 四段位移之和为 0，结束时自动回到原位
                    new SequentialTransition(t1, t2, t3, t4).play();
                });
                return true;
            }
            case "pulse", "心跳", "缩放" -> {
                double scale = d1 > 0 ? (d1 > 2 ? d1 / 100.0 : d1) : 1.08;
                double dur = d2 > 0 ? d2 : 260;
                runOnUiThread(() -> {
                    ScaleTransition s1 = new ScaleTransition(Duration.millis(dur), view);
                    s1.setToX(scale);
                    s1.setToY(scale);
                    ScaleTransition s2 = new ScaleTransition(Duration.millis(dur), view);
                    s2.setToX(1);
                    s2.setToY(1);
                    new SequentialTransition(s1, s2).play();
                });
                return true;
            }
            case "fadein", "淡入" -> {
                double dur = d2 > 0 ? d2 : (d1 > 0 ? d1 * 1000 : 500);
                runOnUiThread(() -> {
                    view.setVisible(true);
                    view.setOpacity(0);
                    FadeTransition ft = new FadeTransition(Duration.millis(dur), view);
                    ft.setToValue(1);
                    ft.play();
                });
                return true;
            }
            case "fadeout", "淡出" -> {
                double dur = d2 > 0 ? d2 : (d1 > 0 ? d1 * 1000 : 500);
                runOnUiThread(() -> {
                    FadeTransition ft = new FadeTransition(Duration.millis(dur), view);
                    ft.setToValue(0);
                    ft.setOnFinished(e -> view.setVisible(false));
                    ft.play();
                });
                return true;
            }
            case "slidein", "滑入", "slideout", "滑出" -> {
                boolean in = preset.startsWith("slidein") || "滑入".equals(preset);
                double dur = d2 > 0 ? d2 : 420;
                double dist = in ? 120 : -120;
                String dir = p1.isEmpty() ? "left" : p1;
                runOnUiThread(() -> {
                    if (in) view.setVisible(true);
                    TranslateTransition tt = new TranslateTransition(Duration.millis(dur), view);
                    switch (dir) {
                        case "right", "右" -> { tt.setFromX(-dist); tt.setToX(0); }
                        case "up", "上" -> { tt.setFromY(dist); tt.setToY(0); }
                        case "down", "下" -> { tt.setFromY(-dist); tt.setToY(0); }
                        default -> { tt.setFromX(dist); tt.setToX(0); }
                    }
                    if (in) tt.setFromX(tt.getFromX());
                    tt.play();
                });
                return true;
            }
            case "zoom", "放大" -> {
                double to = d1 > 0 ? (d1 > 3 ? d1 / 100.0 : d1) : 1.25;
                double dur = d2 > 0 ? d2 : 300;
                runOnUiThread(() -> {
                    ScaleTransition st = new ScaleTransition(Duration.millis(dur), view);
                    st.setToX(to);
                    st.setToY(to);
                    st.play();
                });
                return true;
            }
            case "rotate", "旋转" -> {
                double deg = d1 != 0 ? d1 : 12;
                double dur = d2 > 0 ? d2 : 400;
                runOnUiThread(() -> {
                    RotateTransition rt = new RotateTransition(Duration.millis(dur), view);
                    rt.setToAngle(deg);
                    rt.play();
                });
                return true;
            }
            case "bounce", "弹跳" -> {
                double power = d1 > 0 ? d1 : 18;
                double dur = d2 > 0 ? d2 : 420;
                runOnUiThread(() -> {
                    TranslateTransition up = new TranslateTransition(Duration.millis(dur / 2), view);
                    up.setByY(-power);
                    TranslateTransition down = new TranslateTransition(Duration.millis(dur / 2), view);
                    down.setByY(power);
                    new SequentialTransition(up, down).play();
                });
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** 画面闪白（在画布之上盖一层白，快速淡出） */
    private void flashBoard(double millis) {
        javafx.scene.shape.Rectangle flash = new javafx.scene.shape.Rectangle(CW, CH);
        flash.setFill(javafx.scene.paint.Color.WHITE);
        flash.setOpacity(0.85);
        flash.setMouseTransparent(true);
        mainStack.getChildren().add(flash);
        FadeTransition ft = new FadeTransition(Duration.millis(Math.max(60, millis)), flash);
        ft.setToValue(0);
        ft.setOnFinished(e -> mainStack.getChildren().remove(flash));
        ft.play();
    }

    /**
     * 把当前画面截图保存成 PNG（自带插件 {@code @plugin(screenshot)} 用）。
     * <p>截的是播放器整体视图（含对话框/提示条），不依赖 javafx.swing：逐像素写盘。</p>
     */
    @Override
    public boolean snapshot(File out) {
        if (out == null) return false;
        try {
            WritableImage img = snapshot(null, null);
            return com.studio.util.Snapshots.save(img, out);
        } catch (RuntimeException e) {
            Logs.warn("[Player] 截图失败：" + e.getMessage());
            return false;
        }
    }

    /**
     * 切换全屏（自带插件 {@code @plugin(fullscreen)} 用）。
     *
     * @param want TRUE 进全屏、FALSE 退全屏、null 表示切换
     */
    @Override
    public boolean toggleFullscreen(Boolean want) {
        if (stage == null) return false;
        runOnUiThread(() -> {
            boolean target = want == null ? !stage.isFullScreen() : want;
            stage.setFullScreen(target);
            Logs.info("[Player] 全屏 → " + target);
        });
        return true;
    }

    private static double parseNum(String s, double def) {
        if (s == null || s.isBlank()) return def;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** 插件可能在后台线程调用渲染接口：这里统一切回 JavaFX 线程 */
    @Override
    public void runOnUiThread(Runnable action) {
        if (action == null) return;
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    /** 地图 [option] 段声明的存档变量（供插件按声明类型强制转换）；无工程时空表 */
    @Override
    public List<SaveVarDef> saveVarDefs() {
        return project == null ? List.of() : project.option().saveVars();
    }

    // =====================================================================
    // Toast 提示
    // =====================================================================

    @Override
    public void toast(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("toast");
        HBox row = new HBox(l);
        row.setAlignment(Pos.TOP_RIGHT);
        toastHost.getChildren().add(row);
        FadeTransition in = new FadeTransition(Duration.millis(180), row);
        in.setFromValue(0);
        in.setToValue(1);
        in.play();
        Timeline t = new Timeline(new KeyFrame(Duration.millis(1500), e -> {
            FadeTransition out = new FadeTransition(Duration.millis(300), row);
            out.setToValue(0);
            out.setOnFinished(e2 -> toastHost.getChildren().remove(row));
            out.play();
        }));
        t.setCycleCount(1);
        t.play();
        // 限制同时显示数量
        while (toastHost.getChildren().size() > 3) {
            toastHost.getChildren().remove(0);
        }
    }
}
