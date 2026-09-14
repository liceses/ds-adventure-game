package com.studio.plugin.builtin;

import com.studio.flow.PluginContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 编辑器<b>自带</b>插件：<b>列表</b>——背包、图鉴、已解锁清单、日志这类“一串东西”。
 *
 * <p>存档变量只有 int/double/bool/str 四种，没有数组。这个插件用一个<b>不可见分隔符</b>
 * 把多项内容存在一个 <b>str 变量</b>里，并提供增删查改，于是「背包 / 图鉴 / 已读台词 / 事件日志」
 * 都能做，而且跟着存档一起保存。</p>
 *
 * <h3>关键约定：列表本身也是“参数”</h3>
 * <p>引擎会把 {@code @var(背包)} 解析成它的<b>当前值</b>再传给插件，所以写法上要把同一个变量
 * <b>写两次</b>：前面的那次是“读进来”，最后的输出位是“写回去”（输出位永远是最后一个参数）。</p>
 *
 * <pre>
 *   # 追加：读背包 → 加一项 → 写回背包
 *   slot = 捡到 | @plugin(list) | push | @var(背包) | 苹果 | @var(背包)
 *
 *   # 删除 / 去重 / 清空：同样把结果写回自己
 *   slot = 用掉 | @plugin(list) | remove | @var(背包) | 苹果 | @var(背包)
 *   slot = 去重 | @plugin(list) | unique | @var(背包) | @var(背包)
 *   slot = 清空 | @plugin(list) | clear  | @var(背包)
 *
 *   # 只读的查询：结果写到别的变量
 *   slot = 几样 | @plugin(list) | count | @var(背包) | @var(数量)
 *   slot = 有吗 | @plugin(list) | has   | @var(背包) | 苹果 | @var(有)
 *   slot = 第1样| @plugin(list) | get   | @var(背包) | 0 | @var(第一样)
 *   slot = 全部 | @plugin(list) | join  | @var(背包) | 、 | @var(一行文本)
 *   slot = 弹出 | @plugin(list) | pop   | @var(背包) | @var(背包)
 * </pre>
 *
 * <p>下标从 0 开始，也可以写负数（{@code -1} = 最后一个）。
 * {@code pop} 会把弹出的那一项写进日志/提示，列表本身的新值写回输出位。
 * 内容里不要包含竖线 {@code |} 与不可见分隔符，中文与 emoji 都可以。</p>
 */
public class ListPlugin extends BuiltinPlugin {

    private static final Map<String, String> IDS = new LinkedHashMap<>();
    static {
        IDS.put("list", "list");
        IDS.put("列表", "list");
        IDS.put("array", "list");
    }

    /** 分隔符：不可见字符，正常文本里不会出现 */
    static final String SEP = "\u001f";

    public ListPlugin() { this("list"); }

    public ListPlugin(String id) { super(id); }

    public static List<String> ids() { return new ArrayList<>(IDS.keySet()); }

    public static ListPlugin of(String id) {
        if (id == null) return null;
        String k = id.trim();
        return IDS.containsKey(k.toLowerCase(Locale.ROOT)) || IDS.containsKey(k) ? new ListPlugin(k) : null;
    }

    /** 编辑器插件目录 */
    public static List<PluginInfo> catalog() {
        List<PluginInfo> out = new ArrayList<>();
        out.add(new PluginInfo("list", "列表", "列表,array",
                "@plugin(list) | push | @var(背包) | 苹果 | @var(背包)",
                "把一个 str 变量当数组：push 追加 / pop 弹出 / remove 删除 / unique 去重 / clear 清空（结果写回输出位）；"
                        + "count 计数 / has 判断 / get 取第 N 项 / join 连接成文本"));
        return out;
    }

    @Override
    protected String group() { return "列表"; }

    @Override
    public String description() {
        return "列表：把一个 str 变量当数组用（背包、图鉴、日志）。改内容的动作把新列表写回输出位，"
                + "所以通常写两次同一个变量：@plugin(list) | push | @var(背包) | 苹果 | @var(背包)";
    }

    @Override
    public String usage() {
        return "@plugin(list) | push|pop|remove|unique|clear | @var(列表) | 参数… | @var(列表)    或    "
                + "@plugin(list) | count|has|get|join | @var(列表) | 参数… | @var(输出)";
    }

    // =====================================================================

    @Override
    protected void run(PluginContext ctx, String[] in, String[] out) {
        if (count(in) < 2) {
            warn(ctx, "参数不足（用法：" + usage() + "）");
            return;
        }
        String action = arg(in, 0).toLowerCase(Locale.ROOT);
        List<String> items = parse(raw(in, 1));
        switch (action) {
            case "push", "append", "追加", "添加" -> {
                String value = raw(in, 2);
                items.add(value);
                setOut(out, join(items));
                log(ctx, "列表追加「" + value + "」，现有 " + items.size() + " 项");
            }
            case "pop", "弹出" -> {
                String last = items.isEmpty() ? "" : items.remove(items.size() - 1);
                setOut(out, join(items));
                log(ctx, "列表弹出「" + last + "」（剩余 " + items.size() + " 项）");
                toast(ctx, last.isEmpty() ? "列表是空的" : ("弹出：" + last));
            }
            case "remove", "delete", "删除" -> {
                String value = raw(in, 2);
                boolean removed = items.remove(value);
                setOut(out, join(items));
                log(ctx, removed ? ("列表删除了「" + value + "」，剩余 " + items.size() + " 项")
                        : ("列表里没有「" + value + "」"));
            }
            case "has", "contains", "包含" -> {
                String value = arg(in, 2);
                boolean has = items.contains(value);
                setOut(out, bool(has));
                log(ctx, "列表" + (has ? "含" : "不含") + "「" + value + "」");
            }
            case "count", "len", "size", "长度" -> {
                setOut(out, String.valueOf(items.size()));
                log(ctx, "列表共 " + items.size() + " 项");
            }
            case "get", "取" -> {
                int index = i(arg(in, 2), 0);
                if (index < 0) index = items.size() + index;
                String value = index >= 0 && index < items.size() ? items.get(index) : "";
                setOut(out, value);
                log(ctx, "列表[" + arg(in, 2) + "] → " + value);
            }
            case "join", "连接", "合并" -> {
                String sep = count(in) >= 4 ? raw(in, 2) : "、";
                setOut(out, String.join(sep, items));
                log(ctx, "列表已连接成文本（" + items.size() + " 项）");
            }
            case "unique", "去重" -> {
                List<String> uniq = new ArrayList<>();
                for (String s : items) if (!uniq.contains(s)) uniq.add(s);
                setOut(out, join(uniq));
                log(ctx, "列表去重：" + items.size() + " → " + uniq.size());
            }
            case "clear", "清空" -> {
                setOut(out, "");
                log(ctx, "列表已清空（原有 " + items.size() + " 项）");
            }
            default -> warn(ctx, "未知动作: " + action + "（用法：" + usage() + "）");
        }
    }

    static List<String> parse(String stored) {
        List<String> out = new ArrayList<>();
        if (stored == null || stored.isEmpty()) return out;
        out.addAll(Arrays.asList(stored.split(java.util.regex.Pattern.quote(SEP), -1)));
        return out;
    }

    static String join(List<String> items) {
        return String.join(SEP, items);
    }
}
