package com.studio.plugin.builtin;

import com.studio.flow.PluginContext;
import com.studio.flow.SlotPlugin;
import com.studio.util.Logs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 编辑器<b>自带</b>插件：<b>把视频嵌入节点</b>（随编辑器发行）。
 *
 * <p>效果上等价于“会动的背景图”：把视频挂到某个节点上，读取器就用视频播放器渲染这个节点，
 * 原来的图片（{@code path}）暂时让位；停止时再回到图片。适合片头动画、场景过场、电视/屏幕道具等。</p>
 *
 * <h3>用法（槽的一行式写法）</h3>
 * <pre>
 *   slot = 场景进入 | @plugin(video) | play  | 背景 | resources/video/opening.mp4 | loop
 *   slot = 点击     | @plugin(video) | play  | 电视 | resources/video/news.mp4 | once
 *   slot = 点击     | @plugin(video) | pause | 电视
 *   slot = 点击     | @plugin(video) | resume| 电视
 *   slot = 点击     | @plugin(video) | volume| 电视 | 0.6
 *   slot = 点击     | @plugin(video) | stop  | 电视
 * </pre>
 * 参数位置：{@code 动作 | 节点id | 路径 | loop|once | 音量}（后两项可省略）。
 * 相对路径按<b>地图文件夹</b>解析；物料缺失或格式不支持时会自动退化成 🎬 占位块，不会让剧情崩掉。
 *
 * <p>也可以在编辑器的节点属性里直接填「视频 (video)」，那样进场景即自动循环播放，不需要写槽。</p>
 */
public class VideoPlugin implements SlotPlugin {

    private static final Map<String, String> IDS = new LinkedHashMap<>();
    static {
        IDS.put("video", "video");
        IDS.put("视频", "video");     // 中文别名：@plugin(视频)
    }

    private final String id;

    public VideoPlugin() { this("video"); }

    public VideoPlugin(String id) { this.id = id == null ? "video" : id; }

    public static List<String> ids() { return new ArrayList<>(IDS.keySet()); }

    @Override
    public String name() { return "视频嵌入 · " + id; }

    @Override
    public String description() {
        return "把视频挂到指定节点上（代替该节点的图片/背景作用），支持 play/once/loop/pause/resume/volume/stop。";
    }

    @Override
    public String usage() {
        return "@plugin(video) | play|pause|resume|volume|stop | 节点id | 路径 | loop|once | 音量";
    }

    // =====================================================================

    @Override
    public String[] execute(PluginContext ctx, String[] args) {
        if (args == null || args.length == 0) {
            Logs.warn("[Video] 没有参数（用法：" + usage() + "）");
            return args;
        }
        String action = args[0] == null ? "" : args[0].trim().toLowerCase(Locale.ROOT);
        String nodeId = args.length > 1 ? (args[1] == null ? "" : args[1].trim()) : "";
        String path = args.length > 2 ? (args[2] == null ? "" : args[2].trim()) : "";
        String mode = args.length > 3 ? (args[3] == null ? "" : args[3].trim().toLowerCase(Locale.ROOT)) : "loop";
        double vol = args.length > 4 ? parseVolume(args[4], -1) : -1;

        if (nodeId.isBlank()) {
            Logs.warn("[Video] 需要指定节点 id（用法：" + usage() + "）");
            return args;
        }
        if (ctx.host() == null || ctx.host().node(nodeId) == null) {
            Logs.warn("[Video] 当前场景里找不到节点: " + nodeId);
            ctx.toast("找不到节点: " + nodeId);
            return args;
        }
        try {
            switch (action) {
                case "play", "播放", "embed", "嵌入" -> {
                    if (path.isBlank()) {
                        Logs.warn("[Video] play 需要给出视频路径（用法：" + usage() + "）");
                        return args;
                    }
                    boolean loop = !"once".equals(mode) && !"一次".equals(mode);
                    ctx.setProperty(nodeId, "video", path);
                    ctx.setProperty(nodeId, "videoloop", loop ? "true" : "false");
                    if (vol >= 0) ctx.setProperty(nodeId, "videovolume", String.valueOf(vol));
                    ctx.setProperty(nodeId, "videopause", "false");
                    Logs.info("[Video] 已把视频嵌入节点 " + nodeId + "：" + path + (loop ? "（循环）" : "（一次）"));
                }
                case "pause", "暂停" -> ctx.setProperty(nodeId, "videopause", "true");
                case "resume", "继续" -> ctx.setProperty(nodeId, "videopause", "false");
                case "volume", "音量" -> {
                    // 这里第 3 个参数是音量而不是路径
                    double v = parseVolume(path, -1);
                    if (v < 0) v = parseVolume(mode, -1);
                    if (v < 0) {
                        Logs.warn("[Video] volume 需要一个音量值（0..1 或 0..100）");
                        return args;
                    }
                    ctx.setProperty(nodeId, "videovolume", String.valueOf(v));
                }
                case "stop", "停止", "clear" -> {
                    ctx.setProperty(nodeId, "video", "");
                    ctx.setProperty(nodeId, "videopause", "true");
                    Logs.info("[Video] 已移除节点 " + nodeId + " 上的视频（回到图片）");
                }
                default -> Logs.warn("[Video] 未知动作: " + action + "（用法：" + usage() + "）");
            }
        } catch (RuntimeException e) {
            Logs.warn("[Video] 执行失败：" + e.getMessage());
        }
        return args;
    }

    private static double parseVolume(String raw, double def) {
        if (raw == null || raw.isBlank()) return def;
        String s = raw.trim();
        boolean percent = s.endsWith("%");
        if (percent) s = s.substring(0, s.length() - 1).trim();
        try {
            double v = Double.parseDouble(s);
            if (percent || v > 1.0) v = v / 100.0;
            return Math.max(0, Math.min(1, v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** 编辑器插件目录（选择器 / 手册自动生成用） */
    public static java.util.List<PluginInfo> catalog() {
        java.util.List<PluginInfo> out = new java.util.ArrayList<>();
        out.add(new PluginInfo("video", "视频", "影片,视频嵌入",
                "@plugin(video) | play | 背景 | resources/video/opening.mp4 | loop",
                "把视频挂到节点上代替图片：play 嵌入 / pause / resume / volume / stop（清空后回图片）"));
        return out;
    }

    @Override
    public String toString() { return "VideoPlugin(" + id + ")"; }
}
