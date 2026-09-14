package com.studio.util;

import com.studio.flow.AutoSignals;
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
 * 「倒计时时钟」演示地图：<b>倒计时 / 正常时钟双模式 + 到点自动跳下一幕</b>。
 *
 * <p>整张地图只用编辑器里能编辑的东西搭出来（自带插件 + 存档变量 + 信号槽），不写一行 Java、
 * 也不需要任何美术/音频素材（背景是渐变样式画的）。它是「<b>场景自动信号 + 槽里起定时器发信号</b>」
 * 这条链路的完整例子：</p>
 *
 * <pre>
 *   ① 进场景：场景进入 → 刷新显示 + 重排定时器（@plugin(every) 每秒 / @plugin(after) N 秒后发「时间到」）
 *   ② 每秒：@plugin(clock) 取当前时间、减法算剩余、@plugin(duration) 变 mm:ss、@plugin(bar) 画进度条
 *   ③ 开始/暂停/加分钟/切模式：改变量后 emit「重排」→ 重新算剩余并重排定时器
 *   ④ 倒计时到 0：定时器发「时间到」→ goto 下一幕（时间到）
 *   ⑤ 离开时钟那一幕：场景离开 → 停掉每秒定时器（不然换幕了它还在跑）
 *   ⑥ 到下一幕：新场景用「场景进入（from）」和「上一个场景离开（scene→to）」两个自动信号拿到来路
 * </pre>
 *
 * <p>键盘：M 切模式 / 空格 开始·暂停 / ↑ 加 1 分 / ↓ 减 1 分 / R 重置 / Enter 直接跳下一幕。</p>
 */
public final class ClockMapFactory {

    public static final String DEFAULT_FOLDER = "maps/demo_clock";

    /** 一幕的默认总时长（秒） */
    private static final int DEFAULT_TOTAL = 60;

    /** 定时器“不打算触发”时用的超长延时（秒）：比直接不排表更简单，也不会误触发 */
    private static final int NEVER = 86400;

    private ClockMapFactory() { }

    public static GameProject createMap(File mapDir) throws IOException {
        if (!mapDir.exists() && !mapDir.mkdirs()) {
            throw new IOException("无法创建地图文件夹: " + mapDir);
        }
        GameProject project = new GameProject(mapDir);
        Files.createDirectories(mapDir.toPath().resolve("resources").resolve("images"));

        GameOption opt = project.option();
        opt.setInitialScene("时钟");
        opt.setBackground("#0a0d1a");
        opt.setVolume(0.6);
        opt.setTypewriterSpeed(14);

        // ---------- 存档变量（编辑器：场景 → 地图全局设置 里也能这样增删） ----------
        opt.addSaveVar(new SaveVarDef("倒计时模式", VarType.BOOL, "false"));
        opt.addSaveVar(new SaveVarDef("运行中", VarType.BOOL, "false"));
        opt.addSaveVar(new SaveVarDef("秒数", VarType.INT, "0"));
        opt.addSaveVar(new SaveVarDef("总秒数", VarType.INT, String.valueOf(DEFAULT_TOTAL)));
        opt.addSaveVar(new SaveVarDef("剩余秒数", VarType.INT, String.valueOf(DEFAULT_TOTAL)));
        opt.addSaveVar(new SaveVarDef("剩余文本", VarType.STRING, "03:00"));
        opt.addSaveVar(new SaveVarDef("当前时间", VarType.STRING, "--:--:--"));
        opt.addSaveVar(new SaveVarDef("显示文本", VarType.STRING, "03:00"));
        opt.addSaveVar(new SaveVarDef("模式文本", VarType.STRING, "正常时钟"));
        opt.addSaveVar(new SaveVarDef("状态文本", VarType.STRING, "已暂停"));
        opt.addSaveVar(new SaveVarDef("总分文本", VarType.STRING, "3"));
        opt.addSaveVar(new SaveVarDef("进度条", VarType.STRING, ""));
        opt.addSaveVar(new SaveVarDef("定时秒数", VarType.INT, String.valueOf(NEVER)));
        opt.addSaveVar(new SaveVarDef("步进", VarType.INT, "0"));
        opt.addSaveVar(new SaveVarDef("可计时", VarType.BOOL, "false"));

        buildClockScene(project);
        buildTimeUpScene(project);
        buildEarlyLeaveScene(project);

        ScriptWriter.write(project.scenarioFile(), project);
        return project;
    }

    // =====================================================================

    private static void buildClockScene(GameProject p) {
        GameScene s = p.addScene("时钟");
        s.addNode(backdrop("底", "#101a33", "#1c2f5a"));

        StoryNode title = text("标题", 70, 24, 700, 50, "⏰ 倒计时时钟：可切换成正常时钟，到点自动进入下一幕");
        title.setFontSize(26);
        title.setAlign("center");
        title.setStyle("-fx-text-fill: #cfe4ff;");
        s.addNode(title);

        // 大时钟：显示 mm:ss（倒计时模式）或当前时间（正常时钟模式）
        StoryNode clock = text("时钟显示", 340, 92, 600, 120, "@var(显示文本)");
        clock.setFontSize(64);
        clock.setAlign("center");
        clock.setStyle("-fx-background-color: rgba(12,18,36,0.92); -fx-background-radius: 18;"
                + "-fx-border-color: rgba(120,200,255,0.55); -fx-border-radius: 18;"
                + "-fx-text-fill: #8fe3ff;");
        s.addNode(clock);

        StoryNode mode = text("模式显示", 340, 222, 600, 34, "模式：@var(模式文本)　总时长：@var(总分文本) 分");
        mode.setFontSize(18);
        mode.setAlign("center");
        mode.setStyle("-fx-text-fill: #ffd76a;");
        s.addNode(mode);

        StoryNode state = text("状态显示", 340, 260, 600, 34, "状态：@var(状态文本)");
        state.setFontSize(18);
        state.setAlign("center");
        state.setStyle("-fx-text-fill: #9fe3a8;");
        s.addNode(state);

        StoryNode bar = text("进度显示", 340, 300, 600, 40, "@var(进度条)");
        bar.setFontSize(20);
        bar.setAlign("center");
        bar.setStyle("-fx-text-fill: #7fd7ff;");
        s.addNode(bar);

        StoryNode tip = text("操作说明", 90, 350, 1100, 84,
                "键盘：<b>M</b> 切换模式　<b>空格</b> 开始 / 暂停　<b>↑</b> 加 1 分　<b>↓</b> 减 1 分　"
                        + "<b>R</b> 重置　<b>Enter</b> 提前离开　<b>F</b> 快进到剩 5 秒（验收用）\n"
                        + "倒计时模式：大时钟显示剩余 mm:ss；<b>走到 0 会自动去「时间到」</b>，"
                        + "没走完就按 Enter / 点「提前离开」会去 <b>「提前离开」</b>（两个不同场景）。\n"
                        + "正常时钟模式：大时钟显示系统当前时间（每秒刷新，不受暂停影响）。");
        tip.setFontSize(16);
        tip.setAlign("center");
        tip.setStyle("-fx-text-fill: #a8b6d8;");
        s.addNode(tip);

        // ---------- 按钮（各自独立鼠标信号 → emit 出与键盘同一个信号，逻辑只写一份） ----------
        s.addNode(switchButton("按钮_模式", "🔁 切换模式 (M)", 90, 470, "点模式", "切换模式"));
        s.addNode(switchButton("按钮_减分", "➖ 减 1 分 (↓)", 340, 470, "点减分", "减分钟"));
        s.addNode(switchButton("按钮_加分", "➕ 加 1 分 (↑)", 590, 470, "点加分", "加分钟"));
        s.addNode(switchButton("按钮_开始", "▶ / ⏸ 开始·暂停 (空格)", 840, 470, "点开始", "开始暂停"));
        s.addNode(switchButton("按钮_重置", "↺ 重置 (R)", 340, 540, "点重置", "重置"));
        s.addNode(switchButton("按钮_快进", "⏩ 快进到剩 5 秒 (F)", 590, 540, "点快进", "快进"));
        s.addNode(switchButton("按钮_提前离开", "⏭ 提前离开 (Enter)", 840, 540, "点离开", "提前离开"));

        // 系统提示条：切模式 / 暂停 / 到点时的即时反馈
        StoryNode toast = new StoryNode(NodeType.TOAST, NodeType.toastX(420), 24, 420, 56);
        toast.setId("提示条");
        toast.setText("提示：M 切模式 / 空格 开始·暂停");
        toast.setFontSize(16);
        s.addNode(toast);

        // ---------- 键盘信号（场景级） ----------
        s.signals().add(SignalDef.key("切换模式", "M"));
        s.signals().add(SignalDef.key("开始暂停", "SPACE"));
        s.signals().add(SignalDef.key("加分钟", "UP"));
        s.signals().add(SignalDef.key("减分钟", "DOWN"));
        s.signals().add(SignalDef.key("重置", "R"));
        s.signals().add(SignalDef.key("快进", "F"));
        s.signals().add(SignalDef.key("提前离开", "ENTER"));

        // ---------- 场景级槽 ----------
        buildClockSlots(s);

        // 离开时钟这一幕：停掉每秒定时器（不然换到别的幕它还在跑）
        s.slots().add(new SlotDef(AutoSignals.LEAVE.name(), "@plugin(stoptimer)", "每秒", ""));
        s.slots().add(new SlotDef(AutoSignals.LEAVE.name(), "@plugin(stoptimer)", "时间到", ""));
        s.slots().add(new SlotDef(AutoSignals.LEAVE.name(), "log", "",
                "离开时钟：@param(scene) → @param(to)（已停掉每秒表与到点表）"));
    }

    /** 时钟那一幕的全部场景槽：进入 → 起表/刷新/重排；每秒 → 推进 + 刷新；各控制信号 → 改状态后重排 */
    private static void buildClockSlots(GameScene s) {
        // ---- ① 进场景：起「每秒」表（一直跑，正常时钟模式也要每秒刷新）、复位成暂停态、刷新 + 重排 ----
        // 每次进这一幕都回到“复位 + 暂停”状态：不然倒计时归零后再回到这一幕会立刻又到点，来回弹幕
        s.slots().add(plugin(AutoSignals.ENTER.name(), "set", "@bool(false)", "@var(运行中)"));
        s.slots().add(plugin(AutoSignals.ENTER.name(), "set", "@int(0)", "@var(秒数)"));
        s.slots().add(new SlotDef(AutoSignals.ENTER.name(), "log", "",
                "进入时钟（来自 @param(from)）：起每秒表 → 刷新显示 → 重排到点定时器"));
        s.slots().add(new SlotDef(AutoSignals.ENTER.name(), "@plugin(every)", "1", "每秒"));
        s.slots().add(new SlotDef(AutoSignals.ENTER.name(), "emit", "", "刷新"));
        s.slots().add(new SlotDef(AutoSignals.ENTER.name(), "emit", "", "重排"));
        s.slots().add(new SlotDef(AutoSignals.ENTER.name(), "set", "提示条", "visible")
                .param("value", "true"));

        // ---- ② 「每秒」：只有“运行中”时才推进已过秒数，然后广播「刷新」 ----
        // （一直保留每秒表：正常时钟模式要每秒刷新当前时间；暂停时步进=0，倒计时就停住）
        // 只有“倒计时模式 且 运行中”才推进已过秒数：正常时钟模式/暂停时秒数不动
        // （每秒表本身一直跑，正常时钟才能每秒刷新当前时间）
        s.slots().add(plugin("每秒", "select", "@var(倒计时模式)", "@var(运行中)", "@bool(false)",
                "@var(可计时)"));
        s.slots().add(plugin("每秒", "select", "@var(可计时)", "@int(1)", "@int(0)", "@var(步进)"));
        s.slots().add(plugin("每秒", "add", "@var(秒数)", "@var(步进)", "@var(秒数)"));
        s.slots().add(new SlotDef("每秒", "emit", "", "刷新"));

        // ---- ③ 「刷新」：把时间算出来写进节点文本（引擎即时重绘）。只算，不推进时间 ----
        s.slots().add(plugin("刷新", "clock", "now", "@var(当前时间)"));
        s.slots().add(plugin("刷新", "减法", "@var(总秒数)", "@var(秒数)", "@var(剩余秒数)"));
        s.slots().add(plugin("刷新", "duration", "@var(剩余秒数)", "@var(剩余文本)"));
        // 大时钟：倒计时模式显示剩余 mm:ss，正常时钟模式显示系统当前时间
        s.slots().add(plugin("刷新", "select", "@var(倒计时模式)", "@var(剩余文本)", "@var(当前时间)",
                "@var(显示文本)"));
        s.slots().add(new SlotDef("刷新", "set", "时钟显示", "text").param("valueVar", "显示文本"));
        // 模式 / 状态文字
        s.slots().add(plugin("刷新", "select", "@var(倒计时模式)", "倒计时", "正常时钟", "@var(模式文本)"));
        s.slots().add(plugin("刷新", "select", "@var(运行中)", "运行中", "已暂停", "@var(状态文本)"));
        s.slots().add(plugin("刷新", "div", "@var(总秒数)", "@int(60)", "@var(总分文本)"));
        s.slots().add(new SlotDef("刷新", "set", "模式显示", "text")
                .param("value", "模式：@var(模式文本)　总时长：@var(总分文本) 分"));
        s.slots().add(new SlotDef("刷新", "set", "状态显示", "text")
                .param("value", "状态：@var(状态文本)"));
        // 进度条：剩余 / 总量
        s.slots().add(plugin("刷新", "bar", "@var(剩余秒数)", "@var(总秒数)", "@int(20)", "@var(进度条)"));
        s.slots().add(new SlotDef("刷新", "set", "进度显示", "text")
                .param("value", "@var(进度条)　@var(剩余文本)"));

        // ---- ④ 重排“到点”定时器：倒计时且运行中时，剩余秒数后发「时间到」；否则排一个不会触发的超长延时 ----
        s.slots().add(plugin("重排", "select", "@var(倒计时模式)", "@var(剩余秒数)", "@int(" + NEVER + ")",
                "@var(定时秒数)"));
        s.slots().add(plugin("重排", "select", "@var(运行中)", "@var(定时秒数)", "@int(" + NEVER + ")",
                "@var(定时秒数)"));
        s.slots().add(new SlotDef("重排", "@plugin(stoptimer)", "时间到", ""));
        s.slots().add(new SlotDef("重排", "@plugin(after)", "@var(定时秒数)", "时间到"));

        // ---- ⑤ 倒计时到点：停止运行 + 提示 + 跳下一幕（离开那一幕时「场景离开」会把每秒表也停掉） ----
        s.slots().add(plugin("时间到", "set", "@bool(false)", "@var(运行中)"));   // 到点即停
        s.slots().add(new SlotDef("时间到", "set", "提示条", "text")
                .param("value", "⏰ 倒计时结束，自动进入下一幕"));
        s.slots().add(new SlotDef("时间到", "set", "提示条", "visible").param("value", "true"));
        s.slots().add(new SlotDef("时间到", "log", "", "倒计时结束：总时长 @var(总秒数) 秒"));
        s.slots().add(new SlotDef("时间到", "goto", "", "时间到"));

        // ---- ⑥ 控制：切换模式 / 开始暂停 / 加减分钟 / 重置 / 快进 / 提前离开 ----
        // 切模式：翻转布尔 + 重排（切到正常时钟后到点定时器会被排成超长延时）
        s.slots().add(plugin("切换模式", "not", "@var(倒计时模式)", "@var(倒计时模式)"));
        s.slots().add(new SlotDef("切换模式", "set", "提示条", "text")
                .param("value", "模式已切换：@var(模式文本)"));
        s.slots().add(new SlotDef("切换模式", "set", "提示条", "visible").param("value", "true"));
        s.slots().add(new SlotDef("切换模式", "emit", "", "刷新"));
        s.slots().add(new SlotDef("切换模式", "emit", "", "重排"));

        // 开始 / 暂停：翻转“运行中”（状态文字由「刷新」统一算）
        s.slots().add(plugin("开始暂停", "not", "@var(运行中)", "@var(运行中)"));
        s.slots().add(new SlotDef("开始暂停", "set", "提示条", "text")
                .param("value", "计时状态：@var(状态文本)（当前 @var(模式文本) 模式）"));
        s.slots().add(new SlotDef("开始暂停", "set", "提示条", "visible").param("value", "true"));
        s.slots().add(new SlotDef("开始暂停", "emit", "", "刷新"));
        s.slots().add(new SlotDef("开始暂停", "emit", "", "重排"));

        // 加 / 减 1 分（下限 1 分钟）
        s.slots().add(plugin("加分钟", "add", "@var(总秒数)", "@int(60)", "@var(总秒数)"));
        s.slots().add(plugin("加分钟", "max", "@var(总秒数)", "@int(60)", "@var(总秒数)"));
        s.slots().add(plugin("加分钟", "min", "@var(总秒数)", "@int(3600)", "@var(总秒数)"));
        s.slots().add(new SlotDef("加分钟", "emit", "", "重排"));
        s.slots().add(new SlotDef("加分钟", "emit", "", "刷新"));
        s.slots().add(plugin("减分钟", "减法", "@var(总秒数)", "@int(60)", "@var(总秒数)"));
        s.slots().add(plugin("减分钟", "max", "@var(总秒数)", "@int(60)", "@var(总秒数)"));
        s.slots().add(new SlotDef("减分钟", "emit", "", "重排"));
        s.slots().add(new SlotDef("减分钟", "emit", "", "刷新"));

        // 重置：已过时间归零（总时长不变）
        s.slots().add(plugin("重置", "set", "@int(0)", "@var(秒数)"));
        s.slots().add(new SlotDef("重置", "set", "提示条", "text").param("value", "已重置到 @var(总分文本) 分"));
        s.slots().add(new SlotDef("重置", "set", "提示条", "visible").param("value", "true"));
        s.slots().add(new SlotDef("重置", "emit", "", "重排"));
        s.slots().add(new SlotDef("重置", "emit", "", "刷新"));

        // 快进：把已过秒数推到“只剩 5 秒”，方便几秒内验收自动跳幕（按 F 或点按钮）
        s.slots().add(plugin("快进", "减法", "@var(总秒数)", "@int(5)", "@var(秒数)"));
        s.slots().add(new SlotDef("快进", "set", "提示条", "text").param("value", "⏩ 只剩 5 秒，马上到点"));
        s.slots().add(new SlotDef("快进", "set", "提示条", "visible").param("value", "true"));
        s.slots().add(new SlotDef("快进", "emit", "", "重排"));
        s.slots().add(new SlotDef("快进", "emit", "", "刷新"));

        // 提前离开（没走完就走）：去「提前离开」那一幕 —— 与到点自动跳的「时间到」是不同的场景
        s.slots().add(new SlotDef("提前离开", "log", "", "还没走完就离开：@var(剩余文本) 剩余"));
        s.slots().add(new SlotDef("提前离开", "goto", "", "提前离开"));
    }

    /** 「时间到」：倒计时走完 0 自动跳进来的那一幕 */
    private static void buildTimeUpScene(GameProject p) {
        GameScene s = p.addScene("时间到");
        s.addNode(backdrop("底", "#2a1030", "#4a1b3a"));

        StoryNode title = text("标题", 90, 90, 1100, 70, "⏰ 时间到（倒计时走完了）");
        title.setFontSize(40);
        title.setAlign("center");
        title.setStyle("-fx-text-fill: #ffd76a;");
        s.addNode(title);

        StoryNode from = text("来源显示", 190, 200, 900, 60, "本幕由「@param(from)」跳入");
        from.setFontSize(20);
        from.setAlign("center");
        from.setStyle("-fx-text-fill: #cfe4ff;");
        s.addNode(from);

        // 这一行专门演示「上一个场景离开」：离开信号也能被下一幕收到
        StoryNode prev = text("离开显示", 190, 270, 900, 60, "（等待「上一个场景离开」信号）");
        prev.setFontSize(20);
        prev.setAlign("center");
        prev.setStyle("-fx-text-fill: #9fe3a8;");
        s.addNode(prev);

        StoryNode auto = text("返回提示", 190, 340, 900, 50, "5 秒后自动返回时钟…");
        auto.setFontSize(18);
        auto.setAlign("center");
        auto.setStyle("-fx-text-fill: #a8b6d8;");
        s.addNode(auto);

        s.addNode(switchButton("按钮_返回", "↩ 返回时钟", 340, 470, "点返回", "返回时钟"));
        s.addNode(switchButton("按钮_取消", "✋ 取消自动返回", 590, 470, "点取消", "取消自动返回"));
        s.addNode(switchButton("按钮_退出", "🚪 退出游戏", 840, 470, "点退出", "退出游戏"));

        StoryNode toast = new StoryNode(NodeType.TOAST, NodeType.toastX(420), 24, 420, 56);
        toast.setId("提示条");
        toast.setText("倒计时结束");
        toast.setFontSize(16);
        s.addNode(toast);

        // 键盘：Esc 返回时钟（场景级）
        s.signals().add(SignalDef.key("返回时钟", "ESCAPE"));
        s.signals().add(SignalDef.key("取消自动返回", "C"));

        // ---- 自动信号：进入（from）/ 上一个场景离开（scene→to） ----
        s.slots().add(new SlotDef(AutoSignals.ENTER.name(), "log", "",
                "进入「时间到」（来自 @param(from)）"));
        s.slots().add(new SlotDef(AutoSignals.ENTER.name(), "set", "来源显示", "text")
                .param("value", "本幕由「@param(from)」跳入（场景进入 的 from 参数）"));
        s.slots().add(new SlotDef(AutoSignals.PREV_LEAVE.name(), "set", "离开显示", "text")
                .param("value", "上一幕「@param(scene)」已离开 → 本幕「@param(to)」（上一个场景离开 信号）"));
        s.slots().add(new SlotDef(AutoSignals.PREV_LEAVE.name(), "set", "提示条", "text")
                .param("value", "接收到「上一个场景离开」：@param(scene)"));
        s.slots().add(new SlotDef(AutoSignals.PREV_LEAVE.name(), "set", "提示条", "visible")
                .param("value", "true"));
        s.slots().add(new SlotDef(AutoSignals.PREV_LEAVE.name(), "log", "",
                "上一幕 @param(scene) 已离开（本幕收到）"));

        // 5 秒后自动返回（@plugin(after) 定时器结束 → 发「自动返回」→ 槽里 goto；取消则停表）
        s.slots().add(new SlotDef(AutoSignals.ENTER.name(), "@plugin(after)", "5", "自动返回"));
        s.slots().add(new SlotDef("自动返回", "log", "", "5 秒到：自动返回时钟"));
        s.slots().add(new SlotDef("自动返回", "goto", "", "时钟"));
        s.slots().add(new SlotDef("取消自动返回", "@plugin(stoptimer)", "自动返回", ""));
        s.slots().add(new SlotDef("取消自动返回", "set", "返回提示", "text")
                .param("value", "（已取消自动返回，可手动点「返回时钟」）"));
        s.slots().add(new SlotDef("取消自动返回", "set", "提示条", "text").param("value", "已取消自动返回"));
        s.slots().add(new SlotDef("取消自动返回", "set", "提示条", "visible").param("value", "true"));

        // 手动返回 / 退出
        s.slots().add(new SlotDef("返回时钟", "goto", "", "时钟"));
        s.slots().add(new SlotDef("退出游戏", "@plugin(quit)", "", ""));
        s.slots().add(new SlotDef(AutoSignals.LEAVE.name(), "@plugin(stoptimer)", "自动返回", ""));
        s.slots().add(new SlotDef(AutoSignals.LEAVE.name(), "log", "",
                "离开「时间到」：@param(scene) → @param(to)"));
    }

    /** 「提前离开」：倒计时没走完就按 Enter / 点「提前离开」时去的那一幕（与「时间到」是两个不同场景） */
    private static void buildEarlyLeaveScene(GameProject p) {
        GameScene s = p.addScene("提前离开");
        s.addNode(backdrop("底", "#102a24", "#1b4a3a"));

        StoryNode title = text("标题", 90, 90, 1100, 70, "🚪 提前离开（倒计时还没走完）");
        title.setFontSize(38);
        title.setAlign("center");
        title.setStyle("-fx-text-fill: #9fe3a8;");
        s.addNode(title);

        StoryNode from = text("来源显示", 190, 200, 900, 60, "本幕由「@param(from)」跳入");
        from.setFontSize(20);
        from.setAlign("center");
        from.setStyle("-fx-text-fill: #cfe4ff;");
        s.addNode(from);

        StoryNode prev = text("离开显示", 190, 270, 900, 60, "（等待「上一个场景离开」信号）");
        prev.setFontSize(20);
        prev.setAlign("center");
        prev.setStyle("-fx-text-fill: #9fe3a8;");
        s.addNode(prev);

        // 这里的对比说明：到点自动跳的是「时间到」，没到点走的是本幕
        StoryNode note = text("说明", 150, 344, 980, 80,
                "分支演示：<b>倒计时走到 0</b> 由定时器发「时间到」→ 自动进 <b>「时间到」</b>；\n"
                        + "没走完就按 Enter / 点「提前离开」→ 进 <b>本幕</b>（同一个 goto 槽按不同信号走不同场景）。");
        note.setFontSize(17);
        note.setAlign("center");
        note.setStyle("-fx-text-fill: #ffd76a;");
        s.addNode(note);

        s.addNode(switchButton("按钮_返回", "↩ 返回时钟（重新计时）", 340, 470, "点返回", "返回时钟"));
        s.addNode(switchButton("按钮_退出", "🚪 退出游戏", 590, 470, "点退出", "退出游戏"));

        StoryNode toast = new StoryNode(NodeType.TOAST, NodeType.toastX(420), 24, 420, 56);
        toast.setId("提示条");
        toast.setText("提前离开");
        toast.setFontSize(16);
        s.addNode(toast);

        // 键盘：空格/Enter 返回时钟
        s.signals().add(SignalDef.key("返回时钟", "SPACE"));
        s.signals().add(SignalDef.key("退出游戏", "ESCAPE"));

        // 自动信号：进入（from）/ 上一个场景离开（scene→to）
        s.slots().add(new SlotDef(AutoSignals.ENTER.name(), "log", "",
                "进入「提前离开」（来自 @param(from)）"));
        s.slots().add(new SlotDef(AutoSignals.ENTER.name(), "set", "来源显示", "text")
                .param("value", "本幕由「@param(from)」跳入，但它是“没走完就离开”那条路"));
        s.slots().add(new SlotDef(AutoSignals.PREV_LEAVE.name(), "set", "离开显示", "text")
                .param("value", "上一幕「@param(scene)」已离开 → 本幕「@param(to)」（上一个场景离开 信号）"));
        s.slots().add(new SlotDef(AutoSignals.PREV_LEAVE.name(), "set", "提示条", "text")
                .param("value", "接收到「上一个场景离开」：@param(scene)"));
        s.slots().add(new SlotDef(AutoSignals.PREV_LEAVE.name(), "set", "提示条", "visible")
                .param("value", "true"));

        // 手动返回 / 退出
        s.slots().add(new SlotDef("返回时钟", "goto", "", "时钟"));
        s.slots().add(new SlotDef("退出游戏", "@plugin(quit)", "", ""));
        s.slots().add(new SlotDef(AutoSignals.LEAVE.name(), "log", "",
                "离开「提前离开」：@param(scene) → @param(to)"));
    }

    // =====================================================================

    /** 按钮：自带鼠标信号，点一下 emit 出与键盘同一个信号名（逻辑只写一份） */
    private static StoryNode switchButton(String id, String label, double x, double y,
                                          String mouseSignal, String emitSignal) {
        StoryNode b = new StoryNode(NodeType.BUTTON, x, y, 250, 56);
        b.setId(id);
        b.setText(label);
        b.setFontSize(17);
        b.signals().add(SignalDef.mouse(mouseSignal, "click"));
        b.slots().add(new SlotDef(mouseSignal, "emit", "", emitSignal));
        return b;
    }

    /** 全屏渐变底：用带样式的文本节点画（不需要图片素材） */
    private static StoryNode backdrop(String id, String from, String to) {
        StoryNode n = new StoryNode(NodeType.TEXT, 0, 0, MapTemplateFactory.CANVAS_W, MapTemplateFactory.CANVAS_H);
        n.setId(id);
        n.setText("");
        n.setStyle("-fx-background-color: linear-gradient(to bottom, " + from + ", " + to + ");");
        return n;
    }

    /** 构造一个 {@code @plugin(...)} 槽：动作后依次是 输入… 与最后一个“输出位” */
    private static SlotDef plugin(String signal, String id, String... args) {
        SlotDef slot = new SlotDef(signal, "@plugin(" + id + ")", args.length > 0 ? args[0] : "",
                args.length > 1 ? args[1] : "");
        for (int i = 2; i < args.length; i++) slot.extraArgs().add(args[i]);
        return slot;
    }

    private static StoryNode text(String id, double x, double y, double w, double h, String content) {
        StoryNode n = new StoryNode(NodeType.TEXT, x, y, w, h);
        n.setId(id);
        n.setText(content);
        return n;
    }
}
