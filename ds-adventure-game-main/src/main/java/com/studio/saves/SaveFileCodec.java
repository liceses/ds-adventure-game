package com.studio.saves;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 存档文本 解析/序列化 编解码器。
 *
 * <h3>文件语法（地图工程师自定内容，本模块只保证结构）：</h3>
 * <pre>
 *   # 以 # 开头的整行为注释（行尾的 # 及其后内容也按注释丢弃）
 *   {变量名: 数值, 数值, 数值}      ← 每个变量一行，可多值
 * </pre>
 *
 * 解析策略：
 * <ul>
 *   <li>忽略空行与注释行；</li>
 *   <li>行内遇第一个 '#' 之后的部分视为注释（变量值都是纯数据，不会含 #）；</li>
 *   <li>必须在行首/行尾成对出现 { }，取冒号(:)分割“变量名”与“数值列表”，逗号分割数值；</li>
 *   <li>数值以原始令牌保存（可 double/int/短字符串），解析不因个别非法行而中断 ——
 *       非法行收集为警告后继续（宽容读取，便于工程师手工修改存档）。</li>
 * </ul>
 *
 * 序列化时输出带时间戳的头部注释，变量按写入顺序逐行输出。
 */
public final class SaveFileCodec {

    private SaveFileCodec() { }

    // =====================================================================
    // 读取
    // =====================================================================

    /** 读取存档文件；文件不存在或读取失败返回 null */
    public static SaveData readOrNull(Path file) {
        if (file == null || !Files.exists(file)) return null;
        List<String> warnings = new ArrayList<>();
        SaveData data;
        try {
            data = read(file, warnings);
        } catch (IOException e) {
            System.out.println("[Saves] 存档读取异常: " + e.getMessage());
            return null;
        }
        if (!warnings.isEmpty()) {
            System.out.println("[Saves] 存档解析警告: " + warnings);
        }
        return data;
    }

    /** 读取存档文件（宽容模式，警告输出到列表） */
    public static SaveData read(Path file, List<String> warnings) throws IOException {
        SaveData data = new SaveData();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            int lineNo = i + 1;
            String raw = lines.get(i);
            String line = stripComment(raw).trim();
            if (line.isEmpty()) continue;
            if (!(line.startsWith("{") && line.endsWith("}"))) {
                if (warnings != null) {
                    warnings.add("第 " + lineNo + " 行: 缺少 {…} 包裹，已忽略: " + raw);
                }
                continue;
            }
            String body = line.substring(1, line.length() - 1);
            int colon = body.indexOf(':');
            if (colon <= 0) {
                if (warnings != null) {
                    warnings.add("第 " + lineNo + " 行: 缺少 ':' 分隔，已忽略: " + raw);
                }
                continue;
            }
            String key = body.substring(0, colon).trim();
            String valuesPart = body.substring(colon + 1);
            List<String> tokens = new ArrayList<>();
            for (String tok : valuesPart.split(",")) {
                String t = tok.trim();
                if (!t.isEmpty()) tokens.add(t);
            }
            data.put(key, tokens.toArray(new String[0]));
        }
        return data;
    }

    /**
     * 去掉注释：
     * 含 {…} 的行 —— 只把“闭合花括号之后”的 # 当注释（值本身可能含 #，如 CSS 颜色值）；
     * 不含 {…} 的行 —— 直接按注释/垃圾行处理。
     */
    public static String stripComment(String line) {
        int close = line.lastIndexOf('}');
        if (close < 0) {
            int idx = line.indexOf('#');
            return idx >= 0 ? line.substring(0, idx) : line;
        }
        int idx = line.indexOf('#', close + 1);
        return idx >= 0 ? line.substring(0, idx) : line;
    }

    // =====================================================================
    // 写入
    // =====================================================================

    /** 序列化文本 */
    public static String render(SaveData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ============================================================\n");
        sb.append("# 剧情存档 · ").append(LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append('\n');
        sb.append("# 语法: {变量名: 数值, 数值}   |   # 开头为注释\n");
        sb.append("# ============================================================\n");
        for (String key : data.keys()) {
            List<String> values = data.values(key);
            sb.append('{').append(key).append(':');
            if (!values.isEmpty()) {
                sb.append(' ').append(String.join(", ", values));
            }
            sb.append("}\n");
        }
        return sb.toString();
    }

    /** 写盘（原子替换） */
    public static void write(SaveData data, Path file) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, render(data), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }
}
