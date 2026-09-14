package com.studio.util;

import com.studio.flow.SignalDef;
import com.studio.flow.SlotDef;
import com.studio.flow.VarType;
import com.studio.model.GameOption;
import com.studio.model.GameProject;
import com.studio.model.GameScene;
import com.studio.model.NodeType;
import com.studio.model.SaveVarDef;
import com.studio.model.StoryNode;
import com.studio.parser.ScriptWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * “逻辑门”演示地图：<b>两个开关控制三盏灯，只有两盏都亮时第三盏才亮</b>。
 *
 * <p>这张地图完全用<b>编辑器里能编辑的东西</b>搭出来，不需要写 Java、也不需要改任何外部文件
 * （用的全是编辑器自带的插件 {@code and / or / not / gt} 与 {@code [option]} 里的存档变量）：</p>
 *
 * <pre>
 *   存档变量（场景 → 地图全局设置）：
 *     灯1 / 灯2 / 灯3        bool   三盏灯的状态
 *     任意亮 / 两盏都亮       bool   逻辑加(or) 与 大小比较(gt) 的结果
 *     点亮数                int    int(灯1) + int(灯2)
 *     灯1亮度/灯2亮度/灯3亮度  double 0.25=灭、1.0=亮（用来驱动节点 opacity）
 *
 *   开关按钮的槽（节点属性窗口 → 槽）：
 *     开关1按下 | @plugin(not) | @var(灯1) | @var(灯1)      ← 点一下翻转灯1
 *     开关1按下 | emit | | 刷新                              ← 再发一个“刷新”信号统一重算
 *     （开关2 同理，翻转 灯2）
 *
 *   场景级槽（右侧检查器的“场景槽”，订阅 刷新）：
 *     刷新 | @plugin(and) | @var(灯1) | @var(灯2) | @var(灯3)              ← 逻辑乘：两盏都亮才亮
 *     刷新 | @plugin(or)  | @var(灯1) | @var(灯2) | @var(任意亮)            ← 逻辑加
 *     刷新 | @plugin(add) | @int(@var(灯1)) | @int(@var(灯2)) | @var(点亮数)
 *     刷新 | @plugin(gt)  | @var(点亮数) | @int(1) | @var(两盏都亮)          ← 大小比较
 *     刷新 | @plugin(mul) | @int(@var(灯1)) | @double(0.75) | @var(灯1亮度)  ← 亮度 = 灯*0.75+0.25
 *     刷新 | @plugin(add) | @var(灯1亮度) | @double(0.25) | @var(灯1亮度)
 *     刷新 | set | 灯1 | opacity | value=@var(灯1亮度)                     ← 引擎即时重绘
 *     （灯2、灯3 同理）
 * </pre>
 */
public final class LogicGateDemoMapFactory {

    public static final String DEFAULT_FOLDER = "maps/demo_logic_gate";

    private LogicGateDemoMapFactory() { }

    public static GameProject createMap(File mapDir) throws IOException {
        if (!mapDir.exists() && !mapDir.mkdirs()) {
            throw new IOException("无法创建地图文件夹: " + mapDir);
        }
        GameProject project = new GameProject(mapDir);

        File res = mapDir.toPath().resolve("resources").toFile();
        Files.createDirectories(res.toPath().resolve("images"));
        MapAssets.ensure(res, "images/background.png", MapAssets.Kind.BACKGROUND, 421, null, 1280, 720);

        GameOption opt = project.option();
        opt.setInitialScene("灯控室");
        opt.setBackground("#0b0f1a");
        opt.setVolume(0.7);
        opt.setTypewriterSpeed(14);

        // ---------- 存档变量（编辑器：场景 → 地图全局设置 里也能这样增删） ----------
        opt.addSaveVar(new SaveVarDef("灯1", VarType.BOOL, "false"));
        opt.addSaveVar(new SaveVarDef("灯2", VarType.BOOL, "false"));
        opt.addSaveVar(new SaveVarDef("灯3", VarType.BOOL, "false"));
        opt.addSaveVar(new SaveVarDef("任意亮", VarType.BOOL, "false"));
        opt.addSaveVar(new SaveVarDef("两盏都亮", VarType.BOOL, "false"));
        opt.addSaveVar(new SaveVarDef("点亮数", VarType.INT, "0"));
        opt.addSaveVar(new SaveVarDef("灯1亮度", VarType.DOUBLE, "0.25"));
        opt.addSaveVar(new SaveVarDef("灯2亮度", VarType.DOUBLE, "0.25"));
        opt.addSaveVar(new SaveVarDef("灯3亮度", VarType.DOUBLE, "0.25"));

        buildRoom(project);
        ScriptWriter.write(project.scenarioFile(), project);
        return project;
    }

    // =====================================================================

    private static void buildRoom(GameProject p) {
        GameScene s = p.addScene("灯控室");
        s.addNode(bg("images/background.png"));

        StoryNode title = text("标题", 90, 26, 1100, 54, "逻辑门演示：两盏都亮，第三盏才亮");
        title.setFontSize(28);
        title.setAlign("center");
        s.addNode(title);

        StoryNode tip = text("说明", 90, 84, 1100, 52,
                "点下面两个开关 → 灯1 / 灯2 翻转；灯3 = 灯1 且 灯2（内置插件 @plugin(and) 逻辑乘）。按 R 键重置。");
        tip.setFontSize(15);
        tip.setAlign("center");
        tip.setStyle("-fx-text-fill: #9fb4d8;");
        s.addNode(tip);

        // ---------- 三盏灯（用 opacity 表现亮/灭：0.25 灭、1.0 亮） ----------
        s.addNode(lamp("灯1", 210, 190, "💡 灯1"));
        s.addNode(lamp("灯2", 530, 190, "💡 灯2"));
        s.addNode(lamp("灯3", 850, 190, "💡 灯3"));

        StoryNode st = text("状态文本", 90, 320, 1100, 40,
                "灯1=@bool(@var(灯1))    灯2=@bool(@var(灯2))    灯3=@bool(@var(灯3))");
        st.setFontSize(19);
        st.setAlign("center");
        st.setStyle("-fx-text-fill: #8fe3ff;");
        s.addNode(st);

        StoryNode derived = text("派生文本", 90, 372, 1100, 40,
                "逻辑加(or)=@bool(@var(任意亮))    点亮数=@var(点亮数)    大小比较(点亮数>1)=@bool(@var(两盏都亮))");
        derived.setFontSize(17);
        derived.setAlign("center");
        derived.setStyle("-fx-text-fill: #ffd76a;");
        s.addNode(derived);

        // ---------- 两个开关（各自独立信号名，避免同名信号全场景触发） ----------
        StoryNode sw1 = button("开关1", "🔘 开关1（翻转灯1）", 320, 470, 260);
        sw1.signals().add(SignalDef.mouse("开关1按下", "click"));
        sw1.slots().add(new SlotDef("开关1按下", "@plugin(not)", "@var(灯1)", "@var(灯1)"));
        sw1.slots().add(new SlotDef("开关1按下", "emit", "", "刷新"));
        s.addNode(sw1);

        StoryNode sw2 = button("开关2", "🔘 开关2（翻转灯2）", 700, 470, 260);
        sw2.signals().add(SignalDef.mouse("开关2按下", "click"));
        sw2.slots().add(new SlotDef("开关2按下", "@plugin(not)", "@var(灯2)", "@var(灯2)"));
        sw2.slots().add(new SlotDef("开关2按下", "emit", "", "刷新"));
        s.addNode(sw2);

        // ---------- 场景级：订阅“刷新”，统一重算所有派生量并重绘 ----------
        buildRefreshSlots(s);

        // ---------- 场景级键盘信号：R 重置 ----------
        s.signals().add(SignalDef.key("重置", "R"));
        s.slots().add(new SlotDef("重置", "@plugin(set)", "@bool(false)", "@var(灯1)"));
        s.slots().add(new SlotDef("重置", "@plugin(set)", "@bool(false)", "@var(灯2)"));
        s.slots().add(new SlotDef("重置", "@plugin(set)", "@bool(false)", "@var(灯3)"));
        s.slots().add(new SlotDef("重置", "emit", "", "刷新"));
    }

    /** “刷新”信号的全部场景槽：逻辑乘 / 逻辑加 / 比较 / 亮度 → 重绘 */
    private static void buildRefreshSlots(GameScene s) {
        // 逻辑乘（与）：两盏都亮 → 灯3 亮
        s.slots().add(plugin("刷新", "and", "@var(灯1)", "@var(灯2)", "@var(灯3)"));
        // 逻辑加（或）
        s.slots().add(plugin("刷新", "or", "@var(灯1)", "@var(灯2)", "@var(任意亮)"));
        // 点亮数量 = int(灯1) + int(灯2)
        s.slots().add(plugin("刷新", "add", "@int(@var(灯1))", "@int(@var(灯2))", "@var(点亮数)"));
        // 大小比较：点亮数 > 1（与逻辑乘结果应当一致，可互相印证）
        s.slots().add(plugin("刷新", "gt", "@var(点亮数)", "@int(1)", "@var(两盏都亮)"));
        // 三盏灯的亮度：灯*0.75 + 0.25 → 灭 0.25 / 亮 1.0
        for (String lamp : new String[]{"灯1", "灯2", "灯3"}) {
            s.slots().add(plugin("刷新", "mul", "@int(@var(" + lamp + "))", "@double(0.75)", "@var(" + lamp + "亮度)"));
            s.slots().add(plugin("刷新", "add", "@var(" + lamp + "亮度)", "@double(0.25)", "@var(" + lamp + "亮度)"));
            s.slots().add(new SlotDef("刷新", "set", lamp, "opacity").param("value", "@var(" + lamp + "亮度)"));
        }
    }

    /** 构造一个 {@code @plugin(...)} 槽：动作后依次是 输入… 与最后一个“输出位” */
    private static SlotDef plugin(String signal, String id, String... args) {
        SlotDef slot = new SlotDef(signal, "@plugin(" + id + ")", args.length > 0 ? args[0] : "",
                args.length > 1 ? args[1] : "");
        for (int i = 2; i < args.length; i++) slot.extraArgs().add(args[i]);
        return slot;
    }

    // =====================================================================

    private static StoryNode bg(String image) {
        StoryNode n = new StoryNode(NodeType.BACKGROUND, 0, 0, 1280, 720);
        n.setPath("resources/images/" + image);
        return n;
    }

    private static StoryNode text(String id, double x, double y, double w, double h, String content) {
        StoryNode n = new StoryNode(NodeType.TEXT, x, y, w, h);
        n.setId(id);
        n.setText(content);
        return n;
    }

    private static StoryNode button(String id, String label, double x, double y, double w) {
        StoryNode b = new StoryNode(NodeType.BUTTON, x, y, w, 50);
        b.setId(id);
        b.setText(label);
        return b;
    }

    /** 一盏灯：默认是“灭”的样子（opacity 0.25），之后由槽驱动 */
    private static StoryNode lamp(String id, double x, double y, String label) {
        StoryNode n = new StoryNode(NodeType.TEXT, x, y, 220, 110);
        n.setId(id);
        n.setText(label);
        n.setFontSize(24);
        n.setAlign("center");
        n.setOpacity(0.25);
        n.setStyle("-fx-background-color: rgba(30,34,56,0.85); -fx-background-radius: 14;"
                + "-fx-border-color: rgba(255,215,106,0.55); -fx-border-radius: 14;"
                + "-fx-text-fill: #ffe9a8;");
        return n;
    }
}
