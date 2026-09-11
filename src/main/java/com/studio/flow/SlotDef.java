package com.studio.flow;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 槽定义（挂在节点或场景上）：订阅某个信号并执行动作。
 *
 * <p>脚本一行写法（见 {@link SignalCodec}）：</p>
 * <pre>
 *   slot = 点击 | call | | 信号实验室#onClick | 说明=逻辑层处理
 *   slot = 点击 | set  | 灯 | style | value=-fx-opacity:1;
 *   slot = 点击 | emit | 灯 | 亮灯
 *   slot = 快捷键F | toggle | 提示 | visible
 *   slot = 点击 | goto | | 森林
 *   slot = 点击 | save | | slot2
 * </pre>
 *
 * 动作一览（渲染层内置，工程师无需写界面代码）：
 * <ul>
 *   <li>{@code set}    —— 目标节点 属性=参数 value/valueVar（改完立即重绘）</li>
 *   <li>{@code toggle} —— 目标节点 属性（布尔切换，如 visible）</li>
 *   <li>{@code emit}   —— 向 目标节点（留空=场景）发出 参数中的信号名</li>
 *   <li>{@code goto}   —— 跳转参数中的场景</li>
 *   <li>{@code save}/{@code load} —— 读写参数中的存档槽位</li>
 *   <li>{@code call}   —— 转给地图工程师的逻辑类（参数=逻辑ID 或 全限定类名#方法名）</li>
 *   <li>{@code log}    —— 输出日志/提示（参数=文本）</li>
 *   <li>{@code @plugin(插件名)} —— 调用插件（参数=动作之后的所有字段，作为 args 字符串数组传入；
 *       其中写成 {@code @var(x)} 的位置会把插件返回的值写回存档变量）。</li>
 * </ul>
 */
public class SlotDef {

    /** 订阅的信号名 */
    private String signal = "";
    /** 动作：set/toggle/emit/goto/save/load/call/log */
    private String action = "";
    /** 目标节点 id（@self 表示本对象；goto/save/load/call/log 可留空） */
    private String target = "";
    /** 动作参数（属性名 / 信号名 / 场景名 / 槽位 / 逻辑ID） */
    private String arg = "";
    /** 附加参数（如 value=… / valueVar=…） */
    private final LinkedHashMap<String, String> params = new LinkedHashMap<>();

    /**
     * 插件动作的额外参数：动作写成 {@code @plugin(名)} 时，动作之后的第 3、4、5… 个
     * 字段依次放进这里（第 1、2 个分别存在 {@link #target} 与 {@link #arg}）。
     * 插件拿到的 args 数组 = target + arg + extraArgs（去掉尾部空字段）。
     */
    private final java.util.ArrayList<String> extraArgs = new java.util.ArrayList<>();

    public SlotDef() { }

    public SlotDef(String signal, String action, String target, String arg) {
        this.signal = signal;
        this.action = action;
        this.target = target;
        this.arg = arg;
    }

    public SlotDef param(String k, String v) {
        params.put(k, v);
        return this;
    }

    public String getSignal() { return signal; }
    public void setSignal(String signal) { this.signal = signal == null ? "" : signal.trim(); }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action == null ? "" : action.trim(); }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target == null ? "" : target.trim(); }

    public String getArg() { return arg; }
    public void setArg(String arg) { this.arg = arg == null ? "" : arg; }

    public Map<String, String> params() { return params; }

    /** 插件动作的额外参数列表 */
    public java.util.List<String> extraArgs() { return extraArgs; }

    /** 动作是否为插件调用（{@code @plugin(插件名)}） */
    public boolean isPlugin() { return action.toLowerCase(java.util.Locale.ROOT).startsWith("@plugin("); }

    /** 插件名/插件 ID（非插件动作返回空串） */
    public String pluginId() { return isPlugin() ? Expr.inner(action) : ""; }

    /**
     * 传给插件的参数原文数组：动作之后的所有字段，去掉尾部空字段。
     * 元素可能是字面量、也可能是 {@code @var(x)} / {@code @double(1.05)} 这类表达式。
     */
    public String[] pluginArgs() {
        java.util.ArrayList<String> all = new java.util.ArrayList<>();
        all.add(target == null ? "" : target);
        all.add(arg == null ? "" : arg);
        all.addAll(extraArgs);
        int end = all.size();
        while (end > 0 && all.get(end - 1).isBlank()) end--;
        return all.subList(0, end).toArray(new String[0]);
    }

    public SlotDef copy() {
        SlotDef c = new SlotDef(signal, action, target, arg);
        c.params.putAll(params);
        c.extraArgs.addAll(extraArgs);
        return c;
    }

    @Override
    public String toString() {
        if (isPlugin()) return "槽[" + signal + " → @plugin(" + pluginId() + ") " + String.join(" ", pluginArgs()) + "]";
        return "槽[" + signal + " → " + action + " " + target + " " + arg + "]";
    }
}
