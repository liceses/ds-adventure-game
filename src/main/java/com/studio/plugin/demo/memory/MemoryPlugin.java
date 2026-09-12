package com.studio.plugin.demo.memory;

import com.studio.plugin.GamePlugin;
import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.Map;

/**
 * 记忆翻牌小游戏插件（内置形态，事件ID <code>memory</code>）。
 *
 * <p>玩法规格见 <code>docs/ds-adventrue/记忆翻牌.md</code>：牌背面朝上铺开，每次翻两张，
 * 图案相同就留着，不同则盖回去；靠记忆把所有对子翻出来即通关。支持单人计时与双人轮流计分。</p>
 *
 * <p>流程：进入插件先显示<b>开始界面</b>（选难度 / 主题 / 模式），点难度才开始这一局；
 * 通关或失败后可以再来一局，也可以回到开始界面换难度。</p>
 *
 * <p>规则全部委托给 {@link MemoryGame}，本类只负责界面与交互，
 * {@link #onDetach()} 幂等停掉游戏循环。</p>
 */
public class MemoryPlugin implements GamePlugin {

    // ---------------- 难度与主题 ----------------

    private static final int[] DIFFICULTY_ROWS = {3, 4, 4};
    private static final int[] DIFFICULTY_COLS = {4, 6, 8};
    private static final String[] DIFFICULTY_NAMES = {"简单", "普通", "困难"};
    /** 难度按钮里的第二列：盘面尺寸。 */
    private static final String[] DIFFICULTY_GRID = {"4 x 3", "6 x 4", "8 x 4"};
    /** 难度按钮里的第三列：对子数。 */
    private static final String[] DIFFICULTY_PAIRS = {"6 对牌", "12 对牌", "16 对牌"};
    private static final int DEFAULT_DIFFICULTY = 1;

    private static final String[] THEME_NAMES = {"动物", "水果", "汉字"};
    private static final String[] THEMES = {
        MemoryConfig.THEME_ANIMALS, MemoryConfig.THEME_FRUITS, MemoryConfig.THEME_HANZI,
    };

    private static final String[] MODE_NAMES = {"单人计时", "双人轮流"};

    // ---------------- 尺寸 ----------------

    /**
     * 插件内容的固定首选尺寸。
     *
     * <p><b>必须固定：</b>窗口（或嵌入容器）的尺寸只在开始时定一次，
     * 如果内容的首选尺寸随难度/模式变，切到"困难"或"双人"时右侧超出部分会被裁掉
     * —— 顶栏最右边的【返回选难度】首当其冲，盘面的居中位置也会跟着跳。</p>
     */
    private static final double CONTENT_WIDTH = 660;
    private static final double CONTENT_HEIGHT = 480;

    private static final double CARD_SPACING = 6;
    /** 牌面边长按列数自适应，让三档难度的盘面宽度接近，观感一致。 */
    private static final double BOARD_SIDE_MARGIN = 24;
    private static final double MIN_CARD_SIZE = 54;
    private static final double MAX_CARD_SIZE = 84;
    /** 牌面图案字号相对牌边长的比例。 */
    private static final double CARD_FONT_RATIO = 0.42;
    private static final double HEADER_SPACING = 10;
    private static final double HEADER_PADDING = 10;
    private static final double STAT_FONT_SIZE = 14;
    private static final double OVERLAY_TITLE_FONT_SIZE = 28;
    private static final double OVERLAY_BODY_FONT_SIZE = 15;
    private static final double MENU_TITLE_FONT_SIZE = 32;
    private static final double MENU_HINT_FONT_SIZE = 14;
    private static final double MENU_BUTTON_WIDTH = 280;
    /** 难度按钮内三列的固定宽度，保证三行文字左右对齐。 */
    private static final double DIFFICULTY_NAME_WIDTH = 56;
    private static final double DIFFICULTY_GRID_WIDTH = 70;
    private static final double DIFFICULTY_PAIRS_WIDTH = 80;
    /** 背景点阵的间距与点径。 */
    private static final double PATTERN_STEP = 26;
    private static final double PATTERN_DOT = 2;
    private static final double MENU_SPACING = 12;
    private static final double MENU_ROW_SPACING = 6;
    private static final double MENU_PANEL_PADDING = 26;
    /** 翻牌动画的单边时长（秒）；整个翻转 = 收拢 + 展开 ≈ 两倍。 */
    private static final double FLIP_HALF_SECONDS = 0.11;
    /** 牌的三个显示面，用于判断是否需要播放翻转动画。 */
    private static final int FACE_DOWN = 0;
    private static final int FACE_UP = 1;
    private static final int FACE_MATCHED = 2;

    /** 屏幕中央浮动提示的显示时长（秒）。 */
    private static final double FLASH_SECONDS = 1.6;
    /** 规则页自动开始的倒计时秒数。 */
    private static final double RULES_COUNTDOWN_SECONDS = 5;
    /** 规则正文的换行宽度与字号。 */
    private static final double RULES_TEXT_WIDTH = 400;
    private static final String RULES_TITLE_STYLE =
            "-fx-text-fill: #8ae4ff; -fx-font-size: 24px; -fx-font-weight: bold;";
    private static final String RULES_BODY_STYLE =
            "-fx-text-fill: #d5d9ea; -fx-font-size: 14px; -fx-line-spacing: 3px;";
    private static final String RULES_COUNTDOWN_STYLE =
            "-fx-text-fill: #ffd54f; -fx-font-size: 15px;";

    private static final double STEP_SECONDS = 1.0 / 30;
    private static final double MAX_FRAME_SECONDS = 0.25;

    private static final String HIDDEN_FACE = "?";

    /**
     * 牌的配色（字体与尺寸部分由 {@link #cardBaseStyle()} 按当前牌面大小拼在前面）。
     *
     * <p><b>基底必须把 padding 置 0：</b>JavaFX 按钮的默认内边距随字号缩放（约 0.667em），
     * 28px 字号下左右各吃掉约 19px，68px 的牌只剩约 26px 放得下图案，
     * 稍宽的字形（emoji）会被 Labeled 的 textOverrun 截成"..."。</p>
     */
    private static final String STYLE_DOWN =
            "-fx-background-color: #2a2f45; -fx-text-fill: #6b74a0; -fx-border-color: #3a4160; -fx-border-width: 1;";
    private static final String STYLE_UP =
            "-fx-background-color: #45507a; -fx-text-fill: #ffffff; -fx-border-color: #7d8ac4; -fx-border-width: 2;";
    private static final String STYLE_MATCHED =
            "-fx-background-color: #1f3a2c; -fx-text-fill: #7be08a; -fx-border-color: #2f5a42; -fx-border-width: 1;"
            + "-fx-opacity: 0.75;";

    private static final String STYLE_STAT = "-fx-text-fill: #c9cbe0; -fx-font-size: " + (int) STAT_FONT_SIZE + "px;";
    private static final String HEADER_BUTTON_STYLE =
            "-fx-background-color: #2a2f45; -fx-text-fill: #d5d9ea; -fx-background-radius: 6;"
            + " -fx-border-color: #3a4160; -fx-border-radius: 6; -fx-cursor: hand; -fx-padding: 5 12 5 12;";
    /** 双人模式下两位玩家的代表色，用来一眼看出轮到谁。 */
    private static final String PLAYER_ONE_COLOR = "#6cb6ff";
    private static final String PLAYER_TWO_COLOR = "#ffb86c";
    private static final String STYLE_HINT = "-fx-text-fill: #8a92a6; -fx-font-size: " + (int) STAT_FONT_SIZE + "px;";

    private static final String MENU_VEIL_STYLE = "-fx-background-color: rgba(8,10,18,0.74);";
    private static final String MENU_PANEL_STYLE =
            "-fx-background-color: #1b1f30; -fx-background-radius: 14;"
            + " -fx-border-color: #333a55; -fx-border-radius: 14; -fx-padding: " + (int) MENU_PANEL_PADDING + ";";
    private static final String MENU_TITLE_STYLE =
            "-fx-text-fill: #8ae4ff; -fx-font-size: " + (int) MENU_TITLE_FONT_SIZE + "px; -fx-font-weight: bold;";
    private static final String MENU_HINT_STYLE =
            "-fx-text-fill: #9aa2bd; -fx-font-size: " + (int) MENU_HINT_FONT_SIZE + "px;";
    private static final String MENU_LABEL_STYLE =
            "-fx-text-fill: #c9cbe0; -fx-font-size: " + (int) STAT_FONT_SIZE + "px;";
    private static final String DIFFICULTY_BUTTON_STYLE =
            "-fx-font-size: 15px; -fx-background-color: #2f6fd0; -fx-text-fill: #ffffff;"
            + " -fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 10 16 10 16;";
    private static final String TOGGLE_ON_STYLE =
            "-fx-background-color: #2f6fd0; -fx-text-fill: #ffffff; -fx-background-radius: 6; -fx-cursor: hand;";
    private static final String TOGGLE_OFF_STYLE =
            "-fx-background-color: #2a2f45; -fx-text-fill: #9aa2bd; -fx-background-radius: 6; -fx-cursor: hand;";

    private final MemoryConfig config = new MemoryConfig();
    private final MemoryGame game = new MemoryGame();

    private BorderPane root;
    private Canvas patternLayer;
    private GridPane board;
    private StackPane holder;
    private StackPane menu;
    private StackPane rules;
    private Label rulesBody;
    private Label rulesCountdownLabel;
    private Label flashLabel;
    private double flashTimer;
    private double rulesCountdown;
    private boolean inRules;
    private VBox overlay;
    private Label statLabel;
    private Label playerLabel;
    private Label hintLabel;
    private Label overlayTitle;
    private Label overlayDesc;

    private final Button[] themeButtons = new Button[THEMES.length];
    private final Button[] modeButtons = new Button[MODE_NAMES.length];
    private Button[] cardButtons = new Button[0];
    private int[] cardFaces = new int[0];
    private ScaleTransition[] cardFlips = new ScaleTransition[0];
    private double cardSize = MIN_CARD_SIZE;

    private AnimationTimer loop;
    private double accumulator;
    private long lastNanos;
    private int difficulty = DEFAULT_DIFFICULTY;
    private int themeIndex;
    private int modeIndex;
    private int shownSeconds = -1;
    private boolean inMenu = true;
    /** 宿主（读取器）传入的参数，用于取 back.callback。 */
    private Map<String, Object> hostParams;

    @Override
    public void execute(Stage stage, Map<String, Object> params) {
        hostParams = params;
        boolean embedded = params != null && params.get(PARAM_EMBEDDED) instanceof Boolean b && b;
        if (embedded) {
            return; // 嵌入模式下由读取器托管 createEmbeddedView 返回的界面
        }
        Stage window = new Stage();
        window.setTitle("记忆翻牌（插件窗口模式）");
        window.setScene(new Scene(buildView()));
        window.setOnHidden(e -> stopLoop());
        window.show();
        Platform.runLater(() -> root.requestFocus());
    }

    @Override
    public Parent createEmbeddedView(Map<String, Object> params) {
        hostParams = params;
        return buildView();
    }

    @Override
    public String displayName() {
        return "记忆翻牌小游戏";
    }

    @Override
    public void onDetach() {
        stopLoop(); // ★ 引擎在插件被移除时回调，必须停掉循环
    }

    // =====================================================================
    // 界面搭建
    // =====================================================================

    private Parent buildView() {
        applySettings();

        board = new GridPane();
        board.setHgap(CARD_SPACING);
        board.setVgap(CARD_SPACING);
        board.setAlignment(Pos.CENTER);

        overlay = buildResultOverlay();
        overlay.setVisible(false);
        overlay.setManaged(false);

        menu = buildMenu();
        rules = buildRulesOverlay();
        rules.setVisible(false);
        rules.setManaged(false);
        patternLayer = buildPatternLayer();
        flashLabel = buildFlashLabel();
        holder = new StackPane(patternLayer, board, flashLabel, overlay, rules, menu);

        root = new BorderPane();
        root.setTop(buildHeader());
        root.setCenter(holder);
        root.setFocusTraversable(true);
        // 固定内容尺寸：窗口/嵌入容器尺寸不会随难度或模式变化，避免右侧被裁与位置跳动
        root.setPrefSize(CONTENT_WIDTH, CONTENT_HEIGHT);
        root.setMinSize(CONTENT_WIDTH, CONTENT_HEIGHT);

        bindKeys();
        root.parentProperty().addListener((obs, old, now) -> {
            if (now == null) {
                stopLoop();
            }
        });

        // 先摆一副默认难度的牌在背后，再盖上开始界面
        game.reset(config);
        rebuildBoard();
        showMenu();
        bindPatternSize();
        startLoop();
        Platform.runLater(() -> root.requestFocus());
        return root;
    }

    /**
     * 顶栏按钮。
     *
     * <p>固定成"最小宽度 = 首选宽度"，否则当左侧文字变长时 HBox 会把按钮压扁，
     * 文字被 Labeled 截成"..."（双人模式下的玩家信息就会触发这个问题）。</p>
     */
    private Button headerButton(String text, String tooltip, Runnable action) {
        Button button = new Button(text);
        button.setStyle(HEADER_BUTTON_STYLE);
        button.setTooltip(new Tooltip(tooltip));
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(e -> action.run());
        return button;
    }

    /**
     * 在顶栏加一个「返回剧情」按钮。
     *
     * <p>读取器会在顶部自动生成标题栏与返回按钮，所以这个按钮不是必需的；
     * 但 CONTRIBUTING §2.3 建议插件自己也提供一个，玩家在小游戏区域内就能直接退出。
     * `back.callback` 为空时（例如独立运行）不添加。</p>
     */
    private Button backButtonIfRequested(Map<String, Object> params) {
        Object back = params == null ? null : params.get(PARAM_BACK_CALLBACK);
        if (!(back instanceof Runnable callback)) {
            return null;
        }
        Button button = headerButton("✖ 结束本局并返回剧情", "结束小游戏，回到进入前的剧情场景", () -> {
            stopLoop();       // 先自己收尾，再让引擎切回剧情（§2.3 的推荐顺序）
            callback.run();
        });
        return button;
    }

    /** 背景画布跟随内容区尺寸，内容区变大/切难度时图案自动铺满。 */
    private void bindPatternSize() {
        patternLayer.widthProperty().bind(holder.widthProperty());
        patternLayer.heightProperty().bind(holder.heightProperty());
    }

    private VBox buildHeader() {
        statLabel = new Label();
        statLabel.setStyle(STYLE_STAT);
        playerLabel = new Label();
        playerLabel.setStyle(STYLE_STAT);
        hintLabel = new Label();
        hintLabel.setStyle(STYLE_HINT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button peekBtn = headerButton("💡 提示 (H)", "短暂展示全部牌面，每局限用 1 次", this::usePeek);
        Button restartBtn = headerButton("🔄 重新开始 (R)", "按当前难度重新开一局，计时与步数归零", this::restart);
        Button menuBtn = headerButton("⬅ 返回选难度 (Esc)", "回到开始界面，重新选择难度 / 图案 / 模式", this::showMenu);

        HBox statsRow = new HBox(HEADER_SPACING, statLabel, hintLabel);
        statsRow.setAlignment(Pos.CENTER_LEFT);

        HBox actionRow = new HBox(HEADER_SPACING, playerLabel, spacer, peekBtn, restartBtn, menuBtn);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        // 宿主提供了 back.callback 才加（独立运行时没有，不显示）
        Button backBtn = backButtonIfRequested(hostParams);
        if (backBtn != null) {
            actionRow.getChildren().add(backBtn);
        }

        VBox header = new VBox(6, statsRow, actionRow);
        header.setPadding(new Insets(HEADER_PADDING));
        header.setStyle("-fx-background-color: #1a1d27;");
        return header;
    }

    /** 开始界面：选难度 / 主题 / 模式；点难度即开始这一局。 */
    private StackPane buildMenu() {
        Label title = new Label("记忆翻牌");
        title.setStyle(MENU_TITLE_STYLE);

        Label hint = new Label("每次翻两张，图案相同就配对成功；全部配对即通关。");
        hint.setStyle(MENU_HINT_STYLE);

        Label difficultyLabel = new Label("选择难度开始游戏");
        difficultyLabel.setStyle(MENU_LABEL_STYLE);

        VBox difficultyBox = new VBox(MENU_ROW_SPACING);
        difficultyBox.setAlignment(Pos.CENTER);
        for (int i = 0; i < DIFFICULTY_NAMES.length; i++) {
            final int level = i;
            Button button = new Button();
            button.setPrefWidth(MENU_BUTTON_WIDTH);
            button.setStyle(DIFFICULTY_BUTTON_STYLE);
            // 用固定宽度的三列做图形（而不是一整串文字），三行才会左右对齐
            button.setGraphic(difficultyRow(i));
            button.setAlignment(Pos.CENTER_LEFT);
            button.setOnAction(e -> startGame(level));
            difficultyBox.getChildren().add(button);
        }

        HBox themeRow = new HBox(MENU_ROW_SPACING);
        themeRow.setAlignment(Pos.CENTER);
        themeRow.getChildren().add(menuLabel("图案"));
        for (int i = 0; i < THEME_NAMES.length; i++) {
            final int index = i;
            themeButtons[i] = new Button(THEME_NAMES[i]);
            themeButtons[i].setOnAction(e -> selectTheme(index));
            themeRow.getChildren().add(themeButtons[i]);
        }

        HBox modeRow = new HBox(MENU_ROW_SPACING);
        modeRow.setAlignment(Pos.CENTER);
        modeRow.getChildren().add(menuLabel("模式"));
        for (int i = 0; i < MODE_NAMES.length; i++) {
            final int index = i;
            modeButtons[i] = new Button(MODE_NAMES[i]);
            modeButtons[i].setOnAction(e -> selectMode(index));
            modeRow.getChildren().add(modeButtons[i]);
        }

        VBox panel = new VBox(MENU_SPACING, title, hint, difficultyLabel, difficultyBox, themeRow, modeRow);
        panel.setAlignment(Pos.CENTER);
        panel.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        panel.setStyle(MENU_PANEL_STYLE);

        StackPane veil = new StackPane(panel);
        veil.setStyle(MENU_VEIL_STYLE);
        refreshMenuToggles();
        return veil;
    }

    /** 难度按钮内部的三列：难度名 / 盘面 / 对数；列宽固定，三行自然对齐。 */
    private HBox difficultyRow(int index) {
        HBox row = new HBox(MENU_ROW_SPACING * 2,
                columnLabel(DIFFICULTY_NAMES[index], DIFFICULTY_NAME_WIDTH),
                columnLabel(DIFFICULTY_GRID[index], DIFFICULTY_GRID_WIDTH),
                columnLabel(DIFFICULTY_PAIRS[index], DIFFICULTY_PAIRS_WIDTH));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Label columnLabel(String text, double width) {
        Label label = new Label(text);
        label.setPrefWidth(width);
        label.setMinWidth(width);
        label.setMaxWidth(width);
        label.setAlignment(Pos.CENTER_LEFT);
        label.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 15px;");
        return label;
    }

    /**
     * 背景图案层：竖向渐变 + 细点阵 + 中心柔光。
     *
     * <p>全部用 JavaFX 绘制，不依赖任何图片素材；尺寸跟着 holder 走，
     * 所以开始界面和游戏界面共用同一层背景。</p>
     */
    private Canvas buildPatternLayer() {
        Canvas canvas = new Canvas(1, 1);
        canvas.widthProperty().addListener((obs, old, now) -> drawPattern(canvas));
        canvas.heightProperty().addListener((obs, old, now) -> drawPattern(canvas));
        return canvas;
    }

    private void drawPattern(Canvas canvas) {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, width, height);

        g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#171b2e")), new Stop(1, Color.web("#0c0f1c"))));
        g.fillRect(0, 0, width, height);

        g.setFill(Color.web("#ffffff", 0.05));
        for (double y = PATTERN_STEP / 2; y < height; y += PATTERN_STEP) {
            for (double x = PATTERN_STEP / 2; x < width; x += PATTERN_STEP) {
                g.fillOval(x, y, PATTERN_DOT, PATTERN_DOT);
            }
        }

        g.setFill(new RadialGradient(0, 0, 0.5, 0.42, 0.62, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#4a7fd0", 0.20)), new Stop(1, Color.web("#000000", 0.0))));
        g.fillRect(0, 0, width, height);
    }

    private Label menuLabel(String text) {
        Label label = new Label(text);
        label.setStyle(MENU_LABEL_STYLE);
        return label;
    }

    /**
     * 规则页：选完难度后先看一眼玩法，倒计时结束自动开始。
     *
     * <p>倒计时期间游戏逻辑不推进，所以不会出现"还没开始就被计时"的问题；
     * 想立刻开局可以点【立即开始】或按空格。</p>
     */
    private StackPane buildRulesOverlay() {
        Label title = new Label("玩法规则");
        title.setStyle(RULES_TITLE_STYLE);

        rulesBody = new Label();
        rulesBody.setStyle(RULES_BODY_STYLE);
        rulesBody.setWrapText(true);
        rulesBody.setMaxWidth(RULES_TEXT_WIDTH);

        rulesCountdownLabel = new Label();
        rulesCountdownLabel.setStyle(RULES_COUNTDOWN_STYLE);

        Button startNow = new Button("立即开始 ▶");
        startNow.setStyle(DIFFICULTY_BUTTON_STYLE);
        startNow.setOnAction(e -> beginPlay());

        VBox panel = new VBox(MENU_SPACING, title, rulesBody, rulesCountdownLabel, startNow);
        panel.setAlignment(Pos.CENTER);
        panel.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        panel.setStyle(MENU_PANEL_STYLE);

        StackPane veil = new StackPane(panel);
        veil.setStyle(MENU_VEIL_STYLE);
        return veil;
    }

    private void showRules() {
        inRules = true;
        rulesCountdown = RULES_COUNTDOWN_SECONDS;
        rulesBody.setText(rulesText());
        rulesCountdownLabel.setText(countdownText());
        rules.setVisible(true);
        rules.setManaged(true);
        rules.toFront();
    }

    /** 倒计时结束或被跳过，正式进入对局。 */
    private void beginPlay() {
        inRules = false;
        rules.setVisible(false);
        rules.setManaged(false);
    }

    private void tickRules(double dt) {
        rulesCountdown -= dt;
        if (rulesCountdown <= 0) {
            beginPlay();
            return;
        }
        rulesCountdownLabel.setText(countdownText());
    }

    private String countdownText() {
        return (int) Math.ceil(rulesCountdown) + " 秒后自动开始（或点【立即开始】）";
    }

    /** 规则正文按当前设置动态生成，双人局会多两条回合说明。 */
    private String rulesText() {
        StringBuilder sb = new StringBuilder();
        sb.append("· 每次翻两张牌，图案相同就配对成功并保留\n");
        sb.append("· 图案不同，约 0.9 秒后自动盖回\n");
        sb.append("  这几秒正好用来记住刚才两张的位置\n");
        sb.append("· 把所有对子都翻出来即通关\n");
        if (config.isTwoPlayer()) {
            sb.append("· 双人轮流：翻到对子继续本回合，翻错换人\n");
            sb.append("· 全部翻完后，对子多的一方获胜\n");
        } else {
            sb.append("· 单人计时：步数越少、用时越短越好\n");
        }
        sb.append("· 按 H 可偷看全部牌面，每局限 ").append(config.getPeekUses()).append(" 次\n");
        sb.append("\n本局设置：")
                .append(DIFFICULTY_NAMES[difficulty]).append("  ·  ")
                .append(DIFFICULTY_GRID[difficulty]).append("  ·  ")
                .append(DIFFICULTY_PAIRS[difficulty]).append("\n")
                .append("图案 ").append(THEME_NAMES[themeIndex])
                .append("   ·   模式 ").append(MODE_NAMES[modeIndex]);
        return sb.toString();
    }

    /** 屏幕中央的浮动提示：提示次数用完这类"按键无效果"的反馈。 */
    private Label buildFlashLabel() {
        Label label = new Label();
        label.setStyle("-fx-text-fill: #ffd54f; -fx-font-size: 19px; -fx-font-weight: bold;"
                + " -fx-background-color: rgba(10,12,20,0.82); -fx-background-radius: 8;"
                + " -fx-padding: 8 18 8 18;");
        label.setMouseTransparent(true);
        label.setVisible(false);
        StackPane.setAlignment(label, Pos.CENTER);
        return label;
    }

    private void showFlash(String text) {
        flashLabel.setText(text);
        flashLabel.setVisible(true);
        flashTimer = FLASH_SECONDS;
    }

    private void tickFlash(double dt) {
        if (flashTimer <= 0) {
            return;
        }
        flashTimer -= dt;
        if (flashTimer <= 0) {
            flashLabel.setVisible(false);
        }
    }

    private VBox buildResultOverlay() {
        overlayTitle = new Label();
        overlayTitle.setStyle("-fx-text-fill: #7be08a; -fx-font-size: " + (int) OVERLAY_TITLE_FONT_SIZE + "px; -fx-font-weight: bold;");
        overlayDesc = new Label();
        overlayDesc.setStyle("-fx-text-fill: #d0d4e0; -fx-font-size: " + (int) OVERLAY_BODY_FONT_SIZE + "px;");

        Button again = new Button("再来一局");
        again.setOnAction(e -> restart());
        Button backToMenu = new Button("换个难度");
        backToMenu.setOnAction(e -> showMenu());

        HBox buttons = new HBox(10, again, backToMenu);
        buttons.setAlignment(Pos.CENTER);

        VBox box = new VBox(12, overlayTitle, overlayDesc, buttons);
        box.setAlignment(Pos.CENTER);
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        box.setPadding(new Insets(24));
        box.setStyle("-fx-background-color: rgba(16,19,34,0.94); -fx-background-radius: 12;");
        return box;
    }

    // =====================================================================
    // 开始界面与设置
    // =====================================================================

    private void showMenu() {
        inMenu = true;
        inRules = false;
        if (rules != null) {
            rules.setVisible(false);
            rules.setManaged(false);
        }
        hideOverlay();
        menu.setVisible(true);
        menu.setManaged(true);
        menu.toFront();
    }

    private void hideMenu() {
        inMenu = false;
        menu.setVisible(false);
        menu.setManaged(false);
    }

    private void selectTheme(int index) {
        themeIndex = index;
        config.setTheme(THEMES[themeIndex]);
        refreshMenuToggles();
    }

    private void selectMode(int index) {
        modeIndex = index;
        config.setTwoPlayer(index == 1);
        refreshMenuToggles();
    }

    private void refreshMenuToggles() {
        for (int i = 0; i < themeButtons.length; i++) {
            themeButtons[i].setStyle(i == themeIndex ? TOGGLE_ON_STYLE : TOGGLE_OFF_STYLE);
        }
        for (int i = 0; i < modeButtons.length; i++) {
            modeButtons[i].setStyle(i == modeIndex ? TOGGLE_ON_STYLE : TOGGLE_OFF_STYLE);
        }
    }

    /** 把当前难度与主题写进 config（开局前调用）。 */
    private void applySettings() {
        config.setGrid(DIFFICULTY_ROWS[difficulty], DIFFICULTY_COLS[difficulty]);
        config.setTheme(THEMES[themeIndex]);
        config.setTwoPlayer(modeIndex == 1);
    }

    private void startGame(int level) {
        difficulty = level;
        applySettings();
        resetRound();
        hideMenu();
        showRules();
    }

    private void restart() {
        applySettings();
        resetRound();
        hideMenu();
    }

    private void resetRound() {
        game.reset(config);
        shownSeconds = -1;
        rebuildBoard();
    }

    // =====================================================================
    // 牌面与刷新
    // =====================================================================

    /** 按当前列数算牌面边长，使三档难度的盘面宽度接近。 */
    private double computeCardSize() {
        int columns = Math.max(1, config.getColumns());
        double available = CONTENT_WIDTH - BOARD_SIDE_MARGIN * 2 - (columns - 1) * CARD_SPACING;
        return Math.max(MIN_CARD_SIZE, Math.min(MAX_CARD_SIZE, available / columns));
    }

    /** 牌面基底样式：尺寸与字号跟随当前牌面大小。 */
    private String cardBaseStyle() {
        return "-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 0; -fx-font-size: "
                + (int) Math.round(cardSize * CARD_FONT_RATIO) + "px;";
    }

    private void rebuildBoard() {
        board.getChildren().clear();
        cardSize = computeCardSize();
        int count = game.cardCount();
        cardButtons = new Button[count];
        for (int i = 0; i < count; i++) {
            Button card = new Button(HIDDEN_FACE);
            card.setPrefSize(cardSize, cardSize);
            card.setMinSize(cardSize, cardSize);
            card.setMaxSize(cardSize, cardSize);
            final int index = i;
            card.setOnAction(e -> {
                game.flip(index);
                refreshBoard();
            });
            cardButtons[i] = card;
            board.add(card, i % config.getColumns(), i / config.getColumns());
        }
        cardFaces = new int[count];
        cardFlips = new ScaleTransition[count];
        for (int i = 0; i < count; i++) {
            cardFaces[i] = FACE_DOWN;
            paintFace(i, FACE_DOWN);   // 开局直接是背面，不播翻转
        }
        refreshBoard();
    }

    private void refreshBoard() {
        for (int i = 0; i < cardButtons.length; i++) {
            applyCardStyle(i);
        }
        updateHeader();
        refreshOverlay();
    }

    /**
     * 刷新一张牌。只有当这张牌在「背面 ↔ 正面」之间切换时才播放翻转动画；
     * 正面内部的切换（翻开 → 配对成功变色）只换配色，不会重复翻一遍。
     *
     * <p>本方法每帧都会被调用，所以必须先比对状态再决定是否播动画。</p>
     */
    private void applyCardStyle(int index) {
        int face = faceOf(index);
        int previous = cardFaces[index];
        if (previous == face) {
            return;
        }
        cardFaces[index] = face;

        boolean crossed = (previous == FACE_DOWN) != (face == FACE_DOWN);
        if (crossed) {
            animateFlip(index, face);
        } else {
            paintFace(index, face);
        }
    }

    private int faceOf(int index) {
        if (game.isMatched(index)) {
            return FACE_MATCHED;
        }
        return game.isFaceUp(index) ? FACE_UP : FACE_DOWN;
    }

    /** 牌面翻转：先把 scaleX 收到 0，中途换面，再展开回 1，看起来就像绕竖轴翻过来。 */
    private void animateFlip(int index, int face) {
        Button card = cardButtons[index];
        stopFlip(index);

        ScaleTransition shrink = new ScaleTransition(Duration.seconds(FLIP_HALF_SECONDS), card);
        shrink.setFromX(1);
        shrink.setToX(0);
        shrink.setInterpolator(Interpolator.EASE_IN);
        shrink.setOnFinished(e -> {
            paintFace(index, face);
            ScaleTransition grow = new ScaleTransition(Duration.seconds(FLIP_HALF_SECONDS), card);
            grow.setFromX(0);
            grow.setToX(1);
            grow.setInterpolator(Interpolator.EASE_OUT);
            cardFlips[index] = grow;
            grow.play();
        });
        cardFlips[index] = shrink;
        shrink.play();
    }

    /** 打断这张牌上正在跑的翻转，并把缩放复位（连点、提示开关都可能打断）。 */
    private void stopFlip(int index) {
        ScaleTransition running = cardFlips[index];
        if (running != null) {
            running.stop();
            cardFlips[index] = null;
        }
        cardButtons[index].setScaleX(1);
    }

    /** 直接换面，不带动画。 */
    private void paintFace(int index, int face) {
        Button card = cardButtons[index];
        switch (face) {
            case FACE_MATCHED -> {
                card.setText(game.symbolAt(index));
                card.setStyle(cardBaseStyle() + STYLE_MATCHED);
            }
            case FACE_UP -> {
                card.setText(game.symbolAt(index));
                card.setStyle(cardBaseStyle() + STYLE_UP);
            }
            default -> {
                card.setText(HIDDEN_FACE);
                card.setStyle(cardBaseStyle() + STYLE_DOWN);
            }
        }
    }

    private void updateHeader() {
        int seconds = (int) game.elapsedSeconds();
        if (seconds != shownSeconds) {
            shownSeconds = seconds;
            statLabel.setText("步数 " + game.moves() + "    用时 " + seconds + " 秒    "
                    + "配对 " + game.pairsFound() + " / " + game.totalPairs());
        }
        if (game.isTwoPlayer()) {
            // 只显示轮到的那一方，名称与分值之间用冒号；颜色随玩家切换
            int current = game.currentPlayer();
            playerLabel.setStyle("-fx-font-size: " + (int) STAT_FONT_SIZE + "px; -fx-font-weight: bold;"
                    + " -fx-text-fill: " + (current == 0 ? PLAYER_ONE_COLOR : PLAYER_TWO_COLOR) + ";");
            playerLabel.setText("▶ 玩家" + (current + 1) + "：" + game.pairsOf(current) + " 对");
        } else {
            playerLabel.setStyle(STYLE_STAT);
            playerLabel.setText("单人计时");
        }
        hintLabel.setText(game.peekLeft() > 0 ? "提示剩余 " + game.peekLeft() + " 次" : "提示已用完");
    }

    private void refreshOverlay() {
        if (!game.isOver() || inMenu) {
            hideOverlay();
            return;
        }
        if (game.isTwoPlayer()) {
            int winner = game.winner();
            overlayTitle.setText(winner < 0 ? "平局！" : "玩家" + (winner + 1) + " 获胜！");
            overlayDesc.setText("玩家1 " + game.pairsOf(0) + " 对 · 玩家2 " + game.pairsOf(1) + " 对 · 共 "
                    + game.moves() + " 步 · " + (int) game.elapsedSeconds() + " 秒");
        } else {
            overlayTitle.setText("🎉 全部配对完成！");
            overlayDesc.setText("共 " + game.moves() + " 步 · 用时 " + (int) game.elapsedSeconds() + " 秒");
        }
        if (!overlay.isVisible()) {
            overlay.setVisible(true);
            overlay.setManaged(true);
            overlay.toFront();
        }
    }

    private void hideOverlay() {
        if (overlay != null && overlay.isVisible()) {
            overlay.setVisible(false);
            overlay.setManaged(false);
        }
    }

    // =====================================================================
    // 循环与操作
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
                    if (inRules) {
                        tickRules(STEP_SECONDS);
                    } else if (!inMenu) {
                        game.tick(STEP_SECONDS);
                    }
                    tickFlash(STEP_SECONDS);
                    accumulator -= STEP_SECONDS;
                }
                refreshBoard();
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

    private void bindKeys() {
        root.setOnKeyPressed(event -> {
            if (inRules) {
                if (event.getCode() == KeyCode.ESCAPE) {
                    showMenu();
                } else if (event.getCode() == KeyCode.SPACE || event.getCode() == KeyCode.ENTER) {
                    beginPlay();
                }
                event.consume();
                return;
            }
            if (inMenu) {
                event.consume();
                return;
            }
            switch (event.getCode()) {
                case H -> usePeek();
                case R -> restart();
                case ESCAPE -> showMenu();
                default -> { }
            }
            event.consume();
        });
    }

    private void usePeek() {
        if (inMenu || inRules) {
            return;
        }
        if (game.usePeek()) {
            refreshBoard();
            return;
        }
        // 之前这里是静默失败，玩家会以为 H 键坏了 —— 明确告诉他为什么没反应
        if (game.peekLeft() <= 0) {
            showFlash("提示次数已用完（每局限 " + config.getPeekUses() + " 次）");
        } else if (game.isComparing()) {
            showFlash("等两张牌盖回去之后再按 H");
        }
    }
}
