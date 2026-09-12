package com.studio.flow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 信号 / 槽 的一行式编解码器（供 scenario.txt 双向读写与编辑器文本编辑使用）。
 *
 * <h3>格式</h3>
 * <pre>
 *   信号: 名称 | mouse|key | click|release|按键码 | press|release | 参数k=v,参数k=v
 *   槽:   信号名 | 动作 | 目标 | 参数 | 参数k=v,参数k=v
 * </pre>
 * 字段可省略（用连续竖线占位）；{@code | , = \} 需转义（{@code \|}、{@code \,}、{@code \=}、{@code \\}）。
 * 解码宽容：字段不足用默认值补齐，无法识别的行返回 null 并写入警告列表。
 */
public final class SignalCodec {

    private SignalCodec() { }

    // =====================================================================
    // 信号
    // =====================================================================

    public static String encode(SignalDef s) {
        StringBuilder sb = new StringBuilder();
        sb.append(esc(s.getName())).append(" | ")
          .append(s.getKind() == SignalDef.Kind.KEY ? "key" : "mouse").append(" | ");
        if (s.getKind() == SignalDef.Kind.KEY) {
            sb.append(esc(s.getKey())).append(" | ").append(esc(s.getKeyPhase()));
        } else {
            sb.append(esc(s.getMouse())); // 第 3 段；第 4 段留给参数
        }
        String params = encodeParams(s.params());
        if (!params.isEmpty()) sb.append(" | ").append(params);
        return sb.toString();
    }

    public static SignalDef decodeSignal(String line, List<String> warnings) {
        List<String> f = split(line);
        if (f.isEmpty() || f.get(0).isBlank()) {
            warn(warnings, line, "信号名为空");
            return null;
        }
        SignalDef s = new SignalDef();
        s.setName(f.get(0));
        String kind = f.size() > 1 ? f.get(1) : "mouse";
        s.setKind(kind);
        if (s.getKind() == SignalDef.Kind.KEY) {
            s.setKey(f.size() > 2 ? f.get(2) : "");
            s.setKeyPhase(f.size() > 3 && !f.get(3).isBlank() ? f.get(3) : "press");
            if (f.size() > 4) s.params().putAll(decodeParams(f.get(4)));
        } else {
            s.setMouse(f.size() > 2 && !f.get(2).isBlank() ? f.get(2) : "click");
            if (f.size() > 3 && !f.get(3).isBlank()) s.params().putAll(decodeParams(f.get(3)));
        }
        if (s.getKind() == SignalDef.Kind.KEY && s.getKey().isBlank()) {
            warn(warnings, line, "按键信号缺少按键码");
        }
        return s;
    }

    // =====================================================================
    // 槽
    // =====================================================================

    public static String encode(SlotDef s) {
        StringBuilder sb = new StringBuilder();
        sb.append(esc(s.getSignal())).append(" | ").append(esc(s.getAction()));
        if (s.isPlugin()) {
            // 插件槽：动作之后是可变个数的参数（第 3、4、5… 段）。
            // 注意只写到最后一个“非空参数”为止：像 @plugin(quit) 这种无参插件如果写成
            // “@plugin(quit) |  |”，再次解析会被判成“插件槽没有参数”而告警（往返不干净）。
            java.util.ArrayList<String> all = new java.util.ArrayList<>();
            all.add(s.getTarget() == null ? "" : s.getTarget());
            all.add(s.getArg() == null ? "" : s.getArg());
            all.addAll(s.extraArgs());
            int end = all.size();
            while (end > 0 && all.get(end - 1).isBlank()) end--;
            for (int i = 0; i < end; i++) {
                sb.append(" | ").append(esc(all.get(i)));
            }
            String params = encodeParams(s.params());
            if (!params.isEmpty()) sb.append(" | ").append(params);
            return sb.toString();
        }
        sb.append(" | ").append(esc(s.getTarget())).append(" | ").append(esc(s.getArg()));
        String params = encodeParams(s.params());
        if (!params.isEmpty()) sb.append(" | ").append(params); // 第 5 段起的 k=v 附加参数
        return sb.toString();
    }

    public static SlotDef decodeSlot(String line, List<String> warnings) {
        List<String> f = split(line);
        if (f.isEmpty() || f.get(0).isBlank() || f.size() < 2 || f.get(1).isBlank()) {
            warn(warnings, line, "槽需要至少 “信号名 | 动作”");
            return null;
        }
        SlotDef s = new SlotDef();
        s.setSignal(f.get(0));
        s.setAction(f.get(1));
        if (s.isPlugin()) {
            // 插件槽：动作之后的所有字段都是参数（数量可变），不做 k=v 解析
            if (f.size() > 2) s.setTarget(f.get(2));
            if (f.size() > 3) s.setArg(f.get(3));
            for (int i = 4; i < f.size(); i++) s.extraArgs().add(f.get(i));
            if (f.size() > 2 && s.pluginArgs().length == 0) {
                warn(warnings, line, "插件槽 " + s.pluginId() + " 没有参数");
            }
            return s;
        }
        if (f.size() > 2) s.setTarget(f.get(2));
        if (f.size() > 3) s.setArg(f.get(3));
        if (f.size() > 4) s.params().putAll(decodeParams(f.get(4)));
        return s;
    }

    // =====================================================================
    // 参数与转义
    // =====================================================================

    public static String encodeParams(Map<String, String> params) {
        if (params == null || params.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(esc(e.getKey())).append('=').append(esc(e.getValue()));
        }
        return sb.toString();
    }

    public static LinkedHashMap<String, String> decodeParams(String s) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        if (s == null || s.isBlank()) return map;
        for (String part : splitParams(s)) {
            int eq = indexOfUnescaped(part, '=');
            if (eq <= 0) {
                if (!part.isBlank()) map.put(unesc(part.trim()), "");
                continue;
            }
            map.put(unesc(part.substring(0, eq).trim()), unesc(part.substring(eq + 1).trim()));
        }
        return map;
    }

    /**
     * 按未转义的逗号切分参数；<b>括号内的逗号不切分</b>，
     * 这样 {@code value=@node(宝箱, width)} 这类写法不会被拆坏。
     */
    private static List<String> splitParams(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean esc = false;
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) {
                cur.append('\\').append(c);
                esc = false;
                continue;
            }
            if (c == '\\') { esc = true; continue; }
            if (c == '(') depth++;
            else if (c == ')') depth = Math.max(0, depth - 1);
            if (c == ',' && depth == 0) {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        if (esc) cur.append('\\');
        out.add(cur.toString());
        return out;
    }

    /** 按未转义的 | 切分整行 */
    public static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        for (String p : splitEscaped(line == null ? "" : line, '|')) out.add(unesc(p.trim()));
        return out;
    }

    private static List<String> splitEscaped(String s, char sep) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean esc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) {
                cur.append('\\').append(c);
                esc = false;
                continue;
            }
            if (c == '\\') {
                esc = true;
                continue;
            }
            if (c == sep) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (esc) cur.append('\\');
        out.add(cur.toString());
        return out;
    }

    private static int indexOfUnescaped(String s, char target) {
        boolean esc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) { esc = false; continue; }
            if (c == '\\') { esc = true; continue; }
            if (c == target) return i;
        }
        return -1;
    }

    public static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '|' -> sb.append("\\|");
                case ',' -> sb.append("\\,");
                case '=' -> sb.append("\\=");
                case '\n' -> sb.append("\\n");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String unesc(String s) {
        if (s == null || s.indexOf('\\') < 0) return s == null ? "" : s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(n == 'n' ? '\n' : n);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void warn(List<String> warnings, String line, String msg) {
        if (warnings != null) warnings.add("信号/槽解析: " + msg + " → " + line);
    }
}
