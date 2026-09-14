package com.studio.plugin.builtin;

import com.studio.flow.PluginContext;
import com.studio.flow.VarType;
import com.studio.flow.SlotPlugin;
import com.studio.util.Logs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 编辑器<b>自带</b>插件：存档变量的<b>逻辑运算</b>与<b>大小比较</b>
 * （随编辑器/读取器发行，脚本里直接写 ID，无需注册、无需外部文件）。
 *
 * <h3>通用参数约定（与 {@link MathPlugin} 一致）</h3>
 * <p><b>最后一个参数是“输出位置”，前面的都是输入</b>：</p>
 * <pre>
 *   # 逻辑乘（与）：灯3 = 灯1 并且 灯2
 *   slot = 刷新 | @plugin(and) | @var(灯1) | @var(灯2) | @var(灯3)
 *
 *   # 逻辑加（或）：任意一盏亮
 *   slot = 刷新 | @plugin(or)  | @var(灯1) | @var(灯2) | @var(任意亮)
 *
 *   # 逻辑非：灯1 = 非 灯1（点一下开关翻转）
 *   slot = 开关1点击 | @plugin(not) | @var(灯1) | @var(灯1)
 *
 *   # 大小比较：两盏都亮 = (点亮数 &gt; 1)
 *   slot = 刷新 | @plugin(gt)  | @var(点亮数) | @int(1) | @var(两盏都亮)
 * </pre>
 *
 * <h3>可用插件 ID</h3>
 * <ul>
 *   <li>逻辑运算：{@code and}（逻辑乘/与）、{@code or}（逻辑加/或）、
 *       {@code xor}（异或）、{@code not}（逻辑非，一元）</li>
 *   <li>大小比较：{@code gt}(&gt;)、{@code lt}(&lt;)、{@code ge}(&gt;=)、{@code le}(&lt;=)、
 *       {@code eq}(==)、{@code ne}(!=)</li>
 * </ul>
 * <p>结果统一写成 {@code true} / {@code false}，配合 {@code bool} 类型的存档变量使用；
 * 也兼容数值形式：{@code 0} 为假、非 0 为真。</p>
 *
 * <p><b>宽容处理</b>：参数缺失、类型不匹配都不会抛异常（按“假”或 0 处理并记日志），
 * 因此剧情不会因为一句写错的槽而中断。</p>
 */
public class LogicPlugin implements SlotPlugin {

    /** 支持的运算 */
    public enum Op {
        AND, OR, XOR, NOT,          // 逻辑
        GT, LT, GE, LE, EQ, NE      // 比较
    }

    private static final Map<String, Op> IDS = new LinkedHashMap<>();
    static {
        IDS.put("and", Op.AND);
        IDS.put("or", Op.OR);
        IDS.put("xor", Op.XOR);
        IDS.put("not", Op.NOT);
        IDS.put("gt", Op.GT);
        IDS.put("lt", Op.LT);
        IDS.put("ge", Op.GE);
        IDS.put("le", Op.LE);
        IDS.put("eq", Op.EQ);
        IDS.put("ne", Op.NE);
    }

    private final Op op;
    private final String id;

    /** 无参构造：默认 and（plugins.ini 直接写类名时也能用） */
    public LogicPlugin() { this(Op.AND, "and"); }

    public LogicPlugin(Op op, String id) {
        this.op = op;
        this.id = id;
    }

    /** 全部逻辑插件 ID */
    public static List<String> ids() { return new ArrayList<>(IDS.keySet()); }

    /** 按 ID 取插件；未知 ID 返回 null */
    public static LogicPlugin of(String id) {
        if (id == null) return null;
        Op o = IDS.get(id.trim().toLowerCase(Locale.ROOT));
        return o == null ? null : new LogicPlugin(o, id.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public String name() { return "存档变量逻辑运算 · " + id; }

    @Override
    public String description() {
        switch (op) {
            case AND: return "逻辑乘（与）：所有输入都为真 → true，结果写回最后一个参数";
            case OR: return "逻辑加（或）：任一输入为真 → true，结果写回最后一个参数";
            case XOR: return "逻辑异或：真值个数为奇数 → true";
            case NOT: return "逻辑非：把输入取反（一元）";
            case GT: return "大小比较：第一个参数 > 第二个参数 → true";
            case LT: return "大小比较：第一个参数 < 第二个参数 → true";
            case GE: return "大小比较：第一个参数 >= 第二个参数 → true";
            case LE: return "大小比较：第一个参数 <= 第二个参数 → true";
            case EQ: return "相等比较：数值相等或文本相同 → true";
            case NE: return "不等比较：数值不同或文本不同 → true";
            default: return "";
        }
    }

    @Override
    public String usage() {
        if (op == Op.NOT) return "@plugin(not) | @var(变量)";
        if (isCompare()) return "@plugin(" + id + ") | @var(变量A) | @int(1) | @var(结果bool变量)";
        return "@plugin(" + id + ") | @var(变量A) | @var(变量B) | @var(结果bool变量)";
    }

    private boolean isCompare() {
        return op == Op.GT || op == Op.LT || op == Op.GE || op == Op.LE || op == Op.EQ || op == Op.NE;
    }

    // =====================================================================

    @Override
    public String[] execute(PluginContext ctx, String[] args) {
        if (args == null || args.length == 0) {
            Logs.warn("[Plugin:" + id + "] 没有参数（用法：" + usage() + "）");
            return args;
        }
        String[] out = Arrays.copyOf(args, args.length);
        int last = args.length - 1;
        try {
            switch (op) {
                case NOT -> out[last] = bool(!truthy(args[0]));
                case AND -> out[last] = bool(fold(args, last, true));
                case OR -> out[last] = bool(fold(args, last, false));
                case XOR -> out[last] = bool(xor(args, last));
                default -> {
                    // 比较：输入是前两个参数，输出写最后一位
                    if (args.length < 2) {
                        Logs.warn("[Plugin:" + id + "] 比较运算需要至少 2 个参数（用法：" + usage() + "）");
                        return args;
                    }
                    out[last] = bool(compare(args[0], args[1]));
                }
            }
        } catch (RuntimeException e) {
            Logs.warn("[Plugin:" + id + "] 运算异常：" + e.getMessage());
            return args;
        }
        return out;
    }

    /** 逻辑乘/逻辑加：对前 last 个参数做折叠（last = 输出位下标） */
    private boolean fold(String[] args, int last, boolean isAnd) {
        if (last == 0) return truthy(args[0]);       // 只有一个参数：原样作为结果
        boolean acc = isAnd;
        for (int i = 0; i < last; i++) {
            acc = isAnd ? (acc && truthy(args[i])) : (acc || truthy(args[i]));
        }
        return acc;
    }

    /** 异或：真值个数为奇数 → true（两个输入时就是“不同为真”） */
    private boolean xor(String[] args, int last) {
        boolean acc = false;
        int n = Math.max(1, last);
        for (int i = 0; i < n; i++) {
            acc ^= truthy(args[i]);
        }
        return acc;
    }

    /** 比较：能当数字就按数字比，否则按文本比 */
    private boolean compare(String a, String b) {
        Double da = number(a);
        Double db = number(b);
        if (da != null && db != null) {
            int c = Double.compare(da, db);
            return switch (op) {
                case GT -> c > 0;
                case LT -> c < 0;
                case GE -> c >= 0;
                case LE -> c <= 0;
                case EQ -> c == 0;
                case NE -> c != 0;
                default -> false;
            };
        }
        String sa = a == null ? "" : a.trim();
        String sb = b == null ? "" : b.trim();
        int c = sa.compareTo(sb);
        return switch (op) {
            case GT -> c > 0;
            case LT -> c < 0;
            case GE -> c >= 0;
            case LE -> c <= 0;
            case EQ -> c == 0;
            case NE -> c != 0;
            default -> false;
        };
    }

    /** 真值判定：true/1/是/yes/on/真 或 非 0 数值 → 真（其余为假，不报错） */
    private static boolean truthy(String raw) {
        if (raw == null) return false;
        String s = raw.trim();
        if (s.isEmpty()) return false;
        String low = s.toLowerCase(Locale.ROOT);
        // 与 VarType 保持同一套写法：true/1/是/yes/on/真/开/亮 … → 真，其余按“非 0 为真”
        Boolean b = VarType.boolish(s);
        if (b != null) return b;
        Double d = number(s);
        return d != null && d != 0;
    }

    private static Double number(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String bool(boolean b) { return b ? "true" : "false"; }

    /** 编辑器插件目录（选择器 / 手册自动生成用） */
    public static java.util.List<PluginInfo> catalog() {
        java.util.List<PluginInfo> out = new java.util.ArrayList<>();
        out.add(new PluginInfo("and", "逻辑", "与,逻辑乘", "@plugin(and) | @var(灯1) | @var(灯2) | @var(都亮)", "逻辑与：所有输入都为真 → true"));
        out.add(new PluginInfo("or", "逻辑", "或,逻辑加", "@plugin(or) | @var(a) | @var(b) | @var(任一)", "逻辑或：任一输入为真 → true"));
        out.add(new PluginInfo("xor", "逻辑", "异或", "@plugin(xor) | @var(a) | @var(b) | @var(不同)", "逻辑异或：真值个数为奇数 → true"));
        out.add(new PluginInfo("not", "逻辑", "非,取反", "@plugin(not) | @var(a) | @var(a)", "逻辑非：点一下翻转（开关常用）"));
        out.add(new PluginInfo("gt", "逻辑", "大于", "@plugin(gt) | @var(a) | @int(1) | @var(结果)", "大小比较 a > b"));
        out.add(new PluginInfo("lt", "逻辑", "小于", "@plugin(lt) | @var(a) | @int(1) | @var(结果)", "大小比较 a < b"));
        out.add(new PluginInfo("ge", "逻辑", "大于等于", "@plugin(ge) | @var(a) | @int(1) | @var(结果)", "大小比较 a ≥ b"));
        out.add(new PluginInfo("le", "逻辑", "小于等于", "@plugin(le) | @var(a) | @int(1) | @var(结果)", "大小比较 a ≤ b"));
        out.add(new PluginInfo("eq", "逻辑", "相等", "@plugin(eq) | @var(a) | @var(b) | @var(结果)", "相等比较（数字按数值比，否则按文本比）"));
        out.add(new PluginInfo("ne", "逻辑", "不等", "@plugin(ne) | @var(a) | @var(b) | @var(结果)", "不等比较"));
        return out;
    }

    @Override
    public String toString() { return "LogicPlugin(" + id + ")"; }
}
