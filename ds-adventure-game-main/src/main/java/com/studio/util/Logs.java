package com.studio.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 极简日志工具：带时间戳输出到控制台。
 * 编辑器界面左下角的状态栏会另行展示主要动作（见 EditorPane）。
 */
public final class Logs {

    private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private Logs() { }

    public static void info(String msg) {
        System.out.println("[" + LocalTime.now().format(T) + "] [信息] " + msg);
    }

    public static void warn(String msg) {
        System.out.println("[" + LocalTime.now().format(T) + "] [警告] " + msg);
    }

    public static void error(String msg, Throwable e) {
        System.err.println("[" + LocalTime.now().format(T) + "] [错误] " + msg);
        if (e != null) e.printStackTrace();
    }

    public static void plugin(String pluginId, String msg) {
        System.out.println("[" + LocalTime.now().format(T) + "] [插件:" + pluginId + "] " + msg);
    }
}
