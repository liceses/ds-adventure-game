package com.studio.plugin.demo.breakout;

import com.studio.plugin.GamePlugin;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 打砖块小游戏插件（内置形态，事件ID <code>breakout</code>）。
 *
 * <p>玩法规格见 <code>docs/ds-adventrue/打砖块.md</code>：底部挡板左右移动接球，
 * 球向上弹打掉砖块，砖块有不同颜色与不同血量，掉球扣一条命，打完全部砖块过关。</p>
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li>规则计算全部委托给 {@link BreakoutGame}，本类只负责界面与输入，规则可脱离 JavaFX 单测；</li>
 *   <li>{@link #createEmbeddedView} 返回本游戏界面，读取器会嵌入主舞台中央并自动加"返回"标题栏；</li>
 *   <li>{@link #onDetach()} 幂等停止游戏循环，避免离开小游戏后 AnimationTimer 仍在跑。</li>
 * </ul>
 */
public class BreakoutPlugin implements GamePlugin {

    // ---- 布局与配色 ----
    private static final double HEADER_SPACING = 14;
    private static final double HEADER_PADDING = 10;
    private static final double HUD_FONT_SIZE = 14;
    private static final double OVERLAY_TITLE_FONT_SIZE = 30;
    private static final double OVERLAY_BODY_FONT_SIZE = 15;
    private static final double FIELD_FONT_SIZE = 34;
    private static final double BRICK_ARC = 6;
    private static final double BRICK_INSET = 3;
    private static final double BRICK_SHINE_HEIGHT = 5;
    private static final double PADDLE_ARC = 8;
    private static final double PADDLE_INSET = 4;
    private static final double PADDLE_SHINE_HEIGHT = 4;
    private static final double GLOW_SCALE = 2;
    private static final double HP_PIP_RADIUS = 2;
    private static final double HP_PIP_GAP = 6;
    private static final double HP_PIP_BOTTOM = 5;
    /** 砖块受损后向该颜色靠拢，用来表现"越打越暗"。 */
    private static final Color WORN_COLOR = Color.web("#1b1e29");
    private static final double WORN_STRENGTH = 0.55;
    private static final Color HEADER_BG = Color.web("#1a1d27");
    private static final Color FIELD_BG = Color.web("#14161e");
    private static final Color HUD_TEXT = Color.web("#e8eef7");
    private static final Color HUD_SUBTEXT = Color.web("#8a92a6");
    private static final Color PADDLE_FILL = Color.web("#2b6eb8");
    private static final Color PADDLE_SHINE = Color.web("#8ae4ff");
    private static final Color BALL_FILL = Color.web("#fff4c8");
    private static final Color BALL_GLOW = Color.web("#fff0b0", 0.16);
    private static final Color BRICK_SHINE = Color.web("#ffffff", 0.28);
    private static final Color VEIL = Color.web("#000000", 0.6);
    private static final Color TITLE_TEXT = Color.web("#8ae4ff");
    private static final Color WIN_TEXT = Color.web("#7ae08a");
    private static final Color LOSE_TEXT = Color.web("#e06a6a");
    private static final Color BODY_TEXT = Color.web("#d0d4e0");
    private static final Color HINT_TEXT = Color.web("#ffd54f");

    /** 砖块按行取色，行数超过数组长度时循环使用。 */
    private static final Color[] ROW_COLORS = {
        Color.web("#e05a5a"), Color.web("#e89a3c"), Color.web("#e8d04a"), Color.web("#5cc86a"),
        Color.web("#4cc2ff"), Color.web("#9a7ae8"), Color.web("#e06ac0"), Color.web("#5ad0c8"),
    };

    /** 固定逻辑步长：每帧按 1/60 秒推进，与显示器刷新率解耦。 */
    private static final double STEP_SECONDS = 1.0 / 60;
    /** 单帧最多补多少时间，防止窗口被挂起后一次补上千帧。 */
    private static final double MAX_FRAME_SECONDS = 0.25;

    private final BreakoutConfig config = new BreakoutConfig();
    private final BreakoutGame game = new BreakoutGame();
    private final Set<KeyCode> pressedKeys = new HashSet<>();

    private Canvas canvas;
    private BorderPane root;
    private Label scoreLabel;
    private Label infoLabel;
    private Label livesLabel;
    private StackPane holder;
    private VBox overlay;
    private Label overlayTitle;
    private Label overlayDesc;

    private AnimationTimer loop;
    private boolean paused;
    private double accumulator;
    private long lastNanos;

    private int lastScore = -1;
    private int lastLevel = -1;
    private int lastBricks = -1;
    private int lastLives = -1;

    @Override
    public void execute(Stage stage, Map<String, Object> params) {
        boolean embedded = params != null && params.get(PARAM_EMBEDDED) instanceof Boolean b && b;
        if (embedded) {
            return; // 嵌入模式下由读取器托管 createEmbeddedView 返回的界面
        }
        Stage window = new Stage();
        window.setTitle("打砖块（插件窗口模式）");
        window.setScene(new Scene(buildView()));
        window.setOnHidden(e -> stopLoop());
        window.show();
        Platform.runLater(() -> root.requestFocus());
    }

    @Override
    public Parent createEmbeddedView(Map<String, Object> params) {
        return buildView();
    }

    @Override
    public String displayName() {
        return "打砖块小游戏";
    }

    @Override
    public void onDetach() {
        stopLoop(); // ★ 引擎在插件被移除时回调，必须停掉循环
    }

    // =====================================================================
    // 界面搭建
    // =====================================================================

    private Parent buildView() {
        game.reset(config);
        paused = false;

        canvas = new Canvas(config.getViewWidth(), config.getViewHeight());

        holder = new StackPane(canvas);
        overlay = buildOverlay();
        overlay.setVisible(false);
        overlay.setManaged(false);
        holder.getChildren().add(overlay);

        root = new BorderPane();
        root.getStyleClass().add("breakout-root");
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

    private HBox buildHeader() {
        scoreLabel = new Label();
        scoreLabel.setStyle("-fx-text-fill: " + toHex(HUD_TEXT) + "; -fx-font-size: 17px; -fx-font-weight: bold;");

        infoLabel = new Label();
        infoLabel.setStyle("-fx-text-fill: " + toHex(HUD_SUBTEXT) + "; -fx-font-size: " + (int) HUD_FONT_SIZE + "px;");

        livesLabel = new Label();
        livesLabel.setStyle("-fx-text-fill: " + toHex(BALL_FILL) + "; -fx-font-size: " + (int) HUD_FONT_SIZE + "px;");

        Button pause = new Button("暂停/继续");
        pause.setOnAction(e -> togglePause());

        Button restart = new Button("↻ 重新开局");
        restart.setOnAction(e -> restart());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(HEADER_SPACING, scoreLabel, infoLabel, spacer, livesLabel, pause, restart);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(HEADER_PADDING));
        header.setStyle("-fx-background-color: " + toHex(HEADER_BG) + ";");
        updateHud(true);
        return header;
    }

    private VBox buildOverlay() {
        overlayTitle = new Label();
        overlayTitle.setTextFill(TITLE_TEXT);
        overlayTitle.setFont(Font.font(null, FontWeight.BOLD, OVERLAY_TITLE_FONT_SIZE));

        overlayDesc = new Label();
        overlayDesc.setTextFill(BODY_TEXT);
        overlayDesc.setFont(Font.font(OVERLAY_BODY_FONT_SIZE));

        Button again = new Button("再来一局");
        again.setOnAction(e -> restart());

        VBox box = new VBox(12, overlayTitle, overlayDesc, again);
        box.setAlignment(Pos.CENTER);
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        box.setPadding(new Insets(24));
        box.setStyle("-fx-background-color: rgba(16,19,34,0.92); -fx-background-radius: 12;");
        return box;
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
                if (game.isOver()) {
                    restart();
                } else {
                    game.launch();
                }
            }
            event.consume(); // 别让空格等按键冒泡到宿主场景
        });
        root.setOnKeyReleased(event -> {
            pressedKeys.remove(event.getCode());
            event.consume();
        });

        canvas.setOnMouseMoved(this::movePaddleToMouse);
        canvas.setOnMouseDragged(this::movePaddleToMouse);
    }

    private void movePaddleToMouse(MouseEvent event) {
        game.movePaddleTo(event.getX());
    }

    private void togglePause() {
        if (game.isOver()) {
            return;
        }
        paused = !paused;
        if (!paused) {
            lastNanos = System.nanoTime(); // 避免恢复后一次性补上暂停期间的时间
            accumulator = 0;
        }
        render();
    }

    private void restart() {
        game.reset(config);
        paused = false;
        accumulator = 0;
        lastNanos = System.nanoTime();
        hideOverlay();
        updateHud(true);
        render();
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
                    if (!paused && !game.isOver()) {
                        game.movePaddle(paddleDirection());
                        game.tick(STEP_SECONDS);
                    }
                    accumulator -= STEP_SECONDS;
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

    private double paddleDirection() {
        double dx = 0;
        if (pressedKeys.contains(KeyCode.LEFT) || pressedKeys.contains(KeyCode.A)) {
            dx -= 1;
        }
        if (pressedKeys.contains(KeyCode.RIGHT) || pressedKeys.contains(KeyCode.D)) {
            dx += 1;
        }
        return dx;
    }

    // =====================================================================
    // 绘制
    // =====================================================================

    private void updateHud(boolean force) {
        if (game.score() != lastScore || force) {
            lastScore = game.score();
            scoreLabel.setText("得分 " + lastScore);
        }
        if (game.level() != lastLevel || game.bricksLeft() != lastBricks || force) {
            lastLevel = game.level();
            lastBricks = game.bricksLeft();
            infoLabel.setText("第 " + lastLevel + " / " + config.getTotalLevels() + " 关 · 剩余砖块 " + lastBricks);
        }
        if (game.lives() != lastLives || force) {
            lastLives = game.lives();
            livesLabel.setText("❤".repeat(Math.max(0, lastLives)));
        }
    }

    private void render() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        drawField(g);
        drawBricks(g);
        drawPaddle(g);
        drawBall(g);
        drawCenterHint(g);
        refreshOverlay();
    }

    private void drawField(GraphicsContext g) {
        g.setFill(FIELD_BG);
        g.fillRect(0, 0, config.getViewWidth(), config.getViewHeight());
    }

    private void drawBricks(GraphicsContext g) {
        for (int row = 0; row < game.brickRows(); row++) {
            int maxHp = game.brickMaxHp(row);
            Color base = ROW_COLORS[row % ROW_COLORS.length];
            for (int col = 0; col < config.getBrickColumns(); col++) {
                int hp = game.brickHp(row, col);
                if (hp <= 0) {
                    continue;
                }
                drawBrick(g, row, col, hp, maxHp, base);
            }
        }
    }

    private void drawBrick(GraphicsContext g, int row, int col, int hp, int maxHp, Color base) {
        double x = config.getBrickLeftX(col);
        double y = config.getBrickTopY(row);
        double width = config.getBrickWidth();
        double height = config.getBrickHeight();

        // 受损越多越暗，颜色深浅直观反映剩余血量
        double wear = 1 - (double) hp / maxHp;
        g.setFill(base.interpolate(WORN_COLOR, wear * WORN_STRENGTH));
        g.fillRoundRect(x, y, width, height, BRICK_ARC, BRICK_ARC);

        g.setFill(BRICK_SHINE);
        g.fillRoundRect(x + BRICK_INSET, y + BRICK_INSET,
                width - BRICK_INSET * 2, BRICK_SHINE_HEIGHT, BRICK_ARC, BRICK_ARC);

        drawHpPips(g, x, y + height, width, hp);
    }

    /** 在砖块底部画剩余血量圆点，最多显示 3 个。 */
    private void drawHpPips(GraphicsContext g, double brickLeft, double brickBottom, double brickWidth, int hp) {
        if (hp <= 1) {
            return;
        }
        double totalWidth = (hp - 1) * HP_PIP_GAP;
        double startX = brickLeft + brickWidth / 2.0 - totalWidth / 2.0;
        g.setFill(Color.web("#ffffff", 0.75));
        for (int i = 0; i < hp; i++) {
            double cx = startX + i * HP_PIP_GAP;
            g.fillOval(cx - HP_PIP_RADIUS, brickBottom - HP_PIP_BOTTOM - HP_PIP_RADIUS,
                    HP_PIP_RADIUS * 2, HP_PIP_RADIUS * 2);
        }
    }

    private void drawPaddle(GraphicsContext g) {
        double x = game.paddleX();
        double y = config.getPaddleY();
        double width = config.getPaddleWidth();
        double height = config.getPaddleHeight();

        g.setFill(PADDLE_FILL);
        g.fillRoundRect(x, y, width, height, PADDLE_ARC, PADDLE_ARC);
        g.setFill(PADDLE_SHINE);
        g.fillRoundRect(x + PADDLE_INSET, y + PADDLE_INSET,
                width - PADDLE_INSET * 2, PADDLE_SHINE_HEIGHT, PADDLE_ARC, PADDLE_ARC);
    }

    private void drawBall(GraphicsContext g) {
        double radius = config.getBallRadius();
        double cx = game.ballX();
        double cy = game.ballY();
        double glow = radius * GLOW_SCALE;

        g.setFill(BALL_GLOW);
        g.fillOval(cx - glow, cy - glow, glow * 2, glow * 2);
        g.setFill(BALL_FILL);
        g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
    }

    private void drawCenterHint(GraphicsContext g) {
        if (game.isOver()) {
            return;
        }
        String text;
        if (paused) {
            text = "已暂停 · 按 P 继续";
        } else if (game.isBallWaiting()) {
            text = "按 空格 / J 发射";
        } else {
            return;
        }
        g.setFill(Color.web("#ffffff", 0.82));
        g.setFont(Font.font(null, FontWeight.BOLD, FIELD_FONT_SIZE));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText(text, config.getViewWidth() / 2.0, config.getViewHeight() / 2.0);
        g.setTextAlign(TextAlignment.LEFT);
    }

    private void refreshOverlay() {
        if (!game.isOver()) {
            hideOverlay();
            return;
        }
        if (game.isWin()) {
            overlayTitle.setText("🎉 全部关卡通过！");
            overlayTitle.setTextFill(WIN_TEXT);
            overlayDesc.setText("最终得分 " + game.score() + " · 点【再来一局】继续，或点上方【返回剧情】。");
        } else {
            overlayTitle.setText("💥 球掉光了，本局结束");
            overlayTitle.setTextFill(LOSE_TEXT);
            overlayDesc.setText("本局得分 " + game.score() + " · 点【再来一局】重试，或点上方【返回剧情】。");
        }
        if (!overlay.isVisible()) {
            overlay.setVisible(true);
            overlay.setManaged(true);
        }
    }

    private void hideOverlay() {
        if (overlay != null && overlay.isVisible()) {
            overlay.setVisible(false);
            overlay.setManaged(false);
        }
    }

    /** 把 Color 转成 CSS 用的 #rrggbb 字面量，避免各处重复写死颜色字符串。 */
    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }
}
