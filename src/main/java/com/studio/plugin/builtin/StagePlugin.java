package com.studio.plugin.builtin;

import com.studio.flow.FlowHost;
import com.studio.flow.PluginContext;
import com.studio.util.Logs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 编辑器<b>自带</b>插件：<b>演出</b>——立绘出入场、背景切换、特效。
 *
 * <p>这些事用 {@code set} 槽当然也能做（设 visible / x / y / style / path），
 * 但每次都要手写一堆属性、还得自己算坐标和过渡时间。这一族插件把它们包装成“导演语言”：</p>
 *
 * <h3>可用插件 ID</h3>
 * <pre>
 *   # 立绘：入场（可指定左/中/右）、退场、换表情、换图、移动
 *   slot = 蛇登场 | @plugin(cast) | enter | 立绘_snake | right | 0.4
 *   slot = 蛇退场 | @plugin(cast) | leave | 立绘_snake | 0.3
 *   slot = 变脸   | @plugin(cast) | face  | 立绘_snake | 🐍 蛇专家 / smug
 *   slot = 换图   | @plugin(cast) | path  | 立绘_snake | resources/chars/snake_smug.png
 *
 *   # 背景：纯色 / 渐变 / 图片 / 显隐 / 淡入淡出
 *   slot = 切夜色 | @plugin(bg) | color    | 幕布01 | #101322
 *   slot = 灯会   | @plugin(bg) | gradient | 幕布01 | #1b1030 | #3a2140
 *   slot = 换场景 | @plugin(bg) | image    | 幕布01 | resources/images/room.png
 *   slot = 黑场   | @plugin(bg) | fade     | 幕布01 | 0 | 0.8
 *
 *   # 特效：抖屏 / 闪白 / 心跳 / 淡入 / 放大 …
 *   slot = 崩簧   | @plugin(fx) | shake   | 幕布01 | 8 | 400
 *   slot = 灯爆   | @plugin(fx) | flash   | 幕布01
 *   slot = 心跳   | @plugin(fx) | pulse   | 立绘_ds
 *   slot = 出场   | @plugin(fx) | slidein | 立绘_ds | left
 * </pre>
 *
 * <p>参数约定见每个动作的 {@code usage()}；所有动作都通过宿主
 * {@link FlowHost#setProperty} / {@link FlowHost#animate} 落到渲染层，
 * 所以插件本身<b>不碰 JavaFX 控件</b>，线程安全由引擎保证。
 * 宿主不支持某个特效时会自动退化为“直接设属性/透明度”，不会报错也不会中断剧情。</p>
 */
public class StagePlugin extends BuiltinPlugin {

    private enum Act { CAST, BG, FX }

    private static final Map<String, Act> IDS = new LinkedHashMap<>();
    static {
        IDS.put("cast", Act.CAST);  IDS.put("立绘", Act.CAST);  IDS.put("character", Act.CAST);
        IDS.put("bg", Act.BG);      IDS.put("背景", Act.BG);    IDS.put("background", Act.BG);
        IDS.put("fx", Act.FX);      IDS.put("特效", Act.FX);    IDS.put("effect", Act.FX);
    }

    /**
     * 立绘站位<b>中心线</b>（角色中心，不是节点左上角）：与 {@code tools/build_story.mjs} 的
     * {@code STATIONS} 一致。节点 x = 中心 − 框宽/2，所以同一站位在不同取景档下 x 不同。
     */
    private static final Map<String, Double> STATION = new LinkedHashMap<>();
    static {
        STATION.put("left", 250.0);   STATION.put("左", 250.0);
        STATION.put("center", 640.0); STATION.put("中", 640.0); STATION.put("中间", 640.0);
        STATION.put("right", 1030.0); STATION.put("右", 1030.0);
    }

    /**
     * 立绘取景档：<b>必须与 {@code tools/build_story.mjs} 的 {@code FRAMES} 表逐一对应</b>
     * （有单测 {@code StagePluginFrameTest} 守着，改一边必须改另一边）。
     * <p>立绘素材已归一化到统一画布 1280×1536（身高 1460 / 脚线 y=1500 / 中心 x=640），
     * 所以"节点框 = 取景窗口"对每个角色都成立。</p>
     */
    public static final class Frame {
        public final String name;
        public final double scale;
        public final double y;
        public final int w;
        public final int h;

        Frame(String name, double scale, double y) {
            this.name = name;
            this.scale = scale;
            this.y = y;
            this.w = (int) Math.round(1280 * scale);
            this.h = (int) Math.round(1536 * scale);
        }

        @Override public String toString() { return name + " " + w + "x" + h; }
    }

    private static final Map<String, Frame> FRAMES = new LinkedHashMap<>();
    static {
        FRAMES.put("bust", new Frame("bust", 0.75, 0));    // 头到腰（可见带 0..516），对手戏用
        FRAMES.put("mid", new Frame("mid", 0.55, 0));      // 头到大腿
        FRAMES.put("small", new Frame("small", 0.46, 0));  // 头到膝
        FRAMES.put("crowd", new Frame("crowd", 0.38, 0));  // 群像：全身
        FRAMES.put("full", new Frame("full", 0.469, 0));   // 全身进画面（@debut / @pose full）
    }

    /** 取景档名 → [宽, 高]（单测与编译器 FRAMES 表比对用） */
    public static Map<String, int[]> framePresets() {
        Map<String, int[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, Frame> e : FRAMES.entrySet()) {
            out.put(e.getKey(), new int[]{e.getValue().w, e.getValue().h});
        }
        return out;
    }

    /** 取景档的缩放（单测比对用） */
    public static Map<String, Double> frameScales() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, Frame> e : FRAMES.entrySet()) out.put(e.getKey(), e.getValue().scale);
        return out;
    }

    /** 站位名 → 角色中心线（认不出返回 null，交给调用方按数字处理） */
    private static Double stationCenter(String pos) {
        if (pos == null || pos.isBlank()) return null;
        Double c = STATION.get(pos.toLowerCase(Locale.ROOT));
        if (c == null) c = STATION.get(pos);
        return c;
    }

    /** 站位中心线全表（单测与编译器 STATIONS 表比对用） */
    public static Map<String, Double> stationCenters() {
        return new LinkedHashMap<>(STATION);
    }

    /** 换取景档时的锚点换算：围绕「当前框的中心」缩放，角色不跳位 */
    public static double recenterX(double curX, double curW, int newW) {
        return curX + (curW - newW) / 2.0;
    }

    private final Act act;

    public StagePlugin() { this(Act.CAST, "cast"); }

    public StagePlugin(Act act, String id) {
        super(id);
        this.act = act == null ? Act.CAST : act;
    }

    public static List<String> ids() { return new ArrayList<>(IDS.keySet()); }

    public static StagePlugin of(String id) {
        if (id == null) return null;
        String k = id.trim();
        Act a = IDS.get(k.toLowerCase(Locale.ROOT));
        if (a == null) a = IDS.get(k);
        return a == null ? null : new StagePlugin(a, k);
    }

    /** 编辑器插件目录 */
    public static List<PluginInfo> catalog() {
        List<PluginInfo> out = new ArrayList<>();
        out.add(new PluginInfo("cast", "演出", "立绘,character",
                "@plugin(cast) | frame | 立绘_ds | bust",
                "立绘演出：enter 入场（left/center/right 或具体 x）/ leave 退场 / face 换表情 / path 换图"
                        + " / move 移动 / frame 取景（bust 半身 · mid · small · crowd 群像 · full 全身）"));
        out.add(new PluginInfo("bg", "演出", "背景,background",
                "@plugin(bg) | gradient | 幕布01 | #1b1030 | #3a2140",
                "背景切换：color 纯色 / gradient 渐变 / image 图片 / show 显示 / hide 隐藏 / fade 淡入淡出"));
        out.add(new PluginInfo("fx", "演出", "特效,effect",
                "@plugin(fx) | shake | 幕布01 | 8 | 400",
                "特效预设：shake 抖屏 / flash 闪白 / pulse 心跳 / fadein / fadeout / slidein / slideout / zoom / rotate"));
        return out;
    }

    @Override
    protected String group() { return "演出"; }

    @Override
    public String description() {
        switch (act) {
            case CAST: return "立绘演出：入场 / 退场 / 换表情 / 换图 / 移动 / 取景（半身↔全身）";
            case BG:   return "背景切换：纯色 / 渐变 / 图片 / 显隐 / 淡入淡出";
            case FX:   return "特效预设：抖屏、闪白、心跳、淡入淡出、滑入滑出、缩放、旋转";
            default:   return "";
        }
    }

    @Override
    public String usage() {
        switch (act) {
            case CAST: return "@plugin(cast) | enter|leave|face|path|move|frame | 节点id | 参数…";
            case BG:   return "@plugin(bg) | color|gradient|image|show|hide|fade | 节点id | 参数…";
            case FX:   return "@plugin(fx) | 预设 | 节点id | 参数…";
            default:   return "";
        }
    }

    // =====================================================================

    @Override
    protected void run(PluginContext ctx, String[] in, String[] out) {
        String action = arg(in, 0).toLowerCase(Locale.ROOT);
        String node = arg(in, 1);
        // fx 的 flash 是“屏幕级”特效，可以不写节点；其余动作至少要给节点 id
        if (count(in) < (act == Act.FX ? 1 : 3)) {
            warn(ctx, "参数不足（用法：" + usage() + "）");
            return;
        }
        switch (act) {
            case CAST -> cast(ctx, action, node, in);
            case BG -> bg(ctx, action, node, in);
            case FX -> fx(ctx, action, node, in);
            default -> { /* 不会发生 */ }
        }
    }

    // ---------- 立绘 ----------

    private void cast(PluginContext ctx, String action, String node, String[] in) {
        switch (action) {
            case "enter", "入场", "in" -> {
                // 站位名 → 按「当前框宽」居中到该站位中心线；数字 → 直接当节点 x（旧写法兼容）
                Double center = stationCenter(arg(in, 2));
                Double x = null;
                if (center != null) x = center - numProperty(ctx, node, "width", FRAMES.get("bust").w) / 2.0;
                else if (!arg(in, 2).isEmpty()) x = num(arg(in, 2), 490);
                double dur = num(arg(in, 3), 0.35);
                ctx.setProperty(node, "visible", "true");
                if (x != null) ctx.setProperty(node, "x", trim(x));
                ctx.setProperty(node, "opacity", "0");
                ctx.setProperty(node, "opacity", "1", "opacity:" + ms(dur));
                log(ctx, "立绘入场 " + node + "（x=" + (x == null ? "不变" : trim(x)) + "，" + dur + "s）");
            }
            case "leave", "退场", "out" -> {
                double dur = num(arg(in, 2), 0.3);
                ctx.setProperty(node, "opacity", "0", "opacity:" + ms(dur));
                ctx.setProperty(node, "visible", "false");
                log(ctx, "立绘退场 " + node);
            }
            case "face", "表情", "text" -> {
                String text = raw(in, 2);
                if (text.toLowerCase(Locale.ROOT).startsWith("-fx-")) ctx.setProperty(node, "style", text);
                else ctx.setProperty(node, "text", text);
                log(ctx, "立绘 " + node + " 换成：" + text);
            }
            case "path", "图片", "image" -> {
                ctx.setProperty(node, "path", raw(in, 2));
                ctx.setProperty(node, "video", "");
                log(ctx, "立绘 " + node + " 图片 ← " + raw(in, 2));
            }
            case "style", "样式" -> {
                ctx.setProperty(node, "style", raw(in, 2));
                log(ctx, "立绘 " + node + " 样式已改");
            }
            case "move", "移动" -> {
                ctx.setProperty(node, "x", String.valueOf(num(arg(in, 2), 0)));
                ctx.setProperty(node, "y", String.valueOf(num(arg(in, 3), 0)));
                log(ctx, "立绘 " + node + " 移动到 (" + arg(in, 2) + "," + arg(in, 3) + ")");
            }
            case "frame", "取景", "pose" -> {
                // 换「取景档」：只改 x/y/width/height（纯属性，无需引擎新原语），
                // 且以当前框中心为锚 → 角色不跳位。档位表见 FRAMES（与编译器一致，有单测守着）
                String key = arg(in, 2).toLowerCase(Locale.ROOT);
                Frame f = FRAMES.get(key);
                if (f == null) f = FRAMES.get(arg(in, 2));
                if (f == null) {
                    warn(ctx, "未知取景档: " + arg(in, 2) + "（可选：" + String.join(" / ", FRAMES.keySet()) + "）");
                    break;
                }
                double curX = numProperty(ctx, node, "x", 0);
                double curW = numProperty(ctx, node, "width", f.w);
                double nx = recenterX(curX, curW, f.w);
                ctx.setProperty(node, "x", trim(nx));
                ctx.setProperty(node, "y", trim(f.y));
                ctx.setProperty(node, "width", String.valueOf(f.w));
                ctx.setProperty(node, "height", String.valueOf(f.h));
                log(ctx, "立绘 " + node + " 取景 → " + f + "（中心 " + trim(curX + curW / 2) + " → x=" + trim(nx) + "）");
            }
            case "show", "显示" -> { ctx.setProperty(node, "visible", "true"); log(ctx, "立绘 " + node + " 显示"); }
            case "hide", "隐藏" -> { ctx.setProperty(node, "visible", "false"); log(ctx, "立绘 " + node + " 隐藏"); }
            default -> warn(ctx, "未知立绘动作: " + action + "（用法：" + usage() + "）");
        }
    }

    // ---------- 背景 ----------

    private void bg(PluginContext ctx, String action, String node, String[] in) {
        switch (action) {
            case "color", "颜色" -> {
                ctx.setProperty(node, "style", "-fx-background-color: " + arg(in, 2) + ";");
                log(ctx, "背景 " + node + " ← 颜色 " + arg(in, 2));
            }
            case "gradient", "渐变" -> {
                String top = arg(in, 2);
                String bottom = arg(in, 3);
                if (bottom.isEmpty()) bottom = top;
                ctx.setProperty(node, "style",
                        "-fx-background-color: linear-gradient(to bottom, " + top + ", " + bottom + ");");
                log(ctx, "背景 " + node + " ← 渐变 " + top + " → " + bottom);
            }
            case "image", "图片", "path" -> {
                ctx.setProperty(node, "path", raw(in, 2));
                ctx.setProperty(node, "style", "");
                log(ctx, "背景 " + node + " ← 图片 " + raw(in, 2));
            }
            case "show", "显示" -> { ctx.setProperty(node, "visible", "true"); log(ctx, "背景显示 " + node); }
            case "hide", "隐藏" -> { ctx.setProperty(node, "visible", "false"); log(ctx, "背景隐藏 " + node); }
            case "fade", "淡入淡出", "opacity" -> {
                double target = num(arg(in, 2), 1);
                double dur = num(arg(in, 3), 0.6);
                ctx.setProperty(node, "opacity", trim(target), "opacity:" + ms(dur));
                log(ctx, "背景 " + node + " 透明度 → " + trim(target) + "（" + dur + "s）");
            }
            default -> warn(ctx, "未知背景动作: " + action + "（用法：" + usage() + "）");
        }
    }

    // ---------- 特效 ----------

    private void fx(PluginContext ctx, String action, String node, String[] in) {
        StringBuilder spec = new StringBuilder(action);
        for (int k = 2; k < in.length; k++) {
            String extra = arg(in, k);
            if (!extra.isEmpty()) spec.append(':').append(extra);
        }
        FlowHost host = ctx == null ? null : ctx.host();
        boolean ok = false;
        if (host != null) {
            try {
                ok = host.animate(node, spec.toString());
            } catch (RuntimeException e) {
                Logs.warn("[Plugin:fx] 宿主动画失败：" + e.getMessage());
            }
        }
        if (ok) {
            log(ctx, "特效 " + spec + " → " + node);
            return;
        }
        // 宿主不支持该预设时的退化处理：至少把最能表达意图的属性改掉
        switch (action) {
            case "fadeout", "淡出" -> ctx.setProperty(node, "opacity", "0", "opacity:" + ms(num(arg(in, 2), 0.5)));
            case "fadein", "淡入" -> { ctx.setProperty(node, "visible", "true"); ctx.setProperty(node, "opacity", "1", "opacity:" + ms(num(arg(in, 2), 0.5))); }
            case "flash", "闪白" -> ctx.setProperty(node, "opacity", "0.35", "opacity:" + ms(num(arg(in, 2), 0.2)));
            default -> ctx.setProperty(node, "scale", "1.05", "scale:" + ms(num(arg(in, 2), 0.25)));
        }
        log(ctx, "宿主未实现特效 " + spec + "，已退化为属性改动（节点 " + node + "）");
    }

    private static String ms(double seconds) { return Math.max(1, (long) Math.rint(seconds * 1000)) + "ms"; }

    /** 读节点数值属性（读不到/不是数字就用默认值）—— 取景换框要靠它拿当前 x/width 当锚点 */
    private static double numProperty(PluginContext ctx, String node, String prop, double def) {
        if (ctx == null || node == null || node.isBlank()) return def;
        try {
            String v = ctx.property(node, prop);
            return (v == null || v.isBlank()) ? def : Double.parseDouble(v.trim());
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static String trim(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }
}
