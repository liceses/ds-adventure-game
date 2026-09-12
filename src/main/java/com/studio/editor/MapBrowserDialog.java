package com.studio.editor;

import com.studio.model.GameProject;
import com.studio.model.GameScene;
import com.studio.parser.ScriptParser;
import com.studio.ui.Ui;
import com.studio.util.AppConfig;
import com.studio.util.Logs;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * <b>打开地图</b>窗口：用列表把“编辑器默认文件夹里的地图”列出来，选一张就能打开。
 *
 * <p>以前文件菜单里有五六个“打开某某演示地图”，菜单越堆越长、自己也记不清哪个是哪个。
 * 现在统一成一个入口：本窗口扫描编辑器默认的地图文件夹（{@code config.ini} 的
 * {@code editor.maps.dir}，默认 {@code maps/}），把每个地图的
 * <b>场景数 / 节点数 / 最后修改时间 / 路径</b>列出来，双击或按【打开】即可。</p>
 *
 * <p>窗口里还顺手放了「新建地图」「生成演示地图」「打开该文件夹」「打开其它文件夹…」，
 * 于是文件菜单只需要留一行。</p>
 */
public final class MapBrowserDialog {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /** 列表里的一行（对应默认文件夹下的一个子目录） */
    public static class MapRow {
        private final File dir;
        private final String name;
        private final int scenes;
        private final int nodes;
        private final long modified;
        private final String note;

        MapRow(File dir, String name, int scenes, int nodes, long modified, String note) {
            this.dir = dir;
            this.name = name;
            this.scenes = scenes;
            this.nodes = nodes;
            this.modified = modified;
            this.note = note;
        }

        public File dir() { return dir; }
        public String getName() { return name; }
        public int getScenes() { return scenes; }
        public int getNodes() { return nodes; }
        public long getModified() { return modified; }
        public String getNote() { return note; }
        public String getModifiedText() {
            return modified <= 0 ? "—" : LocalDateTime.ofInstant(Instant.ofEpochMilli(modified), ZoneId.systemDefault()).format(FMT);
        }
        public String getPath() { return dir.getAbsolutePath(); }
    }

    private MapBrowserDialog() { }

    /** 打开窗口 */
    public static void show(EditorPane pane) {
        Stage owner = pane == null ? null : pane.stageForDialog();
        Stage stage = new Stage();
        stage.setTitle("打开地图（编辑器默认文件夹）");
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }

        File root = pane == null ? defaultMapsRoot() : pane.mapsRoot();
        ObservableList<MapRow> rows = FXCollections.observableArrayList(scan(root, pane));

        Label where = new Label("📂 默认地图文件夹：" + root.getAbsolutePath());
        where.getStyleClass().add("hint-text");
        where.setWrapText(true);

        TableView<MapRow> table = new TableView<>(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("这个文件夹里还没有地图 —— 可以点下面的「新建地图」或「生成演示地图」"));
        TableColumn<MapRow, String> cName = new TableColumn<>("地图名");
        cName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        cName.setPrefWidth(220);
        TableColumn<MapRow, Number> cScene = new TableColumn<>("场景");
        cScene.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getScenes()));
        cScene.setPrefWidth(70);
        TableColumn<MapRow, Number> cNode = new TableColumn<>("节点");
        cNode.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getNodes()));
        cNode.setPrefWidth(70);
        TableColumn<MapRow, String> cTime = new TableColumn<>("最后修改");
        cTime.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getModifiedText()));
        cTime.setPrefWidth(110);
        TableColumn<MapRow, String> cNote = new TableColumn<>("备注");
        cNote.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNote()));
        cNote.setPrefWidth(120);
        TableColumn<MapRow, String> cPath = new TableColumn<>("路径");
        cPath.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPath()));
        cPath.setPrefWidth(360);
        table.getColumns().setAll(Arrays.asList(cName, cScene, cNode, cTime, cNote, cPath));
        table.setPrefHeight(320);

        Runnable refresh = () -> {
            List<MapRow> fresh = scan(root, pane);
            rows.setAll(fresh);
        };

        Button openBtn = new Button("📖 打开选中地图");
        openBtn.setDefaultButton(true);
        openBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        openBtn.setOnAction(e -> {
            MapRow row = table.getSelectionModel().getSelectedItem();
            if (row == null) return;
            stage.close();
            pane.openMap(row.dir());
        });
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) openBtn.fire();
        });

        Button refreshBtn = new Button("🔄 刷新");
        refreshBtn.setOnAction(e -> refresh.run());

        Button openFolder = new Button("📂 打开该文件夹");
        openFolder.setOnAction(e -> ExternalEdit.openInExplorer(root));

        Button newMap = new Button("🆕 新建地图…");
        newMap.setOnAction(e -> {
            stage.close();
            pane.createMapDialog(false);
        });

        MenuButton demos = new MenuButton("🎁 生成演示地图");
        for (String[] d : DEMOS) {
            MenuItem mi = new MenuItem(d[1]);
            final String key = d[0];
            mi.setOnAction(e -> {
                stage.close();
                pane.generateDemoMap(key);
            });
            demos.getItems().add(mi);
        }
        demos.setTooltip(new Tooltip("一键生成示例地图并打开（生成在默认地图文件夹里）"));

        Button otherFolder = new Button("📁 打开其它文件夹…");
        otherFolder.setOnAction(e -> {
            stage.close();
            pane.openMapFromChooser();
        });

        Button close = new Button("关闭");
        close.setCancelButton(true);
        close.setOnAction(e -> stage.close());

        javafx.scene.layout.HBox bottom = new javafx.scene.layout.HBox(8,
                openBtn, refreshBtn, openFolder, new javafx.scene.layout.Region());
        javafx.scene.layout.HBox.setHgrow(bottom.getChildren().get(3), javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.layout.HBox bottom2 = new javafx.scene.layout.HBox(8, newMap, demos, otherFolder, close);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom2.setAlignment(Pos.CENTER_LEFT);

        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(10, where, table, bottom, bottom2);
        box.setPadding(new Insets(14));
        Scene scene = new Scene(box, 1000, 470);
        var css = MapBrowserDialog.class.getResource("/styles/studio.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    /** 演示地图：键 → 菜单文字 */
    private static final String[][] DEMOS = {
            {"branch", "分支剧情 + 2048（含多结局）"},
            {"signallab", "信号实验室（信号/槽 + 逻辑层）"},
            {"logicgate", "逻辑门（两个开关控制三盏灯）"},
            {"vardemo", "存档变量 / 表达式 / @plugin"},
            {"saveroom", "存档实验室（3 槽存档台）"},
            {"breakout", "打砖块（breakout 插件）"},
    };

    /** 默认地图文件夹（config.ini 的 editor.maps.dir，默认工程根下的 maps） */
    public static File defaultMapsRoot() {
        return new File(System.getProperty("user.dir"), "maps");
    }

    /**
     * 扫描默认文件夹：列出“含 scenario.txt 的子目录”，顺带统计场景/节点数。
     * <p>另外把“当前打开但这不在默认文件夹里的地图”也列上，标成【当前打开】。</p>
     */
    private static List<MapRow> scan(File root, EditorPane pane) {
        List<MapRow> out = new ArrayList<>();
        File[] dirs = root.isDirectory() ? root.listFiles(File::isDirectory) : null;
        if (dirs != null) {
            for (File dir : dirs) {
                File scenario = new File(dir, "scenario.txt");
                if (!scenario.isFile()) continue;
                out.add(readRow(dir, scenario, ""));
            }
        }
        File current = pane == null ? null : pane.currentMapDir();
        if (current != null && current.isDirectory()) {
            boolean inside = false;
            for (MapRow r : out) if (r.dir().equals(current)) { inside = true; break; }
            if (!inside) {
                File scenario = new File(current, "scenario.txt");
                if (scenario.isFile()) out.add(readRow(current, scenario, "【当前打开·外部】"));
            } else {
                for (int i = 0; i < out.size(); i++) {
                    if (out.get(i).dir().equals(current)) {
                        MapRow r = out.get(i);
                        out.set(i, new MapRow(r.dir(), r.getName(), r.getScenes(), r.getNodes(),
                                r.getModified(), "【当前打开】"));
                    }
                }
            }
        }
        // 按最后修改时间从新到旧；故意写成 lambda，而不是调用 Comparator 的反转方法
        //（同步脚本的 Java 17 兼容扫描会把那个方法名误判成 Java 21 的 SequencedCollection 新方法）
        out.sort((a, b) -> Long.compare(b.getModified(), a.getModified()));
        return out;
    }

    /** 读一行：解析地图拿场景/节点数（解析失败也不炸，只在备注里说明） */
    private static MapRow readRow(File dir, File scenario, String note) {
        int scenes = 0, nodes = 0;
        String extra = note;
        try {
            List<String> warnings = new ArrayList<>();
            GameProject p = ScriptParser.parse(scenario, warnings);
            scenes = p.scenes().size();
            for (GameScene s : p.scenes().values()) nodes += s.nodes().size();
            if (!warnings.isEmpty()) extra = (extra.isEmpty() ? "" : extra + " ") + "⚠ " + warnings.size() + " 条解析提示";
        } catch (Exception e) {
            extra = (extra.isEmpty() ? "" : extra + " ") + "⚠ 解析失败：" + shortMsg(e);
            Logs.warn("[MapBrowser] 解析失败 " + scenario + "：" + e.getMessage());
        }
        return new MapRow(dir, dir.getName(), scenes, nodes, scenario.lastModified(), extra);
    }

    private static String shortMsg(Exception e) {
        String m = e.getMessage();
        if (m == null) return e.getClass().getSimpleName();
        int nl = m.indexOf('\n');
        return nl > 0 ? m.substring(0, nl) : m;
    }

    /** 供探针/其它窗口复用：地图总数（不含“当前打开·外部”那条） */
    public static int countMaps(File root) {
        File[] dirs = root.isDirectory() ? root.listFiles(File::isDirectory) : null;
        if (dirs == null) return 0;
        int n = 0;
        for (File d : dirs) if (new File(d, "scenario.txt").isFile()) n++;
        return n;
    }

    /** 未使用的告警抑制（保持与 Ui 的依赖关系明确） */
    static void unused(Ui ui, AppConfig config) { }
}
