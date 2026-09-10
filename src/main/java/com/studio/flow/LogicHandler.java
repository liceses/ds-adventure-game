package com.studio.flow;

/**
 * 地图工程师逻辑类的统一接口（放在 logic/ 目录，见 logic/README.txt）。
 *
 * <p>工程师只需实现本接口，把编译产物放进 工程根/logic/classes/（或 地图根/logic/），
 * 在逻辑 ini 里登记 ID，然后在编辑器里给槽写 {@code call} 动作指向该 ID 即可。</p>
 */
public interface LogicHandler {

    /** 收到信号时的逻辑入口；渲染/变量读写都通过 {@link FlowContext} 完成 */
    void onSignal(FlowContext ctx, SignalEvent event);
}
