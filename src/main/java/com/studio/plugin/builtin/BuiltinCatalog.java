package com.studio.plugin.builtin;

import com.studio.flow.PluginRuntime;

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
        List<PluginInfo> out = new ArrayList<>(familyEntries());
        // 兜底：运行时注册了、但上面这串手写清单漏掉的 ID 也补进目录 ——
        // 这样即使将来新增了插件族忘了登记，编辑器的下拉框与手册里依然能看到它（只是说明比较简略）
        for (String id : uncoveredRuntimeIds()) out.add(fallbackEntry(id));
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

    /**
     * 自检：目录与运行时登记表必须<b>双向都对得上</b>。
     *
     * <p>加这个是因为真的漏过一次 —— {@code DesktopPlugin}（剪贴板/通知/截图/<b>全屏</b>）
     * 忘了加进 {@link #all()} 的手写清单，结果「插入插件槽」的下拉框和帮助手册里都找不到
     * {@code @plugin(fullscreen)}。现在改成：</p>
     * <ul>
     *   <li>{@link #all()} 会自动把运行时注册、手写清单漏掉的 ID 补成兜底项（界面上不再“找不到”）；</li>
     *   <li>本方法把这些“漏登记的”报出来，由探针断言为空 —— 漏了就会当场失败，逼着补登记。</li>
     * </ul>
     *
     * @return 问题列表（空表 = 目录与运行时完全一致）
     */
    public static List<String> selfCheck() {
        List<String> problems = new ArrayList<>();
        for (String id : uncoveredRuntimeIds()) {
            problems.add(id + "（运行时已注册，但 BuiltinCatalog 的手写清单里漏了登记 —— 界面已用兜底项显示）");
        }
        for (PluginInfo i : all()) {
            if (!PluginRuntime.builtinIds().contains(i.id())) {
                problems.add(i.id() + "（目录里有，但运行时认不出来，检查注册表）");
            }
        }
        // 主 ID 撞名：运行时是“后注册者生效”，撞名的那个在界面上看着能用、实际被顶掉。
        // 曾经真的发生过（数学 sub 被文本 sub 顶掉），所以这里也一并报出来。
        java.util.Map<String, Integer> seen = new java.util.HashMap<>();
        for (PluginInfo i : familyEntries()) {
            String key = i.id().toLowerCase(java.util.Locale.ROOT);
            Integer n = seen.get(key);
            seen.put(key, n == null ? 1 : n + 1);
            if (n != null) {
                problems.add(i.id() + "（有两个插件用了同一个主 ID，运行时只有后注册的那个生效 —— 请给其中一个换 ID）");
            }
        }
        return problems;
    }

    /**
     * 运行时自带 ID 里，没有被目录（含别名）覆盖的那些。
     * <p>正常应当为空；不为空说明有插件族忘了在 {@link #all()} 里登记。</p>
     */
    public static List<String> uncoveredRuntimeIds() {
        java.util.Set<String> covered = new java.util.HashSet<>();
        for (PluginInfo i : familyEntries()) {
            covered.add(i.id().toLowerCase(java.util.Locale.ROOT));
            if (i.aliases() != null) {
                for (String a : i.aliases().split("[,，]")) {
                    if (!a.isBlank()) covered.add(a.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        List<String> out = new ArrayList<>();
        for (String id : PluginRuntime.builtinIds()) {
            if (!covered.contains(id.toLowerCase(java.util.Locale.ROOT))) out.add(id);
        }
        return out;
    }

    /** 只由各插件族的 catalog() 组成的清单（不含兜底项） */
    private static List<PluginInfo> familyEntries() {
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
        out.addAll(DesktopPlugin.catalog());
        out.addAll(NetPlugin.catalog());
        out.addAll(TimePlugin.catalog());
        out.addAll(DebugPlugin.catalog());
        return out;
    }

    /** 兜底目录项：说明它是“漏登记”的，提示去补 */
    private static PluginInfo fallbackEntry(String id) {
        return new PluginInfo(id, "未分类", "",
                "@plugin(" + id + ")",
                "内置插件（运行时可用）。这一条是自动补的：BuiltinCatalog 里还没给它写说明，"
                        + "但它出现在这里说明它能用，可以直接写 @plugin(" + id + ")");
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
