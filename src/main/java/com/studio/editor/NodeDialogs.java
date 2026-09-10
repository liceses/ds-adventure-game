package com.studio.editor;

import com.studio.flow.SignalCodec;
import com.studio.flow.SignalDef;
import com.studio.flow.SlotDef;
import com.studio.model.GameProject;
import com.studio.model.NodeType;
import com.studio.model.StoryNode;
import com.studio.ui.Ui;
import com.studio.util.Logs;
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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

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
        StoryNode snapshot = node.copy();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("编辑节点属性 — " + node.getType().display());
        dialog.setHeaderText("✏️ 修改后画布会实时刷新：【完成】保存修改、【取消】还原");

        ButtonType ok = new ButtonType("完成", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, cancel);

        GameProject project = hub.project();
        Runnable refresh = () -> {
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

        // 类型
        ComboBox<String> typeBox = new ComboBox<>();
        for (NodeType t : NodeType.values()) typeBox.getItems().add(t.icon() + " " + t.display());
        typeBox.getSelectionModel().select(node.getType().ordinal());
        typeBox.valueProperty().addListener((o, a, b) -> {
            NodeType nt = NodeType.values()[Math.max(0, typeBox.getSelectionModel().getSelectedIndex())];
            node.setType(nt);
            refresh.run();
        });
        addRow(grid, 0, "类型", typeBox, "改变类型将保留当前尺寸");

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

        // 音频
        TextField audioField = new TextField(node.getAudio());
        audioField.textProperty().addListener((o, a, b) -> { node.setAudio(b); refresh.run(); });
        Button audioPick = new Button("…选择音频");
        audioPick.setOnAction(e -> {
            String rel = AssetImport.pickAndImport(dialog.getOwner(), project, node, true);
            if (rel != null) audioField.setText(rel);
        });
        addRow(grid, 7, "音频 (audio)", new HBox(6, audioField, audioPick), "音乐轨/音效文件");

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
        addRow(grid, 8, "文字 (text)", textWrap, "支持富文本与 --- 多段台词");
        addRow(grid, 17, "逐字显示 (typewriter)", buildTypewriterCombo(node, refresh),
                "对话默认逐字打字；此处可强制开/关（打字中点击对话=立即显示全文）");

        // 内联样式
        TextArea styleArea = new TextArea(node.getStyle());
        styleArea.setWrapText(true);
        styleArea.setPrefRowCount(2);
        styleArea.textProperty().addListener((o, a, b) -> { node.setStyle(b); refresh.run(); });
        addRow(grid, 9, "样式 (style)", styleArea, "内联 CSS，如 -fx-text-fill: white; -fx-font-size: 20px;");

        // 事件
        TextField eventField = new TextField(node.getEvent());
        eventField.textProperty().addListener((o, a, b) -> { node.setEvent(b); refresh.run(); });
        addRow(grid, 10, "事件 (event)", eventField, "插件 ID（如 minesweeper），按钮 action=event 时触发");

        // 目标场景 / 存档文件名（先声明，供下方 actionBox 监听器引用）
        TextField targetBox = new TextField(node.getTarget());
        targetBox.setPromptText("场景名 或 存档文件名(如 slot2)");
        targetBox.textProperty().addListener((o, a, b) -> { node.setTarget(b == null ? "" : b); refresh.run(); });

        // 动作
        ComboBox<String> actionBox = new ComboBox<>();
        actionBox.getItems().addAll("（无）", "target 跳转场景", "skip 跳过台词",
                "save 写入存档(saves/)", "load 读取存档(saves/)", "speed 加速", "event 运行插件");
        actionBox.getSelectionModel().select(actionIndexFor(node.getAction()));
        actionBox.valueProperty().addListener((o, a, b) -> {
            node.setAction(actionCodeFor(actionBox.getSelectionModel().getSelectedIndex()));
            targetBox.setDisable(!targetEnabled(node));
            refresh.run();
        });
        boolean needTarget = targetEnabled(node);
        Label targetLabel = new Label("目标 (target)");
        addRow(grid, 11, "动作 (action)", actionBox,
                "target=跳场景; save/load=存档文件名; event=运行插件");
        addRow(grid, 12, targetLabel.getText(), targetBox,
                "动作=target 填场景名；save/load 填存档文件名（留空=slot1.txt）；"
                        + "对话节点：此处填“对话结束后的下一个场景”（点击对话走完即跳转）");
        targetBox.setDisable(!needTarget);

        // 可见/字号/对齐/透明度
        CheckBox visibleBox = new CheckBox("可见");
        visibleBox.setSelected(node.isVisible());
        visibleBox.selectedProperty().addListener((o, a, b) -> { node.setVisible(b); refresh.run(); });
        addRow(grid, 13, "显示 (visible)", visibleBox, null);

        TextField fsField = numField(node.getFontSize() > 0 ? node.getFontSize() : 0);
        fsField.textProperty().addListener((o, a, b) -> {
            if (parse(fsField, v -> node.setFontSize(v))) refresh.run();
        });
        addRow(grid, 14, "字号 (fontSize)", fsField, "0 = 使用默认字号");

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
        addRow(grid, 15, "对齐 (align)", alignBox, null);

        TextField opacityField = numField(node.getOpacity());
        opacityField.textProperty().addListener((o, a, b) -> {
            if (parse(opacityField, v -> node.setOpacity(Math.max(0.05, Math.min(1, v))))) refresh.run();
        });
        addRow(grid, 16, "透明度 (opacity)", opacityField, "0.05 ~ 1.0");

        // 过渡动画（引擎补间）
        TextField transitionField = new TextField(node.getTransition());
        transitionField.setPromptText("如 scale/opacity:300ms（留空=立即生效）");
        transitionField.textProperty().addListener((o, a, b) -> { node.setTransition(b); refresh.run(); });
        addRow(grid, 20, "过渡动画 (transition)", transitionField,
                "本节点属性被改变时做简单补间：支持 scale/opacity/rotation/x/y，"
                        + "格式 属性[/属性]:毫秒（省略毫秒默认 300）");

        // ---------- 信号（可重复行；渲染层监听鼠标/键盘后发出） ----------
        TextArea signalsArea = new TextArea(signalsToText(node.signals()));
        signalsArea.setWrapText(true);
        signalsArea.setPrefRowCount(2);
        Label sigCount = new Label();
        signalsArea.textProperty().addListener((o, a, b) -> {
            applySignals(node.signals(), b);
            sigCount.setText("已识别 " + node.signals().size() + " 条信号");
            refresh.run();
        });
        sigCount.setText("已识别 " + node.signals().size() + " 条信号");
        sigCount.setStyle("-fx-text-fill: #8fe3ff; -fx-font-size: 11px;");
        Button addClickSig = template("＋点击信号", signalsArea, "点击 | mouse | click");
        Button addReleaseSig = template("＋鼠标释放", signalsArea, "松开 | mouse | release");
        Button addKeySig = template("＋按键信号(F)", signalsArea, "按键F | key | F | press");
        HBox sigTpl = new HBox(6, addClickSig, addReleaseSig, addKeySig, sigCount);
        sigTpl.setAlignment(Pos.CENTER_LEFT);
        addRow(grid, 18, "信号 (signal)", new VBox(4, signalsArea, sigTpl),
                "格式：名称 | mouse|key | click/release/按键码 | press|release | 参数k=v,参数k=v");

        // ---------- 槽（订阅信号并执行；call 动作转给逻辑层） ----------
        TextArea slotsArea = new TextArea(slotsToText(node.slots()));
        slotsArea.setWrapText(true);
        slotsArea.setPrefRowCount(3);
        Label slotCount = new Label();
        slotsArea.textProperty().addListener((o, a, b) -> {
            applySlots(node.slots(), b);
            slotCount.setText("已识别 " + node.slots().size() + " 个槽");
            refresh.run();
        });
        slotCount.setText("已识别 " + node.slots().size() + " 个槽");
        slotCount.setStyle("-fx-text-fill: #8fe3ff; -fx-font-size: 11px;");
        Button tplSet = template("＋改样式槽", slotsArea, "点击 | set | @self | style | value=-fx-opacity:0.35;");
        Button tplEmit = template("＋发信号槽", slotsArea, "点击 | emit | 目标节点id | 被触发的信号名");
        Button tplCall = template("＋逻辑槽(call)", slotsArea, "点击 | call | | signallab | 说明=转给逻辑层");
        Button tplToggle = template("＋显隐槽", slotsArea, "点击 | toggle | 目标节点id | visible");
        Button tplTransition = template("＋过渡槽", slotsArea,
                "点击 | set | @self | scale | value=1.08 | transition=scale:300ms");
        HBox slotTpl = new HBox(6, tplSet, tplEmit, tplToggle, tplTransition, tplCall, slotCount);
        slotTpl.setAlignment(Pos.CENTER_LEFT);
        addRow(grid, 19, "槽 (slot)", new VBox(4, slotsArea, slotTpl),
                "动作：set改属性 / toggle显隐 / emit发信号 / goto跳场景 / save / load / call逻辑 / log；目标可写 @self");

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
            restore(node, snapshot);
            hub.nodeChanged(node);
        }
        hub.refreshInspector();
    }

    private static void restore(StoryNode node, StoryNode snapshot) {
        node.setType(snapshot.getType());
        node.setId(snapshot.getId());
        node.setX(snapshot.getX()); node.setY(snapshot.getY());
        node.setWidth(snapshot.getWidth()); node.setHeight(snapshot.getHeight());
        node.setPath(snapshot.getPath()); node.setAudio(snapshot.getAudio());
        node.setText(snapshot.getText()); node.setStyle(snapshot.getStyle());
        node.setEvent(snapshot.getEvent()); node.setAction(snapshot.getAction());
        node.setTarget(snapshot.getTarget()); node.setVisible(snapshot.isVisible());
        node.setFontSize(snapshot.getFontSize()); node.setAlign(snapshot.getAlign());
        node.setOpacity(snapshot.getOpacity());
        node.setTransition(snapshot.getTransition());
        node.signals().clear();
        node.signals().addAll(snapshot.signals());
        node.slots().clear();
        node.slots().addAll(snapshot.slots());
        node.extras().clear();
        node.extras().putAll(snapshot.extras());
    }

    /** 需要填写 target 的动作：跳转场景 / 存档文件名 / 读档文件名 */
    public static boolean needsTargetValue(String action) {
        return "target".equals(action) || "save".equals(action) || "load".equals(action);
    }

    /** target 输入框是否可用：上述动作，或“对话类型”（target 表示对话结束后的下一个场景） */
    public static boolean targetEnabled(StoryNode node) {
        return needsTargetValue(node.getAction()) || node.getType() == NodeType.DIALOG;
    }

    /** 供检查器复用：动作索引 → 动作代码 */
    public static String actionCodeFor(int index) {
        return switch (index) {
            case 1 -> "target";
            case 2 -> "skip";
            case 3 -> "save";
            case 4 -> "load";
            case 5 -> "speed";
            case 6 -> "event";
            default -> "";
        };
    }

    /** 供检查器复用：动作代码 → 索引 */
    public static int actionIndexFor(String action) {
        return switch (action == null ? "" : action) {
            case "target" -> 1;
            case "skip" -> 2;
            case "save" -> 3;
            case "load" -> 4;
            case "speed" -> 5;
            case "event" -> 6;
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

    private static void addRow(GridPane grid, int row, String label, javafx.scene.Node field, String tip) {
        Label lb = new Label(label);
        lb.getStyleClass().add("field-label");
        grid.add(lb, 0, row);
        if (tip != null && !tip.isBlank()) {
//            field.setTooltip(new Tooltip(tip));
            Tooltip.install(field, new Tooltip(tip));
        }
        grid.add(field, 1, row);
        GridPane.setHgrow(field, Priority.ALWAYS);
    }

    // =====================================================================
    // [option] 全局设置对话框
    // =====================================================================

    static void showOptionDialog(EditorHub hub) {
        if (hub.project() == null) return;
        var option = hub.project().option();
        Runnable mark = () -> {
            hub.optionChanged();
            hub.setDirty();
        };

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("地图全局设置 [option]");
        dialog.setHeaderText("⚙ 初始场景 / 背景色 / 音量 / 打字机速度");
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("完成", ButtonBar.ButtonData.OK_DONE));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(14));
        grid.getColumnConstraints().addAll(new ColumnConstraints(), new ColumnConstraints(220));

        ComboBox<String> initial = new ComboBox<>();
        initial.getItems().addAll(hub.project().scenes().keySet());
        if (hub.project().hasScene(option.initialScene())) {
            initial.getSelectionModel().select(option.initialScene());
        } else if (hub.project().firstScene() != null) {
            initial.getSelectionModel().select(hub.project().firstScene().getName());
        }
        initial.valueProperty().addListener((o, a, b) -> {
            if (b != null) { option.setInitialScene(b); mark.run(); }
        });
        grid.add(new Label("初始场景 initialScene"), 0, 0);
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
        grid.add(new Label("背景色 background"), 0, 1);
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
        grid.add(new Label("全局音量 volume"), 0, 2);
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
        grid.add(new Label("打字机速度 (0=关闭逐字)"), 0, 3);
        grid.add(speedBox, 1, 3);

        Label tip = new Label("提示：读取器按此背景色铺底；初始场景决定播放起点。");
        tip.getStyleClass().add("field-label");
        grid.add(tip, 0, 4, 2, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getStylesheets().addAll(
                Ui.class.getResource("/styles/studio.css").toExternalForm());
        dialog.showAndWait();
    }
}
