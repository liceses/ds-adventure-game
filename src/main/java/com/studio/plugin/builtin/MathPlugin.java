package com.studio.plugin.builtin;

import com.studio.flow.Expr;
import com.studio.flow.PluginContext;
import com.studio.flow.SlotPlugin;
import com.studio.flow.VarType;
import com.studio.model.StoryNode;
import com.studio.util.Logs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 编辑器<b>自带</b>插件：存档变量的基本运算（随编辑器/读取器一起发行，无需工程师编译）。
 *
 * <h3>通用参数约定</h3>
 * <p>槽写成 {@code 信号名 | @plugin(插件ID) | 参数…}，动作之后的所有字段按顺序解析成
 * 字符串数组传入。<b>最后一个参数是“输出位置”，前面的都是输入</b>，例如：</p>
 * <pre>
 *   # num2 = num1 + 1.05
 *   slot = 点击 | @plugin(add) | @var(num1) | @double(1.05) | @var(num2)
 *
 *   # 直接把两个变量相加存回第二个变量：b = a + b
 *   slot = 点击 | @plugin(add) | @var(a) | @var(b)
 *
 *   # 取整后写回同一变量：n = round(n)
 *   slot = 按键R | @plugin(round) | @var(n)
 * </pre>
 *
 * <h3>可用插件 ID</h3>
 * <ul>
 *   <li>二元（结果写回最后一个参数）：{@code add} {@code sub} {@code mul} {@code div}
 *       {@code mod} {@code pow} {@code min} {@code max}</li>
 *   <li>一元：{@code abs} {@code round} {@code floor} {@code ceil} {@code neg}</li>
 *   <li>赋值类：{@code set}（把第一个参数复制到最后一个）、{@code inc}（+1）、{@code dec}（-1）</li>
 * </ul>
 * <p>所有运算对非法输入都<b>宽容处理</b>（按 0 计），除零返回 0，不会抛异常中断剧情。</p>
 */
public class MathPlugin implements SlotPlugin {

    /** 支持的运算 */
    public enum Op {
        ADD, SUB, MUL, DIV, MOD, POW, MIN, MAX,     // 二元折叠
        ABS, ROUND, FLOOR, CEIL, NEG,               // 一元
        SET, INC, DEC                               // 赋值类
    }

    private static final Map<String, Op> IDS = new LinkedHashMap<>();
    static {
        IDS.put("add", Op.ADD);
        IDS.put("sub", Op.SUB);
        IDS.put("mul", Op.MUL);
        IDS.put("div", Op.DIV);
        IDS.put("mod", Op.MOD);
        IDS.put("pow", Op.POW);
        IDS.put("min", Op.MIN);
        IDS.put("max", Op.MAX);
        IDS.put("abs", Op.ABS);
        IDS.put("round", Op.ROUND);
        IDS.put("floor", Op.FLOOR);
        IDS.put("ceil", Op.CEIL);
        IDS.put("neg", Op.NEG);
        IDS.put("set", Op.SET);
        IDS.put("inc", Op.INC);
        IDS.put("dec", Op.DEC);
    }

    private final Op op;
    private final String id;

    /** 无参构造：默认 add（plugins.ini 直接写类名时也能用） */
    public MathPlugin() { this(Op.ADD, "add"); }

    public MathPlugin(Op op, String id) {
        this.op = op;
        this.id = id;
    }

    /** 全部自带插件 ID */
    public static List<String> ids() { return new ArrayList<>(IDS.keySet()); }

    /** 按 ID 取插件；未知 ID 返回 null */
    public static MathPlugin of(String id) {
        if (id == null) return null;
        Op o = IDS.get(id.trim().toLowerCase(Locale.ROOT));
        return o == null ? null : new MathPlugin(o, id.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public String name() { return "存档变量运算 · " + id; }

    @Override
    public String description() {
        switch (op) {
            case ADD: return "加法：把最后一个参数写成前面所有参数之和";
            case SUB: return "减法：从第一个参数依次减后面的参数，结果写回最后一个参数";
            case MUL: return "乘法：把最后一个参数写成前面所有参数的积";
            case DIV: return "除法：从第一个参数依次除以后面的参数（除零按 0 处理）";
            case MOD: return "取余：a mod b，结果写回最后一个参数";
            case POW: return "幂：a 的 b 次方，结果写回最后一个参数";
            case MIN: return "取最小值，结果写回最后一个参数";
            case MAX: return "取最大值，结果写回最后一个参数";
            case ABS: return "绝对值";
            case ROUND: return "四舍五入取整";
            case FLOOR: return "向下取整";
            case CEIL: return "向上取整";
            case NEG: return "取相反数";
            case SET: return "赋值：把第一个参数复制到最后一个参数";
            case INC: return "自增 1";
            case DEC: return "自减 1";
            default: return "";
        }
    }

    @Override
    public String usage() {
        if (isUnary()) return "@plugin(" + id + ") | @var(变量)";
        return "@plugin(" + id + ") | @var(变量1) | @double(1.05) | @var(结果变量)";
    }

    private boolean isUnary() {
        return op == Op.ABS || op == Op.ROUND || op == Op.FLOOR || op == Op.CEIL || op == Op.NEG
                || op == Op.INC || op == Op.DEC;
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
                case SET: out[last] = args[0]; break;
                case INC: out[last] = fmt(num(args[0]) + 1); break;
                case DEC: out[last] = fmt(num(args[0]) - 1); break;
                case ABS: out[last] = fmt(Math.abs(num(args[0]))); break;
                case ROUND: out[last] = fmt(Math.rint(num(args[0]))); break;
                case FLOOR: out[last] = fmt(Math.floor(num(args[0]))); break;
                case CEIL: out[last] = fmt(Math.ceil(num(args[0]))); break;
                case NEG: out[last] = fmt(-num(args[0])); break;
                default: {
                    double acc = num(args[0]);
                    for (int i = 1; i <= last - 1; i++) {
                        double v = num(args[i]);
                        acc = switch (op) {
                            case ADD -> acc + v;
                            case SUB -> acc - v;
                            case MUL -> acc * v;
                            case DIV -> v == 0 ? 0 : acc / v;
                            case MOD -> v == 0 ? 0 : acc % v;
                            case POW -> Math.pow(acc, v);
                            case MIN -> Math.min(acc, v);
                            case MAX -> Math.max(acc, v);
                            default -> acc;
                        };
                    }
                    if (last == 0) {
                        out[0] = fmt(acc);
                    } else {
                        out[last] = fmt(acc);
                    }
                }
            }
        } catch (RuntimeException e) {
            Logs.warn("[Plugin:" + id + "] 运算异常：" + e.getMessage());
            return args;
        }
        return out;
    }

    /** 参数转数字：非法（含 "abc"、空、表达式未解析）一律按 0 处理 */
    private static double num(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        String s = raw.trim();
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ignored) {
            // 尝试强制转换（容忍 "12px"、"是" 之类）
            String cast = VarType.DOUBLE.cast(s);
            try {
                return Double.parseDouble(cast);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    private static String fmt(double v) { return StoryNode.trimDouble(v); }

    /** 供编辑器展示：某参数是否是变量引用 */
    public static boolean isVarArg(String arg) { return Expr.isVarRef(arg); }

    /** 编辑器插件目录（选择器 / 手册自动生成用） */
    public static java.util.List<PluginInfo> catalog() {
        java.util.List<PluginInfo> out = new java.util.ArrayList<>();
        out.add(new PluginInfo("add", "运算", "加", "@plugin(add) | @var(a) | @var(b) | @var(和)", "二元加法：把动作之后的输入相加，结果写到最后一位"));
        out.add(new PluginInfo("sub", "运算", "减", "@plugin(sub) | @var(a) | @var(b) | @var(差)", "二元减法：a 减 b"));
        out.add(new PluginInfo("mul", "运算", "乘", "@plugin(mul) | @var(a) | @var(b) | @var(积)", "二元乘法：把输入相乘"));
        out.add(new PluginInfo("div", "运算", "除", "@plugin(div) | @var(a) | @var(b) | @var(商)", "二元除法：除数为 0 时返回 0（不报错）"));
        out.add(new PluginInfo("mod", "运算", "取余", "@plugin(mod) | @var(a) | @var(b) | @var(余)", "取余：a mod b"));
        out.add(new PluginInfo("pow", "运算", "幂", "@plugin(pow) | @var(a) | @var(b) | @var(幂)", "幂运算：a 的 b 次方"));
        out.add(new PluginInfo("min", "运算", "最小", "@plugin(min) | @var(a) | @var(b) | @var(最小)", "取输入的较小值（常用来做上限）"));
        out.add(new PluginInfo("max", "运算", "最大", "@plugin(max) | @var(a) | @var(b) | @var(最大)", "取输入的较大值（常用来做下限）"));
        out.add(new PluginInfo("abs", "运算", "绝对值", "@plugin(abs) | @var(a) | @var(绝对值)", "绝对值"));
        out.add(new PluginInfo("round", "运算", "四舍五入", "@plugin(round) | @var(a) | @var(结果)", "四舍五入取整"));
        out.add(new PluginInfo("floor", "运算", "向下取整", "@plugin(floor) | @var(a) | @var(结果)", "向下取整"));
        out.add(new PluginInfo("ceil", "运算", "向上取整", "@plugin(ceil) | @var(a) | @var(结果)", "向上取整"));
        out.add(new PluginInfo("neg", "运算", "取负", "@plugin(neg) | @var(a) | @var(结果)", "取相反数"));
        out.add(new PluginInfo("set", "运算", "赋值", "@plugin(set) | 值 | @var(目标变量)", "把第一个参数复制到输出位（重置/赋常量常用）"));
        out.add(new PluginInfo("inc", "运算", "加一", "@plugin(inc) | @var(a) | @var(a)", "自增 1（点一次加一）"));
        out.add(new PluginInfo("dec", "运算", "减一", "@plugin(dec) | @var(a) | @var(a)", "自减 1"));
        return out;
    }

    @Override
    public String toString() { return "MathPlugin(" + id + ")"; }
}
