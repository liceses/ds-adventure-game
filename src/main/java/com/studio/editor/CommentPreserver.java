package com.studio.editor;

import com.studio.model.GameProject;
import com.studio.model.GameScene;
import com.studio.model.StoryNode;
import com.studio.parser.ScriptParser;
import com.studio.util.Logs;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <b>保存时保住手写的注释</b>。
 *
 * <p>编辑器以前保存是“整篇重新生成”：{@link com.studio.parser.ScriptWriter#serialize(GameProject)}
 * 写出来的地图里没有注释，于是地图工程师在 {@code scenario.txt} 里手写的分段注释、TODO、批注，
 * 只要在编辑器里点一次保存就全没了。这个类在写盘前做一次合并：</p>
 *
 * <ol>
 *   <li>把<b>磁盘上旧文件</b>里的注释/空行按“挂在谁头上”收集起来 ——
 *       挂在场景（{@code [场景名]} 上方那段）、挂在节点（{@code {} 上方那段）、
 *       挂在某个属性行（{@code key = value} 上方那段）、挂在 {@code [option]} 的某个键上方，以及文件末尾；</li>
 *   <li>在新生成的文本里按同样规则找出这些锚点，把旧注释原样插回对应行的前面；</li>
 *   <li>场景/节点改名或删除后，先用“场景名 / 节点 id”匹配，匹配不上再按“第几个场景 / 第几个同类型节点”兜底，
 *       仍然匹配不上的会记进 {@link Result#notes()}（编辑器会提示，不静默丢）。</li>
 * </ol>
 *
 * <p>安全性：{@link #merge} 只“插注释”，绝不修改任何一行由 {@code ScriptWriter} 生成的内容；
 * {@link EditorPane#saveMap()} 还会把合并结果重新解析一遍、和未合并版本比对结构，
 * 不一致就放弃合并 —— 宁可不保注释，也不能写坏地图。</p>
 */
public final class CommentPreserver {

    private CommentPreserver() { }

    /** 合并结果 */
    public static final class Result {
        private final String text;
        private final int kept;
        private final int lost;
        private final List<String> notes;

        Result(String text, int kept, int lost, List<String> notes) {
            this.text = text;
            this.kept = kept;
            this.lost = lost;
            this.notes = notes;
        }

        /** 合并后的文本（没得合并时就是传入的新文本） */
        public String text() { return text; }
        /** 保住的注释块数 */
        public int kept() { return kept; }
        /** 找不到落点而丢掉的注释块数 */
        public int lost() { return lost; }
        /** 给人看的说明（丢了哪些） */
        public List<String> notes() { return notes; }
    }

    /**
     * 一个可以挂注释的位置。
     *
     * <p>{@code key} 是主 key（场景名 / 节点 id / 属性名），{@code alias} 是兜底 key
     * （“第几个场景 · 第几个节点”这种位置信息）：改名或 id 变了，主 key 对不上，还能靠兜底 key 留住注释。
     * 节点块的主 key 要等读到 {@code id}/{@code type} 才知道，所以先占位、之后再改写。</p>
     */
    private static final class Anchor {
        String key;
        final String alias;
        final int lineIndex;
        Anchor(String key, String alias, int lineIndex) {
            this.key = key;
            this.alias = alias;
            this.lineIndex = lineIndex;
        }
    }

    /** 一次扫描的结果 */
    private static final class Scan {
        /** 全部锚点（按出现顺序） */
        final List<Anchor> points = new ArrayList<>();
        /** 主 key → 注释块（只有“收集旧文件”时才填） */
        final Map<String, List<String>> blocks = new LinkedHashMap<>();
    }

    // =====================================================================

    /** 读旧文件、把其中的注释合并进新文本 */
    public static Result merge(File original, String fresh) {
        if (fresh == null) return new Result("", 0, 0, new ArrayList<>());
        if (fresh.isBlank()) return new Result(fresh, 0, 0, new ArrayList<>());   // 空内容没什么可合的
        List<String> oldLines = new ArrayList<>();
        if (original != null && original.isFile()) {
            try {
                oldLines = Files.readAllLines(original.toPath(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                Logs.warn("[注释保留] 读旧文件失败，跳过合并：" + e.getMessage());
                return new Result(fresh, 0, 0, new ArrayList<>());
            }
        }
        if (oldLines.isEmpty()) return new Result(fresh, 0, 0, new ArrayList<>());

        List<String> freshLines = split(fresh);
        Scan oldScan = scan(oldLines, true);
        if (oldScan.blocks.isEmpty()) return new Result(fresh, 0, 0, new ArrayList<>());
        Scan freshScan = scan(freshLines, false);

        // 新文本里：主 key → 行号，兜底 key → 行号（主 key 对不上时用）
        Map<String, Integer> lineByKey = new LinkedHashMap<>();
        Map<String, Integer> aliasLine = new LinkedHashMap<>();
        Map<String, String> aliasToPrimary = new LinkedHashMap<>();
        for (Anchor a : freshScan.points) {
            lineByKey.putIfAbsent(a.key, a.lineIndex);
            if (a.alias != null) {
                aliasLine.putIfAbsent(a.alias, a.lineIndex);
                aliasToPrimary.putIfAbsent(a.alias, a.key);
            }
        }
        // 旧文件里：主 key → 兜底 key（用于主 key 匹配不上时的二次尝试）
        Map<String, String> oldAlias = new LinkedHashMap<>();
        for (Anchor a : oldScan.points) {
            if (a.alias != null) oldAlias.putIfAbsent(a.key, a.alias);
        }

        Map<Integer, List<List<String>>> insertAt = new LinkedHashMap<>();
        Set<String> placed = new LinkedHashSet<>();
        int kept = 0;
        List<String> tail = null;
        for (Map.Entry<String, List<String>> e : oldScan.blocks.entrySet()) {
            if (e.getKey().equals("__tail__")) { tail = e.getValue(); continue; }   // 文件末尾单独处理
            Integer at = lineByKey.get(e.getKey());
            if (at == null) {
                String alias = oldAlias.get(e.getKey());
                if (alias != null) at = aliasLine.get(alias);
            }
            if (at == null) continue;
            placed.add(e.getKey());
            insertAt.computeIfAbsent(at, k -> new ArrayList<>()).add(e.getValue());
            kept++;
        }

        StringBuilder sb = new StringBuilder(fresh.length() + 512);
        for (int i = 0; i < freshLines.size(); i++) {
            List<List<String>> blocks = insertAt.get(i);
            if (blocks != null) {
                for (List<String> block : blocks) {
                    for (String line : block) sb.append(line).append('\n');
                }
            }
            sb.append(freshLines.get(i)).append('\n');
        }
        if (tail != null) {                       // 旧文件末尾那段注释照样留在末尾
            for (String line : tail) sb.append(line).append('\n');
            placed.add("__tail__");
            kept++;
        }

        List<String> notes = new ArrayList<>();
        int lost = 0;
        for (Map.Entry<String, List<String>> e : oldScan.blocks.entrySet()) {
            if (placed.contains(e.getKey())) continue;
            lost++;
            if (notes.size() < 5) notes.add(describe(e.getKey()) + " 的注释没有落点：" + firstComment(e.getValue()));
        }
        if (lost > 0) notes.add("共 " + lost + " 处注释没有落点（对应的场景/节点可能已被删除或改名）");
        return new Result(sb.toString(), kept, lost, notes);
    }

    // =====================================================================
    // 扫描：找出锚点，并在收集模式下把“锚点上方那段注释”挂上去
    // =====================================================================

    private static Scan scan(List<String> lines, boolean collect) {
        Scan s = new Scan();
        String scene = null;
        int sceneOrdinal = 0;
        int nodeOrdinal = 0;
        boolean inOption = false;
        boolean heredoc = false;
        List<String> pending = new ArrayList<>();

        Anchor nodeAnchor = null;           // 当前节点块的锚点（key 会随 id/type 改写）
        String nodeAlias = null;            // 当前节点的兜底 key：第几个场景 · 第几个节点
        List<String> pendingForNode = null; // 节点块上方那段注释（收集用：等认出 id/type 再挂）
        Map<String, Integer> keyCount = new LinkedHashMap<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String t = line.trim();

            if (heredoc) {
                if (t.equals("<<<")) heredoc = false;
                continue;
            }
            if (t.isEmpty() || t.startsWith("#")) {
                if (nodeAnchor != null && collect && pendingForNode == null) {
                    pendingForNode = new ArrayList<>(pending);   // 块内注释：先跟着节点走
                    pending.clear();
                } else {
                    pending.add(line);
                }
                continue;
            }

            if (t.startsWith("[") && t.endsWith("]")) {
                String name = t.substring(1, t.length() - 1).trim();
                if (name.equalsIgnoreCase("option")) {
                    Anchor a = new Anchor("__option__", null, i);
                    s.points.add(a);
                    if (collect) attach(s, a.key, pending, true);
                    inOption = true;
                    scene = null;
                } else {
                    sceneOrdinal++;
                    scene = name;
                    inOption = false;
                    nodeOrdinal = 0;
                    Anchor a = new Anchor("scene:" + name, "scene@" + sceneOrdinal, i);
                    s.points.add(a);
                    if (collect) attach(s, a.key, pending, false);
                }
                pending.clear();
                nodeAnchor = null;
                nodeAlias = null;
                pendingForNode = null;
                continue;
            }

            if (t.equals("{")) {
                nodeOrdinal++;
                nodeAlias = "node@" + sceneOrdinal + "." + nodeOrdinal;
                // 主 key 先占位（还不知道 id/type），认出 id/type 后改写
                nodeAnchor = new Anchor(nodeAlias, nodeAlias, i);
                s.points.add(nodeAnchor);
                pendingForNode = collect ? new ArrayList<>(pending) : null;
                pending.clear();
                keyCount.clear();
                continue;
            }
            if (t.equals("}")) {
                // 节点块结束：把这段注释挂到（已按 id/type 解析好的）节点 key 上
                if (collect && nodeAnchor != null && pendingForNode != null && !pendingForNode.isEmpty()) {
                    attach(s, nodeAnchor.key, pendingForNode, false);
                } else if (collect && nodeAnchor != null && !pending.isEmpty()) {
                    attach(s, nodeAnchor.key + ":tail", pending, false);
                }
                pending.clear();
                nodeAnchor = null;
                nodeAlias = null;
                pendingForNode = null;
                continue;
            }

            int eq = t.indexOf('=');
            if (eq <= 0) {
                pending.clear();
                continue;
            }
            String key = t.substring(0, eq).trim();
            String val = t.substring(eq + 1).trim();
            if (isHeredocStart(t)) heredoc = true;

            if (nodeAnchor != null) {
                int n = keyCount.merge(key, 1, Integer::sum);
                if (key.equals("id") && !val.isBlank()) {
                    nodeAnchor.key = "node:id:" + val;                       // 最稳：全图唯一的 id
                } else if (key.equals("type") && nodeAnchor.key.equals(nodeAlias)) {
                    nodeAnchor.key = "node:type:" + val + "@" + nodeOrdinal;  // 没 id 就按“类型+序号”
                }
                Anchor a = new Anchor(nodeAnchor.key + ":prop:" + key + "#" + n,
                        nodeAlias + ":prop:" + key + "#" + n, i);
                s.points.add(a);
                if (collect) attach(s, a.key, pending, false);
            } else if (inOption) {
                Anchor a = new Anchor("option:" + key, null, i);
                s.points.add(a);
                if (collect) attach(s, a.key, pending, false);
            } else if (scene != null) {
                int n = keyCount.merge(key, 1, Integer::sum);
                Anchor a = new Anchor("scene:" + scene + ":prop:" + key + "#" + n,
                        "scene@" + sceneOrdinal + ":prop:" + key + "#" + n, i);
                s.points.add(a);
                if (collect) attach(s, a.key, pending, false);
            }
            pending.clear();
        }
        // 文件末尾那段
        if (collect) attach(s, "__tail__", pending, false);
        return s;
    }

    /** 把一段注释记到 key 上（空段、重复 key 不记） */
    private static void attach(Scan s, String key, List<String> raw, boolean headerArea) {
        List<String> block = normalize(raw);
        if (headerArea) block.removeIf(CommentPreserver::isGeneratedHeader);
        block = normalize(block);
        if (block.isEmpty()) return;
        if (!s.blocks.containsKey(key)) s.blocks.put(key, block);
    }

    // =====================================================================
    // 小工具
    // =====================================================================

    private static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\n", -1)) out.add(line);
        if (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) out.remove(out.size() - 1);
        return out;
    }

    private static boolean isHeredocStart(String t) {
        int eq = t.indexOf('=');
        return eq > 0 && t.substring(eq + 1).trim().equals("<<<");
    }

    /** 编辑器每次自动写的那几行标题（不保留，每次重新生成） */
    private static boolean isGeneratedHeader(String line) {
        String t = line.trim();
        return t.startsWith("# ===") || t.contains("剧情地图:") || t.contains("由 剧情编辑器")
                || t.startsWith("# 语法:");
    }

    /** 去掉首尾空行、连续空行压成一行（空块返回空表） */
    private static List<String> normalize(List<String> block) {
        List<String> out = new ArrayList<>();
        boolean lastBlank = true;
        for (String line : block) {
            boolean blank = line.trim().isEmpty();
            if (blank) {
                if (lastBlank) continue;
                lastBlank = true;
            } else {
                lastBlank = false;
            }
            out.add(line);
        }
        while (!out.isEmpty() && out.get(0).trim().isEmpty()) out.remove(0);
        while (!out.isEmpty() && out.get(out.size() - 1).trim().isEmpty()) out.remove(out.size() - 1);
        return out;
    }

    private static String describe(String key) {
        if (key.startsWith("scene:")) return "场景「" + key.substring(6).split(":")[0] + "」";
        if (key.startsWith("node:")) {
            String k = key.substring(5);
            int prop = k.indexOf(":prop:");
            if (prop >= 0) k = k.substring(0, prop);
            int tail = k.indexOf(":tail");
            if (tail >= 0) k = k.substring(0, tail);
            return "节点「" + k.replace("id:", "").replace("type:", "类型 ") + "」";
        }
        if (key.startsWith("option:")) return "[option] 的 " + key.substring(7);
        if (key.equals("__option__")) return "[option]";
        if (key.equals("__tail__")) return "文件末尾";
        return key;
    }

    private static String firstComment(List<String> block) {
        for (String line : block) {
            if (!line.trim().isEmpty()) return line.trim();
        }
        return "";
    }

    // =====================================================================
    // 写盘前的结构校验（EditorPane.saveMap 用）
    // =====================================================================

    /** 合并前后结构是否一致：场景名、每场景节点数、节点 id 与类型 */
    public static boolean sameStructure(File rootDir, String base, String merged) {
        try {
            GameProject a = ScriptParser.parseString(base, rootDir, new ArrayList<>());
            GameProject b = ScriptParser.parseString(merged, rootDir, new ArrayList<>());
            if (a.scenes().size() != b.scenes().size()) return false;
            for (Map.Entry<String, GameScene> e : a.scenes().entrySet()) {
                GameScene sb = b.scenes().get(e.getKey());
                if (sb == null || sb.nodes().size() != e.getValue().nodes().size()) return false;
                for (int i = 0; i < e.getValue().nodes().size(); i++) {
                    StoryNode na = e.getValue().nodes().get(i);
                    StoryNode nb = sb.nodes().get(i);
                    String ia = na.getId() == null ? "" : na.getId();
                    String ib = nb.getId() == null ? "" : nb.getId();
                    if (!ia.equals(ib) || na.getType() != nb.getType()) return false;
                }
            }
            return true;
        } catch (Throwable t) {
            Logs.warn("[注释保留] 结构校验异常，放弃合并：" + t.getMessage());
            return false;
        }
    }
}
