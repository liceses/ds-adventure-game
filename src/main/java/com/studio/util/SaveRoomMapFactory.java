package com.studio.util;

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
 * “存档实验室”示例地图：演示 <b>3 槽存档台</b>（保存/读取/删除）。
 *
 * <pre>
 *   [起点]
 *     · 对话介绍 3 槽存档机制
 *     · 💾 打开存档台 —— action=event, event=savepanel（引擎内置插件）
 *     · 🌲 森林小屋 / 🌊 湖畔小屋（先存档→再走远→读档跳回）
 *   [森林小屋]  [湖畔小屋]
 * </pre>
 *
 * 存档台插件界面对每个槽位提供 保存/读取/删除；读取成功后自动跳回存档时的场景。
 * 地图工程师可在此基础上把存档按钮换成自己的 UI，或实现 {@code SaveHook} 增删变量。
 */
public final class SaveRoomMapFactory {

    /** 默认生成目录（相对工程根目录） */
    public static final String DEFAULT_FOLDER = "maps/demo_save_room";

    private SaveRoomMapFactory() { }

    public static GameProject createMap(File mapDir) throws IOException {
        if (!mapDir.exists() && !mapDir.mkdirs()) {
            throw new IOException("无法创建地图文件夹: " + mapDir);
        }
        GameProject project = new GameProject(mapDir);

        // 占位素材
        File res = mapDir.toPath().resolve("resources").toFile();
        Files.createDirectories(res.toPath().resolve("images"));
        MapAssets.ensure(res, "images/background_room.png", MapAssets.Kind.BACKGROUND, 131, null, 1280, 720);
        MapAssets.ensure(res, "images/background_forest.png", MapAssets.Kind.BACKGROUND, 147, null, 1280, 720);
        MapAssets.ensure(res, "images/background_lake.png", MapAssets.Kind.BACKGROUND, 163, null, 1280, 720);
        MapAssets.ensure(res, "images/char_left.png", MapAssets.Kind.CHARACTER, 179, "阿澈", 340, 560);
        MapAssets.ensure(res, "images/char_right.png", MapAssets.Kind.CHARACTER, 197, "小满", 340, 560);

        GameOption opt = project.option();
        opt.setInitialScene("起点");
        opt.setBackground("#10131f");
        opt.setVolume(0.75);
        opt.setTypewriterSpeed(14);

        buildStart(project);
        buildForest(project);
        buildLake(project);

        ScriptWriter.write(project.scenarioFile(), project);
        return project;
    }

    // =====================================================================

    private static void buildStart(GameProject p) {
        GameScene s = p.addScene("起点");
        s.addNode(bg("images/background_room.png"));
        addChar(s, "images/char_left.png", 70, 100, "立绘_澈");
        addChar(s, "images/char_right.png", 870, 100, "立绘_满");
        addName(s, "阿澈", 175, 456, "#ffe2a6");
        addName(s, "小满", 895, 456, "#b8e6ff");
        StoryNode d = new StoryNode(NodeType.DIALOG, 90, 492, 1100, 176);
        d.setId("对话_介绍");
        d.setText("这里是<b>存档实验室</b>：地图文件夹下会自动出现 saves/ 目录，"
                + "里面是 <color:#8fe3ff>slot1.txt / slot2.txt / slot3.txt</color> 三个存档。\n"
                + "点击下方<b>存档台</b>按钮可 保存/读取/删除；"
                + "试试：先存档 → 走到森林/湖畔 → 再回来读档。\n"
                + "（存档内容支持 {变量: 数值} 语法与 # 注释，详见 saves 目录文件）");
        s.addNode(d);
        addButton(s, "💾 打开存档台(3槽)", "event", "", "savepanel", 330, 668);
        addButton(s, "🌲 去森林小屋", "target", "森林小屋", "", 640, 668);
        addButton(s, "🌊 去湖畔小屋", "target", "湖畔小屋", "", 950, 668);
    }

    private static void buildForest(GameProject p) {
        GameScene s = p.addScene("森林小屋");
        s.addNode(bg("images/background_forest.png"));
        addChar(s, "images/char_left.png", 70, 100, "立绘_澈");
        addName(s, "阿澈", 175, 456, "#ffe2a6");
        StoryNode d = new StoryNode(NodeType.DIALOG, 90, 492, 1100, 176);
        d.setId("对话_森林");
        d.setText("你在森林小屋发现一张便签：\n"
                + "「<color:#ffe066>存档请用 slot2</color>，别和起点档混在一起哦。」\n"
                + "—— 打开存档台 → 保存到槽2 → 再去湖畔，试试能否读回这里。");
        s.addNode(d);
        addButton(s, "💾 打开存档台(3槽)", "event", "", "savepanel", 330, 668);
        addButton(s, "🌊 去湖畔小屋", "target", "湖畔小屋", "", 640, 668);
        addButton(s, "↩ 回起点", "target", "起点", "", 950, 668);
    }

    private static void buildLake(GameProject p) {
        GameScene s = p.addScene("湖畔小屋");
        s.addNode(bg("images/background_lake.png"));
        addChar(s, "images/char_right.png", 870, 100, "立绘_满");
        addName(s, "小满", 895, 456, "#b8e6ff");
        StoryNode d = new StoryNode(NodeType.DIALOG, 90, 492, 1100, 176);
        d.setId("对话_湖畔");
        d.setText("湖畔的晚风里，小满把一枚<b>枫叶书签</b>塞进你手心：\n"
                + "「读档会回到<b>存档时的场景</b>——这是给旅行者的保险。」\n"
                + "试试打开存档台 → 读取 slot2（森林档）→ 会瞬移回森林小屋。");
        s.addNode(d);
        addButton(s, "💾 打开存档台(3槽)", "event", "", "savepanel", 330, 668);
        addButton(s, "🌲 去森林小屋", "target", "森林小屋", "", 640, 668);
        addButton(s, "↩ 回起点", "target", "起点", "", 950, 668);
    }

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
}
