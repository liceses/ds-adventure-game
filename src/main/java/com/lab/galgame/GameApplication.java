package com.lab.galgame;

import com.lab.galgame.config.GameConfig;
import com.lab.galgame.controller.GameController;
import com.lab.galgame.util.InputState;
import com.lab.galgame.view.GameView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * 实训入口：Galgame 主框架 + 小游戏特殊演出。
 * 当前是沙盒：WASD/方向键移动方块，空格暂停，R 重置。
 */
public class GameApplication extends Application {

    @Override
    public void start(Stage stage) {
        GameView view = new GameView(GameConfig.VIEW_WIDTH, GameConfig.VIEW_HEIGHT);
        GameController controller = new GameController(view);
        InputState input = controller.getInput();

        Scene scene = new Scene(view.getRoot(), GameConfig.VIEW_WIDTH, GameConfig.VIEW_HEIGHT, Color.web("#111418"));
        var css = getClass().getResource("/css/game.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        scene.setOnKeyPressed(event -> input.press(event.getCode()));
        scene.setOnKeyReleased(event -> input.release(event.getCode()));

        stage.setTitle(GameConfig.TITLE);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.setOnCloseRequest(event -> controller.stop());
        stage.show();
        controller.start();
        view.getRoot().requestFocus();

        long autocloseMs = Long.getLong("fxgame.autoclose", 0L);
        if (autocloseMs > 0) {
            Thread closer = new Thread(() -> {
                try {
                    Thread.sleep(autocloseMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                Platform.runLater(stage::close);
            }, "fx-autoclose");
            closer.setDaemon(true);
            closer.start();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
