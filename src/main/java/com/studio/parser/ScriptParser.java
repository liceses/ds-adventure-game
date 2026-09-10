package com.studio.parser;

import com.studio.flow.SignalCodec;
import com.studio.flow.SignalDef;
import com.studio.flow.SlotDef;
import com.studio.model.GameOption;
import com.studio.model.GameProject;
import com.studio.model.GameScene;
import com.studio.model.StoryNode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * scenario.txt 解析器 —— “文本 → 模型”方向。
 *
 * <h3>核心逻辑（逐行状态机）</h3>
 * <pre>
 *   去掉注释与空行后，文本被切成三种段落：
 *   1) [option]   → 后续键值行写入 GameOption（有序保留）；
 *   2) [场景名]    → 新建 GameScene；其后直到第一个 '{' 之间的键值行
 *                    属于“场景级属性”（如 event / next）；
 *   3) { ... } 节点块 → 一个 StoryNode；块内键值行 = 节点属性。
 * </pre>
 *
 * 键值行采用“首个 '=' 切分”。值支持两种形态：
 * <ul>
 *   <li>内联值：text = 你好 \n 世界 —— 自动解析 \n \t \\ 转义；</li>
 *   <li>Heredoc 多行：text = &lt;&lt;&lt; 之后逐行读取，直到独立一行的
 *       &lt;&lt;&lt; 结束（适合包含大量换行的富文本，可原样保留缩进）。</li>
 * </ul>
 *
 * 键名宽容：中文别名（类型/文本…）与英文规范键等价；未知键不丢弃，
 * 全部透传到 extras / props，从而保证“读入 → 编辑 → 再导出”不丢字段。
 *
 * <h3>双向同步保证</h3>
 * 读侧是本类，写侧是 {@link ScriptWriter}。二者遵循同一份
 * “规范键顺序 + 顺序保持 + 未知键透传”约定：
 * {@code 读(写(模型)) 与 模型 逐字段一致}。
 */
public final class ScriptParser {

    /** Heredoc 多行值的起止记号 */
    private static final String HEREDOC = "<<<";

    private ScriptParser() { }

    /** 解析 scenario.txt（入口） */
    public static GameProject parse(File scenarioFile) {
        List<String> warnings = new ArrayList<>();
        GameProject p = parse(scenarioFile, warnings);
        p.warnings().addAll(warnings);
        return p;
    }

    /** 解析 scenario.txt（宽容模式；非致命问题追加进 warnings） */
    public static GameProject parse(File scenarioFile, List<String> warnings) {
        List<String> lines;
        try {
            lines = Files.readAllLines(scenarioFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ParserException("无法读取脚本文件: " + scenarioFile.getAbsolutePath()
                    + "（" + e.getMessage() + "）");
        }
        GameProject p = parseLines(lines, scenarioFile.getParentFile(), warnings);
        p.warnings().addAll(warnings);
        return p;
    }

    /** 解析字符串内容（测试/导入用） */
    public static GameProject parseString(String content, File rootDir, List<String> warnings) {
        GameProject p = parseLines(List.of(content.split("\\r?\\n", -1)), rootDir, warnings);
        p.warnings().addAll(warnings);
        return p;
    }

    private static GameProject parseLines(List<String> lines, File rootDir, List<String> warnings) {
        GameProject project = new GameProject(rootDir);

        GameScene currentScene = null;   // 当前场景
        StoryNode currentNode = null;    // 当前节点（{ ... } 内）
        boolean inNode = false;          // 是否处于节点块内
        boolean inOption = false;        // 是否处于 [option] 段

        // ---- Heredoc 收集状态 ----
        // owner: 0=无, 1=节点属性, 2=场景属性, 3=[option] 属性
        int heredocOwner = 0;
        String heredocKey = null;
        StringBuilder heredocBuf = null;

        for (int i = 0; i < lines.size(); i++) {
            int lineNo = i + 1;
            String raw = lines.get(i);
            String line = raw.strip();

            // 注释与空行（Heredoc 内部除外 —— 里面可能是以 # 开头的台词）
            if (line.isEmpty()) continue;
            if (heredocOwner == 0 && (line.startsWith("#") || line.startsWith("//"))) continue;

            // ---------- Heredoc 内容收集 ----------
            if (heredocOwner != 0) {
                if (line.equals(HEREDOC) || line.equals(">>>")) {
                    String content = heredocBuf.toString();
                    if (content.endsWith("\n")) {
                        content = content.substring(0, content.length() - 1);
                    }
                    deliverHeredoc(content, heredocKey, heredocOwner,
                            currentNode, currentScene, inOption ? project.option() : null, warnings, lineNo);
                    heredocOwner = 0;
                    heredocKey = null;
                    heredocBuf = null;
                } else {
                    // 逐行保留原始缩进，仅去行尾空白
                    heredocBuf.append(stripTrailing(raw)).append('\n');
                }
                continue;
            }

            // ---------- 段落头 [name] / [option] ----------
            if (line.length() > 2 && line.startsWith("[") && line.endsWith("]")) {
                String title = line.substring(1, line.length() - 1).trim();
                if (title.equalsIgnoreCase("option")) {
                    inOption = true;
                    inNode = false;
                    currentScene = null;
                    currentNode = null;
                    continue;
                }
                // 普通场景
                inOption = false;
                inNode = false;
                currentNode = null;
                if (project.scenes().containsKey(title)) {
                    // 宽容策略：重复的场景段视为“继续补充该场景”，合并节点并告警
                    warnings.add("第 " + lineNo + " 行: 场景 [" + title + "] 重复定义，节点将合并到该场景");
                    currentScene = project.getScene(title);
                } else {
                    GameScene s = new GameScene(title);
                    project.scenes().put(title, s);
                    currentScene = s;
                }
                continue;
            }

            // ---------- 节点块开/关 ----------
            if (line.equals("{")) {
                if (currentScene == null) {
                    throw new ParserException("尚未进入任何场景就出现 '{'，无法归置节点", lineNo, warnings);
                }
                if (inNode) {
                    warnings.add("第 " + lineNo + " 行: 嵌套 '{' 已忽略");
                } else {
                    currentNode = new StoryNode();
                    currentScene.nodes().add(currentNode);
                    inNode = true;
                }
                continue;
            }
            if (line.equals("}")) {
                if (!inNode) {
                    warnings.add("第 " + lineNo + " 行: 多余的 '}' 已忽略");
                } else {
                    applyTypeDefaults(currentNode);
                    inNode = false;
                    currentNode = null;
                }
                continue;
            }

            // ---------- 键值行 key = value ----------
            int eq = line.indexOf('=');
            if (eq <= 0) {
                warnings.add("第 " + lineNo + " 行: 无法识别的行已忽略: " + truncate(raw));
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();

            if (inNode) {
                if (value.equals(HEREDOC)) {
                    heredocOwner = 1;
                    heredocKey = key;
                    heredocBuf = new StringBuilder();
                } else {
                    String canonical = StoryNode.KEY_ALIAS.getOrDefault(key, key);
                    // 信号/槽是“可重复行”，直接使用原始值（不做 \n 反转义，交由 SignalCodec 处理转义）
                    if ("signal".equals(canonical)) {
                        addNodeSignal(currentNode, value, warnings, lineNo);
                    } else if ("slot".equals(canonical)) {
                        addNodeSlot(currentNode, value, warnings, lineNo);
                    } else {
                        setNodeProperty(currentNode, canonical, unescape(value), warnings, lineNo);
                    }
                }
            } else if (inOption) {
                project.option().putAliasedProperty(key, unescape(value));
            } else if (currentScene != null) {
                if (value.equals(HEREDOC)) {
                    heredocOwner = 2;
                    heredocKey = key;
                    heredocBuf = new StringBuilder();
                } else {
                    String canonical = GameScene.SCENE_KEY_ALIAS.getOrDefault(key, key);
                    if ("signal".equals(canonical)) {
                        addSceneSignal(currentScene, value, warnings, lineNo);
                    } else if ("slot".equals(canonical)) {
                        addSceneSlot(currentScene, value, warnings, lineNo);
                    } else {
                        currentScene.putAliasedProperty(canonical, unescape(value));
                    }
                }
            } else {
                warnings.add("第 " + lineNo + " 行: 落在段落之外的键值已忽略: " + truncate(raw));
            }
        }

        // ---------- 收尾检查 ----------
        if (heredocOwner != 0) {
            String content = heredocBuf.toString();
            if (content.endsWith("\n")) content = content.substring(0, content.length() - 1);
            deliverHeredoc(content, heredocKey, heredocOwner,
                    currentNode, currentScene, inOption ? project.option() : null, warnings, lines.size());
        }
        if (inNode) {
            warnings.add("文件结束时 '{' 尚未闭合，该节点仍被保留");
            applyTypeDefaults(currentScene != null && !currentScene.nodes().isEmpty()
                    ? currentScene.nodes().get(currentScene.nodes().size() - 1) : null);
        }

        // 初始场景为空 → 回退第一个场景
        if (project.option().initialScene().isBlank() && project.firstScene() != null) {
            project.option().setInitialScene(project.firstScene().getName());
        }
        return project;
    }

    // =====================================================================
    // 信号 / 槽（可重复行）
    // =====================================================================

    private static void addNodeSignal(StoryNode node, String raw, List<String> warnings, int lineNo) {
        SignalDef def = SignalCodec.decodeSignal(raw, warnings);
        if (def != null) node.signals().add(def);
    }

    private static void addNodeSlot(StoryNode node, String raw, List<String> warnings, int lineNo) {
        SlotDef def = SignalCodec.decodeSlot(raw, warnings);
        if (def != null) node.slots().add(def);
    }

    private static void addSceneSignal(GameScene scene, String raw, List<String> warnings, int lineNo) {
        SignalDef def = SignalCodec.decodeSignal(raw, warnings);
        if (def != null) scene.signals().add(def);
    }

    private static void addSceneSlot(GameScene scene, String raw, List<String> warnings, int lineNo) {
        SlotDef def = SignalCodec.decodeSlot(raw, warnings);
        if (def != null) scene.slots().add(def);
    }

    /** 手工脚本省略 width/height 时回填类型默认尺寸（music 除外） */    private static void applyTypeDefaults(StoryNode node) {
        if (node == null) return;
        if (node.getType() != com.studio.model.NodeType.MUSIC) {
            if (node.getWidth() <= 0) node.setWidth(node.getType().defaultWidth());
            if (node.getHeight() <= 0) node.setHeight(node.getType().defaultHeight());
        }
    }

    private static void deliverHeredoc(String content, String key, int owner,
                                       StoryNode node, GameScene scene, GameOption option,
                                       List<String> warnings, int lineNo) {
        if (key == null) return;
        try {
            switch (owner) {
                case 1 -> node.setScriptProperty(StoryNode.KEY_ALIAS.getOrDefault(key, key), content);
                case 2 -> scene.putAliasedProperty(key, content);
                case 3 -> option.putAliasedProperty(key, content);
                default -> { }
            }
        } catch (RuntimeException e) {
            warnings.add("第 " + lineNo + " 行: 属性 " + key + " 赋值失败: " + e.getMessage());
        }
    }

    private static void setNodeProperty(StoryNode node, String key, String value,
                                        List<String> warnings, int lineNo) {
        String canonical = StoryNode.KEY_ALIAS.getOrDefault(key, key);
        try {
            node.setScriptProperty(canonical, value);
        } catch (RuntimeException e) {
            warnings.add("第 " + lineNo + " 行: 属性 " + key + " 赋值失败: " + e.getMessage());
        }
    }

    /** 反转义内联文本中的 \n \t \\ */
    static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> { /* 忽略 \r */ }
                    default -> sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String truncate(String s) {
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }

    private static String stripTrailing(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) end--;
        return s.substring(0, end);
    }
}
