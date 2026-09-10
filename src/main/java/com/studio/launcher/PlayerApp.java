package com.studio.launcher;

import com.studio.reader.ReaderView;
import com.studio.util.AppConfig;
import com.studio.util.MapTemplateFactory;
import com.studio.util.Logs;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

/**
 * 播放器启动类。
 *
 * <p>地图文件夹解析优先级：</p>
 * <ol>
 *   <li>启动参数（命令行第一个非模式参数 / -Dstudio.map=xxx）；</li>
 *   <li>config.ini 的 map.folder；</li>
 *   <li>都不存在时，在 maps/demo_map 自动生成示例地图并加载（开箱即演示）。</li>
 * </ol>
 */
public class PlayerApp extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        AppConfig config = AppConfig.loadDefault();

        File map = resolveMapFolder(getParameters());
        if (map == null) {
            // 引导生成示例地图
            try {
                File demo = new File(System.getProperty("user.dir"), "maps/demo_map");
                if (!new File(demo, "scenario.txt").isFile()) {
                    MapTemplateFactory.createMap(demo, true);
                    System.out.println("[Player] 已自动生成示例地图: " + demo.getAbsolutePath());
                }
                map = demo;
            } catch (Exception e) {
                com.studio.util.Logs.error("示例地图生成失败", e);
            }
        }
        if (map == null) {
            javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            a.setTitle("启动失败");
            a.setHeaderText(null);
            a.setContentText("无法确定要加载的地图文件夹，请检查 config.ini 的 map.folder。");
            a.showAndWait();
            stage.close();
            return;
        }

        int w = config.getInt("window.width", 1280);
        int h = config.getInt("window.height", 720);
        ReaderView view = new ReaderView(stage, map, config);

        Scene scene = new Scene(view, w, h);
        var css = PlayerApp.class.getResource("/styles/player.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        stage.setTitle("剧情播放器 Player");
        stage.setMinWidth(760);
        stage.setMinHeight(520);
        stage.setScene(scene);
        stage.show();

        view.start();
    }

    /** 解析地图文件夹（参数 / 系统属性 / 配置文件） */
    private static File resolveMapFolder(Application.Parameters params) {
        List<String> raw = params.getRaw();
        for (String a : raw) {
            File f = new File(a);
            if (f.isDirectory() && new File(f, "scenario.txt").isFile()) {
                return f;
            }
        }
        String sys = System.getProperty("studio.map");
        if (sys != null && !sys.isBlank()) {
            File f = new File(sys);
            if (f.isDirectory() && new File(f, "scenario.txt").isFile()) return f;
        }
        AppConfig config = AppConfig.loadDefault();
        String folder = config.get("map.folder");
        if (folder == null || folder.isBlank()) return null;
        File f = new File(folder);
        if (!f.isAbsolute()) f = new File(System.getProperty("user.dir"), folder);
        return f.isDirectory() && new File(f, "scenario.txt").isFile() ? f : null;
    }
}
