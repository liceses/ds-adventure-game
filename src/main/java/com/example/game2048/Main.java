package com.example.game2048;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Main extends Application {

    private static final int SIZE = 4;
    private static final int TARGET = 2048;
    private static final int TILE_SIZE = 88;

    private final int[][] board = new int[SIZE][SIZE];
    private final Random random = new Random();

    private GridPane boardView;
    private StackPane gameArea;
    private Label scoreLabel;
    private Label bestLabel;
    private Label statusLabel;
    private long score;
    private long bestScore;
    private boolean gameOver;
    private boolean won;
    private boolean continueAfterWin;

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #faf8ef;");

        Label title = new Label("2048");
        title.setStyle("-fx-text-fill: #776e65; -fx-font-size: 42px; -fx-font-weight: bold;");

        scoreLabel = createScoreLabel();
        bestLabel = createScoreLabel();

        Button restartButton = new Button("重新开始");
        restartButton.setStyle(buttonStyle());
        restartButton.setOnAction(event -> restartGame());

        HBox scoreBox = new HBox(8, scoreLabel, bestLabel);
        scoreBox.setAlignment(Pos.CENTER_RIGHT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(12, title, spacer, scoreBox, restartButton);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(20, 20, 10, 20));

        Label instructions = new Label("使用方向键或 WASD 移动，相同数字会合并");
        instructions.setStyle("-fx-text-fill: #776e65; -fx-font-size: 14px;");
        HBox instructionsBox = new HBox(instructions);
        instructionsBox.setAlignment(Pos.CENTER);
        instructionsBox.setPadding(new Insets(0, 20, 12, 20));

        boardView = new GridPane();
        boardView.setHgap(8);
        boardView.setVgap(8);
        boardView.setPadding(new Insets(8));
        boardView.setStyle("-fx-background-color: #bbada0; -fx-background-radius: 8;");

        gameArea = new StackPane(boardView);
        gameArea.setAlignment(Pos.CENTER);

        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #776e65; -fx-font-size: 14px;");
        HBox statusBox = new HBox(statusLabel);
        statusBox.setAlignment(Pos.CENTER);
        statusBox.setPadding(new Insets(12, 20, 4, 20));

        VBox controls = createControls();
        VBox content = new VBox(8, header, instructionsBox, gameArea, statusBox, controls);
        content.setAlignment(Pos.TOP_CENTER);
        root.setCenter(content);

        Scene scene = new Scene(root, 500, 650);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            Direction direction = directionFor(event.getCode());
            if (direction != null) {
                event.consume();
                move(direction);
            } else if (event.getCode() == KeyCode.R) {
                event.consume();
                restartGame();
            }
        });

        stage.setTitle("JavaFX 2048");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

        restartGame();
        root.requestFocus();
    }

    private Label createScoreLabel() {
        Label label = new Label();
        label.setMinWidth(78);
        label.setAlignment(Pos.CENTER);
        label.setStyle(
                "-fx-background-color: #bbada0; -fx-background-radius: 5;"
                        + "-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;"
                        + "-fx-padding: 8 10;"
        );
        return label;
    }

    private VBox createControls() {
        Button up = directionButton("上", Direction.UP);
        Button left = directionButton("左", Direction.LEFT);
        Button down = directionButton("下", Direction.DOWN);
        Button right = directionButton("右", Direction.RIGHT);

        HBox middle = new HBox(6, left, down, right);
        middle.setAlignment(Pos.CENTER);
        VBox controls = new VBox(6, up, middle);
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(new Insets(2, 20, 18, 20));
        return controls;
    }

    private Button directionButton(String text, Direction direction) {
        Button button = new Button(text);
        button.setPrefSize(52, 34);
        button.setMinSize(52, 34);
        button.setFocusTraversable(false);
        button.setStyle(buttonStyle());
        button.setOnAction(event -> move(direction));
        return button;
    }

    private String buttonStyle() {
        return "-fx-background-color: #8f7a66; -fx-text-fill: white;"
                + "-fx-font-size: 14px; -fx-font-weight: bold;"
                + "-fx-background-radius: 5; -fx-padding: 9 14;";
    }

    private void restartGame() {
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                board[row][column] = 0;
            }
        }
        score = 0;
        gameOver = false;
        won = false;
        continueAfterWin = false;
        spawnTile();
        spawnTile();
        statusLabel.setText("合成出 2048 即可获胜");
        hideOverlay();
        render();
    }

    private void move(Direction direction) {
        if (gameOver || (won && !continueAfterWin)) {
            return;
        }

        boolean changed = switch (direction) {
            case LEFT -> moveRows(false);
            case RIGHT -> moveRows(true);
            case UP -> moveColumns(false);
            case DOWN -> moveColumns(true);
        };

        if (!changed) {
            if (!hasAvailableMove()) {
                finishGame();
            }
            return;
        }

        spawnTile();
        render();
        checkGameState();
    }

    private boolean moveRows(boolean reverse) {
        boolean changed = false;
        for (int row = 0; row < SIZE; row++) {
            int[] source = new int[SIZE];
            for (int index = 0; index < SIZE; index++) {
                source[index] = reverse ? board[row][SIZE - 1 - index] : board[row][index];
            }
            int[] merged = mergeLine(source);
            for (int index = 0; index < SIZE; index++) {
                int oldValue = reverse ? board[row][SIZE - 1 - index] : board[row][index];
                if (oldValue != merged[index]) {
                    changed = true;
                }
                if (reverse) {
                    board[row][SIZE - 1 - index] = merged[index];
                } else {
                    board[row][index] = merged[index];
                }
            }
        }
        return changed;
    }

    private boolean moveColumns(boolean reverse) {
        boolean changed = false;
        for (int column = 0; column < SIZE; column++) {
            int[] source = new int[SIZE];
            for (int index = 0; index < SIZE; index++) {
                source[index] = reverse
                        ? board[SIZE - 1 - index][column]
                        : board[index][column];
            }
            int[] merged = mergeLine(source);
            for (int index = 0; index < SIZE; index++) {
                int oldValue = reverse
                        ? board[SIZE - 1 - index][column]
                        : board[index][column];
                if (oldValue != merged[index]) {
                    changed = true;
                }
                if (reverse) {
                    board[SIZE - 1 - index][column] = merged[index];
                } else {
                    board[index][column] = merged[index];
                }
            }
        }
        return changed;
    }

    private int[] mergeLine(int[] source) {
        List<Integer> values = new ArrayList<>();
        for (int value : source) {
            if (value != 0) {
                values.add(value);
            }
        }

        List<Integer> merged = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            int value = values.get(index);
            if (index + 1 < values.size() && value == values.get(index + 1)) {
                value *= 2;
                score += value;
                index++;
            }
            merged.add(value);
        }

        int[] result = new int[SIZE];
        for (int index = 0; index < merged.size(); index++) {
            result[index] = merged.get(index);
        }
        return result;
    }

    private void spawnTile() {
        List<int[]> emptyCells = new ArrayList<>();
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                if (board[row][column] == 0) {
                    emptyCells.add(new int[]{row, column});
                }
            }
        }
        if (emptyCells.isEmpty()) {
            return;
        }

        int[] position = emptyCells.get(random.nextInt(emptyCells.size()));
        board[position[0]][position[1]] = random.nextDouble() < 0.9 ? 2 : 4;
    }

    private void checkGameState() {
        if (!won && containsTarget()) {
            won = true;
            showOverlay("恭喜你！", "你已经合成了 2048。", "继续挑战", () -> {
                continueAfterWin = true;
                hideOverlay();
                statusLabel.setText("继续挑战更高分！");
            });
        } else if (!hasAvailableMove()) {
            finishGame();
        }
    }

    private void finishGame() {
        gameOver = true;
        showOverlay("游戏结束", "没有可以移动的方块了。", "再来一局", this::restartGame);
    }

    private boolean containsTarget() {
        for (int[] row : board) {
            for (int value : row) {
                if (value >= TARGET) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasAvailableMove() {
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                if (board[row][column] == 0) {
                    return true;
                }
                if (column + 1 < SIZE && board[row][column] == board[row][column + 1]) {
                    return true;
                }
                if (row + 1 < SIZE && board[row][column] == board[row + 1][column]) {
                    return true;
                }
            }
        }
        return false;
    }

    private void render() {
        boardView.getChildren().clear();
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                int value = board[row][column];
                Label tile = new Label(value == 0 ? "" : String.valueOf(value));
                tile.setMinSize(TILE_SIZE, TILE_SIZE);
                tile.setPrefSize(TILE_SIZE, TILE_SIZE);
                tile.setMaxSize(TILE_SIZE, TILE_SIZE);
                tile.setAlignment(Pos.CENTER);
                tile.setStyle(tileStyle(value));
                boardView.add(tile, column, row);
            }
        }

        scoreLabel.setText("分数\n" + score);
        if (score > bestScore) {
            bestScore = score;
        }
        bestLabel.setText("最高分\n" + bestScore);
    }

    private void showOverlay(
            String title,
            String description,
            String actionText,
            Runnable action
    ) {
        VBox panel = new VBox(12);
        panel.setAlignment(Pos.CENTER);
        panel.setMaxWidth(290);
        panel.setPadding(new Insets(24));
        panel.setStyle(
                "-fx-background-color: rgba(250,248,239,0.95);"
                        + "-fx-background-radius: 8;"
                        + "-fx-border-color: #8f7a66; -fx-border-radius: 8;"
        );

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #776e65; -fx-font-size: 25px; -fx-font-weight: bold;");

        Label descriptionLabel = new Label(description + "\n当前分数：" + score);
        descriptionLabel.setWrapText(true);
        descriptionLabel.setAlignment(Pos.CENTER);
        descriptionLabel.setStyle("-fx-text-fill: #776e65; -fx-font-size: 14px;");

        Button actionButton = new Button(actionText);
        actionButton.setStyle(buttonStyle());
        actionButton.setOnAction(event -> action.run());
        panel.getChildren().addAll(titleLabel, descriptionLabel, actionButton);

        hideOverlay();
        gameArea.getChildren().add(panel);
        StackPane.setAlignment(panel, Pos.CENTER);
        statusLabel.setText(description);
    }

    private void hideOverlay() {
        gameArea.getChildren().removeIf(node -> node != boardView);
    }

    private static String tileStyle(int value) {
        return switch (value) {
            case 0 -> baseTile("#cdc1b4", "#776e65", 24);
            case 2 -> baseTile("#eee4da", "#776e65", 30);
            case 4 -> baseTile("#ede0c8", "#776e65", 30);
            case 8 -> baseTile("#f2b179", "#f9f6f2", 30);
            case 16 -> baseTile("#f59563", "#f9f6f2", 30);
            case 32 -> baseTile("#f67c5f", "#f9f6f2", 30);
            case 64 -> baseTile("#f65e3b", "#f9f6f2", 30);
            case 128 -> baseTile("#edcf72", "#f9f6f2", 27);
            case 256 -> baseTile("#edcc61", "#f9f6f2", 27);
            case 512 -> baseTile("#edc850", "#f9f6f2", 27);
            case 1024 -> baseTile("#edc53f", "#f9f6f2", 23);
            case 2048 -> baseTile("#edc22e", "#f9f6f2", 23);
            default -> baseTile("#3c3a32", "#f9f6f2", 20);
        };
    }

    private static String baseTile(String background, String foreground, int fontSize) {
        return "-fx-background-color: " + background + ";"
                + "-fx-background-radius: 6;"
                + "-fx-text-fill: " + foreground + ";"
                + "-fx-font-size: " + fontSize + "px;"
                + "-fx-font-weight: bold;";
    }

    private Direction directionFor(KeyCode code) {
        return switch (code) {
            case LEFT, A -> Direction.LEFT;
            case RIGHT, D -> Direction.RIGHT;
            case UP, W -> Direction.UP;
            case DOWN, S -> Direction.DOWN;
            default -> null;
        };
    }

    private enum Direction {
        LEFT, RIGHT, UP, DOWN
    }

    public static void main(String[] args) {
        launch(args);
    }
}
