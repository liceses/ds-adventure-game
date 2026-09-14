package com.studio.ui;

import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;

/**
 * 富文本迷你解析器：把带轻量标记的台词文本转成 JavaFX {@link TextFlow}。
 *
 * <p>支持的标记（编辑器属性面板里也有同样说明）：</p>
 * <pre>
 *   &lt;b&gt;加粗&lt;/b&gt;        &lt;i&gt;斜体&lt;/i&gt;      &lt;u&gt;下划线&lt;/u&gt;
 *   &lt;color:#ffcc00&gt;彩色&lt;/color&gt;      &lt;color:yellow&gt;命名色&lt;/color&gt;
 *   &lt;size:26&gt;更大字号&lt;/size&gt;         &lt;br&gt; 换行
 *   未知/不闭合的标记会被当作普通文本输出，绝不抛异常。
 * </pre>
 *
 * 支持“逐字揭示”（打字机）：{@link #toFlow(String, double, String, int)} 的
 * reveal 参数表示只显示前 N 个可见字符（含换行）；每次 tick 增大 N 并重建
 * TextFlow 子节点即可实现打字机动画。
 */
public final class RichText {

    /** 一个带样式的文本片段 */
    public record StyleRun(String text, boolean bold, boolean italic,
                           boolean underline, String color, double sizeDelta) { }

    private RichText() { }

    // =====================================================================
    // 解析
    // =====================================================================

    /** 把标记文本解析为带样式的片段序列（含换行符，保留在 text 内） */
    public static List<StyleRun> parse(String markup) {
        List<StyleRun> runs = new ArrayList<>();
        if (markup == null || markup.isEmpty()) return runs;

        StringBuilder buf = new StringBuilder();
        boolean bold = false, italic = false, underline = false;
        String color = null;
        double sizeDelta = 0;

        int i = 0;
        int n = markup.length();
        while (i < n) {
            char c = markup.charAt(i);
            if (c == '<') {
                int end = markup.indexOf('>', i);
                if (end < 0) { buf.append(c); i++; continue; }
                String tag = markup.substring(i + 1, end).trim();
                i = end + 1;
                if (tag.isEmpty()) { buf.append('<'); continue; }

                // 先结算已积累的普通文本
                flush(buf, runs, bold, italic, underline, color, sizeDelta);

                if (tag.startsWith("/")) {
                    switch (tag.substring(1).toLowerCase()) {
                        case "b" -> bold = false;
                        case "i" -> italic = false;
                        case "u" -> underline = false;
                        case "color" -> color = null;
                        case "size" -> sizeDelta = 0;
                        default -> buf.append('<').append(tag).append('>');
                    }
                } else {
                    int colon = tag.indexOf(':');
                    String cmd = (colon >= 0 ? tag.substring(0, colon) : tag).toLowerCase();
                    String arg = colon >= 0 ? tag.substring(colon + 1).trim() : "";
                    switch (cmd) {
                        case "b", "bold" -> bold = true;
                        case "i", "italic" -> italic = true;
                        case "u", "underline" -> underline = true;
                        case "br" -> buf.append('\n');
                        case "color" -> color = normalizeColorArg(arg);
                        case "size" -> {
                            try { sizeDelta = Double.parseDouble(arg); } catch (NumberFormatException e) { /* 忽略 */ }
                        }
                        default -> buf.append('<').append(tag).append('>');
                    }
                }
            } else {
                buf.append(c);
                i++;
            }
        }
        flush(buf, runs, bold, italic, underline, color, sizeDelta);
        return runs;
    }

    private static void flush(StringBuilder buf, List<StyleRun> runs, boolean bold, boolean italic,
                              boolean underline, String color, double sizeDelta) {
        if (buf.length() > 0) {
            runs.add(new StyleRun(buf.toString(), bold, italic, underline, color, sizeDelta));
            buf.setLength(0);
        }
    }

    private static String normalizeColorArg(String arg) {
        if (arg == null) return null;
        String a = arg.strip();
        if (a.isEmpty()) return null;
        // 简易命名色 → CSS 色值
        return switch (a.toLowerCase()) {
            case "red" -> "#ff5555";
            case "green" -> "#55ff88";
            case "blue" -> "#55aaff";
            case "yellow", "gold" -> "#ffe066";
            case "white" -> "#ffffff";
            case "black" -> "#000000";
            case "gray", "grey" -> "#999999";
            case "orange" -> "#ffa055";
            case "cyan", "sky" -> "#8fe3ff";
            case "pink" -> "#ff9ecb";
            default -> a.startsWith("#") ? a : null; // 不认识的当透明处理
        };
    }

    // =====================================================================
    // 构建 TextFlow
    // =====================================================================

    /**
     * 把标记文本构建成 TextFlow。
     *
     * @param baseFontSize 基础字号（可为 &lt;=0，此时用默认字体大小）
     * @param defaultColor 默认文字颜色（null 时继承 CSS）
     * @param revealChars  只渲染前 N 个字符；-1 表示全部
     */
    public static TextFlow toFlow(String markup, double baseFontSize,
                                  String defaultColor, int revealChars) {
        TextFlow flow = new TextFlow();
        flow.setLineSpacing(4);
        List<StyleRun> runs = parse(markup);
        int budget = revealChars < 0 ? Integer.MAX_VALUE : revealChars;
        for (StyleRun r : runs) {
            if (budget <= 0) break;
            String text = r.text();
            if (text.length() > budget) text = text.substring(0, budget);
            budget -= r.text().length();

            Text t = new Text(text);
            if (r.bold()) t.setFont(Font.font(null, FontWeight.BOLD, fontBase(baseFontSize) + r.sizeDelta()));
            else if (r.italic()) t.setFont(Font.font(null, FontPosture.ITALIC, fontBase(baseFontSize) + r.sizeDelta()));
            else if (r.sizeDelta() != 0) t.setFont(Font.font(null, fontBase(baseFontSize) + r.sizeDelta()));
            else t.setFont(Font.font(fontBase(baseFontSize)));
            t.setUnderline(r.underline());
            String color = r.color() != null ? r.color() : defaultColor;
            if (color != null) {
                try { t.setFill(Color.web(color)); }
                catch (IllegalArgumentException e) { /* 非法颜色交给 CSS */ }
            }
            flow.getChildren().add(t);
        }
        return flow;
    }

    private static double fontBase(double base) {
        return base > 0 ? base : 15;
    }

    /** 生成全部文本的 TextFlow */
    public static TextFlow flow(String markup, double baseFontSize, String defaultColor) {
        return toFlow(markup, baseFontSize, defaultColor, -1);
    }

    /** 去除标记后的纯文本（编辑器里显示预览/统计用） */
    public static String plain(String markup) {
        if (markup == null) return "";
        StringBuilder sb = new StringBuilder(markup.length());
        int i = 0, n = markup.length();
        while (i < n) {
            char c = markup.charAt(i);
            if (c == '<') {
                int end = markup.indexOf('>', i);
                if (end < 0) { sb.append(c); i++; continue; }
                String tag = markup.substring(i + 1, end).trim();
                if (tag.equals("br") || tag.equals("br/")) sb.append('\n');
                i = end + 1;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    /** 计算可见字符数（标记被剔除） */
    public static int visibleLength(String markup) {
        return plain(markup).length();
    }
}
