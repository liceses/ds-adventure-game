package com.studio.plugin.builtin;

import com.studio.flow.PluginContext;
import com.studio.util.Logs;

import javafx.stage.Window;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 自带插件的 JavaFX 小工具：把「弹对话框 / 取宿主窗口 / 切回 FX 线程」这几件事收在一处。
 *
 * <p>插件默认就在 JavaFX 线程上被调用（信号派发发生在界面事件处理里），
 * 但工程师也可能从计时器、网络回调等后台线程调用插件，所以这里统一提供
 * {@link #onFx} 做一次“确保在 FX 线程上取结果”的兜底（最多等 30 秒，等不到就放弃并记日志，
 * 绝不会把界面线程卡死）。</p>
 */
public final class PluginFx {

    private PluginFx() { }

    /** 取一个可见窗口作为对话框宿主（拿不到返回 null，JavaFX 会用默认宿主） */
    public static Window owner() {
        try {
            for (Window w : Window.getWindows()) {
                if (w.isShowing() && w instanceof javafx.stage.Stage) return w;
            }
        } catch (RuntimeException ignored) {
            // 工具包未初始化等：交给 JavaFX 处理 null owner
        }
        return null;
    }

    /** 是否可用 JavaFX（工具包已启动） */
    public static boolean toolkitReady() {
        try {
            return javafx.application.Platform.isFxApplicationThread()
                    || !Window.getWindows().isEmpty();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 保证在 JavaFX 线程上取值：已经在 FX 线程就直接跑；
     * 否则切回去执行并等待结果（最多 30 秒）。拿不到就返回 null。
     */
    public static <T> T onFx(PluginContext ctx, Supplier<T> action) {
        if (ctx == null || ctx.isUiThread()) return action.get();
        final Object[] box = new Object[1];
        CountDownLatch latch = new CountDownLatch(1);
        ctx.onUi(() -> {
            try {
                box[0] = action.get();
            } catch (RuntimeException e) {
                Logs.warn("[Plugin] 界面操作失败：" + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) Logs.warn("[Plugin] 等待界面操作超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        @SuppressWarnings("unchecked")
        T t = (T) box[0];
        return t;
    }
}
