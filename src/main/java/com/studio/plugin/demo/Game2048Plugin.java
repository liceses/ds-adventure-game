package com.studio.plugin.demo;

import com.studio.plugin.GamePlugin;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 示例插件：2048 小游戏。
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li>4×4 棋盘，方向键 / WASD / 屏幕按钮 均可移动；相同数字合并翻倍并计分；</li>
 *   <li>达到 2048 出现“胜利”提示（可继续挑战），无可合并时判定失败；</li>
 *   <li>实现 {@link GamePlugin#createEmbeddedView}，由读取器嵌入主舞台，
 *       顶部自带【返回剧情】标题栏 —— 与扫雷插件相同的“浏览器标签页”式跳转。</li>
 * </ul>
 */
public class Game2048Plugin implements GamePlugin {

    private static final int N = 4;

    // ---- 游戏状态 ----
    private final int[][] cells = new int[N][N];
    private long score = 0;
    private boolean gameOver = false;
    private boolean winShown = false;
    private boolean continueAfterWin = false;
    private final Random random = new Random();

    // ---- 视图 ----
    private GridPane board;
    private Label scoreLabel;
    private Label statusLabel;
    private StackPane overlay;
    private Region root;

    @Override
    public void execute(Stage stage, Map<String, Object> params) {
        boolean embedded = params.get(PARAM_EMBEDDED) instanceof Boolean b && b;
        if (embedded) return;
        Stage win = new Stage();
        win.setTitle("2048（插件窗口模式）");
        win.setScene(new Scene(buildRoot(), 430, 560));
        win.show();
    }

    @Override
    public Parent createEmbeddedView(Map<String, Object> params) {
        return buildRoot();
    }

    @Override
    public String displayName() {
        return "2048 小游戏";
    }

    @Override
    public void onDetach() {
        // 动画/计时均已随视图清理；无后台线程
    }

    // =====================================================================
    // 构建界面
    // =====================================================================

    private Parent buildRoot() {
        // ---- 顶栏 ----
        Label title = new Label("🀄 2048");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #f4d06f;");
        scoreLabel = new Label("分数 0");
        scoreLabel.setStyle("-fx-text-fill: #e8e8f0; -fx-font-size: 14px;");
        statusLabel = new Label("方向键/滑动 移动数字");
        statusLabel.setStyle("-fx-text-fill: #9aa0c8; -fx-font-size: 12px;");

        Button restart = new Button("↻ 重新开始");
        restart.getStyleClass().add("mine-restart");
        restart.setOnAction(e -> restartGame());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, title, scoreLabel, spacer, restart);
        header.setAlignment(Pos.CENTER_LEFT);

        // ---- 棋盘 ----
        board = new GridPane();
        board.setHgap(6);
        board.setVgap(6);
        board.setPadding(new Insets(8));

        overlay = new StackPane();
        overlay.setVisible(false);
        overlay.setManaged(false);

        StackPane area = new StackPane(board, overlay);
        area.setStyle("-fx-background-color: rgba(255,255,255,0.06); -fx-background-radius: 14;"
                + "-fx-padding: 8;");

        // ---- 方向按钮（鼠标可用）----
        VBox ctrl = new VBox(4);
        ctrl.setAlignment(Pos.CENTER);
        ctrl.getChildren().addAll(
                dirRow("⬆", () -> move(Direction.UP)),
                new HBox(4, dirButton("⬅", () -> move(Direction.LEFT)),
                        dirButton("⬇", () -> move(Direction.DOWN)),
                        dirButton("➡", () -> move(Direction.RIGHT))));

        VBox body = new VBox(10, header, area, ctrl, statusLabel);
        body.setPadding(new Insets(14));

        BorderPane rootPane = new BorderPane(body);
        rootPane.getStyleClass().add("mine-root");
        rootPane.setStyle("-fx-background-color: #101322;");

        // 键盘输入（方向键 + WASD）
        rootPane.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            Direction d = switch (e.getCode()) {
                case LEFT, A -> Direction.LEFT;
                case RIGHT, D -> Direction.RIGHT;
                case UP, W -> Direction.UP;
                case DOWN, S -> Direction.DOWN;
                default -> null;
            };
            if (d != null) {
                e.consume();
                move(d);
            } else if (e.getCode() == KeyCode.R) {
                restartGame();
            }
        });

        root = rootPane;
        restartGame();

        // 焦点给到插件，保证方向键可用
        rootPane.setFocusTraversable(true);
        PauseTransition focus = new PauseTransition(Duration.millis(200));
        focus.setOnFinished(e -> rootPane.requestFocus());
        focus.play();
        return rootPane;
    }

    private Button dirButton(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("mine-cell");
        b.setPrefSize(44, 40);
        b.setMinSize(44, 40);
        b.setOnAction(e -> action.run());
        return b;
    }

    private HBox dirRow(String text, Runnable action) {
        Button b = dirButton(text, action);
        b.setPrefSize(140, 40);
        b.setMinSize(140, 40);
        HBox row = new HBox(b);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    // =====================================================================
    // 游戏逻辑
    // =====================================================================

    private enum Direction { LEFT, RIGHT, UP, DOWN }

    private void restartGame() {
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) cells[r][c] = 0;
        score = 0;
        gameOver = false;
        winShown = false;
        continueAfterWin = false;
        spawnRandom();
        spawnRandom();
        hideOverlay();
        statusLabel.setText("方向键 / 屏幕按钮 移动数字，相同数字合体翻倍");
        render();
    }

    private void move(Direction d) {
        if (gameOver || (winShown && !continueAfterWin)) return;
        boolean changed = false;
        switch (d) {
            case LEFT -> changed = moveAll((r, k) -> cells[r][k], (r, k, v) -> cells[r][k] = v);
            case RIGHT -> changed = moveAll((r, k) -> cells[r][N - 1 - k], (r, k, v) -> cells[r][N - 1 - k] = v);
            case UP -> changed = moveAll((r, k) -> cells[k][r], (r, k, v) -> cells[k][r] = v);
            case DOWN -> changed = moveAll((r, k) -> cells[N - 1 - k][r], (r, k, v) -> cells[N - 1 - k][r] = v);
        }
        if (!changed) return;
        spawnRandom();
        render();
        checkState();
    }

    /** 对 4 条“线”执行：读 k=0..3 → 左向合并 → 写回 */
    private interface LineReader { int get(int line, int k); }
    private interface LineWriter { void set(int line, int k, int value); }

    private boolean moveAll(LineReader reader, LineWriter writer) {
        boolean changed = false;
        for (int line = 0; line < N; line++) {
            int[] src = new int[N];
            for (int k = 0; k < N; k++) src[k] = reader.get(line, k);
            int[] dst = mergeLeft(src);
            changed |= !java.util.Arrays.equals(src, dst);
            for (int k = 0; k < N; k++) writer.set(line, k, dst[k]);
        }
        return changed;
    }

    /** 左向合并一行（返回新行，非零值靠左） */
    private int[] mergeLeft(int[] src) {
        int[] out = new int[N];
        boolean[] merged = new boolean[N]; // 每个槽位本回合是否已合并（防止 2,2,4→8 的错误）
        int wi = 0;
        for (int i = 0; i < N; i++) {
            int v = src[i];
            if (v == 0) continue;
            if (wi > 0 && out[wi - 1] == v && !merged[wi - 1]) {
                out[wi - 1] = v * 2;
                merged[wi - 1] = true;
                score += (long) v * 2;
            } else {
                out[wi] = v;
                merged[wi] = false;
                wi++;
            }
        }
        return out;
    }

    private void spawnRandom() {
        List<int[]> empty = new ArrayList<>();
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                if (cells[r][c] == 0) empty.add(new int[]{r, c});
            }
        }
        if (empty.isEmpty()) return;
        int[] p = empty.get(random.nextInt(empty.size()));
        cells[p[0]][p[1]] = random.nextDouble() < 0.9 ? 2 : 4;
    }

    private boolean canMoveAny() {
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                if (cells[r][c] == 0) return true;
                if (c + 1 < N && cells[r][c] == cells[r][c + 1]) return true;
                if (r + 1 < N && cells[r][c] == cells[r + 1][c]) return true;
            }
        }
        return false;
    }

    private void checkState() {
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                if (cells[r][c] >= 2048 && !winShown) {
                    winShown = true;
                    showOverlay("🎉 合出 2048！",
                            "已经超越目标，可以【继续挑战】或【返回剧情】。",
                            () -> {
                                continueAfterWin = true;
                                hideOverlay();
                                statusLabel.setText("继续合成更大的数字！");
                            });
                    return;
                }
            }
        }
        if (!canMoveAny()) {
            gameOver = true;
            showOverlay("😵 无法再移动…",
                    "本局得分 " + score + "，点击【再来一局】，或点上方【返回】退出小游戏。",
                    this::restartGame);
        }
    }

    // =====================================================================
    // 渲染
    // =====================================================================

    private void render() {
        board.getChildren().clear();
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                int v = cells[r][c];
                Label cell = new Label(v == 0 ? "" : String.valueOf(v));
                cell.setMinSize(86, 86);
                cell.setPrefSize(86, 86);
                cell.setMaxSize(86, 86);
                cell.setAlignment(Pos.CENTER);
                cell.setStyle(tileStyle(v));
                board.add(cell, c, r);
            }
        }
        scoreLabel.setText("分数 " + score);
    }

    private static String tileStyle(int v) {
        return switch (v) {
            case 0 -> "-fx-background-color: rgba(255,255,255,0.08); -fx-background-radius: 10;";
            case 2 -> style("#eee4da", "#776e65");
            case 4 -> style("#ede0c8", "#776e65");
            case 8 -> style("#f2b179", "#f9f6f2");
            case 16 -> style("#f59563", "#f9f6f2");
            case 32 -> style("#f67c5f", "#f9f6f2");
            case 64 -> style("#f65e3b", "#f9f6f2");
            case 128 -> style("#edcf72", "#f9f6f2");
            case 256 -> style("#edcc61", "#f9f6f2");
            case 512 -> style("#edc850", "#f9f6f2");
            case 1024 -> style("#edc53f", "#f9f6f2");
            case 2048 -> style("#edc22e", "#f9f6f2");
            default -> style("#3c3a32", "#f9f6f2");
        };
    }

    private static String style(String bg, String fg) {
        return "-fx-background-color: " + bg + "; -fx-background-radius: 10;"
                + "-fx-text-fill: " + fg + "; -fx-font-weight: bold; -fx-font-size: 24px;";
    }

    private void showOverlay(String title, String desc, Runnable primaryAction) {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("mine-overlay");
        Label t = new Label(title);
        t.getStyleClass().add("mine-title");
        Label d = new Label(desc);
        d.setWrapText(true);
        d.setStyle("-fx-text-fill: #cdd0e8;");
        Button again = new Button("再来一局");
        again.getStyleClass().add("mine-restart");
        again.setOnAction(e -> primaryAction.run());
        box.getChildren().addAll(t, d, again);
        overlay.getChildren().clear();
        overlay.getChildren().add(box);
        overlay.setVisible(true);
        overlay.setManaged(true);
        statusLabel.setText(desc);
    }

    private void hideOverlay() {
        overlay.getChildren().clear();
        overlay.setVisible(false);
        overlay.setManaged(false);
    }
}
