package com.studio.plugin.builtin;

import com.studio.flow.PluginContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 编辑器<b>自带</b>插件：<b>文本处理</b>——拼接、替换、截取、大小写、长度、补齐……
 *
 * <p>表达式（{@code @var/@node/@param}）能取值，但“值取出来之后还要加工一下”的活儿它做不了：
 * 把两个人名拼成一句话、把长文本截断、把金额加上千分位、做一条血条。
 * 这一族插件就是干这个的，<b>结果同样写回最后一个参数</b>，所以可以链式使用。</p>
 *
 * <h3>可用插件 ID</h3>
 * <pre>
 *   slot = 拼   | @plugin(concat)  | @var(姓) | @var(名) | @var(全名)
 *   slot = 大写 | @plugin(upper)   | @var(名字) | @var(名字)
 *   slot = 小写 | @plugin(lower)   | @var(名字) | @var(名字)
 *   slot = 去空 | @plugin(trim)    | @var(名字) | @var(名字)
 *   slot = 长度 | @plugin(len)     | @var(台词) | @var(字数)
 *   slot = 截断 | @plugin(sub)     | @var(台词) | 0 | 12 | @var(开头)
 *   slot = 替换 | @plugin(replace) | @var(台词) | 你 | 您 | @var(礼貌台词)
 *   slot = 补齐 | @plugin(pad)     | @var(编号) | 4 | 0 | @var(编号4位)
 *   slot = 重复 | @plugin(repeat)  | -          | 24 | @var(分隔线)
 *   slot = 切分 | @plugin(split)   | @var(列表) | , | 1 | @var(第二项)
 *   slot = 取整 | @plugin(num)     | @var(分数) | 2 | @var(两位小数)
 *   slot = 金额 | @plugin(money)   | @var(金币) | @var(带千分位)
 *   slot = 百分比| @plugin(percent)| @var(进度) | 1 | @var(百分比文本)
 *   slot = 时长 | @plugin(duration)| @var(秒)   | @var(时分秒)
 *   slot = 血条 | @plugin(bar)     | @var(血量) | 100 | 12 | @var(血条文本)
 * </pre>
 *
 * <p>参数约定：{@code 插件ID | 输入… | 输出位}（输出位永远是最后一个参数）。
 * 缺参数、类型不对都不会报错：按空串/0 处理，尽量给一个合理结果。</p>
 */
public class TextPlugin extends BuiltinPlugin {

    /** 支持的文本/格式操作 */
    public enum Op {
        CONCAT, UPPER, LOWER, TRIM, LEN, SUB, REPLACE, PAD, REPEAT, SPLIT,
        NUM, MONEY, PERCENT, DURATION, BAR
    }

    private static final Map<String, Op> IDS = new LinkedHashMap<>();
    static {
        IDS.put("concat", Op.CONCAT);   IDS.put("拼接", Op.CONCAT);   IDS.put("joinstr", Op.CONCAT);
        IDS.put("upper", Op.UPPER);     IDS.put("大写", Op.UPPER);
        IDS.put("lower", Op.LOWER);     IDS.put("小写", Op.LOWER);
        IDS.put("trim", Op.TRIM);       IDS.put("去空格", Op.TRIM);
        IDS.put("len", Op.LEN);         IDS.put("长度", Op.LEN);      IDS.put("strlen", Op.LEN);
        IDS.put("sub", Op.SUB);         IDS.put("截取", Op.SUB);      IDS.put("substr", Op.SUB);
        IDS.put("截断", Op.SUB);
        IDS.put("replace", Op.REPLACE); IDS.put("替换", Op.REPLACE);
        IDS.put("pad", Op.PAD);         IDS.put("补齐", Op.PAD);
        IDS.put("repeat", Op.REPEAT);   IDS.put("重复", Op.REPEAT);
        IDS.put("split", Op.SPLIT);     IDS.put("切分", Op.SPLIT);
        IDS.put("num", Op.NUM);         IDS.put("小数", Op.NUM);      IDS.put("fmtnum", Op.NUM);
        IDS.put("money", Op.MONEY);     IDS.put("金额", Op.MONEY);
        IDS.put("percent", Op.PERCENT); IDS.put("百分比", Op.PERCENT);
        IDS.put("duration", Op.DURATION); IDS.put("时长", Op.DURATION); IDS.put("mmss", Op.DURATION);
        IDS.put("bar", Op.BAR);         IDS.put("进度条", Op.BAR);    IDS.put("血条", Op.BAR);
    }

    private final Op op;

    public TextPlugin() { this(Op.CONCAT, "concat"); }

    public TextPlugin(Op op, String id) {
        super(id);
        this.op = op == null ? Op.CONCAT : op;
    }

    public static List<String> ids() { return new ArrayList<>(IDS.keySet()); }

    public static TextPlugin of(String id) {
        if (id == null) return null;
        String k = id.trim();
        Op o = IDS.get(k.toLowerCase(Locale.ROOT));
        if (o == null) o = IDS.get(k);
        return o == null ? null : new TextPlugin(o, k);
    }

    /** 编辑器插件目录 */
    public static List<PluginInfo> catalog() {
        List<PluginInfo> out = new ArrayList<>();
        out.add(new PluginInfo("concat", "文本", "拼接,joinstr", "@plugin(concat) | @var(姓) | @var(名) | @var(全名)", "把输入按顺序拼接成一个字符串"));
        out.add(new PluginInfo("upper", "文本", "大写", "@plugin(upper) | @var(名字) | @var(名字)", "转成大写"));
        out.add(new PluginInfo("lower", "文本", "小写", "@plugin(lower) | @var(名字) | @var(名字)", "转成小写"));
        out.add(new PluginInfo("trim", "文本", "去空格", "@plugin(trim) | @var(文本) | @var(文本)", "去掉首尾空白"));
        out.add(new PluginInfo("len", "文本", "长度,strlen", "@plugin(len) | @var(台词) | @var(字数)", "取文本长度（字符数）"));
        out.add(new PluginInfo("sub", "文本", "截取,substr,截断", "@plugin(sub) | @var(台词) | 0 | 12 | @var(开头)", "截取一段：起点（可为负=从末尾算）+ 长度（注意：这个短名给文本用了，数学减法请写 @plugin(减法)）"));
        out.add(new PluginInfo("replace", "文本", "替换", "@plugin(replace) | @var(台词) | 你 | 您 | @var(礼貌台词)", "把文本里的某段替换成另一段"));
        out.add(new PluginInfo("pad", "文本", "补齐", "@plugin(pad) | @var(编号) | 4 | 0 | @var(编号4位)", "左补齐到指定宽度（常用于编号/倒计时）"));
        out.add(new PluginInfo("repeat", "文本", "重复", "@plugin(repeat) | - | 24 | @var(分隔线)", "把文本重复 N 次（画分隔线很方便）"));
        out.add(new PluginInfo("split", "文本", "切分", "@plugin(split) | @var(列表) | , | 1 | @var(第二项)", "按分隔符切分并取第 N 项（也可用于计数）"));
        out.add(new PluginInfo("num", "格式", "小数,fmtnum", "@plugin(num) | @var(分数) | 2 | @var(两位小数)", "数字格式化：保留指定小数位"));
        out.add(new PluginInfo("money", "格式", "金额", "@plugin(money) | @var(金币) | @var(带千分位)", "金额格式化：千分位 + 保留 2 位小数"));
        out.add(new PluginInfo("percent", "格式", "百分比", "@plugin(percent) | @var(进度) | 1 | @var(百分比文本)", "百分比格式化：进度 0.42 → 42.0%"));
        out.add(new PluginInfo("duration", "格式", "时长,mmss", "@plugin(duration) | @var(秒) | @var(时分秒)", "秒数格式化成 mm:ss（超过 1 小时显示 h:mm:ss）"));
        out.add(new PluginInfo("bar", "格式", "进度条,血条", "@plugin(bar) | @var(血量) | 100 | 12 | @var(血条文本)", "用方块字符画进度条：████░░░░░░"));
        return out;
    }

    @Override
    protected String group() { return (op == Op.NUM || op == Op.MONEY || op == Op.PERCENT
            || op == Op.DURATION || op == Op.BAR) ? "格式" : "文本"; }

    @Override
    public String description() {
        switch (op) {
            case CONCAT:   return "拼接：把动作之后的所有输入接成一个字符串";
            case UPPER:    return "转大写";       case LOWER: return "转小写";
            case TRIM:     return "去掉首尾空白"; case LEN:   return "取文本长度（字符数）";
            case SUB:      return "截取子串（起点 + 长度；起点可为负）";
            case REPLACE:  return "文本替换";     case PAD:   return "左补齐到指定宽度";
            case REPEAT:   return "把文本重复 N 次";
            case SPLIT:    return "按分隔符切分并取第 N 项";
            case NUM:      return "数字格式化（保留小数位）";
            case MONEY:    return "金额格式化（千分位 + 两位小数）";
            case PERCENT:  return "百分比格式化";
            case DURATION: return "秒数格式化为 mm:ss";
            case BAR:      return "方块字符进度条";
            default:       return "";
        }
    }

    @Override
    public String usage() {
        switch (op) {
            case CONCAT:   return "@plugin(concat) | 片段1 | 片段2 | … | @var(输出)";
            case UPPER:    return "@plugin(upper) | @var(文本) | @var(输出)";
            case LOWER:    return "@plugin(lower) | @var(文本) | @var(输出)";
            case TRIM:     return "@plugin(trim) | @var(文本) | @var(输出)";
            case LEN:      return "@plugin(len) | @var(文本) | @var(输出数字)";
            case SUB:      return "@plugin(sub) | @var(文本) | 起点 | 长度 | @var(输出)";
            case REPLACE:  return "@plugin(replace) | @var(文本) | 被替换 | 替换成 | @var(输出)";
            case PAD:      return "@plugin(pad) | @var(文本) | 宽度 | 填充字符 | @var(输出)";
            case REPEAT:   return "@plugin(repeat) | 文本 | 次数 | @var(输出)";
            case SPLIT:    return "@plugin(split) | @var(文本) | 分隔符 | 第几项 | @var(输出)";
            case NUM:      return "@plugin(num) | @var(数字) | 小数位数 | @var(输出)";
            case MONEY:    return "@plugin(money) | @var(数字) | @var(输出)";
            case PERCENT:  return "@plugin(percent) | @var(0..1的进度) | 小数位数 | @var(输出)";
            case DURATION: return "@plugin(duration) | @var(秒) | @var(输出)";
            case BAR:      return "@plugin(bar) | @var(当前值) | 最大值 | 格子数 | @var(输出)";
            default:       return "";
        }
    }

    // =====================================================================

    @Override
    protected void run(PluginContext ctx, String[] in, String[] out) {
        switch (op) {
            case CONCAT -> {
                StringBuilder sb = new StringBuilder();
                for (int k = 0; k < in.length - 1; k++) sb.append(raw(in, k));
                setOut(out, sb.toString());
            }
            case UPPER -> setOut(out, arg(in, 0).toUpperCase(Locale.ROOT));
            case LOWER -> setOut(out, arg(in, 0).toLowerCase(Locale.ROOT));
            case TRIM -> setOut(out, arg(in, 0));
            case LEN -> setOut(out, String.valueOf(arg(in, 0).length()));
            case SUB -> {
                String s = raw(in, 0);
                int start = i(arg(in, 1), 0);
                int len = i(arg(in, 2), s.length());
                if (start < 0) start = Math.max(0, s.length() + start);
                start = Math.min(start, s.length());
                int end = Math.max(start, Math.min(s.length(), start + Math.max(0, len)));
                setOut(out, s.substring(start, end));
            }
            case REPLACE -> {
                String s = raw(in, 0);
                String from = raw(in, 1);
                String to = raw(in, 2);
                setOut(out, from.isEmpty() ? s : s.replace(from, to));
            }
            case PAD -> {
                String s = arg(in, 0);
                int width = i(arg(in, 1), 0);
                String fill = arg(in, 2);
                if (fill.isEmpty()) fill = " ";
                StringBuilder sb = new StringBuilder();
                for (int k = s.length(); k < width; k++) sb.append(fill.charAt(0));
                setOut(out, sb + s);
            }
            case REPEAT -> {
                String s = raw(in, 0);
                int times = Math.max(0, i(arg(in, 1), 1));
                StringBuilder sb = new StringBuilder();
                for (int k = 0; k < times; k++) sb.append(s);
                setOut(out, sb.toString());
            }
            case SPLIT -> {
                String s = raw(in, 0);
                String sep = raw(in, 1);
                int index = i(arg(in, 2), 0);
                if (sep.isEmpty()) { setOut(out, ""); return; }
                String[] parts = s.split(java.util.regex.Pattern.quote(sep), -1);
                if (index < 0) index = parts.length + index;
                setOut(out, index >= 0 && index < parts.length ? parts[index] : "");
            }
            case NUM -> {
                double v = num(arg(in, 0), 0);
                int digits = Math.max(0, Math.min(6, i(arg(in, 1), 2)));
                setOut(out, String.format(Locale.ROOT, "%." + digits + "f", v));
            }
            case MONEY -> {
                double v = num(arg(in, 0), 0);
                setOut(out, String.format(Locale.ROOT, "%,.2f", v));
            }
            case PERCENT -> {
                double v = num(arg(in, 0), 0);
                int digits = Math.max(0, Math.min(4, i(arg(in, 1), 1)));
                setOut(out, String.format(Locale.ROOT, "%." + digits + "f%%", v * 100.0));
            }
            case DURATION -> {
                long total = Math.max(0, (long) num(arg(in, 0), 0));
                long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
                setOut(out, h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
                        : String.format(Locale.ROOT, "%02d:%02d", m, s));
            }
            case BAR -> {
                double value = num(arg(in, 0), 0);
                double max = num(arg(in, 1), 100);
                int cells = Math.max(1, Math.min(60, i(arg(in, 2), 10)));
                double ratio = max <= 0 ? 0 : Math.max(0, Math.min(1, value / max));
                int full = (int) Math.round(ratio * cells);
                StringBuilder sb = new StringBuilder();
                for (int k = 0; k < cells; k++) sb.append(k < full ? '█' : '░');
                setOut(out, sb.toString());
            }
            default -> { /* 不会发生 */ }
        }
    }
}
