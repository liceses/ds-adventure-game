package com.studio.plugin.builtin;

/**
 * 一条自带插件的“目录项”：编辑器用它自动生成**插件选择器**与**插件手册**。
 *
 * <p>有了它，以后再加一个自带插件，只要在自己的 {@code catalog()} 里登记一条，
 * 编辑器的下拉框、插入模板、帮助页签就会自动出现 —— 不用再手改界面与文档。</p>
 *
 * @param id          脚本里 @plugin(...) 写的 ID（主 ID，非别名）
 * @param group       分组（运算 / 逻辑 / 音频 / 视频 / 系统 / 流程 / 随机 / 交互 / 文本 / 格式 / 演出 / 存档 / 调试 / 网络 / 列表 / 数据 / 时间）
 * @param aliases     中文/英文别名（逗号分隔，可为空）
 * @param usage       一行式用法（编辑器选中后按它填模板）
 * @param description 一句话说明（手册里显示）
 */
public record PluginInfo(String id, String group, String aliases, String usage, String description) {

    /** 下拉框/列表里的显示文字 */
    public String label() {
        return group + " · " + id + (aliases == null || aliases.isBlank() ? "" : "（" + aliases + "）");
    }

    /**
     * 由这一项生成一行可直接用的槽模板：{@code 点击 | @plugin(id) | 参数…}。
     *
     * <p>编辑器（节点属性窗口 / 场景属性窗口）的「插入插件槽」按钮与探针都用它，
     * 保证“界面里插出来的写法”与“目录里写的用法”永远一致。</p>
     */
    public String slotTemplate() {
        String u = usage == null ? "" : usage.trim();
        if (u.isEmpty()) u = "@plugin(" + id + ")";
        if (!u.startsWith("@plugin(")) u = "@plugin(" + u + ")";
        return "点击 | " + u;
    }
}
