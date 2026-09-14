package com.studio.plugin.builtin;

import com.studio.flow.PluginContext;
import com.studio.flow.SlotPlugin;
import com.studio.util.Logs;

import java.util.Arrays;

/**
 * 自带插件的公共基类：统一「id / 分组 / 容错 / 日志 / 输出位」这几件重复的事。
 *
 * <h3>为什么要有它</h3>
 * 自带插件越加越多（运算、逻辑、音频、视频、系统、流程、文本、演出……），
 * 每个都各写一遍 try-catch、参数取值、写回输出位，很容易出现“这个插件抛异常把剧情卡住”的差异。
 * 基类把约定固定下来：
 * <ul>
 *   <li>{@link #execute} 永远<b>不抛异常</b>：内部 catch 住只记日志，参数原样返回；</li>
 *   <li>参数约定与其它自带插件一致：<b>最后一个参数是输出位</b>（{@link #setOut}）；</li>
 *   <li>{@link #arg} 取参数（越界返回空串，不抛下标异常）；</li>
 *   <li>{@link #log}/{@link #toast} 走 {@link PluginContext}，自动带插件 id 前缀。</li>
 * </ul>
 *
 * <p>子类只需要实现 {@link #group()}/{@link #name()}/{@link #usage()} 三个说明性方法与
 * {@link #run(PluginContext, String[], String[])}：{@code out} 已经是要返回给引擎的数组副本，
 * 改它即可（引擎按“输出位一定回写、其它位置仅当变过才回写”的规则写回存档变量）。</p>
 */
public abstract class BuiltinPlugin implements SlotPlugin {

    private final String id;

    protected BuiltinPlugin(String id) {
        this.id = id == null ? "" : id;
    }

    /** 本实例的插件 ID（就是脚本里 @plugin(...) 写的那个） */
    public final String id() { return id; }

    /** 分组名（编辑器插件选择器与手册里用来归类） */
    protected abstract String group();

    @Override
    public String name() { return group() + " · " + id; }

    /** 语义：一次调用要执行什么（实现里不要抛异常，基类会兜底） */
    protected abstract void run(PluginContext ctx, String[] in, String[] out);

    @Override
    public String[] execute(PluginContext ctx, String[] args) {
        String[] in = args == null ? new String[0] : args;
        String[] out = Arrays.copyOf(in, in.length);
        try {
            run(ctx, in, out);
        } catch (RuntimeException e) {
            Logs.warn("[Plugin:" + id + "] 执行失败（已忽略，不影响剧情）：" + e);
        }
        return out;
    }

    // =====================================================================
    // 参数 / 输出 小工具
    // =====================================================================

    /** 取第 i 个参数（去掉首尾空白；越界返回空串） */
    protected static String arg(String[] a, int i) {
        return a != null && i >= 0 && a.length > i && a[i] != null ? a[i].trim() : "";
    }

    /** 取第 i 个参数原样（不去空白，用于保留文本里的空格） */
    protected static String raw(String[] a, int i) {
        return a != null && i >= 0 && a.length > i && a[i] != null ? a[i] : "";
    }

    /** 参数个数 */
    protected static int count(String[] a) { return a == null ? 0 : a.length; }

    /** 写输出位（最后一个参数）；引擎会把它写回存档变量 */
    protected static void setOut(String[] out, String value) {
        if (out != null && out.length > 0) out[out.length - 1] = value == null ? "" : value;
    }

    protected static boolean is(String value, String... candidates) {
        if (value == null) return false;
        String v = value.trim();
        for (String c : candidates) {
            if (v.equalsIgnoreCase(c)) return true;
        }
        return false;
    }

    protected static double num(String s, double def) {
        if (s == null || s.isBlank()) return def;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    protected static int i(String s, int def) {
        return (int) Math.rint(num(s, def));
    }

    protected static String bool(boolean b) { return b ? "true" : "false"; }

    /** 真值判定：true/1/是/yes/on/真/开，或非 0 数字 */
    protected static boolean truthy(String s) {
        if (s == null) return false;
        String v = s.trim();
        if (v.isEmpty()) return false;
        if (v.equalsIgnoreCase("true") || v.equals("1") || v.equals("是") || v.equals("真")
                || v.equalsIgnoreCase("yes") || v.equalsIgnoreCase("on") || v.equals("开")) return true;
        if (v.equalsIgnoreCase("false") || v.equals("0") || v.equals("否") || v.equals("假")) return false;
        try {
            return Double.parseDouble(v) != 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // =====================================================================
    // 日志 / 提示
    // =====================================================================

    protected void log(PluginContext ctx, String msg) {
        if (ctx != null) ctx.log(msg);
        else Logs.info("[Plugin:" + id + "] " + msg);
    }

    protected void toast(PluginContext ctx, String msg) {
        if (ctx != null) ctx.toast(msg);
    }

    protected void warn(PluginContext ctx, String msg) {
        Logs.warn("[Plugin:" + id + "] " + msg);
        if (ctx != null) ctx.toast(msg);
    }

    @Override
    public String toString() { return getClass().getSimpleName() + "(" + id + ")"; }
}
