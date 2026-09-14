package com.studio.editor;

import com.studio.flow.SignalCodec;
import com.studio.flow.SignalDef;
import com.studio.flow.SlotDef;
import com.studio.model.GameScene;
import com.studio.model.StoryNode;
import com.studio.ui.Ui;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景属性窗口：把「一个场景的全部信息」集中在一个弹窗里看。
 *
 * <ul>
 *   <li><b>属性</b>：{@code [场景] } 段里的所有键值（next / event / 以及未知键），可直接编辑、增删；</li>
 *   <li><b>节点</b>：场景内全部节点的列表（层级/类型/编号/坐标/尺寸/可见/文字），
 *       点一行就在画布里选中它并显示它的全部属性，也能直接打开节点属性窗口；</li>
 *   <li><b>信号 / 槽</b>：场景级信号与槽的列表，用一行式文本查看/新增/修改/删除。</li>
 * </ul>
 *
 * <p>与其它对话框一致：改动即时生效，打开前会记一步撤销快照。</p>
 */
final class SceneInspectorDialog {

    private SceneInspectorDialog() { }

    static void show(EditorHub hub, GameScene scene) {
        if (scene == null) {
            hub.notify("当前没有场景");
            return;
        }
        hub.pushUndo("修改场景属性");

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("场景属性 — " + scene.getName());
        dialog.setHeaderText("🧩 场景[" + scene.getName() + "]：属性 / 节点列表 / 信号与槽");
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("关闭", ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefSize(920, 900);

        Runnable mark = () -> {
            hub.setDirty();
            hub.sceneStructureChanged();
            hub.refreshInspector();
        };

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                propsTab(hub, scene, mark),
                nodesTab(hub, scene, mark),
                signalsTab(hub, scene, mark));
        dialog.getDialogPane().setContent(tabs);
        dialog.getDialogPane().getStylesheets().addAll(
                Ui.class.getResource("/styles/studio.css").toExternalForm());
        dialog.showAndWait();
    }

    // =====================================================================
    // 1) 属性
    // =====================================================================

    /** 属性表的一行 */
    public static class PropRow {
        private final String key;
        private String value;

        PropRow(String key, String value) {
            this.key = key;
            this.value = value == null ? "" : value;
        }

        public String getKey() { return key; }
        public String getValue() { return value; }
        public void setValue(String v) { this.value = v == null ? "" : v; }
    }

    private static Tab propsTab(EditorHub hub, GameScene scene, Runnable mark) {
        ObservableList<PropRow> rows = FXCollections.observableArrayList();
        for (Map.Entry<String, String> e : scene.props().entrySet()) {
            rows.add(new PropRow(e.getKey(), e.getValue()));
        }

        Label summary = new Label(summaryText(hub, scene));
        summary.getStyleClass().add("section-title");

        TableView<PropRow> table = new TableView<>(rows);
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<PropRow, String> kCol = new TableColumn<>("属性");
        kCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getKey()));
        kCol.setPrefWidth(280);

        TableColumn<PropRow, String> vCol = new TableColumn<>("值（双击可改）");
        vCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getValue()));
        vCol.setCellFactory(TextFieldTableCell.forTableColumn());
        vCol.setOnEditCommit(ev -> {
            PropRow row = ev.getRowValue();
            row.setValue(ev.getNewValue());
            scene.setProp(row.getKey(), row.getValue());
            hub.notify("场景属性 " + row.getKey() + " = " + row.getValue());
            mark.run();
            summary.setText(summaryText(hub, scene));
        });
        table.getColumns().addAll(kCol, vCol);

        Button addBtn = new Button("＋ 添加属性");
        addBtn.setOnAction(e -> {
            String[] kv = askKeyValue();
            if (kv == null) return;
            scene.setProp(kv[0], kv[1]);
            rows.add(new PropRow(kv[0], kv[1]));
            mark.run();
            summary.setText(summaryText(hub, scene));
        });
        Button delBtn = new Button("－ 删除选中");
        delBtn.setOnAction(e -> {
            PropRow row = table.getSelectionModel().getSelectedItem();
            if (row == null) {
                Ui.warn(null, "未选中", "请先在表格里选中一行属性。");
                return;
            }
            scene.props().remove(row.getKey());
            rows.remove(row);
            mark.run();
            summary.setText(summaryText(hub, scene));
        });
        Button editSceneBtn = new Button("✏️ 重命名场景…");
        editSceneBtn.setOnAction(e -> hub.renameSceneViaTree(scene));

        Label tip = new Label("说明：next = 没有按钮时的下一场景；event = 进入本场景时运行的插件事件；"
                + "其余键值原样保留在 scenario.txt 里。");
        tip.getStyleClass().add("hint-text");
        tip.setWrapText(true);

        HBox bar = new HBox(8, addBtn, delBtn, editSceneBtn);
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(8, summary, table, bar, tip);
        box.setPadding(new Insets(12));
        return new Tab("🧾 属性", box);
    }

    private static String summaryText(EditorHub hub, GameScene scene) {
        boolean isInitial = hub.project() != null
                && scene.getName().equals(hub.project().option().initialScene());
        return scene.getName() + "：节点 " + scene.nodes().size()
                + "　信号 " + scene.signals().size()
                + "　槽 " + scene.slots().size()
                + (isInitial ? "　★ 初始场景" : "");
    }

    /** 弹出“属性名 / 值”输入框 */
    private static String[] askKeyValue() {
        Dialog<Void> d = new Dialog<>();
        d.setTitle("添加场景属性");
        d.setHeaderText("新增一条 [场景] 段属性");
        ButtonType ok = new ButtonType("添加", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        TextField key = new TextField();
        key.setPromptText("属性名，如 next");
        TextField value = new TextField();
        value.setPromptText("值，如 结算");
        VBox box = new VBox(8, new Label("属性名"), key, new Label("值"), value);
        box.setPadding(new Insets(12));
        d.getDialogPane().setContent(box);
        d.getDialogPane().getStylesheets().addAll(
                Ui.class.getResource("/styles/studio.css").toExternalForm());
        var r = d.showAndWait();
        if (r.isEmpty()) return null;
        String k = key.getText() == null ? "" : key.getText().trim();
        if (k.isEmpty()) return null;
        return new String[]{k, value.getText() == null ? "" : value.getText().trim()};
    }

    // =====================================================================
    // 2) 节点列表
    // =====================================================================

    private static Tab nodesTab(EditorHub hub, GameScene scene, Runnable mark) {
        ObservableList<StoryNode> rows = FXCollections.observableArrayList(scene.nodes());

        TableView<StoryNode> table = new TableView<>(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(300);

        table.getColumns().addAll(
                col("层级", 56, n -> String.valueOf(n.getIndex())),
                col("类型", 90, n -> n.getType().icon() + " " + n.getType().display()),
                col("编号 id", 150, StoryNode::getId),
                col("坐标", 120, n -> (int) n.getX() + ", " + (int) n.getY()),
                col("尺寸", 110, n -> (int) n.getWidth() + "×" + (int) n.getHeight()),
                col("可见", 60, n -> n.isVisible() ? "是" : "否"),
                col("文字/素材", 260, n -> {
                    String t = n.getText() == null ? "" : n.getText().replace('\n', ' ');
                    if (t.isBlank()) t = n.getPath();
                    if (t == null) t = "";
                    return t.length() > 40 ? t.substring(0, 40) + "…" : t;
                }));

        TextArea detail = new TextArea();
        detail.setEditable(false);
        detail.setPrefRowCount(9);
        detail.setStyle("-fx-font-family: 'Consolas', 'Microsoft YaHei', monospace; -fx-font-size: 12px;");
        detail.setPromptText("点上面的节点行，这里显示它的全部属性（就是 scenario.txt 里那个 { } 块）");

        Runnable showDetail = () -> {
            StoryNode n = table.getSelectionModel().getSelectedItem();
            if (n == null) {
                detail.clear();
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("【").append(n.getType().display()).append("】").append(n.getId())
              .append("（场景内层级 ").append(n.getIndex()).append("）\n");
            for (Map.Entry<String, String> e : n.toScriptMap().entrySet()) {
                sb.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
            }
            for (SignalDef s : n.signals()) {
                sb.append("signal = ").append(SignalCodec.encode(s)).append('\n');
            }
            for (SlotDef s : n.slots()) {
                sb.append("slot = ").append(SignalCodec.encode(s)).append('\n');
            }
            detail.setText(sb.toString());
        };

        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b != null) hub.selectNode(b);   // 点一行 → 画布里也选中它
            showDetail.run();
        });
        table.setRowFactory(tv -> {
            javafx.scene.control.TableRow<StoryNode> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !row.isEmpty()) {
                    hub.openNodeDialog(row.getItem());
                }
            });
            return row;
        });

        Button editBtn = new Button("✏️ 编辑属性…");
        editBtn.setOnAction(e -> {
            StoryNode n = table.getSelectionModel().getSelectedItem();
            if (n == null) {
                Ui.warn(null, "未选中", "请先在上面的列表里选中一个节点。");
                return;
            }
            hub.openNodeDialog(n);
        });
        Button selectBtn = new Button("🎯 在画布中选中");
        selectBtn.setOnAction(e -> {
            StoryNode n = table.getSelectionModel().getSelectedItem();
            if (n != null) hub.selectNode(n);
        });
        Button delBtn = new Button("🗑 删除节点");
        delBtn.setOnAction(e -> {
            StoryNode n = table.getSelectionModel().getSelectedItem();
            if (n == null) return;
            hub.deleteNode(n);
            rows.setAll(scene.nodes());
        });
        Button refreshBtn = new Button("🔄 刷新列表");
        refreshBtn.setOnAction(e -> rows.setAll(scene.nodes()));

        HBox bar = new HBox(8, selectBtn, editBtn, delBtn, refreshBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        Label tip = new Label("提示：单击一行 → 画布同步选中并在下方显示它的全部属性；双击一行 → 直接打开节点属性窗口。");
        tip.getStyleClass().add("hint-text");
        tip.setWrapText(true);

        VBox box = new VBox(8, table, bar, detail, tip);
        box.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);
        return new Tab("🎬 节点（" + rows.size() + "）", box);
    }

    private static TableColumn<StoryNode, String> col(String title, double width,
                                                      java.util.function.Function<StoryNode, String> getter) {
        TableColumn<StoryNode, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(cd -> new SimpleStringProperty(safe(getter.apply(cd.getValue()))));
        return c;
    }

    private static String safe(String s) { return s == null ? "" : s; }

    // =====================================================================
    // 3) 信号 / 槽
    // =====================================================================

    private static Tab signalsTab(EditorHub hub, GameScene scene, Runnable mark) {
        // ---- 信号 ----
        ObservableList<SignalDef> sigRows = FXCollections.observableArrayList(scene.signals());
        TableView<SignalDef> sigTable = new TableView<>(sigRows);
        sigTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        sigTable.setPrefHeight(170);
        TableColumn<SignalDef, String> s1 = new TableColumn<>("信号名");
        s1.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        s1.setPrefWidth(180);
        TableColumn<SignalDef, String> s2 = new TableColumn<>("类型");
        s2.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getKind() == SignalDef.Kind.KEY ? "key 键盘" : "mouse 鼠标"));
        s2.setPrefWidth(120);
        TableColumn<SignalDef, String> s3 = new TableColumn<>("按键 / 鼠标");
        s3.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getKind() == SignalDef.Kind.KEY ? c.getValue().getKey() : c.getValue().getMouse()));
        s3.setPrefWidth(140);
        TableColumn<SignalDef, String> s4 = new TableColumn<>("相位");
        s4.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getKind() == SignalDef.Kind.KEY ? c.getValue().getKeyPhase() : ""));
        s4.setPrefWidth(90);
        TableColumn<SignalDef, String> s5 = new TableColumn<>("附带参数");
        s5.setCellValueFactory(c -> new SimpleStringProperty(SignalCodec.encodeParams(c.getValue().params())));
        s5.setPrefWidth(200);
        sigTable.getColumns().addAll(s1, s2, s3, s4, s5);

        TextField sigText = new TextField();
        sigText.setPromptText("一行式写法：名称 | mouse|key | click|release|按键码 | press|release | k=v");
        sigTable.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b != null) sigText.setText(SignalCodec.encode(b));
        });
        Button sigAdd = new Button("＋ 新增信号");
        sigAdd.setOnAction(e -> {
            String line = sigText.getText();
            if (line == null || line.isBlank()) {
                Ui.warn(null, "内容为空", "请在下面的输入框里写一行式信号，例如：\n重置变量 | key | R | press");
                return;
            }
            List<String> warnings = new ArrayList<>();
            SignalDef def = SignalCodec.decodeSignal(line, warnings);
            if (def == null) {
                Ui.warn(null, "无法解析", String.join("\n", warnings));
                return;
            }
            scene.signals().add(def);
            sigRows.setAll(scene.signals());
            mark.run();
            hub.notify("已新增场景信号：" + def.getName());
        });
        Button sigApply = new Button("✔ 应用到选中信号");
        sigApply.setOnAction(e -> {
            int i = sigTable.getSelectionModel().getSelectedIndex();
            if (i < 0) {
                Ui.warn(null, "未选中", "请先在上面的信号列表里选中一行。");
                return;
            }
            List<String> warnings = new ArrayList<>();
            SignalDef def = SignalCodec.decodeSignal(sigText.getText(), warnings);
            if (def == null) {
                Ui.warn(null, "无法解析", String.join("\n", warnings));
                return;
            }
            scene.signals().set(i, def);
            sigRows.setAll(scene.signals());
            mark.run();
            hub.notify("已修改场景信号：" + def.getName());
        });
        Button sigDel = new Button("－ 删除选中信号");
        sigDel.setOnAction(e -> {
            int i = sigTable.getSelectionModel().getSelectedIndex();
            if (i < 0) return;
            String name = scene.signals().get(i).getName();
            scene.signals().remove(i);
            sigRows.setAll(scene.signals());
            mark.run();
            hub.notify("已删除场景信号：" + name);
        });
        HBox sigBar = new HBox(8, sigAdd, sigApply, sigDel);
        sigBar.setAlignment(Pos.CENTER_LEFT);

        // ---------- 快捷信号：常用鼠标/键盘信号一键加（和节点属性窗口里那排一样） ----------
        javafx.scene.layout.FlowPane sigTpl = new javafx.scene.layout.FlowPane(6, 4,
                sigAdd, sigApply, sigDel,
                signalTemplate("＋鼠标点击", "场景点击 | mouse | click", scene, sigRows, sigTable, sigText, mark, hub),
                signalTemplate("＋鼠标释放", "场景松开 | mouse | release", scene, sigRows, sigTable, sigText, mark, hub),
                signalTemplate("＋按键 F", "场景按键F | key | F | press", scene, sigRows, sigTable, sigText, mark, hub),
                signalTemplate("＋空格键", "场景空格 | key | SPACE | press", scene, sigRows, sigTable, sigText, mark, hub),
                signalTemplate("＋方向键 ↑", "场景上键 | key | UP | press", scene, sigRows, sigTable, sigText, mark, hub),
                signalTemplate("＋方向键 ↓", "场景下键 | key | DOWN | press", scene, sigRows, sigTable, sigText, mark, hub));
        sigTpl.setAlignment(Pos.CENTER_LEFT);

        // ---- 槽 ----
        ObservableList<SlotDef> slotRows = FXCollections.observableArrayList(scene.slots());
        TableView<SlotDef> slotTable = new TableView<>(slotRows);
        slotTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        slotTable.setPrefHeight(200);
        TableColumn<SlotDef, String> t1 = new TableColumn<>("订阅的信号");
        t1.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSignal()));
        t1.setPrefWidth(150);
        TableColumn<SlotDef, String> t2 = new TableColumn<>("动作");
        t2.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAction()));
        t2.setPrefWidth(150);
        TableColumn<SlotDef, String> t3 = new TableColumn<>("目标");
        t3.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTarget()));
        t3.setPrefWidth(120);
        TableColumn<SlotDef, String> t4 = new TableColumn<>("参数");
        t4.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getArg()));
        t4.setPrefWidth(140);
        TableColumn<SlotDef, String> t5 = new TableColumn<>("附加参数");
        t5.setCellValueFactory(c -> new SimpleStringProperty(SignalCodec.encodeParams(c.getValue().params())));
        t5.setPrefWidth(200);
        slotTable.getColumns().addAll(t1, t2, t3, t4, t5);

        TextField slotText = new TextField();
        slotText.setPromptText("一行式写法：信号名 | 动作 | 目标 | 参数 | 附加参数");
        slotTable.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b != null) slotText.setText(SignalCodec.encode(b));
        });
        Button slotAdd = new Button("＋ 新增槽");
        slotAdd.setOnAction(e -> {
            String line = slotText.getText();
            if (line == null || line.isBlank()) {
                Ui.warn(null, "内容为空",
                        "请在下面的输入框里写一行式槽，例如：\n刷新 | @plugin(and) | @var(灯1) | @var(灯2) | @var(灯3)");
                return;
            }
            List<String> warnings = new ArrayList<>();
            SlotDef def = SignalCodec.decodeSlot(line, warnings);
            if (def == null) {
                Ui.warn(null, "无法解析", String.join("\n", warnings));
                return;
            }
            scene.slots().add(def);
            slotRows.setAll(scene.slots());
            mark.run();
            hub.notify("已新增场景槽：" + def.getSignal() + " → " + def.getAction());
        });
        Button slotApply = new Button("✔ 应用到选中槽");
        slotApply.setOnAction(e -> {
            int i = slotTable.getSelectionModel().getSelectedIndex();
            if (i < 0) {
                Ui.warn(null, "未选中", "请先在上面的槽列表里选中一行。");
                return;
            }
            List<String> warnings = new ArrayList<>();
            SlotDef def = SignalCodec.decodeSlot(slotText.getText(), warnings);
            if (def == null) {
                Ui.warn(null, "无法解析", String.join("\n", warnings));
                return;
            }
            scene.slots().set(i, def);
            slotRows.setAll(scene.slots());
            mark.run();
            hub.notify("已修改场景槽：" + def.getSignal() + " → " + def.getAction());
        });
        Button slotDel = new Button("－ 删除选中槽");
        slotDel.setOnAction(e -> {
            int i = slotTable.getSelectionModel().getSelectedIndex();
            if (i < 0) return;
            scene.slots().remove(i);
            slotRows.setAll(scene.slots());
            mark.run();
            hub.notify("已删除场景槽");
        });
        HBox slotBar = new HBox(8, slotAdd, slotApply, slotDel);
        slotBar.setAlignment(Pos.CENTER_LEFT);

        // ---------- 快捷槽模板：点一下直接多一条槽（发信号 / 定时器 / 循环 / 停表 / 改属性 / 逻辑 / 跳场景） ----------
        javafx.scene.layout.FlowPane slotTpl = new javafx.scene.layout.FlowPane(6, 4,
                slotAdd, slotApply, slotDel,
                slotTemplate("＋发信号槽", "刷新 | emit |  | 我发出的信号", scene, slotRows, slotTable, mark, hub),
                slotTemplate("＋定时槽(after)", "场景进入 | @plugin(after) | 3 | 定时到", scene, slotRows, slotTable, mark, hub),
                slotTemplate("＋循环槽(every)", "场景进入 | @plugin(every) | 1 | 每秒", scene, slotRows, slotTable, mark, hub),
                slotTemplate("＋停定时槽", "停止 | @plugin(stoptimer) | 每秒", scene, slotRows, slotTable, mark, hub),
                slotTemplate("＋改属性槽", "刷新 | set | 节点id | text | value=新文本", scene, slotRows, slotTable, mark, hub),
                slotTemplate("＋逻辑槽(call)", "刷新 | call |  | logic层id | 说明=转给逻辑层", scene, slotRows, slotTable, mark, hub),
                slotTemplate("＋跳场景槽(goto)", "下一幕 | goto | 场景名", scene, slotRows, slotTable, mark, hub),
                slotTemplate("＋日志槽(log)", "刷新 | log |  | 消息", scene, slotRows, slotTable, mark, hub));
        slotTpl.setAlignment(Pos.CENTER_LEFT);

        // ---------- 引擎自动信号：一键订阅（进/离场景时自动发出，不用自己声明） ----------
        javafx.scene.layout.FlowPane autoBar = new javafx.scene.layout.FlowPane(6, 4);
        autoBar.setAlignment(Pos.CENTER_LEFT);
        Label autoTip = new Label("引擎自动信号（不用声明，进/离场景时自动发；点一下 = 加一条订阅它的槽）");
        autoTip.getStyleClass().add("hint-text");
        autoBar.getChildren().add(autoTip);
        for (com.studio.flow.AutoSignals.Def def : com.studio.flow.AutoSignals.ALL) {
            Button b = new Button("🔔 " + def.name());
            b.getStyleClass().add("tool-button");
            b.setTooltip(new javafx.scene.control.Tooltip(
                    def.name() + "（别名 " + String.join(" / ", def.aliases()) + "）\n"
                            + "什么时候发：" + def.when() + "\n"
                            + "参数：" + def.params() + "\n"
                            + "用途：" + def.hint() + "\n"
                            + "（点一下 = 加一条订阅它的 log 槽，改成自己的逻辑即可）"));
            b.setOnAction(e -> {
                scene.slots().add(subscribeAuto(def));
                slotRows.setAll(scene.slots());
                slotTable.getSelectionModel().selectLast();
                mark.run();
                hub.notify("已订阅自动信号：" + def.name() + "（" + def.hint() + "）");
            });
            autoBar.getChildren().add(b);
        }

        // 插件选择器：自带 + 外部（注册表登记 / classes / jar 里编译好的），可输入关键字筛选
        javafx.scene.control.ComboBox<com.studio.plugin.builtin.PluginInfo> pluginPicker =
                PluginPickerField.create(hub, 280);
        Button pluginInsert = new Button("＋ 插入插件槽");
        pluginInsert.setTooltip(new javafx.scene.control.Tooltip(
                "把选中的插件按它自己的用法追加成一条场景槽（点一下列表里就多一行）"));
        pluginInsert.setOnAction(e -> {
            com.studio.plugin.builtin.PluginInfo info = NodeDialogs.pickedPlugin(pluginPicker);
            if (info == null) {
                Ui.warn(null, "还没选插件",
                        "请先在上面的下拉框里选一个插件（可以直接打字筛选，例如 full / 全屏 / 截图），\n"
                                + "再点「＋ 插入插件槽」。当前可用插件共 " + pluginPicker.getItems().size() + " 个。");
                return;
            }
            String line = NodeDialogs.templateOf(info);
            java.util.List<String> warnings = new ArrayList<>();
            SlotDef def = SignalCodec.decodeSlot(line, warnings);
            if (def == null) {
                Ui.warn(null, "模板无法解析", String.join("\n", warnings));
                return;
            }
            scene.slots().add(def);
            slotRows.setAll(scene.slots());
            slotTable.getSelectionModel().selectLast();
            mark.run();
            slotText.setText(line);   // 留在输入框里，方便接着改成自己的变量名
            hub.notify("已插入插件槽：" + info.id() + " → " + def.getSignal() + " | " + def.getAction());
        });
        HBox pluginBar = new HBox(8, new Label("自带插件："), pluginPicker, pluginInsert);
        pluginBar.setAlignment(Pos.CENTER_LEFT);

        Label tip = new Label("提示：这些是**场景级**信号与槽（写在 [场景名] 段里），"
                + "节点自己的信号/槽在「节点」页双击进入节点属性窗口里改。"
                + "槽按信号名全场景订阅：同一场景里同名信号的所有槽都会一起触发。"
                + "「场景进入」/「场景离开」/「上一个场景离开」是引擎自动发的信号（见上面那排 🔔 按钮），"
                + "不用在信号表里声明；定时器可以用 @plugin(after)（N 秒后发一个信号）与 @plugin(every)（每 N 秒发一次）。");
        tip.getStyleClass().add("hint-text");
        tip.setWrapText(true);

        VBox sigBox = new VBox(6, new Label("场景信号（键盘全局监听器按名称分发）"), sigTable, sigText, sigTpl);
        VBox slotBox = new VBox(6, new Label("场景槽（订阅信号后由引擎执行）"), slotTable, slotText, slotTpl,
                autoBar, pluginBar);
        VBox box = new VBox(12, sigBox, slotBox, tip);
        box.setPadding(new Insets(12));
        VBox.setVgrow(sigTable, Priority.ALWAYS);
        VBox.setVgrow(slotTable, Priority.ALWAYS);
        return new Tab("🔗 信号 / 槽（" + sigRows.size() + " / " + slotRows.size() + "）", box);
    }

    // =====================================================================
    // 快捷按钮（与节点属性窗口里的那排模板按钮同一套用法）
    // =====================================================================

    /** 快捷信号按钮：把一行式信号追加进场景信号表，并载入输入框方便继续改 */
    private static Button signalTemplate(String label, String line, GameScene scene,
                                         ObservableList<SignalDef> rows, TableView<SignalDef> table,
                                         TextField lineField, Runnable mark, EditorHub hub) {
        Button b = new Button(label);
        b.getStyleClass().add("tool-button");
        b.setTooltip(new javafx.scene.control.Tooltip("点一下加一条场景信号：" + line + "\n（同名会自动加序号）"));
        b.setOnAction(e -> {
            java.util.List<String> warns = new ArrayList<>();
            SignalDef def = SignalCodec.decodeSignal(line, warns);
            if (def == null) {
                Ui.warn(null, "模板无法解析", String.join("\n", warns));
                return;
            }
            def.setName(freeSignalName(scene, def.getName()));
            scene.signals().add(def);
            rows.setAll(scene.signals());
            table.getSelectionModel().selectLast();
            lineField.setText(SignalCodec.encode(def));
            mark.run();
            hub.notify("已新增场景信号：" + def.getName());
        });
        return b;
    }

    /** 快捷槽按钮：把一行式槽追加进场景槽表（点一下列表里就多一行） */
    private static Button slotTemplate(String label, String line, GameScene scene,
                                       ObservableList<SlotDef> rows, TableView<SlotDef> table,
                                       Runnable mark, EditorHub hub) {
        Button b = new Button(label);
        b.getStyleClass().add("tool-button");
        b.setTooltip(new javafx.scene.control.Tooltip("点一下加一条场景槽：" + line));
        b.setOnAction(e -> {
            java.util.List<String> warns = new ArrayList<>();
            SlotDef def = SignalCodec.decodeSlot(line, warns);
            if (def == null) {
                Ui.warn(null, "模板无法解析", String.join("\n", warns));
                return;
            }
            scene.slots().add(def);
            rows.setAll(scene.slots());
            table.getSelectionModel().selectLast();
            mark.run();
            hub.notify("已新增场景槽：" + def.getSignal() + " | " + def.getAction());
        });
        return b;
    }

    /** 自动信号按钮用的“起步槽”：先写一条 log，工程师改成自己的逻辑即可 */
    private static SlotDef subscribeAuto(com.studio.flow.AutoSignals.Def def) {
        String msg;
        if (def == com.studio.flow.AutoSignals.ENTER) {
            msg = "进入场景 @param(scene)（来自 @param(from)）";
        } else if (def == com.studio.flow.AutoSignals.LEAVE) {
            msg = "离开场景 @param(scene) → @param(to)";
        } else {
            msg = "上一幕 @param(scene) 已离开 → 本幕 @param(to)";
        }
        return new SlotDef(def.name(), "log", "", msg);
    }

    /** 场景信号重名时自动加序号，避免两个信号同名（同名会一起触发，容易踩坑） */
    private static String freeSignalName(GameScene scene, String want) {
        String base = want == null || want.isBlank() ? "新信号" : want.trim();
        String name = base;
        int n = 2;
        while (hasSignal(scene, name)) name = base + "_" + (n++);
        return name;
    }

    private static boolean hasSignal(GameScene scene, String name) {
        for (SignalDef s : scene.signals()) {
            if (s.getName().equals(name)) return true;
        }
        return false;
    }


    /** 供 Inspector 上的按钮/菜单使用：把当前场景的属性窗口打开 */
    static Map<String, String> snapshot(GameScene scene) {
        return scene == null ? new LinkedHashMap<>() : new LinkedHashMap<>(scene.props());
    }
}
