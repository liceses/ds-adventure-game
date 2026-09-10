package com.studio.editor;

import com.studio.model.GameProject;
import com.studio.model.GameScene;
import com.studio.model.StoryNode;

/**
 * 编辑器内部各视图（画布/左右面板/菜单）之间的统一回调接口 ——
 * 由 EditorPane 实现，让画布、检查器、层级树解耦协作（MVC 的 View-Controller 桥）。
 */
public interface EditorHub {

    // ---------- 模型访问 ----------
    GameProject project();
    GameScene scene();                    // 当前场景
    void switchScene(String name);        // 切换当前场景
    void setDirty();                      // 标记未保存

    // ---------- 状态提示 ----------
    void notify(String message);          // 底部状态栏信息
    void setStatusCoords(double x, double y);
    void setStatusZoom(double zoom);

    // ---------- 选择 ----------
    StoryNode selectedNode();
    void selectNode(StoryNode node);      // 画布/树选择节点 → 检查器
    void refreshInspector();              // 检查器内容重建（跟随当前选择）
    void sceneStructureChanged();         // 场景列表/树/场景下拉框重建

    // ---------- 节点变更 ----------
    void nodeChanged(StoryNode node);     // 属性修改 → 实时刷新该节点视图
    void addNode(StoryNode node);         // 新节点加入当前场景
    void deleteNode(StoryNode node);      // 删除节点（含画布与树）
    void nodesLayerChanged();             // 层级顺序变化
    void optionChanged();                 // [option] 全局设置变化 → 重绘底色

    // ---------- 画布 ----------
    /** 打开节点“详情”编辑对话框（右键/双击触发） */
    void openNodeDialog(StoryNode node);
    /** 在画布逻辑坐标处添加一个默认节点（工具箱拖放、右键菜单调用） */
    void createNodeAt(String typeCode, double x, double y);

    // ---------- 左侧层级树的快捷动作 ----------
    void createSceneViaTree();
    void renameSceneViaTree(GameScene scene);
    void deleteSceneViaTree();
}
