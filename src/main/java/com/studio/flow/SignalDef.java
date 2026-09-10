package com.studio.flow;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 信号定义（挂在节点或场景上）：描述“这个对象能发出什么信号”。
 *
 * <p>脚本一行写法（见 {@link SignalCodec}）：</p>
 * <pre>
 *   signal = 点击 | mouse | click | 强度=1
 *   signal = 快捷键F | key | F | press | 提示=切换
 * </pre>
 */
public class SignalDef {

    public enum Kind { MOUSE, KEY }

    /** 信号名（槽按名字订阅） */
    private String name = "";
    private Kind kind = Kind.MOUSE;
    /** MOUSE：click / release */
    private String mouse = "click";
    /** KEY：按键码（如 F、SPACE、LEFT），大写 */
    private String key = "";
    /** KEY：press / release */
    private String keyPhase = "press";
    /** 随信号一起携带的静态附加参数 */
    private final LinkedHashMap<String, String> params = new LinkedHashMap<>();

    public SignalDef() { }

    public SignalDef(String name, Kind kind) {
        this.name = name;
        this.kind = kind;
    }

    public static SignalDef mouse(String name, String mouseEvent) {
        SignalDef s = new SignalDef(name, Kind.MOUSE);
        s.mouse = mouseEvent;
        return s;
    }

    public static SignalDef key(String name, String keyCode) {
        return key(name, keyCode, "press");
    }

    public static SignalDef key(String name, String keyCode, String phase) {
        SignalDef s = new SignalDef(name, Kind.KEY);
        s.key = keyCode == null ? "" : keyCode.toUpperCase();
        s.keyPhase = phase;
        return s;
    }

    public SignalDef param(String k, String v) {
        params.put(k, v);
        return this;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name == null ? "" : name.trim(); }

    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind == null ? Kind.MOUSE : kind; }
    public void setKind(String kind) { this.kind = "key".equalsIgnoreCase(kind) ? Kind.KEY : Kind.MOUSE; }

    public String getMouse() { return mouse; }
    public void setMouse(String mouse) { this.mouse = mouse == null ? "click" : mouse; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key == null ? "" : key.toUpperCase(); }

    public String getKeyPhase() { return keyPhase; }
    public void setKeyPhase(String keyPhase) { this.keyPhase = keyPhase == null ? "press" : keyPhase; }

    public Map<String, String> params() { return params; }

    public SignalDef copy() {
        SignalDef c = new SignalDef(name, kind);
        c.mouse = mouse;
        c.key = key;
        c.keyPhase = keyPhase;
        c.params.putAll(params);
        return c;
    }

    @Override
    public String toString() {
        return kind == Kind.KEY
                ? "信号[" + name + " key=" + key + "/" + keyPhase + "]"
                : "信号[" + name + " mouse=" + mouse + "]";
    }
}
