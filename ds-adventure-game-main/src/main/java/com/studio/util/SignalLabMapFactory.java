package com.studio.util;

import com.studio.flow.SignalDef;
import com.studio.flow.SlotDef;
import com.studio.model.GameOption;
import com.studio.model.GameProject;
import com.studio.model.GameScene;
import com.studio.model.NodeType;
import com.studio.model.StoryNode;
import com.studio.parser.ScriptWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * “信号实验室”示例地图：演示 <b>信号 / 槽 + 逻辑层</b> 的完整链路。
 *
 * <pre>
 *   [控制台]
 *     · 启动按钮 —— 点击信号 → 槽(call) → 逻辑类 SignalLabLogic
 *                     逻辑层改变量(点击次数/最近提示) + 改节点样式/文本（引擎即时重绘）
 *     · 手动灯按钮 —— 纯编辑器槽：点击 → emit 灯“亮灯” → 灯自己的槽 set 样式
 *     · 场景信号 快捷键F —— 键盘全局监听器 → 槽：toggle 提示.visible
 *     · 灯上的按键信号 L —— 节点级键盘信号 → 槽：set 灯样式（蓝色）
 *     · 存档/读档(slot1) —— 变量与样式覆盖一起存取
 *   [走廊] —— 离开再回来，验证变量/样式依然生效
 * </pre>
 */
public final class SignalLabMapFactory {

    public static final String DEFAULT_FOLDER = "maps/demo_signal_lab";

    private SignalLabMapFactory() { }

    public static GameProject createMap(File mapDir) throws IOException {
        if (!mapDir.exists() && !mapDir.mkdirs()) {
            throw new IOException("无法创建地图文件夹: " + mapDir);
        }
        GameProject project = new GameProject(mapDir);

        File res = mapDir.toPath().resolve("resources").toFile();
        Files.createDirectories(res.toPath().resolve("images"));
        MapAssets.ensure(res, "images/background_room.png", MapAssets.Kind.BACKGROUND, 211, null, 1280, 720);
        MapAssets.ensure(res, "images/background_corridor.png", MapAssets.Kind.BACKGROUND, 227, null, 1280, 720);

        GameOption opt = project.option();
        opt.setInitialScene("控制台");
        opt.setBackground("#0d1020");
        opt.setVolume(0.7);
        opt.setTypewriterSpeed(12);

        buildConsole(project);
        buildCorridor(project);

        ScriptWriter.write(project.scenarioFile(), project);
        return project;
    }

    // =====================================================================

    private static void buildConsole(GameProject p) {
        GameScene s = p.addScene("控制台");
        s.addNode(bg("images/background_room.png"));

        // 场景级键盘信号：F 键切换提示显隐
        s.signals().add(SignalDef.key("快捷键F", "F"));
        s.slots().add(new SlotDef("快捷键F", "toggle", "提示", "visible"));

        // 灯（文本节点当“物件”用）
        StoryNode lamp = new StoryNode(NodeType.TEXT, 140, 120, 240, 110);
        lamp.setId("灯");
        lamp.setText("灯（熄灭）");
        lamp.setStyle("-fx-background-color: #2a2d45; -fx-text-fill: #8890c0;");
        lamp.signals().add(SignalDef.mouse("亮灯", "click"));
        lamp.signals().add(SignalDef.key("点亮键L", "L"));
        lamp.slots().add(new SlotDef("亮灯", "set", "@self", "style")
                .param("value", "-fx-background-color: #f4d06f; -fx-text-fill: #3a2a00;"));
        lamp.slots().add(new SlotDef("亮灯", "set", "@self", "text").param("value", "灯（已点亮）"));
        lamp.slots().add(new SlotDef("点亮键L", "set", "@self", "style")
                .param("value", "-fx-background-color: #8fe3ff; -fx-text-fill: #06202b;"));
        lamp.slots().add(new SlotDef("点亮键L", "set", "@self", "text").param("value", "灯（按键点亮）"));
        s.addNode(lamp);

        // 计数（逻辑层写入）
        StoryNode counter = new StoryNode(NodeType.TEXT, 420, 130, 420, 70);
        counter.setId("计数");
        counter.setText("点击次数: 0");
        counter.setStyle("-fx-background-color: rgba(20,24,40,0.7); -fx-text-fill: #ffd76a;");
        s.addNode(counter);

        // 提示区（可被 F 切换显隐；文本由逻辑层通过“变量 + valueVar”写入）
        StoryNode tip = new StoryNode(NodeType.TEXT, 880, 120, 350, 150);
        tip.setId("提示");
        tip.setText("提示区：按 F 可显隐；点“启动按钮”后由逻辑层改写我。");
        tip.setStyle("-fx-background-color: rgba(30,36,60,0.85); -fx-text-fill: #cfe3ff;");
        tip.signals().add(SignalDef.mouse("逻辑广播", "click"));
        tip.slots().add(new SlotDef("逻辑广播", "set", "@self", "text").param("valueVar", "最近提示"));
        s.addNode(tip);

        // 说明对话
        StoryNode d = new StoryNode(NodeType.DIALOG, 90, 470, 1100, 180);
        d.setId("对话_说明");
        d.setText("<b>信号实验室</b>：\n"
                + "① 点“启动按钮” → 信号 → 槽(call) → <color:#8fe3ff>logic/SignalLabLogic</color> 改样式与变量\n"
                + "② 点“手动灯” → 纯编辑器槽 emit → 灯的槽 set 样式（无需写代码）\n"
                + "③ 按 <color:#ffe066>F</color> 切换提示显隐；按 <color:#ffe066>L</color> 让灯变蓝（节点按键信号）\n"
                + "④ 存档/读档 slot1：点击次数、样式覆盖都会一起保存并恢复");
        s.addNode(d);

        // 底部按钮
        StoryNode btnLogic = button("开关", "启动按钮", 8, null, null);
        btnLogic.signals().add(SignalDef.mouse("点击", "click"));
        btnLogic.slots().add(new SlotDef("点击", "call", "", "signallab").param("说明", "转交逻辑层"));
        s.addNode(btnLogic);

        StoryNode btnLamp = button("手动灯", "手动灯", 264, null, null);
        btnLamp.signals().add(SignalDef.mouse("点击", "click"));
        btnLamp.slots().add(new SlotDef("点击", "emit", "灯", "亮灯"));
        s.addNode(btnLamp);

        StoryNode btnSave = button("存档", "存档(slot1)", 520, "save", "slot1");
        s.addNode(btnSave);

        StoryNode btnLoad = button("读档", "读档(slot1)", 776, "load", "slot1");
        s.addNode(btnLoad);

        StoryNode btnGo = button("去走廊", "去走廊", 1032, "target", "走廊");
        s.addNode(btnGo);
    }

    private static void buildCorridor(GameProject p) {
        GameScene s = p.addScene("走廊");
        s.addNode(bg("images/background_corridor.png"));
        StoryNode t = new StoryNode(NodeType.TEXT, 120, 150, 1040, 220);
        t.setId("走廊提示");
        t.setText("走廊很安静。\n回到控制台看看：逻辑层写下的“点击次数”、灯的颜色、提示区的文字\n"
                + "是否仍然保留（这些数据既在变量里，也会随存档写入 saves/slot1.txt）。");
        t.setStyle("-fx-background-color: rgba(18,22,38,0.8); -fx-text-fill: #d8e2ff;");
        s.addNode(t);
        StoryNode back = button("返回", "↩ 回控制台", 490, "target", "控制台");
        s.addNode(back);
    }

    // =====================================================================

    private static StoryNode bg(String image) {
        StoryNode n = new StoryNode(NodeType.BACKGROUND, 0, 0, 1280, 720);
        n.setPath("resources/images/" + image);
        return n;
    }

    private static StoryNode button(String id, String label, double x, String action, String target) {
        StoryNode b = new StoryNode(NodeType.BUTTON, x, 640, 240, 52);
        b.setId(id);
        b.setText(label);
        if (action != null) b.setAction(action);
        if (target != null) b.setTarget(target);
        return b;
    }
}
