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
 * 编辑器<b>自带</b>插件：<b>音频播放</b>（随编辑器发行，不依赖任何节点属性）。
 *
 * <p>过去音频只能靠节点的 {@code audio} 属性“进场景自动放一首循环 BGM”，
 * 既不能放一次性音效、也不能中途停/暂停/改音量。这个插件把这些都补齐了，
 * 并且由<b>信号 / 槽</b>驱动 —— 想什么时候放、放几次、放哪个通道，全写在槽里。</p>
 *
 * <h3>用法（槽的一行式写法）</h3>
 * <pre>
 *   slot = 场景进入 | @plugin(audio) | loop   | resources/audio/theme.mp3 | bgm
 *   slot = 点击     | @plugin(audio) | play   | resources/audio/click.wav | se
 *   slot = 静音     | @plugin(audio) | stop   | | bgm
 *   slot = 暂停     | @plugin(audio) | pause  | | bgm
 *   slot = 继续     | @plugin(audio) | resume | | bgm
 *   slot = 音量     | @plugin(audio) | volume | 0.5 | bgm
 *   slot = 全停     | @plugin(audio) | stopall
 * </pre>
 * 参数位置：{@code 动作 | 路径/数值 | 通道名}；通道名可省略（{@code loop} 默认 {@code bgm}，
 * {@code play} 默认 {@code se}）。路径支持绝对路径；相对路径按<b>地图文件夹</b>解析。
 * 音量缺省取 {@code [option] volume}（主音量）。
 *
 * <p>线程安全：所有播放/停止操作都通过 {@code ctx.onUi(...)} 切回 JavaFX 线程执行，
 * 通道表由引擎锁保护，多个信号并发触发不会互相踩。</p>
 */
public class AudioPlugin implements SlotPlugin {

    /** 支持的插件 ID（可写多个别名，都用同一个实现） */
    private static final Map<String, String> IDS = new LinkedHashMap<>();
    static {
        IDS.put("audio", "audio");
        IDS.put("音效", "audio");     // 中文别名也能直接写：@plugin(音效)
    }

    private final String id;

    /** 通道 → 该通道当前播放的“路径”（仅用于日志/排错；真正的播放器在读取器里） */
    private static final Map<String, String> LAST = new LinkedHashMap<>();

    public AudioPlugin() { this("audio"); }

    public AudioPlugin(String id) { this.id = id == null ? "audio" : id; }

    public static List<String> ids() { return new ArrayList<>(IDS.keySet()); }

    @Override
    public String name() { return "音频播放 · " + id; }

    @Override
    public String description() {
        return "用信号/槽播放音频：loop 循环、play 一次、stop/pause/resume 控制、volume 调音量，"
                + "支持多通道（bgm / se / 自定义），不再依赖节点的 audio 属性。";
    }

    @Override
    public String usage() {
        return "@plugin(audio) | loop|play|stop|pause|resume|volume|stopall | 路径或数值 | 通道名";
    }

    // =====================================================================

    @Override
    public String[] execute(PluginContext ctx, String[] args) {
        if (args == null || args.length == 0) {
            Logs.warn("[Audio] 没有参数（用法：" + usage() + "）");
            return args;
        }
        String action = args[0] == null ? "" : args[0].trim().toLowerCase(Locale.ROOT);
        String a1 = args.length > 1 ? (args[1] == null ? "" : args[1].trim()) : "";
        String a2 = args.length > 2 ? (args[2] == null ? "" : args[2].trim()) : "";
        com.studio.flow.FlowHost host = ctx == null ? null : ctx.host();
        if (host == null) {
            Logs.warn("[Audio] 当前没有可用的宿主，无法播放音频");
            return args;
        }
        double master = masterVolume(ctx);
        try {
            switch (action) {
                case "loop", "循环" -> {
                    String ch = a2.isBlank() ? "bgm" : a2;
                    host.playAudio(ch, a1, true, master);
                    LAST.put(ch, a1);
                    Logs.info("[Audio] 循环播放 " + a1 + " → 通道 " + ch);
                }
                case "play", "播放" -> {
                    String ch = a2.isBlank() ? "se" : a2;
                    host.playAudio(ch, a1, false, master);
                    LAST.put(ch, a1);
                    Logs.info("[Audio] 播放一次 " + a1 + " → 通道 " + ch);
                }
                case "stop", "停止" -> {
                    String ch = a2.isBlank() ? "bgm" : a2;
                    host.stopAudio(ch);
                    LAST.remove(ch);
                    Logs.info("[Audio] 停止通道 " + ch);
                }
                case "stopall", "全停" -> {
                    host.stopAllAudio();
                    LAST.clear();
                    Logs.info("[Audio] 停止全部音频");
                }
                case "pause", "暂停" -> {
                    String ch = a2.isBlank() ? "bgm" : a2;
                    host.pauseAudio(ch, true);
                    Logs.info("[Audio] 暂停通道 " + ch);
                }
                case "resume", "继续" -> {
                    String ch = a2.isBlank() ? "bgm" : a2;
                    host.pauseAudio(ch, false);
                    Logs.info("[Audio] 继续通道 " + ch);
                }
                case "volume", "音量" -> {
                    String ch = a2.isBlank() ? "bgm" : a2;
                    double v = parseVolume(a1, master);
                    host.setAudioVolume(ch, v);
                    Logs.info("[Audio] 通道 " + ch + " 音量 = " + v);
                }
                default -> Logs.warn("[Audio] 未知动作: " + action + "（用法：" + usage() + "）");
            }
        } catch (RuntimeException e) {
            Logs.warn("[Audio] 执行失败：" + e.getMessage());
        }
        return args;
    }


    private static double masterVolume(PluginContext ctx) {
        try {
            return ctx == null || ctx.host() == null ? 0.8 : ctx.host().masterVolume();
        } catch (RuntimeException e) {
            return 0.8;
        }
    }

    /** 音量写法：0.5 或 50% 或 50（>1 视为百分数） */
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

    @Override
    public String toString() { return "AudioPlugin(" + id + ")"; }
}
