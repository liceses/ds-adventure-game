package com.studio.editor;

import com.studio.plugin.builtin.BuiltinCatalog;
import com.studio.plugin.builtin.PluginInfo;

import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 「插入插件槽」用的<b>可搜索插件下拉框</b>。
 *
 * <p>为什么需要它：插件总数已经七十多个（自带就 70+，再算上外部插件），
 * 纯列表找 {@code fullscreen} 这种排在后面的 ID 要靠不停滚动，很容易以为“没有这个插件”。
 * 所以这里做成输入即筛选：打 {@code full}、{@code 全屏}、{@code 截}、甚至打说明里的字都能筛出来。</p>
 *
 * <p>筛选范围：ID、中文别名、分组、说明文字（都不区分大小写）。
 * 选中后编辑器按 {@link PluginInfo#slotTemplate()} 生成一行槽模板。</p>
 */
public final class PluginPickerField {

    private PluginPickerField() { }

    /** 建一个已装好全部可用插件、可输入筛选的下拉框（列表从 hub 取） */
    public static ComboBox<PluginInfo> create(EditorHub hub, double width) {
        List<PluginInfo> all = hub == null ? new ArrayList<>() : new ArrayList<>(hub.pluginCatalog());
        return create(all, width, hub == null ? "" : tooltipText(hub, all));
    }

    /**
     * 建一个可筛选插件下拉框。
     *
     * @param all     全部候选插件（自带 + 外部）
     * @param width   宽度
     * @param tooltip 提示气泡文字（可为空）
     */
    public static ComboBox<PluginInfo> create(List<PluginInfo> all, double width, String tooltip) {
        List<PluginInfo> items = all == null ? new ArrayList<>() : new ArrayList<>(all);
        ComboBox<PluginInfo> box = new ComboBox<>(FXCollections.observableArrayList(items));
        box.setPrefWidth(width);
        box.setConverter(new StringConverter<PluginInfo>() {
            @Override public String toString(PluginInfo info) { return label(info); }
            @Override public PluginInfo fromString(String s) { return null; }
        });
        box.setEditable(true);
        // 建好之后立刻清空选择：免得“第一项被自动选中”的文本被当成搜索词（会把列表筛成一项）
        box.getSelectionModel().clearSelection();
        box.setValue(null);
        box.getEditor().setText("");
        if (tooltip != null && !tooltip.isBlank()) box.setTooltip(new Tooltip(tooltip));
        box.setPromptText("输入关键字筛选（ID / 别名 / 说明），或点开浏览");

        // 输入即筛选：改 items 会重设编辑框文本，所以写完要写回（guard 防自己触发自己）
        //
        // 注意：筛选要“延后一拍”再改 items。点弹层里的某一项时，JavaFX 先设置 value、再把该项的标签回填进编辑框，
        // 回填会触发这个监听器；如果在事件派发过程中直接 setItems，弹层自己的 ListView 被整个换掉，
        // 刚点中的选择会丢 —— 表现出来就是“点了列表项没反应、value 还是 null、按钮按下去什么都不插”。
        // 所以先记下关键字，runLater 里再决定：编辑框文本正是选中项的标签 → 这是“选中回填”，不筛选；
        // 文本已经又变了 → 交给最新那次；其余才按关键字筛。
        final boolean[] guard = {false};
        box.getEditor().textProperty().addListener((o, old, text) -> {
            if (guard[0]) return;
            String kw = text == null ? "" : text;
            javafx.application.Platform.runLater(() -> {
                if (guard[0]) return;
                PluginInfo sel = box.getValue();
                String now = box.getEditor().getText() == null ? "" : box.getEditor().getText();
                if (sel != null && (label(sel).equals(now) || label(sel).equals(kw))) return;
                if (!now.equals(kw)) return;
                List<PluginInfo> filtered = filter(items, kw);
                guard[0] = true;
                try {
                    box.setItems(FXCollections.observableArrayList(filtered));
                    box.getEditor().setText(kw);
                    box.getSelectionModel().clearSelection();
                    box.setValue(null);
                    if (!filtered.isEmpty() && box.isFocused()) box.show();
                } finally {
                    guard[0] = false;
                }
            });
        });
        return box;
    }

    /** 提示气泡文字（说明列表来源与筛选方式） */
    public static String tooltipText(EditorHub hub, List<PluginInfo> all) {
        int builtin = BuiltinCatalog.all().size();
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(all == null ? 0 : all.size()).append(" 个可用插件（自带 ").append(builtin)
                .append(" + 外部 ").append(Math.max(0, (all == null ? 0 : all.size()) - builtin)).append("）\n");
        sb.append("外部插件来自 plugins/varplugins.ini、plugins/plugins.ini，以及 plugins/classes、plugins/*.jar\n");
        sb.append("在输入框里打关键字即可筛选（例如 full、全屏、截图、音频）\n");
        sb.append("选中后点「＋ 插入插件槽」，直接追加一条槽（模板同时留在输入框里，方便改成自己的变量名）");
        if (hub != null) {
            int problems = hub.pluginCatalogDetailed().problems().size();
            if (problems > 0) {
                sb.append("\n（有 ").append(problems).append(" 个注册项不能用于槽：事件插件请用节点的 event 属性）");
            }
        }
        return sb.toString();
    }

    /** 下拉里显示的文字（分组 + ID + 别名） */
    public static String label(PluginInfo info) {
        if (info == null) return "";
        String alias = info.aliases() == null || info.aliases().isBlank() ? "" : "（" + info.aliases() + "）";
        return info.group() + " · " + info.id() + alias;
    }

    /** 按关键字筛选（空关键字返回全部） */
    public static List<PluginInfo> filter(List<PluginInfo> all, String keyword) {
        if (all == null) return new ArrayList<>();
        String k = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (k.isEmpty()) return new ArrayList<>(all);
        List<PluginInfo> out = new ArrayList<>();
        for (PluginInfo i : all) {
            if (hit(i, k)) out.add(i);
        }
        return out;
    }

    private static boolean hit(PluginInfo i, String k) {
        return contains(i.id(), k) || contains(i.aliases(), k) || contains(i.group(), k)
                || contains(i.usage(), k) || contains(i.description(), k);
    }

    private static boolean contains(String s, String k) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(k);
    }

}
