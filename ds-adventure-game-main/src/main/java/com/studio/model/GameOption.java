package com.studio.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局设置（对应 scenario.txt 中的 [option] 段）。
 * <p>
 * 标量内容存于有序 Map：规范化键 + 未知键均按读取顺序保留，保证 读取 ⇄ 序列化 无损往返；
 * 存档变量列表（{@code savevar = 名称 | 类型 | 初值}，可重复出现）单独放在
 * {@link #saveVars()} 里，编辑器可在“地图全局设置”里直接增删。
 */
public class GameOption {

    /** 规范键 */
    public static final String K_INITIAL = "initialScene"; // 初始场景
    public static final String K_BG      = "background";   // 背景色
    public static final String K_VOLUME  = "volume";       // 全局音量 0..1
    public static final String K_SPEED   = "typewriterSpeed"; // 打字机速度(ms/字)
    public static final String K_SAVEVAR = "savevar";      // 存档变量声明（可重复行）

    /** 中文/英文别名 → 规范键 */
    public static final Map<String, String> KEY_ALIAS = new LinkedHashMap<>();
    static {
        KEY_ALIAS.put("initialScene", K_INITIAL); KEY_ALIAS.put("初始场景", K_INITIAL);
        KEY_ALIAS.put("background", K_BG); KEY_ALIAS.put("背景", K_BG);
        KEY_ALIAS.put("背景色", K_BG); KEY_ALIAS.put("背景颜色", K_BG);
        KEY_ALIAS.put("volume", K_VOLUME); KEY_ALIAS.put("音量", K_VOLUME); KEY_ALIAS.put("全局音量", K_VOLUME);
        KEY_ALIAS.put("typewriterSpeed", K_SPEED); KEY_ALIAS.put("打字速度", K_SPEED);
        KEY_ALIAS.put("savevar", K_SAVEVAR); KEY_ALIAS.put("存档变量", K_SAVEVAR);
        KEY_ALIAS.put("变量", K_SAVEVAR); KEY_ALIAS.put("变量列表", K_SAVEVAR);
    }

    /** 有序属性容器（默认给出合理初值） */
    private final LinkedHashMap<String, String> values = new LinkedHashMap<>();

    /** 存档变量声明（名称 | 类型 | 初值），可在编辑器里增删 */
    private final List<SaveVarDef> saveVars = new ArrayList<>();

    public GameOption() {
        values.put(K_INITIAL, "");
        values.put(K_BG, "#0d0f1c");
        values.put(K_VOLUME, "0.8");
        values.put(K_SPEED, "14");
    }

    public Map<String, String> values() { return values; }

    /** 存档变量列表 */
    public List<SaveVarDef> saveVars() { return saveVars; }

    public void setSaveVars(List<SaveVarDef> defs) {
        saveVars.clear();
        if (defs != null) {
            for (SaveVarDef d : defs) if (d != null && d.isValid()) saveVars.add(d.copy());
        }
    }

    /** 新增一个存档变量（同名的直接覆盖初值/类型） */
    public void addSaveVar(SaveVarDef def) {
        if (def == null || !def.isValid()) return;
        SaveVarDef exist = saveVar(def.getName());
        if (exist != null) {
            exist.setType(def.getType());
            exist.setInitial(def.getInitial());
        } else {
            saveVars.add(def.copy());
        }
    }

    public boolean removeSaveVar(String name) {
        if (name == null) return false;
        return saveVars.removeIf(d -> name.equals(d.getName()));
    }

    /** 按名称找变量声明（没有返回 null） */
    public SaveVarDef saveVar(String name) {
        if (name == null) return null;
        for (SaveVarDef d : saveVars) {
            if (name.equals(d.getName())) return d;
        }
        return null;
    }

    /** 简单校验：变量名重复或为空时返回提示文本，正常返回空串 */
    public String validateSaveVars() {
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (SaveVarDef d : saveVars) {
            if (!d.isValid()) return "存在没有名字的存档变量";
            if (!seen.add(d.getName())) return "存档变量名重复：" + d.getName();
            if (d.getName().contains(" ") || d.getName().contains("|")) {
                return "变量名不能包含空格或竖线：" + d.getName();
            }
        }
        return "";
    }

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
        if (K_SAVEVAR.equals(canonical)) {
            SaveVarDef def = SaveVarDef.parse(value);
            if (def != null) addSaveVar(def);
            return;
        }
        values.put(canonical, value == null ? "" : value);
    }

    /** 拷贝一份（用于编辑时预览，避免污染已打开工程） */
    public GameOption copy() {
        GameOption g = new GameOption();
        g.values.clear();
        g.values.putAll(values);
        g.setSaveVars(saveVars);
        return g;
    }

    /**
     * 用另一份设置覆盖当前对象（编辑器“撤销/恢复”用：保持对象引用不变，
     * 因为界面上多处持有 {@code project.option()} 的引用）。
     */
    public void copyFrom(GameOption other) {
        if (other == null) return;
        values.clear();
        values.putAll(other.values);
        setSaveVars(other.saveVars);
    }

    @Override
    public String toString() {
        return "初始场景=" + initialScene() + ", 背景=" + background() + ", 音量=" + volume()
                + ", 存档变量=" + saveVars.size() + " 个";
    }
}
