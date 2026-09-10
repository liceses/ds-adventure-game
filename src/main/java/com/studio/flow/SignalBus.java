package com.studio.flow;

import com.studio.model.GameScene;
import com.studio.model.StoryNode;
import com.studio.util.Logs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 信号总线：地图级“信号 → 槽”派发器（渲染层执行者）。
 *
 * <p>职责：</p>
 * <ol>
 *   <li>收集当前场景的槽（场景槽 + 各节点槽）；</li>
 *   <li>把鼠标/键盘/逻辑层发出的信号按名称匹配（支持 {@code *} 通配）；</li>
 *   <li>执行内置渲染动作（set/toggle/emit/goto/save/load/log），
 *       或把信号转交地图工程师的逻辑类（call）。</li>
 * </ol>
 */
public class SignalBus {

    private final FlowHost host;
    private final LogicLoader loader;

    public SignalBus(FlowHost host, LogicLoader loader) {
        this.host = host;
        this.loader = loader;
    }

    // =====================================================================
    // 发信号
    // =====================================================================

    /** 节点发信号：附带该信号定义上的静态参数 */
    public void emitFromNode(StoryNode source, String signalName, Map<String, Object> ctxParams) {
        if (source == null || signalName == null || signalName.isBlank()) return;
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        if (ctxParams != null) params.putAll(ctxParams);
        SignalDef def = findNodeSignal(source, signalName);
        if (def != null) params.putAll(def.params());
        dispatch(new SignalEvent(signalName, "node", source.getId(), sceneName(),
                def != null && def.getKind() == SignalDef.Kind.KEY ? def.getKey() : "",
                def != null && def.getKind() == SignalDef.Kind.MOUSE ? def.getMouse() : "",
                params));
    }

    /** 场景发信号（键盘等全局信号） */
    public void emitFromScene(String signalName, Map<String, Object> ctxParams) {
        if (signalName == null || signalName.isBlank()) return;
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        if (ctxParams != null) params.putAll(ctxParams);
        SignalDef def = findSceneSignal(signalName);
        if (def != null) params.putAll(def.params());
        dispatch(new SignalEvent(signalName, "scene", "", sceneName(),
                def != null && def.getKind() == SignalDef.Kind.KEY ? def.getKey() : "",
                "", params));
    }

    private String sceneName() {
        GameScene s = host.scene();
        return s == null ? "" : s.getName();
    }

    private static SignalDef findNodeSignal(StoryNode node, String name) {
        for (SignalDef d : node.signals()) {
            if (d.getName().equals(name)) return d;
        }
        return null;
    }

    private SignalDef findSceneSignal(String name) {
        GameScene s = host.scene();
        if (s == null) return null;
        for (SignalDef d : s.signals()) {
            if (d.getName().equals(name)) return d;
        }
        return null;
    }

    // =====================================================================
    // 派发
    // =====================================================================

    public void dispatch(SignalEvent event) {
        GameScene scene = host.scene();
        if (scene == null) return;
        List<SlotTarget> slots = new ArrayList<>();
        for (SlotDef slot : scene.slots()) slots.add(new SlotTarget(slot, null));
        for (StoryNode node : scene.nodes()) {
            for (SlotDef slot : node.slots()) slots.add(new SlotTarget(slot, node));
        }
        int matched = 0;
        for (SlotTarget st : slots) {
            if (!matches(st.slot.getSignal(), event.signal())) continue;
            matched++;
            execute(st.slot, st.owner, event);
        }
        if (matched == 0) {
            Logs.info("[Flow] 信号 " + event.signal() + " 没有匹配的槽（来源 " + event.sourceKind() + "）");
        }
    }

    private static boolean matches(String slotSignal, String eventSignal) {
        return "*".equals(slotSignal) || slotSignal.equals(eventSignal);
    }

    private record SlotTarget(SlotDef slot, StoryNode owner) { }

    // =====================================================================
    // 槽执行
    // =====================================================================

    private void execute(SlotDef slot, StoryNode owner, SignalEvent event) {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>(event.params());
        params.putAll(slot.params());
        String target = resolveTarget(slot, owner, event);
        try {
            switch (slot.getAction()) {
                case "set" -> {
                    String prop = slot.getArg();
                    String value = resolveValue(params);
                    String transition = str(params.get("transition"));
                    if (!transition.isEmpty()) {
                        host.setPropertyAnimated(target, prop, value, transition);
                    } else {
                        host.setProperty(target, prop, value);
                    }
                    Logs.info("[Flow] set " + target + "." + prop + " = " + brief(value)
                            + (transition.isEmpty() ? "" : "（过渡 " + transition + "）"));
                }
                case "toggle" -> {
                    String prop = slot.getArg().isBlank() ? "visible" : slot.getArg();
                    String cur = host.property(target, prop);
                    boolean now = !("true".equalsIgnoreCase(cur) || "1".equals(cur) || "是".equals(cur));
                    host.setProperty(target, prop, now ? "true" : "false");
                    Logs.info("[Flow] toggle " + target + "." + prop + " → " + now);
                }
                case "emit" -> {
                    String signal = slot.getArg().isBlank() ? str(params.get("signal")) : slot.getArg();
                    if (!target.isBlank()) {
                        StoryNode n = host.node(target);
                        emitFromNode(n, signal, new LinkedHashMap<>(params));
                    } else {
                        emitFromScene(signal, new LinkedHashMap<>(params));
                    }
                }
                case "goto" -> host.gotoScene(slot.getArg().isBlank() ? str(params.get("scene")) : slot.getArg());
                case "save" -> host.saveSlot(slotArgOrParam(slot, params, "slot", "slot1"));
                case "load" -> host.loadSlot(slotArgOrParam(slot, params, "slot", "slot1"));
                case "call" -> loader.invoke(slot.getArg(), new FlowContext(host),
                        new SignalEvent(event.signal(), event.sourceKind(), event.sourceId(),
                                event.scene(), event.keyCode(), event.mouseButton(), params));
                case "log" -> {
                    String msg = slot.getArg().isBlank() ? str(params.get("message")) : slot.getArg();
                    Logs.info("[Flow] " + msg);
                    host.toast(msg);
                }
                default -> Logs.warn("[Flow] 未知槽动作: " + slot.getAction());
            }
        } catch (RuntimeException e) {
            Logs.error("[Flow] 槽执行异常 " + slot, e);
        }
    }

    private static String slotArgOrParam(SlotDef slot, Map<String, Object> params, String key, String def) {
        if (!slot.getArg().isBlank()) return slot.getArg();
        String v = str(params.get(key));
        return v.isBlank() ? def : v;
    }

    /** set 槽取值：value=字面值；valueVar=从变量仓库取；都没有则空串 */
    private String resolveValue(Map<String, Object> params) {
        String literal = str(params.get("value"));
        if (!literal.isEmpty()) return literal;
        String varName = str(params.get("valueVar"));
        if (!varName.isEmpty()) return host.variables().get(varName, "");
        return "";
    }

    private String resolveTarget(SlotDef slot, StoryNode owner, SignalEvent event) {
        String t = slot.getTarget();
        if (t == null || t.isBlank()) {
            if ("node".equals(event.sourceKind())) return event.sourceId();
            return owner == null ? "" : owner.getId();
        }
        if ("@self".equals(t)) {
            if ("node".equals(event.sourceKind())) return event.sourceId();
            return owner == null ? "" : owner.getId();
        }
        return t;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String brief(String s) {
        if (s == null) return "";
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }
}
