package com.studio.logic.demo;

import com.studio.flow.FlowContext;
import com.studio.flow.LogicHandler;
import com.studio.flow.SignalEvent;

/**
 * 示例逻辑层（内置，供“信号实验室”示例地图演示）。
 *
 * <p>工程师写逻辑时只需关心：收到了什么信号 → 改哪些变量/节点属性 → 发什么信号；
 * 具体渲染由引擎即时完成（{@code ctx.setStyle/setText/setVisible/emit}）。</p>
 *
 * 逻辑里写的变量（{@code ctx.setVar}）与节点属性改动都会随存档写入 saves/*.txt，
 * 读档时自动恢复并可继续作为逻辑输入。
 */
public class SignalLabLogic implements LogicHandler {

    private static final String LIT_STYLE =
            "-fx-background-color: #f4d06f; -fx-text-fill: #3a2a00;";
    private static final String DARK_STYLE =
            "-fx-background-color: #2a2d45; -fx-text-fill: #8890c0;";

    @Override
    public void onSignal(FlowContext ctx, SignalEvent event) {
        int n = ctx.intVar("点击次数", 0) + 1;
        ctx.setVar("点击次数", n);
        ctx.setNodeVar("灯", "已点亮", (n % 2 == 1) ? "1" : "0");

        // 逻辑 → 渲染：改节点文本与样式（引擎立即重绘）
        ctx.setText("计数", "点击次数: " + n + "（逻辑层写入，随存档保存）");
        boolean lit = n % 2 == 1;
        ctx.setStyle("灯", lit ? LIT_STYLE : DARK_STYLE);
        ctx.setText("灯", lit ? "灯（已点亮）" : "灯（熄灭）");

        // 逻辑 → 变量 → 槽（valueVar 取值）：演示“参数与附带参数”的传递
        ctx.setVar("最近提示", "逻辑广播：第 " + n + " 次点击（信号=" + event.signal()
                + "，来源=" + event.sourceId() + "）");
        ctx.emit("提示", "逻辑广播", FlowContext.params("次数", n));

        ctx.log("onSignal: signal=" + event.signal() + " 来源=" + event.sourceId()
                + " 参数=" + event.params() + " → 点击次数=" + n);
        ctx.toast("逻辑层已处理第 " + n + " 次点击");
    }
}
