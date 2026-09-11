package com.studio.flow;

import java.util.Locale;

import com.studio.model.StoryNode;

/**
 * 存档变量的基本数据类型，以及“强制类型转换”规则。
 *
 * <p>编辑器里在 [option] 段声明的每个存档变量都带一个类型；
 * 信号/槽、插件读写变量时，若实际值与声明类型不一致，会按本类的规则
 * <b>强制转换</b>而不是抛异常（转换失败退回该类型的默认值并记日志），
 * 从而避免剧情脚本因类型问题中断运行。</p>
 *
 * <p>脚本里的强制类型转换写法：{@code @int(x)} / {@code @long(x)} /
 * {@code @double(x)} / {@code @bool(x)} / {@code @str(x)}，可嵌套，如
 * {@code @double(@var(分数))}。</p>
 */
public enum VarType {

    /** 整数（内部按 long 存储，越界或非法输入 → 0） */
    INT("int", "整数"),
    /** 长整数（与 INT 同规则，便于脚本显式声明大数） */
    LONG("long", "长整数"),
    /** 小数（非法输入 → 0.0） */
    DOUBLE("double", "小数"),
    /** 布尔（true/1/是/on → true，其余 false） */
    BOOL("bool", "布尔"),
    /** 文本（永不转换失败） */
    STRING("str", "文本");

    private final String code;
    private final String display;

    VarType(String code, String display) {
        this.code = code;
        this.display = display;
    }

    public String code() { return code; }
    public String display() { return display; }

    /** 是否数值类型（含整数/小数） */
    public boolean isNumber() { return this == INT || this == LONG || this == DOUBLE; }

    /** 该类型的默认值（转换失败时的兜底） */
    public String defaultValue() { return this == BOOL ? "false" : (this == STRING ? "" : "0"); }

    /** 宽容解析类型名：int/整数/INTEGER/… */
    public static VarType from(String raw) {
        if (raw == null) return STRING;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        for (VarType t : values()) {
            if (t.code.equals(s) || t.name().equalsIgnoreCase(s) || t.display.equals(raw.trim())) return t;
        }
        switch (s) {
            case "integer": case "整型": return INT;
            case "float": case "number": case "小数": case "浮点": return DOUBLE;
            case "boolean": case "bool": case "布尔": return BOOL;
            case "string": case "text": case "文本": case "字符串": return STRING;
            default: return STRING;
        }
    }

    /**
     * 把任意字符串强制转换成该类型的规范写法。
     * <p>绝不抛异常：无法转换时返回 {@link #defaultValue()}。</p>
     */
    public String cast(String raw) {
        String v = raw == null ? "" : raw.trim();
        switch (this) {
            case INT:
            case LONG: {
                try {
                    return String.valueOf((long) Math.rint(Double.parseDouble(extractNumber(v))));
                } catch (RuntimeException e) {
                    // 布尔值也能“当数字用”：true → 1 / false → 0（方便 @int(@var(开关变量)) 计数）
                    Boolean b = boolish(v);
                    return b == null ? "0" : (b ? "1" : "0");
                }
            }
            case DOUBLE: {
                try {
                    return StoryNode.trimDouble(Double.parseDouble(extractNumber(v)));
                } catch (RuntimeException e) {
                    Boolean b = boolish(v);
                    return b == null ? "0" : (b ? "1" : "0");
                }
            }
            case BOOL: {
                if (v.isEmpty()) return "false";
                Boolean b = boolish(v);
                if (b != null) return b ? "true" : "false";
                // 数字也能“当布尔用”：非 0 → true
                Double d = numberOrNull(v);
                return (d != null && d != 0) ? "true" : "false";
            }
            default:
                return raw == null ? "" : raw;
        }
    }

    /** 中文/英文的布尔写法 → Boolean；不是布尔写法返回 null */
    public static Boolean boolish(String v) {
        if (v == null) return null;
        String s = v.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        switch (s) {
            case "true": case "1": case "是": case "yes": case "y":
            case "on": case "真": case "开": case "亮": case "有":
                return Boolean.TRUE;
            case "false": case "0": case "否": case "no": case "n":
            case "off": case "假": case "关": case "灭": case "无":
                return Boolean.FALSE;
            default:
                return null;
        }
    }

    private static Double numberOrNull(String v) {
        try {
            return Double.parseDouble(v.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 取字符串里最长的合法数字前缀（容忍 "12px" 这类脏数据），失败则原样返回交给 catch */
    private static String extractNumber(String v) {
        if (v.isEmpty()) return v;
        int i = 0;
        if (v.charAt(0) == '+' || v.charAt(0) == '-') i = 1;
        int start = i;
        boolean dot = false;
        while (i < v.length()) {
            char c = v.charAt(i);
            if (c >= '0' && c <= '9') { i++; continue; }
            if (c == '.' && !dot) { dot = true; i++; continue; }
            break;
        }
        if (i == start) return v;              // 没有数字 → 让 parseDouble 抛错走兜底
        return v.substring(0, i);
    }

    /** 两个类型运算后的结果类型（取“更宽”的那个） */
    public static VarType wider(VarType a, VarType b) {
        if (a == DOUBLE || b == DOUBLE) return DOUBLE;
        if (a == STRING || b == STRING) return STRING;
        if (a == LONG || b == LONG) return LONG;
        if (a == INT || b == INT) return INT;
        return BOOL;
    }

    @Override
    public String toString() { return display + "(" + code + ")"; }
}
