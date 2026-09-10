package com.studio.flow;

import com.studio.model.GameScene;
import com.studio.model.StoryNode;
import com.studio.saves.GameSaveManager;

/**
 * 引擎侧能力接口（由读取器 ReaderView 实现）——
 * 让“槽执行器”和“地图工程师的逻辑层”在不依赖 JavaFX 的前提下操作引擎。
 */
public interface FlowHost {

    /** 当前场景模型 */
    GameScene scene();

    /** 按 id 查找当前场景内的节点（找不到返回 null） */
    StoryNode node(String id);

    /** 读取节点属性（style/text/visible/x/y/... ） */
    String property(String nodeId, String prop);

    /**
     * 设置节点属性并<b>立即重绘</b>（渲染层由引擎负责，逻辑层只给意图）；
     * 同时记录为属性覆盖，随存档保存、读档后自动重新应用。
     */
    void setProperty(String nodeId, String prop, String value);

    /**
     * 带过渡动画的属性设置（例如 {@code transitionSpec = "scale/opacity:300ms"}）。
     * 默认实现退化为立即设置；渲染引擎会真正做补间动画。
     */
    default void setPropertyAnimated(String nodeId, String prop, String value, String transitionSpec) {
        setProperty(nodeId, prop, value);
    }

    /** 派发信号：targetId 为空表示场景信号；params 可为 null */
    void emit(String targetId, String signalName, java.util.Map<String, Object> params);

    /** 跳转场景 */
    void gotoScene(String name);

    /** 读写存档槽位（内部会带上引擎快照与地图变量） */
    boolean saveSlot(String slot);

    boolean loadSlot(String slot);

    /** 存档管理器 */
    GameSaveManager saves();

    /** 运行期变量仓库 */
    FlowVariables variables();

    /** 顶部提示条 */
    void toast(String message);

    /** 控制台日志 */
    void log(String message);
}
