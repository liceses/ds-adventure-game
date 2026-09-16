package com.studio.model;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 舞台摆位的守卫：生成的地图里每个立绘的「角色中心线」都必须落在画面内。
 *
 * <p>由来的一个真实事故：编译器对「同一站位的第二个人」一律向画面外错开，于是
 * {@code left} 位的第二个人被直接推出画外 —— 第 6 章初音未来（角色中心 = −14）、
 * 序章的 GLM 娘、{@code song_pick_join} 的 ds 娘都只剩一条边在画面里，玩家反馈"看不见"。
 * 编译器现已改为「向画面内侧错开 + 安全带夹取」（{@code tools/build_story.mjs} 的
 * {@code clampBodyCenter}），本测试把结果钉住，防止再退化。</p>
 *
 * <p>判据与编译器同源：角色中心线至少离画面边 1/4 框宽（框越大人越粗，越要往里让）。
 * 地图是构建产物（由 {@code node tools/build_story.mjs} 生成），所以这里校验的是
 * "提交进仓库的那份地图"本身 —— 一旦有人用坏掉的摆位规则重新生成并提交，本测试就会红。</p>
 */
class StagePlacementTest {

    private static final int CANVAS_W = 1280;
    private static final Pattern CHAR_NODE = Pattern.compile(
            "type = char\\nid = char_(\\S+)\\n\\s*x = (-?\\d+)\\n\\s*y = (-?\\d+)\\n"
                    + "\\s*width = (\\d+)\\n\\s*height = (\\d+)\\n\\s*path = (\\S+)");

    private record Node(String scene, String role, int x, int width, String path) {
        double center() { return x + width / 2.0; }
        double margin() { return width * 0.25; }
    }

    private static List<Node> spriteNodes() throws Exception {
        File map = new File("maps/story/scenario.txt");
        assertTrue(map.isFile(), "找不到生成的地图（单测工作目录应为仓库根）：" + map.getAbsolutePath());
        String text = new String(Files.readAllBytes(map.toPath()), StandardCharsets.UTF_8);
        List<Node> out = new ArrayList<>();
        String scene = "?";
        for (String block : text.split("\\n(?=\\[)")) {
            Matcher head = Pattern.compile("^\\[([^\\]]+)\\]").matcher(block);
            if (head.find()) scene = head.group(1);
            Matcher m = CHAR_NODE.matcher(block);
            while (m.find()) {
                out.add(new Node(scene, m.group(1), Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(4)), m.group(6)));
            }
        }
        assertTrue(out.size() > 1500, "地图里只解析出 " + out.size() + " 个立绘节点，解析八成坏了");
        return out;
    }

    @Test
    void everySpriteKeepsItsCharacterOnScreen() throws Exception {
        List<String> off = new ArrayList<>();
        for (Node n : spriteNodes()) {
            if (n.center() < n.margin() - 0.5 || n.center() > CANVAS_W - n.margin() + 0.5) {
                off.add(n.scene() + " " + n.role() + " center=" + Math.round(n.center())
                        + "（安全带 " + Math.round(n.margin()) + ".." + Math.round(CANVAS_W - n.margin()) + "）");
            }
        }
        assertTrue(off.isEmpty(), "以下立绘的角色中心线顶出画面（玩家只会看到一条边）：\n  "
                + String.join("\n  ", off));
    }

    @Test
    void everySpritePathExists() throws Exception {
        // 缺图不会报错，只会在画面上变成白色占位块（第 6 章司秤吏踩过一次）：
        // 表情名写错时，这里必须红，而不是等玩家看见白块。
        List<String> missing = new ArrayList<>();
        for (Node n : spriteNodes()) {
            if (!new File("src/main/resources/" + n.path()).isFile()) {
                missing.add(n.scene() + " " + n.role() + " -> " + n.path());
            }
        }
        assertTrue(missing.isEmpty(), "以下立绘文件不存在（引擎会画白色占位块）：\n  "
                + String.join("\n  ", missing));
    }
}
