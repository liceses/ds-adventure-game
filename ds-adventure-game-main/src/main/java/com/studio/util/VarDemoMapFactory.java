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
 * “存档变量 / 表达式 / @plugin”示例地图：演示本轮新增的全部能力。
 *
 * <pre>
 *   [运算台]
 *     · 名字输入（文本框，绑定存档变量「玩家名」）—— 打字实时写变量
 *     · 回显名字 —— 文本模板 @var(玩家名) + @node(名字输入, bind)
 *     · num2 = num1 + 1.05 —— 槽 @plugin(add) | @var(num1) | @double(1.05) | @var(num2)
 *     · 金币 +1 —— @plugin(inc) 累加整数变量（超范围/非法输入都自动强制转换）
 *     · num2 取整 —— 目标写 @var(...) 把 @int(...) 的结果“传出”到变量
 *     · 存档 / 读档 slot1 —— 变量随存档一起存取
 *     · 按 R 键 —— 场景级键盘信号重置变量
 *   [结算] —— 整段文字都是 @var(...) 表达式，进入场景时替换成实际值
 * </pre>
 */
public final class VarDemoMapFactory {

    public static final String DEFAULT_FOLDER = "maps/demo_var_plugin";

    private VarDemoMapFactory() { }

    public static GameProject createMap(File mapDir) throws IOException {
        if (!mapDir.exists() && !mapDir.mkdirs()) {
            throw new IOException("无法创建地图文件夹: " + mapDir);
        }
        GameProject project = new GameProject(mapDir);

        File res = mapDir.toPath().resolve("resources").toFile();
        Files.createDirectories(res.toPath().resolve("images"));
        MapAssets.ensure(res, "images/background.png", MapAssets.Kind.BACKGROUND, 313, null, 1280, 720);

        GameOption opt = project.option();
        opt.setInitialScene("运算台");
        opt.setBackground("#101322");
        opt.setVolume(0.8);
        opt.setTypewriterSpeed(16);

        // ---------- 存档变量声明（[option] savevar = 名称 | 类型 | 初值） ----------
        opt.addSaveVar(new SaveVarDef("num1", VarType.DOUBLE, "1.5"));
        opt.addSaveVar(new SaveVarDef("num2", VarType.DOUBLE, "0"));
        opt.addSaveVar(new SaveVarDef("金币", VarType.INT, "0"));
        opt.addSaveVar(new SaveVarDef("玩家名", VarType.STRING, "无名氏"));
        opt.addSaveVar(new SaveVarDef("已通关", VarType.BOOL, "false"));

        buildConsole(project);
        buildEnd(project);

        ScriptWriter.write(project.scenarioFile(), project);
        return project;
    }

    // =====================================================================

    private static void buildConsole(GameProject p) {
        GameScene s = p.addScene("运算台");
        s.setNext("结算");
        s.addNode(bg("images/background.png"));

        StoryNode title = text("标题", 90, 28, 1100, 56, "存档变量 / 表达式 / @plugin 插件演示");
        title.setFontSize(30);
        title.setAlign("center");
        s.addNode(title);

        StoryNode tip = text("说明", 90, 92, 1100, 34,
                "点按钮看变量变化；在输入框里打字会实时写入存档变量；按 R 键重置");
        tip.setFontSize(16);
        tip.setAlign("center");
        tip.setStyle("-fx-text-fill: #9fb4d8;");
        s.addNode(tip);

        // 文本框：单行，绑定存档变量「玩家名」
        StoryNode input = new StoryNode(NodeType.TEXTBOX, 90, 150, 420, 48);
        input.setId("名字输入");
        input.setText("请输入你的名字");
        input.setBind("玩家名");
        input.setMultiline(false);
        input.setFontSize(18);
        s.addNode(input);

        // 回显：文本模板 + @node(...)
        StoryNode echoBtn = button("回显按钮", "回显名字", 530, 150, 200);
        echoBtn.signals().add(SignalDef.mouse("回显", "click"));
        echoBtn.slots().add(new SlotDef("回显", "set", "回显文本", "text")
                .param("value", "你好，@var(玩家名)！"));
        echoBtn.slots().add(new SlotDef("回显", "set", "结果文本", "text")
                .param("value", "按钮宽=@node(width)，文本框宽=@node(名字输入, width)，绑定变量=@node(名字输入, bind)"));
        s.addNode(echoBtn);

        StoryNode echoText = text("回显文本", 750, 150, 440, 48, "（还没回显）");
        echoText.setFontSize(18);
        s.addNode(echoText);

        // @plugin(add)：num2 = num1 + 1.05
        StoryNode addBtn = button("加号按钮", "num2 = num1 + 1.05", 90, 240, 300);
        addBtn.signals().add(SignalDef.mouse("加号", "click"));
        SlotDef addSlot = new SlotDef("加号", "@plugin(add)", "@var(num1)", "@double(1.05)");
        addSlot.extraArgs().add("@var(num2)");
        addBtn.slots().add(addSlot);
        addBtn.slots().add(new SlotDef("加号", "set", "结果文本", "text")
                .param("value", "num2 = @var(num2)"));
        s.addNode(addBtn);

        StoryNode result = text("结果文本", 410, 240, 780, 48, "num2 = 0");
        result.setFontSize(20);
        result.setStyle("-fx-text-fill: #8fe3ff;");
        s.addNode(result);

        // @plugin(inc)：整数变量 +1
        StoryNode coinBtn = button("金币按钮", "金币 +1（@plugin(inc)）", 90, 320, 300);
        coinBtn.signals().add(SignalDef.mouse("加金币", "click"));
        SlotDef incSlot = new SlotDef("加金币", "@plugin(inc)", "@var(金币)", "@var(金币)");
        coinBtn.slots().add(incSlot);
        coinBtn.slots().add(new SlotDef("加金币", "set", "金币文本", "text")
                .param("value", "金币: @var(金币)"));
        s.addNode(coinBtn);

        StoryNode coinText = text("金币文本", 410, 320, 780, 48, "金币: 0");
        coinText.setFontSize(20);
        coinText.setStyle("-fx-text-fill: #ffd76a;");
        s.addNode(coinText);

        // 把结果“传出”到变量：目标写 @var(...)
        StoryNode roundBtn = button("取整按钮", "num2 取整（写回变量）", 90, 400, 300);
        roundBtn.signals().add(SignalDef.mouse("取整", "click"));
        roundBtn.slots().add(new SlotDef("取整", "set", "@var(num2)", "")
                .param("value", "@int(@var(num2))"));
        roundBtn.slots().add(new SlotDef("取整", "set", "结果文本", "text")
                .param("value", "num2 取整后 = @var(num2)"));
        s.addNode(roundBtn);

        StoryNode saveBtn = button("存档按钮", "存档 slot1", 410, 400, 180);
        saveBtn.signals().add(SignalDef.mouse("存档", "click"));
        saveBtn.slots().add(new SlotDef("存档", "save", "", "slot1"));
        saveBtn.slots().add(new SlotDef("存档", "log", "", "已保存到 slot1"));
        s.addNode(saveBtn);

        StoryNode loadBtn = button("读档按钮", "读档 slot1", 610, 400, 180);
        loadBtn.signals().add(SignalDef.mouse("读档", "click"));
        loadBtn.slots().add(new SlotDef("读档", "load", "", "slot1"));
        loadBtn.slots().add(new SlotDef("读档", "set", "结果文本", "text")
                .param("value", "读档后 num2 = @var(num2)"));
        s.addNode(loadBtn);

        StoryNode goBtn = button("前往结算", "前往结算 →", 410, 480, 220);
        goBtn.signals().add(SignalDef.mouse("前往结算", "click"));
        goBtn.slots().add(new SlotDef("前往结算", "set", "@var(已通关)", "")
                .param("value", "@bool(是)"));
        goBtn.slots().add(new SlotDef("前往结算", "goto", "", "结算"));
        s.addNode(goBtn);

        // 场景级键盘信号：R 键重置变量
        s.signals().add(SignalDef.key("重置变量", "R"));
        SlotDef resetA = new SlotDef("重置变量", "@plugin(set)", "@double(1.5)", "@var(num1)");
        s.slots().add(resetA);
        SlotDef resetB = new SlotDef("重置变量", "@plugin(set)", "@double(0)", "@var(num2)");
        s.slots().add(resetB);
        s.slots().add(new SlotDef("重置变量", "set", "结果文本", "text")
                .param("value", "num2 = @var(num2)"));
        s.slots().add(new SlotDef("重置变量", "set", "金币文本", "text")
                .param("value", "金币: @var(金币)"));
    }

    private static void buildEnd(GameProject p) {
        GameScene s = p.addScene("结算");
        s.addNode(bg("images/background.png"));

        StoryNode title = text("结算标题", 90, 200, 1100, 60, "结算");
        title.setFontSize(34);
        title.setAlign("center");
        s.addNode(title);

        // 整段文字都是表达式：进场景渲染时替换成实际值
        StoryNode body = text("结算文本", 90, 280, 1100, 130,
                "@var(玩家名)，你一共拿到 @var(金币) 金币。\n"
                        + "num2 的最终值是 @var(num2)。\n"
                        + "已通关 = @var(已通关)");
        body.setFontSize(22);
        body.setAlign("center");
        s.addNode(body);

        StoryNode back = button("返回按钮", "← 回到运算台", 490, 470, 300);
        back.signals().add(SignalDef.mouse("返回", "click"));
        back.slots().add(new SlotDef("返回", "goto", "", "运算台"));
        s.addNode(back);
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
        StoryNode b = new StoryNode(NodeType.BUTTON, x, y, w, 48);
        b.setId(id);
        b.setText(label);
        return b;
    }
}
