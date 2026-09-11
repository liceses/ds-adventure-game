package com.studio.plugin;

import javafx.scene.Parent;
import javafx.stage.Stage;

import java.util.Map;

/**
 * 外部游戏/功能插件接口（工程师只需实现本接口，编译后放入 plugins 目录）。
 *
 * <p>两种接入形态：</p>
 * <ol>
 *   <li><b>嵌入模式（推荐）</b>：覆写 {@link #createEmbeddedView(Map)} 返回一个
 *       JavaFX {@link Parent}，读取器会把它包装到主舞台中央，并自动加上统一的
 *       “返回”标题栏 —— 像浏览器标签页一样无缝跳转（见扫雷 Demo）；</li>
 *   <li><b>窗口模式</b>：仅实现 {@link #execute(Stage, Map)}，自行弹出新窗口。</li>
 * </ol>
 *
 * <p>两种模式读取器都会调用一次 {@link #execute}（参数 {@link #PARAM_EMBEDDED}
 * 可区分是否处于嵌入流程），兼容需求中“调用 execute 启动外部程序”的语义。</p>
 *
 * <p>参数表（params 中的约定键，见下方 PARAM_* 常量）：</p>
 * <ul>
 *   <li>{@link #PARAM_MAP_FOLDER} —— 当前地图文件夹（File）；</li>
 *   <li>{@link #PARAM_PLUGIN_ID} —— 触发本插件的场景/节点事件 ID；</li>
 *   <li>{@link #PARAM_BACK_CALLBACK} —— Runnable，插件可主动请求“返回上一场景”；</li>
 *   <li>{@link #PARAM_EMBEDDED} —— Boolean：true 表示读取器将以嵌入模式托管。</li>
 * </ul>
 */
public interface GamePlugin {

    String PARAM_MAP_FOLDER = "map.folder";
    String PARAM_PLUGIN_ID = "plugin.id";
    String PARAM_BACK_CALLBACK = "back.callback";
    String PARAM_EMBEDDED = "embedded";
    String PARAM_HOST_STAGE = "host.stage";
    /** 存档门户（com.studio.saves.SavePortal）：含 GameSaveManager，可读写 地图/saves/ 任意槽位 */
    String PARAM_SAVES = "saves";

    /** 场景事件要求定义的标准入口：execute(舞台, 参数) */
    void execute(Stage stage, Map<String, Object> params);

    /**
     * 嵌入视图（可选）。返回非 null 时读取器把它嵌入主舞台中央，
     * 顶部自动生成“插件名 + 返回”标题栏；返回 null 则按窗口模式处理。
     */
    default Parent createEmbeddedView(Map<String, Object> params) { return null; }

    /** 展示名称（出现在嵌入标题栏与日志中） */
    default String displayName() { return getClass().getSimpleName(); }

    /**
     * 插件被从主舞台移除时回调：点【← 返回剧情】、被另一个插件替换、或播放器窗口关闭时都会调用，
     * 每运行一次插件最多回调一次。适合在这里停后台线程 / 停计时器 / 释放媒体资源。
     */
    default void onDetach() { }
}
