package com.studio.plugin.builtin;

import com.studio.flow.PluginContext;
import com.studio.saves.SaveData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 编辑器<b>自带</b>插件：<b>存档管理</b>——列出槽位、复制/删除、读写别的存档里的变量。
 *
 * <p>引擎已经内置了 `save` / `load` 两个槽动作（存/读当前局），这个插件补的是“存档屋”需要的那一套：
 * 有没有存档、几个存档、把 slot1 复制到 slot2、把别的存档里的金币读出来比较……
 * 全部通过 {@link PluginContext} 的存档 API 完成，不依赖界面。</p>
 *
 * <h3>可用插件 ID</h3>
 * <pre>
 *   slot = 列存档 | @plugin(slots)     | @var(存档列表)          # "slot1、slot2"
 *   slot = 有档吗 | @plugin(hasslot)   | slot1 | @var(有档)
 *   slot = 删档   | @plugin(delslot)   | slot1
 *   slot = 复制   | @plugin(copyslot)  | slot1 | slot2
 *   slot = 读变量 | @plugin(readslot)  | slot1 | 金币 | @var(那边的金币)
 *   slot = 写变量 | @plugin(writeslot) | slot1 | 金币 | 999
 *   slot = 档信息 | @plugin(saveinfo)  | slot1 | @var(信息)
 * </pre>
 *
 * <p>槽位名就是 {@code 地图/saves/} 里的文件名（不带扩展名），默认 {@code slot1}。
 * 读不到、写失败都只记日志 + 提示，不会抛异常。</p>
 */
public class SavePlugin extends BuiltinPlugin {

    private enum Act { SLOTS, HAS, DEL, COPY, READ, WRITE, INFO }

    private static final Map<String, Act> IDS = new LinkedHashMap<>();
    static {
        IDS.put("slots", Act.SLOTS);       IDS.put("存档列表", Act.SLOTS);
        IDS.put("hasslot", Act.HAS);       IDS.put("有存档", Act.HAS);
        IDS.put("delslot", Act.DEL);       IDS.put("删存档", Act.DEL); IDS.put("deletesave", Act.DEL);
        IDS.put("copyslot", Act.COPY);     IDS.put("复制存档", Act.COPY);
        IDS.put("readslot", Act.READ);     IDS.put("读存档变量", Act.READ);
        IDS.put("writeslot", Act.WRITE);   IDS.put("写存档变量", Act.WRITE);
        IDS.put("saveinfo", Act.INFO);     IDS.put("存档信息", Act.INFO);
    }

    private final Act act;

    public SavePlugin() { this(Act.SLOTS, "slots"); }

    public SavePlugin(Act act, String id) {
        super(id);
        this.act = act == null ? Act.SLOTS : act;
    }

    public static List<String> ids() { return new ArrayList<>(IDS.keySet()); }

    public static SavePlugin of(String id) {
        if (id == null) return null;
        String k = id.trim();
        Act a = IDS.get(k.toLowerCase(Locale.ROOT));
        if (a == null) a = IDS.get(k);
        return a == null ? null : new SavePlugin(a, k);
    }

    /** 编辑器插件目录 */
    public static List<PluginInfo> catalog() {
        List<PluginInfo> out = new ArrayList<>();
        out.add(new PluginInfo("slots", "存档", "存档列表", "@plugin(slots) | @var(存档列表)", "列出已有的存档槽位（用「、」连接）"));
        out.add(new PluginInfo("hasslot", "存档", "有存档", "@plugin(hasslot) | slot1 | @var(有档)", "某个槽位是否存在 → bool"));
        out.add(new PluginInfo("delslot", "存档", "删存档,deletesave", "@plugin(delslot) | slot1", "删除某个槽位的存档"));
        out.add(new PluginInfo("copyslot", "存档", "复制存档", "@plugin(copyslot) | slot1 | slot2", "把一个槽位复制到另一个槽位"));
        out.add(new PluginInfo("readslot", "存档", "读存档变量", "@plugin(readslot) | slot1 | 金币 | @var(那边的金币)", "读别的存档里的某个变量"));
        out.add(new PluginInfo("writeslot", "存档", "写存档变量", "@plugin(writeslot) | slot1 | 金币 | 999", "写别的存档里的某个变量（槽位不存在则新建）"));
        out.add(new PluginInfo("saveinfo", "存档", "存档信息", "@plugin(saveinfo) | slot1 | @var(信息)", "取存档摘要（场景 / 变量条数）"));
        return out;
    }

    @Override
    protected String group() { return "存档"; }

    @Override
    public String description() {
        switch (act) {
            case SLOTS: return "列出已有存档槽位";
            case HAS:   return "判断槽位是否存在 → bool";
            case DEL:   return "删除槽位";
            case COPY:  return "复制槽位";
            case READ:  return "读别的存档里的变量";
            case WRITE: return "写别的存档里的变量";
            case INFO:  return "取存档摘要";
            default:    return "";
        }
    }

    @Override
    public String usage() {
        switch (act) {
            case SLOTS: return "@plugin(slots) | @var(输出)";
            case HAS:   return "@plugin(hasslot) | 槽位 | @var(输出bool)";
            case DEL:   return "@plugin(delslot) | 槽位";
            case COPY:  return "@plugin(copyslot) | 源槽位 | 目标槽位";
            case READ:  return "@plugin(readslot) | 槽位 | 变量名 | @var(输出)";
            case WRITE: return "@plugin(writeslot) | 槽位 | 变量名 | 值";
            case INFO:  return "@plugin(saveinfo) | 槽位 | @var(输出)";
            default:    return "";
        }
    }

    // =====================================================================

    @Override
    protected void run(PluginContext ctx, String[] in, String[] out) {
        if (ctx == null) {
            warn(null, "没有上下文，无法访问存档");
            return;
        }
        switch (act) {
            case SLOTS -> {
                List<String> names = ctx.listSaves();
                setOut(out, String.join("、", names));
                log(ctx, names.isEmpty() ? "目前没有任何存档" : ("存档槽位：" + String.join("、", names)));
            }
            case HAS -> {
                String slot = slotOf(in, 0);
                boolean exists = ctx.readSave(slot) != null;
                setOut(out, bool(exists));
                log(ctx, "槽位 " + slot + (exists ? " 存在" : " 不存在"));
            }
            case DEL -> {
                String slot = slotOf(in, 0);
                boolean ok = ctx.deleteSave(slot);
                log(ctx, (ok ? "已删除存档 " : "删除失败（可能不存在）：") + slot);
                toast(ctx, ok ? ("已删除存档：" + slot) : ("没有这个存档：" + slot));
            }
            case COPY -> {
                String from = slotOf(in, 0);
                String to = slotOf(in, 1);
                SaveData data = ctx.readSave(from);
                if (data == null) {
                    warn(ctx, "源存档不存在：" + from);
                    return;
                }
                boolean ok = ctx.writeSave(to, data);
                log(ctx, ok ? ("已把 " + from + " 复制到 " + to) : ("复制失败：" + from + " → " + to));
            }
            case READ -> {
                String slot = slotOf(in, 0);
                String name = arg(in, 1);
                setOut(out, ctx.saveVar(slot, name, ""));
                log(ctx, "读 " + slot + "." + name + " → " + (out.length > 0 ? out[out.length - 1] : ""));
            }
            case WRITE -> {
                String slot = slotOf(in, 0);
                String name = arg(in, 1);
                String value = raw(in, 2);
                boolean ok = ctx.setSaveVar(slot, name, value);
                log(ctx, ok ? ("已写 " + slot + "." + name + " = " + value) : ("写存档失败：" + slot));
            }
            case INFO -> {
                String slot = slotOf(in, 0);
                SaveData data = ctx.readSave(slot);
                if (data == null) {
                    setOut(out, "（无此存档）");
                    return;
                }
                String scene = data.getString("scene", "?");
                int keys = data.keys().size();
                String info = "场景=" + scene + " 变量=" + keys + " 项";
                setOut(out, info);
                log(ctx, slot + " → " + info);
            }
            default -> { /* 不会发生 */ }
        }
    }

    private static String slotOf(String[] in, int index) {
        String s = arg(in, index);
        return s.isEmpty() ? "slot1" : s;
    }
}
