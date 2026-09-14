package com.studio.model;

import java.util.Objects;

import com.studio.flow.VarType;

/**
 * 存档变量声明（写在 scenario.txt 的 [option] 段，一行一个）。
 *
 * <p>脚本写法：</p>
 * <pre>
 *   savevar = 金币 | int    | 0
 *   savevar = 分数 | double | 1.5
 *   savevar = 已通关 | bool  | false
 *   savevar = 玩家名 | str   | 无名氏
 * </pre>
 * 编辑器「场景 → 地图全局设置」里可直接增删这些变量。
 * 变量会被写入存档（{@code var.<名称>}），并可在信号/槽里用
 * {@code @var(名称)} 读写、用 {@code @double(1.2)} 之类的写法强制转换。
 */
public class SaveVarDef {

    /** 变量名（不含空格，建议用中文或英文单词） */
    private String name = "";
    /** 基本数据类型 */
    private VarType type = VarType.STRING;
    /** 初值（按类型规范化后的字符串） */
    private String initial = "";

    public SaveVarDef() { }

    public SaveVarDef(String name, VarType type, String initial) {
        setName(name);
        setType(type);
        setInitial(initial);
    }

    public String getName() { return name; }

    public void setName(String name) { this.name = name == null ? "" : name.trim(); }

    public VarType getType() { return type; }

    public void setType(VarType type) { this.type = type == null ? VarType.STRING : type; }

    /** 按类型名设置（编辑器/解析器用） */
    public void setTypeName(String typeName) { setType(VarType.from(typeName)); }

    public String getTypeName() { return type.code(); }

    public String getInitial() { return initial; }

    /** 设置初值时按声明类型强制转换，保证初值永远合法 */
    public void setInitial(String initial) { this.initial = type.cast(initial); }

    public boolean isValid() { return !name.isBlank(); }

    public SaveVarDef copy() { return new SaveVarDef(name, type, initial); }

    /** 一行式写法：{@code 名称 | 类型 | 初值} */
    public String toScriptValue() {
        return name + " | " + type.code() + " | " + initial;
    }

    /** 解析一行式写法；宽容处理缺省字段 */
    public static SaveVarDef parse(String raw) {
        if (raw == null) return null;
        String[] parts = raw.split("\\|", -1);
        if (parts.length == 0 || parts[0].trim().isEmpty()) return null;
        SaveVarDef d = new SaveVarDef();
        d.setName(parts[0]);
        d.setType(parts.length > 1 ? VarType.from(parts[1]) : VarType.STRING);
        d.setInitial(parts.length > 2 ? parts[2].trim() : d.getType().defaultValue());
        return d;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SaveVarDef other)) return false;
        return Objects.equals(name, other.name) && type == other.type && Objects.equals(initial, other.initial);
    }

    @Override
    public int hashCode() { return Objects.hash(name, type, initial); }

    @Override
    public String toString() { return name + ":" + type.code() + "=" + initial; }
}
