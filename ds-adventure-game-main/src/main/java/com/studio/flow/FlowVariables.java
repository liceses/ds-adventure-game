package com.studio.flow;

import com.studio.model.SaveVarDef;
import com.studio.saves.SaveData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 剧情运行期变量仓库（信号/槽、逻辑层与插件共享）。
 *
 * <p>三类数据都会被写入存档（saves/*.txt）：</p>
 * <ul>
 *   <li>地图变量：{@code var.<key>} —— 逻辑层的“存档数据”，读档后可作为变量影响逻辑；</li>
 *   <li>节点变量：{@code nodevar.<节点id>.<key>} —— 单个节点上的私有数据；</li>
 *   <li>节点属性覆盖：{@code prop.<节点id>.<属性>} —— 逻辑层改过的渲染属性，
 *       读档/重新进入场景时由引擎自动重新应用（保证画面一致）。</li>
 * </ul>
 *
 * <p><b>线程安全</b>：所有读写方法都是 {@code synchronized} 的，
 * 插件线程 / 逻辑线程 / JavaFX 线程并发访问不会破坏内部结构；
 * 需要“读-改-写”原子性时请配合 {@link PluginContext#locked} 使用。</p>
 */
public class FlowVariables {

    private final LinkedHashMap<String, String> mapVars = new LinkedHashMap<>();
    private final LinkedHashMap<String, LinkedHashMap<String, String>> nodeVars = new LinkedHashMap<>();
    private final LinkedHashMap<String, LinkedHashMap<String, String>> propOverrides = new LinkedHashMap<>();

    // ---------------- 地图变量 ----------------

    public synchronized String get(String key, String def) {
        String v = mapVars.get(key);
        return v == null ? def : v;
    }

    public synchronized void set(String key, String value) {
        if (key == null || key.isBlank()) return;
        mapVars.put(key, value == null ? "" : value);
    }

    public synchronized int getInt(String key, int def) {
        try {
            return (int) Math.rint(Double.parseDouble(get(key, String.valueOf(def)).trim()));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public synchronized double getDouble(String key, double def) {
        try {
            return Double.parseDouble(get(key, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public synchronized boolean getBool(String key, boolean def) {
        String v = get(key, def ? "1" : "0").trim();
        return v.equals("1") || v.equalsIgnoreCase("true") || v.equals("是");
    }

    /** 变量是否存在（用于区分“未设置”与“空值”） */
    public synchronized boolean has(String key) { return mapVars.containsKey(key); }

    public synchronized Map<String, String> mapVars() { return new LinkedHashMap<>(mapVars); }

    /**
     * 按 [option] 里的存档变量声明补齐初值：
     * 只填充“当前不存在”的变量，不会覆盖运行中已经改过的值。
     */
    public synchronized void applyDefaults(List<SaveVarDef> defs) {
        if (defs == null) return;
        for (SaveVarDef d : defs) {
            if (d == null || !d.isValid()) continue;
            mapVars.putIfAbsent(d.getName(), d.getType().cast(d.getInitial()));
        }
    }

    /** 按声明类型写入（找不到声明则原样存） */
    public synchronized void setTyped(String key, String value, List<SaveVarDef> defs) {
        VarType t = null;
        if (defs != null) {
            for (SaveVarDef d : defs) {
                if (d != null && d.getName().equals(key)) { t = d.getType(); break; }
            }
        }
        set(key, t == null ? value : t.cast(value));
    }

    // ---------------- 节点变量 ----------------

    public synchronized String getNodeVar(String nodeId, String key, String def) {
        Map<String, String> m = nodeVars.get(nodeId);
        if (m == null) return def;
        String v = m.get(key);
        return v == null ? def : v;
    }

    public synchronized void setNodeVar(String nodeId, String key, String value) {
        if (nodeId == null || nodeId.isBlank() || key == null || key.isBlank()) return;
        nodeVars.computeIfAbsent(nodeId, k -> new LinkedHashMap<>()).put(key, value == null ? "" : value);
    }

    public synchronized Map<String, ? extends Map<String, String>> nodeVars() { return nodeVars; }

    // ---------------- 属性覆盖 ----------------

    public synchronized void recordProp(String nodeId, String prop, String value) {
        if (nodeId == null || nodeId.isBlank() || prop == null || prop.isBlank()) return;
        propOverrides.computeIfAbsent(nodeId, k -> new LinkedHashMap<>()).put(prop, value == null ? "" : value);
    }

    /** 某节点的全部属性覆盖（供进入场景时重新应用） */
    public synchronized Map<String, String> propsOf(String nodeId) {
        Map<String, String> m = propOverrides.get(nodeId);
        return m == null ? Map.of() : new LinkedHashMap<>(m);
    }

    public synchronized Map<String, ? extends Map<String, String>> propOverrides() { return propOverrides; }

    /** 读取某个属性的覆盖值（无则返回 def） */
    public synchronized String prop(String nodeId, String propName, String def) {
        Map<String, String> m = propOverrides.get(nodeId);
        if (m == null) return def;
        String v = m.get(propName);
        return v == null ? def : v;
    }

    public synchronized void clear() {
        mapVars.clear();
        nodeVars.clear();
        propOverrides.clear();
    }

    // ---------------- 存档读写 ----------------

    public synchronized void writeTo(SaveData data) {
        for (Map.Entry<String, String> e : mapVars.entrySet()) {
            data.put("var." + e.getKey(), e.getValue());
        }
        for (Map.Entry<String, LinkedHashMap<String, String>> n : nodeVars.entrySet()) {
            for (Map.Entry<String, String> e : n.getValue().entrySet()) {
                data.put("nodevar." + n.getKey() + "." + e.getKey(), e.getValue());
            }
        }
        for (Map.Entry<String, LinkedHashMap<String, String>> n : propOverrides.entrySet()) {
            for (Map.Entry<String, String> e : n.getValue().entrySet()) {
                data.put("prop." + n.getKey() + "." + e.getKey(), e.getValue());
            }
        }
    }

    public synchronized void readFrom(SaveData data) {
        clear();
        if (data == null) return;
        for (String key : data.keys()) {
            String value = data.getString(key, "");
            if (key.startsWith("var.")) {
                mapVars.put(key.substring(4), value);
            } else if (key.startsWith("nodevar.")) {
                String[] parts = split3(key.substring(8));
                if (parts != null) {
                    nodeVars.computeIfAbsent(parts[0], k -> new LinkedHashMap<>()).put(parts[1], value);
                }
            } else if (key.startsWith("prop.")) {
                String[] parts = split3(key.substring(5));
                if (parts != null) {
                    propOverrides.computeIfAbsent(parts[0], k -> new LinkedHashMap<>()).put(parts[1], value);
                }
            }
        }
    }

    /** 把 "节点id.属性" 拆成两段（属性名可能含点，故只按第一个点拆） */
    private static String[] split3(String s) {
        int dot = s.indexOf('.');
        if (dot <= 0 || dot >= s.length() - 1) return null;
        return new String[]{s.substring(0, dot), s.substring(dot + 1)};
    }

    @Override
    public synchronized String toString() {
        return "FlowVariables{地图变量=" + mapVars.size()
                + ", 节点变量=" + nodeVars.size()
                + ", 属性覆盖=" + propOverrides.size() + "}";
    }
}
