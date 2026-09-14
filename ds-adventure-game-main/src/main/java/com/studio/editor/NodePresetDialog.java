package com.studio.editor;

import com.studio.ui.Ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * <b>个性化节点</b>列表窗口：看有哪些模板、把选中的模板应用到“新建节点”、删除或重命名。
 *
 * <p>入口有三处：检查器「➕ 新增节点模板」栏的「⭐ 个性化节点…」按钮、
 * 编辑菜单、以及画布右键菜单（右键菜单里是“把当前节点存成模板”）。</p>
 *
 * <p>应用之后，右键「添加节点」生成的新节点就会带着这些属性；
 * 模板本身只是初值，不会影响场景里已有的节点。</p>
 */
public final class NodePresetDialog {

    private NodePresetDialog() { }

    /** 打开窗口（owner 可以为 null） */
    public static void show(EditorHub hub, Stage owner) {
        Stage stage = new Stage();
        stage.setTitle("个性化节点（模板存于 " + NodePresetStore.FILE_NAME + "）");
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }

        ObservableList<NodePresetStore.Preset> rows =
                FXCollections.observableArrayList(NodePresetStore.load());
        TableView<NodePresetStore.Preset> table = new TableView<>(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("还没有个性化节点。\n"
                + "在画布上右键一个调好的节点 →「⭐ 添加为个性化节点…」，或点下面的「＋ 把当前选中节点存为模板」。"));
        table.setPrefHeight(320);

        TableColumn<NodePresetStore.Preset, String> cName = new TableColumn<>("模板名");
        cName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        cName.setPrefWidth(200);
        TableColumn<NodePresetStore.Preset, String> cType = new TableColumn<>("类型");
        cType.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTypeText()));
        cType.setPrefWidth(90);
        TableColumn<NodePresetStore.Preset, String> cSum = new TableColumn<>("属性摘要");
        cSum.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSummary()));
        cSum.setPrefWidth(420);
        table.getColumns().setAll(java.util.Arrays.asList(cName, cType, cSum));

        Label where = new Label("📄 模板文件：" + NodePresetStore.file().getAbsolutePath()
                + "（就是地图脚本那套写法，可直接用文本编辑器改）");
        where.getStyleClass().add("hint-text");
        where.setWrapText(true);

        Label current = new Label(currentText(hub));
        current.getStyleClass().add("hint-text");
        current.setWrapText(true);

        Runnable refresh = () -> {
            rows.setAll(NodePresetStore.load());
            current.setText(currentText(hub));
        };

        Button apply = new Button("✔ 应用到新建节点");
        apply.setDefaultButton(true);
        apply.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        apply.setOnAction(e -> {
            NodePresetStore.Preset p = table.getSelectionModel().getSelectedItem();
            if (p == null) return;
            applyPreset(hub, p);
            current.setText(currentText(hub));
        });
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) apply.fire();
        });

        Button addCurrent = new Button("＋ 新建个性化节点（用当前模板）");
        addCurrent.setTooltip(new javafx.scene.control.Tooltip(
                "把检查器里「➕ 新增节点模板」当前的状态存成一条个性化节点（会先让你命名）"));
        addCurrent.setOnAction(e -> {
            NodePresetStore.Preset made = EditorActions.addTemplateAsPreset(hub);
            refresh.run();
            if (made != null) selectByName(table, made.getName());
        });

        Button addFromNode = new Button("＋ 用画布选中节点建模板");
        addFromNode.setTooltip(new javafx.scene.control.Tooltip(
                "把画布上当前选中的节点存成一条个性化节点（等同于右键菜单里的「⭐ 添加为个性化节点…」）"));
        addFromNode.setOnAction(e -> {
            NodePresetStore.Preset made = EditorActions.addSelectedNodeAsPreset(hub);
            refresh.run();
            if (made != null) selectByName(table, made.getName());
        });

        Button rename = new Button("✏️ 重命名…");
        rename.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        rename.setOnAction(e -> {
            NodePresetStore.Preset p = table.getSelectionModel().getSelectedItem();
            if (p == null) return;
            String finalName = EditorActions.renamePreset(hub, p);
            refresh.run();
            if (finalName != null) selectByName(table, finalName);
        });

        Button del = new Button("－ 删除个性化节点");
        del.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        del.setOnAction(e -> {
            NodePresetStore.Preset p = table.getSelectionModel().getSelectedItem();
            if (p == null) return;
            if (EditorActions.deletePreset(hub, p)) refresh.run();
        });

        Button openFile = new Button("📂 打开模板文件所在目录");
        openFile.setOnAction(e -> ExternalEdit.openInExplorer(NodePresetStore.file().getParentFile()));

        Button refreshBtn = new Button("🔄 刷新");
        refreshBtn.setOnAction(e -> refresh.run());

        Button close = new Button("关闭");
        close.setCancelButton(true);
        close.setOnAction(e -> stage.close());

        javafx.scene.layout.HBox bar1 = new javafx.scene.layout.HBox(8, apply, addCurrent, addFromNode);
        javafx.scene.layout.HBox bar2 = new javafx.scene.layout.HBox(8, rename, del, refreshBtn, openFile, close);
        bar1.setAlignment(Pos.CENTER_LEFT);
        bar2.setAlignment(Pos.CENTER_LEFT);

        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(10, where, current, table, bar1, bar2);
        box.setPadding(new Insets(14));
        Scene scene = new Scene(box, 900, 520);
        var css = NodePresetDialog.class.getResource("/styles/studio.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    /** 按名字选中一行（新建/重命名后把光标停在它上面） */
    private static void selectByName(TableView<NodePresetStore.Preset> table, String name) {
        for (int i = 0; i < table.getItems().size(); i++) {
            if (table.getItems().get(i).getName().equals(name)) {
                table.getSelectionModel().select(i);
                table.scrollTo(i);
                return;
            }
        }
    }

    /** 把模板应用到“新建节点模板”（右键添加节点时就会带上这些属性） */
    public static void applyPreset(EditorHub hub, NodePresetStore.Preset preset) {
        if (hub == null || preset == null) return;
        hub.setNewNodeTemplate(preset.getNode().copy());
        hub.notify("已应用个性化节点：「" + preset.getName() + "」—— 之后右键添加的节点都会带上它的属性");
    }

    private static String currentText(EditorHub hub) {
        if (hub == null) return "当前新建节点模板：（未知）";
        var tpl = hub.newNodeTemplate();
        String type = tpl == null || tpl.getType() == null ? "?" : tpl.getType().display();
        String size = tpl == null ? "" : ((int) tpl.getWidth() + "×" + (int) tpl.getHeight());
        return "当前「新增节点模板」：" + type + " " + size
                + "（这个状态就是右键添加节点时的初值；点上面的「应用到新建节点」即可换成某个模板）";
    }

    /** 供检查器按钮使用：打开窗口 */
    public static void showFrom(EditorHub hub) {
        Stage owner = hub instanceof EditorPane p ? p.stageForDialog() : null;
        show(hub, owner);
    }
}
