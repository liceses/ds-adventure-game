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
import java.util.ArrayList;
import java.util.List;

/**
 * “打砖块”示例地图工厂：把 breakout 插件接进地图编辑器与播放器。
 *
 * <p>与 {@link BranchMapFactory}（2048）、{@link MapTemplateFactory}（扫雷）、
 * {@link SaveRoomMapFactory}（存档台）保持同一套写法，演示两种触发方式：</p>
 * <pre>
 *   场景级 event : [砖墙迷宫] event=breakout —— 进入场景即嵌入打砖块插件
 *   按钮级 action: action=event + event=breakout —— 剧情中“顺手玩一把”直接呼出
 * </pre>
 *
 * <p>结构：</p>
 * <pre>
 *   [起点] ──▶ [砖墙迷宫](场景事件=breakout) ──▶ [结局·破墙之后]
 *      └(按钮事件=breakout 随时可玩)
 * </pre>
 *
 * <p>注意：{@code maps/} 已被 .gitignore 忽略，本类是<b>运行时生成</b>示例地图，
 * 与其它示例地图一致，不需要把生成结果提交进仓库
 * （编辑器的「文件 → 打开打砖块演示地图…」即调用本类）。
 */
public final class BreakoutMapFactory {

    /** 默认生成目录（相对工程根目录） */
    public static final String DEFAULT_FOLDER = "maps/demo_breakout";

    /** 触发打砖块插件的事件 ID，需与 plugins/plugins.ini 中的登记一致。 */
    public static final String EVENT_ID = "breakout";

    private BreakoutMapFactory() { }

    /** 生成打砖块示例地图（含占位素材） */
    public static GameProject createMap(File mapDir) throws IOException {
        if (!mapDir.exists() && !mapDir.mkdirs()) {
            throw new IOException("无法创建地图文件夹: " + mapDir);
        }
        GameProject project = new GameProject(mapDir);

        // ---------- 占位素材 ----------
        File res = mapDir.toPath().resolve("resources").toFile();
        Files.createDirectories(res.toPath().resolve("images"));
        Files.createDirectories(res.toPath().resolve("audio"));
        MapAssets.ensure(res, "images/background_hall.png", MapAssets.Kind.BACKGROUND, 211, null, 1280, 720);
        MapAssets.ensure(res, "images/background_wall.png", MapAssets.Kind.BACKGROUND, 227, null, 1280, 720);
        MapAssets.ensure(res, "images/background_dawn.png", MapAssets.Kind.BACKGROUND, 241, null, 1280, 720);
        MapAssets.ensure(res, "images/char_left.png", MapAssets.Kind.CHARACTER, 257, "阿澈", 340, 560);
        MapAssets.ensure(res, "images/char_right.png", MapAssets.Kind.CHARACTER, 263, "小满", 340, 560);
        MapAssets.ensureWav(res, "audio/theme.wav", 6.0, 196.0);

        // ---------- 全局设置 ----------
        GameOption opt = project.option();
        opt.setInitialScene("起点");
        opt.setBackground("#0b1020");
        opt.setVolume(0.75);
        opt.setTypewriterSpeed(14);

        // ---------- 场景 ----------
        buildStart(project);
        buildBrickHall(project);
        buildEnding(project);

        // ---------- 落盘 ----------
        ScriptWriter.write(project.scenarioFile(), project);
        return project;
    }

    // =====================================================================
    // 各场景
    // =====================================================================

    /** 起点：既有分支跳转，也有按钮级插件事件 */
    private static void buildStart(GameProject p) {
        GameScene s = p.addScene("起点");
        s.addNode(bg("images/background_hall.png"));
        addChar(s, "images/char_left.png", 70, 100, "立绘_澈");
        addChar(s, "images/char_right.png", 870, 100, "立绘_满");
        addName(s, "阿澈", 175, 456, "#ffe2a6");
        addName(s, "小满", 895, 456, "#b8e6ff");
        addDialog(s, "长廊尽头立着一面会自己重砌的<b>砖墙</b>。\n"
                + "「听说把砖全部打掉，墙后面的门就会开，」阿澈把小球抛了抛，\n"
                + "「而且每块砖的<b>颜色不同、硬度也不同</b>。」\n"
                + "---\n"
                + "小满已经蹲下来画了张图：「要么现在就 <u>来一局打砖块</u>，\n"
                + "要么先走到墙前面再说？」", "对话_长廊");
        addButton(s, "🧱 走向砖墙迷宫", "target", "砖墙迷宫", "", 330, 668);
        addButton(s, "🎮 现场来一局打砖块", "event", "", EVENT_ID, 640, 668);
        addButton(s, "存档(占位)", "save", "", "", 950, 668);
    }

    /** 场景级事件演示：进入即被打砖块插件接管 */
    private static void buildBrickHall(GameProject p) {
        GameScene s = p.addScene("砖墙迷宫");
        s.setEvent(EVENT_ID);
        s.addNode(bg("images/background_wall.png"));
        StoryNode note = text(90, 60, 1100, 130,
                "砖墙在你面前缓缓拼合，底部升起一块<b>挡板</b>……\n"
                + "（场景 event=breakout → 已嵌入 <color:#ffe066>打砖块小游戏</color>插件；"
                + "3 条命用尽或点上方【返回剧情】回到长廊）");
        note.setId("提示_打砖块");
        s.addNode(note);
        addMusic(s);
    }

    private static void buildEnding(GameProject p) {
        GameScene s = p.addScene("结局·破墙之后");
        s.addNode(bg("images/background_dawn.png"));
        addDialog(s, "最后一块砖落地的瞬间，墙后的门无声滑开，晨光铺了一地。\n"
                + "「你看，」小满把剩下的半盒粉笔塞给你，\n"
                + "「规则很简单——<u>接住球，别让它掉下去</u>。别的，都可以慢慢来。」\n"
                + "---\n"
                + "—— <color:#ffe066>结局·破墙之后：第一缕晨光</color>", "对话_结局");
        addButton(s, "🔁 再走一遍", "target", "起点", "", 565, 668);
    }

    // =====================================================================
    // 节点快捷构造（与 BranchMapFactory 保持一致）
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

    private static void addDialog(GameScene s, String content, String id) {
        StoryNode d = new StoryNode(NodeType.DIALOG, 90, 492, 1100, 176);
        d.setId(id);
        d.setText(content);
        s.addNode(d);
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
        List<String> warnings = new ArrayList<>();
        GameProject back = ScriptParser.parse(new File(mapDir, "scenario.txt"), warnings);
        if (!warnings.isEmpty()) {
            throw new IOException("解析警告: " + warnings);
        }
        return back.scenes().size();
    }
}
