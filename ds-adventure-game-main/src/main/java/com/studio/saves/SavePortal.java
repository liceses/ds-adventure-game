package com.studio.saves;

import java.io.IOException;
import java.util.List;

/**
 * 存档门户 —— 引擎暴露给 插件 / 地图工程师 的统一入口（保存在
 * {@code GamePlugin.PARAM_SAVES} 参数中）。
 *
 * <p>通过它可以在不触碰引擎内部的前提下：</p>
 * <ul>
 *   <li>{@link #manager()} —— 拿到底层 {@link GameSaveManager}（枚举/删除任意槽位）；</li>
 *   <li>{@link #saveTo(String)} —— 把<b>当前引擎进度</b>（内置 scene 变量 + SaveHook 补充变量）写入指定槽位；</li>
 *   <li>{@link #loadFrom(String)} —— 读取指定槽位并自动跳回存档中的场景（若由存档界面触发，
 *       成功后引擎会自动退出插件嵌入层）；</li>
 *   <li>{@link #currentScene()} —— 当前场景名。</li>
 * </ul>
 */
public interface SavePortal {

    /** 底层存档管理器 */
    GameSaveManager manager();

    /** 当前场景名 */
    String currentScene();

    /** 保存当前进度到指定槽位（文件名自动补 .txt）；成功返回 true */
    boolean saveTo(String slot);

    /** 读取指定槽位并应用（跳转场景等）；成功返回 true，槽位不存在/失败返回 false */
    boolean loadFrom(String slot);

    /** 便捷：列出 saves 目录全部 .txt（无则空表，不会因目录缺失抛异常） */
    default List<String> listSlots() {
        return manager().listSaveFiles();
    }

    /** 便捷：读取槽位原始存档（供界面预览），读取失败返回 null */
    default SaveData peek(String slot) {
        return manager().read(slot);
    }

    /** 便捷：删除槽位文件（不存在时为 false） */
    default boolean delete(String slot) {
        return manager().delete(slot);
    }
}
