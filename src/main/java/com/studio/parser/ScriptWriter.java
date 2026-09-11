package com.studio.parser;

import com.studio.model.GameOption;
import com.studio.model.GameProject;
import com.studio.model.GameScene;
import com.studio.model.StoryNode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * scenario.txt 序列化器 —— “模型 → 文本”方向（与 {@link ScriptParser} 互为逆过程）。
 *
 * <h3>输出语法（严格稳定，读取器可直接识别）</h3>
 * <pre>
 *   # 头部注释（# 开头，解析时忽略）
 *   [option]                 ← 全局设置段
 *   initialScene = Start
 *   background = #0d0f1c
 *   volume = 0.8
 *
 *   [Start]                  ← 场景段
 *   event = minesweeper      ← 场景级属性（可选）
 *   {                        ← 节点对象
 *   type = bg                ← 属性行：规范键 = 值
 *   x = 0
 *   y = 0
 *   ...
 *   }
 * </pre>
 *
 * 序列化策略：
 * <ul>
 *   <li>属性按“规范键固定顺序”输出（见 {@link StoryNode#SCRIPT_ORDER}），
 *       未知键按读取顺序追加在尾部 —— 与解析器约定一致，保证往返一致；</li>
 *   <li>数值以最简形式输出（整数不带小数点）；默认值省略，保持脚本可读；</li>
 *   <li>含换行的文本自动使用 {@code key = <<< ... <<<} Heredoc 形式；
 *       单行文本的 \ 反斜杠会被转义（\\）以便往返无损。</li>
 * </ul>
 */
public final class ScriptWriter {

    private static final String HEREDOC = "<<<";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ScriptWriter() { }

    /** 把工程序列化到其 scenario.txt（写临时文件后原子替换，避免半写坏文件） */
    public static void write(File scenarioFile, GameProject project) throws IOException {
        Path target = scenarioFile.toPath();
        Path tmp = target.resolveSibling(scenarioFile.getName() + ".tmp");
        Files.writeString(tmp, serialize(project), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    /** 序列化为文本内容 */
    public static String serialize(GameProject project) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("# ============================================================\n");
        sb.append("# 剧情地图: ").append(safeComment(project.name())).append('\n');
        sb.append("# 由 剧情编辑器(Studio) 于 ").append(LocalDateTime.now().format(FMT)).append(" 生成\n");
        sb.append("# 语法: [option] / [场景名] / { 节点属性 } （# 开头为注释）\n");
        sb.append("# ============================================================\n\n");

        // ---------- [option] 全局设置 ----------
        sb.append("[option]\n");
        for (Map.Entry<String, String> e : orderedOption(project.option()).entrySet()) {
            writeKeyValue(sb, e.getKey(), e.getValue());
        }
        // 存档变量声明：一行一个（名称 | 类型 | 初值）
        for (com.studio.model.SaveVarDef d : project.option().saveVars()) {
            if (d == null || !d.isValid()) continue;
            sb.append("savevar = ").append(d.toScriptValue()).append('\n');
        }
        sb.append('\n');

        // ---------- 各场景 ----------
        for (GameScene scene : project.scenes().values()) {
            sb.append('[').append(scene.getName()).append("]\n");
            for (Map.Entry<String, String> e : scene.props().entrySet()) {
                writeKeyValue(sb, e.getKey(), e.getValue());
            }
            // 场景级信号 / 槽（可重复行；编码器自带转义，不再走通用转义）
            for (com.studio.flow.SignalDef sig : scene.signals()) {
                sb.append("signal = ").append(com.studio.flow.SignalCodec.encode(sig)).append('\n');
            }
            for (com.studio.flow.SlotDef slot : scene.slots()) {
                sb.append("slot = ").append(com.studio.flow.SignalCodec.encode(slot)).append('\n');
            }
            if (!scene.props().isEmpty() || !scene.signals().isEmpty() || !scene.slots().isEmpty()) {
                sb.append('\n');
            }
            for (StoryNode node : scene.nodes()) {
                writeNode(sb, node);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    // ---------------- 细节 ----------------

    /** 节点块 */
    private static void writeNode(StringBuilder sb, StoryNode node) {
        sb.append("{\n");
        LinkedHashMap<String, String> attrs = node.toScriptMap();
        for (Map.Entry<String, String> e : attrs.entrySet()) {
            writeKeyValue(sb, e.getKey(), e.getValue());
        }
        // 信号 / 槽（可重复行；编码器自带转义）
        for (com.studio.flow.SignalDef sig : node.signals()) {
            sb.append("signal = ").append(com.studio.flow.SignalCodec.encode(sig)).append('\n');
        }
        for (com.studio.flow.SlotDef slot : node.slots()) {
            sb.append("slot = ").append(com.studio.flow.SignalCodec.encode(slot)).append('\n');
        }
        sb.append("}\n");
    }

    /** 输出一行键值；含换行的值转成 Heredoc 多行形式 */
    private static void writeKeyValue(StringBuilder sb, String key, String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) return;
        if (rawValue.indexOf('\n') >= 0) {
            sb.append(key).append(" = ").append(HEREDOC).append('\n');
            // 原样保留多行内容（解析侧 Heredoc 不反转义），保证文本所见即所得
            for (String ln : rawValue.split("\n", -1)) {
                sb.append(ln).append('\n');
            }
            sb.append(HEREDOC).append('\n');
        } else {
            // 单行：转义反斜杠（路径统一为正斜杠）
            String v = rawValue;
            if (key.equals("path") || key.equals("audio")) {
                v = v.replace('\\', '/');
            }
            sb.append(key).append(" = ").append(escapeInline(v)).append('\n');
        }
    }

    private static String escapeInline(String v) {
        if (v.indexOf('\\') < 0) return v;
        StringBuilder sb = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '\\') sb.append("\\\\");
            else sb.append(c);
        }
        return sb.toString();
    }

    /** [option] 的固定输出顺序（先规范键后未知键） */
    private static LinkedHashMap<String, String> orderedOption(GameOption option) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        String[] order = {GameOption.K_INITIAL, GameOption.K_BG, GameOption.K_VOLUME, GameOption.K_SPEED};
        for (String k : order) {
            if (option.values().containsKey(k)) out.put(k, option.values().get(k));
        }
        for (Map.Entry<String, String> e : option.values().entrySet()) {
            if (!out.containsKey(e.getKey())) out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /** 去掉注释头里的换行，避免污染脚本 */
    private static String safeComment(String s) {
        String t = s.replace('\n', ' ').replace('\r', ' ');
        return t.length() > 40 ? t.substring(0, 40) + "…" : t;
    }
}
