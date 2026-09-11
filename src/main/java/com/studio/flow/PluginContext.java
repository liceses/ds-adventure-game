package com.studio.flow;

import com.studio.model.SaveVarDef;
import com.studio.saves.GameSaveManager;
import com.studio.saves.SaveData;
import com.studio.util.Logs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 插件上下文 —— 交给 {@link SlotPlugin} 的能力集合。
 *
 * <p>三类能力：</p>
 * <ol>
 *   <li><b>存档变量</b>：{@link #var}/{@link #setVar} 等，写入的值按变量声明类型
 *       <b>强制转换</b>，类型不符不会报错；变量随存档一起保存。</li>
 *   <li><b>存档文件</b>：{@link #readSave}/{@link #writeSave}/{@link #deleteSave}/{@link #listSaves}
 *       —— 直接读写 {@code 地图/saves/*.txt}。</li>
 *   <li><b>信号</b>：{@link #emit} 发信号、{@link #subscribe} 订阅信号（回调 {@link SlotPlugin#onSignal}）。</li>
 * </ol>
 *
 * <p><b>线程安全</b>：引擎用一把可重入锁串行化“插件执行 + 变量读写 + 存档读写 + 信号派发”，
 * 需要把多步操作做成原子的请用 {@link #locked}；所有渲染相关操作会在内部自动切回
 * JavaFX 线程，插件作者无需自己处理线程问题。</p>
 */
public class PluginContext {

    private final FlowContext flow;
    private final PluginRuntime runtime;
    private final String pluginId;
    private SignalEvent event;

    public PluginContext(FlowContext flow, PluginRuntime runtime, String pluginId) {
        this.flow = flow;
        this.runtime = runtime;
        this.pluginId = pluginId;
    }

    /** 绑定本次调用的事件（引擎内部使用） */
    public PluginContext withEvent(SignalEvent event) {
        this.event = event;
        return this;
    }

    public String pluginId() { return pluginId; }

    /** 引擎宿主（可读地图目录、主音量等） */
    public com.studio.flow.FlowHost host() { return flow == null ? null : flow.host(); }

    /** 当前地图文件夹（插件解析相对素材路径用），未知返回 null */
    public java.io.File mapDir() { return host() == null ? null : host().mapDir(); }

    public String scene() { return flow == null ? "" : flow.scene(); }

    /** 触发本次调用的信号（经 subscribe 收到信号时为该事件的信号） */
    public SignalEvent event() { return event; }

    public String signalName() { return event == null ? "" : event.signal(); }

    public String sourceNodeId() { return event == null ? "" : event.sourceId(); }

    // =====================================================================
    // 存档变量
    // =====================================================================

    public String var(String name) { return var(name, ""); }

    public String var(String name, String def) {
        if (name == null || name.isBlank()) return def;
        return locked(() -> flow.var(name, def));
    }

    public int intVar(String name) { return intVar(name, 0); }

    public int intVar(String name, int def) {
        return (int) Math.rint(doubleVar(name, def));
    }

    public double doubleVar(String name) { return doubleVar(name, 0); }

    public double doubleVar(String name, double def) {
        String v = var(name, String.valueOf(def));
        try {
            return Double.parseDouble(v.trim());
        } catch (RuntimeException e) {
            return def;
        }
    }

    public boolean boolVar(String name) { return boolVar(name, false); }

    public boolean boolVar(String name, boolean def) {
        String v = var(name, def ? "true" : "false").trim().toLowerCase(java.util.Locale.ROOT);
        if (v.equals("true") || v.equals("1") || v.equals("是") || v.equals("yes") || v.equals("on")) return true;
        if (v.equals("false") || v.equals("0") || v.equals("否") || v.equals("no") || v.equals("off")) return false;
        return def;
    }

    /** 写变量：按声明的类型强制转换（未声明的变量按原样存字符串） */
    public void setVar(String name, String value) {
        if (name == null || name.isBlank()) return;
        locked(() -> {
            VarType t = declaredType(name);
            flow.setVar(name, t == null ? (value == null ? "" : value) : t.cast(value));
            return null;
        });
    }

    public void setVar(String name, double value) { setVar(name, com.studio.model.StoryNode.trimDouble(value)); }

    public void setVar(String name, int value) { setVar(name, String.valueOf(value)); }

    public void setVar(String name, boolean value) { setVar(name, value ? "true" : "false"); }

    public void addVar(String name, double delta) { setVar(name, doubleVar(name) + delta); }

    /** 变量在 [option] 里声明的类型（未声明返回 null） */
    public VarType declaredType(String name) {
        if (flow == null || flow.host() == null) return null;
        List<SaveVarDef> defs = flow.host().saveVarDefs();
        if (defs == null) return null;
        for (SaveVarDef d : defs) {
            if (d.getName().equals(name)) return d.getType();
        }
        return null;
    }

    // =====================================================================
    // 存档文件（maps/xxx/saves/*.txt）
    // =====================================================================

    /** 读存档槽位；不存在或读失败返回 null */
    public SaveData readSave(String slot) {
        return locked(() -> {
            GameSaveManager m = saves();
            return m == null ? null : m.read(slot);
        });
    }

    /** 写存档槽位（自动建目录） */
    public boolean writeSave(String slot, SaveData data) {
        return Boolean.TRUE.equals(locked(() -> {
            GameSaveManager m = saves();
            if (m == null || data == null) return false;
            try {
                m.ensureDir();
                m.write(slot, data);
                return true;
            } catch (Exception e) {
                Logs.warn("[Plugin] 写存档失败 " + slot + "：" + e.getMessage());
                return false;
            }
        }));
    }

    public boolean deleteSave(String slot) {
        return Boolean.TRUE.equals(locked(() -> {
            GameSaveManager m = saves();
            return m != null && m.delete(slot);
        }));
    }

    /** 已有存档槽位名列表（如 slot1/slot2） */
    public List<String> listSaves() {
        List<String> out = locked(() -> {
            GameSaveManager m = saves();
            return m == null ? new ArrayList<String>() : new ArrayList<>(m.listSaveFiles());
        });
        return out == null ? new ArrayList<>() : out;
    }

    /** 存档管理器（想直接用底层 API 也可以） */
    public GameSaveManager saves() {
        return flow == null || flow.host() == null ? null : flow.host().saves();
    }

    /** 新建一份空的存档数据（供 readSave 结果为空时兜底） */
    public SaveData newSaveData() { return new SaveData(); }

    /** 读某槽位的变量（{@code var.名称}），不存在返回 def —— 便于插件读取“另一份存档” */
    public String saveVar(String slot, String name, String def) {
        SaveData d = readSave(slot);
        if (d == null || name == null) return def;
        return d.getString("var." + name, def);
    }

    /** 写某槽位的变量（会把存档整体读出来改一行再写回；槽位不存在则新建） */
    public boolean setSaveVar(String slot, String name, String value) {
        SaveData d = readSave(slot);
        if (d == null) d = new SaveData();
        d.put("var." + name, value);
        return writeSave(slot, d);
    }

    // =====================================================================
    // 信号：发出 / 接收
    // =====================================================================

    public void emit(String targetId, String signalName) {
        emit(targetId, signalName, new LinkedHashMap<>());
    }

    /** 向目标节点（targetId 为空 = 场景）发信号 */
    public void emit(String targetId, String signalName, Map<String, Object> params) {
        if (signalName == null || signalName.isBlank()) return;
        onUi(() -> flow.emit(targetId, signalName, params == null ? Map.of() : params));
    }

    /** 向场景发信号（场景级信号，由地图全局监听器分发） */
    public void emitScene(String signalName, Map<String, Object> params) {
        emit("", signalName, params);
    }

    /**
     * 订阅信号：匹配的信号派发时，本插件的 {@link SlotPlugin#onSignal} 会被调用。
     * 支持 {@code *} 通配（订阅全部信号）。
     */
    public void subscribe(String signalPattern) {
        runtime.subscribe(pluginId, signalPattern);
    }

    public void unsubscribe(String signalPattern) {
        runtime.unsubscribe(pluginId, signalPattern);
    }

    // =====================================================================
    // 渲染（引擎代劳，自动切回 JavaFX 线程）
    // =====================================================================

    public void setProperty(String nodeId, String prop, String value) {
        onUi(() -> flow.setProperty(nodeId, prop, value));
    }

    public void setProperty(String nodeId, String prop, String value, String transition) {
        onUi(() -> flow.setProperty(nodeId, prop, value, transition));
    }

    public void setText(String nodeId, String text) { onUi(() -> flow.setText(nodeId, text)); }

    public void setStyle(String nodeId, String style) { onUi(() -> flow.setStyle(nodeId, style)); }

    public void setVisible(String nodeId, boolean visible) { onUi(() -> flow.setVisible(nodeId, visible)); }

    public String property(String nodeId, String prop) { return locked(() -> flow.property(nodeId, prop)); }

    public void gotoScene(String sceneName) { onUi(() -> flow.gotoScene(sceneName)); }

    public void toast(String message) { onUi(() -> flow.toast(message)); }

    public void log(String message) {
        Logs.info("[Plugin:" + pluginId + "] " + message);
        if (flow != null && flow.host() != null) flow.host().log("[Plugin:" + pluginId + "] " + message);
    }

    // =====================================================================
    // 线程安全
    // =====================================================================

    /** 引擎的插件锁（可重入）；插件内部多线程访问共享状态时可自行加锁 */
    public ReentrantLock lock() { return runtime.lock(); }

    /** 在引擎锁内执行一段操作，保证与其它插件/槽的变量、存档访问互斥 */
    public <T> T locked(Supplier<T> action) {
        ReentrantLock l = runtime.lock();
        l.lock();
        try {
            return action.get();
        } finally {
            l.unlock();
        }
    }

    /** 是否在 JavaFX 线程上 */
    public boolean isUiThread() {
        return flow == null || flow.host() == null || flow.host().isUiThread();
    }

    /** 确保在 JavaFX 线程上执行（渲染类操作必须走这里） */
    public void onUi(Runnable action) {
        if (flow == null || flow.host() == null) {
            action.run();
            return;
        }
        flow.host().runOnUiThread(action);
    }
}
