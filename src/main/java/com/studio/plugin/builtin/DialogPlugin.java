package com.studio.plugin.builtin;

import com.studio.flow.PluginContext;
import com.studio.ui.Ui;

import javafx.scene.control.TextInputDialog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 编辑器<b>自带</b>插件：<b>交互</b>——弹确认框、弹输入框，把玩家的回答写成存档变量。
 *
 * <p>按钮和选项只能表达“预设好的几种选择”；有时候剧情需要当场问玩家一句话
 * （起名字、输密码、确认退出），这个插件补上这一块。</p>
 *
 * <h3>可用插件 ID</h3>
 * <pre>
 *   # 确认框：把「确定 / 取消」写进 bool 变量
 *   slot = 退出确认 | @plugin(confirm) | 退出游戏 | 未存档的进度会丢失，确定要退出吗？ | @var(确定)
 *
 *   # 输入框：把玩家输入的文字写进变量（可给默认值）
 *   slot = 起名     | @plugin(input)   | 请输入你的名字 | 无名氏 | @var(玩家名)
 * </pre>
 *
 * <p><b>参数约定</b>：</p>
 * <ul>
 *   <li>{@code confirm | 标题 | 提示内容 | 输出位}，也可简写 {@code confirm | 提示内容 | 输出位}；</li>
 *   <li>{@code input | 提示文字 | 默认值 | 输出位}，也可简写 {@code input | 提示文字 | 输出位}。</li>
 * </ul>
 *
 * <p><b>注意</b>：对话框是<b>模态</b>的，弹出时会暂停一会儿剧情推进（点掉就继续），
 * 所以要放在按钮/信号的槽里，不要放在“每帧都在跑”的地方。对话框一定在 JavaFX 线程上弹出
 * （后台线程调用会自动切回）；玩家关掉窗口按“取消”处理（赋值 false / 空串）。</p>
 */
public class DialogPlugin extends BuiltinPlugin {

    private enum Act { CONFIRM, INPUT }

    private static final Map<String, Act> IDS = new LinkedHashMap<>();
    static {
        IDS.put("confirm", Act.CONFIRM);
        IDS.put("确认", Act.CONFIRM);
        IDS.put("ask", Act.CONFIRM);
        IDS.put("input", Act.INPUT);
        IDS.put("输入", Act.INPUT);
        IDS.put("asktext", Act.INPUT);
    }

    private final Act act;

    public DialogPlugin() { this(Act.CONFIRM, "confirm"); }

    public DialogPlugin(Act act, String id) {
        super(id);
        this.act = act == null ? Act.CONFIRM : act;
    }

    public static List<String> ids() { return new ArrayList<>(IDS.keySet()); }

    public static DialogPlugin of(String id) {
        if (id == null) return null;
        String k = id.trim();
        Act a = IDS.get(k.toLowerCase(Locale.ROOT));
        if (a == null) a = IDS.get(k);
        return a == null ? null : new DialogPlugin(a, k);
    }

    /** 编辑器插件目录 */
    public static List<PluginInfo> catalog() {
        List<PluginInfo> out = new ArrayList<>();
        out.add(new PluginInfo("confirm", "交互", "确认,ask",
                "@plugin(confirm) | 退出游戏 | 未存档的进度会丢失，确定吗？ | @var(确定)",
                "弹确认框：把「确定/取消」写进 bool 变量（取消或关窗按 false 处理）"));
        out.add(new PluginInfo("input", "交互", "输入,asktext",
                "@plugin(input) | 请输入你的名字 | 无名氏 | @var(玩家名)",
                "弹输入框：把玩家输入的文字写进变量（可给默认值）"));
        return out;
    }

    @Override
    protected String group() { return "交互"; }

    @Override
    public String description() {
        return act == Act.CONFIRM ? "弹确认框，把玩家的选择写进 bool 变量" : "弹输入框，把玩家输入写进变量";
    }

    @Override
    public String usage() {
        return act == Act.CONFIRM
                ? "@plugin(confirm) | 标题 | 提示内容 | @var(输出bool)"
                : "@plugin(input) | 提示文字 | 默认值 | @var(输出)";
    }

    // =====================================================================

    @Override
    protected void run(PluginContext ctx, String[] in, String[] out) {
        if (count(in) < 2) {
            warn(ctx, "参数不足（用法：" + usage() + "）");
            return;
        }
        if (act == Act.CONFIRM) {
            // 两个参数：提示内容 | 输出位；三个及以上：标题 | 内容 | 输出位
            String title = count(in) >= 3 ? arg(in, 0) : "请确认";
            String content = count(in) >= 3 ? arg(in, 1) : arg(in, 0);
            Boolean ok = PluginFx.onFx(ctx, () -> Ui.confirm(PluginFx.owner(), title, title, content));
            setOut(out, bool(Boolean.TRUE.equals(ok)));
            log(ctx, "确认框「" + title + "」→ " + (Boolean.TRUE.equals(ok) ? "确定" : "取消"));
        } else {
            // 两个参数：提示文字 | 输出位；三个及以上：提示文字 | 默认值 | 输出位
            String prompt = arg(in, 0);
            String def = count(in) >= 3 ? raw(in, 1) : "";
            String value = PluginFx.onFx(ctx, () -> {
                TextInputDialog dlg = new TextInputDialog(def);
                dlg.initOwner(PluginFx.owner());
                dlg.setTitle("请输入");
                dlg.setHeaderText(null);
                dlg.setContentText(prompt.isEmpty() ? "请输入：" : prompt);
                Optional<String> r = dlg.showAndWait();
                return r.orElse(null);
            });
            String result = value == null ? "" : value;
            setOut(out, result);
            log(ctx, "输入框「" + prompt + "」→ " + (result.isEmpty() ? "（取消/空）" : result));
        }
    }
}
