package com.example.minesweeper;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.Random;

public class Main extends Application {

    private static final int SIZE = 9;
    private static final int MINE_COUNT = 10;
    private static final int CELL_SIZE = 48;

    private static final String WINDOW_STYLE =
            "-fx-background-color: #17202a; -fx-border-color: #34495e;";
    private static final String HIDDEN_STYLE =
            "-fx-background-color: #34495e; -fx-border-color: #5d6d7e;"
                    + " -fx-border-width: 1; -fx-font-size: 18px; -fx-font-weight: bold;";
    private static final String REVEALED_STYLE =
            "-fx-background-color: #ecf0f1; -fx-border-color: #bdc3c7;"
                    + " -fx-border-width: 1; -fx-font-size: 18px; -fx-font-weight: bold;";
    private static final String MINE_STYLE =
            "-fx-background-color: #e74c3c; -fx-border-color: #922b21;"
                    + " -fx-border-width: 1; -fx-font-size: 18px; -fx-font-weight: bold;";
    private static final String FLAG_STYLE =
            "-fx-background-color: #f39c12; -fx-border-color: #d68910;"
                    + " -fx-border-width: 1; -fx-font-size: 18px; -fx-font-weight: bold;";

    private final Button[][] cells = new Button[SIZE][SIZE];
    private final boolean[][] mines = new boolean[SIZE][SIZE];
    private final boolean[][] revealed = new boolean[SIZE][SIZE];
    private final boolean[][] flagged = new boolean[SIZE][SIZE];
    private final int[][] adjacentMines = new int[SIZE][SIZE];
    private final Random random = new Random();

    private Label mineLabel;
    private Label timeLabel;
    private Label statusLabel;
    private Timeline timer;
    private int flaggedCount;
    private int revealedSafeCount;
    private int elapsedSeconds;
    private boolean firstMove = true;
    private boolean gameFinished;

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle(WINDOW_STYLE);

        Label title = new Label("MINESWEEPER");
        title.setStyle("-fx-text-fill: #f7f9f9; -fx-font-size: 24px; -fx-font-weight: bold;");

        mineLabel = createInfoLabel();
        timeLabel = createInfoLabel();
        Button restartButton = new Button("重新开始");
        restartButton.setStyle(
                "-fx-background-color: #3498db; -fx-text-fill: white;"
                        + " -fx-font-weight: bold; -fx-background-radius: 6;"
        );
        restartButton.setOnAction(event -> resetGame());

        HBox header = new HBox(18, title, mineLabel, timeLabel, restartButton);
        header.setAlignment(Pos.CENTER);
        header.setStyle("-fx-padding: 18 18 14 18;");
        root.setTop(header);

        GridPane board = new GridPane();
        board.setAlignment(Pos.CENTER);
        board.setStyle("-fx-padding: 8;");
        createBoard(board);
        root.setCenter(board);

        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #d6eaf8; -fx-font-size: 14px;");
        VBox footer = new VBox(statusLabel);
        footer.setAlignment(Pos.CENTER);
        footer.setStyle("-fx-padding: 10 10 18 10;");
        root.setBottom(footer);

        Scene scene = new Scene(root);
        scene.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("R")) {
                resetGame();
            }
        });

        stage.setTitle("JavaFX 扫雷");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
        resetGame();
    }

    private Label createInfoLabel() {
        Label label = new Label();
        label.setStyle("-fx-text-fill: #f7f9f9; -fx-font-size: 14px; -fx-font-weight: bold;");
        return label;
    }

    private void createBoard(GridPane board) {
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                int currentRow = row;
                int currentColumn = column;
                Button cell = new Button();
                cell.setPrefSize(CELL_SIZE, CELL_SIZE);
                cell.setMinSize(CELL_SIZE, CELL_SIZE);
                cell.setMaxSize(CELL_SIZE, CELL_SIZE);
                cell.setFocusTraversable(false);
                cell.setStyle(HIDDEN_STYLE);
                cell.setOnAction(event -> reveal(currentRow, currentColumn));
                cell.setOnMouseClicked(event -> {
                    if (event.getButton() == MouseButton.SECONDARY) {
                        toggleFlag(currentRow, currentColumn);
                        event.consume();
                    }
                });
                cells[row][column] = cell;
                board.add(cell, column, row);
            }
        }
    }

    private void resetGame() {
        stopTimer();
        flaggedCount = 0;
        revealedSafeCount = 0;
        elapsedSeconds = 0;
        firstMove = true;
        gameFinished = false;

        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                mines[row][column] = false;
                revealed[row][column] = false;
                flagged[row][column] = false;
                adjacentMines[row][column] = 0;
            }
        }

        updateHeader();
        statusLabel.setText("左键翻开格子，右键插旗。第一次点击不会踩雷。");
        renderBoard();
    }

    private void reveal(int row, int column) {
        if (gameFinished || revealed[row][column] || flagged[row][column]) {
            return;
        }

        if (firstMove) {
            placeMines(row, column);
            firstMove = false;
            startTimer();
        }

        if (mines[row][column]) {
            revealed[row][column] = true;
            gameFinished = true;
            stopTimer();
            revealAllMines();
            statusLabel.setText("游戏结束！你踩到了地雷，点击“重新开始”再来一次。");
            renderBoard();
            return;
        }

        floodReveal(row, column);
        renderBoard();

        if (revealedSafeCount == SIZE * SIZE - MINE_COUNT) {
            gameFinished = true;
            stopTimer();
            revealAllFlags();
            statusLabel.setText("恭喜胜利！你找出了全部地雷。");
            renderBoard();
        } else {
            statusLabel.setText("继续排雷，谨慎一点！");
        }
    }

    private void toggleFlag(int row, int column) {
        if (gameFinished || revealed[row][column]) {
            return;
        }

        if (!flagged[row][column] && flaggedCount >= MINE_COUNT) {
            statusLabel.setText("已经插满 10 面旗子，请先取消一面旗子。");
            return;
        }

        flagged[row][column] = !flagged[row][column];
        flaggedCount += flagged[row][column] ? 1 : -1;
        updateHeader();
        renderBoard();
    }

    private void placeMines(int safeRow, int safeColumn) {
        int placed = 0;
        while (placed < MINE_COUNT) {
            int row = random.nextInt(SIZE);
            int column = random.nextInt(SIZE);
            if ((row == safeRow && column == safeColumn) || mines[row][column]) {
                continue;
            }
            mines[row][column] = true;
            placed++;
        }

        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                adjacentMines[row][column] = countAdjacentMines(row, column);
            }
        }
    }

    private int countAdjacentMines(int row, int column) {
        int count = 0;
        for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
            for (int columnOffset = -1; columnOffset <= 1; columnOffset++) {
                if (rowOffset == 0 && columnOffset == 0) {
                    continue;
                }
                int neighborRow = row + rowOffset;
                int neighborColumn = column + columnOffset;
                if (isInside(neighborRow, neighborColumn) && mines[neighborRow][neighborColumn]) {
                    count++;
                }
            }
        }
        return count;
    }

    private void floodReveal(int startRow, int startColumn) {
        ArrayDeque<int[]> pending = new ArrayDeque<>();
        pending.add(new int[]{startRow, startColumn});

        while (!pending.isEmpty()) {
            int[] current = pending.removeFirst();
            int row = current[0];
            int column = current[1];

            if (!isInside(row, column) || revealed[row][column]
                    || flagged[row][column] || mines[row][column]) {
                continue;
            }

            revealed[row][column] = true;
            revealedSafeCount++;

            if (adjacentMines[row][column] != 0) {
                continue;
            }

            for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
                for (int columnOffset = -1; columnOffset <= 1; columnOffset++) {
                    if (rowOffset != 0 || columnOffset != 0) {
                        pending.add(new int[]{row + rowOffset, column + columnOffset});
                    }
                }
            }
        }
    }

    private void renderBoard() {
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                Button cell = cells[row][column];
                cell.setText("");
                cell.setTextFill(Color.WHITE);

                if (flagged[row][column] && !revealed[row][column]) {
                    cell.setText("!");
                    cell.setTextFill(Color.WHITE);
                    cell.setStyle(FLAG_STYLE);
                } else if (revealed[row][column]) {
                    if (mines[row][column]) {
                        cell.setText("*");
                        cell.setTextFill(Color.WHITE);
                        cell.setStyle(MINE_STYLE);
                    } else {
                        int count = adjacentMines[row][column];
                        cell.setText(count == 0 ? "" : String.valueOf(count));
                        cell.setTextFill(numberColor(count));
                        cell.setStyle(REVEALED_STYLE);
                    }
                } else {
                    cell.setStyle(HIDDEN_STYLE);
                }
            }
        }
    }

    private Color numberColor(int count) {
        return switch (count) {
            case 1 -> Color.web("#2471a3");
            case 2 -> Color.web("#229954");
            case 3 -> Color.web("#cb4335");
            case 4 -> Color.web("#6c3483");
            case 5 -> Color.web("#922b21");
            case 6 -> Color.web("#148f77");
            case 7 -> Color.web("#17202a");
            case 8 -> Color.web("#566573");
            default -> Color.web("#17202a");
        };
    }

    private void revealAllMines() {
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                if (mines[row][column]) {
                    revealed[row][column] = true;
                }
            }
        }
    }

    private void revealAllFlags() {
        for (int row = 0; row < SIZE; row++) {
            for (int column = 0; column < SIZE; column++) {
                if (mines[row][column]) {
                    flagged[row][column] = true;
                }
            }
        }
        flaggedCount = MINE_COUNT;
        updateHeader();
    }

    private void startTimer() {
        timer = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            elapsedSeconds++;
            updateHeader();
        }));
        timer.setCycleCount(Animation.INDEFINITE);
        timer.play();
    }

    private void stopTimer() {
        if (timer != null) {
            timer.stop();
        }
    }

    private void updateHeader() {
        mineLabel.setText("地雷: " + (MINE_COUNT - flaggedCount));
        timeLabel.setText("时间: " + elapsedSeconds + "s");
    }

    private boolean isInside(int row, int column) {
        return row >= 0 && row < SIZE && column >= 0 && column < SIZE;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
