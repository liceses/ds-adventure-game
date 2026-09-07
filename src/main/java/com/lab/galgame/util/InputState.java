package com.lab.galgame.util;

import javafx.scene.input.KeyCode;

import java.util.EnumSet;
import java.util.Set;

/**
 * 按键状态抽象（工具层）：把 JavaFX 事件抽出来，游戏循环每帧读取。
 * 小游戏各自复用，不直接绑 Scene 事件。
 */
public class InputState {
    private final Set<KeyCode> held = EnumSet.noneOf(KeyCode.class);
    private boolean pausePressed;
    private boolean resetPressed;

    public void press(KeyCode code) {
        held.add(code);
        if (code == KeyCode.SPACE) {
            pausePressed = true;
        }
        if (code == KeyCode.R) {
            resetPressed = true;
        }
    }

    public void release(KeyCode code) {
        held.remove(code);
    }

    public boolean left() {
        return held.contains(KeyCode.LEFT) || held.contains(KeyCode.A);
    }

    public boolean right() {
        return held.contains(KeyCode.RIGHT) || held.contains(KeyCode.D);
    }

    public boolean up() {
        return held.contains(KeyCode.UP) || held.contains(KeyCode.W);
    }

    public boolean down() {
        return held.contains(KeyCode.DOWN) || held.contains(KeyCode.S);
    }

    public boolean consumePauseToggle() {
        boolean value = pausePressed;
        pausePressed = false;
        return value;
    }

    public boolean consumeReset() {
        boolean value = resetPressed;
        resetPressed = false;
        return value;
    }

    public void endFrame() {
        // 预留：以后做“本帧刚按下”的边沿检测
    }
}
