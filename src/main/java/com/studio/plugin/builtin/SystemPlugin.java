package com.studio.plugin.builtin;

import com.studio.flow.FlowHost;
import com.studio.flow.PluginContext;
import com.studio.flow.SlotPlugin;
import com.studio.util.Logs;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 编辑器<b>自带</b>插件：<b>系统级操作</b>（退出游戏 / 打开文件 / 选择文件 / 打开所在目录）。
 *
 * <p>随编辑器与读取器一起发行，脚本里直接写 ID，<b>无需注册、无需外部文件</b>。
 * 典型用途是在剧情地图里做「菜单」：一个槽让玩家退出游戏，一个槽打开说明文档或素材文件。</p>
 *
 * <h3>可用插件 ID（英文 / 中文别名都行）</h3>
 * <table border="1">
 *   <caption>插件 ID 与语义</caption>
 *   <tr><th>ID</th><th>别名</th><th>作用</th></tr>
 *   <tr><td>{@code quit}</td><td>{@code exit} / {@code 退出} / {@code 退出游戏}</td>
 *       <td>退出游戏：交宿主收尾（读取器会释放媒体/插件后关闭播放器窗口）</td></tr>
 *   <tr><td>{@code open}</td><td>{@code openfile} / {@code 打开} / {@code 打开文件}</td>
 *       <td>用系统默认程序打开文件；支持 {@code http(s)://} 链接；参数留空则弹文件选择框</td></tr>
 *   <tr><td>{@code pick}</td><td>{@code pickfile} / {@code 选择文件} / {@code 选文件}</td>
 *       <td>弹文件选择框，把选中的<b>绝对路径写回输出位</b>（存档变量），不打开文件</td></tr>
 *   <tr><td>{@code reveal}</td><td>{@code opendir} / {@code 打开目录} / {@code 打开文件夹} / {@code 所在目录}</td>
 *       <td>在资源管理器里打开该文件所在目录（并尽量选中它）</td></tr>
 * </table>
 *
 * <h3>用法（槽的一行式写法）</h3>
 * <pre>
 *   # 菜单：退出游戏
 *   slot = 退出 | @plugin(quit)
 *
 *   # 打开地图里的说明文档（相对路径按“地图文件夹”解析，也支持绝对路径）
 *   slot = 看攻略 | @plugin(open) | 文档/攻略.txt
 *
 *   # 打开玩家自己挑的文件（不给路径 → 先弹文件选择框，再打开）
 *   slot = 打开 | @plugin(open)
 *
 *   # 让玩家选一张图，把路径存进存档变量（供立绘/背景节点等后续使用）
 *   slot = 选图 | @plugin(pick) | png;jpg | @var(选中立绘)
 *
 *   # 打开存档目录（排查存档文件用）
 *   slot = 存档目录 | @plugin(reveal) | saves/slot1.txt
 * </pre>
 *
 * <p><b>参数约定</b>：{@code 动作 | 路径/过滤 | 输出位}。路径支持表达式（例如 {@code @var(选中立绘)}）；
 * 相对路径先按<b>地图文件夹</b>解析，找不到再按运行目录解析。
 * {@code pick} 的规则：只有 1 个参数时它就是输出位；有 2 个参数时第 1 个是扩展名过滤（如 {@code png;jpg}）、
 * 第 2 个是输出位。<b>玩家取消选择时输出位保持原值</b>，不会被空串覆盖。</p>
 *
 * <p><b>线程安全 / 容错</b>：文件选择框一定在 JavaFX 线程上弹出（后台线程调用时会自动切回并在 30 秒后放弃）；
 * 文件不存在、系统没有默认程序、宿主无法退出等情况都只记日志 + 顶部提示，<b>不会中断剧情</b>。</p>
 */
public class SystemPlugin implements SlotPlugin {

    /** 系统操作种类 */
    public enum Op { QUIT, OPEN, PICK, REVEAL }

    private static final Map<String, Op> IDS = new LinkedHashMap<>();
    static {
        for (String s : new String[]{"quit", "exit", "退出", "退出游戏", "离开游戏"}) IDS.put(s, Op.QUIT);
        for (String s : new String[]{"open", "openfile", "打开", "打开文件", "开启文件"}) IDS.put(s, Op.OPEN);
        for (String s : new String[]{"pick", "pickfile", "选择文件", "选文件", "选择一个文件"}) IDS.put(s, Op.PICK);
        for (String s : new String[]{"reveal", "opendir", "打开目录", "打开文件夹", "所在目录"}) IDS.put(s, Op.REVEAL);
    }

    private final Op op;
    private final String id;

    /** 无参构造：默认 quit（直接写类名注册时也能用） */
    public SystemPlugin() { this(Op.QUIT, "quit"); }

    public SystemPlugin(Op op, String id) {
        this.op = op == null ? Op.QUIT : op;
        this.id = id == null ? "quit" : id;
    }

    /** 全部系统插件 ID（含中文别名） */
    public static List<String> ids() { return new ArrayList<>(IDS.keySet()); }

    /** 按 ID 取插件；未知 ID 返回 null */
    public static SystemPlugin of(String id) {
        if (id == null) return null;
        String k = id.trim().toLowerCase(Locale.ROOT);
        Op o = IDS.get(k);
        if (o == null) o = IDS.get(id.trim());   // 中文别名不受 toLowerCase 影响
        return o == null ? null : new SystemPlugin(o, id.trim());
    }

    @Override
    public String name() { return "系统操作 · " + id; }

    @Override
    public String description() {
        switch (op) {
            case QUIT:   return "退出游戏：交宿主收尾（释放媒体/插件）后关闭播放器窗口";
            case OPEN:   return "用系统默认程序打开文件（支持网址；参数留空则先弹文件选择框）";
            case PICK:   return "弹文件选择框，把选中的绝对路径写回输出位（存档变量）";
            case REVEAL: return "在资源管理器里打开该文件所在目录并尽量选中它";
            default:     return "";
        }
    }

    @Override
    public String usage() {
        switch (op) {
            case QUIT:   return "@plugin(quit)";
            case OPEN:   return "@plugin(open) | 路径或@var(变量)（可留空 → 弹选择框）";
            case PICK:   return "@plugin(pick) | [png;jpg] | @var(选中文件)";
            case REVEAL: return "@plugin(reveal) | 路径或@var(变量)";
            default:     return "";
        }
    }

    // =====================================================================

    @Override
    public String[] execute(PluginContext ctx, String[] args) {
        String[] in = args == null ? new String[0] : args;
        String[] out = Arrays.copyOf(in, in.length);
        try {
            switch (op) {
                case QUIT -> doQuit(ctx);
                case OPEN -> doOpen(ctx, arg(in, 0));
                case REVEAL -> doReveal(ctx, arg(in, 0));
                case PICK -> {
                    String filter = in.length > 1 ? arg(in, 0) : "";   // 1 个参数时它就是输出位
                    String picked = chooseFile(ctx, filter);
                    if (picked.isEmpty()) {
                        // 玩家取消：输出位保持原值（不要用空串覆盖已有变量）
                        log(ctx, "没有选择文件（已取消），输出位保持原值");
                    } else {
                        if (out.length > 0) out[out.length - 1] = picked;
                        log(ctx, "已选择文件：" + picked);
                        toast(ctx, "已选择：" + new File(picked).getName());
                    }
                }
                default -> { /* 不会发生 */ }
            }
        } catch (RuntimeException e) {
            Logs.warn("[System:" + id + "] 执行失败：" + e.getMessage());
        }
        return out;
    }

    // =====================================================================
    // 退出游戏
    // =====================================================================

    private void doQuit(PluginContext ctx) {
        log(ctx, "收到“退出游戏”请求");
        toast(ctx, "正在退出游戏…");
        FlowHost host = ctx == null ? null : ctx.host();
        boolean handled = false;
        if (host != null) {
            try {
                handled = host.requestQuit();
            } catch (RuntimeException e) {
                Logs.warn("[System:quit] 宿主处理退出请求时出错：" + e.getMessage());
            }
        }
        if (!handled) {
            // 宿主不接管时绝不擅自退出进程：编辑器静默预览里 @plugin(quit) 应当是“安全”的
            Logs.warn("[System:quit] 当前宿主未接管退出请求，已忽略（编辑器预览请直接关闭预览窗口）");
            toast(ctx, "当前宿主不支持退出游戏（已忽略）");
        }
    }

    // =====================================================================
    // 打开文件 / 打开目录
    // =====================================================================

    private void doOpen(PluginContext ctx, String raw) {
        String path = raw == null ? "" : raw.trim();
        if (path.isEmpty()) {
            log(ctx, "没有给路径 → 先弹文件选择框");
            path = chooseFile(ctx, "");
            if (path.isEmpty()) {
                log(ctx, "未选择文件，取消打开");
                return;
            }
        }
        if (isUrl(path)) {
            if (browse(path)) {
                log(ctx, "已用浏览器打开：" + path);
                toast(ctx, "已打开链接");
            } else {
                fail(ctx, "无法打开链接：" + path);
            }
            return;
        }
        File f = resolve(ctx, path);
        if (f == null || !f.exists()) {
            fail(ctx, "文件不存在：" + path);
            return;
        }
        if (openWithDesktop(f) || openWithShell(f)) {
            log(ctx, "已用系统默认程序打开：" + f.getAbsolutePath());
            toast(ctx, "已打开：" + f.getName());
        } else {
            fail(ctx, "系统里找不到可打开该文件的程序：" + f.getName());
        }
    }

    private void doReveal(PluginContext ctx, String raw) {
        String path = raw == null ? "" : raw.trim();
        if (path.isEmpty()) {
            // 没给路径 → 打开当前地图文件夹
            File dir = ctx == null ? null : ctx.mapDir();
            if (dir == null || !dir.isDirectory()) {
                fail(ctx, "没有可打开的目录（也未指定路径）");
                return;
            }
            path = dir.getAbsolutePath();
        }
        File f = resolve(ctx, path);
        if (f == null || !f.exists()) {
            fail(ctx, "路径不存在：" + path);
            return;
        }
        File dir = f.isDirectory() ? f : f.getParentFile();
        if (dir == null || !dir.isDirectory()) {
            fail(ctx, "找不到所在目录：" + f.getAbsolutePath());
            return;
        }
        if (revealInShell(f) || openWithDesktop(dir)) {
            log(ctx, "已在文件管理器中打开：" + dir.getAbsolutePath());
            toast(ctx, "已打开目录：" + dir.getName());
        } else {
            fail(ctx, "无法打开目录：" + dir.getAbsolutePath());
        }
    }

    // =====================================================================
    // 文件选择框（必须在 JavaFX 线程上弹）
    // =====================================================================

    /** 弹文件选择框；用户取消或环境不支持时返回空串 */
    private String chooseFile(PluginContext ctx, String filterSpec) {
        if (ctx == null || ctx.host() == null) {
            // 没有宿主就没法切回 JavaFX 线程，直接放弃而不是抛异常
            Logs.warn("[System:" + id + "] 当前没有可用宿主，无法弹出文件选择框");
            return "";
        }
        String picked = onFx(ctx, () -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("打开文件（@plugin(" + id + ")）");
            File init = ctx == null ? null : ctx.mapDir();
            if (init != null && init.isDirectory()) fc.setInitialDirectory(init);
            List<String> patterns = patternsOf(filterSpec);
            if (!patterns.isEmpty()) {
                fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(
                        "指定类型（" + String.join(", ", patterns) + "）", patterns));
            }
            fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("所有文件", "*.*"));
            File f = fc.showOpenDialog(ownerWindow());
            return f == null ? "" : f.getAbsolutePath();
        });
        return picked == null ? "" : picked;
    }

    /** 取一个可见窗口作为对话框宿主（拿不到时允许为 null，JavaFX 会用默认窗口） */
    private static javafx.stage.Window ownerWindow() {
        try {
            for (javafx.stage.Window w : javafx.stage.Window.getWindows()) {
                if (w.isShowing() && w instanceof javafx.stage.Stage) return w;
            }
        } catch (RuntimeException ignored) {
            // 工具包未初始化等异常：交给 JavaFX 处理 null owner
        }
        return null;
    }

    /** "png;jpg" / "*.png,*.jpg" → ["*.png", "*.jpg"] */
    private static List<String> patternsOf(String spec) {
        List<String> out = new ArrayList<>();
        if (spec == null || spec.isBlank()) return out;
        for (String part : spec.split("[;,，、]")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            out.add(p.startsWith("*") ? p : "*." + p);
        }
        return out;
    }

    // =====================================================================
    // 路径解析与系统调用
    // =====================================================================

    /** 相对路径优先按地图文件夹解析，其次按当前工作目录 */
    private static File resolve(PluginContext ctx, String raw) {
        if (raw == null || raw.isBlank()) return null;
        File f = new File(raw);
        if (f.isAbsolute()) return f;
        File mapDir = ctx == null ? null : ctx.mapDir();
        if (mapDir != null) {
            File rel = new File(mapDir, raw.replace('\\', '/'));
            if (rel.exists()) return rel;
        }
        return new File(System.getProperty("user.dir"), raw.replace('\\', '/'));
    }

    private static boolean isUrl(String s) {
        if (s == null) return false;
        String low = s.trim().toLowerCase(Locale.ROOT);
        return low.startsWith("http://") || low.startsWith("https://") || low.startsWith("file://");
    }

    private static boolean browse(String url) {
        try {
            if (!Desktop.isDesktopSupported()) return false;
            Desktop d = Desktop.getDesktop();
            if (!d.isSupported(Desktop.Action.BROWSE)) return false;
            d.browse(URI.create(url));
            return true;
        } catch (Exception e) {
            Logs.warn("[System] 浏览器打开失败：" + e.getMessage());
            return false;
        }
    }

    private static boolean openWithDesktop(File f) {
        try {
            if (!Desktop.isDesktopSupported()) return false;
            Desktop d = Desktop.getDesktop();
            if (!d.isSupported(Desktop.Action.OPEN)) return false;
            d.open(f);
            return true;
        } catch (Exception e) {
            Logs.warn("[System] Desktop.open 失败：" + e.getMessage());
            return false;
        }
    }

    /** 无 Desktop 支持时的兜底（Windows / macOS / Linux） */
    private static boolean openWithShell(File f) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                new ProcessBuilder("rundll32.exe", "url.dll,FileProtocolHandler", f.getAbsolutePath()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", f.getAbsolutePath()).start();
            } else {
                new ProcessBuilder("xdg-open", f.getAbsolutePath()).start();
            }
            return true;
        } catch (Exception e) {
            Logs.warn("[System] 兜底打开失败：" + e.getMessage());
            return false;
        }
    }

    /** 在文件管理器里打开目录（Windows 下尽量选中该文件） */
    private static boolean revealInShell(File f) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                if (f.isFile()) {
                    new ProcessBuilder("explorer.exe", "/select," + f.getAbsolutePath()).start();
                } else {
                    new ProcessBuilder("explorer.exe", f.getAbsolutePath()).start();
                }
                return true;
            }
            if (os.contains("mac")) {
                new ProcessBuilder("open", "-R", f.getAbsolutePath()).start();
                return true;
            }
            return false;
        } catch (Exception e) {
            Logs.warn("[System] 打开目录失败：" + e.getMessage());
            return false;
        }
    }

    // =====================================================================
    // 小工具
    // =====================================================================

    private static String arg(String[] args, int i) {
        return args != null && args.length > i && args[i] != null ? args[i].trim() : "";
    }

    private static void log(PluginContext ctx, String msg) {
        if (ctx != null) ctx.log(msg); else Logs.info("[System] " + msg);
    }

    private static void toast(PluginContext ctx, String msg) {
        if (ctx != null) ctx.toast(msg);
    }

    private static void fail(PluginContext ctx, String msg) {
        Logs.warn("[System] " + msg);
        if (ctx != null) ctx.toast(msg);
    }

    /** 保证在 JavaFX 线程上取值（后台线程调用时切回去等结果，最多等 30 秒） */
    private static <T> T onFx(PluginContext ctx, Supplier<T> action) {
        if (ctx == null || ctx.isUiThread()) return action.get();
        final Object[] box = new Object[1];
        CountDownLatch latch = new CountDownLatch(1);
        ctx.onUi(() -> {
            try {
                box[0] = action.get();
            } catch (RuntimeException e) {
                Logs.warn("[System] 文件选择框出错：" + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) Logs.warn("[System] 等待文件选择框超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        @SuppressWarnings("unchecked")
        T t = (T) box[0];
        return t;
    }

    /** 编辑器插件目录（选择器 / 手册自动生成用） */
    public static java.util.List<PluginInfo> catalog() {
        java.util.List<PluginInfo> out = new java.util.ArrayList<>();
        out.add(new PluginInfo("quit", "系统", "exit,退出,退出游戏,离开游戏", "@plugin(quit)",
                "退出游戏：交宿主收尾（释放媒体/插件）后关闭播放器窗口；宿主不接管时不会擅自结束进程"));
        out.add(new PluginInfo("open", "系统", "openfile,打开,打开文件", "@plugin(open) | 文档/攻略.txt",
                "用系统默认程序打开文件（相对地图文件夹；也支持 http(s) 链接）；不给路径则先弹文件选择框"));
        out.add(new PluginInfo("pick", "系统", "pickfile,选择文件,选文件", "@plugin(pick) | png;jpg | @var(选中文件)",
                "弹文件选择框，把选中的绝对路径写回输出位（玩家取消时保持原值）"));
        out.add(new PluginInfo("reveal", "系统", "opendir,打开目录,打开文件夹,所在目录", "@plugin(reveal) | saves/slot1.txt",
                "在文件管理器里打开该文件所在目录并选中它"));
        return out;
    }

    @Override
    public String toString() { return "SystemPlugin(" + id + ")"; }
}
