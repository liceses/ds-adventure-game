package com.lab.galgame.util;

import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputStateTest {
    @Test
    void tracksWasdAndArrows() {
        InputState input = new InputState();
        input.press(KeyCode.A);
        assertTrue(input.left());
        input.release(KeyCode.A);
        assertFalse(input.left());

        input.press(KeyCode.RIGHT);
        assertTrue(input.right());
    }

    @Test
    void pauseAndResetAreEdgeTriggered() {
        InputState input = new InputState();
        input.press(KeyCode.SPACE);
        input.press(KeyCode.R);
        assertTrue(input.consumePauseToggle());
        assertTrue(input.consumeReset());
        assertFalse(input.consumePauseToggle());
        assertFalse(input.consumeReset());
    }
}
