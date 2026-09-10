package com.studio.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 剧情场景（对应 scenario.txt 中 [场景名] 到下一个 [场景名] 之间的内容）。
 * <p>
 * 场景级属性（如 event 插件事件、next 下一场景）按“键值行”保存于
 * {@link #props}，顺序即文件顺序；读取脚本 ⇄ 序列化无损往返。
 */
public class GameScene {

    /** 场景标题（[ ... ] 中的名称） */
    private String name;

    /** 场景级键值属性（event / next 等，保持读取顺序） */
    private final LinkedHashMap<String, String> props = new LinkedHashMap<>();

    /** 场景内的全部节点，顺序 = 层级顺序（后加入者在上层） */
    private final List<StoryNode> nodes = new ArrayList<>();

    /** 中文/英文别名 → 规范键 */
    public static final Map<String, String> SCENE_KEY_ALIAS = new LinkedHashMap<>();
    static {
        SCENE_KEY_ALIAS.put("event", "event");   SCENE_KEY_ALIAS.put("事件", "event");
        SCENE_KEY_ALIAS.put("next", "next");     SCENE_KEY_ALIAS.put("下一场景", "next"); SCENE_KEY_ALIAS.put("下个场景", "next");
        SCENE_KEY_ALIAS.put("end", "end");       SCENE_KEY_ALIAS.put("结束", "end");
    }

    public GameScene(String name) { setName(name); }

    public String getName() { return name; }
    public void setName(String name) { this.name = name == null ? "未命名场景" : name.trim(); }

    public Map<String, String> props() { return props; }
    public List<StoryNode> nodes() { return nodes; }

    // ---------------- 便捷访问场景级属性 ----------------

    /** 读取规范化后的属性（别名→规范键） */
    public String prop(String canonicalKey) {
        return props.get(canonicalKey);
    }

    public void setProp(String canonicalKey, String value) {
        if (value == null || value.isEmpty()) props.remove(canonicalKey);
        else props.put(canonicalKey, value);
    }

    /** 场景事件（插件 ID）：进入该场景时由读取器触发 */
    public String event() { return prop("event"); }
    public void setEvent(String pluginId) { setProp("event", pluginId); }

    /** 可选：无按钮时的“下一场景” */
    public String next() { return prop("next"); }
    public void setNext(String sceneName) { setProp("next", sceneName); }

    /** 场景级未知键别名归一化写入（解析器用） */
    public void putAliasedProperty(String rawKey, String value) {
        String canonical = SCENE_KEY_ALIAS.getOrDefault(rawKey, rawKey);
        setProp(canonical, value);
    }

    // ---------------- 节点管理 ----------------

    public void addNode(StoryNode node) { nodes.add(node); }

    public void removeNode(StoryNode node) { nodes.remove(node); }

    public void removeNodeAt(int index) { nodes.remove(index); }

    /** 前移一层（向画面顶层移动） */
    public boolean bringForward(StoryNode node) {
        int i = nodes.indexOf(node);
        if (i < 0 || i >= nodes.size() - 1) return false;
        StoryNode t = nodes.remove(i);
        nodes.add(i + 1, t);
        return true;
    }

    /** 后移一层（向画面底层移动） */
    public boolean sendBackward(StoryNode node) {
        int i = nodes.indexOf(node);
        if (i <= 0) return false;
        StoryNode t = nodes.remove(i);
        nodes.add(i - 1, t);
        return true;
    }

    public boolean contains(StoryNode node) { return nodes.contains(node); }

    @Override
    public String toString() { return "场景[" + name + "](" + nodes.size() + " 个节点)"; }
}
