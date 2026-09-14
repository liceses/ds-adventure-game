package com.studio.editor;

import com.studio.model.GameProject;
import com.studio.model.GameScene;
import com.studio.model.StoryNode;
import com.studio.parser.ScriptParser;
import com.studio.parser.ScriptWriter;
import com.studio.util.Logs;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * <b>个性化节点</b>仓库：地图工程师把调好的节点存成“模板”，以后右键添加节点时直接套用。
 *
 * <p>全部模板存在<b>一个 txt 文件</b>里（工程根目录 {@code node-presets.txt}），
 * 格式与地图脚本一致 —— 每个模板就是一段 {@code [模板名]} + 一个 {@code { 节点属性 }} 块：</p>
 *
 * <pre>
 * [ds 娘立绘]
 * {
 * type = char
 * width = 300
 * height = 400
 * path = resources/chars/ds_cute.png
 * }
 * </pre>
 *
 * <p>这么做的好处：文件可以直接用文本编辑器改、可以拷给别人、也可以用编辑器打开检查；
 * 解析走的是地图同款 {@link ScriptParser}（宽容模式，写错不会炸），写盘走 {@link ScriptWriter}。</p>
 */
public final class NodePresetStore {

    /** 文件名（放在工程根目录，ASCII 命名，避免中文目录/文件名带来的各种麻烦） */
    public static final String FILE_NAME = "node-presets.txt";

    private NodePresetStore() { }

    /** 模板文件位置 */
    public static File file() {
        return new File(System.getProperty("user.dir"), FILE_NAME);
    }

    /** 一条个性化节点：名字 + 节点属性 */
    public static class Preset {
        private final String name;
        private final StoryNode node;

        public Preset(String name, StoryNode node) {
            this.name = name == null || name.isBlank() ? "未命名" : name.trim();
            this.node = node == null ? new StoryNode() : node;
        }

        public String getName() { return name; }
        public StoryNode getNode() { return node; }

        /** 列表里显示的类型列 */
        public String getTypeText() { return node.getType() == null ? "文本" : node.getType().display(); }

        /** 一句话摘要（尺寸 / 文本 / 样式 / 信号槽数量） */
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append((int) node.getWidth()).append("×").append((int) node.getHeight());
            if (node.getText() != null && !node.getText().isBlank()) {
                String t = node.getText().replace('\n', ' ');
                sb.append("　文字：").append(t.length() > 18 ? t.substring(0, 18) + "…" : t);
            }
            if (node.getPath() != null && !node.getPath().isBlank()) sb.append("　图片：").append(shorten(node.getPath()));
            if (node.getStyle() != null && !node.getStyle().isBlank()) sb.append("　带样式");
            if (!node.signals().isEmpty()) sb.append("　信号×").append(node.signals().size());
            if (!node.slots().isEmpty()) sb.append("　槽×").append(node.slots().size());
            return sb.toString();
        }

        private static String shorten(String s) {
            return s.length() > 24 ? "…" + s.substring(s.length() - 24) : s;
        }
    }

    // =====================================================================

    /** 读取全部模板（文件不存在返回空表） */
    public static List<Preset> load() {
        List<Preset> out = new ArrayList<>();
        File f = file();
        if (!f.isFile()) return out;
        try {
            List<String> warnings = new ArrayList<>();
            GameProject p = ScriptParser.parse(f, warnings);
            for (GameScene scene : p.scenes().values()) {
                if (scene.nodes().isEmpty()) continue;
                StoryNode node = scene.nodes().get(0).copy();
                node.setId("");          // 模板不带 id：新节点用自己的 id
                out.add(new Preset(scene.getName(), node));
            }
            if (!warnings.isEmpty()) {
                Logs.warn("[Presets] " + f.getName() + " 有 " + warnings.size() + " 条解析提示（已按宽容模式读取）");
            }
        } catch (Exception e) {
            Logs.warn("[Presets] 读取失败 " + f.getAbsolutePath() + "：" + e.getMessage());
        }
        return out;
    }

    /** 写回全部模板（按当前顺序） */
    public static void save(List<Preset> presets) throws IOException {
        GameProject p = new GameProject();   // 工程名由文件名/目录决定，这里不用设
        int i = 0;
        for (Preset preset : presets) {
            String name = uniqueSceneName(p, preset.getName());
            GameScene scene = new GameScene(name);
            StoryNode node = preset.getNode().copy();
            node.setId("");
            node.setIndex(0);
            scene.addNode(node);
            p.scenes().put(name, scene);
            i++;
        }
        ScriptWriter.write(file(), p);
        Logs.info("[Presets] 已保存 " + i + " 个个性化节点 → " + file().getAbsolutePath());
    }

    /** 追加一个模板（名字重复时自动加序号）并立刻落盘；返回最终名字 */
    public static String add(String name, StoryNode source) throws IOException {
        List<Preset> presets = load();
        String finalName = freeName(presets, name);
        StoryNode copy = source.copy();
        copy.setId("");
        presets.add(new Preset(finalName, copy));
        save(presets);
        return finalName;
    }

    /** 按名字删除；返回是否删掉了 */
    public static boolean remove(String name) throws IOException {
        List<Preset> presets = load();
        boolean removed = presets.removeIf(p -> p.getName().equals(name));
        if (removed) save(presets);
        return removed;
    }

    /** 重命名（列表窗口用）；返回最终名字 */
    public static String rename(String oldName, String newName) throws IOException {
        List<Preset> presets = load();
        String finalName = freeName(presets, newName);
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).getName().equals(oldName)) {
                presets.set(i, new Preset(finalName, presets.get(i).getNode()));
                break;
            }
        }
        save(presets);
        return finalName;
    }

    /** 文件里是否已经有这个模板名 */
    public static boolean exists(String name) {
        for (Preset p : load()) if (p.getName().equals(name)) return true;
        return false;
    }

    /** 建议的默认模板名（取节点 id 或类型名） */
    public static String suggestName(StoryNode node) {
        if (node == null) return "新模板";
        String id = node.getId() == null ? "" : node.getId().trim();
        if (!id.isEmpty()) return id;
        return node.getType() == null ? "新模板" : node.getType().display() + "模板";
    }

    // =====================================================================

    /** 在现有名字里找一个不冲突的（重名时加 _2、_3 …） */
    private static String freeName(List<Preset> presets, String want) {
        String base = want == null || want.isBlank() ? "未命名" : want.trim();
        String name = base;
        int n = 2;
        while (containsName(presets, name)) name = base + "_" + (n++);
        return name;
    }

    private static boolean containsName(List<Preset> presets, String name) {
        for (Preset p : presets) if (p.getName().equals(name)) return true;
        return false;
    }

    /** 写盘时保证场景名唯一（ScriptWriter/解析器都按场景名索引） */
    private static String uniqueSceneName(GameProject p, String name) {
        String base = name == null || name.isBlank() ? "未命名" : name.trim();
        String out = base;
        int n = 2;
        while (p.scenes().containsKey(out)) out = base + "_" + (n++);
        return out;
    }

    /** 模板文件内容预览（列表窗口的“查看文件”用） */
    public static String readRaw() {
        try {
            File f = file();
            return f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "(读取失败：" + e.getMessage() + ")";
        }
    }
}
