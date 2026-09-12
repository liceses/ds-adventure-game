package com.studio.plugin.builtin;

import com.studio.flow.PluginContext;
import com.studio.util.Logs;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 编辑器<b>自带</b>插件：<b>流程控制</b>——条件选择与定时器。
 *
 * <p>信号 / 槽本身是“事件驱动”的：有信号就执行、没有信号就静静待着。缺的是三样东西：
 * <b>按条件取不同的值</b>、<b>过一会儿再做一件事</b>、<b>每隔一段时间重复做一件事</b>。
 * 这个插件把它们补齐（不需要改引擎，也不需要写 Java）：</p>
 *
 * <h3>可用插件 ID</h3>
 * <table border="1">
 *   <caption>流程控制插件</caption>
 *   <tr><th>ID</th><th>作用</th><th>槽写法</th></tr>
 *   <tr><td>{@code select}</td><td>条件选择（三目）：条件为真取 A，否则取 B</td>
 *       <td>{@code slot = 判定 | @plugin(select) | @var(够热) | 热台词 | 冷台词 | @var(当前台词)}}</td></tr>
 *   <tr><td>{@code after}</td><td>延时之后发一个信号（做演出节奏）</td>
 *       <td>{@code slot = 进场 | @plugin(after) | 1.2 | 灯亮}</td></tr>
 *   <tr><td>{@code every}</td><td>每隔一段时间发一个信号（心跳 / 闪烁 / 倒计时）</td>
 *       <td>{@code slot = 开始 | @plugin(every) | 0.4 | 闪烁 | 5}</td></tr>
 *   <tr><td>{@code stoptimer}</td><td>停止定时器（可按信号名停，也可全停）</td>
 *       <td>{@code slot = 停止 | @plugin(stoptimer) | 闪烁}}</td></tr>
 * </table>
 *
 * <h3>参数约定</h3>
 * <ul>
 *   <li>{@code select | 条件 | A | B | 输出位}：条件为“真”（true/1/是/非 0）时输出位取 A，否则取 B；
 *       也可以写少一个参数（{@code select | 条件 | A | 输出位}，假分支为空串）。</li>
 *   <li>{@code after | 秒数 | 信号名 [| 目标节点id]}：延时后向目标节点（留空 = 场景）发信号；
 *       秒数支持小数（{@code 0.5}）。</li>
 *   <li>{@code every | 秒数 | 信号名 [| 次数] [| 目标节点id]}：每隔一段时间发一次；
 *       次数留空或写 0 = 一直重复。同一个「信号名 + 目标」再次调用会先停掉旧的再重开。</li>
 *   <li>{@code stoptimer | 信号名}：停掉该信号名对应的定时器；信号名留空 = 停掉本插件建的全部定时器。</li>
 * </ul>
 *
 * <p><b>生命周期</b>：定时器在插件 {@code onDetach()}（返回剧情 / 被替换 / 关闭播放器）时会被统一停掉，
 * 所以“离开这一局之后计时器还在后台跑”这种事不会发生。</p>
 *
 * <p><b>为什么容易用错</b>：定时器发出的是<b>普通信号</b>，而槽按信号名<b>全场景订阅</b>，
 * 所以定时器用的信号名要起得唯一（例如 {@code 心跳_序11}），否则同场景里同名的槽也会被一起触发。</p>
 */
public class ControlPlugin extends BuiltinPlugin {

    /** 支持的 ID → 内部动作 */
    private enum Act { SELECT, AFTER, EVERY, STOP }

    private static final Map<String, Act> IDS = new LinkedHashMap<>();
    static {
        IDS.put("select", Act.SELECT);
        IDS.put("如果", Act.SELECT);
        IDS.put("三目", Act.SELECT);
        IDS.put("after", Act.AFTER);
        IDS.put("delay", Act.AFTER);
        IDS.put("延时", Act.AFTER);
        IDS.put("every", Act.EVERY);
        IDS.put("interval", Act.EVERY);
        IDS.put("定时", Act.EVERY);
        IDS.put("stoptimer", Act.STOP);
        IDS.put("stopevery", Act.STOP);
        IDS.put("停止定时", Act.STOP);
    }

    /** 定时器表：key = 目标 + "|" + 信号名（同类定时器只保留一个） */
    private static final Map<String, Timeline> TIMERS = new LinkedHashMap<>();

    private final Act act;

    public ControlPlugin() { this(Act.SELECT, "select"); }

    public ControlPlugin(Act act, String id) {
        super(id);
        this.act = act == null ? Act.SELECT : act;
    }

    /** 全部 ID（含别名） */
    public static List<String> ids() { return new ArrayList<>(IDS.keySet()); }

    /** 按 ID 取实例；未知返回 null */
    public static ControlPlugin of(String id) {
        if (id == null) return null;
        String k = id.trim();
        Act a = IDS.get(k.toLowerCase(Locale.ROOT));
        if (a == null) a = IDS.get(k);
        return a == null ? null : new ControlPlugin(a, k);
    }

    /** 编辑器插件目录 */
    public static List<PluginInfo> catalog() {
        List<PluginInfo> out = new ArrayList<>();
        out.add(new PluginInfo("select", "流程", "如果,三目",
                "@plugin(select) | @var(够热) | 热台词 | 冷台词 | @var(当前台词)",
                "条件选择：条件为真取第一个值，否则取第二个（等于给剧情补上 @if）"));
        out.add(new PluginInfo("after", "流程", "delay,延时",
                "@plugin(after) | 1.2 | 灯亮",
                "延时之后发一个信号（做演出节奏：先黑屏，1.2 秒后再亮灯）"));
        out.add(new PluginInfo("every", "流程", "interval,定时",
                "@plugin(every) | 0.4 | 心跳 | 5",
                "每隔一段时间发一次信号（心跳/闪烁/倒计时），次数留空 = 一直重复"));
        out.add(new PluginInfo("stoptimer", "流程", "stopevery,停止定时",
                "@plugin(stoptimer) | 心跳",
                "停掉定时器（信号名留空 = 停掉本插件建立的全部定时器）"));
        return out;
    }

    @Override
    protected String group() { return "流程"; }

    @Override
    public String description() {
        switch (act) {
            case SELECT: return "条件选择（三目）：条件为真取 A、否则取 B，结果写回输出位";
            case AFTER:  return "延时后发信号（演出节奏）";
            case EVERY:  return "每隔一段时间重复发信号（可指定次数）";
            case STOP:   return "停止定时器";
            default:     return "";
        }
    }

    @Override
    public String usage() {
        switch (act) {
            case SELECT: return "@plugin(select) | 条件 | 真值 | 假值 | @var(输出)";
            case AFTER:  return "@plugin(after) | 秒数 | 信号名 | 目标节点id（可空）";
            case EVERY:  return "@plugin(every) | 秒数 | 信号名 | 次数（可空=无限） | 目标节点id（可空）";
            case STOP:   return "@plugin(stoptimer) | 信号名（可空=全部）";
            default:     return "";
        }
    }

    // =====================================================================

    @Override
    protected void run(PluginContext ctx, String[] in, String[] out) {
        switch (act) {
            case SELECT -> doSelect(ctx, in, out);
            case AFTER -> doAfter(ctx, in);
            case EVERY -> doEvery(ctx, in);
            case STOP -> doStop(ctx, in);
            default -> { /* 不会发生 */ }
        }
    }

    /** select | 条件 | A | B | 输出位（简写：条件 | A | 输出位） */
    private void doSelect(PluginContext ctx, String[] in, String[] out) {
        if (in.length < 3) {
            warn(ctx, "参数不足（用法：" + usage() + "）");
            return;
        }
        boolean cond = truthy(in[0]);
        String a = raw(in, 1);
        String b = in.length >= 4 ? raw(in, 2) : "";
        setOut(out, cond ? a : b);
        log(ctx, (cond ? "条件为真 → " : "条件为假 → ") + setOutPreview(out));
    }

    /** after | 秒数 | 信号名 [| 目标节点id] */
    private void doAfter(PluginContext ctx, String[] in) {
        double sec = num(arg(in, 0), 1.0);
        String signal = arg(in, 1);
        String target = arg(in, 2);
        if (signal.isEmpty()) {
            warn(ctx, "after 没有给信号名（用法：" + usage() + "）");
            return;
        }
        schedule(ctx, Math.max(0.01, sec), signal, target, 1);
        log(ctx, "将在 " + trim(sec) + " 秒后发信号「" + signal + "」" + (target.isEmpty() ? "" : " → 节点 " + target));
    }

    /** every | 秒数 | 信号名 [| 次数] [| 目标节点id] */
    private void doEvery(PluginContext ctx, String[] in) {
        double sec = num(arg(in, 0), 0.5);
        String signal = arg(in, 1);
        int times = i(arg(in, 2), 0);
        String target = arg(in, 3);
        if (signal.isEmpty()) {
            warn(ctx, "every 没有给信号名（用法：" + usage() + "）");
            return;
        }
        schedule(ctx, Math.max(0.02, sec), signal, target, times);
        log(ctx, "每 " + trim(sec) + " 秒发一次「" + signal + "」"
                + (times > 0 ? "（共 " + times + " 次）" : "（无限）"));
    }

    /** stoptimer | 信号名（可空） */
    private void doStop(PluginContext ctx, String[] in) {
        String signal = arg(in, 0);
        int n = 0;
        for (Map.Entry<String, Timeline> e : new ArrayList<>(TIMERS.entrySet())) {
            if (signal.isEmpty() || e.getKey().endsWith("|" + signal)) {
                stopTimer(e.getKey());
                n++;
            }
        }
        log(ctx, signal.isEmpty() ? ("停掉全部定时器（" + n + " 个）") : ("停掉「" + signal + "」的定时器（" + n + " 个）"));
    }

    // =====================================================================
    // 定时器实现（JavaFX Timeline；onDetach 时统一清理）
    // =====================================================================

    private void schedule(PluginContext ctx, double seconds, String signal, String target, int times) {
        final String key = target + "|" + signal;
        Runnable create = () -> {
            stopTimer(key);
            final int[] left = {times};
            Timeline tl = new Timeline(new KeyFrame(Duration.seconds(seconds), e -> {
                if (ctx == null) return;
                ctx.emit(target, signal, new LinkedHashMap<>());
                if (times > 0 && --left[0] <= 0) stopTimer(key);
            }));
            if (times <= 1) tl.setCycleCount(1);
            else if (times > 1) tl.setCycleCount(times);
            else tl.setCycleCount(Timeline.INDEFINITE);
            TIMERS.put(key, tl);
            tl.play();
        };
        if (ctx != null) ctx.onUi(create);
        else create.run();
    }

    private static void stopTimer(String key) {
        Timeline tl = TIMERS.remove(key);
        if (tl != null) {
            try {
                tl.stop();
            } catch (RuntimeException e) {
                Logs.warn("[Plugin:timers] 停止定时器失败：" + e.getMessage());
            }
        }
    }

    /** 插件被移除 / 引擎关闭：停掉本插件建立的全部定时器 */
    @Override
    public void onDetach() {
        int n = TIMERS.size();
        for (String key : new ArrayList<>(TIMERS.keySet())) stopTimer(key);
        if (n > 0) Logs.info("[Plugin:timers] onDetach 停掉 " + n + " 个定时器");
    }

    // =====================================================================

    private static String trim(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    private static String setOutPreview(String[] out) {
        return out != null && out.length > 0 ? out[out.length - 1] : "";
    }
}
