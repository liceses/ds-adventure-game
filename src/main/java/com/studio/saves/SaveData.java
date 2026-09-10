package com.studio.saves;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一份存档数据（变量名 → 数值列表）。
 *
 * <p>对应脚本文件里的多行语法：</p>
 * <pre>
 *   # 注释行
 *   {生命值: 100}
 *   {位置: 320.5, 180}
 *   {剧情标记: 1}
 * </pre>
 *
 * 每个变量可携带 1~N 个值（数值即可，也允许字符串令牌），
 * 是否“地图工程师按自己的规则解释”——本类只负责结构化存取。
 */
public class SaveData {

    private final LinkedHashMap<String, List<String>> vars = new LinkedHashMap<>();

    // =====================================================================
    // 写入
    // =====================================================================

    public SaveData put(String key, String... values) {
        vars.put(norm(key), values == null ? new ArrayList<>() : new ArrayList<>(List.of(values)));
        return this;
    }

    public SaveData putNumber(String key, double... values) {
        List<String> tokens = new ArrayList<>();
        for (double v : values) tokens.add(doubleStr(v));
        vars.put(norm(key), tokens);
        return this;
    }

    public SaveData putInt(String key, int... values) {
        List<String> tokens = new ArrayList<>();
        for (int v : values) tokens.add(String.valueOf(v));
        vars.put(norm(key), tokens);
        return this;
    }

    public SaveData putBool(String key, boolean value) {
        vars.put(norm(key), new ArrayList<>(List.of(value ? "1" : "0")));
        return this;
    }

    public void remove(String key) {
        vars.remove(norm(key));
    }

    // =====================================================================
    // 读取
    // =====================================================================

    public boolean has(String key) {
        return vars.containsKey(norm(key));
    }

    /** 全部值令牌（原始字符串）；不存在返回空列表 */
    public List<String> values(String key) {
        List<String> v = vars.get(norm(key));
        return v == null ? Collections.emptyList() : v;
    }

    /** 第一个值令牌；不存在返回 null */
    public String first(String key) {
        List<String> v = values(key);
        return v.isEmpty() ? null : v.get(0);
    }

    public double getDouble(String key, double def) {
        String s = first(key);
        if (s == null) return def;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public int getInt(String key, int def) {
        return (int) Math.round(getDouble(key, def));
    }

    public boolean getBool(String key, boolean def) {
        String s = first(key);
        if (s == null) return def;
        String t = s.trim();
        if (t.equals("1") || t.equalsIgnoreCase("true") || t.equals("是")) return true;
        if (t.equals("0") || t.equalsIgnoreCase("false") || t.equals("否")) return false;
        return def;
    }

    public String getString(String key, String def) {
        String s = first(key);
        return s == null ? def : s;
    }

    /** 变量名集合（按写入顺序） */
    public Set<String> keys() {
        return vars.keySet();
    }

    public Map<String, List<String>> asMap() {
        LinkedHashMap<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : vars.entrySet()) {
            copy.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return copy;
    }

    public boolean isEmpty() {
        return vars.isEmpty();
    }

    // =====================================================================

    private static String norm(String key) {
        return key == null ? "" : key.trim();
    }

    public static String doubleStr(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v) && Math.abs(v) < 1e15) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    @Override
    public String toString() {
        return "SaveData" + vars;
    }
}
