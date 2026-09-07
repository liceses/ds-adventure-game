package com.lab.galgame.view;

import com.lab.galgame.config.GameConfig;
import com.lab.galgame.model.GameState;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * 视图层：只负责把 GameState 画出来，不碰输入和逻辑。
 * 以后每个小游戏一个 View，剧情界面用 FXML。
 */
public class GameView {
    private final StackPane root = new StackPane();
    private final Canvas canvas;

    public GameView(int width, int height) {
        this.canvas = new Canvas(width, height);
        this.root.getChildren().add(canvas);
        this.root.setFocusTraversable(true);
    }

    public StackPane getRoot() {
        return root;
    }

    public void render(GameState state) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#111418"));
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        g.setStroke(Color.web("#2a3340"));
        g.setLineWidth(1);
        for (int x = 0; x < canvas.getWidth(); x += 48) {
            g.strokeLine(x, 0, x, canvas.getHeight());
        }
        for (int y = 0; y < canvas.getHeight(); y += 48) {
            g.strokeLine(0, y, canvas.getWidth(), y);
        }

        g.setFill(Color.web("#5ad0ff"));
        g.fillRoundRect(state.getPlayerX(), state.getPlayerY(), GameConfig.PLAYER_SIZE, GameConfig.PLAYER_SIZE, 8, 8);

        g.setFill(Color.web("#e8eef7"));
        g.setFont(Font.font("Segoe UI", 16));
        g.fillText("Galgame Mini-Game Lab  |  WASD/方向键移动  空格暂停  R重置", 16, 28);
        g.fillText(state.isPaused() ? "状态: 暂停" : "状态: 运行中", 16, 52);
        g.fillText(String.format("位置: (%.0f, %.0f)", state.getPlayerX(), state.getPlayerY()), 16, 76);
        if (!state.isRunning()) {
            g.fillText("循环已停止", 16, 100);
        }
    }
}
