package com.lab.galgame.controller;

import com.lab.galgame.config.GameConfig;
import com.lab.galgame.model.GameState;
import com.lab.galgame.util.InputState;
import com.lab.galgame.view.GameView;
import javafx.animation.AnimationTimer;

/**
 * 控制器层：固定时间步游戏循环 + 输入处理。
 * 以后 Galgame 剧情推进、小游戏切换也由控制器调度。
 */
public class GameController {
    private final GameView view;
    private final GameState state = new GameState();
    private final InputState input = new InputState();
    private final AnimationTimer loop;
    private long lastNanos;

    public GameController(GameView view) {
        this.view = view;
        this.state.reset(
                (GameConfig.VIEW_WIDTH - GameConfig.PLAYER_SIZE) / 2,
                (GameConfig.VIEW_HEIGHT - GameConfig.PLAYER_SIZE) / 2
        );
        this.loop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastNanos == 0) {
                    lastNanos = now;
                    return;
                }
                double dt = (now - lastNanos) / 1_000_000_000.0;
                lastNanos = now;
                dt = Math.min(dt, 0.05); // 防止窗口拖动后单帧 dt 爆炸
                tick(dt);
            }
        };
    }

    public InputState getInput() {
        return input;
    }

    public void start() {
        state.setRunning(true);
        lastNanos = 0;
        loop.start();
    }

    public void stop() {
        state.setRunning(false);
        loop.stop();
    }

    private void tick(double dt) {
        if (input.consumeReset()) {
            state.reset(
                    (GameConfig.VIEW_WIDTH - GameConfig.PLAYER_SIZE) / 2,
                    (GameConfig.VIEW_HEIGHT - GameConfig.PLAYER_SIZE) / 2
            );
        }
        if (input.consumePauseToggle()) {
            state.setPaused(!state.isPaused());
        }
        if (!state.isPaused()) {
            update(dt);
        }
        view.render(state);
        input.endFrame();
    }

    private void update(double dt) {
        double dx = 0;
        double dy = 0;
        if (input.left()) {
            dx -= 1;
        }
        if (input.right()) {
            dx += 1;
        }
        if (input.up()) {
            dy -= 1;
        }
        if (input.down()) {
            dy += 1;
        }
        if (dx != 0 && dy != 0) {
            dx *= 0.7071;
            dy *= 0.7071;
        }
        double x = state.getPlayerX() + dx * GameConfig.PLAYER_SPEED * dt;
        double y = state.getPlayerY() + dy * GameConfig.PLAYER_SPEED * dt;
        state.setPlayerX(clamp(x, 0, GameConfig.VIEW_WIDTH - GameConfig.PLAYER_SIZE));
        state.setPlayerY(clamp(y, 0, GameConfig.VIEW_HEIGHT - GameConfig.PLAYER_SIZE));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
