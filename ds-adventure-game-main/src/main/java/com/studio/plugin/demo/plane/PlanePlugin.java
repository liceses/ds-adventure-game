package com.studio.plugin.demo.plane;

import com.studio.plugin.GamePlugin;
import com.studio.ui.FxAnim;
import com.studio.util.Logs;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 飞机大战小游戏插件（内置形态，事件ID <code>plane</code>）。
 *
 * <p>玩法规格见 <code>docs/ds-adventrue/飞机大战.md</code> 与需求 F5：
 * 战机在下方，自上而下的敌机推进；击落敌机得分，击落 20 架通关；
 * 3 条命，被撞或中弹扣命；拾取护盾道具后 3 秒内免伤。</p>
 *
 * <p>接入方式（对照 CONTRIBUTING §2）：</p>
 * <ul>
 *   <li><b>嵌入模式</b>：{@link #createEmbeddedView} 返回本游戏界面，读取器把它放进主舞台中央，
 *       并自动加「🎮 插件名 … ← 返回剧情」标题栏 —— <b>不另开窗口</b>；</li>
 *   <li><b>返回剧情</b>：自建返回按钮走 {@link GamePlugin#PARAM_BACK_CALLBACK}，
 *       先停循环收尾再切回剧情（§2.6 第 4 条）；</li>
 *   <li><b>生命周期</b>：{@link #onDetach()} 幂等停止 AnimationTimer，避免返回剧情后循环残留；</li>
 *   <li>规则计算全部委托给 {@link PlaneGame}，本类只负责界面与输入，规则可脱离 JavaFX 单测。</li>
 * </ul>
 */
public class PlanePlugin implements GamePlugin {

    // ---- 布局 ----
    private static final double HEADER_SPACING = 14;
    private static final double HEADER_PADDING = 10;
    private static final double HUD_FONT_SIZE = 14;
    private static final double HUD_TITLE_FONT_SIZE = 17;
    private static final double BAR_MARGIN_X = 18;
    private static final double BAR_LABEL_BASELINE = 22;
    private static final double BAR_TOP = 30;
    private static final double BAR_HEIGHT = 12;
    private static final double BAR_FONT_SIZE = 12;

    // ---- 标题与排版 ----
    /** 标题用显示字体：优先挑系统里专门为标题设计的粗体。 */
    private static final String TITLE_FAMILY = pickFamily(new String[]{
        "YouSheBiaoTiHei",      // 优设标题黑：专为标题设计，笔画厚重
        "STHupo",               // 华文琥珀：粗圆，冲击力强
        "Noto Sans SC Black",   // 思源黑体特粗
        "Microsoft YaHei UI",
        "SimHei",
    });
    /** 正文用 UI 字体：保证小字号下的可读性。 */
    private static final String UI_FAMILY = pickFamily(new String[]{
        "Microsoft YaHei UI",
        "Microsoft YaHei",
        "SimHei",
    });
    private static final int TITLE_FONT_SIZE = 46;
    private static final int SUBTITLE_FONT_SIZE = 12;
    private static final int INFO_FONT_SIZE = 15;
    private static final int HINT_FONT_SIZE = 17;
    /** 信息区行数（开始界面用满，结算界面用前两行）。 */
    private static final int INFO_ROWS = 4;
    private static final double DIVIDER_WIDTH = 110;
    private static final double DIVIDER_THICKNESS = 1;

    private static final String TITLE_STYLE_START =
            "-fx-font-family: \"" + TITLE_FAMILY + "\";"
            + "-fx-font-size: " + TITLE_FONT_SIZE + "px; -fx-font-weight: bold;"
            + "-fx-text-fill: linear-gradient(to bottom, #b6f0ff, #2f7fc4);"
            + "-fx-effect: dropshadow(gaussian, rgba(76,194,255,0.55), 18, 0.35, 0, 0);";
    private static final String TITLE_STYLE_WIN =
            "-fx-font-family: \"" + TITLE_FAMILY + "\";"
            + "-fx-font-size: " + TITLE_FONT_SIZE + "px; -fx-font-weight: bold;"
            + "-fx-text-fill: linear-gradient(to bottom, #c4ffd8, #2f9b58);"
            + "-fx-effect: dropshadow(gaussian, rgba(122,224,138,0.55), 18, 0.35, 0, 0);";
    private static final String TITLE_STYLE_LOSE =
            "-fx-font-family: \"" + TITLE_FAMILY + "\";"
            + "-fx-font-size: " + TITLE_FONT_SIZE + "px; -fx-font-weight: bold;"
            + "-fx-text-fill: linear-gradient(to bottom, #ffc9c0, #b83f34);"
            + "-fx-effect: dropshadow(gaussian, rgba(224,106,106,0.55), 18, 0.35, 0, 0);";
    private static final String TITLE_STYLE_PAUSE =
            "-fx-font-family: \"" + TITLE_FAMILY + "\";"
            + "-fx-font-size: " + TITLE_FONT_SIZE + "px; -fx-font-weight: bold;"
            + "-fx-text-fill: linear-gradient(to bottom, #ffedb8, #c9961f);"
            + "-fx-effect: dropshadow(gaussian, rgba(255,213,79,0.55), 18, 0.35, 0, 0);";
    private static final String SUBTITLE_STYLE =
            "-fx-font-family: \"" + UI_FAMILY + "\";"
            + "-fx-font-size: " + SUBTITLE_FONT_SIZE + "px; -fx-font-weight: bold; -fx-text-fill: #6d7a9c;";
    private static final String LABEL_STYLE =
            "-fx-font-family: \"" + UI_FAMILY + "\";"
            + "-fx-font-size: " + INFO_FONT_SIZE + "px; -fx-font-weight: bold; -fx-text-fill: #7d88a6;";
    private static final String VALUE_STYLE =
            "-fx-font-family: \"" + UI_FAMILY + "\";"
            + "-fx-font-size: " + INFO_FONT_SIZE + "px; -fx-text-fill: #d0d4e0;";
    private static final String HINT_STYLE =
            "-fx-font-family: \"" + UI_FAMILY + "\";"
            + "-fx-font-size: " + HINT_FONT_SIZE + "px; -fx-font-weight: bold; -fx-text-fill: #ffd54f;";
    /** 遮罩整体透明：明暗交给画布上的聚光遮罩，不要面板也不要边框。 */
    private static final String OVERLAY_BOX_STYLE = "-fx-background-color: transparent;";

    /** 暂停状态的强调色（金黄）。 */
    private static final Color ACCENT_PAUSE = Color.web("#ffd54f");
    /** 遮罩入场时的起始缩放。 */
    private static final double OVERLAY_ENTER_SCALE = 0.90;
    private static final int OVERLAY_FADE_MS = 180;
    private static final int OVERLAY_SCALE_MS = 220;

    /** 强度达到该值即视为"读条已满"，开始震动告警。 */
    private static final double BAR_FULL_THRESHOLD = 0.995;
    /** 读条震动的水平幅度（像素）。 */
    private static final double BAR_SHAKE_AMPLITUDE = 2.6;
    /** 读条震动的频率（弧度/秒）。 */
    private static final double BAR_SHAKE_SPEED = 44.0;
    /** 纵向抖动相对水平的比例，让震动不是纯左右而是有点抖。 */
    private static final double BAR_SHAKE_Y_RATIO = 0.45;
    /** 纵向抖动与横向的频率比，避免走成一条斜线。 */
    private static final double BAR_SHAKE_Y_SPEED = 1.37;
    /** 满格时红框呼吸的速度。 */
    private static final double BAR_GLOW_SPEED = 7.0;
    private static final Color BAR_FULL_COLOR = Color.web("#ff5a5a");

    private static final double OVERLAY_TITLE_FONT_SIZE = 30;
    private static final double OVERLAY_BODY_FONT_SIZE = 15;
    private static final double FIELD_HINT_FONT_SIZE = 26;

    // ---- 配色 ----
    /** 按钮常态：深蓝渐变底 + 圆角 + 细描边，与游戏整体冷色调一致。 */
    private static final String BUTTON_STYLE =
            "-fx-background-color: linear-gradient(to bottom, #2b3554, #1a2136);"
            + "-fx-background-radius: 9;"
            + "-fx-border-radius: 9;"
            + "-fx-border-color: #3d4a6b;"
            + "-fx-border-width: 1;"
            + "-fx-text-fill: #d8e2f5;"
            + "-fx-font-size: 13.5px;"
            + "-fx-font-weight: bold;"
            + "-fx-padding: 7 18 7 18;"
            + "-fx-cursor: hand;"
            + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 6, 0, 0, 2);";

    /** 按钮悬停：整体提亮 + 描边变亮，配合 FxAnim 的放大一起用。 */
    private static final String BUTTON_HOVER_STYLE =
            "-fx-background-color: linear-gradient(to bottom, #3d4c78, #26325a);"
            + "-fx-background-radius: 9;"
            + "-fx-border-radius: 9;"
            + "-fx-border-color: #8fa4dd;"
            + "-fx-border-width: 1;"
            + "-fx-text-fill: #ffffff;"
            + "-fx-font-size: 13.5px;"
            + "-fx-font-weight: bold;"
            + "-fx-padding: 7 18 7 18;"
            + "-fx-cursor: hand;"
            + "-fx-effect: dropshadow(gaussian, rgba(120,150,220,0.55), 12, 0, 0, 2);";

    private static final Color HEADER_BG = Color.web("#111524");
    private static final Color FIELD_BG_TOP = Color.web("#0b1020");
    private static final Color FIELD_BG_BOTTOM = Color.web("#161d33");
    private static final Color HUD_TEXT = Color.web("#e8eef7");
    private static final Color HUD_SUBTEXT = Color.web("#8a92a6");
    private static final Color PLAYER_BODY = Color.web("#5ad0ff");
    private static final Color PLAYER_COCKPIT = Color.web("#eaf7ff");
    private static final Color PLAYER_BULLET = Color.web("#fff4c8");
    private static final Color ENEMY_BODY = Color.web("#e0665a");
    private static final Color ENEMY_CORE = Color.web("#ffd0c8");
    private static final Color ENEMY_BULLET = Color.web("#ff9a6a");
    private static final Color SHIELD_RING = Color.web("#8ae4ff");
    private static final Color POWERUP_COLOR = Color.web("#7ae08a");
    private static final Color STAR_COLOR = Color.web("#ffffff");
    private static final Color WIN_TEXT = Color.web("#7ae08a");
    private static final Color LOSE_TEXT = Color.web("#e06a6a");
    private static final Color TITLE_TEXT = Color.web("#8ae4ff");
    private static final Color BODY_TEXT = Color.web("#d0d4e0");
    private static final Color VEIL = Color.web("#000000", 0.42);
    private static final Color BAR_TRACK = Color.web("#ffffff", 0.10);
    private static final Color BAR_BORDER = Color.web("#ffffff", 0.16);
    /** 读条渐变：低 → 中 → 高，代表攻势由松到紧。 */
    private static final Color BAR_LOW = Color.web("#4cd18a");
    private static final Color BAR_MID = Color.web("#ffd54f");
    private static final Color BAR_HIGH = Color.web("#e05a5a");
    private static final Color BAR_LABEL = Color.web("#8a92a6");

    // ---- 形状 ----
    private static final double PLAYER_NOSE_RATIO = 0.5;
    private static final double ENEMY_NOSE_RATIO = 0.5;
    private static final double SHIELD_RADIUS_PAD = 10;
    private static final double POWERUP_RADIUS = 11;
    private static final double BULLET_ARC = 3;

    // ---- 背景星点 ----
    private static final int STAR_COUNT = 70;
    private static final double STAR_MIN_RADIUS = 0.7;
    private static final double STAR_RADIUS_SPAN = 1.4;
    private static final double STAR_MIN_SPEED = 22;
    private static final double STAR_SPEED_SPAN = 46;

    /** 固定逻辑步长：每帧按 1/60 秒推进，与显示器刷新率解耦。 */
    private static final double STEP_SECONDS = 1.0 / 60;
    /** 单帧最多补多少时间，防止窗口被挂起后一次补上千帧。 */
    private static final double MAX_FRAME_SECONDS = 0.25;

    private final PlaneConfig config = new PlaneConfig();
    private final PlaneGame game = new PlaneGame();
    private final Set<KeyCode> pressedKeys = new HashSet<>();
    private final Random random = new Random();

    private final List<double[]> stars = new ArrayList<>();

    private Canvas canvas;
    private BorderPane root;
    private Label scoreLabel;
    private Label infoLabel;
    private Label shieldLabel;
    private Label livesLabel;
    private StackPane holder;
    private VBox overlay;
    private Label overlayTitle;
    private Label overlaySubtitle;
    private HBox overlayDivider;
    private GridPane overlayInfo;
    private Label overlayHint;
    private final List<Label> infoKeys = new ArrayList<>();
    private final List<Label> infoValues = new ArrayList<>();
    /** 结算遮罩上的按钮：开始界面显示「开始游戏」，结束后显示「再来一局」。 */
    private Button overlayButton;

    private Runnable backCallback;
    private java.util.function.Consumer<com.studio.plugin.MiniGameResult> resultSink;
    private boolean reported;
    private String pluginId = "plane";

    private AnimationTimer loop;
    /** 界面动效的累计时间（秒）：读条震动的相位由它驱动。 */
    private double animTime;
    /** 上一次的遮罩状态，用于检测切换并重放入场动画。 */
    private String lastOverlayKey = "";
    private boolean paused;
    /** 是否已按下开始；false 时停在开始界面，游戏逻辑不推进。 */
    private boolean started;
    private double accumulator;
    private long lastNanos;

    private int lastScore = -1;
    private int lastKills = -1;
    private int lastLives = -1;
    private int lastShield = -1;

    @Override
    public void execute(Stage stage, Map<String, Object> params) {
        readPluginId(params);
        if (isEmbedded(params)) {
            // 嵌入流程：界面由读取器托管 createEmbeddedView 的返回值。
            // 这里必须什么都不做，更不能自己 new Stage —— 否则就变成"另开一个窗口"。
            Logs.plugin(pluginId, "execute：嵌入模式，界面交给读取器托管");
            return;
        }
        Logs.plugin(pluginId, "execute：窗口模式，独立运行（仅供开发期调试，接入剧情时不走这条）");
        Stage window = new Stage();
        window.setTitle("飞机大战（插件窗口模式 · 仅独立调试）");
        window.setScene(new Scene(buildView()));
        window.setOnHidden(e -> stopLoop());
        window.show();
        Platform.runLater(() -> root.requestFocus());
    }

    @Override
    public Parent createEmbeddedView(Map<String, Object> params) {
        readPluginId(params);
        this.backCallback = params == null ? null : (Runnable) params.get(PARAM_BACK_CALLBACK);
        Object sink = params == null ? null : params.get(PARAM_RESULT_SINK);
        this.resultSink = (sink instanceof java.util.function.Consumer)
                ? (java.util.function.Consumer<com.studio.plugin.MiniGameResult>) sink : null;
        Logs.plugin(pluginId, "createEmbeddedView：构建嵌入界面"
                + (backCallback == null ? "（未拿到 back.callback）" : "（已拿到 back.callback）"));
        return buildView();
    }

    @Override
    public String displayName() {
        return "飞机大战";
    }

    @Override
    public void onDetach() {
        stopLoop(); // ★ 引擎在插件被移除时回调，必须停掉循环（CONTRIBUTING §2.6）
        Logs.plugin(pluginId, "onDetach：游戏循环已停止");
    }

    private static boolean isEmbedded(Map<String, Object> params) {
        return params != null && params.get(PARAM_EMBEDDED) instanceof Boolean b && b;
    }

    private void readPluginId(Map<String, Object> params) {
        Object id = params == null ? null : params.get(PARAM_PLUGIN_ID);
        if (id instanceof String s && !s.isBlank()) {
            pluginId = s;
        }
    }

    // =====================================================================
    // 界面搭建
    // =====================================================================

    private Parent buildView() {
        game.reset(config);
        paused = false;
        started = false;
        initStars();

        canvas = new Canvas(config.getViewWidth(), config.getViewHeight());

        holder = new StackPane(canvas);
        overlay = buildOverlay();
        overlay.setVisible(false);
        overlay.setManaged(false);
        holder.getChildren().add(overlay);

        root = new BorderPane();
        root.setTop(buildHeader());
        root.setCenter(holder);
        root.setFocusTraversable(true);

        bindInput();
        // 兜底：将来若有其它路径把界面从主舞台上摘下，也要停掉循环
        root.parentProperty().addListener((obs, old, now) -> {
            if (now == null) {
                stopLoop();
            }
        });

        render();
        startLoop();
        Platform.runLater(() -> root.requestFocus());
        return root;
    }

    private void initStars() {
        stars.clear();
        for (int i = 0; i < STAR_COUNT; i++) {
            double x = random.nextDouble() * config.getViewWidth();
            double y = config.getFieldTop() + random.nextDouble() * (config.getViewHeight() - config.getFieldTop());
            double r = STAR_MIN_RADIUS + random.nextDouble() * STAR_RADIUS_SPAN;
            double v = STAR_MIN_SPEED + random.nextDouble() * STAR_SPEED_SPAN;
            stars.add(new double[]{x, y, r, v});
        }
    }

    /** 按候选顺序挑一个系统里存在的字体，都没有就退回默认字体。 */
    private static String pickFamily(String[] candidates) {
        List<String> families = Font.getFamilies();
        for (String candidate : candidates) {
            if (families.contains(candidate)) {
                return candidate;
            }
        }
        return Font.getDefault().getFamily();
    }

    /** 把文字逐字用空格隔开，模拟字距（JavaFX CSS 不支持 letter-spacing）。 */
    private static String spaced(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(text.charAt(i));
        }
        return sb.toString();
    }

    /**
     * 统一按钮外观：渐变底 + 圆角描边 + 粗体；悬停提亮，并叠加 FxAnim 的缩放动效。
     *
     * <p><b>关键</b>：按钮必须 {@code setFocusTraversable(false)}。JavaFX 里按钮一旦获得焦点，
     * 空格会去触发这个按钮而不是游戏逻辑 —— 鼠标点过「暂停/继续」后焦点留在按钮上，
     * 于是空格变成暂停、整局按键都乱掉。</p>
     */
    private static Button styledButton(String text) {
        Button button = new Button(text);
        button.setFocusTraversable(false);
        button.setStyle(BUTTON_STYLE);
        button.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> button.setStyle(BUTTON_HOVER_STYLE));
        button.addEventHandler(MouseEvent.MOUSE_EXITED, e -> button.setStyle(BUTTON_STYLE));
        FxAnim.makeInteractive(button);
        return button;
    }

    private HBox buildHeader() {
        scoreLabel = new Label();
        scoreLabel.setStyle("-fx-text-fill: " + toHex(HUD_TEXT) + "; -fx-font-size: "
                + (int) HUD_TITLE_FONT_SIZE + "px; -fx-font-weight: bold;");

        infoLabel = new Label();
        infoLabel.setStyle("-fx-text-fill: " + toHex(HUD_SUBTEXT) + "; -fx-font-size: "
                + (int) HUD_FONT_SIZE + "px;");

        shieldLabel = new Label();
        shieldLabel.setStyle("-fx-text-fill: " + toHex(SHIELD_RING) + "; -fx-font-size: "
                + (int) HUD_FONT_SIZE + "px;");

        livesLabel = new Label();
        livesLabel.setStyle("-fx-text-fill: " + toHex(PLAYER_BULLET) + "; -fx-font-size: "
                + (int) HUD_FONT_SIZE + "px;");

        Button pause = styledButton("⏸ 暂停 / 继续");
        pause.setOnAction(e -> togglePause());

        Button restart = styledButton("↻ 重新开局");
        restart.setOnAction(e -> restart());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(HEADER_SPACING, scoreLabel, infoLabel, spacer,
                shieldLabel, livesLabel, pause, restart);
        // 嵌入模式下自建「返回剧情」按钮：走引擎回调，先收尾再切场景（CONTRIBUTING §2.6 第 4 条）
        if (backCallback != null) {
            Button back = styledButton("← 返回剧情");
            back.setOnAction(e -> leaveToStory());
            header.getChildren().add(back);
        }
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(HEADER_PADDING));
        header.setStyle("-fx-background-color: " + toHex(HEADER_BG) + ";");
        updateHud(true);
        return header;
    }

    private VBox buildOverlay() {
        overlayTitle = new Label();
        overlayTitle.setStyle(TITLE_STYLE_START);

        overlaySubtitle = new Label(spaced("AIR COMBAT"));
        overlaySubtitle.setStyle(SUBTITLE_STYLE);

        overlayDivider = buildDivider();
        overlayInfo = buildInfoGrid();

        overlayHint = new Label();
        overlayHint.setStyle(HINT_STYLE);

        overlayButton = styledButton("开始游戏");
        overlayButton.setOnAction(e -> startOrRestart());

        VBox box = new VBox(12, overlayTitle, overlaySubtitle, overlayDivider, overlayInfo,
                overlayHint, overlayButton);
        box.setAlignment(Pos.CENTER);
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        box.setPadding(new Insets(26, 40, 26, 40));
        // 透明：明暗交给画布上的聚光遮罩，不要面板也不要边框
        box.setStyle(OVERLAY_BOX_STYLE);
        return box;
    }

    /** 标题下的分隔线：两段细线夹一个菱形。 */
    private static HBox buildDivider() {
        HBox box = new HBox(10, dividerLine(), diamond(), dividerLine());
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static Region dividerLine() {
        Region line = new Region();
        line.setPrefSize(DIVIDER_WIDTH, DIVIDER_THICKNESS);
        line.setStyle("-fx-background-color: rgba(255,255,255,0.20);");
        return line;
    }

    private static Label diamond() {
        Label label = new Label("◆");
        label.setStyle("-fx-text-fill: rgba(255,255,255,0.35); -fx-font-size: 8px;");
        return label;
    }

    /** 信息区：左列标签右对齐、右列内容左对齐，做成两栏排版。 */
    private GridPane buildInfoGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(8);
        grid.setAlignment(Pos.CENTER);
        for (int row = 0; row < INFO_ROWS; row++) {
            Label key = new Label();
            key.setStyle(LABEL_STYLE);
            Label value = new Label();
            value.setStyle(VALUE_STYLE);
            GridPane.setHalignment(key, HPos.RIGHT);
            grid.add(key, 0, row);
            grid.add(value, 1, row);
            infoKeys.add(key);
            infoValues.add(value);
        }
        return grid;
    }

    /** 填一行信息；label 为空则整行隐藏。 */
    private void setInfoRow(int row, String label, String value) {
        Label key = infoKeys.get(row);
        Label val = infoValues.get(row);
        boolean show = label != null && !label.isEmpty();
        key.setText(show ? label : "");
        val.setText(show ? value : "");
        key.setVisible(show);
        key.setManaged(show);
        val.setVisible(show);
        val.setManaged(show);
    }

    /** 副标题与分隔线只在开始界面出现。 */
    private void setSubtitleVisible(boolean visible) {
        overlaySubtitle.setVisible(visible);
        overlaySubtitle.setManaged(visible);
        overlayDivider.setVisible(visible);
        overlayDivider.setManaged(visible);
    }

    // =====================================================================
    // 输入
    // =====================================================================

    private void bindInput() {
        root.setOnKeyPressed(event -> {
            pressedKeys.add(event.getCode());
            KeyCode code = event.getCode();
            if (code == KeyCode.P) {
                togglePause();
            } else if (code == KeyCode.R) {
                restart();
            } else if (code == KeyCode.SPACE || code == KeyCode.J) {
                if (!started || game.isOver()) {
                    startOrRestart();
                } else {
                    game.shoot();
                }
            }
            event.consume(); // 别让空格等按键冒泡到宿主场景
        });
        root.setOnKeyReleased(event -> {
            pressedKeys.remove(event.getCode());
            event.consume();
        });

        canvas.setOnMouseMoved(this::movePlayerToMouse);
        canvas.setOnMouseDragged(this::movePlayerToMouse);
    }

    /** 鼠标只控制横向，纵向仍由键盘控制，避免误触。 */
    private void movePlayerToMouse(MouseEvent event) {
        double dx = event.getX() - game.playerX();
        game.move(Math.max(-1, Math.min(1, dx / 12.0)), 0);
    }

    private void togglePause() {
        if (game.isOver() || !started) {
            return;
        }
        paused = !paused;
        if (!paused) {
            lastNanos = System.nanoTime(); // 避免恢复后一次性补上暂停期间的时间
            accumulator = 0;
        }
        render();
        refocus();
    }

    /** 把键盘焦点还给根节点：避免按键被某个控件吃掉。 */
    private void refocus() {
        if (root != null) {
            root.requestFocus();
        }
    }

    /** 遮罩按钮：已结束则重开，暂停中则继续，还没开始则开始。 */
    private void startOrRestart() {
        if (game.isOver()) {
            restart();
        } else if (paused) {
            togglePause();
        } else if (!started) {
            started = true;
            accumulator = 0;
            lastNanos = System.nanoTime();
            render();
            refocus();
        }
    }

    private void restart() {
        reported = false;
        game.reset(config);
        initStars();
        started = true;
        paused = false;
        accumulator = 0;
        lastNanos = System.nanoTime();
        hideOverlay();
        updateHud(true);
        render();
        refocus();
    }

    /** 结束本局并返回剧情：先停循环收尾，再调引擎回调（CONTRIBUTING §2.6 第 4 条）。 */
    private void leaveToStory() {
        if (!reported && resultSink != null) {   // 局中主动退出：按需求记为失败
            reported = true;
            resultSink.accept(com.studio.plugin.MiniGameResult.lose(game.score()));
        }
        stopLoop();
        if (backCallback != null) {
            backCallback.run();
        }
    }

    // =====================================================================
    // 游戏循环
    // =====================================================================

    private void startLoop() {
        stopLoop();
        lastNanos = System.nanoTime();
        accumulator = 0;
        loop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double elapsed = (now - lastNanos) / 1_000_000_000.0;
                lastNanos = now;
                accumulator += Math.min(elapsed, MAX_FRAME_SECONDS);

                while (accumulator >= STEP_SECONDS) {
                    // 背景动效始终推进：开始界面与结算界面的星空也是持续滚动的
                    scrollStars(STEP_SECONDS);
                    animTime += STEP_SECONDS;
                    if (started && !paused && !game.isOver()) {
                        game.move(moveDx(), moveDy());
                        if (isFiring()) {
                            game.shoot();
                        }
                        game.tick(STEP_SECONDS);
                    }
                    accumulator -= STEP_SECONDS;
                }
                if (started && game.isOver() && !reported && resultSink != null) {
                    reported = true;
                    resultSink.accept(game.isWin()
                            ? com.studio.plugin.MiniGameResult.win(game.score())
                            : com.studio.plugin.MiniGameResult.lose(game.score()));
                }
                updateHud(false);
                render();
            }
        };
        loop.start();
    }

    /** 幂等：onDetach 与兜底监听都可能触发。 */
    private void stopLoop() {
        if (loop != null) {
            loop.stop();
            loop = null;
        }
    }

    private double moveDx() {
        double dx = 0;
        if (pressedKeys.contains(KeyCode.LEFT) || pressedKeys.contains(KeyCode.A)) {
            dx -= 1;
        }
        if (pressedKeys.contains(KeyCode.RIGHT) || pressedKeys.contains(KeyCode.D)) {
            dx += 1;
        }
        return dx;
    }

    private double moveDy() {
        double dy = 0;
        if (pressedKeys.contains(KeyCode.UP) || pressedKeys.contains(KeyCode.W)) {
            dy -= 1;
        }
        if (pressedKeys.contains(KeyCode.DOWN) || pressedKeys.contains(KeyCode.S)) {
            dy += 1;
        }
        return dy;
    }

    private boolean isFiring() {
        return pressedKeys.contains(KeyCode.SPACE) || pressedKeys.contains(KeyCode.J);
    }

    private void scrollStars(double dt) {
        double top = config.getFieldTop();
        double height = config.getViewHeight() - top;
        for (double[] s : stars) {
            s[1] += s[3] * dt;
            if (s[1] > top + height) {
                s[1] = top;
                s[0] = random.nextDouble() * config.getViewWidth();
            }
        }
    }

    // =====================================================================
    // 绘制
    // =====================================================================

    private void updateHud(boolean force) {
        if (game.score() != lastScore || force) {
            lastScore = game.score();
            scoreLabel.setText("得分 " + lastScore);
        }
        if (game.kills() != lastKills || force) {
            lastKills = game.kills();
            infoLabel.setText("击落 " + lastKills + " / " + config.getWinKills());
        }
        if (game.lives() != lastLives || force) {
            lastLives = game.lives();
            livesLabel.setText("❤".repeat(Math.max(0, lastLives)));
        }
        int shield = (int) Math.ceil(game.shieldSeconds());
        if (shield != lastShield || force) {
            lastShield = shield;
            shieldLabel.setText(shield > 0 ? "🛡 " + shield + "s" : "");
        }
    }

    private void render() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        drawField(g);
        drawStars(g);
        drawPowerups(g);
        drawEnemies(g);
        drawBullets(g);
        drawPlayer(g);
        drawIntensityBar(g);
        drawOverlayVeil(g);
        refreshOverlay();
    }

    /**
     * 聚光式遮罩：上下浅、中间深，没有边框也没有面板。
     *
     * <p>靠明暗把视线收到中央的遮罩文字上，同时上下两端仍能看见滚动的星空。</p>
     */
    private void drawOverlayVeil(GraphicsContext g) {
        if (overlayKey().isEmpty()) {
            return;
        }
        g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.00, Color.web("#000000", 0.20)),
                new Stop(0.30, Color.web("#000000", 0.66)),
                new Stop(0.70, Color.web("#000000", 0.66)),
                new Stop(1.00, Color.web("#000000", 0.20))));
        g.fillRect(0, 0, config.getViewWidth(), config.getViewHeight());
    }

    /**
     * 敌机攻势强度读条。
     *
     * <p>画在信息栏下方预留的那一条空带里：整条用「绿 → 黄 → 红」的渐变铺满，
     * 再按当前强度裁剪填充宽度 —— 渐变的坐标是绝对的，所以强度越高，
     * 露出来的颜色越偏红，一眼就能看出现在有多紧张。</p>
     */
    private void drawIntensityBar(GraphicsContext g) {
        double baseX = BAR_MARGIN_X;
        double width = config.getViewWidth() - BAR_MARGIN_X * 2;
        double value = game.intensity();
        boolean full = value >= BAR_FULL_THRESHOLD;

        // 读条一满就震动告警：横向为主、纵向为辅，用时间驱动正弦偏移
        double shakeX = 0;
        double shakeY = 0;
        if (full) {
            shakeX = Math.sin(animTime * BAR_SHAKE_SPEED) * BAR_SHAKE_AMPLITUDE;
            shakeY = Math.cos(animTime * BAR_SHAKE_SPEED * BAR_SHAKE_Y_SPEED)
                    * BAR_SHAKE_AMPLITUDE * BAR_SHAKE_Y_RATIO;
        }
        double x = baseX + shakeX;
        double top = BAR_TOP + shakeY;

        // 标签与百分比：满格时转红并加粗，和震动的读条一起形成告警
        Color labelColor = full ? BAR_FULL_COLOR : BAR_LABEL;
        g.setFont(Font.font(null, FontWeight.BOLD, BAR_FONT_SIZE));
        g.setFill(labelColor);
        g.setTextAlign(TextAlignment.LEFT);
        g.fillText(full ? "敌机攻势强度 · 已满" : "敌机攻势强度", x, BAR_LABEL_BASELINE + shakeY);
        String percent = (int) Math.round(value * 100) + "%";
        g.setTextAlign(TextAlignment.RIGHT);
        g.fillText(percent, x + width, BAR_LABEL_BASELINE + shakeY);
        g.setTextAlign(TextAlignment.LEFT);

        g.setFill(BAR_TRACK);
        g.fillRoundRect(x, top, width, BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT);

        double filled = width * value;
        if (filled > 0.5) {
            g.setFill(new LinearGradient(x, 0, x + width, 0, false, CycleMethod.NO_CYCLE,
                    new Stop(0, BAR_LOW), new Stop(0.5, BAR_MID), new Stop(1, BAR_HIGH)));
            g.fillRoundRect(x, top, filled, BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT);
        }

        // 满格时描边变红并呼吸，否则是常态细描边
        if (full) {
            double alpha = 0.45 + 0.55 * Math.abs(Math.sin(animTime * BAR_GLOW_SPEED));
            g.setStroke(Color.color(BAR_FULL_COLOR.getRed(), BAR_FULL_COLOR.getGreen(),
                    BAR_FULL_COLOR.getBlue(), alpha));
            g.setLineWidth(2);
        } else {
            g.setStroke(BAR_BORDER);
            g.setLineWidth(1);
        }
        g.strokeRoundRect(x + 0.5, top + 0.5, width - 1, BAR_HEIGHT - 1, BAR_HEIGHT, BAR_HEIGHT);
        g.setLineWidth(1);
    }

    private void drawField(GraphicsContext g) {
        // 上深下浅的纵向渐变，营造纵深；用渐变而不是两段纯色，避免出现横向接缝
        g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, FIELD_BG_TOP), new Stop(1, FIELD_BG_BOTTOM)));
        g.fillRect(0, 0, config.getViewWidth(), config.getViewHeight());
    }

    private void drawStars(GraphicsContext g) {
        for (double[] s : stars) {
            g.setFill(Color.color(STAR_COLOR.getRed(), STAR_COLOR.getGreen(), STAR_COLOR.getBlue(), 0.25));
            g.fillOval(s[0] - s[2], s[1] - s[2], s[2] * 2, s[2] * 2);
        }
    }

    private void drawPlayer(GraphicsContext g) {
        double w = config.getPlayerWidth();
        double h = config.getPlayerHeight();
        double cx = game.playerX();
        double cy = game.playerY();
        double noseY = cy - h * PLAYER_NOSE_RATIO;
        double tailY = cy + h * PLAYER_NOSE_RATIO;

        g.setFill(PLAYER_BODY);
        g.fillPolygon(
                new double[]{cx, cx - w / 2.0, cx - w / 4.0, cx + w / 4.0, cx + w / 2.0},
                new double[]{noseY, tailY, tailY * 0.94 + cy * 0.06, tailY * 0.94 + cy * 0.06, tailY},
                5);

        g.setFill(PLAYER_COCKPIT);
        g.fillOval(cx - w / 8.0, cy - h / 8.0, w / 4.0, h / 4.0);

        if (game.hasShield()) {
            g.setStroke(SHIELD_RING);
            g.setLineWidth(2);
            double r = Math.max(w, h) / 2.0 + SHIELD_RADIUS_PAD;
            g.strokeOval(cx - r, cy - r, r * 2, r * 2);
        }
    }

    private void drawEnemies(GraphicsContext g) {
        double w = config.getEnemyWidth();
        double h = config.getEnemyHeight();
        for (PlaneGame.Enemy e : game.enemies()) {
            double noseY = e.y() + h * ENEMY_NOSE_RATIO;
            double topY = e.y() - h * ENEMY_NOSE_RATIO;
            g.setFill(ENEMY_BODY);
            g.fillPolygon(
                    new double[]{e.x(), e.x() - w / 2.0, e.x() - w / 4.0, e.x() + w / 4.0, e.x() + w / 2.0},
                    new double[]{noseY, topY, topY * 0.94 + e.y() * 0.06, topY * 0.94 + e.y() * 0.06, topY},
                    5);
            g.setFill(ENEMY_CORE);
            g.fillOval(e.x() - w / 8.0, e.y() - h / 8.0, w / 4.0, h / 4.0);
        }
    }

    private void drawBullets(GraphicsContext g) {
        double bw = config.getBulletWidth();
        double bh = config.getBulletHeight();

        g.setFill(PLAYER_BULLET);
        for (PlaneGame.Bullet b : game.bullets()) {
            g.fillRoundRect(b.x() - bw / 2.0, b.y() - bh / 2.0, bw, bh, BULLET_ARC, BULLET_ARC);
        }

        g.setFill(ENEMY_BULLET);
        double r = config.getEnemyBulletRadius();
        for (PlaneGame.Bullet b : game.enemyBullets()) {
            g.fillOval(b.x() - r, b.y() - r, r * 2, r * 2);
        }
    }

    private void drawPowerups(GraphicsContext g) {
        for (PlaneGame.Powerup p : game.powerups()) {
            g.setFill(Color.color(POWERUP_COLOR.getRed(), POWERUP_COLOR.getGreen(),
                    POWERUP_COLOR.getBlue(), 0.25));
            g.fillOval(p.x() - POWERUP_RADIUS * 1.6, p.y() - POWERUP_RADIUS * 1.6,
                    POWERUP_RADIUS * 3.2, POWERUP_RADIUS * 3.2);
            g.setFill(POWERUP_COLOR);
            g.fillOval(p.x() - POWERUP_RADIUS, p.y() - POWERUP_RADIUS,
                    POWERUP_RADIUS * 2, POWERUP_RADIUS * 2);
            g.setFill(Color.web("#0b1020"));
            g.setFont(Font.font(null, FontWeight.BOLD, 13));
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText("S", p.x(), p.y() + 5);
            g.setTextAlign(TextAlignment.LEFT);
        }
    }

    /** 当前该显示哪种遮罩；空串表示不显示。 */
    private String overlayKey() {
        if (game.isWin()) {
            return "win";
        }
        if (game.isLose()) {
            return "lose";
        }
        if (paused) {
            return "pause";
        }
        return started ? "" : "start";
    }

    /** 遮罩四态：开始 / 暂停 / 通关 / 失败；标题字体与文案随状态变化。 */
    private void refreshOverlay() {
        String key = overlayKey();
        if (key.isEmpty()) {
            hideOverlay();
            return;
        }

        switch (key) {
            case "start" -> {
                overlayTitle.setStyle(TITLE_STYLE_START);
                overlayTitle.setText(spaced("飞机大战"));
                overlaySubtitle.setText(spaced("AIR COMBAT"));
                setSubtitleVisible(true);
                setInfoRow(0, "移动", "方向键 / WASD");
                setInfoRow(1, "射击", "空格 / J");
                setInfoRow(2, "目标", "击落 " + config.getWinKills() + " 架敌机");
                setInfoRow(3, "道具", "拾取 S 获得 " + (int) config.getShieldDuration() + " 秒护盾");
                overlayHint.setText("按 空格 开始游戏");
                overlayButton.setText("开始游戏");
            }
            case "pause" -> {
                overlayTitle.setStyle(TITLE_STYLE_PAUSE);
                overlayTitle.setText(spaced("已暂停"));
                setSubtitleVisible(false);
                hideInfoRows();
                overlayHint.setText("按 P 或点击下方按钮继续游戏");
                overlayButton.setText("继续游戏");
            }
            case "win" -> {
                overlayTitle.setStyle(TITLE_STYLE_WIN);
                overlayTitle.setText(spaced("通关！"));
                setSubtitleVisible(false);
                setInfoRow(0, "击落", game.kills() + " 架");
                setInfoRow(1, "得分", String.valueOf(game.score()));
                setInfoRow(2, "", "");
                setInfoRow(3, "", "");
                overlayHint.setText("按 空格 再来一局");
                overlayButton.setText("再来一局");
            }
            default -> {
                overlayTitle.setStyle(TITLE_STYLE_LOSE);
                overlayTitle.setText(spaced("GAME OVER"));
                setSubtitleVisible(false);
                setInfoRow(0, "击落", game.kills() + " / " + config.getWinKills() + " 架");
                setInfoRow(1, "得分", String.valueOf(game.score()));
                setInfoRow(2, "", "");
                setInfoRow(3, "", "");
                overlayHint.setText("按 空格 再来一局");
                overlayButton.setText("再来一局");
            }
        }

        boolean changed = !key.equals(lastOverlayKey);
        lastOverlayKey = key;
        showOverlay(changed);
    }

    private void hideInfoRows() {
        for (int row = 0; row < INFO_ROWS; row++) {
            setInfoRow(row, "", "");
        }
    }

    /**
     * 显示遮罩；状态切换时播放一段「由小到大淡入」的入场动画。
     *
     * @param animate true 表示这是状态切换（需要动画），false 表示已在显示中（保持原样）
     */
    private void showOverlay(boolean animate) {
        overlay.setVisible(true);
        overlay.setManaged(true);
        if (!animate) {
            return;
        }
        overlay.setOpacity(0);
        overlay.setScaleX(OVERLAY_ENTER_SCALE);
        overlay.setScaleY(OVERLAY_ENTER_SCALE);

        FadeTransition fade = new FadeTransition(Duration.millis(OVERLAY_FADE_MS), overlay);
        fade.setToValue(1);
        ScaleTransition scale = new ScaleTransition(Duration.millis(OVERLAY_SCALE_MS), overlay);
        scale.setToX(1);
        scale.setToY(1);
        scale.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(fade, scale).play();
    }

    private void hideOverlay() {
        if (overlay != null && overlay.isVisible()) {
            overlay.setVisible(false);
            overlay.setManaged(false);
        }
        lastOverlayKey = "";
    }

    /** 把 Color 转成 CSS 用的 #rrggbb 字面量，避免各处重复写死颜色字符串。 */
    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }
}
