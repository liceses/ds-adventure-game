package com.studio.flow;

import com.studio.saves.SaveData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 剧情运行期变量仓库（信号/槽与逻辑层共享）。
 *
 * <p>三类数据都会被写入存档（saves/*.txt）：</p>
 * <ul>
 *   <li>地图变量：{@code var.<key>} —— 逻辑层的“存档数据”，读档后可作为变量影响逻辑；</li>
 *   <li>节点变量：{@code nodevar.<节点id>.<key>} —— 单个节点上的私有数据；</li>
 *   <li>节点属性覆盖：{@code prop.<节点id>.<属性>} —— 逻辑层改过的渲染属性，
 *       读档/重新进入场景时由引擎自动重新应用（保证画面一致）。</li>
 * </ul>
 */
public class FlowVariables {

    private final LinkedHashMap<String, String> mapVars = new LinkedHashMap<>();
    private final LinkedHashMap<String, LinkedHashMap<String, String>> nodeVars = new LinkedHashMap<>();
    private final LinkedHashMap<String, LinkedHashMap<String, String>> propOverrides = new LinkedHashMap<>();

    // ---------------- 地图变量 ----------------

    public String get(String key, String def) {
        String v = mapVars.get(key);
        return v == null ? def : v;
    }

    public void set(String key, String value) {
        if (key == null || key.isBlank()) return;
        mapVars.put(key, value == null ? "" : value);
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public double getDouble(String key, double def) {
        try {
            return Double.parseDouble(get(key, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean getBool(String key, boolean def) {
        String v = get(key, def ? "1" : "0").trim();
        return v.equals("1") || v.equalsIgnoreCase("true") || v.equals("是");
    }

    public Map<String, String> mapVars() { return mapVars; }

    // ---------------- 节点变量 ----------------

    public String getNodeVar(String nodeId, String key, String def) {
        Map<String, String> m = nodeVars.get(nodeId);
        if (m == null) return def;
        String v = m.get(key);
        return v == null ? def : v;
    }

    public void setNodeVar(String nodeId, String key, String value) {
        if (nodeId == null || nodeId.isBlank() || key == null || key.isBlank()) return;
        nodeVars.computeIfAbsent(nodeId, k -> new LinkedHashMap<>()).put(key, value == null ? "" : value);
    }

    public Map<String, ? extends Map<String, String>> nodeVars() { return nodeVars; }

    // ---------------- 属性覆盖 ----------------

    public void recordProp(String nodeId, String prop, String value) {
        if (nodeId == null || nodeId.isBlank() || prop == null || prop.isBlank()) return;
        propOverrides.computeIfAbsent(nodeId, k -> new LinkedHashMap<>()).put(prop, value == null ? "" : value);
    }

    /** 某节点的全部属性覆盖（供进入场景时重新应用） */
    public Map<String, String> propsOf(String nodeId) {
        Map<String, String> m = propOverrides.get(nodeId);
        return m == null ? Map.of() : m;
    }

    public Map<String, ? extends Map<String, String>> propOverrides() { return propOverrides; }

    /** 读取某个属性的覆盖值（无则返回 def） */
    public String prop(String nodeId, String propName, String def) {
        Map<String, String> m = propOverrides.get(nodeId);
        if (m == null) return def;
        String v = m.get(propName);
        return v == null ? def : v;
    }

    public void clear() {
        mapVars.clear();
        nodeVars.clear();
        propOverrides.clear();
    }

    // ---------------- 存档读写 ----------------

    public void writeTo(SaveData data) {
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

    public void readFrom(SaveData data) {
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
    public String toString() {
        return "FlowVariables{地图变量=" + mapVars.size()
                + ", 节点变量=" + nodeVars.size()
                + ", 属性覆盖=" + propOverrides.size() + "}";
    }
}
