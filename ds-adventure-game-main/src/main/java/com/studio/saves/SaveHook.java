package com.studio.saves;

/**
 * 存档参与者钩子 —— 供“地图工程师”编写的后端类实现，扩展引擎默认快照：
 *
 * <pre>
 *   public class 我的存档扩展 implements SaveHook {
 *       public void onEngineSave(SaveData data) {
 *           data.putInt("金币", 主角金币);
 *           data.put("位置", x, y);
 *       }
 *       public void onEngineLoad(SaveData data) {
 *           主角金币 = data.getInt("金币", 0);
 *       }
 *   }
 * </pre>
 *
 * <p>接入方式（二选一）：</p>
 * <ol>
 *   <li>随某个插件类一起实现 —— 插件每次被加载时引擎会检测 {@code instanceof SaveHook}
 *       并自动注册：之后每次 存档/读档 都会回调它；</li>
 *   <li>由地图工程师在自己的事件插件里直接拿到 {@code saves} 参数
 *       （{@code GamePlugin.PARAM_SAVES}），自行读写任意存档文件。</li>
 * </ol>
 *
 * 引擎内置“场景变量”{@code scene}（读取后自动跳到该场景）。
 */
public interface SaveHook {

    /** 即将写入存档时调用：把自定义变量补充进 data */
    default void onEngineSave(SaveData data) { }

    /** 读取存档成功并即将跳转前调用：把变量应用回游戏状态 */
    default void onEngineLoad(SaveData data) { }
}
