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
 * 地图模板工厂 —— 纯模型层组装“标准 AVG 界面”的初始地图。
 *
 * <p>“文件→新建地图”与读取器首次运行的示例地图都复用这里：
 * 先在内存中构建出符合语法的 {@link GameProject}，再统一走
 * {@link ScriptWriter} 落盘 —— 编辑器新建内容与导出内容天然同构。</p>
 *
 * 初始画面（1280x720 逻辑分辨率，类似经典 AVG 立绘+对话框界面）：
 * <pre>
 *   ┌─────────────────────────────────────┐
 *   │   背景(全屏)                          │
 *   │  左立绘              右立绘            │
 *   │ [星野遥]          [苏晚晴]             │
 *   │  ┌──────── 对话内容 ────────┐        │
 *   │  └──────────────────────────┘        │
 *   │  [跳过][存档][读档][加速]  (demo加[前往])│
 *   └─────────────────────────────────────┘
 * </pre>
 * 按钮均绑定“占位动作”（读取器内输出日志即可）。
 */
public final class MapTemplateFactory {

    /** 逻辑画布尺寸 */
    public static final double CANVAS_W = 1280.0;
    public static final double CANVAS_H = 720.0;

    private MapTemplateFactory() { }

    /**
     * 在 mapDir 下生成一个完整可运行的初始地图。
     *
     * @param mapDir    地图文件夹（不存在则创建）
     * @param demoFlow  true：附带 [Forest] 插件事件场景的演示流程；
     *                  false：仅标准 AVG 初始场景（新建地图模板）
     */
    public static GameProject createMap(File mapDir, boolean demoFlow) throws IOException {
        if (!mapDir.exists() && !mapDir.mkdirs()) {
            throw new IOException("无法创建地图文件夹: " + mapDir);
        }
        GameProject project = new GameProject(mapDir);

        // ---------- 生成占位素材 ----------
        File res = mapDir.toPath().resolve("resources").toFile();
        Files.createDirectories(res.toPath().resolve("images"));
        Files.createDirectories(res.toPath().resolve("audio"));
        MapAssets.ensure(res, "images/background.png", MapAssets.Kind.BACKGROUND, 7, null, 1280, 720);
        MapAssets.ensure(res, "images/hero_left.png", MapAssets.Kind.CHARACTER, 11, "星野遥", 340, 560);
        MapAssets.ensure(res, "images/hero_right.png", MapAssets.Kind.CHARACTER, 23, "苏晚晴", 340, 560);
        if (demoFlow) {
            MapAssets.ensure(res, "images/background_forest.png", MapAssets.Kind.BACKGROUND, 31, null, 1280, 720);
            MapAssets.ensureWav(res, "audio/theme.wav", 6.0, 220.0);
        }

        // ---------- 模型组装 ----------
        GameOption opt = project.option();
        opt.setInitialScene("Start");
        opt.setBackground("#0d0f1c");
        opt.setVolume(0.8);
        opt.setTypewriterSpeed(14);

        buildStartScene(project, demoFlow);
        if (demoFlow) buildForestScene(project);

        // ---------- 落盘 ----------
        ScriptWriter.write(project.scenarioFile(), project);
        return project;
    }

    // =====================================================================

    private static void buildStartScene(GameProject project, boolean demoFlow) {
        GameScene s = project.addScene("Start");

        StoryNode bg = n(NodeType.BACKGROUND, 0, 0, CANVAS_W, CANVAS_H);
        bg.setPath("resources/images/background.png");
        s.addNode(bg);

        // 左右立绘
        StoryNode left = n(NodeType.CHARACTER, 40, 100, 340, 560);
        left.setId("立绘_遥");
        left.setPath("resources/images/hero_left.png");
        s.addNode(left);

        StoryNode right = n(NodeType.CHARACTER, 900, 100, 340, 560);
        right.setId("立绘_晴");
        right.setPath("resources/images/hero_right.png");
        s.addNode(right);

        // 左下角 / 右下角：人物名字 Label
        StoryNode nameLeft = n(NodeType.NAME, 170, 452, 220, 46);
        nameLeft.setText("星野遥");
        nameLeft.setStyle("-fx-text-fill: #ffe2a6;");
        s.addNode(nameLeft);

        StoryNode nameRight = n(NodeType.NAME, 890, 452, 220, 46);
        nameRight.setText("苏晚晴");
        nameRight.setStyle("-fx-text-fill: #b8e6ff;");
        s.addNode(nameRight);

        // 中间区域：对话内容框（对话框节点）
        StoryNode dialog = n(NodeType.DIALOG, 90, 492, 1100, 180);
        if (demoFlow) {
            dialog.setText("「欢迎来到 <color:#ffe066>星语之森</color>，<b>冒险者</b>！」\n"
                    + "我是 <u>星野遥</u>，这是剧情编辑器生成的<b>示例地图</b>。\n"
                    + "点击下方的 <color:#8fe3ff>前往森林</color>，会触发一个扫雷小游戏插件哦。");
        } else {
            dialog.setText("「这是新建地图的<b>初始模板</b>。」\n"
                    + "双击任意物件即可编辑属性；也可以从左侧工具箱拖出新物件。\n"
                    + "按 <color:#ffe066>Ctrl+S</color> 保存脚本，或到 文件→导出 生成独立地图。");
        }
        dialog.setId("对话_主");
        s.addNode(dialog);

        // 底部按钮（按顺序：跳过/存档/读档/加速 + demo 的跳转按钮）
        String[] labels = demoFlow
                ? new String[]{"跳过", "存档", "读档", "加速", "▶ 前往森林"}
                : new String[]{"跳过", "存档", "读档", "加速"};
        String[] actions = demoFlow
                ? new String[]{"skip", "save", "load", "speed", "target"}
                : new String[]{"skip", "save", "load", "speed"};

        double bw = 150, bh = 52, gap = 16;
        double total = labels.length * bw + (labels.length - 1) * gap;
        double startX = (CANVAS_W - total) / 2;
        for (int i = 0; i < labels.length; i++) {
            // y = 668，使按钮底边与 720 逻辑画布底边对齐
            StoryNode btn = n(NodeType.BUTTON, startX + i * (bw + gap), 668, bw, bh);
            btn.setId("按钮_" + labels[i]);
            btn.setText(labels[i]);
            btn.setAction(actions[i]);
            if (actions[i].equals("target")) btn.setTarget("Forest");
            s.addNode(btn);
        }
    }

    /** 演示场景：进入即被扫雷插件接管，点【返回】回到 Start */
    private static void buildForestScene(GameProject project) {
        GameScene s = project.addScene("Forest");
        s.setEvent("minesweeper");      // ← 场景事件：进入该场景时由读取器动态加载插件

        StoryNode bg = n(NodeType.BACKGROUND, 0, 0, CANVAS_W, CANVAS_H);
        bg.setPath("resources/images/background_forest.png");
        s.addNode(bg);

        // 进入即被插件覆盖，本段文字作为“插件加载前”的备用说明
        StoryNode tip = n(NodeType.TEXT, 90, 60, 1100, 120);
        tip.setText("你走进了森林深处…… 一道传送门把你们拉进了<b>扫雷小游戏</b>！\n"
                + "（场景 event 属性触发了 <color:#ffe066>minesweeper</color> 插件，"
                + "点击上方【返回】可回到上一场景）");
        tip.setStyle("-fx-font-size: 22px; -fx-text-fill: #fff2d0;");
        s.addNode(tip);

        // 背景音乐轨（隐藏节点）
        StoryNode music = n(NodeType.MUSIC, 0, 0, 0, 0);
        music.setAudio("resources/audio/theme.wav");
        s.addNode(music);
    }

    private static StoryNode n(NodeType t, double x, double y, double w, double h) {
        StoryNode node = new StoryNode(t, x, y, w, h);
        return node;
    }
}
