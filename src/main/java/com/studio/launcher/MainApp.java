package com.studio.launcher;

import javafx.application.Application;

import java.util.List;

/**
 * 统一入口：按参数区分启动 编辑器(Studio) / 播放器(Player)。
 *
 * <p>用法：</p>
 * <pre>
 *   java -jar app.jar                → 编辑器
 *   java -jar app.jar player         → 播放器（读 config.ini 的 map.folder）
 *   java -jar app.jar player maps/xxx→ 播放器并打开指定地图文件夹
 *   java -jar app.jar studio maps/xxx→ 编辑器并打开指定地图文件夹
 * </pre>
 * Maven 下更简单的做法：
 * <pre>
 *   mvn javafx:run            （编辑器）
 *   mvn -Pplayer javafx:run   （播放器）
 * </pre>
 */
public final class MainApp {

    private MainApp() { }

    public static void main(String[] args) {
        Class<? extends Application> app;
        List<String> rest = new java.util.ArrayList<>();
        boolean player = false;
        for (String a : args) {
            if (a.equalsIgnoreCase("player") || a.equals("--player") || a.equals("-player")) player = true;
            else if (a.equalsIgnoreCase("studio") || a.equals("--studio") || a.equals("-studio")) player = false;
            else rest.add(a);
        }
        app = player ? PlayerApp.class : EditorApp.class;
        // 剩余参数透传给具体 Application（map 文件夹路径等）
        Application.launch(app, rest.toArray(new String[0]));
    }
}
