package com.studio.flow;

import com.studio.model.GameScene;
import com.studio.model.SaveVarDef;
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
 *   <li>把鼠标/键盘/逻辑层/插件发出的信号按名称匹配（支持 {@code *} 通配）；</li>
 *   <li>执行内置渲染动作（set/toggle/emit/goto/save/load/log）、
 *       调用插件（{@code @plugin(名)}）、或把信号转交地图工程师的逻辑类（call）；</li>
 *   <li>把信号分发给用 {@link PluginContext#subscribe} 订阅过的插件。</li>
 * </ol>
 *
 * <p>所有字段都支持 {@link Expr 表达式}：{@code @var(名)}、{@code @double(1.05)}、
 * {@code @node(属性名)}、{@code @param(名)} 等。</p>
 */
public class SignalBus {

    private final FlowHost host;
    private final LogicLoader loader;
    private PluginRuntime pluginRuntime;

    public SignalBus(FlowHost host, LogicLoader loader) {
        this.host = host;
        this.loader = loader;
    }

    /**
     * 插件运行时（首次使用时按工程/地图目录创建）。
     * <p>{@code synchronized} 是必要的：多个线程可能同时首次调用，
     * 若各自创建出不同实例就会各持一把锁，导致变量并发更新丢失。</p>
     */
    public synchronized PluginRuntime plugins() {
        if (pluginRuntime == null) {
            pluginRuntime = new PluginRuntime(host.projectDir(), host.mapDir());
        }
        return pluginRuntime;
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
        // 再分发给订阅了该信号的插件（无论有没有匹配的槽）
        int notified = plugins().notifySignal(new FlowContext(host), event);
        if (matched == 0 && notified == 0) {
            Logs.info("[Flow] 信号 " + event.signal() + " 没有匹配的槽（来源 " + event.sourceKind() + "）");
        }
    }

    private static boolean matches(String slotSignal, String eventSignal) {
        return "*".equals(slotSignal) || slotSignal.equals(eventSignal);
    }

    private record SlotTarget(SlotDef slot, StoryNode owner) { }

    // =====================================================================
    // 表达式作用域（@var / @node / @param / 强制转换）
    // =====================================================================

    /** 把信号/槽语境包装成 {@link Expr.Scope} */
    private final class BusScope implements Expr.Scope {

        private final StoryNode owner;
        private final SignalEvent event;
        private final Map<String, Object> params;

        BusScope(StoryNode owner, SignalEvent event, Map<String, Object> params) {
            this.owner = owner;
            this.event = event;
            this.params = params;
        }

        @Override
        public String var(String name, String def) {
            return host.variables().get(name, def);
        }

        @Override
        public void setVar(String name, String value) {
            host.variables().setTyped(name, value, host.saveVarDefs());
        }

        @Override
        public String nodeProp(String nodeId, String prop) {
            String id = nodeId;
            if (id == null || id.isBlank()) id = sourceNodeId();
            if (id == null || id.isBlank()) return "";
            if ("@self".equals(id) || "self".equals(id)) {
                id = owner != null ? owner.getId() : sourceNodeId();
            }
            String v = host.property(id, prop);
            if (v == null || v.isEmpty()) {
                // 属性覆盖里没有时，退回节点模型里的静态属性（x/y/text/visible…）
                StoryNode n = host.node(id);
                if (n != null) v = nodeStaticProp(n, prop);
            }
            return v == null ? "" : v;
        }

        @Override
        public String sourceNodeId() {
            if (event != null && event.sourceId() != null && !event.sourceId().isBlank()) return event.sourceId();
            return owner == null ? "" : owner.getId();
        }

        @Override
        public String param(String name) {
            Object v = params.get(name);
            return v == null ? "" : String.valueOf(v);
        }
    }

    /** 节点模型上的静态属性（供 {@code @node(属性名)} 取值；也供编辑器/探针复用） */
    public static String nodeStaticProp(StoryNode n, String prop) {
        if (n == null || prop == null) return "";
        switch (prop.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "id": return n.getId();
            case "type": return n.getTypeCode();
            case "x": return StoryNode.trimDouble(n.getX());
            case "y": return StoryNode.trimDouble(n.getY());
            case "width": case "w": return StoryNode.trimDouble(n.getWidth());
            case "height": case "h": return StoryNode.trimDouble(n.getHeight());
            case "text": return n.getText();
            case "path": return n.getPath();
            case "video": return n.getVideo();
            case "audio": return n.getAudio();
            case "style": return n.getStyle();
            case "visible": return n.isVisible() ? "true" : "false";
            case "opacity": return StoryNode.trimDouble(n.getOpacity());
            case "fontsize": return StoryNode.trimDouble(n.getFontSize());
            case "align": return n.getAlign();
            case "index": return String.valueOf(n.getIndex());
            case "transition": return n.getTransition();
            case "bind": return n.getBind();                 // 文本框绑定的存档变量名
            case "multiline": return n.isMultiline() ? "true" : "false";
            case "action": return n.getAction();
            case "target": return n.getTarget();
            case "event": return n.getEvent();
            case "typewriter": return n.typewriterEffective() ? "true" : "false";
            default: return "";
        }
    }

    // =====================================================================
    // 槽执行
    // =====================================================================

    private void execute(SlotDef slot, StoryNode owner, SignalEvent event) {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>(event.params());
        params.putAll(slot.params());
        Expr.Scope scope = new BusScope(owner, event, params);
        String target = resolveTarget(slot, owner, event);
        String action = slot.getAction() == null ? "" : slot.getAction().trim();
        // 整条槽的执行（读变量 → 插件运算 → 回写变量）都放在引擎锁内，保证并发下不丢更新
        java.util.concurrent.locks.ReentrantLock busLock = plugins().lock();
        busLock.lock();
        try {
            if (slot.isPlugin() || action.startsWith("plugin:")) {
                runPlugin(slot, event, scope);
                return;
            }
            switch (action) {
                case "set" -> {
                    String prop = Expr.resolve(slot.getArg(), scope);
                    String value = resolveValue(params, scope);
                    String transition = Expr.resolve(str(params.get("transition")), scope);
                    if (target.startsWith("@var(")) {
                        // 直接写存档变量（“传出”用）：目标写成 @var(变量名)
                        String varName = Expr.varRefName(target);
                        VarType t = declaredType(varName);
                        host.variables().set(varName, t == null ? value : t.cast(value));
                        Logs.info("[Flow] set " + target + " = " + brief(value));
                    } else if (!transition.isEmpty()) {
                        host.setPropertyAnimated(target, prop, value, transition);
                        Logs.info("[Flow] set " + target + "." + prop + " = " + brief(value) + "（过渡 " + transition + "）");
                    } else {
                        host.setProperty(target, prop, value);
                        Logs.info("[Flow] set " + target + "." + prop + " = " + brief(value));
                    }
                }
                case "toggle" -> {
                    String prop = slot.getArg().isBlank() ? "visible" : Expr.resolve(slot.getArg(), scope);
                    String cur = host.property(target, prop);
                    boolean now = !("true".equalsIgnoreCase(cur) || "1".equals(cur) || "是".equals(cur));
                    host.setProperty(target, prop, now ? "true" : "false");
                    Logs.info("[Flow] toggle " + target + "." + prop + " → " + now);
                }
                case "emit" -> {
                    String signal = slot.getArg().isBlank()
                            ? Expr.resolve(str(params.get("signal")), scope) : Expr.resolve(slot.getArg(), scope);
                    if (!target.isBlank()) {
                        StoryNode n = host.node(target);
                        emitFromNode(n, signal, new LinkedHashMap<>(params));
                    } else {
                        emitFromScene(signal, new LinkedHashMap<>(params));
                    }
                }
                case "goto" -> {
                    String scene = slot.getArg().isBlank()
                            ? Expr.resolve(str(params.get("scene")), scope) : Expr.resolve(slot.getArg(), scope);
                    host.gotoScene(scene);
                }
                case "save" -> host.saveSlot(Expr.resolve(slotArgOrParam(slot, params, "slot", "slot1"), scope));
                case "load" -> host.loadSlot(Expr.resolve(slotArgOrParam(slot, params, "slot", "slot1"), scope));
                case "call" -> loader.invoke(Expr.resolve(slot.getArg(), scope), new FlowContext(host),
                        new SignalEvent(event.signal(), event.sourceKind(), event.sourceId(),
                                event.scene(), event.keyCode(), event.mouseButton(), params));
                case "log" -> {
                    String msg = slot.getArg().isBlank()
                            ? Expr.resolve(str(params.get("message")), scope) : Expr.resolve(slot.getArg(), scope);
                    Logs.info("[Flow] " + msg);
                    host.toast(msg);
                }
                default -> Logs.warn("[Flow] 未知槽动作: " + action
                        + "（内置动作: set/toggle/emit/goto/save/load/call/log，插件: @plugin(名)）");
            }
        } catch (RuntimeException e) {
            Logs.error("[Flow] 槽执行异常 " + slot, e);
        } finally {
            busLock.unlock();
        }
    }

    /**
     * 执行 {@code @plugin(名)} 槽。
     *
     * <p>动作之后的所有字段先按表达式求值成字符串数组传给插件，
     * 插件返回同长度数组后，<b>只有原本写成 {@code @var(x)} 的位置</b>会把结果写回变量
     * （并按外层强制转换 / [option] 声明类型规范化）。</p>
     */
    private void runPlugin(SlotDef slot, SignalEvent event, Expr.Scope scope) {
        String id = slot.isPlugin() ? slot.pluginId() : slot.getAction().substring("plugin:".length()).trim();
        if (id.isEmpty()) {
            Logs.warn("[Flow] @plugin(...) 未指定插件名");
            return;
        }
        String[] raw = slot.pluginArgs();
        String[] values = new String[raw.length];
        for (int i = 0; i < raw.length; i++) values[i] = Expr.resolve(raw[i], scope);

        String[] out = plugins().execute(id, new FlowContext(host), event, values, raw);
        if (out == null) return;
        int n = Math.min(raw.length, out.length);
        int last = raw.length - 1;
        for (int i = 0; i < n; i++) {
            if (!Expr.isVarRef(raw[i])) continue;       // 只回写 @var(...) 位置
            // 回写规则：① 输出位（最后一个参数）永远回写；
            //           ② 其它位置仅当插件确实改过它的值才回写。
            // 这样 “@int(@var(灯1))” 这类“带转换的输入参数”不会被结果覆盖掉
            //（插件约定：最后一个参数是输出位置，前面的都是输入）。
            boolean isOutputSlot = (i == last);
            boolean changed = !java.util.Objects.equals(values[i], out[i]);
            if (!isOutputSlot && !changed) continue;
            String name = Expr.varRefName(raw[i]);
            if (name.isEmpty()) continue;
            VarType t = Expr.castType(raw[i]);
            if (t == null) t = declaredType(name);
            String v = t == null ? out[i] : t.cast(out[i]);
            host.variables().set(name, v);
            Logs.info("[Flow] @plugin(" + id + ") → " + name + " = " + brief(v));
        }
    }

    /** 变量在 [option] 里声明的类型（未声明返回 null） */
    private VarType declaredType(String name) {
        List<SaveVarDef> defs = host.saveVarDefs();
        if (defs == null || name == null) return null;
        for (SaveVarDef d : defs) {
            if (d != null && name.equals(d.getName())) return d.getType();
        }
        return null;
    }

    private static String slotArgOrParam(SlotDef slot, Map<String, Object> params, String key, String def) {
        if (!slot.getArg().isBlank()) return slot.getArg();
        String v = str(params.get(key));
        return v.isBlank() ? def : v;
    }

    /**
     * set 槽取值：{@code value=字面值/表达式}；{@code valueVar=变量名}；
     * 都没有则空串。都支持 {@code @var/@double/@node} 表达式。
     */
    private String resolveValue(Map<String, Object> params, Expr.Scope scope) {
        String literal = str(params.get("value"));
        if (!literal.isEmpty()) return Expr.resolve(literal, scope);
        String varName = str(params.get("valueVar"));
        if (!varName.isEmpty()) {
            String plain = Expr.varRefName(varName);
            if (!plain.isEmpty()) return host.variables().get(plain, "");
            return Expr.resolve(varName, scope);
        }
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
