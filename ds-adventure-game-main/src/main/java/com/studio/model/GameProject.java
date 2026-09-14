package com.studio.model;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 剧情地图工程：一个“地图 = 一个文件夹 = 一个剧情游戏”。
 * <p>
 * 典型文件夹结构：
 * <pre>
 *   maps/我的地图/
 *   ├── scenario.txt            ← 场景脚本（[option] [场景] {节点} ...）
 *   └── resources/              ← 图片/音频/视频/CSS 等素材
 * </pre>
 */
public class GameProject {

    /** 地图文件夹 */
    private File rootDir;

    /** 场景名 → 场景（保持顺序，即脚本文件中的出现顺序） */
    private final LinkedHashMap<String, GameScene> scenes = new LinkedHashMap<>();

    /** 全局设置 */
    private final GameOption option = new GameOption();

    /** 最近一次解析产生的警告（编辑器可展示给用户） */
    private final List<String> warnings = new ArrayList<>();

    // ---------------- 构造 ----------------

    public GameProject() { }

    public GameProject(File rootDir) { this.rootDir = rootDir; }

    // ---------------- 访问器 ----------------

    public File rootDir() { return rootDir; }
    public void setRootDir(File rootDir) { this.rootDir = rootDir; }

    /** 地图文件夹名（也是“地图名”） */
    public String name() {
        return rootDir != null ? rootDir.getName() : "未命名地图";
    }

    public GameOption option() { return option; }

    public Map<String, GameScene> scenes() { return scenes; }

    public List<String> warnings() { return warnings; }

    /**
     * 深拷贝整个工程（编辑器“撤销/恢复”的快照用）。
     * <p>场景、节点、信号/槽、[option] 全部复制一份，与原件互不影响。</p>
     */
    public GameProject copy() {
        GameProject p = new GameProject(rootDir);
        p.option.copyFrom(option);
        for (Map.Entry<String, GameScene> e : scenes.entrySet()) {
            p.scenes.put(e.getKey(), e.getValue().copy());
        }
        p.warnings.addAll(warnings);
        return p;
    }

    // ---------------- 场景管理 ----------------

    public GameScene firstScene() {
        return scenes.isEmpty() ? null : scenes.values().iterator().next();
    }

    /** 初始场景（配置指定，回退到第一个场景） */
    public GameScene initialScene() {
        String name = option.initialScene();
        GameScene s = scenes.get(name);
        return s != null ? s : firstScene();
    }

    public boolean hasScene(String name) { return scenes.containsKey(name); }

    public GameScene addScene(String name) {
        GameScene s = new GameScene(uniqueSceneName(name));
        scenes.put(s.getName(), s);
        return s;
    }

    public GameScene getScene(String name) { return scenes.get(name); }

    public boolean removeScene(String name) {
        if (scenes.size() <= 1) return false; // 至少保留一个场景
        return scenes.remove(name) != null;
    }

    public boolean renameScene(String oldName, String newName) {
        if (!scenes.containsKey(oldName) || scenes.containsKey(newName)) return false;
        GameScene s = scenes.remove(oldName);
        s.setName(newName);
        scenes.put(newName, s);
        // 更新指向旧名的跳转目标
        for (GameScene sc : scenes.values()) {
            for (StoryNode n : sc.nodes()) {
                if (oldName.equals(n.getTarget())) n.setTarget(newName);
            }
            if (oldName.equals(sc.next())) sc.setNext(newName);
        }
        return true;
    }

    public String uniqueSceneName(String base) {
        String b = base == null || base.isBlank() ? "新场景" : base.trim();
        if (!scenes.containsKey(b)) return b;
        int i = 2;
        while (scenes.containsKey(b + "_" + i)) i++;
        return b + "_" + i;
    }

    /** 统计全部节点的总数 */
    public int totalNodes() {
        return scenes.values().stream().mapToInt(s -> s.nodes().size()).sum();
    }

    /** scenario.txt 文件位置 */
    public File scenarioFile() {
        return new File(rootDir, "scenario.txt");
    }

    public File resourcesDir() {
        return new File(rootDir, "resources");
    }

    @Override
    public String toString() {
        return "GameProject{" + name() + ", " + scenes.size() + " 个场景, " + totalNodes() + " 个节点}";
    }
}
