package com.studio.plugin.demo.memory;

import com.studio.plugin.GamePlugin;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Map;

/**
 * 记忆翻牌小游戏插件（内置形态，事件ID <code>memory</code>）。
 *
 * <p>玩法规格见 <code>docs/ds-adventrue/记忆翻牌.md</code>：牌背面朝上铺开，每次翻两张，
 * 图案相同就留着，不同则盖回去；靠记忆把所有对子翻出来即通关。支持单人计时与双人轮流计分。</p>
 *
 * <p>规则全部委托给 {@link MemoryGame}，本类只负责界面与交互，
 * {@link #onDetach()} 幂等停掉游戏循环。</p>
 */
public class MemoryPlugin implements GamePlugin {

    private static final int[] DIFFICULTY_ROWS = {3, 4, 4};
    private static final int[] DIFFICULTY_COLS = {4, 6, 8};
    private static final String[] DIFFICULTY_NAMES = {"简单", "普通", "困难"};
    private static final String[] THEME_NAMES = {"动物", "水果", "汉字"};
    private static final String[] THEMES = {
        MemoryConfig.THEME_ANIMALS, MemoryConfig.THEME_FRUITS, MemoryConfig.THEME_HANZI,
    };

    private static final double CARD_SIZE = 68;
    private static final double CARD_SPACING = 6;
    private static final double CARD_FONT_SIZE = 28;
    private static final double HEADER_SPACING = 10;
    private static final double HEADER_PADDING = 10;
    private static final double STAT_FONT_SIZE = 14;
    private static final double OVERLAY_TITLE_FONT_SIZE = 28;
    private static final double OVERLAY_BODY_FONT_SIZE = 15;
    private static final double STEP_SECONDS = 1.0 / 30;
    private static final double MAX_FRAME_SECONDS = 0.25;

    private static final String HIDDEN_FACE = "?";
    private static final String CARD_BASE =
            "-fx-background-radius: 8; -fx-border-radius: 8; -fx-font-size: " + (int) CARD_FONT_SIZE + "px;";
    private static final String STYLE_DOWN = CARD_BASE
            + "-fx-background-color: #2a2f45; -fx-text-fill: #6b74a0; -fx-border-color: #3a4160; -fx-border-width: 1;";
    private static final String STYLE_UP = CARD_BASE
            + "-fx-background-color: #45507a; -fx-text-fill: #ffffff; -fx-border-color: #7d8ac4; -fx-border-width: 2;";
    private static final String STYLE_MATCHED = CARD_BASE
            + "-fx-background-color: #1f3a2c; -fx-text-fill: #7be08a; -fx-border-color: #2f5a42; -fx-border-width: 1;"
            + "-fx-opacity: 0.75;";
    private static final String STYLE_STAT = "-fx-text-fill: #c9cbe0; -fx-font-size: " + (int) STAT_FONT_SIZE + "px;";
    private static final String STYLE_HINT = "-fx-text-fill: #8a92a6; -fx-font-size: " + (int) STAT_FONT_SIZE + "px;";

    private final MemoryConfig config = new MemoryConfig();
    private final MemoryGame game = new MemoryGame();

    private BorderPane root;
    private GridPane board;
    private Label statLabel;
    private Label playerLabel;
    private Label hintLabel;
    private StackPane holder;
    private VBox overlay;
    private Label overlayTitle;
    private Label overlayDesc;

    private Button[] cardButtons = new Button[0];
    private AnimationTimer loop;
    private double accumulator;
    private long lastNanos;
    private int difficulty = 1;
    private int themeIndex;
    private int shownSeconds = -1;

    @Override
    public void execute(Stage stage, Map<String, Object> params) {
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
        applyDifficulty(difficulty);
        game.reset(config);

        board = new GridPane();
        board.setHgap(CARD_SPACING);
        board.setVgap(CARD_SPACING);
        board.setAlignment(Pos.CENTER);

        holder = new StackPane(board);
        overlay = buildOverlay();
        overlay.setVisible(false);
        overlay.setManaged(false);
        holder.getChildren().add(overlay);

        root = new BorderPane();
        root.setTop(buildHeader());
        root.setCenter(holder);
        root.setFocusTraversable(true);

        root.parentProperty().addListener((obs, old, now) -> {
            if (now == null) {
                stopLoop();
            }
        });

        bindKeys();
        rebuildBoard();
        startLoop();
        Platform.runLater(() -> root.requestFocus());
        return root;
    }

    /** 快捷键：H 提示、R 重开。事件在此消费，避免冒泡到宿主场景。 */
    private void bindKeys() {
        root.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case H -> usePeek();
                case R -> restart();
                default -> { }
            }
            event.consume();
        });
    }

    private void usePeek() {
        if (game.usePeek()) {
            refreshBoard();
        }
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
        HBox stats = new HBox(HEADER_SPACING, statLabel, playerLabel, spacer, hintLabel);
        stats.setAlignment(Pos.CENTER_LEFT);

        HBox controls = new HBox(6);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.getChildren().add(new Label("难度:"));
        for (int i = 0; i < DIFFICULTY_NAMES.length; i++) {
            final int level = i;
            Button b = new Button(DIFFICULTY_NAMES[i]);
            b.setOnAction(e -> {
                difficulty = level;
                restart();
            });
            controls.getChildren().add(b);
        }
        Button themeBtn = new Button("换主题");
        themeBtn.setOnAction(e -> {
            themeIndex = (themeIndex + 1) % THEMES.length;
            config.setTheme(THEMES[themeIndex]);
            restart();
        });
        Button modeBtn = new Button("切换单人/双人");
        modeBtn.setOnAction(e -> {
            config.setTwoPlayer(!config.isTwoPlayer());
            restart();
        });
        Button peekBtn = new Button("提示 (H)");
        peekBtn.setOnAction(e -> usePeek());
        Button restartBtn = new Button("↻ 重新开局");
        restartBtn.setOnAction(e -> restart());
        controls.getChildren().addAll(peekBtn, themeBtn, modeBtn, restartBtn);

        VBox header = new VBox(6, stats, controls);
        header.setPadding(new Insets(HEADER_PADDING));
        header.setStyle("-fx-background-color: #1a1d27;");
        return header;
    }

    private VBox buildOverlay() {
        overlayTitle = new Label();
        overlayTitle.setStyle("-fx-text-fill: #7be08a; -fx-font-size: " + (int) OVERLAY_TITLE_FONT_SIZE + "px; -fx-font-weight: bold;");
        overlayDesc = new Label();
        overlayDesc.setStyle("-fx-text-fill: #d0d4e0; -fx-font-size: " + (int) OVERLAY_BODY_FONT_SIZE + "px;");

        Button again = new Button("再来一局");
        again.setOnAction(e -> restart());

        VBox box = new VBox(12, overlayTitle, overlayDesc, again);
        box.setAlignment(Pos.CENTER);
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        box.setPadding(new Insets(24));
        box.setStyle("-fx-background-color: rgba(16,19,34,0.94); -fx-background-radius: 12;");
        return box;
    }

    // =====================================================================
    // 牌面与刷新
    // =====================================================================

    private void applyDifficulty(int level) {
        config.setGrid(DIFFICULTY_ROWS[level], DIFFICULTY_COLS[level]);
        config.setTheme(THEMES[themeIndex]);
    }

    private void rebuildBoard() {
        board.getChildren().clear();
        int count = game.cardCount();
        cardButtons = new Button[count];
        for (int i = 0; i < count; i++) {
            Button card = new Button(HIDDEN_FACE);
            card.setPrefSize(CARD_SIZE, CARD_SIZE);
            card.setMinSize(CARD_SIZE, CARD_SIZE);
            card.setMaxSize(CARD_SIZE, CARD_SIZE);
            final int index = i;
            card.setOnAction(e -> {
                game.flip(index);
                refreshBoard();
            });
            cardButtons[i] = card;
            board.add(card, i % config.getColumns(), i / config.getColumns());
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

    private void applyCardStyle(int index) {
        Button card = cardButtons[index];
        if (game.isMatched(index)) {
            card.setText(game.symbolAt(index));
            card.setStyle(STYLE_MATCHED);
        } else if (game.isFaceUp(index)) {
            card.setText(game.symbolAt(index));
            card.setStyle(STYLE_UP);
        } else {
            card.setText(HIDDEN_FACE);
            card.setStyle(STYLE_DOWN);
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
            playerLabel.setText("玩家1 " + game.pairsOf(0) + " 对   |   玩家2 " + game.pairsOf(1) + " 对   (轮到玩家"
                    + (game.currentPlayer() + 1) + ")");
        } else {
            playerLabel.setText("单人计时模式");
        }
        hintLabel.setText(game.peekLeft() > 0 ? "提示剩余 " + game.peekLeft() + " 次" : "提示已用完");
    }

    private void refreshOverlay() {
        if (!game.isOver()) {
            if (overlay.isVisible()) {
                overlay.setVisible(false);
                overlay.setManaged(false);
            }
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
                    game.tick(STEP_SECONDS);
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

    private void restart() {
        applyDifficulty(difficulty);
        game.reset(config);
        shownSeconds = -1;
        rebuildBoard();
    }
}
