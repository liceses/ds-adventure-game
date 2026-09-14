package com.studio.plugin.builtin;

import com.studio.flow.PluginContext;
import com.studio.util.Logs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 编辑器<b>自带</b>插件：<b>节点信号 / 槽的总开关</b>。
 *
 * <p>做剧情图时经常需要“临时把某个节点的交互关掉/打开”，例如：</p>
 * <ul>
 *   <li>过场演出期间禁止玩家点按钮（把那一堆按钮的信号关掉），演出结束再打开；</li>
 *   <li>某个只能点一次的选项点过之后关掉它的信号；</li>
 *   <li>某个提示节点上的槽先别跑，等玩家做了某事再启用。</li>
 * </ul>
 *
 * <p>它对应节点上的两个新属性（都默认开，落盘只在关掉时写）：</p>
 * <pre>
 *   signalsEnabled = false    ← 本节点的信号不触发（鼠标点击/键盘/按钮信号都算）
 *   slotsEnabled   = false    ← 挂在本节点上的槽不执行（场景级槽不受影响）
 * </pre>
 *
 * <h3>可用插件 ID</h3>
 * <pre>
 *   slot = 演出开始 | @plugin(switch) | off | 按钮_存档 | signals    ← 关掉某个节点的信号
 *   slot = 演出结束 | @plugin(switch) | on  | 按钮_存档 | signals
 *   slot = 点过之后 | @plugin(switch) | off | 选项_一次           | all      ← 信号+槽一起关
 *   slot = 恢复     | @plugin(switch) | toggle | 选项_一次         | slots    ← 取反
 * </pre>
 * 参数：{@code 开关(on|off|toggle/开|关|取反) | 节点id | signals|slots|all（可空=all）}。
 * 也可以直接写在节点属性里（编辑器节点属性窗口有对应复选框），运行时用本插件随时改。
 */
public class SwitchPlugin extends BuiltinPlugin {

    private final String id;

    private static final Map<String, String> IDS = new LinkedHashMap<>();
    static {
        IDS.put("switch", "switch");
        IDS.put("开关", "switch");        // 中文别名：@plugin(开关)
        IDS.put("节点开关", "switch");
        IDS.put("enable", "switch");      // 顺手写法：@plugin(enable) / @plugin(disable) 后面照样写 on/off
        IDS.put("disable", "switch");
        IDS.put("启用", "switch");
        IDS.put("禁用", "switch");
    }

    public SwitchPlugin() { this("switch"); }

    public SwitchPlugin(String id) { super(id == null ? "switch" : id); this.id = id == null ? "switch" : id; }

    public static List<String> ids() { return new ArrayList<>(IDS.keySet()); }

    public static SwitchPlugin of(String id) {
        if (id == null) return null;
        String k = id.trim();
        if (IDS.get(k.toLowerCase(Locale.ROOT)) == null && IDS.get(k) == null) return null;
        return new SwitchPlugin(k);
    }

    // =====================================================================

    @Override
    protected String group() { return "系统"; }

    @Override
    public String description() {
        return "开关某个节点的「信号」或「槽」：off 之后它的信号不再触发、挂在自己身上的槽不再执行"
                + "（场景级槽不受影响）；对应节点属性 signalsEnabled / slotsEnabled";
    }

    @Override
    public String usage() {
        return "@plugin(switch) | on|off|toggle | 节点id | signals|slots|all（可空=all）";
    }

    @Override
    protected void run(PluginContext ctx, String[] in, String[] out) {
        String what = arg(in, 0);
        String nodeId = arg(in, 1);
        String scope = arg(in, 2);
        if (what.isEmpty() || nodeId.isEmpty()) {
            warn(ctx, "参数不足（用法：" + usage() + "）");
            return;
        }
        String target = scope.isEmpty() ? "all" : scope;
        boolean signals = is(target, "signals", "signal", "信号", "all", "全部", "所有", "*", "");
        boolean slots = is(target, "slots", "slot", "槽", "all", "全部", "所有", "*", "");
        if (!signals && !slots) {
            warn(ctx, "第三个参数只能是 signals（信号）/ slots（槽）/ all（两个都改）");
            return;
        }
        Boolean on = switch (what.toLowerCase(Locale.ROOT)) {
            case "on", "true", "1", "开", "开启", "启用", "打开" -> Boolean.TRUE;
            case "off", "false", "0", "关", "关闭", "禁用", "禁用掉" -> Boolean.FALSE;
            case "toggle", "flip", "取反", "切换", "反转" -> null;   // 取反：按当前值决定
            default -> null;
        };
        boolean toggle = is(what, "toggle", "flip", "取反", "切换", "反转");
        if (on == null && !toggle) {
            warn(ctx, "第一个参数只能是 on / off / toggle（也认 开/关/取反）");
            return;
        }
        if (ctx == null) return;
        StringBuilder done = new StringBuilder();
        if (signals) {
            boolean value = toggle ? !current(ctx, nodeId, "signalsEnabled") : on;
            ctx.setProperty(nodeId, "signalsEnabled", value ? "true" : "false");
            done.append("信号=").append(value ? "开" : "关");
        }
        if (slots) {
            boolean value = toggle ? !current(ctx, nodeId, "slotsEnabled") : on;
            ctx.setProperty(nodeId, "slotsEnabled", value ? "true" : "false");
            if (done.length() > 0) done.append("　");
            done.append("槽=").append(value ? "开" : "关");
        }
        log(ctx, "🎚 节点「" + nodeId + "」" + done);
    }

    /** 读当前开关状态（没设置过 = 默认开） */
    private static boolean current(PluginContext ctx, String nodeId, String prop) {
        String v = ctx.property(nodeId, prop);
        if (v == null || v.isBlank()) return true;
        return !(v.equalsIgnoreCase("false") || v.equals("0") || v.equals("否") || v.equals("关"));
    }

    /** 编辑器插件目录 */
    public static java.util.List<PluginInfo> catalog() {
        java.util.List<PluginInfo> out = new ArrayList<>();
        out.add(new PluginInfo("switch", "系统", "开关,节点开关,启用,禁用,enable,disable",
                "@plugin(switch) | off | 节点id | signals|slots|all",
                "开关某个节点的信号/槽：off 之后它的信号不再触发、自己身上的槽不再执行（场景级槽不受影响）；"
                        + "也可直接写在节点属性里（signalsEnabled / slotsEnabled）"));
        return out;
    }

    @Override
    public String toString() { return "SwitchPlugin(" + id + ")"; }
}
