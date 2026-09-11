package com.studio.model;

import java.util.Locale;

/**
 * 剧情节点类型。
 * <p>
 * 画布上的每个对象都对应一种类型，决定其在读取器中的 JavaFX 控件形态：
 * 背景(填满画面)、立绘(图片)、文本、文本框(可输入)、人物名、对话、按钮、纯音乐轨。
 */
public enum NodeType {
    /** 背景图（填满整个逻辑画布） */
    BACKGROUND("bg", "背景", "🖼", 1280.0, 720.0),
    /** 角色立绘（图片，保持比例显示在指定区域内） */
    CHARACTER("char", "立绘", "👤", 320.0, 520.0),
    /** 普通文本标签 */
    TEXT("text", "文本", "📝", 420.0, 90.0),
    /** 文本框：玩家可输入的输入框（单行/多行，可绑定存档变量） */
    TEXTBOX("textbox", "文本框", "⌨", 420.0, 56.0),
    /** 人物名字牌 */
    NAME("name", "人物名", "🏷", 260.0, 48.0),
    /** 对话内容框（富文本 + 打字机效果） */
    DIALOG("dialog", "对话", "💬", 1000.0, 170.0),
    /** 按钮节点（可绑定动作/目标场景/插件事件） */
    BUTTON("button", "按钮", "🔘", 150.0, 52.0),
    /** 音乐轨：不渲染画面，仅负责循环播放 audio 资源 */
    MUSIC("music", "音乐", "🎵", 0.0, 0.0);

    private final String code;      // scenario.txt 中使用的规范键值
    private final String display;   // 中文显示名
    private final String icon;      // 工具箱图标（emoji）
    private final double defaultWidth;
    private final double defaultHeight;

    NodeType(String code, String display, String icon, double defaultWidth, double defaultHeight) {
        this.code = code;
        this.display = display;
        this.icon = icon;
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
    }

    public String code() { return code; }
    public String display() { return display; }
    public String icon() { return icon; }
    public double defaultWidth() { return defaultWidth; }
    public double defaultHeight() { return defaultHeight; }

    /**
     * 宽容解析类型：支持规范码(bg/char/...)、中文名(背景/立绘/...)、
     * 枚举英文名(BACKGROUND/CHARACTER/...)。
     */
    public static NodeType from(String raw) {
        if (raw == null) return TEXT;
        String s = raw.trim();
        for (NodeType t : values()) {
            if (t.code.equalsIgnoreCase(s)
                    || t.name().equalsIgnoreCase(s)
                    || t.display.equals(s)
                    || (t.icon != null && t.icon.equals(s))) {
                return t;
            }
        }
        return TEXT; // 未知类型回退为文本，调用方可通过警告列表感知
    }

    public String toScriptValue() { return code; }

    /** 依据类型在指定坐标处新建一个带默认属性的节点 */
    public static StoryNode createDefault(String typeCode, double x, double y) {
        NodeType t = from(typeCode);
        StoryNode n = new StoryNode();
        n.setType(t);
        n.setX(x);
        n.setY(y);
        n.setWidth(t.defaultWidth());
        n.setHeight(t.defaultHeight());
        switch (t) {
            case TEXT   -> n.setText("双击或右键编辑文字…");
            case TEXTBOX -> n.setText("请输入…");
            case NAME   -> n.setText("角色名");
            case DIALOG -> n.setText("「在这里输入对话内容……」");
            case BUTTON -> n.setText("按钮");
            default     -> { /* 图片类节点没有默认文字 */ }
        }
        // 为中文可读性生成一个默认 id（仅作编辑器内标识，可留空）
        n.setId(t.display() + "_" + (int) x + "_" + (int) y);
        return n;
    }

    /** 供字符串拼接/调试使用 */
    @Override
    public String toString() {
        return display + "(" + code + ")";
    }

    /** 把标识变成小写形式，便于别名查找 */
    public String lowerName() { return name().toLowerCase(Locale.ROOT); }
}
