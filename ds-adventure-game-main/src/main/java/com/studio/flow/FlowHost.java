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

    // =====================================================================
    // 插件支持（编辑器/读取器实现；默认实现保证向后兼容）
    // =====================================================================

    /** 工程根目录（插件目录 plugins/ 的父目录），未知返回 null */
    default java.io.File projectDir() { return null; }

    /** 当前地图文件夹，未知返回 null */
    default java.io.File mapDir() { return null; }

    /**
     * 在地图 [option] 段声明的存档变量列表（供插件按声明类型强制转换）。
     * 默认返回空表。
     */
    default java.util.List<com.studio.model.SaveVarDef> saveVarDefs() { return java.util.List.of(); }

    /** 主音量 0..1（读取器取 [option] volume；插件播放音频时作为默认音量） */
    default double masterVolume() { return 0.8; }

    // =====================================================================
    // 音频通道（由读取器实现；插件通过 @plugin(audio) 使用）
    //   channel 缺省约定：循环用 bgm、一次性用 se；也可自定义通道名同时播放多条
    // =====================================================================

    /** 在指定通道播放音频（路径相对地图文件夹；volume 0..1） */
    default void playAudio(String channel, String path, boolean loop, double volume) { }

    /** 停止某个通道 */
    default void stopAudio(String channel) { }

    /** 停止全部通道 */
    default void stopAllAudio() { }

    /** 暂停 / 继续某个通道；返回是否作用到了真实播放器 */
    default boolean pauseAudio(String channel, boolean pause) { return false; }

    /** 设置某通道音量；返回是否作用到了真实播放器 */
    default boolean setAudioVolume(String channel, double volume) { return false; }

    /**
     * 请求“退出游戏”（自带插件 {@code @plugin(quit)} 调用）。
     *
     * <p>返回 {@code true} 表示宿主已经接管（读取器会先释放媒体/插件资源，再关闭播放器窗口）；
     * 默认返回 {@code false} 表示宿主不处理该请求 —— 插件<b>不会</b>擅自退出整个进程，
     * 这样在编辑器里做静默预览时不会被剧情脚本误杀。</p>
     */
    default boolean requestQuit() { return false; }

    // =====================================================================
    // 演出 / 系统能力（自带插件 @plugin(fx|screenshot|fullscreen) 使用）
    //   与 requestQuit 同一套路：宿主不实现就返回 false，插件自己退化为属性改动，
    //   绝不因为“宿主不支持”而中断剧情。
    // =====================================================================

    /**
     * 播放一个特效预设（由渲染层实现真实动画）。
     *
     * @param nodeId 目标节点 id
     * @param spec   预设与参数，形如 {@code shake:8:400}（抖动强度 8、时长 400ms）、
     *               {@code flash}、{@code pulse}、{@code fadeout:600}、{@code slidein:left}
     * @return true 表示宿主真的播了动画；false 表示不支持（调用方会退化为直接改属性）
     */
    default boolean animate(String nodeId, String spec) { return false; }

    /**
     * 把当前画面截图保存到文件（@plugin(screenshot) 用）。
     *
     * @return true 表示已写出文件
     */
    default boolean snapshot(java.io.File out) { return false; }

    /**
     * 切换全屏（@plugin(fullscreen) 用）。
     *
     * @param want {@code TRUE} 强制全屏、{@code FALSE} 强制退出全屏、{@code null} 表示切换
     * @return true 表示宿主支持并已执行
     */
    default boolean toggleFullscreen(Boolean want) { return false; }

    /** 是否处于 JavaFX 应用线程（默认 true） */
    default boolean isUiThread() { return true; }

    /**
     * 保证在 JavaFX 线程上执行——插件可能在后台线程调用渲染接口，
     * 渲染实现应在这里用 {@code Platform.runLater} 切回去。
     */
    default void runOnUiThread(Runnable action) { action.run(); }
}
