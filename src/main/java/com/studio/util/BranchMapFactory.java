package com.studio.util;

import com.studio.model.GameOption;
import com.studio.model.GameProject;
import com.studio.model.GameScene;
import com.studio.model.NodeType;
import com.studio.model.StoryNode;
import com.studio.parser.ScriptParser;
import com.studio.parser.ScriptWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * “分支冒险”示例地图工厂：一张带<b>剧情分支/多结局</b>与
 * <b>2048 小游戏事件</b>的地图，演示三类事件用法：
 * <pre>
 *   场景级 event  : [Forest2048] event=2048 —— 进入场景即嵌入 2048 插件
 *   按钮级 action : action=event + event=2048 —— 剧情中“顺手玩一把”直接呼出
 *   分支跳转      : action=target —— 按玩家选择走向不同场景/结局
 * </pre>
 * 结构：
 * <pre>
 *   [起点] ──▶ [Forest2048](场景事件=2048)
 *      │
 *      └──▶ [湖畔] ──▶ [湖心岛] ──▶ [结局·秘宝]  /  [结局·晚霞]
 *                  └(按钮事件=2048 随时可玩)
 * </pre>
 */
public final class BranchMapFactory {

    /** 默认生成目录（相对工程根目录） */
    public static final String DEFAULT_FOLDER = "maps/branch_demo_2048";

    private BranchMapFactory() { }

    /** 生成分支冒险示例地图（含占位素材）；已存在则跳过 */
    public static GameProject createMap(File mapDir) throws IOException {
        if (!mapDir.exists() && !mapDir.mkdirs()) {
            throw new IOException("无法创建地图文件夹: " + mapDir);
        }
        GameProject project = new GameProject(mapDir);

        // ---------- 占位素材 ----------
        File res = mapDir.toPath().resolve("resources").toFile();
        Files.createDirectories(res.toPath().resolve("images"));
        Files.createDirectories(res.toPath().resolve("audio"));
        MapAssets.ensure(res, "images/background_cross.png", MapAssets.Kind.BACKGROUND, 41, null, 1280, 720);
        MapAssets.ensure(res, "images/background_forest.png", MapAssets.Kind.BACKGROUND, 57, null, 1280, 720);
        MapAssets.ensure(res, "images/background_lake.png", MapAssets.Kind.BACKGROUND, 73, null, 1280, 720);
        MapAssets.ensure(res, "images/background_cave.png", MapAssets.Kind.BACKGROUND, 89, null, 1280, 720);
        MapAssets.ensure(res, "images/char_left.png", MapAssets.Kind.CHARACTER, 103, "阿澈", 340, 560);
        MapAssets.ensure(res, "images/char_right.png", MapAssets.Kind.CHARACTER, 127, "小满", 340, 560);
        MapAssets.ensureWav(res, "audio/theme.wav", 6.0, 174.0);

        // ---------- 全局设置 ----------
        GameOption opt = project.option();
        opt.setInitialScene("起点");
        opt.setBackground("#101322");
        opt.setVolume(0.75);
        opt.setTypewriterSpeed(14);

        // ---------- 场景与分支 ----------
        buildStart(project);
        buildForest2048(project);
        buildLake(project);
        buildIsland(project);
        buildEnding(project, "结局·秘宝", "background_cave.png",
                "你打开宝箱，里面是一枚会轻声哼唱的<b>星辉琥珀</b>。\n"
                + "「传说集齐七枚就能许愿。」小满笑了，「那就……先从吃一顿开始吧？」\n"
                + "—— <color:#ffe066>结局·秘宝：冒险者的晚餐之约</color>");
        buildEnding(project, "结局·晚霞", "background_lake.png",
                "湖面把晚霞折成金币，你在芦苇边捡到漂流瓶，里面是上一支冒险队的地图。\n"
                + "「下次……带我们去那里吧？」\n"
                + "—— <color:#ff9ecb>结局·晚霞：未完的地图</color>");

        // ---------- 落盘 ----------
        ScriptWriter.write(project.scenarioFile(), project);
        return project;
    }

    // =====================================================================
    // 各场景
    // =====================================================================

    private static void buildStart(GameProject p) {
        GameScene s = p.addScene("起点");
        s.addNode(bg("images/background_cross.png"));
        addChar(s, "images/char_left.png", 70, 100, "立绘_澈");
        addChar(s, "images/char_right.png", 870, 100, "立绘_满");
        addName(s, "阿澈", 175, 456, "#ffe2a6");
        addName(s, "小满", 895, 456, "#b8e6ff");
        StoryNode d = addDialog(s, "岔路口的风很轻。\n"
                + "「左边通向传说中会<b>吞掉影子</b>的森林迷阵，」阿澈说，\n"
                + "「右边是安静的湖畔。你来决定方向。」\n"
                + "---\n"
                + "小满从背包里掏出两颗糖：「不急，<u>点击这里</u>可以继续看台词哦。」\n"
                + "（这段台词演示了<b>多段台词</b>：点击对话框自动切段）",
                "对话_岔路");
        d.setTypewriter(Boolean.TRUE); // 显式开启逐字（省略时对话也默认开启）
        addButton(s, "🌲 森林·2048迷阵", "target", "Forest2048", "", 330, 668);
        addButton(s, "🌊 湖畔", "target", "湖畔", "", 620, 668);
        addButton(s, "存档(占位)", "save", "", "", 910, 668);
    }

    /** 场景级事件演示：进入即被 2048 插件接管 */
    private static void buildForest2048(GameProject p) {
        GameScene s = p.addScene("Forest2048");
        s.setEvent("2048");
        s.addNode(bg("images/background_forest.png"));
        StoryNode note = text(90, 60, 1100, 130,
                "你踏入迷阵，石门轰然合拢，地面浮现出<b>4×4 的发光方格</b>……\n"
                + "（场景 event=2048 → 已嵌入 <color:#ffe066>2048 小游戏</color>插件；"
                + "通关或点击上方【返回剧情】回到岔路）");
        note.setId("提示_2048");
        s.addNode(note);
        addMusic(s);
    }

    private static void buildLake(GameProject p) {
        GameScene s = p.addScene("湖畔");
        s.addNode(bg("images/background_lake.png"));
        addChar(s, "images/char_left.png", 70, 100, "立绘_澈");
        addName(s, "阿澈", 175, 456, "#ffe2a6");
        StoryNode d = addDialog(s, "湖心有一座雾中的小岛。老渔夫说：\n"
                + "「岛上的古箱上了 <u>四位数密码</u>……也有人说是 <color:#8fe3ff>2048</color>。」\n"
                + "---\n"
                + "小满已经在岸边铺开了野餐垫。她朝你挥手：\n"
                + "「要么先去岛上找箱子，要么……<b>就地玩一局 2048</b>？」",
                "对话_湖");
        d.setTypewriter(Boolean.FALSE); // 显式关闭逐字：整段立即显示
        addButton(s, "🛶 划向湖心岛", "target", "湖心岛", "", 330, 668);
        addButton(s, "🎮 现场来一局2048", "event", "", "2048", 640, 668);
        addButton(s, "返回岔路", "target", "起点", "", 950, 668);
        addMusic(s);
    }

    private static void buildIsland(GameProject p) {
        GameScene s = p.addScene("湖心岛");
        s.addNode(bg("images/background_lake.png"));
        addChar(s, "images/char_right.png", 870, 100, "立绘_满");
        addName(s, "小满", 895, 456, "#b8e6ff");
        StoryNode d = addDialog(s, "岛心立着一口覆满青苔的古箱，锁孔正缓缓亮起微光。\n"
                + "「选吧，」小满把捡到的枫叶递给你，「<u>每个选择都是一条支线</u>。」\n"
                + "---\n"
                + "古箱上刻着一行小字：<color:#ffe066>「四位数密码—— 2、0、4、8。」</color>",
                "对话_岛");
        d.setTypewriter(Boolean.TRUE);
        addButton(s, "📦 打开古箱", "target", "结局·秘宝", "", 330, 668);
        addButton(s, "🌙 看看湖心夜色", "target", "结局·晚霞", "", 640, 668);
        addButton(s, "回去再想想", "target", "湖畔", "", 950, 668);
        addMusic(s);
    }

    private static void buildEnding(GameProject p, String sceneName,
                                   String bgImage, String dialogText) {
        GameScene s = p.addScene(sceneName);
        s.addNode(bg(bgImage));
        addDialog(s, dialogText, "对话_结局");
        addButton(s, "🔁 再走一遍冒险", "target", "起点", "", 565, 668);
    }

    // =====================================================================
    // 节点快捷构造
    // =====================================================================

    private static StoryNode bg(String image) {
        StoryNode n = new StoryNode(NodeType.BACKGROUND, 0, 0, 1280, 720);
        n.setPath("resources/images/" + image);
        return n;
    }

    private static void addChar(GameScene s, String image, double x, double y, String id) {
        StoryNode n = new StoryNode(NodeType.CHARACTER, x, y, 340, 560);
        n.setId(id);
        n.setPath("resources/images/" + image);
        s.addNode(n);
    }

    private static void addName(GameScene s, String who, double x, double y, String color) {
        StoryNode n = new StoryNode(NodeType.NAME, x, y, 220, 46);
        n.setText(who);
        n.setStyle("-fx-text-fill: " + color + ";");
        s.addNode(n);
    }

    private static StoryNode addDialog(GameScene s, String text, String id) {
        StoryNode d = new StoryNode(NodeType.DIALOG, 90, 492, 1100, 176);
        d.setId(id);
        d.setText(text);
        s.addNode(d);
        return d;
    }

    private static void addButton(GameScene s, String label, String action,
                                  String target, String eventId, double x, double y) {
        StoryNode b = new StoryNode(NodeType.BUTTON, x, y, 300, 52);
        b.setText(label);
        b.setAction(action);
        if (target != null && !target.isBlank()) b.setTarget(target);
        if (eventId != null && !eventId.isBlank()) b.setEvent(eventId);
        b.setId("按钮_" + label);
        s.addNode(b);
    }

    private static StoryNode text(double x, double y, double w, double h, String content) {
        StoryNode t = new StoryNode(NodeType.TEXT, x, y, w, h);
        t.setText(content);
        t.setStyle("-fx-font-size: 22px; -fx-text-fill: #fff2d0;");
        return t;
    }

    private static void addMusic(GameScene s) {
        StoryNode m = new StoryNode(NodeType.MUSIC, 0, 0, 0, 0);
        m.setAudio("resources/audio/theme.wav");
        s.addNode(m);
    }

    /** 解析校验：生成后立刻按读取器同款解析器读回并报告场景数 */
    public static int validate(File mapDir) throws IOException {
        List<String> warnings = new java.util.ArrayList<>();
        GameProject back = ScriptParser.parse(new File(mapDir, "scenario.txt"), warnings);
        if (!warnings.isEmpty()) {
            throw new IOException("解析警告: " + warnings);
        }
        return back.scenes().size();
    }
}
