package com.studio.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局设置（对应 scenario.txt 中的 [option] 段）。
 * <p>
 * 全部内容存于有序 Map：规范化键 + 未知键均按读取顺序保留，
 * 保证 读取 ⇄ 序列化 无损往返。
 */
public class GameOption {

    /** 规范键 */
    public static final String K_INITIAL = "initialScene"; // 初始场景
    public static final String K_BG      = "background";   // 背景色
    public static final String K_VOLUME  = "volume";       // 全局音量 0..1
    public static final String K_SPEED   = "typewriterSpeed"; // 打字机速度(ms/字)

    /** 中文/英文别名 → 规范键 */
    public static final Map<String, String> KEY_ALIAS = new LinkedHashMap<>();
    static {
        KEY_ALIAS.put("initialScene", K_INITIAL); KEY_ALIAS.put("初始场景", K_INITIAL);
        KEY_ALIAS.put("background", K_BG); KEY_ALIAS.put("背景", K_BG);
        KEY_ALIAS.put("背景色", K_BG); KEY_ALIAS.put("背景颜色", K_BG);
        KEY_ALIAS.put("volume", K_VOLUME); KEY_ALIAS.put("音量", K_VOLUME); KEY_ALIAS.put("全局音量", K_VOLUME);
        KEY_ALIAS.put("typewriterSpeed", K_SPEED); KEY_ALIAS.put("打字速度", K_SPEED);
    }

    /** 有序属性容器（默认给出合理初值） */
    private final LinkedHashMap<String, String> values = new LinkedHashMap<>();

    public GameOption() {
        values.put(K_INITIAL, "");
        values.put(K_BG, "#0d0f1c");
        values.put(K_VOLUME, "0.8");
        values.put(K_SPEED, "14");
    }

    public Map<String, String> values() { return values; }

    public String initialScene() { return values.getOrDefault(K_INITIAL, ""); }
    public void setInitialScene(String s) { values.put(K_INITIAL, s == null ? "" : s); }

    /** 返回可用的背景色（十六进制或 named color） */
    public String background() { return values.getOrDefault(K_BG, "#0d0f1c"); }
    public void setBackground(String s) { values.put(K_BG, s == null ? "#0d0f1c" : s); }

    public double volume() { return StoryNode.parseDoubleSafe(values.getOrDefault(K_VOLUME, "0.8"), 0.8); }
    public void setVolume(double v) { values.put(K_VOLUME, StoryNode.trimDouble(clamp(v, 0, 1))); }

    public double typewriterSpeed() { return StoryNode.parseDoubleSafe(values.getOrDefault(K_SPEED, "14"), 14); }
    public void setTypewriterSpeed(double ms) { values.put(K_SPEED, StoryNode.trimDouble(ms)); }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** 别名归一化写入（解析器用） */
    public void putAliasedProperty(String rawKey, String value) {
        String canonical = KEY_ALIAS.getOrDefault(rawKey, rawKey);
        values.put(canonical, value == null ? "" : value);
    }

    /** 拷贝一份（用于编辑时预览，避免污染已打开工程） */
    public GameOption copy() {
        GameOption g = new GameOption();
        g.values.clear();
        g.values.putAll(values);
        return g;
    }

    @Override
    public String toString() {
        return "初始场景=" + initialScene() + ", 背景=" + background() + ", 音量=" + volume();
    }
}
