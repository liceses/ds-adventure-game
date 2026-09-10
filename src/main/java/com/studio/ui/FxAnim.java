package com.studio.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

/**
 * 伪类动画助手 —— 模拟“CSS 过渡 (transition)”。
 *
 * <p>JavaFX 原生 CSS 并不支持 transition 声明，因此本类用代码监听
 * {@code :hover} / {@code :pressed} 对应的鼠标事件，
 * 并以 {@link ScaleTransition} 驱动缩放，效果等价于：
 * <pre>
 *   .node:hover   { scale: 1.06; }   /* 悬停放大 */
// *   .node:pressed { scale: 0.94; }   /* 点击缩小 */
// * </pre>
// * 编辑器与读取器共用本工具，保证两处交互手感一致。
// */
public final class FxAnim {

    private FxAnim() { }

    /** 默认悬停放大倍数 */
    public static final double DEFAULT_HOVER = 1.06;
    /** 默认按下缩小倍数 */
    public static final double DEFAULT_PRESSED = 0.94;

    /**
     * 为节点附加 hover / pressed 缩放动画。
     * 通过 addEventHandler 挂监听，不会覆盖业务侧已有的事件处理器。
     */
    public static void makeHoverable(Node node, double hoverScale, double pressedScale) {
        final ScaleTransition hoverIn = new ScaleTransition(Duration.millis(160), node);
        hoverIn.setToX(hoverScale);
        hoverIn.setToY(hoverScale);
        hoverIn.setInterpolator(Interpolator.EASE_OUT);

        final ScaleTransition hoverOut = new ScaleTransition(Duration.millis(220), node);
        hoverOut.setToX(1.0);
        hoverOut.setToY(1.0);
        hoverOut.setInterpolator(Interpolator.EASE_BOTH);

        final ScaleTransition pressDown = new ScaleTransition(Duration.millis(70), node);
        pressDown.setToX(pressedScale);
        pressDown.setToY(pressedScale);

        final ScaleTransition pressUp = new ScaleTransition(Duration.millis(140), node);
        pressUp.setToX(hoverScale);
        pressUp.setToY(hoverScale);

        node.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
            hoverIn.stop(); hoverOut.stop(); pressUp.stop();
            hoverIn.playFromStart();
        });
        node.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            hoverIn.stop(); pressUp.stop();
            hoverOut.playFromStart();
        });
        node.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            hoverIn.stop(); pressDown.stop();
            pressDown.playFromStart();
        });
        node.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            pressUp.playFromStart();
        });
    }

    /** 悬停/按下动画 + 手型光标 */
    public static void makeInteractive(Node node) {
        makeHoverable(node, DEFAULT_HOVER, DEFAULT_PRESSED);
    }

    /**
     * 入场动画：淡入 + 上浮 + 轻微放大。
     * @param delayMs 延迟（多节点依次入场时错开）
     */
    public static void entrance(Node node, double delayMs, double durationMs) {
        node.setOpacity(0);
        node.setScaleX(0.92);
        node.setScaleY(0.92);

        FadeTransition fade = new FadeTransition(Duration.millis(durationMs), node);
        fade.setToValue(1.0);

        ScaleTransition scale = new ScaleTransition(Duration.millis(durationMs), node);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition rise = new TranslateTransition(Duration.millis(durationMs), node);
        rise.setFromY(18);
        rise.setToY(0);
        rise.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition pt = new ParallelTransition(node, fade, scale, rise);
        pt.setDelay(Duration.millis(delayMs));
        pt.play();
    }

    /** 快速淡出（切场景时使用） */
    public static void fadeOut(Node node, double ms, Runnable after) {
        FadeTransition ft = new FadeTransition(Duration.millis(ms), node);
        ft.setToValue(0);
        ft.setOnFinished(e -> { if (after != null) after.run(); });
        ft.play();
    }
}
