package com.example;

import com.studio.flow.PluginContext;
import com.studio.flow.SignalEvent;
import com.studio.flow.SlotPlugin;
import com.studio.saves.SaveData;

import java.util.Map;

/**
 * 变量插件模板（工程师照着改即可）——演示编辑器为插件提供的全部能力。
 *
 * <h3>怎么用</h3>
 * <pre>
 *   1) 编译：javac -encoding UTF-8 -cp &lt;工程classes&gt; -d plugins\classes VarPluginTemplate.java
 *   2) 注册：在 plugins\varplugins.ini 里写  我的插件 = com.example.VarPluginTemplate
 *   3) 脚本：slot = 点击 | @plugin(我的插件) | @var(num1) | @double(1.05) | @var(num2)
 *      动作之后的所有字段会按顺序求值成字符串数组 args 传进来；
 *      返回值同长度数组，只有原本写 @var(x) 的位置会把结果写回变量。
 * </pre>
 *
 * <h3>可用能力（都在 PluginContext 上）</h3>
 * <ul>
 *   <li>变量：{@code var/intVar/doubleVar/boolVar、setVar/addVar}（按 [option] 声明类型强制转换，随存档保存）</li>
 *   <li>存档文件：{@code readSave/writeSave/deleteSave/listSaves/saveVar/setSaveVar}</li>
 *   <li>信号：{@code emit/emitScene} 发；{@code subscribe("信号名")} + {@link #onSignal} 收</li>
 *   <li>渲染：{@code setProperty/setText/setStyle/setVisible/gotoScene/toast/log}（引擎自动切回 JavaFX 线程）</li>
 *   <li>线程安全：{@code locked(() -&gt; ...)} 做原子操作；{@code isUiThread()/onUi(...)} 判断与切换线程</li>
 * </ul>
 */
public class VarPluginTemplate implements SlotPlugin {

    /** 展示名（编辑器里显示） */
    @Override
    public String name() { return "示例变量插件"; }

    @Override
    public String description() { return "把前两个参数相加写回最后一个参数（args 最后一个位置是输出）"; }

    @Override
    public String usage() { return "@plugin(我的插件) | @var(变量A) | @double(1.05) | @var(结果变量)"; }

    /** 首次加载时调用一次：这里订阅一个信号，之后就能在 onSignal 里收到 */
    @Override
    public void onAttach(PluginContext ctx) {
        ctx.subscribe("点击");      // 支持 "*" 订阅全部信号
        ctx.log("示例变量插件已挂载，当前地图变量：" + ctx.intVar("金币"));
    }

    /** 收到订阅的信号时回调（已在引擎锁内、JavaFX 线程上） */
    @Override
    public void onSignal(PluginContext ctx, SignalEvent ev) {
        ctx.log("收到信号 " + ev.signal() + "，来自节点 " + ev.sourceId());
    }

    /**
     * 主逻辑：args[0..n-2] 是输入，args[n-1] 是输出位置。
     * <p>返回 null 表示不改任何参数；返回数组则按位置回写（宿主只写 @var(...) 的位置）。</p>
     */
    @Override
    public String[] execute(PluginContext ctx, String[] args) {
        if (args.length == 0) return args;
        int last = args.length - 1;

        // 1) 读存档变量（不存在时返回默认值；类型不符也不会报错）
        double 金币 = ctx.doubleVar("金币");
        ctx.log("执行前 金币=" + 金币);

        // 2) 用参数做运算（args 里已经是求值后的字符串）
        double a = parse(args[0], 0);
        double b = args.length > 1 ? parse(args[1], 0) : 1.05;
        args[last] = trim(a + b);

        // 3) 顺手改一个存档变量（会随存档保存），并写一条存档文件
        ctx.setVar("金币", 金币 + 1);
        SaveData d = ctx.readSave("slot1");
        if (d == null) d = ctx.newSaveData();
        d.put("plugin.最后调用", String.valueOf(System.currentTimeMillis()));
        ctx.writeSave("slot1", d);

        // 4) 还可以发信号、改渲染属性（渲染操作会自动切回 JavaFX 线程）
        ctx.emit("提示", "插件算完了", Map.of("结果", args[last]));
        ctx.setProperty("提示", "visible", "true");

        return args;
    }

    @Override
    public void onDetach() { /* 释放自己的资源 */ }

    private static double parse(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (RuntimeException e) { return def; }
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
