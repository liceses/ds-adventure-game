package com.studio.flow;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 逻辑层上下文（传给地图工程师的 Java 逻辑类）——
 * 逻辑层只关心“逻辑”，渲染由引擎即刻完成。
 *
 * <p>典型用法（工程师在自己的类里）：</p>
 * <pre>
 *   public class 我的逻辑 implements LogicHandler {
 *       public void onSignal(FlowContext ctx, SignalEvent ev) {
 *           int n = ctx.intVar("点击次数", 0) + 1;
 *           ctx.setVar("点击次数", n);                  // 会被写入存档
 *           ctx.setText("计数", "点击次数: " + n);      // 立即重绘
 *           ctx.setStyle("灯", n % 2 == 1 ? "…亮…" : "…暗…");
 *           ctx.emit("灯", "亮灯", Map.of("n", n));     // 向其它节点发信号
 *       }
 *   }
 * </pre>
 */
public class FlowContext {

    private final FlowHost host;

    public FlowContext(FlowHost host) {
        this.host = host;
    }

    public FlowHost host() { return host; }

    public String scene() {
        return host.scene() == null ? "" : host.scene().getName();
    }

    public com.studio.model.StoryNode node(String id) { return host.node(id); }

    // ---------------- 渲染（引擎代劳） ----------------

    public void setProperty(String nodeId, String prop, String value) {
        host.setProperty(nodeId, prop, value);
    }

    /**
     * 带过渡动画地设置属性。
     *
     * @param transitionSpec 形如 {@code scale/opacity:300ms}（省略毫秒默认 300）
     */
    public void setProperty(String nodeId, String prop, String value, String transitionSpec) {
        host.setPropertyAnimated(nodeId, prop, value, transitionSpec);
    }

    public void setText(String nodeId, String text) { host.setProperty(nodeId, "text", text); }

    public void setStyle(String nodeId, String style) { host.setProperty(nodeId, "style", style); }

    public void setVisible(String nodeId, boolean visible) {
        host.setProperty(nodeId, "visible", visible ? "true" : "false");
    }

    public void setPosition(String nodeId, double x, double y) {
        host.setProperty(nodeId, "x", com.studio.model.StoryNode.trimDouble(x));
        host.setProperty(nodeId, "y", com.studio.model.StoryNode.trimDouble(y));
    }

    public String property(String nodeId, String prop) { return host.property(nodeId, prop); }

    // ---------------- 变量（随存档保存，可作为逻辑输入） ----------------

    public String var(String key, String def) { return host.variables().get(key, def); }

    public int intVar(String key, int def) { return host.variables().getInt(key, def); }

    public double doubleVar(String key, double def) { return host.variables().getDouble(key, def); }

    public boolean boolVar(String key, boolean def) { return host.variables().getBool(key, def); }

    public void setVar(String key, String value) { host.variables().set(key, value); }

    public void setVar(String key, int value) { host.variables().set(key, String.valueOf(value)); }

    public void addVar(String key, int delta) {
        host.variables().set(key, String.valueOf(host.variables().getInt(key, 0) + delta));
    }

    public String nodeVar(String nodeId, String key, String def) {
        return host.variables().getNodeVar(nodeId, key, def);
    }

    public void setNodeVar(String nodeId, String key, String value) {
        host.variables().setNodeVar(nodeId, key, value);
    }

    // ---------------- 信号 / 场景 / 存档 ----------------

    public void emit(String targetId, String signalName) {
        emit(targetId, signalName, Map.of());
    }

    public void emit(String targetId, String signalName, Map<String, Object> params) {
        host.emit(targetId, signalName, params == null ? Map.of() : params);
    }

    public void gotoScene(String sceneName) { host.gotoScene(sceneName); }

    public boolean saveSlot(String slot) { return host.saveSlot(slot); }

    public boolean loadSlot(String slot) { return host.loadSlot(slot); }

    public com.studio.saves.GameSaveManager saves() { return host.saves(); }

    public void toast(String message) { host.toast(message); }

    public void log(String message) { host.log(message); }

    /** 便捷：构造参数表 */
    public static Map<String, Object> params(Object... kv) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
