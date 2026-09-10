package com.studio.flow;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次信号派发事件（传给槽与逻辑层）。
 *
 * <p>参数合并优先级（后者覆盖前者）：事件上下文（按键/鼠标等）
 * → 信号定义上的静态附带参数 → 槽上的附带参数。</p>
 */
public record SignalEvent(
        String signal,        // 信号名
        String sourceKind,    // "node" / "scene"
        String sourceId,      // 节点 id（场景信号为 ""）
        String scene,         // 当前场景名
        String keyCode,       // 按键码（键盘信号，否则 ""）
        String mouseButton,   // 鼠标事件（"click"/"release"，否则 ""）
        Map<String, Object> params   // 已合并的参数
) {

    public static SignalEvent of(String signal, String sourceKind, String sourceId,
                                 String scene, Map<String, Object> params) {
        return new SignalEvent(signal, sourceKind, sourceId, scene, "", "", params);
    }

    public String param(String key, String def) {
        Object v = params == null ? null : params.get(key);
        return v == null ? def : String.valueOf(v);
    }

    public int intParam(String key, int def) {
        try {
            return Integer.parseInt(param(key, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public SignalEvent withParam(String key, Object value) {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>(
                params == null ? Map.of() : params);
        merged.put(key, value);
        return new SignalEvent(signal, sourceKind, sourceId, scene, keyCode, mouseButton, merged);
    }

    @Override
    public String toString() {
        return "信号[" + signal + "] 来源=" + sourceKind + ":" + sourceId
                + (keyCode.isEmpty() ? "" : " 键=" + keyCode)
                + (mouseButton.isEmpty() ? "" : " 鼠标=" + mouseButton)
                + " 参数=" + params;
    }
}
