package com.studio.model;

import java.util.Locale;

/**
 * 剧情节点类型。
 * <p>
 * 画布上的每个对象都对应一种类型，决定其在读取器中的 JavaFX 控件形态：
 * 背景(填满画面)、立绘(图片)、文本、文本框(可输入)、人物名、对话、按钮、系统提示(toast)。
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
    /**
     * 系统提示（Toast）：屏幕角上的小提示条，例如右上角显示“存档中…”。
     * <p>自带一套默认样式（深色圆角底 + 白字 + 投影），不需要美工素材；
     * 显示后停留 {@code duration} 秒（默认 {@link StoryNode#DEFAULT_TOAST_SECONDS} 秒）自动淡出。
     * 用槽 {@code set | 节点id | visible | value=true} 就能随时弹一次提示。</p>
     */
    TOAST("toast", "系统提示", "🔔", 320.0, 56.0);

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
        // 常用简称（历史写法/顺手写法），别让地图工程师为了“提示”两个字去查文档
        if (s.equals("提示") || s.equals("系统提示条") || s.equals("toast提示")) return TOAST;
        return TEXT; // 未知类型回退为文本，调用方可通过警告列表感知
    }

    /** 这个字符串是不是“认识的类型”（解析器据此给出“已废弃/写错了”的提示） */
    public static boolean isKnown(String raw) {
        if (raw == null) return false;
        String s = raw.trim();
        for (NodeType t : values()) {
            if (t.code.equalsIgnoreCase(s) || t.name().equalsIgnoreCase(s)
                    || t.display.equals(s) || (t.icon != null && t.icon.equals(s))) return true;
        }
        return s.equals("提示") || s.equals("系统提示条") || s.equals("toast提示");
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
            case TOAST  -> {
                // 系统提示就是“屏幕角上的小条”：直接放到逻辑画面右上角，工程师不用自己算坐标
                n.setText("提示：已保存");
                n.setX(toastX(n.getWidth()));
                n.setY(StoryNode.TOAST_MARGIN);
            }
            default     -> { /* 图片类节点没有默认文字 */ }
        }
        // 为中文可读性生成一个默认 id（仅作编辑器内标识，可留空）
        n.setId(t.display() + "_" + (int) n.getX() + "_" + (int) n.getY());
        return n;
    }

    /** 系统提示默认摆放的横坐标（逻辑画面右上角，留出边距） */
    public static double toastX(double width) {
        return CANVAS_WIDTH - Math.max(40, width) - StoryNode.TOAST_MARGIN;
    }

    /** 逻辑画面默认宽（与读取器的逻辑画布一致） */
    public static final double CANVAS_WIDTH = 1280.0;

    /** 供字符串拼接/调试使用 */
    @Override
    public String toString() {
        return display + "(" + code + ")";
    }

    /** 把标识变成小写形式，便于别名查找 */
    public String lowerName() { return name().toLowerCase(Locale.ROOT); }
}
