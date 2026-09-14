package com.studio.launcher;

import com.studio.editor.EditorPane;
import com.studio.util.AppConfig;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

/**
 * 编辑器启动类（JavaFX Application）。
 * 打开指定文件夹（可选）或空工作区。
 */
public class EditorApp extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        AppConfig config = AppConfig.loadDefault();
        EditorPane pane = new EditorPane(stage, config);

        Scene scene = new Scene(pane, 1500, 900);
        var css = EditorApp.class.getResource("/styles/studio.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        stage.setTitle("剧情编辑器 Studio — Visual Novel Editor");
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            if (!pane.canClose()) e.consume(); // 未保存确认被取消 → 阻止关闭
        });
        stage.show();

        // 打开命令行/启动参数指定目录（例如 mvn javafx:run -Dexec.args=... 场景）
        File open = firstDirectory(getParameters().getRaw());
        if (open != null) {
            pane.openMap(open);
        }
    }

    private static File firstDirectory(List<String> args) {
        for (String a : args) {
            File f = new File(a);
            if (f.isDirectory() && new File(f, "scenario.txt").isFile()) {
                return f;
            }
        }
        return null;
    }
}
