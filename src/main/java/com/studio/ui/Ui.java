package com.studio.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextInputDialog;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * 通用 UI 小助手：弹窗（信息/警告/错误/二次确认/文本输入）与
 * 文件/目录选择器，全部文案中文。样式继承全局 CSS。
 */
public final class Ui {

    private Ui() { }

    private static void style(DialogPane pane) {
        pane.getStyleClass().add("dialog-pane");
        String css = String.valueOf(Ui.class.getResource("/styles/studio.css"));
        if (css != null) pane.getStylesheets().add(css);
    }

    public static void info(Window owner, String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        style(a.getDialogPane());
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        if (owner != null) a.initOwner(owner);
        a.showAndWait();
    }

    public static void warn(Window owner, String title, String message) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        style(a.getDialogPane());
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        if (owner != null) a.initOwner(owner);
        a.showAndWait();
    }

    public static void error(Window owner, String title, String message, Throwable e) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        style(a.getDialogPane());
        a.setTitle(title);
        a.setHeaderText(null);
        String detail = e == null ? "" : "\n\n" + e.getClass().getSimpleName() + ": " + e.getMessage();
        a.setContentText(message + detail);
        if (owner != null) a.initOwner(owner);
        a.showAndWait();
    }

    /** 二次确认：返回是否确认 */
    public static boolean confirm(Window owner, String title, String header, String content) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        style(a.getDialogPane());
        a.setTitle(title);
        a.setHeaderText(header);
        a.setContentText(content);
        if (owner != null) a.initOwner(owner);
        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get() == ButtonType.OK;
    }

    /** 文本输入框 */
    public static Optional<String> askText(Window owner, String title, String header,
                                           String content, String initial) {
        TextInputDialog d = new TextInputDialog(initial == null ? "" : initial);
        style(d.getDialogPane());
        d.setTitle(title);
        d.setHeaderText(header);
        d.setContentText(content);
        if (owner != null) d.initOwner(owner);
        return d.showAndWait();
    }

    /** 目录选择 */
    public static File chooseDirectory(Window owner, String title, File initial) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle(title);
        if (initial != null && initial.isDirectory()) dc.setInitialDirectory(initial);
        return dc.showDialog(owner);
    }

    /** 打开文件（可带扩展名过滤） */
    public static File chooseOpenFile(Window owner, String title, File initial,
                                      FileChooser.ExtensionFilter... filters) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        if (filters.length > 0) fc.getExtensionFilters().addAll(filters);
        if (initial != null) {
            if (initial.isDirectory()) fc.setInitialDirectory(initial);
            else if (initial.getParentFile() != null) fc.setInitialDirectory(initial.getParentFile());
        }
        return fc.showOpenDialog(owner);
    }

    /** 展示解析警告列表 */
    public static void showWarnings(Window owner, String title, List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (String w : warnings) {
            if (shown++ < 12) sb.append("• ").append(w).append('\n');
        }
        if (warnings.size() > shown) sb.append("… 共 ").append(warnings.size()).append(" 条");
        warn(owner, title, sb.toString().trim());
    }
}
