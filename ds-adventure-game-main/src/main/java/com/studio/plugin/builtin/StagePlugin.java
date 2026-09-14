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

    /** 立绘位置预设（与地图模板里的三档站位一致） */
    private static final Map<String, Double> POS = new LinkedHashMap<>();
    static {
        POS.put("left", 70.0);   POS.put("左", 70.0);
        POS.put("center", 490.0); POS.put("中", 490.0); POS.put("中间", 490.0);
        POS.put("right", 920.0); POS.put("右", 920.0);
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
                "@plugin(cast) | enter | 立绘_ds | center | 0.4",
                "立绘演出：enter 入场（left/center/right 或具体 x）/ leave 退场 / face 换表情 / path 换图 / move 移动"));
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
            case CAST: return "立绘演出：入场 / 退场 / 换表情 / 换图 / 移动（带动画时长）";
            case BG:   return "背景切换：纯色 / 渐变 / 图片 / 显隐 / 淡入淡出";
            case FX:   return "特效预设：抖屏、闪白、心跳、淡入淡出、滑入滑出、缩放、旋转";
            default:   return "";
        }
    }

    @Override
    public String usage() {
        switch (act) {
            case CAST: return "@plugin(cast) | enter|leave|face|path|move | 节点id | 参数…";
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
                Double x = POS.get(arg(in, 2).toLowerCase(Locale.ROOT));
                if (x == null) x = POS.get(arg(in, 2));
                if (x == null && !arg(in, 2).isEmpty()) x = num(arg(in, 2), 490);
                double dur = num(arg(in, 3), 0.35);
                ctx.setProperty(node, "visible", "true");
                if (x != null) ctx.setProperty(node, "x", String.valueOf(x));
                ctx.setProperty(node, "opacity", "0");
                ctx.setProperty(node, "opacity", "1", "opacity:" + ms(dur));
                log(ctx, "立绘入场 " + node + "（x=" + (x == null ? "不变" : x) + "，" + dur + "s）");
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

    private static String trim(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }
}
