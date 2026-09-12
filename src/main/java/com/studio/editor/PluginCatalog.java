package com.studio.editor;

import com.studio.flow.PluginRuntime;
import com.studio.flow.SlotPlugin;
import com.studio.plugin.builtin.BuiltinCatalog;
import com.studio.plugin.builtin.PluginInfo;
import com.studio.util.Logs;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * 编辑器用的<b>插件总目录</b>：自带内置插件 + 外部插件（注册表里登记的、以及已编译但没登记的）。
 *
 * <p>「插入插件槽」的下拉框以前只列自带插件，工程师自己写的插件（放进 {@code plugins/classes}
 * 或打成 jar，并在 {@code varplugins.ini} / {@code plugins.ini} 里登记）就看不到，只能手打
 * {@code @plugin(全限定类名)}。现在这些都会一起列出来，并且按“能不能真的用于 @plugin 槽”过滤：</p>
 *
 * <ol>
 *   <li><b>自带插件</b>：{@link BuiltinCatalog} 里的条目（含中文别名与用法）；</li>
 *   <li><b>注册表插件</b>：{@code 工程根/plugins} 与 {@code 地图/plugins} 下的
 *       {@code varplugins.ini}、{@code plugins.ini} 里登记的 {@code 名字 = 全限定类名}；</li>
 *   <li><b>已编译插件</b>：{@code plugins/classes/**} 与 {@code plugins/*.jar} 里
 *       实现了 {@link SlotPlugin} 的类（即使还没登记也能直接写全限定类名用）；</li>
 *   <li>加载不成功、或者只实现了 {@code GamePlugin}（事件插件）的项会被<b>排除</b>，
 *       并在返回值里通过 {@link Catalog#problems} 报告，编辑器可以提示“为什么某个插件没出现在列表里”。</li>
 * </ol>
 *
 * <p>每一项都会真的用 {@link PluginRuntime} 加载一次，所以显示出来的名字/说明/用法来自插件本身，
 * 不会和代码脱节。（加载是缓存的，编辑器打开一次对话框不会有明显开销。）</p>
 */
public final class PluginCatalog {

    private PluginCatalog() { }

    /** 目录结果：可用插件列表 + 被排除的注册项（带原因） */
    public static class Catalog {
        private final List<PluginInfo> items = new ArrayList<>();
        private final List<String> problems = new ArrayList<>();

        public List<PluginInfo> items() { return items; }
        public List<String> problems() { return problems; }
    }

    /** 全部可用插件（自带 + 外部） */
    public static List<PluginInfo> all(File projectDir, File mapDir) {
        return load(projectDir, mapDir).items();
    }

    /**
     * 扫描并校验全部候选插件。
     *
     * @param projectDir 工程根（plugins 目录的父目录），可为 null
     * @param mapDir     当前地图目录，可为 null
     */
    public static Catalog load(File projectDir, File mapDir) {
        Catalog cat = new Catalog();
        Set<String> seen = new LinkedHashSet<>();

        // ---------- 1) 自带插件 ----------
        for (PluginInfo info : BuiltinCatalog.all()) {
            cat.items().add(info);
            seen.add(info.id().toLowerCase(java.util.Locale.ROOT));
        }

        // ---------- 2) 注册表 ----------
        PluginRuntime rt = new PluginRuntime(projectDir, mapDir);
        Map<String, String> registry = readRegistry(projectDir, mapDir);
        for (Map.Entry<String, String> e : registry.entrySet()) {
            String id = e.getKey();
            String className = e.getValue();
            if (id == null || id.isBlank() || seen.contains(id.toLowerCase(java.util.Locale.ROOT))) continue;
            SlotPlugin p = tryLoad(rt, id);
            if (p == null) {
                // 注册了但加载不了：可能是只实现了 GamePlugin（事件插件），不能用在槽里
                cat.problems().add("注册项「" + id + " → " + className
                        + "」不能用于 @plugin 槽（加载失败或不是 SlotPlugin；事件插件请用节点/场景的 event 属性）");
                continue;
            }
            cat.items().add(external(id, className, p, "注册表"));
            seen.add(id.toLowerCase(java.util.Locale.ROOT));
        }

        // ---------- 3) 已编译但可能没登记的类 ----------
        for (String className : compiledClasses(projectDir, mapDir)) {
            if (seen.contains(className.toLowerCase(java.util.Locale.ROOT))) continue;
            SlotPlugin p = tryLoad(rt, className);
            if (p == null) continue;                     // 不是槽插件（或依赖缺失）：静默跳过，不用打扰人
            cat.items().add(external(className, className, p, "plugins/classes 或 jar"));
            seen.add(className.toLowerCase(java.util.Locale.ROOT));
        }

        return cat;
    }

    // =====================================================================

    private static PluginInfo external(String id, String className, SlotPlugin p, String source) {
        String name = safe(p.name());
        String desc = safe(p.description());
        String usage = safe(p.usage());
        if (usage.isBlank()) usage = "@plugin(" + id + ")";
        else if (!usage.startsWith("@plugin(")) usage = "@plugin(" + id + ") | " + usage;
        StringBuilder d = new StringBuilder();
        if (!name.isBlank()) d.append(name);
        if (!desc.isBlank()) d.append(d.length() > 0 ? "：" : "").append(desc);
        d.append("（").append(source).append("：").append(className).append("）");
        return new PluginInfo(id, "外部插件", "", usage, d.toString());
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }

    /** 用运行时真加载一次；拿不到实例返回 null */
    private static SlotPlugin tryLoad(PluginRuntime rt, String id) {
        try {
            return rt.plugin(id);
        } catch (Throwable t) {
            Logs.warn("[PluginCatalog] 加载插件失败 " + id + "：" + t.getMessage());
            return null;
        }
    }

    /** 合并 plugins/varplugins.ini 与 plugins/plugins.ini（地图目录优先，同名覆盖） */
    static Map<String, String> readRegistry(File projectDir, File mapDir) {
        Map<String, String> out = new LinkedHashMap<>();
        for (File base : new File[]{projectDir, mapDir}) {
            if (base == null) continue;
            for (String ini : new String[]{"plugins/varplugins.ini", "plugins/plugins.ini"}) {
                File f = new File(base, ini);
                if (!f.isFile()) continue;
                try {
                    for (String raw : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                        String line = raw.strip();
                        if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;
                        int eq = line.indexOf('=');
                        if (eq <= 0) continue;
                        out.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                    }
                } catch (Exception e) {
                    Logs.warn("[PluginCatalog] 读注册表失败 " + f + "：" + e.getMessage());
                }
            }
        }
        return out;
    }

    /** plugins/classes/** 与 plugins/*.jar 里看起来像插件类的全限定名 */
    static List<String> compiledClasses(File projectDir, File mapDir) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (File base : new File[]{projectDir, mapDir}) {
            if (base == null) continue;
            File classes = new File(base, "plugins/classes");
            if (classes.isDirectory()) {
                collectFromDir(classes, classes, out, seen);
            }
            File pluginDir = new File(base, "plugins");
            File[] jars = pluginDir.listFiles((d, n) -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".jar"));
            if (jars != null) {
                for (File jar : jars) collectFromJar(jar, out, seen);
            }
        }
        return out;
    }

    private static void collectFromDir(File root, File dir, List<String> out, Set<String> seen) {
        File[] files = dir.listFiles();
        if (files == null || out.size() > 500) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectFromDir(root, f, out, seen);
            } else if (f.getName().endsWith(".class") && f.getName().indexOf('$') < 0) {
                String rel = root.toPath().relativize(f.toPath()).toString().replace('\\', '/');
                String fqn = rel.substring(0, rel.length() - ".class".length()).replace('/', '.');
                if (seen.add(fqn)) out.add(fqn);
            }
        }
    }

    private static void collectFromJar(File jar, List<String> out, Set<String> seen) {
        try (JarFile jf = new JarFile(jar)) {
            int n = 0;
            var en = jf.entries();
            while (en.hasMoreElements() && n < 500) {
                var e = en.nextElement();
                String name = e.getName();
                if (!name.endsWith(".class") || name.indexOf('$') >= 0) continue;
                String fqn = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                if (seen.add(fqn)) {
                    out.add(fqn);
                    n++;
                }
            }
        } catch (Exception e) {
            Logs.warn("[PluginCatalog] 扫描 jar 失败 " + jar + "：" + e.getMessage());
        }
    }
}
