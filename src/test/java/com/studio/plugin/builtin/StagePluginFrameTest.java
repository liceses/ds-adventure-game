package com.studio.plugin.builtin;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 立绘取景档的一致性守卫。
 *
 * <p>取景档表有两份：编译器 {@code tools/build_story.mjs} 的 {@code STATIONS} / {@code FRAMES}
 * （决定地图里每个立绘节点的几何）与插件 {@link StagePlugin} 的同名常量（决定运行时
 * {@code @plugin(cast) | frame} 能切换成什么）。两份必须逐一对得上，否则"编译器摆的位置"
 * 与"插件切的位置"会分叉 —— 改一边忘了另一边，本测试就会红。</p>
 */
class StagePluginFrameTest {

    private static final double CANVAS_H = 1536;   // 归一化画布（见 tools/normalize_sprites.py）
    private static final double CHAR_H = 1460;     // 归一化后的角色身高
    private static final double HEAD_LINE = 40;    // 归一化后角色头顶在画布上的 y
    private static final double WAIST_LINE = 697;  // 头顶 + 45% 身高 ≈ 腰线
    private static final double DIALOG_TOP = 516;  // 对话框/UI 皮肤顶边：可见带 = 0..516

    private static String compilerSource() throws Exception {
        File f = new File("tools/build_story.mjs");
        assertTrue(f.isFile(), "找不到编译器 build_story.mjs（单测工作目录应为仓库根）：" + f.getAbsolutePath());
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    /** tools/build_story.mjs 的 STATIONS 表：left/center/right → 角色中心线 */
    private static Map<String, Double> compilerStations() throws Exception {
        Matcher m = Pattern.compile("const STATIONS = \\{([^}]*)\\}").matcher(compilerSource());
        assertTrue(m.find(), "编译器里找不到 STATIONS 表");
        Map<String, Double> out = new LinkedHashMap<>();
        Matcher kv = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*(-?[0-9.]+)").matcher(m.group(1));
        while (kv.find()) out.put(kv.group(1), Double.parseDouble(kv.group(2)));
        return out;
    }

    /** tools/build_story.mjs 的 FRAMES 表：档位 → s（缩放） */
    private static Map<String, Double> compilerFrameScales() throws Exception {
        Matcher m = Pattern.compile("const FRAMES = \\{([\\s\\S]*?)\\n\\};").matcher(compilerSource());
        assertTrue(m.find(), "编译器里找不到 FRAMES 表");
        Map<String, Double> out = new LinkedHashMap<>();
        Matcher kv = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*\\{\\s*s:\\s*([0-9.]+)").matcher(m.group(1));
        while (kv.find()) out.put(kv.group(1), Double.parseDouble(kv.group(2)));
        return out;
    }

    @Test
    void frameTableMatchesCompiler() throws Exception {
        Map<String, Double> js = compilerFrameScales();
        Map<String, Double> java = StagePlugin.frameScales();
        assertEquals(js.keySet(), java.keySet(), "取景档名不一致（编译器 vs 插件）");
        for (Map.Entry<String, Double> e : js.entrySet()) {
            assertEquals(e.getValue(), java.get(e.getKey()), 1e-9,
                    "取景档 " + e.getKey() + " 的缩放两边不一致");
        }
        // 由缩放推出的框尺寸也必须一致（两边都用 Math.round(1280*s) / Math.round(1536*s)）
        Map<String, int[]> presets = StagePlugin.framePresets();
        for (Map.Entry<String, Double> e : js.entrySet()) {
            int[] wh = presets.get(e.getKey());
            assertNotNull(wh, "插件缺少取景档 " + e.getKey());
            assertEquals((int) Math.round(1280 * e.getValue()), wh[0], "框宽不一致：" + e.getKey());
            assertEquals((int) Math.round(CANVAS_H * e.getValue()), wh[1], "框高不一致：" + e.getKey());
        }
    }

    @Test
    void stationCentersMatchCompiler() throws Exception {
        Map<String, Double> js = compilerStations();
        Map<String, Double> java = StagePlugin.stationCenters();
        for (String k : new String[]{"left", "center", "right"}) {
            assertTrue(js.containsKey(k), "编译器 STATIONS 缺少 " + k);
            assertEquals(js.get(k), java.get(k), 1e-9, "站位中心线不一致：" + k);
        }
    }

    @Test
    void changingFrameKeepsCharacterCenter() {
        // 半身 → 全身：框宽 960 → 600，x 必须跟着左移 180，中心线不动
        double center = 160 + 960 / 2.0;                  // 640（center 站位）
        double nx = StagePlugin.recenterX(160, 960, 600);
        assertEquals(center, nx + 600 / 2.0, 1e-9, "换框后角色中心线跑了");
        // 站位数反推：节点 x = 中心 − 框宽/2，两条路必须等价
        assertEquals(-230.0, StagePlugin.stationCenters().get("left") - 960 / 2.0, 1e-9);
    }

    @Test
    void frameGeometryKeepsTheFramingPromise() {
        // 「半身 = 头到腰」不是口号：头顶不能被画面切掉，腰线要正好落在对话框上沿附近
        for (String pose : new String[]{"bust"}) {
            double s = StagePlugin.frameScales().get(pose);
            double headOnScreen = HEAD_LINE * s;
            double waistOnScreen = WAIST_LINE * s;
            assertTrue(headOnScreen > 8, pose + "：头顶被切了（" + headOnScreen + "px）");
            assertTrue(headOnScreen < 80, pose + "：头顶留白过多（" + headOnScreen + "px）");
            assertTrue(Math.abs(waistOnScreen - DIALOG_TOP) < 60,
                    pose + "：腰线没落在对话框上沿（" + waistOnScreen + " vs " + DIALOG_TOP + "）");
            // 角色画出来的高度要明显大于可见带 —— 否则就不叫"放大/半身"了
            assertTrue(CHAR_H * s > DIALOG_TOP * 1.8, pose + "：放大幅度不够");
        }
        // 全身档必须真能把整张画布塞进 720 高的画面（用四舍五入后的真实框高判定）
        assertTrue(StagePlugin.framePresets().get("full")[1] <= 720,
                "full 档放不进 720 高的画面：" + StagePlugin.framePresets().get("full")[1]);
    }
}
