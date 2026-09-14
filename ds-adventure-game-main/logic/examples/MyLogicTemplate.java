package com.studio.logic.example;

import com.studio.flow.FlowContext;
import com.studio.flow.LogicHandler;
import com.studio.flow.SignalEvent;

import java.util.Map;

/**
 * 外部逻辑类模板（放在 <工程根>/logic/examples，需自行编译到 logic/classes）。
 *
 * <pre>
 *   javac -encoding UTF-8 -cp ..\..\target\classes -d ..\classes com\studio\logic\example\MyLogicTemplate.java
 *   # 然后在 logic/logic.ini 里加： mylogic = com.studio.logic.example.MyLogicTemplate
 * </pre>
 */
public class MyLogicTemplate implements LogicHandler {

    @Override
    public void onSignal(FlowContext ctx, SignalEvent event) {
        ctx.log("收到信号 " + event.signal() + "，来源节点=" + event.sourceId()
                + "，按键=" + event.keyCode() + "，参数=" + event.params());

        int coins = ctx.intVar("金币", 0) + 10;
        ctx.setVar("金币", coins);                                   // 随存档保存
        ctx.setText("金币文本", "金币：" + coins);                    // 即时重绘
        ctx.setStyle("宝箱", "-fx-opacity: 0.35;");                   // 改样式
        ctx.emit("提示", "获得金币", Map.of("数量", 10));              // 向其它节点发信号
        ctx.toast("获得 10 金币！");
    }
}
