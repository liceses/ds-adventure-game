package com.studio.parser;

import com.studio.model.GameOption;
import com.studio.model.GameProject;
import com.studio.model.GameScene;
import com.studio.model.NodeType;
import com.studio.model.StoryNode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 解析器“双向同步”自测（纯 JDK 运行，无需 JavaFX / 图形环境）：
 * <pre>
 *   1) 模型 → ScriptWriter → scenario.txt → ScriptParser → 模型（逐字段比对）
 *   2) 手写中文别名脚本 → 解析 → 验证别名归一化 / Heredoc / 注释语义
 * </pre>
 *
 * 运行：java -cp target/classes com.studio.parser.ParserSelfTest
 */
public final class ParserSelfTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("========== 剧情脚本 Parser 双向同步自测开始 ==========");
        testModelRoundTrip();
        testChineseAliasScript();
        testToleranceWarnings();

        if (failures == 0) {
            System.out.println("\n[通过] 全部自测用例通过：文本 ⇄ 模型 往返一致。");
        } else {
            System.out.println("\n[失败] 共 " + failures + " 处不一致！");
            System.exit(1);
        }
    }

    // =====================================================================
    // 用例 1：模型 → 文本 → 模型 往返
    // =====================================================================
    private static void testModelRoundTrip() throws Exception {
        GameProject project = new GameProject();
        project.option().setInitialScene("Start");
        project.option().setBackground("#0f1224");
        project.option().setVolume(0.65);
        project.option().putAliasedProperty("自定义设置", "保留值");

        // 场景一
        GameScene start = project.addScene("Start");
        start.setEvent(""); // 清空占位
        start.putAliasedProperty("备注", "序章");

        StoryNode bg = new StoryNode(NodeType.BACKGROUND, 0, 0, 1280, 720);
        bg.setPath("resources/images/background.png");
        start.addNode(bg);

        StoryNode dialog = new StoryNode(NodeType.DIALOG, 140, 500, 1000, 180);
        dialog.setText("「你好，<color:#ffe066>冒险者</color>！」\n欢迎来到 <b>剧情编辑器</b>。\n反斜杠\\\\与#颜色都保留。");
        dialog.setStyle("-fx-font-size: 22px; -fx-text-fill: white;");
        dialog.setVisible(false); // 测试默认省略逻辑
        dialog.extras().put("昵称", "小明");
        dialog.extras().put("customKey", "custom value=带等号");
        start.addNode(dialog);

        StoryNode btn = new StoryNode(NodeType.BUTTON, 500, 660, 150, 50);
        btn.setText("前往森林");
        btn.setAction("target");
        btn.setTarget("Forest");
        start.addNode(btn);

        // 场景二（带插件事件）
        GameScene forest = project.addScene("Forest");
        forest.setEvent("minesweeper");
        forest.setNext("Start");
        forest.putAliasedProperty("氛围", "黄昏");
        StoryNode charNode = new StoryNode(NodeType.CHARACTER, 80, 90, 320, 520);
        charNode.setPath("resources/images/hero_left.png");
        charNode.setId("立绘_遥");
        forest.addNode(charNode);
        project.addScene("Empty"); // 无节点场景

        // 序列化 → 写临时文件 → 解析
        Path tmp = Files.createTempFile("studio_roundtrip_", ".txt");
        ScriptWriter.write(tmp.toFile(), project);
        String script = Files.readString(tmp);
        System.out.println("---- 序列化片段预览 ----");
        System.out.println(script.lines().limit(14).reduce((a, b) -> a + "\n" + b).orElse(""));
        System.out.println("------------------------");

        List<String> warnings = new ArrayList<>();
        GameProject parsed = ScriptParser.parse(tmp.toFile(), warnings);
        Files.deleteIfExists(tmp);

        check("往返解析无致命异常", true);
        check("往返后无警告（应无）", warnings.isEmpty(),
                warnings.isEmpty() ? "" : "有警告: " + warnings);
        checkEqual("option 数量", parsed.option().values().size(), project.option().values().size());
        checkEqual("option initialScene", parsed.option().initialScene(), "Start");
        checkEqual("option background", parsed.option().background(), "#0f1224");
        check("option 音量≈0.65", Math.abs(parsed.option().volume() - 0.65) < 1e-9);
        checkEqual("option 自定义键", parsed.option().values().get("自定义设置"), "保留值");
        checkEqual("场景顺序", parsed.scenes().keySet().toString(),
                "[Start, Forest, Empty]");

        GameScene ps = parsed.getScene("Start");
        GameScene ms = project.getScene("Start");
        checkEqual("Start 场景属性数", ps.props().size(), ms.props().size());
        checkEqual("Start 场景自定义属性", ps.prop("备注"), "序章");
        checkEqual("Start 节点数", ps.nodes().size(), 3);

        StoryNode d = ps.nodes().get(1);
        checkEqual("对话文本往返", d.getText(), dialog.getText());
        checkEqual("对话样式往返", d.getStyle(), dialog.getStyle());
        check("对话不可见往返", !d.isVisible());
        checkEqual("未知键往返(昵称)", d.extras().get("昵称"), "小明");
        checkEqual("未知键往返(带等号值)", d.extras().get("customKey"), "custom value=带等号");

        StoryNode b = ps.nodes().get(2);
        checkEqual("按钮目标", b.getTarget(), "Forest");
        checkEqual("按钮动作", b.getAction(), "target");

        GameScene pf = parsed.getScene("Forest");
        checkEqual("Forest event", pf.event(), "minesweeper");
        checkEqual("Forest next", pf.next(), "Start");
        checkEqual("Forest 氛围", pf.prop("氛围"), "黄昏");
        checkEqual("Forest 立绘路径", pf.nodes().get(0).getPath(), "resources/images/hero_left.png");
        checkEqual("Forest 立绘 id", pf.nodes().get(0).getId(), "立绘_遥");

        GameScene pe = parsed.getScene("Empty");
        check("Empty 空场景保留", pe != null && pe.nodes().isEmpty());

        System.out.println("== 用例1（模型⇄文本⇄模型）执行完毕 ==");
    }

    // =====================================================================
    // 用例 2：手写中文别名脚本
    // =====================================================================
    private static void testChineseAliasScript() {
        // 说明：文本块中 "\\n" 代表脚本里的字面反斜杠 n（即 \n 转义序列），
        // 解析侧 ScriptParser.unescape 会把它还原成真实换行。
        String script = """
                # 这是手写的脚本：全部使用中文别名
                [option]
                初始场景 = 序章
                背景颜色 = #223344
                全局音量 = 0.5

                [序章]
                事件 = minesweeper
                {
                类型 = 立绘
                坐标X = 100
                坐标Y = 120
                宽度 = 300
                高度 = 500
                图片 = resources/img/a.png
                文本 = <<<
                #这句话以井号开头，是台词而不是注释
                第二行台词
                <<<
                样式 = -fx-opacity: 0.8;
                }
                {
                类型 = 按钮
                文本 = 开始游戏
                动作 = target
                目标场景 = 第二章
                可见 = 是
                }

                [第二章]
                {
                类型 = 对话
                文本 = 第二幕开场\\n
                }
                """; // 末尾 \\n 演示 \n 转义

        List<String> warnings = new ArrayList<>();
        GameProject p = ScriptParser.parseString(script, new File("."), warnings);
        checkEqual("别名-初始场景", p.option().initialScene(), "序章");
        checkEqual("别名-背景", p.option().background(), "#223344");
        check("别名-音量", Math.abs(p.option().volume() - 0.5) < 1e-9);

        GameScene s1 = p.getScene("序章");
        checkEqual("别名-场景事件", s1.event(), "minesweeper");
        checkEqual("别名-节点数", s1.nodes().size(), 2);

        StoryNode n0 = s1.nodes().get(0);
        checkEqual("别名-类型映射为立绘", n0.getType(), NodeType.CHARACTER);
        checkEqual("别名-X", n0.getX(), 100.0);
        checkEqual("别名-图片路径", n0.getPath(), "resources/img/a.png");
        check("别名-文本含 # 台词（非注释）",
                n0.getText().equals("#这句话以井号开头，是台词而不是注释\n第二行台词"));
        checkEqual("别名-内联样式", n0.getStyle().trim(), "-fx-opacity: 0.8;");

        StoryNode n1 = s1.nodes().get(1);
        checkEqual("别名-可见=是→true", n1.isVisible(), true);
        checkEqual("别名-按钮目标", n1.getTarget(), "第二章");

        GameScene s2 = p.getScene("第二章");
        checkEqual("转义-\\n 还原换行", s2.nodes().get(0).getText(), "第二幕开场\n");
        System.out.println("== 用例2（中文别名/Heredoc/转义）执行完毕 ==");
    }

    // =====================================================================
    // 用例 3：宽容模式（重复场景/悬空花括号等产生警告而非崩溃）
    // =====================================================================
    private static void testToleranceWarnings() {
        String script = """
                [A]
                {
                类型 = 文本
                文本 = 你好
                }
                [A]
                {
                类型 = 文本
                }
                """;
        List<String> warnings = new ArrayList<>();
        GameProject p = ScriptParser.parseString(script, new File("."), warnings);
        check("重复场景警告已产生", warnings.stream().anyMatch(w -> w.contains("重复定义")));
        check("场景 A 仅存一份且节点合并", p.scenes().size() == 1 && p.getScene("A").nodes().size() == 2);
        System.out.println("== 用例3（宽容模式警告）执行完毕 ==");
    }

    // =====================================================================
    // 断言小工具
    // =====================================================================
    private static void check(String name, boolean ok) {
        check(name, ok, "");
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            System.out.println("  [OK] " + name);
        } else {
            failures++;
            System.out.println("  [FAIL] " + name + (detail.isEmpty() ? "" : " —— " + detail));
        }
    }

    private static void checkEqual(String name, Object actual, Object expected) {
        boolean ok = (actual == null && expected == null)
                || (actual != null && actual.equals(expected));
        check(name, ok, "期望 <" + expected + ">，实际 <" + actual + ">");
    }
}
