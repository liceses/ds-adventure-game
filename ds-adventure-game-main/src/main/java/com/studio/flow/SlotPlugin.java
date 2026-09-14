package com.studio.flow;

/**
 * 槽插件接口 —— 把“插件事件”整合进信号与槽之后，工程师用它与变量打交道。
 *
 * <h3>在脚本里怎么用</h3>
 * <pre>
 *   # 槽：信号名 | 动作 | 参数1 | 参数2 | 参数3 …
 *   slot = 点击 | @plugin(add) | @var(num1) | @double(1.05) | @var(num2)
 * </pre>
 * 语义：动作写成 {@code @plugin(插件名)}，动作之后的所有字段按顺序解析成
 * <b>通用的字符串数组 args</b> 传给插件；插件返回一个<b>同长度</b>的数组，
 * 宿主只把那些原本写成 {@code @var(x)}（或带强制转换的 {@code @double(@var(x))}）
 * 的位置写回存档变量 —— 于是上面这行就是 {@code num2 = num1 + 1.05}。
 *
 * <h3>工程师实现要点</h3>
 * <ul>
 *   <li>{@link #execute} 里可以读 {@code ctx.var(...)}、写 {@code ctx.setVar(...)}；</li>
 *   <li>存档文件读写用 {@link PluginContext#readSave}/{@link PluginContext#writeSave}；</li>
 *   <li>发信号用 {@link PluginContext#emit}；收信号先 {@link PluginContext#subscribe}，
 *       然后实现 {@link #onSignal}；</li>
 *   <li>需要多步原子操作时用 {@link PluginContext#locked}（引擎保证线程安全，
 *       并且会自动把渲染操作切回 JavaFX 线程）。</li>
 * </ul>
 */
public interface SlotPlugin {

    /**
     * 执行插件。
     *
     * @param ctx  插件上下文（变量 / 存档 / 信号 / 渲染 / 线程安全）
     * @param args 由槽的动作之后各字段解析而来的参数数组（可能为空数组，永不为 null）
     * @return 同长度数组表示“回写后的参数”，返回 null 表示不修改任何参数
     */
    String[] execute(PluginContext ctx, String[] args);

    /** 展示名（编辑器里的插件列表用） */
    default String name() { return getClass().getSimpleName(); }

    /** 一句话说明 */
    default String description() { return ""; }

    /** 参数用法示例（编辑器提示用） */
    default String usage() { return ""; }

    /** 插件被首次加载时调用一次 */
    default void onAttach(PluginContext ctx) { }

    /** 引擎关闭 / 地图切换时调用 */
    default void onDetach() { }

    /** 收到已订阅的信号时回调（在引擎的插件锁内、JavaFX 线程上调用） */
    default void onSignal(PluginContext ctx, SignalEvent event) { }
}
