package com.studio.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.studio.flow.SignalDef;
import com.studio.flow.SlotDef;

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

    /** 场景级信号（主要是键盘：地图的全局事件监听器接收后分发） */
    private final List<SignalDef> signals = new ArrayList<>();

    /** 场景级槽（响应场景信号或任意节点信号） */
    private final List<SlotDef> slots = new ArrayList<>();

    /** 中文/英文别名 → 规范键 */
    public static final Map<String, String> SCENE_KEY_ALIAS = new LinkedHashMap<>();
    static {
        SCENE_KEY_ALIAS.put("event", "event");   SCENE_KEY_ALIAS.put("事件", "event");
        SCENE_KEY_ALIAS.put("next", "next");     SCENE_KEY_ALIAS.put("下一场景", "next"); SCENE_KEY_ALIAS.put("下个场景", "next");
        SCENE_KEY_ALIAS.put("end", "end");       SCENE_KEY_ALIAS.put("结束", "end");
        SCENE_KEY_ALIAS.put("signal", "signal"); SCENE_KEY_ALIAS.put("信号", "signal");
        SCENE_KEY_ALIAS.put("slot", "slot");     SCENE_KEY_ALIAS.put("槽", "slot"); SCENE_KEY_ALIAS.put("槽位", "slot");
    }

    public GameScene(String name) { setName(name); }

    public String getName() { return name; }
    public void setName(String name) { this.name = name == null ? "未命名场景" : name.trim(); }

    public Map<String, String> props() { return props; }
    public List<StoryNode> nodes() { return nodes; }

    /** 场景级信号列表（键盘全局监听器按此分发） */
    public List<SignalDef> signals() { return signals; }

    /** 场景级槽列表 */
    public List<SlotDef> slots() { return slots; }

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

    public void addNode(StoryNode node) {
        nodes.add(node);
        node.setIndex(nodes.size() - 1);
    }

    public void removeNode(StoryNode node) {
        nodes.remove(node);
        reindex();
    }

    public void removeNodeAt(int index) {
        nodes.remove(index);
        reindex();
    }

    /** 前移一层（向画面顶层移动） */
    public boolean bringForward(StoryNode node) {
        int i = nodes.indexOf(node);
        if (i < 0 || i >= nodes.size() - 1) return false;
        StoryNode t = nodes.remove(i);
        nodes.add(i + 1, t);
        reindex();
        return true;
    }

    /** 后移一层（向画面底层移动） */
    public boolean sendBackward(StoryNode node) {
        int i = nodes.indexOf(node);
        if (i <= 0) return false;
        StoryNode t = nodes.remove(i);
        nodes.add(i - 1, t);
        reindex();
        return true;
    }

    /** 移动 n 层（正数向上/向顶层，负数向下/向底层） */
    public boolean moveBy(StoryNode node, int delta) {
        int i = nodes.indexOf(node);
        if (i < 0 || delta == 0) return false;
        int target = Math.max(0, Math.min(nodes.size() - 1, i + delta));
        if (target == i) return false;
        StoryNode t = nodes.remove(i);
        nodes.add(target, t);
        reindex();
        return true;
    }

    /** 把节点移动到指定层级下标 */
    public boolean moveTo(StoryNode node, int newIndex) {
        int i = nodes.indexOf(node);
        if (i < 0) return false;
        int target = Math.max(0, Math.min(nodes.size() - 1, newIndex));
        if (target == i) return false;
        StoryNode t = nodes.remove(i);
        nodes.add(target, t);
        reindex();
        return true;
    }

    /** 把每个节点的 index 重新编号为它在列表中的位置（0 起） */
    public void reindex() {
        for (int i = 0; i < nodes.size(); i++) nodes.get(i).setIndex(i);
    }

    /**
     * 按节点的 index 稳定排序（解析脚本后调用）；
     * 排序完成后把 index 规范化为连续的 0..n-1。
     */
    public void sortByIndex() {
        List<StoryNode> copy = new ArrayList<>(nodes);
        copy.sort(java.util.Comparator.comparingInt(StoryNode::getIndex));
        nodes.clear();
        nodes.addAll(copy);
        reindex();
    }

    public boolean contains(StoryNode node) { return nodes.contains(node); }

    /**
     * 深拷贝本场景（撤销/恢复的快照用）：节点、场景属性、信号、槽全部复制一份，
     * 与原件互不影响。
     */
    public GameScene copy() {
        GameScene c = new GameScene(name);
        c.props.putAll(props);
        for (StoryNode n : nodes) c.nodes.add(n.copy());
        for (SignalDef s : signals) c.signals.add(s.copy());
        for (SlotDef s : slots) c.slots.add(s.copy());
        return c;
    }

    @Override
    public String toString() { return "场景[" + name + "](" + nodes.size() + " 个节点)"; }
}
