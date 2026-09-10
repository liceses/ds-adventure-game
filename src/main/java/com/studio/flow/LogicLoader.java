package com.studio.flow;

import com.studio.util.Logs;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 逻辑类动态加载器 —— 使地图工程师的 Java 逻辑与渲染层解耦。
 *
 * <h3>目录约定（两处都会扫描，地图内同名类优先 map 目录）</h3>
 * <pre>
 *   工程根/logic/           ← 全局逻辑：logic.ini(id=类名) + classes/ 或 *.jar
 *   地图根/logic/           ← 该地图专属逻辑（同一套规则）
 * </pre>
 *
 * <p>槽里写 {@code call} 时可用：注册 ID（如 {@code signallab}）、
 * {@code ID#方法名}、或直接写全限定类名（可带 {@code #方法名}）。
 * 类可实现的调用形态：</p>
 * <ul>
 *   <li>{@code implements LogicHandler} → 调用 {@code onSignal(FlowContext, SignalEvent)}；</li>
 *   <li>或提供 public 方法 {@code 任意名(FlowContext, SignalEvent)}（用 {@code #方法名} 指定）。</li>
 * </ul>
 * 类加载与插件相同的“父加载器优先 + URLClassLoader 兜底”策略，天然规避模块化限制。
 */
public class LogicLoader {

    /** 内置逻辑注册表（演示用；logic.ini 可覆盖） */
    private static final Map<String, String> BUILTIN = new LinkedHashMap<>();
    static {
        BUILTIN.put("signallab", "com.studio.logic.demo.SignalLabLogic");
    }

    private final List<File> roots = new ArrayList<>();
    private final Map<String, Object> instances = new LinkedHashMap<>();

    public LogicLoader(File projectDir, File mapDir) {
        if (projectDir != null) {
            roots.add(new File(projectDir, "logic"));
            roots.add(new File(projectDir, "logic/classes"));
        }
        if (mapDir != null) {
            roots.add(new File(mapDir, "logic"));
            roots.add(new File(mapDir, "logic/classes"));
        }
    }

    public List<File> roots() { return roots; }

    // =====================================================================
    // 调用入口
    // =====================================================================

    /** 执行一个 call 槽；失败记日志并返回 false，绝不抛出到事件循环 */
    public boolean invoke(String spec, FlowContext ctx, SignalEvent event) {
        if (spec == null || spec.isBlank()) {
            Logs.warn("call 槽未指定逻辑 ID/类名");
            return false;
        }
        String className = spec;
        String methodName = null;
        int hash = spec.indexOf('#');
        if (hash > 0) {
            className = spec.substring(0, hash).trim();
            methodName = spec.substring(hash + 1).trim();
        }
        try {
            Object instance = instanceOf(className);
            if (instance == null) return false;
            return dispatch(instance, methodName, ctx, event);
        } catch (Throwable t) {
            Logs.error("逻辑执行失败: " + spec, t);
            if (ctx != null) ctx.toast("逻辑执行失败: " + className);
            return false;
        }
    }

    private boolean dispatch(Object instance, String methodName, FlowContext ctx, SignalEvent event)
            throws ReflectiveOperationException {
        if (methodName == null || methodName.isBlank()) {
            if (instance instanceof LogicHandler handler) {
                handler.onSignal(ctx, event);
                return true;
            }
            // 退化为查找约定方法名 onSignal
            methodName = "onSignal";
        }
        Method m = findMethod(instance.getClass(), methodName);
        if (m == null) {
            Logs.warn("逻辑类 " + instance.getClass().getName() + " 缺少方法: " + methodName);
            return false;
        }
        m.setAccessible(true);
        if (m.getParameterCount() == 2) {
            m.invoke(instance, ctx, event);
        } else if (m.getParameterCount() == 1
                && m.getParameterTypes()[0] == FlowContext.class) {
            m.invoke(instance, ctx);
        } else if (m.getParameterCount() == 0) {
            m.invoke(instance);
        } else {
            Logs.warn("逻辑方法签名不支持: " + m);
            return false;
        }
        return true;
    }

    private static Method findMethod(Class<?> clazz, String name) {
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name)) return m;
        }
        return null;
    }

    // =====================================================================
    // 类解析与实例缓存
    // =====================================================================

    private Object instanceOf(String idOrClass) throws ReflectiveOperationException {
        String className = resolveClassName(idOrClass);
        Object cached = instances.get(className);
        if (cached != null) return cached;
        Class<?> clazz = loadClass(className);
        if (clazz == null) {
            Logs.warn("找不到逻辑类: " + className + "（请编译到 logic/classes 或 地图/logic）");
            return null;
        }
        Object instance = clazz.getDeclaredConstructor().newInstance();
        instances.put(className, instance);
        Logs.info("逻辑类已加载: " + className);
        return instance;
    }

    private String resolveClassName(String idOrClass) {
        if (idOrClass.indexOf('.') >= 0) return idOrClass; // 已是全限定名
        Map<String, String> registry = readRegistry();
        String fromIni = registry.get(idOrClass);
        if (fromIni != null && !fromIni.isBlank()) return fromIni.trim();
        String builtin = BUILTIN.get(idOrClass);
        return builtin != null ? builtin : idOrClass;
    }

    /** 读取 logic.ini：工程根/logic/logic.ini 与 地图/logic/logic.ini（后者覆盖前者） */
    private Map<String, String> readRegistry() {
        Map<String, String> map = new LinkedHashMap<>();
        for (File root : roots) {
            if (root == null || !root.isDirectory()) continue;
            File ini = new File(root, "logic.ini");
            if (!ini.isFile()) continue;
            try {
                for (String raw : Files.readAllLines(ini.toPath(), StandardCharsets.UTF_8)) {
                    String line = raw.strip();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;
                    int eq = line.indexOf('=');
                    if (eq > 0) map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            } catch (IOException e) {
                Logs.warn("读取逻辑注册表失败: " + ini + "（" + e.getMessage() + "）");
            }
        }
        return map;
    }

    private Class<?> loadClass(String className) {
        // 1) 应用 classpath（内置/随工程分发的逻辑类）
        try {
            return Class.forName(className, true, LogicLoader.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            // 继续尝试 logic 目录
        }
        // 2) logic 目录（classes/ 或 jar）
        List<URL> urls = new ArrayList<>();
        for (File root : roots) {
            if (root == null || !root.isDirectory()) continue;
            try {
                urls.add(root.toURI().toURL());
                File[] jars = root.listFiles((d, n) -> n.toLowerCase().endsWith(".jar"));
                if (jars != null) {
                    for (File j : jars) urls.add(j.toURI().toURL());
                }
            } catch (IOException e) {
                Logs.warn("扫描逻辑目录失败: " + root + "（" + e.getMessage() + "）");
            }
        }
        if (urls.isEmpty()) return null;
        try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]),
                LogicLoader.class.getClassLoader())) {
            return Class.forName(className, true, loader);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (IOException e) {
            Logs.warn("关闭逻辑类加载器失败: " + e.getMessage());
            return null;
        }
    }
}
