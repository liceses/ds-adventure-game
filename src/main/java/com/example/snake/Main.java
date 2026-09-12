package com.example.snake;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) {

        Canvas canvas = new Canvas(600, 600);

        GamePanel gamePanel = new GamePanel(canvas);

        StackPane root = new StackPane(canvas);

        Scene scene = new Scene(root);

        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case UP:
                    gamePanel.changeDirection(Dir.UP);
                    break;
                case DOWN:
                    gamePanel.changeDirection(Dir.DOWN);
                    break;
                case LEFT:
                    gamePanel.changeDirection(Dir.LEFT);
                    break;
                case RIGHT:
                    gamePanel.changeDirection(Dir.RIGHT);
                    break;
                case SPACE:
                    gamePanel.restart();
                    break;
            }
        });

        stage.setTitle("贪吃蛇");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

        root.requestFocus();

        gamePanel.start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}