package com.studio.plugin.builtin;

import com.studio.flow.PluginContext;
import com.studio.flow.SignalEvent;
import com.studio.model.SaveVarDef;
import com.studio.util.Logs;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 编辑器<b>自带</b>插件：<b>调试</b>——把“看不见的东西”摊开给做地图的人看。
 *
 * <p>做剧情图最容易卡住的不是写不出效果，而是<b>不知道现在的状态是什么</b>：
 * 变量到底变成几了？这个信号到底被哪些槽响应了？为什么点按钮没反应？
 * 这一族插件专门解决这个：</p>
 *
 * <h3>可用插件 ID</h3>
 * <pre>
 *   # 一键打印：当前场景 + 全部存档变量（顶部提示 + 控制台日志）
 *   slot = 看状态 | @plugin(debug)
 *
 *   # 跟踪信号：开/关。开启后每一次信号派发都会打印「信号名 ← 来源」
 *   slot = 开始跟踪 | @plugin(trace) | 开
 *   slot = 停止跟踪 | @plugin(trace) | 关
 *
 *   # 把变量表导出成文件（写到地图文件夹，方便离线对比）
 *   slot = 导出 | @plugin(dump) | 变量快照.txt
 * </pre>
 *
 * <p>调试插件只读不写（除了导出的文件），可以放心留在成品地图里 ——
 * 也可以只放在测试用的按钮上。</p>
 */
public class DebugPlugin extends BuiltinPlugin {

    private enum Act { DEBUG, TRACE, DUMP }

    private static final Map<String, Act> IDS = new LinkedHashMap<>();
    static {
        IDS.put("debug", Act.DEBUG);   IDS.put("变量表", Act.DEBUG); IDS.put("状态", Act.DEBUG);
        IDS.put("trace", Act.TRACE);   IDS.put("跟踪", Act.TRACE);
        IDS.put("dump", Act.DUMP);     IDS.put("导出", Act.DUMP);
    }

    private final Act act;

    /** 是否正在跟踪信号（静态：一个播放器里只需要一份开关） */
    private static volatile boolean tracing = false;

    public DebugPlugin() { this(Act.DEBUG, "debug"); }

    public DebugPlugin(Act act, String id) {
        super(id);
        this.act = act == null ? Act.DEBUG : act;
    }

    public static List<String> ids() { return new ArrayList<>(IDS.keySet()); }

    public static DebugPlugin of(String id) {
        if (id == null) return null;
        String k = id.trim();
        Act a = IDS.get(k.toLowerCase(Locale.ROOT));
        if (a == null) a = IDS.get(k);
        return a == null ? null : new DebugPlugin(a, k);
    }

    /** 编辑器插件目录 */
    public static List<PluginInfo> catalog() {
        List<PluginInfo> out = new ArrayList<>();
        out.add(new PluginInfo("debug", "调试", "变量表,状态", "@plugin(debug)",
                "一键打印当前场景与全部存档变量（顶部提示 + 日志），排查“变量到底变成几了”"));
        out.add(new PluginInfo("trace", "调试", "跟踪", "@plugin(trace) | 开",
                "信号跟踪开关：开启后每次信号派发都会打印「信号名 ← 来源」，专治“点了没反应/触发错槽”"));
        out.add(new PluginInfo("dump", "调试", "导出", "@plugin(dump) | 变量快照.txt",
                "把变量表导出到地图文件夹里的文本文件（方便离线对比/交作业留档）"));
        return out;
    }

    @Override
    protected String group() { return "调试"; }

    @Override
    public String description() {
        switch (act) {
            case DEBUG: return "打印当前场景与全部存档变量";
            case TRACE: return "开关信号跟踪（打印每一次信号派发）";
            case DUMP:  return "把变量表导出成文本文件";
            default:    return "";
        }
    }

    @Override
    public String usage() {
        switch (act) {
            case DEBUG: return "@plugin(debug)";
            case TRACE: return "@plugin(trace) | 开|关";
            case DUMP:  return "@plugin(dump) | 文件名.txt（可空）";
            default:    return "";
        }
    }

    // =====================================================================

    @Override
    protected void run(PluginContext ctx, String[] in, String[] out) {
        switch (act) {
            case DEBUG -> {
                String text = snapshot(ctx);
                log(ctx, "\n" + text);
                toast(ctx, firstLine(text));
            }
            case TRACE -> {
                boolean on = count(in) < 1 || truthy(arg(in, 0)) || is(arg(in, 0), "开", "on", "start");
                tracing = on;
                if (ctx != null) {
                    if (on) ctx.subscribe("*");
                    else ctx.unsubscribe("*");
                }
                log(ctx, on ? "信号跟踪：已开启（每次信号都会打印）" : "信号跟踪：已关闭");
                toast(ctx, on ? "已开启信号跟踪（看控制台）" : "已关闭信号跟踪");
            }
            case DUMP -> {
                String name = arg(in, 0);
                if (name.isEmpty()) name = "变量快照.txt";
                File dir = ctx == null ? null : ctx.mapDir();
                File file = new File(dir == null ? new File(".") : dir, name);
                try {
                    Files.write(file.toPath(), snapshot(ctx).getBytes(StandardCharsets.UTF_8));
                    log(ctx, "变量表已导出：" + file.getAbsolutePath());
                    toast(ctx, "已导出：" + file.getName());
                } catch (Exception e) {
                    warn(ctx, "导出失败：" + e.getMessage());
                }
            }
            default -> { /* 不会发生 */ }
        }
    }

    /** 跟踪开启时：每次信号都打印（引擎在插件锁内、JavaFX 线程上回调） */
    @Override
    public void onSignal(PluginContext ctx, SignalEvent event) {
        if (!tracing || event == null) return;
        String src = event.sourceId() == null || event.sourceId().isBlank() ? "场景" : ("节点 " + event.sourceId());
        Logs.info("[Trace] 信号「" + event.signal() + "」← " + src);
    }

    /** 变量表文本 */
    private static String snapshot(PluginContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 剧情状态快照 ===\n");
        if (ctx != null) {
            sb.append("当前场景: ").append(ctx.scene()).append('\n');
            File map = ctx.mapDir();
            if (map != null) sb.append("地图目录: ").append(map.getAbsolutePath()).append('\n');
        } else {
            sb.append("（没有插件上下文：只输出了静态信息）\n");
        }
        if (ctx != null && ctx.host() != null) {
            List<SaveVarDef> defs = ctx.host().saveVarDefs();
            if (defs != null && !defs.isEmpty()) {
                sb.append("--- 存档变量 ---\n");
                for (SaveVarDef d : defs) {
                    sb.append("  ").append(d.getName()).append("（").append(d.getType()).append("） = ")
                      .append(ctx.var(d.getName(), "（未设置）")).append('\n');
                }
            }
            sb.append("存档槽位: ");
            List<String> slots = ctx.listSaves();
            sb.append(slots.isEmpty() ? "（无）" : String.join("、", slots)).append('\n');
        }
        return sb.toString();
    }

    private static String firstLine(String text) {
        for (String line : text.split("\n")) {
            if (!line.isBlank() && !line.startsWith("===")) return line.trim();
        }
        return "状态已打印（看控制台）";
    }
}
