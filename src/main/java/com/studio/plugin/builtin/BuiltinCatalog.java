package com.studio.plugin.builtin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自带插件总目录：<b>编辑器里所有“插件相关界面”都从这里生成</b>
 * （插件选择器、一键插入模板、帮助窗口的插件手册、提示文字）。
 *
 * <p>约定：每个插件族在自己的类里提供静态 {@code catalog()} 返回若干 {@link PluginInfo}，
 * 本类把它们汇总起来按分组排好序。加插件时只改插件类，界面与文档自动跟上，
 * 不会再出现“帮助里漏写某个插件”或“文档与代码不一致”。</p>
 */
public final class BuiltinCatalog {

    private BuiltinCatalog() { }

    /** 分组显示顺序（不在表里的分组排在最后，按字典序） */
    private static final List<String> GROUP_ORDER = List.of(
            "运算", "逻辑", "文本", "格式", "流程", "随机", "交互",
            "演出", "音频", "视频", "存档", "数据", "列表", "系统", "网络", "时间", "调试");

    /** 全部自带插件（含既有插件族），按分组排序后返回 */
    public static List<PluginInfo> all() {
        List<PluginInfo> out = new ArrayList<>();
        out.addAll(MathPlugin.catalog());
        out.addAll(LogicPlugin.catalog());
        out.addAll(TextPlugin.catalog());
        out.addAll(RandomPlugin.catalog());
        out.addAll(ControlPlugin.catalog());
        out.addAll(DialogPlugin.catalog());
        out.addAll(StagePlugin.catalog());
        out.addAll(AudioPlugin.catalog());
        out.addAll(VideoPlugin.catalog());
        out.addAll(SavePlugin.catalog());
        out.addAll(ListPlugin.catalog());
        out.addAll(JsonPlugin.catalog());
        out.addAll(SystemPlugin.catalog());
        out.addAll(NetPlugin.catalog());
        out.addAll(TimePlugin.catalog());
        out.addAll(DebugPlugin.catalog());
        out.sort((a, b) -> {
            int ia = GROUP_ORDER.indexOf(a.group());
            int ib = GROUP_ORDER.indexOf(b.group());
            if (ia < 0) ia = GROUP_ORDER.size();
            if (ib < 0) ib = GROUP_ORDER.size();
            if (ia != ib) return Integer.compare(ia, ib);
            int c = a.group().compareTo(b.group());
            return c != 0 ? c : a.id().compareTo(b.id());
        });
        return out;
    }

    /** 按分组归类（编辑器手册按组显示；保持 {@link #all()} 的顺序） */
    public static Map<String, List<PluginInfo>> byGroup() {
        Map<String, List<PluginInfo>> map = new LinkedHashMap<>();
        for (PluginInfo info : all()) {
            map.computeIfAbsent(info.group(), k -> new ArrayList<>()).add(info);
        }
        return map;
    }

    /** 全部主 ID */
    public static List<String> ids() {
        List<String> out = new ArrayList<>();
        for (PluginInfo info : all()) out.add(info.id());
        return Collections.unmodifiableList(out);
    }

    /** 按 ID 找目录项（忽略大小写；中文别名也能找） */
    public static PluginInfo find(String id) {
        if (id == null) return null;
        String want = id.trim();
        for (PluginInfo info : all()) {
            if (info.id().equalsIgnoreCase(want)) return info;
        }
        for (PluginInfo info : all()) {
            if (info.aliases() == null) continue;
            for (String a : info.aliases().split("[,，]")) {
                if (a.trim().equalsIgnoreCase(want) || a.trim().equals(want)) return info;
            }
        }
        return null;
    }

    /** 插件手册文本（帮助窗口用；从目录自动生成，永不过期） */
    public static String manual() {
        StringBuilder sb = new StringBuilder();
        sb.append("编辑器自带插件（随编辑器/读取器发行，脚本里直接写 ID，无需注册、无需外部文件）\n");
        sb.append("参数约定：**最后一个参数是输出位置，前面的都是输入**；\n");
        sb.append("回写规则：输出位一定写回；其它位置只有当插件确实改过它的值时才写回。\n");
        sb.append("任何插件都不会因为写错参数而中断剧情（只记日志 + 顶部提示）。\n");
        for (Map.Entry<String, List<PluginInfo>> e : byGroup().entrySet()) {
            sb.append("\n============ ").append(e.getKey()).append(" ============\n");
            for (PluginInfo p : e.getValue()) {
                sb.append(p.id());
                if (p.aliases() != null && !p.aliases().isBlank()) sb.append("（别名 ").append(p.aliases()).append("）");
                sb.append("\n    ").append(p.description());
                sb.append("\n    ").append(p.usage()).append('\n');
            }
        }
        return sb.toString();
    }
}
