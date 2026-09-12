package com.studio.plugin.builtin;

import com.studio.flow.PluginContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * 编辑器<b>自带</b>插件：<b>随机</b>——随机数、概率判定、随机挑选。
 *
 * <p>剧情里到处都需要“不一定”：掉落、暴击、随机事件、骰子、随机台词、
 * 从一组候选里挑一个。现在写一行槽就能做到。</p>
 *
 * <h3>可用插件 ID 与用法</h3>
 * <pre>
 *   # 整数随机（含两端）：点数 = 1..6
 *   slot = 掷骰 | @plugin(rand) | int    | 1 | 6 | @var(点数)
 *
 *   # 小数随机：[0,1)
 *   slot = 抽签 | @plugin(rand) | double | 0 | 1 | @var(系数)
 *
 *   # 概率判定：30% 中奖 → true/false（写进 bool 变量）
 *   slot = 抽奖 | @plugin(rand) | chance | 30 | @var(中奖)
 *
 *   # 从候选里随机挑一个（候选写在输出位之前，输出位永远是最后一个参数）
 *   slot = 天气 | @plugin(rand) | pick   | 晴 | 雨 | 雾 | @var(今天天气)
 * </pre>
 *
 * <p><b>参数约定</b>：{@code rand | 动作 | … | 输出位}，动作是 {@code int/double/chance/pick}
 * （中文别名 {@code 整数/小数/概率/挑选}）。{@code pick} 的候选个数不限，但输出位必须在最后。</p>
 *
 * <p><b>可复现</b>：想复现某次随机（测试/存档回放）可以写
 * {@code @plugin(rand) | seed | 42}，之后用同一个种子就能得到同一串随机数。</p>
 */
public class RandomPlugin extends BuiltinPlugin {

    private static final Map<String, String> IDS = new LinkedHashMap<>();
    static {
        IDS.put("rand", "rand");
        IDS.put("random", "rand");
        IDS.put("随机", "rand");
        IDS.put("randomnumber", "rand");
    }

    /** 全局随机源（seed 动作可重置，便于复现） */
    private static Random random = new Random();

    public RandomPlugin() { this("rand"); }

    public RandomPlugin(String id) { super(id); }

    public static List<String> ids() { return new ArrayList<>(IDS.keySet()); }

    public static RandomPlugin of(String id) {
        if (id == null) return null;
        String k = id.trim();
        return IDS.containsKey(k.toLowerCase(Locale.ROOT)) || IDS.containsKey(k) ? new RandomPlugin(k) : null;
    }

    /** 编辑器插件目录 */
    public static List<PluginInfo> catalog() {
        List<PluginInfo> out = new ArrayList<>();
        out.add(new PluginInfo("rand", "随机", "random,随机",
                "@plugin(rand) | int | 1 | 6 | @var(点数)",
                "随机数：int 整数 / double 小数 / chance 概率判定 / pick 从候选里挑一个 / seed 设定种子"));
        return out;
    }

    @Override
    protected String group() { return "随机"; }

    @Override
    public String description() {
        return "随机：整数、小数、概率判定、从候选里随机挑一个（输出位永远是最后一个参数）";
    }

    @Override
    public String usage() {
        return "@plugin(rand) | int|double|chance|pick|seed | 参数… | @var(输出)";
    }

    // =====================================================================

    @Override
    protected void run(PluginContext ctx, String[] in, String[] out) {
        if (count(in) < 2) {
            warn(ctx, "参数不足（用法：" + usage() + "）");
            return;
        }
        String action = arg(in, 0).toLowerCase(Locale.ROOT);
        switch (action) {
            case "int", "整数" -> {
                int min = i(arg(in, 1), 0);
                int max = i(arg(in, 2), min);
                if (max < min) { int t = min; min = max; max = t; }
                int v = min + random.nextInt(Math.max(1, max - min + 1));
                setOut(out, String.valueOf(v));
                log(ctx, "整数随机 " + min + ".." + max + " → " + v);
            }
            case "double", "小数" -> {
                double min = num(arg(in, 1), 0);
                double max = num(arg(in, 2), 1);
                double v = min + random.nextDouble() * (max - min);
                setOut(out, trim(v));
                log(ctx, "小数随机 " + trim(min) + ".." + trim(max) + " → " + trim(v));
            }
            case "chance", "概率", "percent" -> {
                double p = num(arg(in, 1), 50);
                boolean hit = random.nextDouble() * 100.0 < p;
                setOut(out, bool(hit));
                log(ctx, "概率 " + trim(p) + "% → " + (hit ? "命中" : "未命中"));
            }
            case "pick", "挑选", "choose" -> {
                if (count(in) < 3) {
                    warn(ctx, "pick 至少要有「一个候选 + 输出位」（用法：" + usage() + "）");
                    return;
                }
                // 候选 = 第 2 个参数到倒数第 2 个参数；输出位 = 最后一个参数
                int last = in.length - 1;
                List<String> candidates = new ArrayList<>();
                for (int i = 1; i < last; i++) {
                    String c = raw(in, i);
                    if (!c.isEmpty()) candidates.add(c);
                }
                if (candidates.isEmpty()) {
                    warn(ctx, "pick 没有可用候选");
                    return;
                }
                String chosen = candidates.get(random.nextInt(candidates.size()));
                setOut(out, chosen);
                log(ctx, "从 " + candidates.size() + " 个候选里挑中：" + chosen);
            }
            case "seed", "种子" -> {
                long seed = (long) num(arg(in, 1), System.currentTimeMillis() % 100000);
                random = new Random(seed);
                log(ctx, "随机种子已设为 " + seed + "（之后可复现同一串随机）");
            }
            default -> warn(ctx, "未知动作: " + action + "（用法：" + usage() + "）");
        }
    }

    private static String trim(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.valueOf(Math.rint(v * 1000) / 1000.0);
    }
}
