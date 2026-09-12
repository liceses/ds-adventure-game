package com.studio.reader;

import com.studio.flow.Expr;
import com.studio.flow.FlowHost;
import com.studio.flow.FlowVariables;
import com.studio.flow.LogicLoader;
import com.studio.flow.SignalBus;
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
import javafx.scene.input.KeyEvent;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
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
 *   <li>按节点类型渲染：背景图 / 立绘(保持比例) / 名字牌 / 富文本对话 / 按钮；
 *       支持 伪类 hover/pressed 动画 与 打字机逐字显示；</li>
 *   <li>自动播放场景音乐（MUSIC 节点或带 audio 的资源），音量取自 [option]；</li>
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
    private MediaPlayer music;
    /** 音频通道（bgm / se / 自定义）：插件与旧的 audio 属性共用同一套通道 */
    private final java.util.LinkedHashMap<String, MediaPlayer> audioChannels = new java.util.LinkedHashMap<>();
    /** 每个节点当前挂着的视频播放器（节点视图重建时要 dispose，避免泄漏） */
    private final java.util.LinkedHashMap<String, MediaPlayer> videoPlayers = new java.util.LinkedHashMap<>();

    // ---- 插件 ----
    private String previousScene;        // 进入插件前正在展示的场景（插件【返回】的目标）
    private String pluginReturnScene;    // 由场景级/按钮级事件临时指定的返回目标
    /** 当前运行中的事件插件：返回剧情 / 被替换 / 关闭播放器时都要回调它的 onDetach */
    private GamePlugin activePlugin;
    private String activePluginId = "";
    /** 场景自动信号防重入（「场景进入」每次进入只发一次；槽里再跳场景时不重发） */
    private boolean emittingSceneSignal = false;
    private boolean enteredSceneSignals = false;
    private boolean suppressSceneEvent;  // 从插件返回时避免重复触发场景事件

    // ---- 存档 ----
    private GameSaveManager saveManager;
    private final java.util.ArrayList<SaveHook> saveHooks = new java.util.ArrayList<>();

    // ---- 信号/槽 与逻辑层 ----
    private LogicLoader logicLoader;
    private SignalBus signalBus;
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
        stopMusic();
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
        // 离开旧场景：先发「场景离开」信号（此时旧场景的节点还在，槽仍能改它们）
        if (cameFrom != null && !cameFrom.isBlank() && !cameFrom.equals(name)) {
            emitSceneSignal("场景离开", cameFrom);
        }
        this.sceneName = name;
        this.scene = target;
        this.enteredSceneSignals = false;   // 本次进入还没发过「场景进入」

        board.getChildren().clear();
        nodeViews.clear();
        disposeAllVideos();
        FxAssets.clearCache();

        // 背景底色来自 [option]
        String bg = project.option().background();
        board.setStyle("-fx-background-color: " + bg + ";");

        playSceneMusic(scene);

        int index = 0;
        for (StoryNode node : scene.nodes()) {
            applyStoredOverrides(node); // 读档/上次逻辑改动过的属性优先生效
            Node view = buildNode(node);
            if (view == null) continue;
            view.setLayoutX(node.getX());
            view.setLayoutY(node.getY());
            view.setOpacity(clamp(node.getOpacity(), 0.05, 1.0));
            view.setVisible(node.isVisible());
            board.getChildren().add(view);
            if (!node.getId().isBlank()) nodeViews.put(node.getId(), view);
            installMouseSignals(view, node);
            applyViewProps(view, node);
            if (animate && node.needsVisual()) {
                FxAnim.entrance(view, 60 + index * 70, 380);
            }
            index++;
        }

        if (animate) {
            board.setOpacity(0);
            FadeTransition ft = new FadeTransition(Duration.millis(280), board);
            ft.setToValue(1.0);
            ft.play();
        }

        // 场景事件：进入场景时动态加载外部插件并嵌入
        if (fireEvent && !suppressSceneEvent && scene.event() != null && !scene.event().isBlank()) {
            String id = scene.event().trim();
            pluginReturnScene = cameFrom != null ? cameFrom : sceneName; // 场景级事件：返回进入前的场景
            runPlugin(id, "场景事件 [" + sceneName + "]");
        }
        suppressSceneEvent = false;

        // 进入新场景：发「场景进入」信号（节点已经建好，槽可以立刻改它们的属性）
        emitSceneSignal("场景进入", name);
    }

    /**
     * 发送引擎自动信号（目前是「场景进入」/「场景离开」）。
     *
     * <p>地图里不用声明 {@code signal = ...}，直接在场景头写
     * {@code slot = 场景进入 | …} 就能在进入这一场景时自动跑一段逻辑 ——
     * 这是“条件内容 / 自动演出 / 自动存档”最常用的挂载点。</p>
     *
     * <p>防重入：槽里如果再 {@code goto} 别的场景，会递归走到这里，
     * 这种情况下不再重复发出自动信号（避免无限循环），只记一条日志。</p>
     *
     * @param signal 信号名（「场景进入」或「场景离开」）
     * @param scene  对应的场景名（作为参数 {@code scene} 传给槽，可用 {@code @param(scene)} 取）
     */
    private void emitSceneSignal(String signal, String scene) {
        if (signalBus == null || scene == null || scene.isBlank()) return;
        if ("场景进入".equals(signal)) {
            if (enteredSceneSignals) {
                Logs.info("[Flow] 场景信号防重入：跳过「场景进入」（" + scene + "）");
                return;
            }
            enteredSceneSignals = true;
        }
        if (emittingSceneSignal) {
            Logs.info("[Flow] 场景信号防重入：跳过「" + signal + "」（" + scene + "）");
            return;
        }
        emittingSceneSignal = true;
        try {
            LinkedHashMap<String, Object> p = new LinkedHashMap<>();
            p.put("scene", scene);
            signalBus.emitFromScene(signal, p);
            Logs.info("[Flow] 场景信号「" + signal + "」→ " + scene);
        } catch (RuntimeException e) {
            Logs.warn("[Flow] 场景信号「" + signal + "」执行出错：" + e.getMessage());
        } finally {
            emittingSceneSignal = false;
        }
    }

    /** 场景音乐：播放场景中 MUSIC 类型节点的音频（循环） */
    private void playSceneMusic(GameScene s) {
        String path = null;
        for (StoryNode n : s.nodes()) {
            if (n.getType() == NodeType.MUSIC && !n.getAudio().isBlank()) {
                path = n.getAudio();
            }
        }
        playMusic(path);
    }

    /**
     * 旧写法：节点的 {@code audio} 属性自动作为场景 BGM 循环播放。
     * <p>已改为走统一音频通道（与 {@code @plugin(audio)} 共用实现），
     * 方便日后废除这个属性：只要不再调用这里即可。</p>
     */
    private void playMusic(String relPath) {
        if (relPath == null || relPath.isBlank()) {
            stopAudioChannel("bgm");
            return;
        }
        playAudioChannel("bgm", relPath, true, clamp(masterVolume() * 0.9, 0, 1));
    }

    private void stopMusic() {
        stopAudioChannel("bgm");
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
            case MUSIC -> null;
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
    private File resolveAsset(String rel) {
        if (rel == null || rel.isBlank()) return null;
        File f = new File(rel);
        if (f.isAbsolute()) return f;
        return mapDir == null ? f : new File(mapDir, rel.replace('\\', '/'));
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
            Logs.warn("[Audio] 找不到音频: " + relPath);
            toast("找不到音频: " + relPath);
            return;
        }
        runOnUiThread(() -> {
            stopAudioChannel(ch);
            try {
                Media media = new Media(f.toURI().toString());
                MediaPlayer p = new MediaPlayer(media);
                p.setCycleCount(loop ? MediaPlayer.INDEFINITE : 1);
                p.setVolume(clamp(vol, 0, 1));
                p.setOnError(() -> Logs.warn("[Audio] 播放失败 " + relPath + "：" + p.getError()));
                if (!loop) p.setOnEndOfMedia(() -> Logs.info("[Audio] 播放结束: " + relPath));
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
    private void onDialogClicked(DialogParagraph st) {
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
        btn.setOnAction(e -> onStoryAction(node));
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
        st.busy = true;
        st.timer = new Timeline(new KeyFrame(Duration.millis(40), e -> {
            if (!st.busy) {
                if (st.timer != null) st.timer.stop();
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
        pluginTitle.setText("🎮 " + plugin.displayName() + "（事件: " + eventId + "）");

        pluginContent.setCenter(null);
        pluginContent.setCenter(view);
        pluginLayer.setVisible(true);
        pluginLayer.setManaged(true);
    }

    /** 点击【返回】：移除嵌入层，回到进入插件前的场景 */
    private void leavePlugin() {
        if (!pluginMode()) return;
        pluginLayer.setVisible(false);
        pluginLayer.setManaged(false);
        pluginContent.setCenter(null);
        suppressSceneEvent = true; // 防止场景事件再次把玩家拉回插件
        detachActivePlugin("返回剧情");   // 插件从主舞台移除 → 回调 onDetach
        if (previousScene != null && project.hasScene(previousScene)) {
            renderScene(previousScene, false, false);
        }
    }

    // =====================================================================
    // 信号 / 槽 引擎侧（FlowHost 实现）
    // =====================================================================

    /** 全局键盘监听：把按键信号按“场景信号定义 + 各节点按键信号定义”分发 */
    private void installGlobalKeyListener() {
        Scene sc = stage.getScene();
        if (sc == null || keyListenerInstalled) return;
        keyListenerInstalled = true;
        sc.addEventFilter(KeyEvent.KEY_PRESSED, e -> dispatchKey(e, "press"));
        sc.addEventFilter(KeyEvent.KEY_RELEASED, e -> dispatchKey(e, "release"));
    }

    private void dispatchKey(KeyEvent e, String phase) {
        if (project == null || scene == null || pluginMode()) return;
        String code = e.getCode() == null ? "" : e.getCode().name();
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
        // 节点级键盘信号
        for (StoryNode n : scene.nodes()) {
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
            case "audio" -> n.setAudio(value);
            case "video" -> n.setVideo(value);
            case "visible" -> n.setVisible(StoryNode.parseBoolSafe(value, true));
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
            case "audio" -> n.getAudio();
            case "video" -> n.getVideo();
            case "videoloop" -> videoProp(nodeId, "videoloop", "true");
            case "videovolume" -> videoProp(nodeId, "videovolume", String.valueOf(masterVolume()));
            case "videopause" -> videoProp(nodeId, "videopause", "false");
            case "visible" -> String.valueOf(n.isVisible());
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
    /** 主音量：取 [option] volume（插件播放音频/视频时作为默认音量） */
    @Override
    public double masterVolume() {
        return project == null ? 0.8 : project.option().volume();
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
