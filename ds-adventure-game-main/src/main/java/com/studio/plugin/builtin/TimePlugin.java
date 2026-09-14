package com.studio.plugin.builtin;

import com.studio.flow.PluginContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 编辑器<b>自带</b>插件：<b>时间</b>——把当前时间、日期、耗时写进存档变量。
 *
 * <p>剧情里常要显示“现在是几点”“这一章玩了多久”“用了多长时间通关”；
 * 这些数字放进变量后就能被 {@code @var(...)} 打进文本，也能参与比较。</p>
 *
 * <h3>可用插件 ID</h3>
 * <pre>
 *   slot = 现在   | @plugin(clock)   | now     | HH:mm:ss | @var(显示时间)
 *   slot = 今天   | @plugin(clock)   | date    | yyyy年MM月dd日 | @var(显示日期)
 *   slot = 时间戳 | @plugin(clock)   | stamp   | @var(开始时刻)
 *   slot = 耗时   | @plugin(clock)   | elapsed | @var(开始时刻) | @var(毫秒)
 *   slot = 耗时秒 | @plugin(clock)   | seconds | @var(开始时刻) | @var(秒)
 *   slot = 星期几 | @plugin(clock)   | weekday | @var(星期文本)
 *   slot = 时段   | @plugin(clock)   | part    | @var(早上/下午/晚上)
 * </pre>
 *
 * <p>动作说明：</p>
 * <ul>
 *   <li>{@code now | 格式 | 输出位}：默认格式 {@code HH:mm:ss}（可用 {@code HH:mm}、{@code yyyy-MM-dd HH:mm} 等）；</li>
 *   <li>{@code date | 格式 | 输出位}：默认 {@code yyyy-MM-dd}；</li>
 *   <li>{@code stamp | 输出位}：把“现在”的毫秒时间戳记下来，供后面算耗时；</li>
 *   <li>{@code elapsed | 起点时间戳 | 输出位}：返回毫秒；{@code seconds} 返回秒（保留 1 位小数）；</li>
 *   <li>{@code weekday | 输出位}、{@code part | 输出位}：中文星期、时段（凌晨/早上/上午/下午/傍晚/晚上）。</li>
 * </ul>
 *
 * <p>格式串用的是 Java 的 {@code DateTimeFormatter} 模式（{@code yyyy MM dd HH mm ss}），
 * 写错格式不会报错，会退化成默认格式。</p>
 */
public class TimePlugin extends BuiltinPlugin {

    private static final Map<String, String> IDS = new LinkedHashMap<>();
    static {
        IDS.put("clock", "clock");
        IDS.put("时间", "clock");
        IDS.put("now", "clock");
    }

    public TimePlugin() { this("clock"); }

    public TimePlugin(String id) { super(id); }

    public static List<String> ids() { return new ArrayList<>(IDS.keySet()); }

    public static TimePlugin of(String id) {
        if (id == null) return null;
        String k = id.trim();
        return IDS.containsKey(k.toLowerCase(Locale.ROOT)) || IDS.containsKey(k) ? new TimePlugin(k) : null;
    }

    /** 编辑器插件目录 */
    public static List<PluginInfo> catalog() {
        List<PluginInfo> out = new ArrayList<>();
        out.add(new PluginInfo("clock", "时间", "时间,now",
                "@plugin(clock) | now | HH:mm:ss | @var(显示时间)",
                "时间与耗时：now 当前时间 / date 日期 / stamp 打点 / elapsed 毫秒 / seconds 秒 / weekday 星期 / part 时段"));
        return out;
    }

    @Override
    protected String group() { return "时间"; }

    @Override
    public String description() {
        return "时间：now/date 取当前时间日期、stamp 打点、elapsed/seconds 算耗时、weekday/part 取星期与时段";
    }

    @Override
    public String usage() {
        return "@plugin(clock) | now|date|stamp|elapsed|seconds|weekday|part | 格式或起点 | @var(输出)";
    }

    // =====================================================================

    @Override
    protected void run(PluginContext ctx, String[] in, String[] out) {
        if (count(in) < 2) {
            warn(ctx, "参数不足（用法：" + usage() + "）");
            return;
        }
        String action = arg(in, 0).toLowerCase(Locale.ROOT);
        LocalDateTime now = LocalDateTime.now();
        switch (action) {
            case "now", "时间", "time" -> {
                String pattern = count(in) >= 3 ? raw(in, 1) : "HH:mm:ss";
                setOut(out, format(now, pattern, "HH:mm:ss"));
            }
            case "date", "日期" -> {
                String pattern = count(in) >= 3 ? raw(in, 1) : "yyyy-MM-dd";
                setOut(out, format(now, pattern, "yyyy-MM-dd"));
            }
            case "stamp", "打点", "timestamp" -> {
                long stamp = System.currentTimeMillis();
                setOut(out, String.valueOf(stamp));
                log(ctx, "已打点：" + stamp);
            }
            case "elapsed", "耗时", "ms" -> {
                long from = (long) num(arg(in, 1), 0);
                long ms = from <= 0 ? 0 : System.currentTimeMillis() - from;
                setOut(out, String.valueOf(Math.max(0, ms)));
            }
            case "seconds", "秒" -> {
                long from = (long) num(arg(in, 1), 0);
                double sec = from <= 0 ? 0 : (System.currentTimeMillis() - from) / 1000.0;
                setOut(out, String.format(Locale.ROOT, "%.1f", Math.max(0, sec)));
            }
            case "weekday", "星期" -> setOut(out, weekday(now.getDayOfWeek().getValue()));
            case "part", "时段" -> setOut(out, part(now.toLocalTime()));
            default -> warn(ctx, "未知动作: " + action + "（用法：" + usage() + "）");
        }
    }

    private static String format(LocalDateTime now, String pattern, String def) {
        try {
            return now.format(DateTimeFormatter.ofPattern(pattern));
        } catch (RuntimeException e) {
            return now.format(DateTimeFormatter.ofPattern(def));
        }
    }

    private static String weekday(int value) {
        switch (value) {
            case 1: return "星期一";
            case 2: return "星期二";
            case 3: return "星期三";
            case 4: return "星期四";
            case 5: return "星期五";
            case 6: return "星期六";
            default: return "星期日";
        }
    }

    private static String part(LocalTime t) {
        int h = t.getHour();
        if (h < 5) return "凌晨";
        if (h < 8) return "早上";
        if (h < 11) return "上午";
        if (h < 13) return "中午";
        if (h < 17) return "下午";
        if (h < 19) return "傍晚";
        return "晚上";
    }

    /** 供编辑器/探针复用：今天是几号 */
    public static String today() { return LocalDate.now().toString(); }
}
