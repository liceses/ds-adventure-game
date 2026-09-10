package com.studio.plugin.demo;

import com.studio.plugin.GamePlugin;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Map;
import java.util.Random;

/**
 * 示例插件：扫雷小游戏（外部程序嵌入演示）。
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li>只实现 {@link GamePlugin}；{@link #createEmbeddedView} 返回本游戏的
 *       JavaFX Parent —— 读取器会把它嵌入主舞台中央并自动加“返回”标题栏；</li>
 *   <li>左键翻开格子，右键插旗/取消旗；数字代表周围雷数；</li>
 *   <li>全部安全格翻开即胜利（剩余插旗自动补全）；踩雷失败会展示全图雷位；</li>
 *   <li>与地图模型零耦合，可独立作为工程师插件编译进 plugins 目录。</li>
 * </ul>
 */
public class MinesweeperPlugin implements GamePlugin {

    // ---- 游戏配置 ----
    private static final int DEF_ROWS = 9, DEF_COLS = 9, DEF_MINES = 10;
    private int rows = DEF_ROWS, cols = DEF_COLS, mines = DEF_MINES;

    // ---- 棋盘状态 ----
    private int[][] grid;          // -1=雷, 0..8=周围雷数
    private boolean[][] revealed;
    private boolean[][] flagged;
    private boolean initialized;   // 首次点击后才布雷（保证第一步安全）
    private boolean over;
    private int safeToOpen;

    // ---- 视图 ----
    private Button[][] cells;
    private Label mineCountLabel;
    private Label statusLabel;
    private StackPane area;
    private StackPane overlay;
    private final Random random = new Random();

    @Override
    public void execute(Stage stage, Map<String, Object> params) {
        boolean embedded = params.get(PARAM_EMBEDDED) instanceof Boolean b && b;
        if (embedded) return; // 嵌入模式下，由读取器托管 createEmbeddedView 的 Parent
        // 窗口模式：自建一个独立窗口运行
        try {
            readDifficulty(params);
            Stage win = new Stage();
            win.setTitle("扫雷（插件窗口模式）");
            win.setScene(new Scene(buildRoot(), 480, 560));
            win.show();
        } catch (Exception e) {
            throw new IllegalStateException("插件启动失败", e);
        }
    }

    @Override
    public Parent createEmbeddedView(Map<String, Object> params) {
        readDifficulty(params);
        return buildRoot();
    }

    @Override
    public String displayName() { return "扫雷小游戏"; }

    // =====================================================================

    private void readDifficulty(Map<String, Object> params) {
        rows = intParam(params, "rows", DEF_ROWS);
        cols = intParam(params, "cols", DEF_COLS);
        mines = intParam(params, "mines", DEF_MINES);
        int maxMines = Math.max(1, rows * cols - 9);
        mines = Math.min(Math.max(1, mines), maxMines);
        safeToOpen = rows * cols - mines;
    }

    private static int intParam(Map<String, Object> params, String key, int def) {
        Object v = params == null ? null : params.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { /* 忽略 */ }
        }
        return def;
    }

    private Parent buildRoot() {
        // ---- 顶部信息条 ----
        Button restart = new Button("↻ 重新开局");
        restart.getStyleClass().add("mine-restart");
        restart.setOnAction(e -> restartGame());

        mineCountLabel = new Label("💣 剩余: " + mines);
        mineCountLabel.getStyleClass().add("mine-title");

        statusLabel = new Label("左键翻开 · 右键插旗");
        statusLabel.getStyleClass().add("mine-hint");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10, mineCountLabel, spacer, restart, statusLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        // ---- 棋盘区 ----
        area = new StackPane();
        area.setAlignment(Pos.CENTER);

        overlay = new StackPane();
        overlay.setVisible(false);
        overlay.setManaged(false);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("mine-root");
        root.setTop(header);
        BorderPane.setMargin(header, new Insets(0, 0, 12, 0));
        root.setCenter(area);
        root.setPrefWidth(cols * 40.0 + 28);

        buildBoard();
        overlay.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        area.getChildren().addAll(buildGridHolder(), overlay);
        return root;
    }

    /** 分配棋盘状态数组并复位（首次构建与“重新开局”共用） */
    private void buildBoard() {
        grid = new int[rows][cols];
        revealed = new boolean[rows][cols];
        flagged = new boolean[rows][cols];
        initialized = false;
        over = false;
    }

    private VBox buildGridHolder() {
        GridPane board = new GridPane();
        board.setHgap(3);
        board.setVgap(3);
        cells = new Button[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Button cell = new Button(" ");
                cell.getStyleClass().add("mine-cell");
                cell.setPrefSize(36, 36);
                cell.setMinSize(36, 36);
                cell.setMaxSize(36, 36);
                final int rr = r, cc = c;
                cell.setOnMousePressed(e -> {
                    if (over) return;
                    if (e.getButton() == MouseButton.SECONDARY) {
                        e.consume();
                        if (!revealed[rr][cc]) toggleFlag(rr, cc);
                    } else if (e.getButton() == MouseButton.PRIMARY && !flagged[rr][cc]) {
                        open(rr, cc);
                    }
                });
                cells[r][c] = cell;
                board.add(cell, c, r);
            }
        }
        VBox holder = new VBox(board);
        holder.setAlignment(Pos.CENTER);
        return holder;
    }

    // =====================================================================
    // 游戏逻辑
    // =====================================================================

    private void restartGame() {
        buildBoard();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Button b = cells[r][c];
                b.setText(" ");
                b.getStyleClass().removeAll("revealed", "flag", "mine-hit");
                b.setStyle(""); // 清除数字颜色，避免与下局混淆
            }
        }
        updateFlagLabel();
        overlay.setVisible(false);
        statusLabel.setText("左键翻开 · 右键插旗");
    }

    /** 首击后布雷（排除该格与其九宫格），再执行翻开 */
    private void ensureMines(int safeR, int safeC) {
        if (initialized) return;
        initialized = true;
        int placed = 0;
        while (placed < mines) {
            int r = random.nextInt(rows);
            int c = random.nextInt(cols);
            if (grid[r][c] == -1) continue;
            if (Math.abs(r - safeR) <= 1 && Math.abs(c - safeC) <= 1) continue;
            grid[r][c] = -1;
            placed++;
        }
        // 计算邻雷数
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (grid[r][c] == -1) continue;
                grid[r][c] = countAround(r, c);
            }
        }
    }

    private int countAround(int r, int c) {
        int n = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int rr = r + dr, cc = c + dc;
                if (rr >= 0 && rr < rows && cc >= 0 && cc < cols && grid[rr][cc] == -1) n++;
            }
        }
        return n;
    }

    private void open(int r, int c) {
        if (revealed[r][c] || flagged[r][c]) return;
        ensureMines(r, c);
        if (grid[r][c] == -1) {
            gameOver(false);
            return;
        }
        // 洪水式展开 0 区域
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        queue.add(new int[]{r, c});
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            int rr = p[0], cc = p[1];
            if (rr < 0 || rr >= rows || cc < 0 || cc >= cols) continue;
            if (revealed[rr][cc] || flagged[rr][cc]) continue;
            revealed[rr][cc] = true;
            safeToOpen--;
            renderCell(rr, cc);
            if (grid[rr][cc] == 0) {
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        queue.add(new int[]{rr + dr, cc + dc});
                    }
                }
            }
        }
        updateFlagLabel();
        if (safeToOpen <= 0) gameOver(true);
    }

    private void toggleFlag(int r, int c) {
        flagged[r][c] = !flagged[r][c];
        Button b = cells[r][c];
        if (flagged[r][c]) {
            b.setText("🚩");
            b.getStyleClass().add("flag");
        } else {
            b.setText(" ");
            b.getStyleClass().remove("flag");
        }
        updateFlagLabel();
    }

    private void updateFlagLabel() {
        int flags = 0;
        for (boolean[] row : flagged) for (boolean f : row) if (f) flags++;
        mineCountLabel.setText("💣 剩余: " + (mines - flags));
    }

    private void renderCell(int r, int c) {
        Button b = cells[r][c];
        b.getStyleClass().removeAll("flag", "mine-hit");
        b.getStyleClass().add("revealed");
        if (grid[r][c] == -1) {
            b.setText("💣");
            b.getStyleClass().add("mine-hit");
            return;
        }
        if (grid[r][c] == 0) {
            b.setText(" ");
        } else {
            b.setText(String.valueOf(grid[r][c]));
            b.setStyle("-fx-text-fill: " + numberColor(grid[r][c]) + ";");
        }
    }

    private String numberColor(int n) {
        return switch (n) {
            case 1 -> "#6cb6ff";
            case 2 -> "#7be08a";
            case 3 -> "#ff8b6c";
            case 4 -> "#b48cff";
            case 5 -> "#ffd76a";
            case 6 -> "#6cd6d6";
            case 7 -> "#ff9ecb";
            default -> "#ffffff";
        };
    }

    private void gameOver(boolean win) {
        over = true;
        if (win) {
            // 胜利：未插旗处自动补旗，展示完美棋盘
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (grid[r][c] == -1 && !flagged[r][c]) {
                        flagged[r][c] = true;
                        Button b = cells[r][c];
                        b.setText("🚩");
                        b.getStyleClass().add("flag");
                    }
                }
            }
            updateFlagLabel();
            showOverlay("🎉 全部雷区已排清，胜利！", "太棒了，可以点击【返回】回到剧情继续冒险～");
        } else {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (grid[r][c] == -1) {
                        if (!revealed[r][c]) {
                            revealed[r][c] = true;
                            renderCell(r, c);
                        }
                    }
                }
            }
            showOverlay("💥 踩到地雷啦！", "点击“再来一局”挑战，或点上方【返回】退出小游戏。");
        }
    }

    private void showOverlay(String title, String desc) {
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("mine-overlay");
        Label t = new Label(title);
        t.getStyleClass().add("mine-title");
        Label d = new Label(desc);
        Button again = new Button("再来一局");
        again.getStyleClass().add("mine-restart");
        again.setOnAction(e -> restartGame());
        box.getChildren().addAll(t, d, again);
        overlay.getChildren().clear();
        overlay.getChildren().add(box);
        overlay.setVisible(true);
        overlay.setManaged(true);
    }
}
