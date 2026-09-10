package com.studio.external;

import com.studio.plugin.GamePlugin;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 外部插件示例（在 plugins/examples 目录，不参与主工程编译）。
 *
 * <p>编译并把产物放入 plugins 后，在 plugins.ini 登记
 * {@code clock = com.studio.external.ClockDemoPlugin}，
 * 即可在地图场景 event / 按钮 action=event 中使用事件ID: clock。</p>
 */
public class ClockDemoPlugin implements GamePlugin {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy年MM月dd日  HH:mm:ss");

    private Timeline ticker;

    @Override
    public void execute(Stage stage, Map<String, Object> params) {
        // 嵌入模式下无需额外动作（引擎已自动展示嵌入视图并调用本方法）
        System.out.println("[ClockPlugin] execute 被调用，embedded=" + params.get(PARAM_EMBEDDED));
    }

    @Override
    public Parent createEmbeddedView(Map<String, Object> params) {
        Label clock = new Label();
        clock.setStyle("-fx-font-size: 56px; -fx-font-weight: bold; -fx-text-fill: #ffd76a;");
        Label hint = new Label("这是一个动态加载的外部插件示例：每秒刷新一次时间。\n点击上方【返回剧情】可无缝回到游戏。");
        hint.setStyle("-fx-font-size: 14px; -fx-text-fill: #aab0d0;");
        hint.setWrapText(true);

        VBox box = new VBox(14, clock, hint);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(30));

        BorderPane root = new BorderPane(box);
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #14172b, #232640);");

        ticker = new Timeline(new KeyFrame(Duration.seconds(1), e ->
                clock.setText(LocalDateTime.now().format(FMT))));
        ticker.setCycleCount(Timeline.INDEFINITE);
        ticker.play();
        return root;
    }

    @Override
    public void onDetach() {
        if (ticker != null) ticker.stop();
    }

    @Override
    public String displayName() {
        return "时钟插件";
    }
}
