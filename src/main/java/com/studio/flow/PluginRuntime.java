package com.studio.flow;

import com.studio.util.Logs;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 槽插件运行时：负责 加载 / 缓存 / 串行执行 与 信号订阅分发。
 *
 * <h3>插件来源（按优先级）</h3>
 * <ol>
 *   <li>{@code plugins/varplugins.ini}（工程师注册自己的变量插件：{@code id = 全限定类名}）；</li>
 *   <li>{@code plugins/plugins.ini}（与事件插件同一张表，类实现 {@link SlotPlugin} 即可）；</li>
 *   <li>编辑器<b>自带</b>的插件（随编辑器发行，见 {@link com.studio.plugin.builtin.MathPlugin}）。</li>
 * </ol>
 * 类加载沿用“应用类加载器优先 + URLClassLoader 兜底”，与 {@link LogicLoader} 一致，
 * 天然规避 Java 模块化限制。
 *
 * <h3>线程安全</h3>
 * 所有插件执行、变量读写、存档读写、信号派发都由同一把 {@link ReentrantLock} 串行化；
 * 渲染类操作由 {@link PluginContext} 自动切回 JavaFX 线程。
 */
public class PluginRuntime {

    /** 编辑器自带的插件（id 小写） */
    private static final Map<String, java.util.function.Supplier<SlotPlugin>> BUILTIN = new LinkedHashMap<>();

    static {
        // 变量运算插件（随编辑器发行）
        for (String id : com.studio.plugin.builtin.MathPlugin.ids()) {
            final String op = id;
            BUILTIN.put(op, () -> com.studio.plugin.builtin.MathPlugin.of(op));
        }
        // 逻辑运算 / 大小比较插件（随编辑器发行）
        for (String id : com.studio.plugin.builtin.LogicPlugin.ids()) {
            final String op = id;
            BUILTIN.put(op, () -> com.studio.plugin.builtin.LogicPlugin.of(op));
        }
        // 音频播放插件 / 视频嵌入插件（随编辑器发行）
        for (String id : com.studio.plugin.builtin.AudioPlugin.ids()) {
            final String op = id;
            BUILTIN.put(op, () -> new com.studio.plugin.builtin.AudioPlugin(op));
        }
        for (String id : com.studio.plugin.builtin.VideoPlugin.ids()) {
            final String op = id;
            BUILTIN.put(op, () -> new com.studio.plugin.builtin.VideoPlugin(op));
        }
    }

    /** 自带插件的 id 列表（编辑器/文档使用） */
    public static List<String> builtinIds() { return new ArrayList<>(BUILTIN.keySet()); }

    private final List<File> roots = new ArrayList<>();
    private final Map<String, SlotPlugin> instances = new LinkedHashMap<>();
    private final Map<String, Set<String>> subscriptions = new LinkedHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, String> registry = new LinkedHashMap<>();
    private final List<File> registryFiles = new ArrayList<>();

    public PluginRuntime(File projectDir, File mapDir) {
        for (File base : new File[]{projectDir, mapDir}) {
            if (base == null) continue;
            roots.add(new File(base, "plugins"));
            roots.add(new File(base, "plugins/classes"));
        }
        for (File r : roots) {
            if (r.isDirectory()) registryFiles.add(new File(r, "varplugins.ini"));
        }
        for (File r : roots) {
            if (r.isDirectory()) registryFiles.add(new File(r, "plugins.ini"));
        }
        readRegistries();
    }

    public ReentrantLock lock() { return lock; }

    public List<File> roots() { return roots; }

    /** 当前已加载的插件表（id → 插件），供编辑器展示 */
    public Map<String, SlotPlugin> loaded() { return instances; }

    // =====================================================================
    // 加载
    // =====================================================================

    /** 按 id 或全限定类名取插件实例（失败返回 null，并记日志） */
    public SlotPlugin plugin(String idOrClass) {
        if (idOrClass == null || idOrClass.isBlank()) return null;
        String key = idOrClass.trim();
        lock.lock();
        try {
            SlotPlugin cached = instances.get(key);
            if (cached != null) return cached;

            String id = key.toLowerCase(java.util.Locale.ROOT);
            java.util.function.Supplier<SlotPlugin> builtin = BUILTIN.get(id);
            if (builtin != null) {
                SlotPlugin p = builtin.get();
                if (p != null) {
                    instances.put(key, p);
                    Logs.info("[Plugin] 已加载自带插件: " + key);
                    return p;
                }
            }
            String className = registry.getOrDefault(key, registry.getOrDefault(id, key));
            SlotPlugin p = instantiate(className);
            if (p == null) {
                Logs.warn("[Plugin] 找不到槽插件: " + key
                        + "（自带插件: " + String.join("/", BUILTIN.keySet())
                        + "；自定义插件请编译到 plugins/classes 并在 plugins/varplugins.ini 注册）");
                return null;
            }
            instances.put(key, p);
            Logs.info("[Plugin] 已加载插件: " + key + " → " + p.getClass().getName());
            return p;
        } finally {
            lock.unlock();
        }
    }

    private SlotPlugin instantiate(String className) {
        if (className == null || className.isBlank()) return null;
        Class<?> clazz = loadClass(className);
        if (clazz == null) return null;
        if (!SlotPlugin.class.isAssignableFrom(clazz)) {
            Logs.warn("[Plugin] 类 " + className + " 未实现 " + SlotPlugin.class.getName());
            return null;
        }
        try {
            return (SlotPlugin) clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            Logs.error("[Plugin] 实例化失败: " + className, e);
            return null;
        }
    }

    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className, true, PluginRuntime.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            // 继续尝试插件目录
        }
        List<URL> urls = new ArrayList<>();
        for (File root : roots) {
            if (root == null || !root.isDirectory()) continue;
            try {
                urls.add(root.toURI().toURL());
                File[] jars = root.listFiles((d, n) -> n.toLowerCase().endsWith(".jar"));
                if (jars != null) for (File j : jars) urls.add(j.toURI().toURL());
            } catch (IOException e) {
                Logs.warn("[Plugin] 扫描目录失败: " + root + "（" + e.getMessage() + "）");
            }
        }
        if (urls.isEmpty()) return null;
        try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]),
                PluginRuntime.class.getClassLoader())) {
            return Class.forName(className, true, loader);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (IOException e) {
            Logs.warn("[Plugin] 关闭插件加载器失败: " + e.getMessage());
            return null;
        }
    }

    private void readRegistries() {
        for (File ini : registryFiles) {
            if (ini == null || !ini.isFile()) continue;
            try {
                for (String raw : Files.readAllLines(ini.toPath(), StandardCharsets.UTF_8)) {
                    String line = raw.strip();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    registry.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            } catch (IOException e) {
                Logs.warn("[Plugin] 读取注册表失败: " + ini + "（" + e.getMessage() + "）");
            }
        }
    }

    // =====================================================================
    // 执行
    // =====================================================================

    /**
     * 执行插件：全程持有引擎锁，保证变量/存档/信号访问互斥。
     *
     * @return 插件返回的数组（用于回写 @var 位置）；失败返回 null
     */
    public String[] execute(String idOrClass, FlowContext flow, SignalEvent event, String[] args) {
        SlotPlugin plugin = plugin(idOrClass);
        if (plugin == null) return null;
        PluginContext ctx = new PluginContext(flow, this, idOrClass).withEvent(event);
        lock.lock();
        try {
            String[] in = args == null ? new String[0] : args;
            String[] out = plugin.execute(ctx, in);
            if (out == null) return null;
            if (out.length != in.length) {
                Logs.warn("[Plugin] " + idOrClass + " 返回的参数个数(" + out.length
                        + ")与传入(" + in.length + ")不一致，按较短长度回写");
            }
            return out;
        } catch (Throwable t) {
            Logs.error("[Plugin] 执行失败: " + idOrClass, t);
            try {
                ctx.toast("插件执行失败: " + idOrClass);
            } catch (RuntimeException ignored) {
                // 提示失败不影响主流程
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    /** 插件首次加载时的挂载回调 */
    public void attach(String idOrClass, FlowContext flow) {
        SlotPlugin p = plugin(idOrClass);
        if (p == null) return;
        lock.lock();
        try {
            p.onAttach(new PluginContext(flow, this, idOrClass));
        } catch (Throwable t) {
            Logs.error("[Plugin] onAttach 失败: " + idOrClass, t);
        } finally {
            lock.unlock();
        }
    }

    /** 关闭全部插件 */
    public void shutdown() {
        lock.lock();
        try {
            for (Map.Entry<String, SlotPlugin> e : instances.entrySet()) {
                try {
                    e.getValue().onDetach();
                } catch (Throwable t) {
                    Logs.warn("[Plugin] onDetach 失败: " + e.getKey() + "（" + t.getMessage() + "）");
                }
            }
            instances.clear();
            subscriptions.clear();
        } finally {
            lock.unlock();
        }
    }

    // =====================================================================
    // 信号订阅
    // =====================================================================

    public void subscribe(String pluginId, String signalPattern) {
        if (pluginId == null || signalPattern == null || signalPattern.isBlank()) return;
        plugin(pluginId);   // 确保已加载
        lock.lock();
        try {
            subscriptions.computeIfAbsent(pluginId, k -> new LinkedHashSet<>()).add(signalPattern.trim());
            Logs.info("[Plugin] " + pluginId + " 订阅信号: " + signalPattern);
        } finally {
            lock.unlock();
        }
    }

    public void unsubscribe(String pluginId, String signalPattern) {
        lock.lock();
        try {
            Set<String> s = subscriptions.get(pluginId);
            if (s != null) s.remove(signalPattern == null ? "" : signalPattern.trim());
        } finally {
            lock.unlock();
        }
    }

    /** 是否有插件订阅了该信号（供信号总线快速判断） */
    public boolean hasSubscribers(String signal) {
        lock.lock();
        try {
            for (Set<String> patterns : subscriptions.values()) {
                for (String p : patterns) {
                    if ("*".equals(p) || p.equals(signal)) return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 把信号分发给订阅了它的插件（在引擎锁内执行）。
     *
     * @return 收到信号的插件个数
     */
    public int notifySignal(FlowContext flow, SignalEvent event) {
        if (event == null || event.signal() == null) return 0;
        List<String> targets = new ArrayList<>();
        lock.lock();
        try {
            for (Map.Entry<String, Set<String>> e : subscriptions.entrySet()) {
                for (String p : e.getValue()) {
                    if ("*".equals(p) || p.equals(event.signal())) {
                        targets.add(e.getKey());
                        break;
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        int n = 0;
        for (String id : targets) {
            SlotPlugin p = plugin(id);
            if (p == null) continue;
            PluginContext ctx = new PluginContext(flow, this, id).withEvent(event);
            lock.lock();
            try {
                p.onSignal(ctx, event);
                n++;
            } catch (Throwable t) {
                Logs.error("[Plugin] onSignal 失败: " + id, t);
            } finally {
                lock.unlock();
            }
        }
        return n;
    }
}
