package com.studio.editor;

import com.studio.ui.Ui;

import javafx.stage.Stage;

import java.io.IOException;

/**
 * 编辑器的“小动作”集合：把「个性化节点」的增删这类需要在多个入口
 * （画布右键菜单 / 编辑菜单 / 个性化节点窗口）复用的逻辑收在一处。
 *
 * <p>这样三处入口的行为完全一致：都走同一套命名校验、同一个模板文件、同一条提示。</p>
 */
public final class EditorActions {

    private EditorActions() { }

    /** 对话框宿主：编辑器窗口 */
    private static Stage ownerOf(EditorHub hub) {
        return hub instanceof EditorPane p ? p.stageForDialog() : null;
    }

    // =====================================================================
    // 个性化节点：新建 / 删除
    // =====================================================================

    /**
     * 把<b>当前「新增节点模板」</b>的状态存成一个新的个性化节点（需要命名）。
     * <p>个性化节点窗口里的「＋ 新建个性化节点」按钮走这条；</p>
     *
     * @return 新建的模板（用户取消或失败返回 null）
     */
    public static NodePresetStore.Preset addTemplateAsPreset(EditorHub hub) {
        if (hub == null) return null;
        var tpl = hub.newNodeTemplate();
        if (tpl == null) {
            Ui.warn(ownerOf(hub), "没有模板状态", "请先在检查器的「➕ 新增节点模板」里调好要保存的节点状态。");
            return null;
        }
        String suggested = NodePresetStore.suggestName(tpl);
        var r = Ui.askText(ownerOf(hub), "新建个性化节点",
                "把当前「新增节点模板」（" + (tpl.getType() == null ? "?" : tpl.getType().display())
                        + " " + (int) tpl.getWidth() + "×" + (int) tpl.getHeight() + "）存为个性化节点",
                "给它起个名字（例如「对白框」「左上角立绘」）：", suggested);
        if (r.isEmpty() || r.get().isBlank()) return null;
        String name = r.get().trim();
        if (NodePresetStore.exists(name)
                && !Ui.confirm(ownerOf(hub), "名字已存在",
                "已经有一个叫「" + name + "」的个性化节点了。",
                "继续的话会自动改名（例如 " + name + "_2），不会覆盖原来的那个。要继续吗？")) {
            return null;
        }
        try {
            String finalName = NodePresetStore.add(name, tpl);
            hub.notify("已新建个性化节点：「" + finalName + "」（存在 " + NodePresetStore.FILE_NAME + "）");
            return new NodePresetStore.Preset(finalName, tpl.copy());
        } catch (IOException e) {
            Ui.error(ownerOf(hub), "保存失败", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 把<b>画布上当前选中的节点</b>存成一个新的个性化节点（需要命名）。
     * <p>画布右键菜单「⭐ 添加为个性化节点…」与编辑菜单同名项走这条。</p>
     *
     * @return 新建的模板（没有选中节点/用户取消/失败返回 null）
     */
    public static NodePresetStore.Preset addSelectedNodeAsPreset(EditorHub hub) {
        if (hub == null) return null;
        var node = hub.selectedNode();
        if (node == null) {
            Ui.warn(ownerOf(hub), "未选中节点", "请先在画布上点选一个调好的节点，再把它存为个性化节点。");
            return null;
        }
        String suggested = NodePresetStore.suggestName(node);
        var r = Ui.askText(ownerOf(hub), "添加为个性化节点",
                "把节点「" + (node.getId() == null || node.getId().isBlank() ? node.getType().display() : node.getId())
                        + "」存为个性化节点",
                "给它起个名字（之后在「个性化节点」窗口里可以随时套用到新节点）：", suggested);
        if (r.isEmpty() || r.get().isBlank()) return null;
        String name = r.get().trim();
        if (NodePresetStore.exists(name)
                && !Ui.confirm(ownerOf(hub), "名字已存在",
                "已经有一个叫「" + name + "」的个性化节点了。",
                "继续的话会自动改名，不会覆盖原来的那个。要继续吗？")) {
            return null;
        }
        try {
            String finalName = NodePresetStore.add(name, node);
            hub.notify("已添加个性化节点：「" + finalName + "」（存在 " + NodePresetStore.FILE_NAME + "）");
            return new NodePresetStore.Preset(finalName, node.copy());
        } catch (IOException e) {
            Ui.error(ownerOf(hub), "保存失败", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 删除一个个性化节点（会二次确认）。
     *
     * @return 是否删掉了
     */
    public static boolean deletePreset(EditorHub hub, NodePresetStore.Preset preset) {
        if (preset == null) return false;
        if (!Ui.confirm(ownerOf(hub), "删除个性化节点",
                "要从模板文件里删掉「" + preset.getName() + "」吗？",
                "只删除这条模板，场景里的节点与当前「新增节点模板」都不受影响。\n文件："
                        + NodePresetStore.FILE_NAME)) {
            return false;
        }
        try {
            boolean ok = NodePresetStore.remove(preset.getName());
            if (hub != null) hub.notify(ok ? ("已删除个性化节点：" + preset.getName()) : "没有找到这个模板");
            return ok;
        } catch (IOException e) {
            Ui.error(ownerOf(hub), "删除失败", e.getMessage(), e);
            return false;
        }
    }

    /** 重命名一个个性化节点（返回最终名字；取消返回 null） */
    public static String renamePreset(EditorHub hub, NodePresetStore.Preset preset) {
        if (preset == null) return null;
        var r = Ui.askText(ownerOf(hub), "重命名个性化节点", "模板：" + preset.getName(), "新的名字：", preset.getName());
        if (r.isEmpty() || r.get().isBlank()) return null;
        try {
            String finalName = NodePresetStore.rename(preset.getName(), r.get());
            if (hub != null) hub.notify("已重命名为：" + finalName);
            return finalName;
        } catch (IOException e) {
            Ui.error(ownerOf(hub), "重命名失败", e.getMessage(), e);
            return null;
        }
    }
}
