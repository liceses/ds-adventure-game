package com.studio.plugin;

import com.studio.util.AppConfig;
import com.studio.util.Logs;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件动态加载器 —— 核心职责：把“事件 ID”解析为 {@link GamePlugin} 实例。
 *
 * <h3>类加载策略</h3>
 * <pre>
 *   plugins/plugins.ini:      id=全限定类名  （工程师在此注册自己的插件）
 *   内置白名单:               minesweeper → com.studio.plugin.demo.MinesweeperPlugin
 * </pre>
 *
 * 事件 ID 若本身含 '.' 则直接当作全限定类名。
 *
 * <h3>如何规避 Java 模块化（JPMS）限制</h3>
 * <ol>
 *   <li><b>本工程不写 module-info.java</b>（保持非模块化）：主程序与 JavaFX 全部处于
 *       “未命名模块”。JPMS 规则：未命名模块可读取引导层中所有已解析模块，
 *       并能访问它们的导出包 —— 因此运行期加载进来的插件类也能直接使用 javafx.*；</li>
 *   <li>插件目录用 {@link URLClassLoader} 加载，且<b>父加载器 = 应用类加载器</b>
 *       （双亲委派）。GamePlugin 接口、模型类永远由父加载器解析，插件 class/jar
 *       只负责“补位”自己新增的类，天然避免同名类冲突，instanceof 判断也始终安全；</li>
 *   <li>如果未来把主程序改造成模块化（添加 module-info.java），则需要额外：
 *       exports com.studio.plugin（供未命名模块读取），或为插件创建独立
 *       {@code ModuleLayer} / 向加载器注入 --add-reads、--add-exports。
 *       本设计默认保持 classpath 模式运行，故无需上述处理。</li>
 * </ol>
 */
public class PluginLoader {

    /** 内置插件注册表（事件 ID → 类名）；plugins.ini 中同名条目优先覆盖 */
    private static final Map<String, String> BUILTIN = new LinkedHashMap<>();
    static {
        BUILTIN.put("minesweeper", "com.studio.plugin.demo.MinesweeperPlugin");
        BUILTIN.put("2048", "com.studio.plugin.demo.Game2048Plugin");
        BUILTIN.put("savepanel", "com.studio.plugin.demo.SavePanelPlugin");
    }

    private final File pluginDir;      // 插件根目录
    private final ClassLoader parent;  // 应用类加载器

    /** 依据配置创建（plugins.dir 相对 user.dir 解析） */
    public static PluginLoader fromConfig(AppConfig config) {
        String dir = config.get("plugins.dir", "plugins");
        File f = new File(dir);
        if (!f.isAbsolute()) f = new File(System.getProperty("user.dir"), dir);
        return new PluginLoader(f);
    }

    public PluginLoader(File pluginDir) {
        this.pluginDir = pluginDir;
        this.parent = PluginLoader.class.getClassLoader();
    }

    public File pluginDir() { return pluginDir; }

    // =====================================================================
    // 解析 ID
    // =====================================================================

    /** 解析并实例化插件（每次调用都会重新扫描注册表，便于热替换） */
    public GamePlugin load(String eventId) throws PluginException {
        String id = eventId == null ? "" : eventId.trim();
        if (id.isEmpty()) throw new PluginException("事件 ID 为空");
        String className = resolveClassName(id);
        Logs.info("加载插件: id=" + id + " → " + className);

        Class<?> clazz = tryLoadByParent(className);
        String origin = "内置/classpath";
        if (clazz == null) {
            clazz = tryLoadByUrlLoader(className);
            origin = "plugins 目录(URLClassLoader)";
            if (clazz == null) {
                throw new PluginException("在 classpath 与 plugins 目录中都找不到插件类: " + className
                        + "（请确认已编译到 " + pluginDir.getAbsolutePath() + "）");
            }
        }
        if (!GamePlugin.class.isAssignableFrom(clazz)) {
            throw new PluginException("类 " + className + " 未实现 com.studio.plugin.GamePlugin 接口");
        }
        try {
            GamePlugin plugin = (GamePlugin) clazz.getDeclaredConstructor().newInstance();
            Logs.info("插件实例化成功 [" + plugin.displayName() + "]（来源: " + origin + "）");
            return plugin;
        } catch (ReflectiveOperationException e) {
            throw new PluginException("插件实例化失败: " + className + "（" + e.getCause() + "）");
        }
    }

    // =====================================================================

    private String resolveClassName(String id) {
        if (id.indexOf('.') >= 0) return id; // 直接是全限定类名
        // 先查 plugins.ini 注册表，再查内置白名单
        String fromIni = readRegistry().get(id);
        if (fromIni != null && !fromIni.isBlank()) return fromIni.trim();
        String builtin = BUILTIN.get(id);
        if (builtin != null) return builtin;
        return id; // 找不到时按类名尝试，加载失败会给出清晰报错
    }

    /** 读取 plugins/plugins.ini（id=class，忽略 # 注释）；文件不存在返回空表 */
    private Map<String, String> readRegistry() {
        Map<String, String> map = new LinkedHashMap<>();
        File ini = new File(pluginDir, "plugins.ini");
        if (!ini.exists()) return map;
        try {
            for (String raw : Files.readAllLines(ini.toPath(), StandardCharsets.UTF_8)) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;
                int eq = line.indexOf('=');
                if (eq > 0) map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        } catch (IOException e) {
            Logs.warn("读取插件注册表失败: " + ini + "（" + e.getMessage() + "）");
        }
        return map;
    }

    /** 双亲委派第一站：应用 classpath（内置 Demo 插件即走此路径） */
    private Class<?> tryLoadByParent(String className) {
        try {
            return Class.forName(className, true, parent);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /** 第二站：URLClassLoader 扫描插件目录下的 .jar 与编译产物目录 */
    private Class<?> tryLoadByUrlLoader(String className) {
        List<URL> urls = scanUrls();
        if (urls.isEmpty()) return null;
        try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), parent)) {
            return Class.forName(className, true, loader);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (IOException e) {
            Logs.warn("关闭插件类加载器失败: " + e.getMessage());
            return null;
        }
    }

    /** 收集插件目录中的加载源：目录根 + plugins/classes + 所有 *.jar */
    private List<URL> scanUrls() {
        List<URL> urls = new ArrayList<>();
        if (pluginDir == null || !pluginDir.isDirectory()) return urls;
        try {
            urls.add(pluginDir.toURI().toURL());
            File classes = new File(pluginDir, "classes");
            if (classes.isDirectory()) urls.add(classes.toURI().toURL());
            File[] jars = pluginDir.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".jar"));
            if (jars != null) {
                for (File jar : jars) urls.add(jar.toURI().toURL());
            }
        } catch (IOException e) {
            Logs.warn("扫描插件目录失败: " + e.getMessage());
        }
        return urls;
    }

    /** 统一异常 */
    public static class PluginException extends Exception {
        public PluginException(String message) { super(message); }
    }
}
