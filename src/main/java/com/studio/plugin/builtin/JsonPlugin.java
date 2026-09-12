package com.studio.plugin.builtin;

import com.studio.flow.PluginContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 编辑器<b>自带</b>插件：<b>键值数据</b>——把一个 str 变量当“平铺的 JSON 对象”用。
 *
 * <p>适合放一堆“同一个小本子上的数”：装备栏、章节进度、NPC 好感度表、设置项。
 * 它比开一堆存档变量更好管（一个变量存一张表、能整体存进存档），
 * 也比列表更“带名字”。</p>
 *
 * <h3>可用插件 ID 与动作</h3>
 * <pre>
 *   # 改内容的动作：把同一个变量写在最后（输出位），新对象会写回它
 *   slot = 记好感 | @plugin(json) | set   | @var(好感表) | 蛇专家 | 3 | @var(好感表)
 *   slot = 加好感 | @plugin(json) | add   | @var(好感表) | 蛇专家 | 1 | @var(好感表)
 *   slot = 删一条 | @plugin(json) | del   | @var(好感表) | 蛇专家 | @var(好感表)
 *   slot = 清空   | @plugin(json) | clear | @var(好感表)
 *
 *   # 只读查询：结果写到别的变量
 *   slot = 查好感 | @plugin(json) | get   | @var(好感表) | 蛇专家 | @var(蛇好感)
 *   slot = 有记录 | @plugin(json) | has   | @var(好感表) | 蛇专家 | @var(有)
 *   slot = 所有键 | @plugin(json) | keys  | @var(好感表) | @var(键列表)
 *   slot = 几条   | @plugin(json) | count | @var(好感表) | @var(条数)
 *   slot = 转文本 | @plugin(json) | dump  | @var(好感表) | @var(可读文本)
 * </pre>
 *
 * <p>存储格式就是标准 JSON 对象（值统一按字符串存，取出来也是字符串）：
 * {@code {"蛇专家":"3","灯官":"1"}}，所以外部工具也能读。
 * {@code add} 会把取到的值当数字加，非数字按 0 算。</p>
 *
 * <p><b>注意</b>：数据要存在 <b>str 类型</b>的存档变量里
 * （{@code savevar = 好感表 | str |}）；键名不要包含双引号。</p>
 */
public class JsonPlugin extends BuiltinPlugin {

    private static final Map<String, String> IDS = new LinkedHashMap<>();
    static {
        IDS.put("json", "json");
        IDS.put("数据", "json");
        IDS.put("map", "json");
        IDS.put("dict", "json");
    }

    public JsonPlugin() { this("json"); }

    public JsonPlugin(String id) { super(id); }

    public static List<String> ids() { return new ArrayList<>(IDS.keySet()); }

    public static JsonPlugin of(String id) {
        if (id == null) return null;
        String k = id.trim();
        return IDS.containsKey(k.toLowerCase(Locale.ROOT)) || IDS.containsKey(k) ? new JsonPlugin(k) : null;
    }

    /** 编辑器插件目录 */
    public static List<PluginInfo> catalog() {
        List<PluginInfo> out = new ArrayList<>();
        out.add(new PluginInfo("json", "数据", "数据,map,dict",
                "@plugin(json) | set | @var(好感表) | 蛇专家 | 3 | @var(好感表)",
                "把一个 str 变量当 JSON 对象：set 写 / add 累加 / del 删 / clear 清空（新对象写回输出位，通常把同一个变量写在最后）；"
                        + "get 读 / has 判断 / keys 列键 / count 条数 / dump 转文本（结果写到别的变量）"));
        return out;
    }

    @Override
    protected String group() { return "数据"; }

    @Override
    public String description() {
        return "键值数据：把一个 str 变量当 JSON 对象（set/get/add/has/del/keys/count/clear/dump）";
    }

    @Override
    public String usage() {
        return "@plugin(json) | set|add|del|clear | @var(数据) | 键 | 值 | @var(数据)    或    "
                + "@plugin(json) | get|has|keys|count|dump | @var(数据) | 键 | @var(输出)";
    }

    // =====================================================================

    @Override
    protected void run(PluginContext ctx, String[] in, String[] out) {
        if (count(in) < 2) {
            warn(ctx, "参数不足（用法：" + usage() + "）");
            return;
        }
        String action = arg(in, 0).toLowerCase(Locale.ROOT);
        // 引擎会把 @var(好感表) 解析成“当前值”再传进来：第 2 个参数是数据本身，输出位是最后一个参数
        Map<String, String> data = parse(raw(in, 1));
        String key = arg(in, 2);
        switch (action) {
            case "set", "put", "写" -> {
                data.put(key, raw(in, 3));
                setOut(out, toJson(data));
                log(ctx, "数据[" + key + "] = " + raw(in, 3) + "（共 " + data.size() + " 条）");
            }
            case "get", "read", "读" -> {
                String v = data.getOrDefault(key, "");
                setOut(out, v);
                log(ctx, "数据[" + key + "] → " + v);
            }
            case "add", "累加", "inc" -> {
                double delta = num(arg(in, 3), 1);
                double now = num(data.get(key), 0) + delta;
                String text = trim(now);
                data.put(key, text);
                setOut(out, toJson(data));
                log(ctx, "数据[" + key + "] += " + trim(delta) + " → " + text);
            }
            case "has", "contains", "有" -> {
                setOut(out, bool(data.containsKey(key)));
                log(ctx, "数据" + (data.containsKey(key) ? "有 " : "没有 ") + key);
            }
            case "del", "remove", "删除" -> {
                String old = data.remove(key);
                setOut(out, toJson(data));
                log(ctx, old == null ? ("数据里没有 " + key) : ("已删除数据[" + key + "]"));
            }
            case "keys", "键" -> {
                setOut(out, String.join("、", data.keySet()));
                log(ctx, "数据的键：" + String.join("、", data.keySet()));
            }
            case "count", "条数" -> {
                setOut(out, String.valueOf(data.size()));
                log(ctx, "数据共 " + data.size() + " 条");
            }
            case "clear", "清空" -> {
                setOut(out, "{}");
                log(ctx, "数据已清空（原有 " + data.size() + " 条）");
            }
            case "dump", "转文本" -> {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, String> e : data.entrySet()) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(e.getKey()).append(" = ").append(e.getValue());
                }
                setOut(out, sb.toString());
            }
            default -> warn(ctx, "未知动作: " + action + "（用法：" + usage() + "）");
        }
    }

    // =====================================================================
    // 极简 JSON（平铺对象、值按字符串）编解码
    // =====================================================================

    static Map<String, String> parse(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        if (json == null) return out;
        String s = json.trim();
        if (s.isEmpty() || s.equals("{}")) return out;
        int i = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (i < 0 || end <= i) return out;
        s = s.substring(i + 1, end);
        int p = 0;
        while (p < s.length()) {
            while (p < s.length() && (s.charAt(p) == ',' || Character.isWhitespace(s.charAt(p)))) p++;
            if (p >= s.length()) break;
            if (s.charAt(p) != '"') break;
            int keyEnd = findQuoteEnd(s, p + 1);
            if (keyEnd < 0) break;
            String key = unescape(s.substring(p + 1, keyEnd));
            p = keyEnd + 1;
            while (p < s.length() && s.charAt(p) != ':') p++;
            p++;
            while (p < s.length() && Character.isWhitespace(s.charAt(p))) p++;
            String value;
            if (p < s.length() && s.charAt(p) == '"') {
                int valEnd = findQuoteEnd(s, p + 1);
                if (valEnd < 0) break;
                value = unescape(s.substring(p + 1, valEnd));
                p = valEnd + 1;
            } else {
                int valEnd = p;
                while (valEnd < s.length() && s.charAt(valEnd) != ',') valEnd++;
                value = s.substring(p, valEnd).trim();
                p = valEnd;
            }
            out.put(key, value);
        }
        return out;
    }

    static String toJson(Map<String, String> data) {
        if (data == null || data.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : data.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":\"").append(escape(e.getValue())).append('"');
        }
        return sb.append('}').toString();
    }

    private static int findQuoteEnd(String s, int from) {
        for (int k = from; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '\\') { k++; continue; }
            if (c == '"') return k;
        }
        return -1;
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    default -> sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String trim(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.valueOf(Math.rint(v * 1000) / 1000.0);
    }
}
