package com.studio.editor;

import com.studio.flow.SignalCodec;
import com.studio.flow.SignalDef;
import com.studio.flow.SlotDef;
import com.studio.flow.VarType;
import com.studio.model.GameOption;
import com.studio.model.GameProject;
import com.studio.model.GameScene;
import com.studio.model.NodeType;
import com.studio.model.SaveVarDef;
import com.studio.model.StoryNode;
import com.studio.ui.Ui;
import com.studio.util.Logs;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 节点“详情”编辑对话框。
 *
 * <p>所有字段均做<b>实时应用</b>：一旦修改，模型即刻更新并刷新画布
 * （所见即所得）；点【取消】时用快照还原，点【完成】时保持修改结果。</p>
 */
final class NodeDialogs {

    private NodeDialogs() { }

    // =====================================================================
    // 节点属性编辑
    // =====================================================================

    static void showNodeDialog(EditorHub hub, StoryNode node) {
        showEditDialog(hub, node, false);
    }

    /**
     * 编辑「新增节点模板」（右键“添加节点”生成新节点时的初值来源）。
     * 模板不属于任何场景，因此编辑它<b>不会</b>把工程标记为未保存。
     */
    static void showTemplateDialog(EditorHub hub, StoryNode node) {
        showEditDialog(hub, node, true);
    }

    private static void showEditDialog(EditorHub hub, StoryNode node, boolean templateMode) {
        if (node == null) return;
        StoryNode snapshot = node.copy();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle((templateMode ? "编辑新增节点模板 — " : "编辑节点属性 — ") + node.getType().display());
        dialog.setHeaderText(templateMode
                ? "✏️ 这里只决定右键“添加节点”生成的新节点初值：【完成】保存、【取消】还原"
                : "✏️ 修改后画布会实时刷新：【完成】保存修改、【取消】还原");

        ButtonType ok = new ButtonType("完成", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, cancel);

        GameProject project = hub.project();
        Runnable refresh = templateMode
                ? () -> hub.refreshInspector()   // 模板不在画布里，只需刷新检查器里的模板摘要
                : () -> {
                    hub.nodeChanged(node);
                    hub.setDirty();
                };

        // ---------- 表单 ----------
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(24);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(76);
        grid.getColumnConstraints().addAll(c1, c2);
        grid.setStyle(
                "-fx-background-color: #23242F;" +
                        "-fx-background-insets: 0;" +
                        "-fx-background-radius: 0;" +
                        "-fx-border-color: #23242F;" +
                        "-fx-border-width: 1;"
        );

        // 类型（下拉项来自 NodeType.values()，已自动包含新增的 TEXTBOX 文本框；
        // 切换联动“文本框专属字段”的监听在下方控件创建完成后统一注册）
        ComboBox<String> typeBox = new ComboBox<>();
        for (NodeType t : NodeType.values()) typeBox.getItems().add(t.icon() + " " + t.display());
        typeBox.getSelectionModel().select(node.getType().ordinal());
        addRow(grid, 0, "类型", typeBox, "改变类型将保留当前尺寸；选“⌨ 文本框”会出现多行/绑定变量字段");

        // 编号
        TextField idField = new TextField(node.getId());
        idField.textProperty().addListener((o, a, b) -> { node.setId(b); refresh.run(); });
        addRow(grid, 1, "编号 (id)", idField, "编辑器内标识，可留空");

        // 坐标与尺寸
        TextField xField = numField(node.getX());
        xField.textProperty().addListener((o, a, b) -> { if (parse(xField, v -> node.setX(v))) refresh.run(); });
        addRow(grid, 2, "坐标 X (x)", xField, "相对画布左上角");

        TextField yField = numField(node.getY());
        yField.textProperty().addListener((o, a, b) -> { if (parse(yField, v -> node.setY(v))) refresh.run(); });
        addRow(grid, 3, "坐标 Y (y)", yField, null);

        TextField wField = numField(node.getWidth());
        wField.textProperty().addListener((o, a, b) -> { if (parse(wField, v -> node.setWidth(Math.max(1, v)))) refresh.run(); });
        addRow(grid, 4, "宽度 (width)", wField, null);

        TextField hField = numField(node.getHeight());
        hField.textProperty().addListener((o, a, b) -> { if (parse(hField, v -> node.setHeight(Math.max(1, v)))) refresh.run(); });
        addRow(grid, 5, "高度 (height)", hField, null);

        // 素材路径
        TextField pathField = new TextField(node.getPath());
        pathField.textProperty().addListener((o, a, b) -> { node.setPath(b); refresh.run(); });
        Button pathPick = new Button("…选择素材");
        pathPick.setOnAction(e -> {
            String rel = AssetImport.pickAndImport(dialog.getOwner(), project, node, false);
            if (rel != null) pathField.setText(rel);
        });
        addRow(grid, 6, "图片/立绘 (path)", new HBox(6, pathField, pathPick), "双击选取文件会自动复制进 resources");

        // 视频（video）：设了就代替该节点的图片渲染（背景节点＝会动的背景图）
        TextField videoField = new TextField(node.getVideo());
        videoField.textProperty().addListener((o, a, b) -> { node.setVideo(b); refresh.run(); });
        Button videoPick = new Button("…选择视频");
        videoPick.setOnAction(e -> {
            String rel = pickAndImportVideo(dialog.getOwner(), project);
            if (rel != null) videoField.setText(rel);
        });
        Label videoHint = new Label("视频会代替该节点的图片渲染（背景节点＝会动的背景图）；留空则用图片。"
                + "也可用槽 @plugin(video) 在运行时切换。");
        videoHint.setWrapText(true);
        videoHint.getStyleClass().add("hint-text");
        addRow(grid, 7, "视频 (video)", new VBox(4, new HBox(6, videoField, videoPick), videoHint),
                "视频相对地图根目录（如 resources/video/opening.mp4）；自动复制进地图 resources/video");

        // 音频
        TextField audioField = new TextField(node.getAudio());
        audioField.textProperty().addListener((o, a, b) -> { node.setAudio(b); refresh.run(); });
        Button audioPick = new Button("…选择音频");
        audioPick.setOnAction(e -> {
            String rel = AssetImport.pickAndImport(dialog.getOwner(), project, node, true);
            if (rel != null) audioField.setText(rel);
        });
        addRow(grid, 8, "音频 (audio)", new HBox(6, audioField, audioPick), "音乐轨/音效文件");

        // 文字（可多段：独立一行的 --- 分隔，播放时点击对话自动切到下一段）
        TextArea textArea = new TextArea(node.getText());
        textArea.setWrapText(true);
        textArea.setPrefRowCount(5);
        textArea.textProperty().addListener((o, a, b) -> { node.setText(b); refresh.run(); });

        Button insertBreak = new Button("＋插入分页符(---)");
        insertBreak.getStyleClass().add("tool-button");
        insertBreak.setTooltip(new Tooltip("在光标处插入 --- ，把一段长台词拆成多段：播放时点击对话框自动切换"));
        insertBreak.setOnAction(e -> textArea.replaceSelection("\n---\n"));
        Label paraTip = new Label("💬 独立一行的 --- 分隔多段台词（点击对话自动切换）");
        paraTip.setStyle("-fx-text-fill: #8fe3ff; -fx-font-size: 11px;");

        Label markupHelp = new Label("📖 富文本标记说明");
        markupHelp.setStyle("-fx-text-fill: #7c81c9; -fx-cursor: hand; -fx-underline: true;");
        markupHelp.setOnMouseClicked(e -> Ui.info(null, "富文本标记 / 多段台词",
                "富文本：<b>加粗</b> / <i>斜体</i> / <u>下划线</u>\n"
                + "<color:#ffcc00>彩色</color> / <color:yellow>命名色</color>\n"
                + "<size:26>字号</size> / <br> 换行\n\n"
                + "多段台词：在 text 里用“独立一行的 --- ”把内容分成若干段，\n"
                + "播放时点击对话框即可自动切到下一段（脚本保存为 <<< … <<<）。"));
        HBox tips = new HBox(10, insertBreak, markupHelp, paraTip);
        tips.setAlignment(Pos.CENTER_LEFT);
        VBox textWrap = new VBox(4, textArea, tips);
        VBox.setVgrow(textArea, Priority.ALWAYS);
        // 文本框节点时标签改为“初始内容”（同一字段：text）
        Label textLabel = makeLabel("文字 (text)");
        addRow(grid, 9, textLabel, textWrap, "支持富文本与 --- 多段台词");
        addRow(grid, 18, "逐字显示 (typewriter)", buildTypewriterCombo(node, refresh),
                "对话默认逐字打字；此处可强制开/关（打字中点击对话=立即显示全文）");

        // 内联样式
        TextArea styleArea = new TextArea(node.getStyle());
        styleArea.setWrapText(true);
        styleArea.setPrefRowCount(2);
        styleArea.textProperty().addListener((o, a, b) -> { node.setStyle(b); refresh.run(); });
        addRow(grid, 10, "样式 (style)", styleArea, "内联 CSS，如 -fx-text-fill: white; -fx-font-size: 20px;");

        // 事件
        TextField eventField = new TextField(node.getEvent());
        eventField.textProperty().addListener((o, a, b) -> { node.setEvent(b); refresh.run(); });
        addRow(grid, 11, "事件 (event)", eventField, "插件 ID（如 minesweeper），按钮 action=event 时触发");

        // 目标场景 / 存档文件名（先声明，供下方 actionBox 监听器引用）
        TextField targetBox = new TextField(node.getTarget());
        targetBox.setPromptText("场景名 或 存档文件名(如 slot2)");
        targetBox.textProperty().addListener((o, a, b) -> { node.setTarget(b == null ? "" : b); refresh.run(); });

        // 动作（skip 跳过台词 / speed 加速 两个旧动作已移除：读取器不再支持，见 ReaderView）
        ComboBox<String> actionBox = new ComboBox<>();
        actionBox.getItems().addAll("（无）", "target 跳转场景",
                "save 写入存档(saves/)", "load 读取存档(saves/)", "event 运行插件");
        actionBox.getSelectionModel().select(actionIndexFor(node.getAction()));
        actionBox.valueProperty().addListener((o, a, b) -> {
            node.setAction(actionCodeFor(actionBox.getSelectionModel().getSelectedIndex()));
            targetBox.setDisable(!targetEnabled(node));
            refresh.run();
        });
        boolean needTarget = targetEnabled(node);
        Label targetLabel = new Label("目标 (target)");
        addRow(grid, 12, "动作 (action)", actionBox,
                "target=跳场景; save/load=存档文件名; event=运行插件；"
                        + "注：skip/speed 已移除，读取器不再支持这两个动作（旧地图里残留会走默认分支）");
        Label actionNote = makeLabel("⚠ skip（跳过台词）/ speed（加速）已从读取器移除，"
                + "需要类似效果请改用信号/槽或逻辑层。");
        actionNote.getStyleClass().add("hint-text");
        addRow(grid, 13, targetLabel.getText(), targetBox,
                "动作=target 填场景名；save/load 填存档文件名（留空=slot1.txt）；"
                        + "对话节点：此处填“对话结束后的下一个场景”（点击对话走完即跳转）");
        targetBox.setDisable(!needTarget);
        grid.add(actionNote, 0, 25, 2, 1);

        // 可见/字号/对齐/透明度
        CheckBox visibleBox = new CheckBox("可见");
        visibleBox.setSelected(node.isVisible());
        visibleBox.selectedProperty().addListener((o, a, b) -> { node.setVisible(b); refresh.run(); });
        addRow(grid, 14, "显示 (visible)", visibleBox, null);

        TextField fsField = numField(node.getFontSize() > 0 ? node.getFontSize() : 0);
        fsField.textProperty().addListener((o, a, b) -> {
            if (parse(fsField, v -> node.setFontSize(v))) refresh.run();
        });
        addRow(grid, 15, "字号 (fontSize)", fsField, "0 = 使用默认字号");

        ComboBox<String> alignBox = new ComboBox<>();
        alignBox.getItems().addAll("左对齐 left", "居中 center", "右对齐 right");
        alignBox.getSelectionModel().select(Math.max(0, switch (node.getAlign()) {
            case "center" -> 1;
            case "right" -> 2;
            default -> 0;
        }));
        alignBox.valueProperty().addListener((o, a, b) -> {
            String val = alignBox.getSelectionModel().getSelectedItem();
            node.setAlign(val.contains("center") ? "center" : val.contains("right") ? "right" : "left");
            refresh.run();
        });
        addRow(grid, 16, "对齐 (align)", alignBox, null);

        TextField opacityField = numField(node.getOpacity());
        opacityField.textProperty().addListener((o, a, b) -> {
            if (parse(opacityField, v -> node.setOpacity(Math.max(0.05, Math.min(1, v))))) refresh.run();
        });
        addRow(grid, 17, "透明度 (opacity)", opacityField, "0.05 ~ 1.0");

        // 过渡动画（引擎补间）
        TextField transitionField = new TextField(node.getTransition());
        transitionField.setPromptText("如 scale/opacity:300ms（留空=立即生效）");
        transitionField.textProperty().addListener((o, a, b) -> { node.setTransition(b); refresh.run(); });
        addRow(grid, 21, "过渡动画 (transition)", transitionField,
                "本节点属性被改变时做简单补间：支持 scale/opacity/rotation/x/y，"
                        + "格式 属性[/属性]:毫秒（省略毫秒默认 300）");

        // 层级 index（0 = 最底层，越大越靠上）：【完成】时统一应用，避免边打字边重排
        TextField indexField = new TextField(String.valueOf(node.getIndex()));
        indexField.setPromptText("0=最底层");
        indexField.textProperty().addListener((o, a, b) -> {
            if (b == null || b.trim().isEmpty()) {
                indexField.setStyle("");
                return;
            }
            try {
                Integer.parseInt(b.trim());
                indexField.setStyle("");
            } catch (NumberFormatException ex) {
                indexField.setStyle("-fx-border-color: #ff6b6b;");
            }
        });
        addRow(grid, 22, "层级 index", indexField,
                "0 = 最底层，数值越大越靠上；点【完成】时按此值调整本节点在本场景中的层级");

        // ---------- 文本框（TEXTBOX）专属字段 ----------
        CheckBox multilineBox = new CheckBox("多行文本");
        multilineBox.setSelected(node.isMultiline());
        multilineBox.selectedProperty().addListener((o, a, b) -> {
            node.setMultiline(b);
            refresh.run();
        });
        Label multiLabel = makeLabel("多行文本 (multiline)");
        addRow(grid, 23, multiLabel, multilineBox, "文本框节点专用：勾选后读取器渲染为多行输入框");

        ComboBox<String> bindBox = new ComboBox<>();
        bindBox.setEditable(true);
        bindBox.getItems().add("");
        if (project != null) {
            for (SaveVarDef d : project.option().saveVars()) bindBox.getItems().add(d.getName());
        }
        bindBox.setPromptText("存档变量名（留空=不绑定）");
        bindBox.setValue(node.getBind() == null ? "" : node.getBind());
        // 可编辑：允许手输尚未在 [option] 里声明的变量名
        bindBox.getEditor().textProperty().addListener((o, a, b) -> {
            node.setBind(b);
            refresh.run();
        });
        Label bindLabel = makeLabel("绑定变量 (bind)");
        addRow(grid, 24, bindLabel, bindBox,
                "文本框节点专用：玩家输入实时写入该存档变量；下拉项来自 [option] 的存档变量声明");

        // 类型联动：仅 TEXTBOX 显示/启用上述两个字段，其他类型整行隐藏
        Runnable syncTextBox = () -> {
            boolean tb = node.getType() == NodeType.TEXTBOX;
            textLabel.setText(tb ? "初始内容" : "文字 (text)");
            setRowVisible(tb, multiLabel, multilineBox, bindLabel, bindBox);
            multilineBox.setDisable(!tb);
            bindBox.setDisable(!tb);
        };
        syncTextBox.run();
        typeBox.valueProperty().addListener((o, a, b) -> {
            NodeType nt = NodeType.values()[Math.max(0, typeBox.getSelectionModel().getSelectedIndex())];
            node.setType(nt);
            syncTextBox.run();
            refresh.run();
        });

        // ---------- 信号列表（可增删；渲染层监听鼠标/键盘后发出） ----------
        javafx.collections.ObservableList<SignalDef> sigRows =
                javafx.collections.FXCollections.observableArrayList(node.signals());
        TableView<SignalDef> sigTable = new TableView<>(sigRows);
        sigTable.setPrefHeight(140);
        sigTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        sigTable.getColumns().addAll(
                sigCol("名称", 150, SignalDef::getName),
                sigCol("类型", 110, s -> s.getKind() == SignalDef.Kind.KEY ? "key 键盘" : "mouse 鼠标"),
                sigCol("按键 / 鼠标", 130, s -> s.getKind() == SignalDef.Kind.KEY ? s.getKey() : s.getMouse()),
                sigCol("相位", 80, s -> s.getKind() == SignalDef.Kind.KEY ? s.getKeyPhase() : ""),
                sigCol("附带参数", 190, s -> SignalCodec.encodeParams(s.params())));

        TextField sigLine = new TextField();
        sigLine.setPromptText("一行式：名称 | mouse|key | click/release/按键码 | press|release | 参数k=v");
        sigTable.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b != null) sigLine.setText(SignalCodec.encode(b));
        });
        Label sigCount = new Label();
        sigCount.setStyle("-fx-text-fill: #8fe3ff; -fx-font-size: 11px;");
        Runnable sigSync = () -> sigCount.setText("共 " + node.signals().size() + " 条信号");
        sigSync.run();

        Button sigAdd = new Button("＋ 新增信号");
        sigAdd.getStyleClass().add("tool-button");
        sigAdd.setOnAction(e -> {
            List<String> warns = new ArrayList<>();
            SignalDef d = SignalCodec.decodeSignal(sigLine.getText(), warns);
            if (d == null) {
                Ui.warn(dialog.getOwner(), "无法解析", String.join("\n", warns));
                return;
            }
            node.signals().add(d);
            sigRows.setAll(node.signals());
            sigTable.getSelectionModel().selectLast();
            sigSync.run();
            refresh.run();
        });
        Button sigApply = new Button("✔ 应用到选中信号");
        sigApply.getStyleClass().add("tool-button");
        sigApply.setOnAction(e -> {
            int i = sigTable.getSelectionModel().getSelectedIndex();
            if (i < 0) {
                Ui.warn(dialog.getOwner(), "未选中", "请先在上面的信号列表里选中一行。");
                return;
            }
            List<String> warns = new ArrayList<>();
            SignalDef d = SignalCodec.decodeSignal(sigLine.getText(), warns);
            if (d == null) {
                Ui.warn(dialog.getOwner(), "无法解析", String.join("\n", warns));
                return;
            }
            node.signals().set(i, d);
            sigRows.setAll(node.signals());
            sigSync.run();
            refresh.run();
        });
        Button sigDel = new Button("－ 删除选中信号");
        sigDel.getStyleClass().add("tool-button");
        sigDel.setOnAction(e -> {
            int i = sigTable.getSelectionModel().getSelectedIndex();
            if (i < 0) {
                Ui.warn(dialog.getOwner(), "未选中", "请先在上面的信号列表里选中一行。");
                return;
            }
            node.signals().remove(i);
            sigRows.setAll(node.signals());
            sigSync.run();
            refresh.run();
        });
        Button addClickSig = sigTemplate("＋点击信号", "点击 | mouse | click", node, sigRows, sigSync, refresh);
        Button addReleaseSig = sigTemplate("＋鼠标释放", "松开 | mouse | release", node, sigRows, sigSync, refresh);
        Button addKeySig = sigTemplate("＋按键信号(F)", "按键F | key | F | press", node, sigRows, sigSync, refresh);
        FlowPane sigTpl = new FlowPane(6, 4, sigAdd, sigApply, sigDel, addClickSig, addReleaseSig, addKeySig, sigCount);
        sigTpl.setAlignment(Pos.CENTER_LEFT);
        addRow(grid, 19, "信号 (signal)", new VBox(4, sigTable, sigLine, sigTpl),
                "格式：名称 | mouse|key | click/release/按键码 | press|release | 参数k=v,参数k=v"
                        + "（选中一行可载入下面的输入框修改）");

        // ---------- 槽列表（订阅信号并执行；call 动作转给逻辑层） ----------
        javafx.collections.ObservableList<SlotDef> slotRows =
                javafx.collections.FXCollections.observableArrayList(node.slots());
        TableView<SlotDef> slotTable = new TableView<>(slotRows);
        slotTable.setPrefHeight(170);
        slotTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        slotTable.getColumns().addAll(
                slotCol("订阅的信号", 130, SlotDef::getSignal),
                slotCol("动作", 130, SlotDef::getAction),
                slotCol("目标", 110, SlotDef::getTarget),
                slotCol("参数", 130, SlotDef::getArg),
                slotCol("附加参数", 190, s -> SignalCodec.encodeParams(s.params())));

        TextField slotLine = new TextField();
        slotLine.setPromptText("一行式：信号名 | 动作 | 目标 | 参数 | 附加参数");
        slotTable.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b != null) slotLine.setText(SignalCodec.encode(b));
        });
        Label slotCount = new Label();
        slotCount.setStyle("-fx-text-fill: #8fe3ff; -fx-font-size: 11px;");
        Runnable slotSync = () -> slotCount.setText("共 " + node.slots().size() + " 个槽");
        slotSync.run();

        Button slotAdd = new Button("＋ 新增槽");
        slotAdd.getStyleClass().add("tool-button");
        slotAdd.setOnAction(e -> {
            List<String> warns = new ArrayList<>();
            SlotDef d = SignalCodec.decodeSlot(slotLine.getText(), warns);
            if (d == null) {
                Ui.warn(dialog.getOwner(), "无法解析", String.join("\n", warns));
                return;
            }
            node.slots().add(d);
            slotRows.setAll(node.slots());
            slotTable.getSelectionModel().selectLast();
            slotSync.run();
            refresh.run();
        });
        Button slotApply = new Button("✔ 应用到选中槽");
        slotApply.getStyleClass().add("tool-button");
        slotApply.setOnAction(e -> {
            int i = slotTable.getSelectionModel().getSelectedIndex();
            if (i < 0) {
                Ui.warn(dialog.getOwner(), "未选中", "请先在上面的槽列表里选中一行。");
                return;
            }
            List<String> warns = new ArrayList<>();
            SlotDef d = SignalCodec.decodeSlot(slotLine.getText(), warns);
            if (d == null) {
                Ui.warn(dialog.getOwner(), "无法解析", String.join("\n", warns));
                return;
            }
            node.slots().set(i, d);
            slotRows.setAll(node.slots());
            slotSync.run();
            refresh.run();
        });
        Button slotDel = new Button("－ 删除选中槽");
        slotDel.getStyleClass().add("tool-button");
        slotDel.setOnAction(e -> {
            int i = slotTable.getSelectionModel().getSelectedIndex();
            if (i < 0) {
                Ui.warn(dialog.getOwner(), "未选中", "请先在上面的槽列表里选中一行。");
                return;
            }
            node.slots().remove(i);
            slotRows.setAll(node.slots());
            slotSync.run();
            refresh.run();
        });

        // 模板按钮：一键追加一条常用槽
        Button tplSet = slotTemplate("＋改样式槽", "点击 | set | @self | style | value=-fx-opacity:0.35;", node, slotRows, slotSync, refresh);
        Button tplEmit = slotTemplate("＋发信号槽", "点击 | emit | 目标节点id | 被触发的信号名", node, slotRows, slotSync, refresh);
        Button tplCall = slotTemplate("＋逻辑槽(call)", "点击 | call | | signallab | 说明=转给逻辑层", node, slotRows, slotSync, refresh);
        Button tplToggle = slotTemplate("＋显隐槽", "点击 | toggle | 目标节点id | visible", node, slotRows, slotSync, refresh);
        Button tplTransition = slotTemplate("＋过渡槽", "点击 | set | @self | scale | value=1.08 | transition=scale:300ms",
                node, slotRows, slotSync, refresh);
        Button tplPlugin = slotTemplate("＋插件槽", "点击 | @plugin(add) | @var(num1) | @double(1.05) | @var(num2)",
                node, slotRows, slotSync, refresh);
        tplPlugin.setTooltip(new Tooltip("调用自带插件。可用插件 ID：\n"
                + "算术 add / sub / mul / div / mod / pow / min / max / abs / round / floor / ceil / neg / set / inc / dec\n"
                + "逻辑 and / or / xor / not；比较 gt / lt / ge / le / eq / ne\n"
                + "示例含义：把 num1 + 1.05 的结果写回 num2"));
        Button tplExpr = slotTemplate("＋表达式槽", "点击 | set | @self | text | value=@var(变量名)",
                node, slotRows, slotSync, refresh);
        tplExpr.setTooltip(new Tooltip("表达式赋值：@var(变量名) 取存档变量值；"
                + "也可写 @int(@var(分数))、@double(1.05)、@str(@var(名字)) 或普通字面量"));
        FlowPane slotTpl = new FlowPane(6, 4, slotAdd, slotApply, slotDel, tplSet, tplEmit, tplToggle,
                tplTransition, tplCall, tplPlugin, tplExpr, slotCount);
        slotTpl.setAlignment(Pos.CENTER_LEFT);
        Label slotNote = new Label("槽按“信号名”全场景订阅：同一场景里同名信号的所有槽都会一起触发。"
                + "选中一行会把它的写法载入下面的输入框，改完点「✔ 应用到选中」。");
        slotNote.setWrapText(true);
        slotNote.getStyleClass().add("hint-text");
        Label pluginNote = new Label("可用插件 ID：算术 add/sub/mul/div/mod/pow/min/max/abs/round/floor/ceil/neg/set/inc/dec；"
                + "逻辑 and/or/xor/not；比较 gt/lt/ge/le/eq/ne");
        pluginNote.setWrapText(true);
        pluginNote.getStyleClass().add("hint-text");
        addRow(grid, 20, "槽 (slot)", new VBox(4, slotTable, slotLine, slotTpl, slotNote, pluginNote),
                "动作：set改属性 / toggle显隐 / emit发信号 / goto跳场景 / save / load / call逻辑 / log；"
                        + "目标可写 @self；@plugin(插件ID) 调用自带插件");

        // ---------- 保持原来的单列平铺样式；内容超出时用鼠标滚轮上下滚动 ----------
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(520);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPannable(true);

        dialog.setResizable(true);
        dialog.getDialogPane().setPrefSize(800, 640);
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().getStylesheets().addAll(
                Ui.class.getResource("/styles/studio.css").toExternalForm());

        dialog.showAndWait();
        if (dialog.getResult() != ok) {
            // 【取消】还原全部字段（含 index/multiline/bind/typewriter）
            restore(node, snapshot);
            if (!templateMode) hub.nodeChanged(node);
        } else {
            // 层级 index 在【完成】时才应用：节点在当前场景 → scene.moveTo；否则退化为 setIndex
            applyLayerIndex(hub, node, indexField.getText(), node.getIndex());
        }
        hub.refreshInspector();
    }

    /**
     * 应用“层级 index”：
     * 节点在当前场景里时用 {@link GameScene#moveTo}（会自动重排全部节点的 index），
     * 否则退化为 {@code node.setIndex(...)}（例如“新增节点模板”不属于任何场景）。
     */
    private static void applyLayerIndex(EditorHub hub, StoryNode node, String raw, int fallback) {
        int want = fallback;
        if (raw != null && !raw.trim().isEmpty()) {
            try {
                want = Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                want = fallback;
            }
        }
        GameScene scene = hub.scene();
        boolean moved = scene != null && scene.contains(node) && scene.moveTo(node, want);
        if (moved) {
            hub.nodesLayerChanged();   // 重排后重建画布与层级树
        } else {
            node.setIndex(want);
        }
    }

    /** 整行（标签 + 控件）一起显示/隐藏；隐藏时同时 setManaged(false) 以收起该行 */
    private static void setRowVisible(boolean visible, javafx.scene.Node... nodes) {
        for (javafx.scene.Node n : nodes) {
            if (n == null) continue;
            n.setVisible(visible);
            n.setManaged(visible);
        }
    }

    private static void restore(StoryNode node, StoryNode snapshot) {
        node.setType(snapshot.getType());
        node.setId(snapshot.getId());
        node.setX(snapshot.getX()); node.setY(snapshot.getY());
        node.setWidth(snapshot.getWidth()); node.setHeight(snapshot.getHeight());
        node.setPath(snapshot.getPath()); node.setAudio(snapshot.getAudio());
        node.setVideo(snapshot.getVideo());   // 视频也要还原，否则【取消】后会残留
        node.setText(snapshot.getText()); node.setStyle(snapshot.getStyle());
        node.setEvent(snapshot.getEvent()); node.setAction(snapshot.getAction());
        node.setTarget(snapshot.getTarget()); node.setVisible(snapshot.isVisible());
        node.setFontSize(snapshot.getFontSize()); node.setAlign(snapshot.getAlign());
        node.setOpacity(snapshot.getOpacity());
        node.setTransition(snapshot.getTransition());
        // 新增字段也要还原，否则【取消】后会残留
        node.setTypewriter(snapshot.getTypewriter());
        node.setIndex(snapshot.getIndex());
        node.setMultiline(snapshot.isMultiline());
        node.setBind(snapshot.getBind());
        node.signals().clear();
        node.signals().addAll(snapshot.signals());
        node.slots().clear();
        node.slots().addAll(snapshot.slots());
        node.extras().clear();
        node.extras().putAll(snapshot.extras());
    }

    // =====================================================================
    // 节点属性窗口：信号 / 槽 列表的列工厂与“模板按钮”
    // =====================================================================

    private static TableColumn<SignalDef, String> sigCol(String title, double width,
                                                         java.util.function.Function<SignalDef, String> getter) {
        TableColumn<SignalDef, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(cd -> new SimpleStringProperty(
                getter.apply(cd.getValue()) == null ? "" : getter.apply(cd.getValue())));
        return c;
    }

    private static TableColumn<SlotDef, String> slotCol(String title, double width,
                                                        java.util.function.Function<SlotDef, String> getter) {
        TableColumn<SlotDef, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(cd -> new SimpleStringProperty(
                getter.apply(cd.getValue()) == null ? "" : getter.apply(cd.getValue())));
        return c;
    }

    /** 模板按钮：把一行式信号追加到节点的信号列表，并刷新表格与计数 */
    private static Button sigTemplate(String label, String line, StoryNode node,
                                      javafx.collections.ObservableList<SignalDef> rows,
                                      Runnable sync, Runnable refresh) {
        Button b = new Button(label);
        b.getStyleClass().add("tool-button");
        b.setOnAction(e -> {
            List<String> warns = new ArrayList<>();
            SignalDef d = SignalCodec.decodeSignal(line, warns);
            if (d == null) {
                Ui.warn(null, "模板无法解析", String.join("\n", warns));
                return;
            }
            node.signals().add(d);
            rows.setAll(node.signals());
            sync.run();
            refresh.run();
        });
        return b;
    }

    /** 模板按钮：把一行式槽追加到节点的槽列表，并刷新表格与计数 */
    private static Button slotTemplate(String label, String line, StoryNode node,
                                       javafx.collections.ObservableList<SlotDef> rows,
                                       Runnable sync, Runnable refresh) {
        Button b = new Button(label);
        b.getStyleClass().add("tool-button");
        b.setOnAction(e -> {
            List<String> warns = new ArrayList<>();
            SlotDef d = SignalCodec.decodeSlot(line, warns);
            if (d == null) {
                Ui.warn(null, "模板无法解析", String.join("\n", warns));
                return;
            }
            node.slots().add(d);
            rows.setAll(node.slots());
            sync.run();
            refresh.run();
        });
        return b;
    }

    /** 需要填写 target 的动作：跳转场景 / 存档文件名 / 读档文件名 */
    public static boolean needsTargetValue(String action) {
        return "target".equals(action) || "save".equals(action) || "load".equals(action);
    }

    /** target 输入框是否可用：上述动作，或“对话类型”（target 表示对话结束后的下一个场景） */
    public static boolean targetEnabled(StoryNode node) {
        return needsTargetValue(node.getAction()) || node.getType() == NodeType.DIALOG;
    }

    /**
     * 供检查器复用：动作索引 → 动作代码。
     * <p>skip（跳过台词）/ speed（加速）已移除：读取器不再支持这两个动作，
     * 下拉框里也不再有对应项（旧脚本里残留的值会显示为“（无）”，
     * 但只有用户主动改动下拉框时才会被覆盖）。</p>
     */
    public static String actionCodeFor(int index) {
        return switch (index) {
            case 1 -> "target";
            case 2 -> "save";
            case 3 -> "load";
            case 4 -> "event";
            default -> "";
        };
    }

    /** 供检查器复用：动作代码 → 索引（含已废弃的 skip/speed → 0“（无）”） */
    public static int actionIndexFor(String action) {
        return switch (action == null ? "" : action) {
            case "target" -> 1;
            case "save" -> 2;
            case "load" -> 3;
            case "event" -> 4;
            default -> 0;
        };
    }

    private static TextField numField(double v) {
        TextField f = new TextField(StoryNode.trimDouble(v));
        f.setPrefWidth(90);
        return f;
    }

    /** 数字解析：成功回调并返回 true；失败红色提示返回 false */
    private static boolean parse(TextField f, Consumer<Double> apply) {
        try {
            double v = Double.parseDouble(f.getText().trim());
            apply.accept(v);
            f.setStyle("");
            return true;
        } catch (NumberFormatException e) {
            f.setStyle("-fx-border-color: #ff6b6b;");
            return false;
        }
    }

    // =====================================================================
    // 信号 / 槽 文本 ⇄ 列表（编辑器与检查器共用）
    // =====================================================================

    public static String signalsToText(java.util.List<SignalDef> list) {
        StringBuilder sb = new StringBuilder();
        for (SignalDef s : list) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(SignalCodec.encode(s));
        }
        return sb.toString();
    }

    public static String slotsToText(java.util.List<SlotDef> list) {
        StringBuilder sb = new StringBuilder();
        for (SlotDef s : list) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(SignalCodec.encode(s));
        }
        return sb.toString();
    }

    /** 把多行文本解析进信号列表（宽容：非法行忽略并记日志） */
    public static void applySignals(java.util.List<SignalDef> target, String text) {
        java.util.List<String> warnings = new java.util.ArrayList<>();
        java.util.List<SignalDef> parsed = new java.util.ArrayList<>();
        for (String line : text.split("\n", -1)) {
            String t = line.strip();
            if (t.isEmpty() || t.startsWith("#")) continue;
            SignalDef def = SignalCodec.decodeSignal(t, warnings);
            if (def != null) parsed.add(def);
        }
        target.clear();
        target.addAll(parsed);
        if (!warnings.isEmpty()) Logs.warn("信号解析: " + warnings);
    }

    /** 把多行文本解析进槽列表 */
    public static void applySlots(java.util.List<SlotDef> target, String text) {
        java.util.List<String> warnings = new java.util.ArrayList<>();
        java.util.List<SlotDef> parsed = new java.util.ArrayList<>();
        for (String line : text.split("\n", -1)) {
            String t = line.strip();
            if (t.isEmpty() || t.startsWith("#")) continue;
            SlotDef def = SignalCodec.decodeSlot(t, warnings);
            if (def != null) parsed.add(def);
        }
        target.clear();
        target.addAll(parsed);
        if (!warnings.isEmpty()) Logs.warn("槽解析: " + warnings);
    }

    /** 模板按钮：点击后在文本框末尾追加一行示例 */
    private static Button template(String label, TextArea area, String line) {
        Button b = new Button(label);
        b.getStyleClass().add("tool-button");
        b.setOnAction(e -> {
            String cur = area.getText();
            if (!cur.isEmpty() && !cur.endsWith("\n")) cur += "\n";
            area.setText(cur + line);
        });
        return b;
    }

    /**
     * 把源 GridPane 中指定行号的（标签+字段）节点搬到一个新的 GridPane。
     * （保留工具方法：供将来需要分组显示时使用）
     */
    private static GridPane regroup(GridPane src, int... rows) {
        java.util.Set<Integer> wanted = new java.util.HashSet<>();
        for (int r : rows) wanted.add(r);
        java.util.List<javafx.scene.Node> moved = new java.util.ArrayList<>();
        for (javafx.scene.Node child : new java.util.ArrayList<>(src.getChildren())) {
            Integer r = GridPane.getRowIndex(child);
            int row = r == null ? 0 : r;
            if (!wanted.contains(row)) continue;
            src.getChildren().remove(child);
            moved.add(child);
        }
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        g.setPadding(new Insets(10, 6, 10, 6));
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(26);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(74);
        g.getColumnConstraints().addAll(c1, c2);
        int outRow = 0;
        // 两个一组：标签(第0列) + 字段(第1列)
        for (int i = 0; i < moved.size(); i += 2) {
            javafx.scene.Node label = moved.get(i);
            javafx.scene.Node field = (i + 1 < moved.size()) ? moved.get(i + 1) : null;
            g.add(label, 0, outRow);
            if (field != null) {
                g.add(field, 1, outRow);
                GridPane.setHgrow(field, Priority.ALWAYS);
            }
            outRow++;
        }
        return g;
    }

    /** 逐字显示开关下拉：默认/开启/关闭 → null/TRUE/FALSE */
    private static ComboBox<String> buildTypewriterCombo(StoryNode node, Runnable refresh) {
        ComboBox<String> box = new ComboBox<>();
        box.getItems().addAll("默认（对话开启）", "开启", "关闭");
        Boolean v = node.getTypewriter();
        box.getSelectionModel().select(v == null ? 0 : (v ? 1 : 2));
        box.valueProperty().addListener((o, a, b) -> {
            node.setTypewriter(switch (box.getSelectionModel().getSelectedIndex()) {
                case 1 -> Boolean.TRUE;
                case 2 -> Boolean.FALSE;
                default -> null;
            });
            refresh.run();
        });
        return box;
    }

    private static Label makeLabel(String text) {
        Label lb = new Label(text);
        lb.getStyleClass().add("field-label");
        return lb;
    }

    private static void addRow(GridPane grid, int row, String label, javafx.scene.Node field, String tip) {
        addRow(grid, row, makeLabel(label), field, tip);
    }

    /** 允许传入已有 Label（便于运行时改文案，如“文字 text”→“初始内容”） */
    private static void addRow(GridPane grid, int row, Label lb, javafx.scene.Node field, String tip) {
        grid.add(lb, 0, row);
        if (tip != null && !tip.isBlank()) {
//            field.setTooltip(new Tooltip(tip));
            Tooltip.install(field, new Tooltip(tip));
        }
        grid.add(field, 1, row);
        GridPane.setHgrow(field, Priority.ALWAYS);
    }

    // =====================================================================
    // 视频素材导入（节点属性窗口与右侧检查器共用）
    // =====================================================================

    /**
     * 挑选视频素材并复制进地图的 {@code resources/video} 目录，
     * 返回“相对地图根目录”的引用路径（形如 {@code resources/video/opening.mp4}）；取消/失败返回 null。
     *
     * <p>说明：{@link AssetImport#pickAndImport} 只有“图片/音频”两类（目标目录分别是
     * resources/images、resources/audio），视频是新增类别，且不改动 AssetImport，
     * 因此这里按它的同一套做法单列实现；文件本来就在地图内时同样直接引用。</p>
     */
    static String pickAndImportVideo(javafx.stage.Window owner, GameProject project) {
        if (project == null || project.rootDir() == null) return null;
        File root = project.rootDir();

        FileChooser fc = new FileChooser();
        fc.setTitle("选择要导入的视频文件（自动复制进地图 resources/video 目录）");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("视频文件",
                "*.mp4", "*.m4v", "*.flv", "*.avi", "*.mov", "*.mkv", "*.webm", "*.wmv"));
        File picked = fc.showOpenDialog(owner);
        if (picked == null) return null;

        try {
            File canonical = picked.getCanonicalFile();
            File rootCanonical = root.getCanonicalFile();
            if (canonical.getPath().startsWith(rootCanonical.getPath())) {
                // 素材本来就在地图内：直接引用为相对路径
                return canonical.getPath().substring(rootCanonical.getPath().length())
                        .replace('\\', '/').replaceFirst("^/+", "");
            }
            File destDir = new File(root, "resources/video");
            if (!destDir.exists() && !destDir.mkdirs()) {
                throw new IOException("无法创建素材目录: " + destDir);
            }
            Path target = uniqueVideoTarget(destDir.toPath(), picked.getName());
            Files.copy(picked.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            String rel = "resources/video/" + target.getFileName();
            Logs.info("视频素材已导入: " + picked + " → " + rel);
            return rel;
        } catch (IOException e) {
            Ui.error(owner, "导入失败", "复制视频素材失败: " + e.getMessage(), e);
            return null;
        }
    }

    /** 目标重名时追加 _1/_2 …（与 AssetImport.uniqueTarget 行为一致） */
    private static Path uniqueVideoTarget(Path dir, String name) {
        Path target = dir.resolve(name);
        if (!Files.exists(target)) return target;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String suffix = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; ; i++) {
            Path t = dir.resolve(base + "_" + i + suffix);
            if (!Files.exists(t)) return t;
        }
    }

    // =====================================================================
    // [option] 全局设置对话框
    // =====================================================================

    static void showOptionDialog(EditorHub hub) {
        if (hub.project() == null) return;
        var option = hub.project().option();
        // 对话框是“边改边生效”的，所以在打开时先记一步快照（【取消】/直接关闭也能整体撤销）
        hub.pushUndo("修改地图全局设置");
        Runnable mark = () -> {
            hub.optionChanged();
            hub.setDirty();
        };

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("地图全局设置 [option]");
        dialog.setHeaderText("⚙ 初始场景 / 背景色 / 音量 / 打字机速度 / 存档变量");
        ButtonType okType = new ButtonType("完成", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(okType);
        dialog.getDialogPane().setPrefWidth(700);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(14));
        ColumnConstraints oc1 = new ColumnConstraints();
        oc1.setMinWidth(180);
        ColumnConstraints oc2 = new ColumnConstraints(260);   // 值列留宽一点，色值/下拉才不会显示不全
        oc2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(oc1, oc2);

        ComboBox<String> initial = new ComboBox<>();
        initial.getItems().addAll(hub.project().scenes().keySet());
        if (hub.project().hasScene(option.initialScene())) {
            initial.getSelectionModel().select(option.initialScene());
        } else if (hub.project().firstScene() != null) {
            initial.getSelectionModel().select(hub.project().firstScene().getName());
        }
        initial.setMaxWidth(Double.MAX_VALUE);    // 撑满值列：长场景名不会被截断
        initial.setPrefWidth(240);
        initial.valueProperty().addListener((o, a, b) -> {
            if (b != null) { option.setInitialScene(b); mark.run(); }
        });
        grid.add(makeLabel("初始场景 initialScene"), 0, 0);
        grid.add(initial, 1, 0);

        javafx.scene.control.ColorPicker color = new javafx.scene.control.ColorPicker();
        try {
            color.setValue(javafx.scene.paint.Color.web(option.background()));
        } catch (IllegalArgumentException e) {
            color.setValue(javafx.scene.paint.Color.web("#0d0f1c"));
        }
        color.valueProperty().addListener((o, a, b) -> {
            if (b != null) {
                option.setBackground(String.format("#%02x%02x%02x",
                        (int) Math.round(b.getRed() * 255),
                        (int) Math.round(b.getGreen() * 255),
                        (int) Math.round(b.getBlue() * 255)));
                mark.run();
            }
        });
        color.setMaxWidth(Double.MAX_VALUE);      // 撑满值列，十六进制色值完整可见
        color.setPrefWidth(240);
        grid.add(makeLabel("背景色 background"), 0, 1);
        grid.add(color, 1, 1);

        javafx.scene.control.Slider volume = new javafx.scene.control.Slider(0, 100, option.volume() * 100);
        Label volumeVal = new Label((int) Math.round(option.volume() * 100) + "%");
        volume.valueProperty().addListener((o, a, b) -> {
            option.setVolume(b.doubleValue() / 100.0);
            volumeVal.setText((int) Math.round(b.doubleValue()) + "%");
            mark.run();
        });
        HBox volBox = new HBox(10, volume, volumeVal);
        HBox.setHgrow(volume, Priority.ALWAYS);
        grid.add(makeLabel("全局音量 volume"), 0, 2);
        grid.add(volBox, 1, 2);

        javafx.scene.control.Slider speed = new javafx.scene.control.Slider(0, 60, option.typewriterSpeed());
        Label speedVal = new Label(StoryNode.trimDouble(option.typewriterSpeed()) + " ms/字");
        speed.valueProperty().addListener((o, a, b) -> {
            option.setTypewriterSpeed(Math.round(b.doubleValue() * 10.0) / 10.0);
            speedVal.setText(StoryNode.trimDouble(option.typewriterSpeed()) + " ms/字");
            mark.run();
        });
        HBox speedBox = new HBox(10, speed, speedVal);
        HBox.setHgrow(speed, Priority.ALWAYS);
        grid.add(makeLabel("打字机速度 (0=关闭逐字)"), 0, 3);
        grid.add(speedBox, 1, 3);

        Label tip = new Label("提示：读取器按此背景色铺底；初始场景决定播放起点。"
                + "　存档变量在信号/槽里用 @var(名称) 读写。");
        tip.getStyleClass().add("hint-text");
        tip.setWrapText(true);
        grid.add(tip, 0, 4, 2, 1);

        // ---------- 存档变量（[option] 里的 savevar 列表） ----------
        grid.add(buildSaveVarsSection(hub, option, dialog, mark, okType), 0, 5, 2, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getStylesheets().addAll(
                Ui.class.getResource("/styles/studio.css").toExternalForm());
        dialog.showAndWait();
    }

    // =====================================================================
    // [option] 存档变量列表（三列：名称 / 类型 / 初值）
    // =====================================================================

    /**
     * 「存档变量」区域：TableView + 增删按钮。
     * <p>表格编辑的是一份<b>草稿副本</b>，只有点【完成】并且
     * {@link GameOption#validateSaveVars()} 通过时才写回 {@code project.option()}，
     * 保证非法数据（重名/空名/含空格竖线）不会进入工程。</p>
     */
    private static VBox buildSaveVarsSection(EditorHub hub, GameOption option, Dialog<Void> dialog,
                                             Runnable mark, ButtonType okType) {
        // 草稿：从当前工程的存档变量复制一份，编辑期间不动工程
        javafx.collections.ObservableList<SaveVarDef> draft =
                javafx.collections.FXCollections.observableArrayList();
        for (SaveVarDef d : option.saveVars()) draft.add(d.copy());

        TableView<SaveVarDef> table = new TableView<>(draft);
        table.setEditable(true);
        table.setPrefHeight(170);
        table.setPrefWidth(600);
        // 用 CONSTRAINED_RESIZE_POLICY（而不是 JavaFX 20+ 才有的 CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN）：
        // 主仓库编译目标是 JavaFX 17，用新 API 会编不过（同步脚本的编译校验会拦住）。
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label placeholder = new Label("还没有存档变量，点下面的「＋ 添加变量」新增。");
        placeholder.getStyleClass().add("hint-text");
        table.setPlaceholder(placeholder);

        // 名称（可直接输入，内部会 trim；重名/空名在【完成】时被拦下）
        TableColumn<SaveVarDef, String> nameCol = new TableColumn<>("名称");
        nameCol.setPrefWidth(200);
        nameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getName()));
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nameCol.setOnEditCommit(e -> {
            SaveVarDef d = e.getRowValue();
            if (d == null) return;
            d.setName(e.getNewValue());
            table.refresh();
        });

        // 类型（ComboBox<VarType>：int/long/double/bool/str）
        // 说明：单元格里放的是“常驻下拉框”，点一下就能选类型；
        //      这里刻意不在选中时同步 table.refresh()（那会重建单元格、把展开中的弹层顶掉），
        //      也只在值确实不同才 setValue（否则交互中会把用户的选择重置回去）。
        TableColumn<SaveVarDef, VarType> typeCol = new TableColumn<>("类型（点此选择）");
        typeCol.setPrefWidth(170);
        typeCol.setSortable(false);
        typeCol.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().getType()));
        typeCol.setCellFactory(col -> new TableCell<SaveVarDef, VarType>() {
            private final ComboBox<VarType> box = new ComboBox<>();

            {
                box.getItems().addAll(VarType.values());
                box.setConverter(new javafx.util.StringConverter<VarType>() {
                    @Override
                    public String toString(VarType t) {
                        return t == null ? "" : t.display() + "(" + t.code() + ")";
                    }

                    @Override
                    public VarType fromString(String s) {
                        return VarType.from(s);
                    }
                });
                box.setMaxWidth(Double.MAX_VALUE);
                box.setPrefWidth(160);
                box.setVisibleRowCount(8);
                box.valueProperty().addListener((o, a, b) -> {
                    if (b == null || getTableRow() == null) return;
                    SaveVarDef d = getTableRow().getItem();
                    if (d == null || d.getType() == b) return;   // 单元格刷新导致的“同值”不处理
                    d.setType(b);
                    d.setInitial(b.cast(d.getInitial()));        // 换类型时把初值规范化
                    // 初值列需要重画，但推迟到下一帧：此刻下拉弹层可能还开着，直接 refresh 会把它关掉
                    javafx.application.Platform.runLater(table::refresh);
                });
            }

            @Override
            protected void updateItem(VarType v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                VarType cur = getTableRow().getItem().getType();
                if (!box.isShowing() && box.getValue() != cur) {
                    box.setValue(cur);
                }
                setGraphic(box);
            }
        });

        // 初值（可直接输入，setInitial 会按声明类型强制转换）
        TableColumn<SaveVarDef, String> initCol = new TableColumn<>("初值");
        initCol.setPrefWidth(240);
        initCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getInitial()));
        initCol.setCellFactory(TextFieldTableCell.forTableColumn());
        initCol.setOnEditCommit(e -> {
            SaveVarDef d = e.getRowValue();
            if (d == null) return;
            d.setInitial(e.getNewValue());
            table.refresh();
        });

        table.getColumns().add(nameCol);
        table.getColumns().add(typeCol);
        table.getColumns().add(initCol);

        Button addVar = new Button("＋ 添加变量");
        addVar.getStyleClass().add("tool-button");
        addVar.setOnAction(ev -> {
            int k = draft.size() + 1;
            String name = "新变量" + k;
            while (nameUsed(draft, name)) name = "新变量" + (++k);
            draft.add(new SaveVarDef(name, VarType.INT, "0"));
            table.getSelectionModel().selectLast();
        });

        Button delVar = new Button("－ 删除选中");
        delVar.getStyleClass().add("tool-button");
        delVar.setOnAction(ev -> {
            int i = table.getSelectionModel().getSelectedIndex();
            if (i < 0) {
                Ui.warn(dialog.getOwner(), "未选中", "请先在表格里选中一行要删除的存档变量。");
                return;
            }
            draft.remove(i);
        });

        HBox btns = new HBox(8, addVar, delVar);
        btns.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("💾 存档变量（[option] savevar）");
        title.getStyleClass().add("section-title");
        Label note = new Label("这些变量会随存档保存；在信号/槽里用 @var(名称) 读写，初值与类型在读取器里生效。");
        note.setWrapText(true);
        note.getStyleClass().add("hint-text");

        // 【完成】时先校验再写回；不通过就弹提示并保持对话框打开，工程数据不变
        javafx.scene.Node okBtn = dialog.getDialogPane().lookupButton(okType);
        if (okBtn == null) {
            // 兜底：个别 JavaFX 版本在对话框初次布局前取不到按钮，这里按文案再找一次
            for (javafx.scene.Node n : dialog.getDialogPane().lookupAll(".button")) {
                if (n instanceof Button b && okType.getText().equals(b.getText())) {
                    okBtn = n;
                    break;
                }
            }
        }
        if (okBtn == null) {
            Logs.warn("未找到 [option] 对话框的【完成】按钮，存档变量校验将被跳过");
        } else {
            okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
                String problem = writeBackSaveVars(option, draft);
                if (!problem.isEmpty()) {
                    Ui.warn(dialog.getOwner(), "存档变量有误",
                            problem + "\n\n（未写入工程，请修改后再点【完成】；重名 / 空名 / 含空格或竖线的名字都不允许）");
                    ev.consume();   // 拦截关闭，便于原地修改
                    return;
                }
                mark.run();         // 合法 → 写回并标记未保存
            });
        }

        VBox box = new VBox(6, title, table, btns, note);
        return box;
    }

    /** 名字是否已被草稿里的变量占用 */
    private static boolean nameUsed(List<SaveVarDef> draft, String name) {
        for (SaveVarDef d : draft) {
            if (d != null && name.equals(d.getName())) return true;
        }
        return false;
    }

    /**
     * 把草稿写回 {@code option.saveVars()}：临时写入 → 用
     * {@link GameOption#validateSaveVars()} 校验 → 不通过则整体还原并返回问题描述。
     *
     * @return 空串表示写入成功；非空为错误提示（此时工程数据保持原样）
     */
    private static String writeBackSaveVars(GameOption option, List<SaveVarDef> draft) {
        List<SaveVarDef> before = new ArrayList<>();
        for (SaveVarDef d : option.saveVars()) before.add(d.copy());

        option.saveVars().clear();
        for (SaveVarDef d : draft) option.saveVars().add(d == null ? new SaveVarDef() : d.copy());
        String problem = option.validateSaveVars();
        if (!problem.isEmpty()) {
            option.saveVars().clear();
            option.saveVars().addAll(before);   // 还原：非法数据绝不留在工程里
            return problem;
        }
        return "";
    }
}
