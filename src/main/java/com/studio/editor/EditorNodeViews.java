package com.studio.editor;

import com.studio.model.NodeType;
import com.studio.model.StoryNode;
import com.studio.ui.FxAssets;
import com.studio.ui.RichText;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextFlow;

import java.io.File;

/**
 * 编辑器节点预览工厂 —— 把 StoryNode 渲染为画布上的“所见即所得”外观。
 * 与读取器的渲染区别：编辑器里只做静态预览（点击/拖动交给包装层）。
 */
final class EditorNodeViews {

    private EditorNodeViews() { }

    static Region create(File mapRoot, StoryNode node) {
        double w = Math.max(2, node.getWidth());
        double h = Math.max(2, node.getHeight());
        double fs = node.getFontSize() > 0 ? node.getFontSize()
                : (node.getType() == NodeType.DIALOG ? 19 : 14);

        Region view = switch (node.getType()) {
            case BACKGROUND -> background(mapRoot, node, w, h);
            case CHARACTER -> character(mapRoot, node, w, h);
            case DIALOG -> richPanel(node, w, h, fs, "#f2f3ff", true);
            case NAME -> richPanel(node, w, h, fs, "#ffe2a6", false);
            case TEXT -> richPanel(node, w, h, fs, "#cfd2e6", false);
            case TEXTBOX -> textBox(node, w, h, fs);
            case BUTTON -> button(node, w, h);
            case MUSIC -> musicChip(node);
        };
        // 带视频的节点：在预览右上角叠一个角标（各类型都适用；BACKGROUND 仍照旧显示图片，
        // 读取器会用视频顶上——角标只是让人一眼看出这节点带视频）
        if (node.getVideo() != null && !node.getVideo().isBlank()) {
            addVideoBadge(view);
        }
        return view;
    }

    /** 在节点预览右上角叠一个「🎬 视频」角标（写法与文本框的「→ 变量名」角标一致） */
    private static void addVideoBadge(Region view) {
        if (!(view instanceof StackPane)) return;      // wrap() 返回的都是 StackPane
        StackPane holder = (StackPane) view;
        Label badge = new Label("🎬 视频");
        badge.setStyle("-fx-background-color: rgba(255,120,180,0.85); -fx-text-fill: white;"
                + "-fx-font-size: 10px; -fx-padding: 1 6 1 6; -fx-background-radius: 8;");
        badge.setMouseTransparent(true);               // 角标不吃鼠标，点击/拖动仍落在节点上
        StackPane.setAlignment(badge, Pos.TOP_RIGHT);
        holder.getChildren().add(badge);
    }

    /** 文本框预览：单行/多行输入框外观 + 初始内容 + 绑定变量提示 */
    private static Region textBox(StoryNode n, double w, double h, double fs) {
        String init = n.getText() == null ? "" : n.getText();
        boolean has = !init.isBlank();
        Label body = new Label(has ? init : (n.isMultiline() ? "多行文本框（初始内容为空）" : "单行文本框（初始内容为空）"));
        body.setTextFill(has ? Color.rgb(232, 236, 255) : Color.rgb(140, 146, 176));
        body.setFont(Font.font(fs));
        body.setWrapText(n.isMultiline());
        body.setMaxWidth(Math.max(30, w - 24));

        StackPane field = new StackPane(body);
        field.setAlignment(Pos.TOP_LEFT);
        field.setPadding(new javafx.geometry.Insets(6, 10, 6, 10));
        field.setStyle("-fx-background-color: rgba(12,14,26,0.85);"
                + "-fx-background-radius: 8;"
                + "-fx-border-color: rgba(140,180,255,0.55); -fx-border-radius: 8;"
                + (n.getStyle() == null ? "" : n.getStyle()));

        StackPane holder = wrap(field, w, h);
        if (!n.getBind().isBlank()) {
            Label badge = new Label("→ " + n.getBind());
            badge.setStyle("-fx-background-color: rgba(90,150,255,0.85); -fx-text-fill: white;"
                    + "-fx-font-size: 10px; -fx-padding: 1 6 1 6; -fx-background-radius: 8;");
            badge.setMouseTransparent(true);   // 角标不能吞掉落在上面的点击/拖动
            StackPane.setAlignment(badge, Pos.TOP_RIGHT);
            holder.getChildren().add(badge);
        }
        return holder;
    }

    private static Region background(File root, StoryNode n, double w, double h) {
        Image img = FxAssets.loadRooted(root, n.getPath(), w, h, "背景");
        ImageView iv = new ImageView(img);
        iv.setFitWidth(w);
        iv.setFitHeight(h);
        iv.setPreserveRatio(false);
        iv.setStyle(n.getStyle());
        return wrap(iv, w, h);
    }

    private static Region character(File root, StoryNode n, double w, double h) {
        Image img = FxAssets.loadRooted(root, n.getPath(), w, h, n.getId());
        ImageView iv = new ImageView(img);
        iv.setPreserveRatio(true);
        iv.setFitWidth(w);
        iv.setFitHeight(h); // 双约束 → 等比适配框内
        iv.setStyle(n.getStyle());
        return wrap(iv, w, h);
    }

    private static Region richPanel(StoryNode n, double w, double h, double fs,
                                    String defColor, boolean panelBg) {
        String color = defColor;
        if (n.getStyle() != null && n.getStyle().contains("-fx-text-fill")) {
            // 粗略提取用户自定义文字颜色
            int i = n.getStyle().indexOf("-fx-text-fill");
            String seg = n.getStyle().substring(i);
            int j = seg.indexOf(';');
            if (j < 0) j = seg.length();
            seg = seg.substring(0, j);
            int k = seg.indexOf(':');
            if (k >= 0) color = seg.substring(k + 1).trim();
        }
        TextFlow flow = RichText.flow(n.getText(), fs, color);
        flow.setMaxWidth(Math.max(30, w - 24));

        StackPane box = new StackPane(flow);
        box.setAlignment(alignment(n.getAlign()));
        box.setPadding(new javafx.geometry.Insets(6, 10, 6, 10));
        if (panelBg) {
            box.setStyle((n.getStyle() == null ? "" : n.getStyle())
                    + "-fx-background-color: rgba(20,22,40,0.55);"
                    + "-fx-background-radius: 12;");
        } else if (n.getStyle() != null && !n.getStyle().isBlank()) {
            box.setStyle(n.getStyle());
        }
        return wrap(box, w, h);
    }

    private static Region button(StoryNode n, double w, double h) {
        Label text = new Label(n.getText() == null || n.getText().isBlank() ? "按钮" : n.getText());
        text.setTextFill(Color.WHITE);
        text.setFont(Font.font(15));
        StackPane box = new StackPane(text);
        box.setStyle("-fx-background-color: #3d4060; -fx-background-radius: 12;"
                + "-fx-border-color: rgba(255,255,255,0.28); -fx-border-radius: 12;"
                + (n.getStyle() == null ? "" : n.getStyle()));
        return wrap(box, w, h);
    }

    private static Region musicChip(StoryNode n) {
        Label chip = new Label("🎵 " + (n.getAudio().isBlank() ? "音乐(空)" : n.getAudio()));
        chip.setTextFill(Color.rgb(255, 215, 106));
        chip.setStyle("-fx-background-color: rgba(40,42,64,0.9); -fx-background-radius: 10;"
                + "-fx-padding: 2 10 2 10;");
        StackPane box = new StackPane(chip);
        box.setAlignment(Pos.CENTER_LEFT);
        return wrap(box, 230, 26);
    }

    private static StackPane wrap(Node inner, double w, double h) {
        StackPane holder = new StackPane(inner);
        holder.setPrefSize(w, h);
        holder.setMinSize(w, h);
        holder.setMaxSize(w, h);
        holder.setAlignment(Pos.TOP_LEFT);
        holder.setPickOnBounds(false);
        return holder;
    }

    private static Pos alignment(String align) {
        return switch (align == null ? "left" : align.toLowerCase()) {
            case "center" -> Pos.CENTER;
            case "right" -> Pos.CENTER_RIGHT;
            default -> Pos.CENTER_LEFT;
        };
    }
}
