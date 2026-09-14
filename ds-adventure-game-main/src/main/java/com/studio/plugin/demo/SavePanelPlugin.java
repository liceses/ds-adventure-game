package com.studio.plugin.demo;

import com.studio.plugin.GamePlugin;
import com.studio.saves.SaveData;
import com.studio.saves.SavePortal;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Map;

/**
 * 示例插件：三槽“存档台”界面。
 *
 * <p>由地图里的按钮以 {@code action=event, event=savepanel} 呼出（嵌入读取器主舞台），
 * 为每个槽位提供 [💾 保存] [📂 读取] [🗑 删除]：</p>
 * <ul>
 *   <li>保存 → 写入 地图/saves/slotN.txt（内置 scene 变量 + SaveHook 自定义变量）；</li>
 *   <li>读取 → 还原变量并自动跳回存档场景，同时自动退出本界面；</li>
 *   <li>删除 → 移除对应存档文件（二次确认在插件内直接做）。</li>
 * </ul>
 */
public class SavePanelPlugin implements GamePlugin {

    /** 默认三个槽位文件名（地图工程师可自行修改源码常量后重新打包） */
    public static final String[] SLOT_NAMES = {"slot1", "slot2", "slot3"};
    private static final String[] SLOT_LABELS = {"槽 1", "槽 2", "槽 3"};

    private SavePortal portal;
    private final Label[] statusLabels = new Label[SLOT_NAMES.length];

    @Override
    public void execute(Stage stage, Map<String, Object> params) {
        boolean embedded = params.get(PARAM_EMBEDDED) instanceof Boolean b && b;
        if (embedded) return;
        Stage win = new Stage();
        win.setTitle("存档台（窗口模式）");
        win.setScene(new Scene(buildRoot(), 560, 360));
        win.show();
    }

    @Override
    public Parent createEmbeddedView(Map<String, Object> params) {
        Object p = params == null ? null : params.get(GamePlugin.PARAM_SAVES);
        portal = (p instanceof SavePortal sp) ? sp : null;
        return buildRoot();
    }

    @Override
    public String displayName() {
        return "存档台";
    }

    // =====================================================================

    private Parent buildRoot() {
        Label title = new Label("💾 存档台（3 槽）");
        title.setStyle("-fx-font-size: 19px; -fx-font-weight: bold; -fx-text-fill: #f4d06f;");
        Label hint = new Label("存档内容为 地图/saves/*.txt（含 scene 等变量，支持 # 注释）。\n"
                + "读取后会跳回存档时的场景；保存/读取的界面由本插件提供。");
        hint.setStyle("-fx-text-fill: #9aa0c8; -fx-font-size: 12px;");
        hint.setWrapText(true);

        VBox rows = new VBox(8);
        for (int i = 0; i < SLOT_NAMES.length; i++) {
            rows.getChildren().add(buildRow(i));
        }
        if (portal == null) {
            Label no = new Label("未接入引擎（请通过剧情播放器的事件打开）");
            no.setStyle("-fx-text-fill: #ff8b6c;");
            rows.getChildren().add(no);
        }

        VBox body = new VBox(10, title, hint, rows);
        body.setPadding(new Insets(14));
        BorderPane root = new BorderPane(body);
        root.getStyleClass().add("mine-root");
        root.setStyle("-fx-background-color: #101322;");
        refreshAll();
        return root;
    }

    private HBox buildRow(int index) {
        String file = SLOT_NAMES[index];
        Label name = new Label(SLOT_LABELS[index] + "  " + file + ".txt");
        name.setMinWidth(170);
        name.setStyle("-fx-text-fill: #e8e8f0; -fx-font-size: 13px;");

        Button save = button("💾 保存", () -> {
            if (portal == null) return;
            boolean ok = portal.saveTo(file);
            statusLabels[index].setText(ok ? "✓ 已保存（当前场景 " + portal.currentScene() + "）" : "✗ 保存失败");
            refreshStatus(index);
        });
        Button load = button("📂 读取", () -> {
            if (portal == null) return;
            boolean ok = portal.loadFrom(file);
            statusLabels[index].setText(ok ? "✓ 已读取，正在返回存档场景…" : "✗ 槽位为空或读取失败");
        });
        Button del = button("🗑 删除", () -> {
            if (portal == null) return;
            if (portal.delete(file)) {
                statusLabels[index].setText("已删除");
                refreshStatus(index);
            } else {
                statusLabels[index].setText("该槽位没有存档");
            }
        });

        Label status = new Label("");
        status.setMinWidth(210);
        status.setStyle("-fx-text-fill: #8fe3ff; -fx-font-size: 12px;");
        statusLabels[index] = status;

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8, name, save, load, del, status);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Button button(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("mine-restart");
        b.setOnAction(e -> action.run());
        return b;
    }

    private void refreshAll() {
        for (int i = 0; i < SLOT_NAMES.length; i++) refreshStatus(i);
    }

    private void refreshStatus(int index) {
        Label st = statusLabels[index];
        if (st == null || portal == null) return;
        String file = SLOT_NAMES[index];
        if (!portal.manager().exists(file)) {
            st.setText("空槽位");
            return;
        }
        SaveData d = portal.peek(file);
        if (d == null) {
            st.setText("文件存在（暂无法预览）");
            return;
        }
        String scene = d.getString("scene", "");
        st.setText("已存" + d.keys().size() + " 变量" + (scene.isBlank() ? "" : " · scene=" + scene));
    }
}
