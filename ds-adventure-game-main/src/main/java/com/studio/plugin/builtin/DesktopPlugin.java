package com.studio.plugin.builtin;

import com.studio.flow.FlowHost;
import com.studio.flow.PluginContext;
import com.studio.util.Logs;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 编辑器<b>自带</b>插件：<b>系统扩展</b>——剪贴板、系统通知、截图、全屏。
 *
 * <p>配合已有的系统操作插件（{@code quit/open/pick/reveal}），这一组把“游戏之外”的事补齐：</p>
 *
 * <h3>可用插件 ID</h3>
 * <pre>
 *   slot = 复制码   | @plugin(clipboard) | copy  | 兑奖码：DSW-09
 *   slot = 粘贴     | @plugin(clipboard) | paste | @var(剪贴板内容)
 *   slot = 提醒     | @plugin(notify)    | 万秤楼 | 你的泉眼已经被记账了
 *   slot = 名场面   | @plugin(screenshot)| 名场面.png
 *   slot = 全屏     | @plugin(fullscreen)| toggle
 * </pre>
 *
 * <p>全部操作都靠宿主 / JavaFX 标准能力完成：剪贴板需要 JavaFX 工具包（在播放器里一定有），
 * 系统通知在系统托盘不可用时自动退化成顶部提示，截图与全屏需要宿主实现
 * （{@link FlowHost#snapshot(File)} / {@link FlowHost#toggleFullscreen(Boolean)}），
 * 宿主不支持时只记日志 + 提示，不影响剧情。</p>
 */
public class DesktopPlugin extends BuiltinPlugin {

    private enum Act { CLIPBOARD, NOTIFY, SCREENSHOT, FULLSCREEN }

    private static final Map<String, Act> IDS = new LinkedHashMap<>();
    static {
        IDS.put("clipboard", Act.CLIPBOARD); IDS.put("剪贴板", Act.CLIPBOARD); IDS.put("复制", Act.CLIPBOARD);
        IDS.put("notify", Act.NOTIFY);       IDS.put("通知", Act.NOTIFY);       IDS.put("提醒", Act.NOTIFY);
        IDS.put("screenshot", Act.SCREENSHOT); IDS.put("截图", Act.SCREENSHOT);
        IDS.put("fullscreen", Act.FULLSCREEN); IDS.put("全屏", Act.FULLSCREEN);
    }

    private final Act act;

    /** 系统通知用（懒创建；托盘不可用时为 null） */
    private static TrayIcon tray;

    public DesktopPlugin() { this(Act.CLIPBOARD, "clipboard"); }

    public DesktopPlugin(Act act, String id) {
        super(id);
        this.act = act == null ? Act.CLIPBOARD : act;
    }

    public static List<String> ids() { return new ArrayList<>(IDS.keySet()); }

    public static DesktopPlugin of(String id) {
        if (id == null) return null;
        String k = id.trim();
        Act a = IDS.get(k.toLowerCase(Locale.ROOT));
        if (a == null) a = IDS.get(k);
        return a == null ? null : new DesktopPlugin(a, k);
    }

    /** 编辑器插件目录 */
    public static List<PluginInfo> catalog() {
        List<PluginInfo> out = new ArrayList<>();
        out.add(new PluginInfo("clipboard", "系统", "剪贴板,复制",
                "@plugin(clipboard) | copy | 兑奖码：DSW-09",
                "剪贴板：copy 写进去 / paste 读出来写进变量（分享链接、兑奖码、让玩家去搜索都很方便）"));
        out.add(new PluginInfo("notify", "系统", "通知,提醒",
                "@plugin(notify) | 万秤楼 | 你的泉眼已经被记账了",
                "系统通知（托盘气泡）；不可用时自动退化为顶部提示"));
        out.add(new PluginInfo("screenshot", "系统", "截图",
                "@plugin(screenshot) | 名场面.png",
                "把当前画面截图保存到地图文件夹（名场面收藏、交作业截图）"));
        out.add(new PluginInfo("fullscreen", "系统", "全屏",
                "@plugin(fullscreen) | toggle",
                "切换全屏：toggle / on / off（需要宿主实现）"));
        return out;
    }

    @Override
    protected String group() { return "系统"; }

    @Override
    public String description() {
        switch (act) {
            case CLIPBOARD:  return "剪贴板：copy 写文本 / paste 读到变量";
            case NOTIFY:     return "系统通知（托盘气泡，不可用时退化为顶部提示）";
            case SCREENSHOT: return "截图保存到地图文件夹（需宿主实现）";
            case FULLSCREEN: return "切换全屏（需宿主实现）";
            default:         return "";
        }
    }

    @Override
    public String usage() {
        switch (act) {
            case CLIPBOARD:  return "@plugin(clipboard) | copy|paste | 文本或@var(输出)";
            case NOTIFY:     return "@plugin(notify) | 标题 | 内容";
            case SCREENSHOT: return "@plugin(screenshot) | 文件名.png（可空）";
            case FULLSCREEN: return "@plugin(fullscreen) | toggle|on|off";
            default:         return "";
        }
    }

    // =====================================================================

    @Override
    protected void run(PluginContext ctx, String[] in, String[] out) {
        switch (act) {
            case CLIPBOARD -> clipboard(ctx, in, out);
            case NOTIFY -> notify(ctx, in);
            case SCREENSHOT -> screenshot(ctx, in);
            case FULLSCREEN -> fullscreen(ctx, in);
            default -> { /* 不会发生 */ }
        }
    }

    /** clipboard | copy 文本 / paste 输出位 */
    private void clipboard(PluginContext ctx, String[] in, String[] out) {
        if (count(in) < 2) {
            warn(ctx, "参数不足（用法：" + usage() + "）");
            return;
        }
        String action = arg(in, 0).toLowerCase(Locale.ROOT);
        if (action.startsWith("copy") || action.equals("复制") || action.equals("写")) {
            String text = raw(in, 1);
            Boolean ok = PluginFx.onFx(ctx, () -> {
                ClipboardContent content = new ClipboardContent();
                content.putString(text);
                return Clipboard.getSystemClipboard().setContent(content);
            });
            log(ctx, "已复制到剪贴板：" + text);
            toast(ctx, Boolean.TRUE.equals(ok) ? "已复制到剪贴板" : "复制失败（剪贴板不可用）");
        } else {
            String text = PluginFx.onFx(ctx, () -> {
                Clipboard cb = Clipboard.getSystemClipboard();
                return cb.hasString() ? cb.getString() : "";
            });
            setOut(out, text == null ? "" : text);
            log(ctx, "从剪贴板读到 " + (text == null ? 0 : text.length()) + " 个字符");
        }
    }

    /** notify | 标题 | 内容 */
    private void notify(PluginContext ctx, String[] in) {
        String title = arg(in, 0);
        String content = count(in) >= 2 ? arg(in, 1) : title;
        if (title.isEmpty()) title = "提示";
        boolean ok = false;
        try {
            if (SystemTray.isSupported()) {
                if (tray == null) {
                    BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                    tray = new TrayIcon(img, "剧情通知");
                    tray.setImageAutoSize(true);
                    SystemTray.getSystemTray().add(tray);
                }
                tray.displayMessage(title, content, TrayIcon.MessageType.INFO);
                ok = true;
            }
        } catch (Exception e) {
            Logs.warn("[Plugin:notify] 系统通知失败：" + e.getMessage());
        }
        if (!ok) {
            toast(ctx, title + "：" + content);
            log(ctx, "系统托盘不可用，已退化为顶部提示：" + title + " / " + content);
        } else {
            log(ctx, "已发送系统通知：" + title + " / " + content);
        }
    }

    /** screenshot | 文件名 */
    private void screenshot(PluginContext ctx, String[] in) {
        String name = arg(in, 0);
        if (name.isEmpty()) name = "截图.png";
        File dir = ctx == null ? null : ctx.mapDir();
        File file = new File(dir == null ? new File(".") : dir, name);
        FlowHost host = ctx == null ? null : ctx.host();
        boolean ok = false;
        if (host != null) {
            try {
                ok = host.snapshot(file);
            } catch (RuntimeException e) {
                Logs.warn("[Plugin:screenshot] 截图失败：" + e.getMessage());
            }
        }
        if (ok) {
            log(ctx, "已截图：" + file.getAbsolutePath());
            toast(ctx, "已保存截图：" + file.getName());
        } else {
            warn(ctx, "当前宿主不支持截图（编辑器预览/无界面环境）");
        }
    }

    /** fullscreen | toggle|on|off */
    private void fullscreen(PluginContext ctx, String[] in) {
        String mode = arg(in, 0).isEmpty() ? "toggle" : arg(in, 0).toLowerCase(Locale.ROOT);
        FlowHost host = ctx == null ? null : ctx.host();
        boolean ok = false;
        if (host != null) {
            try {
                ok = host.toggleFullscreen("off".equals(mode) || mode.equals("关") ? Boolean.FALSE
                        : ("on".equals(mode) || mode.equals("开") ? Boolean.TRUE : null));
            } catch (RuntimeException e) {
                Logs.warn("[Plugin:fullscreen] 切换全屏失败：" + e.getMessage());
            }
        }
        if (ok) log(ctx, "全屏状态已切换（" + mode + "）");
        else warn(ctx, "当前宿主不支持全屏切换");
    }
}
