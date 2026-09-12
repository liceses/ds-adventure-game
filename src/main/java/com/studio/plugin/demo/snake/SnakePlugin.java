package com.studio.plugin.demo.snake;

import com.studio.plugin.GamePlugin;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.List;
import java.util.Map;

/**
 * 贪吃蛇小游戏插件（事件 ID：{@code snake}），嵌入模式接入。
 *
 * <p>规则全部委托给纯 Java 的 {@link SnakeGame}；本类只负责渲染、输入与生命周期：
 * </p>
 * <ul>
 *   <li>{@link #createEmbeddedView(Map)} 返回棋盘界面，由读取器嵌入主舞台中央；</li>
 *   <li>{@link #execute(Stage, Map)} 在嵌入模式下不创建任何窗口；</li>
 *   <li>{@link #onDetach()} 幂等停止 {@link AnimationTimer}，另有
 *       {@code root.parentProperty()} 兜底监听（视图被摘除时同样停循环）。</li>
 * </ul>
 *
 * <p>参数取值见需求 F4：10×10 格、初始长度 3、累计 97 豆通关、
 * 速度 = 初速 + 0.01 × 已玩秒数，均集中在 {@link SnakeConfig}，可调。</p>
 */
public class SnakePlugin implements GamePlugin {

    /** 每格像素 */
    private static final int CELL = 48;
    private static final Color C_BG = Color.web("#0d1020");
    private static final Color C_GRID = Color.web("#1b2140");
    private static final Color C_CELL_A = Color.web("#141a33");
    private static final Color C_CELL_B = Color.web("#171e3b");
    private static final Color C_SNAKE_HEAD = Color.web("#7ee787");
    private static final Color C_SNAKE_BODY = Color.web("#3fb950");
    private static final Color C_SNAKE_EDGE = Color.web("#1f6f2e");
    private static final Color C_FOOD = Color.web("#ff6b6b");
    private static final Color C_OBSTACLE = Color.web("#4a5070");
    private static final String C_TEXT = "#e8eef7";
    private static final String C_DIM = "#9aa0c8";

    private final SnakeConfig config = SnakeConfig.defaults();

    private SnakeGame game;
    private Canvas canvas;
    private BorderPane root;
    private StackPane boardStack;
    private Region overlayVeil;
    private VBox overlayBox;
    private Label overlayTitle;
    private Label overlayDetail;
    private Label scoreLabel;
    private Label progressLabel;
    private Label speedLabel;
    private Label hintLabel;

    private AnimationTimer loop;
    private long lastNanos;
    private double countdown;
    private Runnable backCallback;

    // ------------------------------------------------------------------
    // GamePlugin
    // ------------------------------------------------------------------

    @Override
    public void execute(Stage stage, Map<String, Object> params) {
        Object back = params == null ? null : params.get(PARAM_BACK_CALLBACK);
        this.backCallback = (back instanceof Runnable) ? (Runnable) back : null;
        // 嵌入模式：不创建任何窗口，界面由 createEmbeddedView 提供
    }

    @Override
    public Parent createEmbeddedView(Map<String, Object> params) {
        game = new SnakeGame(config);
        countdown = config.getCountdownSeconds();
        buildUi();
        startLoop();
        Platform.runLater(() -> {
            if (root != null) {
                root.requestFocus();
            }
        });
        return root;
    }

    @Override
    public String displayName() {
        return "贪吃蛇";
    }

    @Override
    public void onDetach() {
        stopLoop();
    }

    // ------------------------------------------------------------------
    // 界面
    // ------------------------------------------------------------------

    private void buildUi() {
        double boardW = config.getCols() * (double) CELL;
        double boardH = config.getRows() * (double) CELL;

        canvas = new Canvas(boardW, boardH);

        scoreLabel = hudLabel("分数 0");
        progressLabel = hudLabel("进度 0 / " + config.getWinBeans());
        speedLabel = hudLabel(String.format("速度 %.2f 格/秒", config.getInitialSpeed()));

        HBox hud = new HBox(18, scoreLabel, progressLabel, speedLabel);
        hud.setAlignment(Pos.CENTER_LEFT);
        hud.setPadding(new Insets(8, 12, 8, 12));
        hud.setStyle("-fx-background-color: #12162b;");

        overlayTitle = new Label();
        overlayTitle.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 30));
        overlayTitle.setStyle("-fx-text-fill: #ffffff;");
        overlayDetail = new Label();
        overlayDetail.setStyle("-fx-text-fill: " + C_DIM + "; -fx-font-size: 13px;");
        overlayDetail.setWrapText(true);
        Button restart = new Button("重新开始 (R)");
        restart.setStyle("-fx-background-color: #2d6cdf; -fx-text-fill: white; -fx-font-size: 13px;");
        restart.setOnAction(e -> restartGame());
        Button overlayBack = new Button("返回剧情");
        overlayBack.setStyle("-fx-background-color: #3a3f5c; -fx-text-fill: white; -fx-font-size: 13px;");
        overlayBack.setOnAction(e -> requestBack());
        HBox overlayButtons = new HBox(10, restart, overlayBack);
        overlayButtons.setAlignment(Pos.CENTER);

        overlayBox = new VBox(12, overlayTitle, overlayDetail, overlayButtons);
        overlayBox.setAlignment(Pos.CENTER);
        overlayBox.setPadding(new Insets(18, 26, 18, 26));
        overlayBox.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        overlayBox.setStyle("-fx-background-color: rgba(10, 12, 26, 0.92);"
                + "-fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: #3a3f5c;");

        overlayVeil = new Region();
        overlayVeil.setStyle("-fx-background-color: rgba(0, 0, 0, 0.45);");
        overlayVeil.setVisible(false);

        StackPane overlays = new StackPane(overlayVeil, overlayBox);
        overlays.setPickOnBounds(false);

        boardStack = new StackPane(canvas, overlays);
        boardStack.setAlignment(Pos.CENTER);
        boardStack.setStyle("-fx-background-color: #0d1020;");

        hintLabel = new Label("方向键 / WASD 转向 · 允许 180° 反向（撞到自己即失败） · R 重开");
        hintLabel.setStyle("-fx-text-fill: " + C_DIM + "; -fx-font-size: 12px;");
        Button back = new Button("结束本局并返回剧情");
        back.setStyle("-fx-background-color: #3a3f5c; -fx-text-fill: white; -fx-font-size: 12px;");
        back.setOnAction(e -> requestBack());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottom = new HBox(12, hintLabel, spacer, back);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(8, 12, 8, 12));
        bottom.setStyle("-fx-background-color: #12162b;");

        root = new BorderPane();
        root.setTop(hud);
        root.setCenter(boardStack);
        root.setBottom(bottom);
        root.setFocusTraversable(true);
        root.setPrefSize(boardW + 24, boardH + 108);
        root.setStyle("-fx-background-color: #0d1020;");

        // 键盘：转向 / 重开
        root.setOnKeyPressed(event -> handleKey(event.getCode()));

        // 兜底：视图被摘除时也停一次循环（主路径是引擎回调 onDetach）
        root.parentProperty().addListener((obs, old, now) -> {
            if (now == null) {
                stopLoop();
            }
        });

        updateHud();
        render();
        updateOverlay();
    }

    private Label hudLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + C_TEXT + "; -fx-font-size: 13px;");
        return label;
    }

    private void handleKey(KeyCode code) {
        if (code == null) {
            return;
        }
        if (code == KeyCode.R) {
            restartGame();
            return;
        }
        if (game == null || game.isOver() || countdown > 0) {
            return;
        }
        switch (code) {
            case UP:
            case W:
                game.setDirection(SnakeGame.Direction.UP);
                break;
            case DOWN:
            case S:
                game.setDirection(SnakeGame.Direction.DOWN);
                break;
            case LEFT:
            case A:
                game.setDirection(SnakeGame.Direction.LEFT);
                break;
            case RIGHT:
            case D:
                game.setDirection(SnakeGame.Direction.RIGHT);
                break;
            default:
                break;
        }
    }

    private void restartGame() {
        game.reset();
        countdown = config.getCountdownSeconds();
        updateHud();
        render();
        updateOverlay();
    }

    private void requestBack() {
        stopLoop();
        if (backCallback != null) {
            backCallback.run();
        }
    }

    // ------------------------------------------------------------------
    // 循环
    // ------------------------------------------------------------------

    private void startLoop() {
        if (loop != null) {
            return;
        }
        lastNanos = 0;
        loop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastNanos == 0) {
                    lastNanos = now;
                    return;
                }
                double dt = (now - lastNanos) / 1_000_000_000.0;
                lastNanos = now;
                dt = Math.min(dt, 0.05);   // 窗口拖动后防止单帧 dt 爆炸
                tick(dt);
            }
        };
        loop.start();
    }

    /** 幂等停循环 */
    private void stopLoop() {
        if (loop != null) {
            loop.stop();
            loop = null;
        }
    }

    private void tick(double dt) {
        if (game == null) {
            return;
        }
        if (countdown > 0) {
            countdown -= dt;
            if (countdown < 0) {
                countdown = 0;
            }
            render();
            updateOverlay();
            return;
        }
        if (!game.isOver()) {
            game.step(dt);
        }
        render();
        updateHud();
        updateOverlay();
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

    private void render() {
        if (canvas == null || game == null) {
            return;
        }
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        double cell = CELL;

        g.setFill(C_BG);
        g.fillRect(0, 0, w, h);

        // 棋盘底色
        for (int y = 0; y < config.getRows(); y++) {
            for (int x = 0; x < config.getCols(); x++) {
                g.setFill(((x + y) % 2 == 0) ? C_CELL_A : C_CELL_B);
                g.fillRect(x * cell, y * cell, cell, cell);
            }
        }

        // 网格线
        g.setStroke(C_GRID);
        g.setLineWidth(1);
        for (int x = 0; x <= config.getCols(); x++) {
            g.strokeLine(x * cell + 0.5, 0, x * cell + 0.5, config.getRows() * cell);
        }
        for (int y = 0; y <= config.getRows(); y++) {
            g.strokeLine(0, y * cell + 0.5, config.getCols() * cell, y * cell + 0.5);
        }

        // 障碍
        g.setFill(C_OBSTACLE);
        for (int[] o : game.obstacles()) {
            g.fillRoundRect(o[0] * cell + 6, o[1] * cell + 6, cell - 12, cell - 12, 8, 8);
        }

        // 食物
        int[] food = game.getFood();
        double fx = food[0] * cell;
        double fy = food[1] * cell;
        g.setFill(C_FOOD);
        g.fillOval(fx + cell * 0.22, fy + cell * 0.24, cell * 0.56, cell * 0.56);
        g.setStroke(Color.web("#7bbf5a"));
        g.setLineWidth(3);
        g.strokeLine(fx + cell * 0.5, fy + cell * 0.24, fx + cell * 0.62, fy + cell * 0.1);

        // 蛇（尾巴先画，头最后画，保证层次）
        List<int[]> cells = game.snakeCells();
        for (int i = cells.size() - 1; i >= 0; i--) {
            int[] c = cells.get(i);
            boolean head = (i == 0);
            g.setFill(head ? C_SNAKE_HEAD : C_SNAKE_BODY);
            g.fillRoundRect(c[0] * cell + 3, c[1] * cell + 3, cell - 6, cell - 6, 12, 12);
            g.setStroke(C_SNAKE_EDGE);
            g.setLineWidth(1.5);
            g.strokeRoundRect(c[0] * cell + 3, c[1] * cell + 3, cell - 6, cell - 6, 12, 12);
            if (head) {
                drawEyes(g, c[0] * cell, c[1] * cell, cell);
            }
        }
    }

    private void drawEyes(GraphicsContext g, double px, double py, double cell) {
        SnakeGame.Direction d = game.getDirection();
        double cx = px + cell / 2.0;
        double cy = py + cell / 2.0;
        double off = cell * 0.18;
        double ex1;
        double ey1;
        double ex2;
        double ey2;
        if (d == SnakeGame.Direction.LEFT || d == SnakeGame.Direction.RIGHT) {
            double dx = (d == SnakeGame.Direction.LEFT) ? -off : off;
            ex1 = cx + dx;
            ey1 = cy - off;
            ex2 = cx + dx;
            ey2 = cy + off;
        } else {
            double dy = (d == SnakeGame.Direction.UP) ? -off : off;
            ex1 = cx - off;
            ey1 = cy + dy;
            ex2 = cx + off;
            ey2 = cy + dy;
        }
        double r = Math.max(2, cell * 0.09);
        g.setFill(Color.web("#0d1020"));
        g.fillOval(ex1 - r, ey1 - r, r * 2, r * 2);
        g.fillOval(ex2 - r, ey2 - r, r * 2, r * 2);
    }

    private void updateHud() {
        if (game == null) {
            return;
        }
        scoreLabel.setText("分数 " + game.getScore());
        progressLabel.setText("进度 " + game.getScore() + " / " + config.getWinBeans());
        speedLabel.setText(String.format("速度 %.2f 格/秒 · 长度 %d · %.0f 秒",
                game.speed(), game.getLength(), game.getElapsedSeconds()));
    }

    private void updateOverlay() {
        if (overlayVeil == null || game == null) {
            return;
        }
        if (countdown > 0) {
            overlayVeil.setVisible(true);
            overlayTitle.setText(String.valueOf((int) Math.ceil(countdown)));
            overlayDetail.setText("准备开始：方向键 / WASD 转向，吃到 " + config.getWinBeans() + " 颗通关");
            overlayBox.setVisible(true);
            return;
        }
        if (game.isWin()) {
            overlayVeil.setVisible(true);
            overlayTitle.setText("🎉 通关！");
            overlayDetail.setText("累计吃豆 " + game.getScore() + " 颗（目标 " + config.getWinBeans() + "）· 用时 "
                    + String.format("%.1f", game.getElapsedSeconds()) + " 秒");
            overlayBox.setVisible(true);
            return;
        }
        if (game.isLose()) {
            overlayVeil.setVisible(true);
            overlayTitle.setText("💀 失败");
            overlayDetail.setText("吃到 " + game.getScore() + " / " + config.getWinBeans()
                    + " 颗 · 撞墙或撞到自己。按 R 或点「重新开始」再来一局。");
            overlayBox.setVisible(true);
            return;
        }
        overlayVeil.setVisible(false);
        overlayBox.setVisible(false);
    }
}
