package com.studio.flow;

import java.util.ArrayList;
import java.util.List;

import com.studio.util.Logs;

/**
 * 信号 / 槽 与插件里的统一取值表达式引擎。
 *
 * <p>支持的写法（可嵌套）：</p>
 * <table border="1">
 *   <caption>表达式一览</caption>
 *   <tr><th>写法</th><th>含义</th></tr>
 *   <tr><td>{@code @var(名称)}</td><td>取存档变量的值；{@code @var(名称, 默认值)} 可给默认值</td></tr>
 *   <tr><td>{@code @int(x)} {@code @long(x)} {@code @double(x)} {@code @bool(x)} {@code @str(x)}</td>
 *       <td><b>强制类型转换</b>：转换失败退回该类型默认值，绝不报错</td></tr>
 *   <tr><td>{@code @node(属性名)}</td><td>把<b>触发信号的节点</b>的某个属性当作变量取值</td></tr>
 *   <tr><td>{@code @node(节点id, 属性名)}</td><td>取指定节点的属性</td></tr>
 *   <tr><td>{@code @param(参数名)}</td><td>取信号事件附带的参数</td></tr>
 *   <tr><td>{@code @plugin(插件名)}</td><td>仅作为槽动作使用，见 {@link SlotPlugin}</td></tr>
 * </table>
 *
 * <p>整个字段是一个表达式时按表达式求值；字段里夹杂文字时按“模板”替换，
 * 例如 {@code 金币: @var(金币)} 会得到 {@code 金币: 12}。</p>
 */
public final class Expr {

    private Expr() { }

    /** 求值所需的上下文（由渲染引擎 / 信号总线提供） */
    public interface Scope {

        /** 读存档变量（不存在时返回 def） */
        String var(String name, String def);

        /** 写存档变量（写回位置由调用方决定是否使用） */
        void setVar(String name, String value);

        /** 读节点属性 */
        String nodeProp(String nodeId, String prop);

        /** 触发本次信号的节点 id（{@code @node(属性)} 的默认对象），可为空 */
        String sourceNodeId();

        /** 本次事件附带的参数（{@code @param(name)}） */
        String param(String name);
    }

    // =====================================================================
    // 判断与拆解
    // =====================================================================

    /** 是否是 {@code @名称(...)} 形式的表达式 */
    public static boolean isExpr(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.length() < 3 || t.charAt(0) != '@') return false;
        int open = t.indexOf('(');
        return open > 1 && t.endsWith(")");
    }

    /** 整个字符串是否就是<b>单个</b>表达式（前后没有多余文字） */
    public static boolean isWholeExpr(String s) {
        if (!isExpr(s)) return false;
        String t = s.trim();
        int open = t.indexOf('(');
        // 括号必须配平到结尾，避免 "…) + 文字" 被误判
        int depth = 0;
        for (int i = open; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i == t.length() - 1;
            }
        }
        return false;
    }

    /** 表达式名称，如 {@code var} / {@code double} / {@code plugin} */
    public static String fn(String s) {
        if (!isExpr(s)) return "";
        String t = s.trim();
        return t.substring(1, t.indexOf('(')).trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** 表达式括号内的原文 */
    public static String inner(String s) {
        if (!isExpr(s)) return "";
        String t = s.trim();
        int open = t.indexOf('(');
        int depth = 0;
        for (int i = open; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return t.substring(open + 1, i);
            }
        }
        return t.substring(open + 1, t.length() - 1);
    }

    // =====================================================================
    // 求值
    // =====================================================================

    /** 求值：整字段表达式 / 模板替换 / 纯字面量 */
    public static String resolve(String raw, Scope scope) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty() || s.indexOf('@') < 0) return raw;
        if (isWholeExpr(s)) return eval(s, scope);
        return substitute(raw, scope);
    }

    /** 把字符串里所有 {@code @xxx(...)} 片段替换成求值结果 */
    public static String substitute(String template, Scope scope) {
        if (template == null || template.indexOf('@') < 0) return template == null ? "" : template;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '@') {
                int open = template.indexOf('(', i + 1);
                if (open > i + 1) {
                    int depth = 0;
                    int end = -1;
                    for (int j = open; j < template.length(); j++) {
                        char d = template.charAt(j);
                        if (d == '(') depth++;
                        else if (d == ')') {
                            depth--;
                            if (depth == 0) { end = j; break; }
                        }
                    }
                    if (end > 0) {
                        String expr = template.substring(i, end + 1);
                        sb.append(eval(expr, scope));
                        i = end + 1;
                        continue;
                    }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /** 单个表达式求值（不含模板替换） */
    public static String eval(String expr, Scope scope) {
        if (!isExpr(expr)) return expr;
        String f = fn(expr);
        String inner = inner(expr).trim();
        try {
            switch (f) {
                case "var": {
                    String[] args = splitArgs(inner);
                    String name = args.length > 0 ? args[0] : "";
                    String def = args.length > 1 ? resolve(args[1], scope) : "";
                    if (name.isEmpty()) return def;
                    String v = scope.var(name, def);
                    return v == null ? def : v;
                }
                case "node": {
                    String[] args = splitArgs(inner);
                    String id = args.length > 1 ? resolve(args[0], scope) : scope.sourceNodeId();
                    String prop = args.length > 1 ? args[1] : (args.length == 1 ? args[0] : "");
                    if (id == null || prop == null || prop.isEmpty()) return "";
                    String v = scope.nodeProp(id, prop);
                    return v == null ? "" : v;
                }
                case "param": case "ev": {
                    String name = resolve(inner, scope);
                    String v = scope.param(name);
                    return v == null ? "" : v;
                }
                case "int":    return VarType.INT.cast(resolve(inner, scope));
                case "long":   return VarType.LONG.cast(resolve(inner, scope));
                case "float":  case "double": return VarType.DOUBLE.cast(resolve(inner, scope));
                case "bool":   case "boolean": return VarType.BOOL.cast(resolve(inner, scope));
                case "str":    case "string":  return resolve(inner, scope);
                case "plugin": return inner;      // 作为值使用时就是插件名
                default:
                    Logs.warn("[Expr] 未知表达式 @" + f + "(" + inner + ")，按原样返回");
                    return expr;
            }
        } catch (RuntimeException e) {
            Logs.warn("[Expr] 表达式求值失败 " + expr + "：" + e.getMessage());
            return "";
        }
    }

    /** 按逗号切分参数（忽略括号内的逗号） */
    public static String[] splitArgs(String s) {
        if (s == null || s.isBlank()) return new String[0];
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            if (c == ',' && depth == 0) {
                out.add(cur.toString().trim());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        out.add(cur.toString().trim());
        return out.toArray(new String[0]);
    }

    /** 该表达式是否最终指向一个存档变量（供插件回写判断） */
    public static boolean isVarRef(String s) {
        if (!isWholeExpr(s)) return false;
        String f = fn(s);
        if (f.equals("var")) return true;
        // 强制转换要往里看一层：@double(@var(x)) 是变量引用，@double(1.2) 不是
        if (f.equals("int") || f.equals("long") || f.equals("double") || f.equals("float")
                || f.equals("bool") || f.equals("boolean") || f.equals("str") || f.equals("string")) {
            return isVarRef(inner(s).trim());
        }
        return false;
    }

    /** 取出 {@code @var(x)} / {@code @double(@var(x))} 里最内层的变量名，没有则返回空 */
    public static String varRefName(String s) {
        if (s == null) return "";
        String cur = s.trim();
        for (int guard = 0; guard < 8 && isWholeExpr(cur); guard++) {
            String f = fn(cur);
            String inner = inner(cur).trim();
            if (f.equals("var")) {
                String[] args = splitArgs(inner);
                return args.length > 0 ? args[0] : "";
            }
            if (f.equals("int") || f.equals("long") || f.equals("double") || f.equals("float")
                    || f.equals("bool") || f.equals("boolean") || f.equals("str") || f.equals("string")) {
                cur = inner;
                continue;
            }
            return "";
        }
        return "";
    }

    /** 该表达式外层的强制转换类型（没有则返回 null） */
    public static VarType castType(String s) {
        if (!isWholeExpr(s)) return null;
        switch (fn(s)) {
            case "int": return VarType.INT;
            case "long": return VarType.LONG;
            case "double": case "float": return VarType.DOUBLE;
            case "bool": case "boolean": return VarType.BOOL;
            case "str": case "string": return VarType.STRING;
            default: return null;
        }
    }
}
