package com.studio.editor;

import com.studio.model.GameProject;
import com.studio.model.GameScene;
import com.studio.model.NodeType;
import com.studio.model.StoryNode;
import com.studio.ui.Ui;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 左侧栏：场景 / 节点层级树；右侧栏：属性检查器。
 * 二者都只依赖 {@link EditorHub}，由 EditorPane 装配。
 */
final class EditorPanels {

    private EditorPanels() { }

    // =====================================================================
    // 左侧：场景与节点层级树
    // =====================================================================

    static final class SceneTreePanel extends VBox {
        private final EditorHub hub;
        private final TreeView<String> tree = new TreeView<>();
        private final Map<TreeItem<String>, Object> itemOwner = new IdentityHashMap<>();
        private TreeItem<String> root;
        /** 程序化重建/选中时的重入抑制（防止 switchScene ⇄ refresh 无限循环） */
        private boolean selectingInternally = false;
        /** 当前打开的右键菜单（用于点击别处时自动收起） */
        private ContextMenu openMenu;
        /** 上一次真正激活过的树项（用于“点同一个也能再激活”） */
        private TreeItem<String> lastActivated;

        /** 激活一个树项：场景 → 切换当前场景；节点 → 切到它所在场景并选中它 */
        private void activate(TreeItem<String> sel) {
            Object owner = itemOwner.get(sel);
            if (owner instanceof GameScene scene) {
                hub.switchScene(scene.getName());
                hub.selectNode(null);
            } else if (owner instanceof StoryNode node) {
                GameScene owning = findSceneOf(node);
                if (owning != null) hub.switchScene(owning.getName());
                hub.selectNode(node);
            }
        }

        /** 打开菜单并接管“自动收起”：点击界面任意其它位置/切换选择时都会关掉 */
        private void openMenu(ContextMenu menu, javafx.scene.Node anchor, double screenX, double screenY) {
            hideOpenMenu();
            openMenu = menu;
            menu.show(anchor, screenX, screenY);
            menu.setOnHidden(e -> {
                if (openMenu == menu) openMenu = null;
            });
        }

        /** 收起层级树的右键菜单（EditorPane 全局点击过滤器会调用） */
        void hideOpenMenu() {
            if (openMenu != null) {
                ContextMenu m = openMenu;
                openMenu = null;
                m.hide();
            }
        }

        SceneTreePanel(EditorHub hub) {
            this.hub = hub;
            getStyleClass().add("sidebar");
            setPrefWidth(230);
            setMinWidth(180);

            Label title = new Label("📑 场景 / 节点");
            title.getStyleClass().add("sidebar-title");

            Button addScene = new Button("＋ 场景");
            addScene.getStyleClass().add("tool-button");
            addScene.setOnAction(e -> hub.createSceneViaTree());

            Button delScene = new Button("🗑 场景");
            delScene.getStyleClass().add("tool-button");
            delScene.setOnAction(e -> hub.deleteSceneViaTree());

            HBox btns = new HBox(8, addScene, delScene);
            btns.setAlignment(Pos.CENTER_LEFT);

            tree.setShowRoot(true);
            VBox.setVgrow(tree, Priority.ALWAYS);

            tree.getSelectionModel().selectedItemProperty().addListener((o, old, sel) -> {
                if (sel == null || selectingInternally) return;
                lastActivated = sel;
                activate(sel);
            });
            // 点“已经处于选中状态”的那一项时 selectedItemProperty 不会变化，
            // 这里补一次激活，避免“场景在树里是选中的，但画布不显示、再点也没反应”。
            tree.setOnMouseClicked(e -> {
                if (e.getButton() != MouseButton.PRIMARY || selectingInternally) return;
                TreeItem<String> sel = tree.getSelectionModel().getSelectedItem();
                if (sel != null && sel == lastActivated) activate(sel);
            });

            // 右键菜单（场景/节点/空白）
            tree.setOnContextMenuRequested(e -> {
                TreeItem<String> item = tree.getSelectionModel().getSelectedItem();
                ContextMenu menu = new ContextMenu();
                if (item == null || item == root) {
                    MenuItem add = new MenuItem("＋ 新增场景");
                    add.setOnAction(ev -> hub.createSceneViaTree());
                    menu.getItems().add(add);
                } else {
                    Object owner = itemOwner.get(item);
                    if (owner instanceof GameScene scene) {
                        MenuItem cur = new MenuItem("📌 设为当前场景");
                        cur.setOnAction(ev -> hub.switchScene(scene.getName()));
                        MenuItem rename = new MenuItem("✏️ 重命名场景");
                        rename.setOnAction(ev -> hub.renameSceneViaTree(scene));
                        MenuItem del = new MenuItem("🗑 删除场景");
                        del.setOnAction(ev -> hub.deleteSceneViaTree());
                        MenuItem newNode = new MenuItem("🧩 添加子节点");
                        newNode.setOnAction(ev -> {
                            hub.switchScene(scene.getName());
                            hub.createNodeAt(NodeType.TEXT.code(), 100, 100);
                        });
                        menu.getItems().addAll(cur, rename, del, new SeparatorMenuItem(), newNode);
                    } else if (owner instanceof StoryNode node) {
                        MenuItem edit = new MenuItem("✏️ 编辑属性…");
                        edit.setOnAction(ev -> hub.openNodeDialog(node));
                        MenuItem del = new MenuItem("🗑 删除节点");
                        del.setOnAction(ev -> hub.deleteNode(node));
                        MenuItem dup = new MenuItem("📋 复制节点");
                        dup.setOnAction(ev -> {
                            StoryNode c = node.copy();
                            c.setX(c.getX() + 26);
                            c.setY(c.getY() + 26);
                            hub.addNode(c);
                        });
                        menu.getItems().addAll(edit, dup, del);
                    }
                }
                if (!menu.getItems().isEmpty()) {
                    openMenu(menu, tree, e.getScreenX(), e.getScreenY());
                }
            });
            getChildren().addAll(title, btns, tree);
            refresh(null);
        }

        /** 结构/选择变化后重建树（程序化选中期间抑制监听，防无限重入） */
        void refresh(GameScene currentScene) {
            hideOpenMenu();
            selectingInternally = true;
            try {
                itemOwner.clear();
                GameProject project = hub.project();
                TreeItem<String> newRoot = new TreeItem<>("🗀 " + (project == null ? "（未打开地图）" : project.name()));
                newRoot.setExpanded(true);
                if (project != null) {
                    for (GameScene scene : project.scenes().values()) {
                        TreeItem<String> sceneItem = new TreeItem<>("📄 " + scene.getName());
                        itemOwner.put(sceneItem, scene);
                        sceneItem.setExpanded(true);
                        for (StoryNode node : scene.nodes()) {
                            TreeItem<String> nodeItem = new TreeItem<>(nodeIcon(node));
                            itemOwner.put(nodeItem, node);
                            sceneItem.getChildren().add(nodeItem);
                        }
                        newRoot.getChildren().add(sceneItem);
                    }
                }
                root = newRoot;
                tree.setRoot(root);
                if (currentScene != null) {
                    for (TreeItem<String> sceneItem : root.getChildren()) {
                        if (itemOwner.get(sceneItem) == currentScene) {
                            tree.getSelectionModel().select(sceneItem);
                            break;
                        }
                    }
                }
            } finally {
                selectingInternally = false;
            }
        }

        private GameScene findSceneOf(StoryNode node) {
            GameProject p = hub.project();
            if (p == null) return null;
            for (GameScene s : p.scenes().values()) {
                if (s.contains(node)) return s;
            }
            return null;
        }

        private static String nodeIcon(StoryNode node) {
            return node.getType().icon() + " " + (node.getId().isBlank() ? node.getType().display() : node.getId());
        }
    }

    // =====================================================================
    // 右侧：属性检查器（场景区 + 节点区）
    // =====================================================================

    static final class InspectorPanel extends VBox {
        private final EditorHub hub;
        private final VBox body = new VBox(6);
        private final ScrollPane scroll = new ScrollPane(body);
        private boolean building = false;

        InspectorPanel(EditorHub hub) {
            this.hub = hub;
            getStyleClass().add("sidebar");
            setPrefWidth(300);
            setMinWidth(230);

            Label title = new Label("🛠 属性检查器");
            title.getStyleClass().add("sidebar-title");

            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            VBox.setVgrow(scroll, Priority.ALWAYS);

            getChildren().addAll(title, scroll);
            refresh();
        }

        void refresh() {
            if (building) return;
            building = true;
            try {
                body.getChildren().clear();
                // 顶部独立分区：新增节点模板（属于编辑器设置，与是否打开地图无关）
                buildTemplateSection();
                if (hub.project() == null || hub.scene() == null) {
                    Label empty = new Label("尚未打开地图。\n请使用 文件 → 打开/新建。");
                    empty.setWrapText(true);
                    body.getChildren().add(empty);
                    return;
                }
                buildSceneSection();
                StoryNode node = hub.selectedNode();
                if (node != null) {
                    buildNodeSection(node);
                } else {
                    Label hint = new Label("💡 未选中节点：在画布点击物件，\n或从左侧层级树选择。");
                    hint.setWrapText(true);
                    hint.getStyleClass().add("field-label");
                    body.getChildren().add(hint);
                }
            } finally {
                building = false;
            }
        }

        // ---------- 新增节点模板区 ----------
        /**
         * 一栏「新增节点模板」：类型下拉 + 摘要 + 编辑模板状态。
         * 模板只决定右键“添加节点”生成的新节点初值，不影响已有节点。
         */
        private void buildTemplateSection() {
            body.getChildren().add(sectionTitle("➕ 新增节点模板"));

            StoryNode tpl = hub.newNodeTemplate();
            if (tpl == null) {
                tpl = NodeType.createDefault(NodeType.TEXT.code(), 0, 0);
                hub.setNewNodeTemplate(tpl);
            }

            ComboBox<NodeType> typeBox = new ComboBox<>();
            typeBox.getItems().addAll(NodeType.values());
            // 显示 icon + display（如 “⌨ 文本框”）
            typeBox.setConverter(new javafx.util.StringConverter<NodeType>() {
                @Override
                public String toString(NodeType t) {
                    return t == null ? "" : t.icon() + " " + t.display();
                }

                @Override
                public NodeType fromString(String s) {
                    return NodeType.from(s);
                }
            });
            typeBox.setMaxWidth(Double.MAX_VALUE);
            typeBox.getSelectionModel().select(tpl.getType());

            Label summary = new Label(templateSummary(tpl));
            summary.setWrapText(true);
            summary.getStyleClass().add("hint-text");

            // 切换类型 → 用该类型的默认值重建模板（尺寸/默认文本随之变化）并刷新摘要
            typeBox.valueProperty().addListener((o, a, b) -> {
                if (b == null) return;
                hub.setNewNodeTemplate(NodeType.createDefault(b.code(), 0, 0));
                summary.setText(templateSummary(hub.newNodeTemplate()));
            });

            Button editTpl = new Button("✏️ 编辑模板状态…");
            editTpl.getStyleClass().add("tool-button");
            editTpl.setMaxWidth(Double.MAX_VALUE);
            editTpl.setOnAction(e -> {
                // 编辑的是“模板本身”，不是场景里的节点
                NodeDialogs.showTemplateDialog(hub, hub.newNodeTemplate());
                summary.setText(templateSummary(hub.newNodeTemplate()));
            });

            Label note = new Label("右键“添加节点”生成的节点将与这里的状态一致"
                    + "（只决定新节点的类型与初值，不影响已有节点）");
            note.setWrapText(true);
            note.getStyleClass().add("hint-text");

            body.getChildren().addAll(row("新节点类型", typeBox), row("模板摘要", summary),
                    editTpl, note, new Separator());
        }

        /** 一行式模板摘要：类型 尺寸｜初始内容｜信号数｜槽数 */
        private static String templateSummary(StoryNode t) {
            if (t == null) return "（未设置模板）";
            String text = t.getText() == null ? "" : t.getText().replace("\n", " ").trim();
            if (text.length() > 16) text = text.substring(0, 16) + "…";
            return t.getType().display() + " "
                    + StoryNode.trimDouble(t.getWidth()) + "×" + StoryNode.trimDouble(t.getHeight())
                    + "｜初始内容: " + (text.isEmpty() ? "（空）" : text)
                    + "｜信号 " + t.signals().size()
                    + "｜槽 " + t.slots().size();
        }

        // ---------- 场景区 ----------
        private void buildSceneSection() {
            GameScene scene = hub.scene();
            body.getChildren().add(sectionTitle("当前场景: " + scene.getName()));

            TextField event = new TextField(scene.event() == null ? "" : scene.event());
            event.setPromptText("如 minesweeper（进入场景时加载插件）");
            bind(event, v -> scene.setEvent(v));
            body.getChildren().add(row("场景事件 event", event));

            ComboBox<String> nextBox = new ComboBox<>();
            nextBox.getItems().add("");
            nextBox.getItems().addAll(hub.project().scenes().keySet());
            nextBox.getSelectionModel().select(scene.next() == null ? "" : scene.next());
            bind(nextBox, v -> scene.setNext(v == null ? "" : v));
            body.getChildren().add(row("下一场景 next", nextBox));

            Button optionBtn = new Button("⚙ 地图全局设置 [option]…");
            optionBtn.getStyleClass().add("tool-button");
            optionBtn.setOnAction(e -> NodeDialogs.showOptionDialog(hub));

            // 场景属性窗口：属性 / 节点列表 / 信号与槽
            Button scenePropsBtn = new Button("🧩 场景属性 / 节点 / 信号槽…");
            scenePropsBtn.getStyleClass().add("tool-button");
            scenePropsBtn.setMaxWidth(Double.MAX_VALUE);
            scenePropsBtn.setOnAction(e -> SceneInspectorDialog.show(hub, hub.scene()));

            body.getChildren().addAll(optionBtn, scenePropsBtn);

            // 场景级信号（键盘）与槽：地图全局监听器接收按键后分发
            TextArea sceneSig = new TextArea(NodeDialogs.signalsToText(scene.signals()));
            sceneSig.setWrapText(true);
            sceneSig.setPrefRowCount(2);
            sceneSig.setTooltip(new Tooltip("场景级键盘信号：名称 | key | 按键码 | press|release"));
            bind(sceneSig, v -> {
                NodeDialogs.applySignals(scene.signals(), v);
                hub.setDirty();
            });
            body.getChildren().add(row("场景信号(键盘)", sceneSig));

            TextArea sceneSlot = new TextArea(NodeDialogs.slotsToText(scene.slots()));
            sceneSlot.setWrapText(true);
            sceneSlot.setPrefRowCount(2);
            sceneSlot.setTooltip(new Tooltip("场景槽：信号名 | 动作 | 目标 | 参数；例：快捷键F | toggle | 提示 | visible"));
            bind(sceneSlot, v -> {
                NodeDialogs.applySlots(scene.slots(), v);
                hub.setDirty();
            });
            body.getChildren().add(row("场景槽", sceneSlot));
            body.getChildren().add(new Separator());
        }

        // ---------- 节点区 ----------
        private void buildNodeSection(StoryNode node) {


            body.getChildren().add(sectionTitle("节点: " + node.getType().display()));
            Runnable refreshView = () -> {
                hub.nodeChanged(node);
                hub.setDirty();
            };

            Button full = new Button("📋 打开完整属性窗口…");
            full.getStyleClass().add("tool-button");
            full.setOnAction(e -> hub.openNodeDialog(node));
            body.getChildren().add(full);

            ComboBox<String> typeBox = new ComboBox<>();
            for (NodeType t : NodeType.values()) typeBox.getItems().add(t.icon() + " " + t.display());
            typeBox.getSelectionModel().select(node.getType().ordinal());
            bind(typeBox, v -> {
                node.setType(NodeType.values()[Math.max(0, typeBox.getSelectionModel().getSelectedIndex())]);
                refreshView.run();
            });

            TextField idField = new TextField(node.getId());
            bind(idField, v -> { node.setId(v); refreshView.run(); });

            TextField x = num(node.getX(), v -> { node.setX(v); refreshView.run(); });
            TextField y = num(node.getY(), v -> { node.setY(v); refreshView.run(); });
            TextField w = num(node.getWidth(), v -> { node.setWidth(v); refreshView.run(); });
            TextField h = num(node.getHeight(), v -> { node.setHeight(v); refreshView.run(); });

            GridPane geom = new GridPane();
            geom.setHgap(6);
            geom.setVgap(4);
            geom.add(label("X"), 0, 0); geom.add(x, 1, 0);
            geom.add(label("Y"), 2, 0); geom.add(y, 3, 0);
            geom.add(label("宽"), 0, 1); geom.add(w, 1, 1);
            geom.add(label("高"), 2, 1); geom.add(h, 3, 1);

            body.getChildren().add(row("类型", typeBox));
            body.getChildren().add(row("编号 id", idField));
            body.getChildren().add(row("几何(实时拖动也行)", geom));

            TextField path = new TextField(node.getPath());
            bind(path, v -> { node.setPath(v); refreshView.run(); });
            Button pick = new Button("…");
            pick.setOnAction(e -> {
                String rel = AssetImport.pickAndImport(getScene().getWindow(),
                        hub.project(), node, false);
                if (rel != null) path.setText(rel);
            });
            HBox pathRow = new HBox(6, path, pick);
            HBox.setHgrow(path, Priority.ALWAYS);
            body.getChildren().add(row("图片 path", pathRow));

            TextField audio = new TextField(node.getAudio());
            bind(audio, v -> { node.setAudio(v); refreshView.run(); });
            Button pickA = new Button("…");
            pickA.setOnAction(e -> {
                String rel = AssetImport.pickAndImport(getScene().getWindow(),
                        hub.project(), node, true);
                if (rel != null) audio.setText(rel);
            });
            HBox audioRow = new HBox(6, audio, pickA);
            HBox.setHgrow(audio, Priority.ALWAYS);
            body.getChildren().add(row("音频 audio", audioRow));

            // 视频 video：非空时读取器用视频播放器渲染该节点（背景节点＝会动的背景图），空则用图片
            TextField video = new TextField(node.getVideo());
            video.setTooltip(new Tooltip("视频相对地图根目录（如 resources/video/opening.mp4）；"
                    + "留空则用图片 path；也可用槽 @plugin(video) 在运行时切换"));
            bind(video, v -> { node.setVideo(v); refreshView.run(); });
            Button pickV = new Button("…选择视频");
            pickV.setOnAction(e -> {
                String rel = NodeDialogs.pickAndImportVideo(getScene().getWindow(), hub.project());
                if (rel != null) video.setText(rel);
            });
            HBox videoRow = new HBox(6, video, pickV);
            HBox.setHgrow(video, Priority.ALWAYS);
            body.getChildren().add(row("视频 video", videoRow));

            TextArea text = new TextArea(node.getText());
            text.setWrapText(true);
            text.setPrefRowCount(3);
            text.setTooltip(new Tooltip("支持富文本标记；独立一行的 --- 分隔多段台词，播放时点击对话框自动切换"));
            bind(text, v -> { node.setText(v); refreshView.run(); });
            body.getChildren().add(row("文字 text(富文本/---多段)", text));

            TextArea style = new TextArea(node.getStyle());
            style.setWrapText(true);
            style.setPrefRowCount(2);
            bind(style, v -> { node.setStyle(v); refreshView.run(); });
            body.getChildren().add(row("样式 style(内联CSS)", style));

            ComboBox<String> typeOn = new ComboBox<>();
            typeOn.getItems().addAll("默认（对话开启）", "开启", "关闭");
            Boolean tv = node.getTypewriter();
            typeOn.getSelectionModel().select(tv == null ? 0 : (tv ? 1 : 2));
            bind(typeOn, v -> {
                int i = typeOn.getSelectionModel().getSelectedIndex();
                node.setTypewriter(i == 1 ? Boolean.TRUE : (i == 2 ? Boolean.FALSE : null));
                refreshView.run();
            });
            body.getChildren().add(row("逐字显示 typewriter", typeOn));

            TextField eventF = new TextField(node.getEvent());
            bind(eventF, v -> { node.setEvent(v); refreshView.run(); });
            body.getChildren().add(row("插件事件 event", eventF));

            // 目标（先建，供 action 监听联动禁用）
            TextField targetF = new TextField(node.getTarget());
            targetF.setPromptText("场景名 或 存档文件名(slot2)");
            bind(targetF, v -> { node.setTarget(v == null ? "" : v); refreshView.run(); });

            ComboBox<String> action = new ComboBox<>();
            // 注意：索引必须与 NodeDialogs.actionCodeFor/actionIndexFor 保持一致
            // （skip/speed 两个旧动作已移除，读取器不再支持）
            action.getItems().addAll("（无）", "target 跳转", "save 写存档", "load 读存档", "event 插件");
            action.getSelectionModel().select(NodeDialogs.actionIndexFor(node.getAction()));
            bind(action, v -> {
                node.setAction(NodeDialogs.actionCodeFor(action.getSelectionModel().getSelectedIndex()));
                targetF.setDisable(!NodeDialogs.targetEnabled(node));
                refreshView.run();
            });
            body.getChildren().add(row("动作 action", action));
            targetF.setDisable(!NodeDialogs.targetEnabled(node));
            body.getChildren().add(row("目标 target（场景名/存档名/对话下一场景）", targetF));

            CheckBox visible = new CheckBox("可见");
            visible.setSelected(node.isVisible());
            visible.selectedProperty().addListener((o, a, b) -> {
                node.setVisible(b);
                refreshView.run();
            });
            body.getChildren().add(row("显示", visible));

            TextField fs = num(node.getFontSize() > 0 ? node.getFontSize() : 0,
                    v -> { node.setFontSize(v); refreshView.run(); });
            body.getChildren().add(row("字号 fontSize(0=默认)", fs));

            TextField op = num(node.getOpacity(),
                    v -> { node.setOpacity(Math.max(0.05, Math.min(1, v))); refreshView.run(); });
            body.getChildren().add(row("透明度 opacity", op));

            TextField trans = new TextField(node.getTransition());
            trans.setPromptText("scale/opacity:300ms（留空=立即）");
            trans.setTooltip(new Tooltip("本节点属性改变时的简单补间：scale/opacity/rotation/x/y[:毫秒]"));
            bind(trans, v -> { node.setTransition(v); refreshView.run(); });
            body.getChildren().add(row("过渡动画 transition", trans));

            // 节点信号 / 槽（与完整属性窗口里的编辑同步）
            TextArea nodeSig = new TextArea(NodeDialogs.signalsToText(node.signals()));
            nodeSig.setWrapText(true);
            nodeSig.setPrefRowCount(2);
            nodeSig.setTooltip(new Tooltip("名称 | mouse|key | click/release/按键码 | 参数"));
            bind(nodeSig, v -> {
                NodeDialogs.applySignals(node.signals(), v);
                hub.setDirty();
            });
            body.getChildren().add(row("信号 signal", nodeSig));

            TextArea nodeSlot = new TextArea(NodeDialogs.slotsToText(node.slots()));
            nodeSlot.setWrapText(true);
            nodeSlot.setPrefRowCount(3);
            nodeSlot.setTooltip(new Tooltip("信号名 | 动作 | 目标 | 参数：set/toggle/emit/goto/save/load/call/log"));
            bind(nodeSlot, v -> {
                NodeDialogs.applySlots(node.slots(), v);
                hub.setDirty();
            });
            body.getChildren().add(row("槽 slot", nodeSlot));
        }

        // ---------- 小工具 ----------
        private Label sectionTitle(String text) {
            Label l = new Label(text);
            l.getStyleClass().add("sidebar-title");
            return l;
        }

        private static Label label(String s) {
            Label l = new Label(s);
            l.getStyleClass().add("field-label");
            return l;
        }

        private static javafx.scene.Node row(String labelText, Node field) {
            Label lb = label(labelText);
            lb.setWrapText(true);
            VBox box = new VBox(3, lb, field);
            return box;
        }

        private static TextField num(double v, Consumer<Double> apply) {
            TextField f = new TextField(StoryNode.trimDouble(v));
            f.textProperty().addListener((o, a, b) -> {
                try {
                    double d = Double.parseDouble(b.trim());
                    if (d >= 0 && d < 1e6) {
                        apply.accept(d);
                        f.setStyle("");
                    }
                } catch (NumberFormatException ex) {
                    f.setStyle("-fx-border-color: #ff6b6b;");
                }
            });
            return f;
        }

        private static <T> void bind(TextField f, Consumer<String> c) {
            f.textProperty().addListener((o, a, b) -> c.accept(b));
        }

        private static void bind(ComboBox<String> box, Consumer<String> c) {
            box.valueProperty().addListener((o, a, b) -> c.accept(b));
        }

        private static void bind(TextArea ta, Consumer<String> c) {
            ta.textProperty().addListener((o, a, b) -> c.accept(b));
        }
    }
}
