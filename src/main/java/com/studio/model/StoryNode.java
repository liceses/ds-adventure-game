package com.studio.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.studio.flow.SignalDef;
import com.studio.flow.SlotDef;

/**
 * 剧情节点（对应 scenario.txt 中的一个 { ... } 块）。
 * <p>
 * 属性与脚本一一对应；对于脚本中出现而本类尚未建模的“未知键”，
 * 会原样保留在 {@link #extras} 中，保证 读取脚本 ⇄ 反向序列化 无损往返。
 */
public class StoryNode {

    /** 规范键：写文件时输出的固定顺序 */
    public static final String[] SCRIPT_ORDER = {
            "type", "id", "index", "x", "y", "width", "height",
            "path", "video", "audio", "text", "multiline", "bind", "style", "event", "action", "target",
            "visible", "fontSize", "align", "opacity"
    };

    /** 中文/英文别名 → 规范键（解析器容错用） */
    public static final Map<String, String> KEY_ALIAS = new LinkedHashMap<>();
    static {
        KEY_ALIAS.put("类型", "type");
        KEY_ALIAS.put("名称", "id");
        KEY_ALIAS.put("编号", "id");
        KEY_ALIAS.put("x", "x");          KEY_ALIAS.put("坐标x", "x");     KEY_ALIAS.put("坐标X", "x");
        KEY_ALIAS.put("y", "y");          KEY_ALIAS.put("坐标y", "y");     KEY_ALIAS.put("坐标Y", "y");
        KEY_ALIAS.put("width", "width");  KEY_ALIAS.put("宽度", "width");  KEY_ALIAS.put("宽", "width");
        KEY_ALIAS.put("height", "height");KEY_ALIAS.put("高度", "height"); KEY_ALIAS.put("高", "height");
        KEY_ALIAS.put("path", "path");    KEY_ALIAS.put("图片", "path");   KEY_ALIAS.put("立绘路径", "path"); KEY_ALIAS.put("路径", "path");
        KEY_ALIAS.put("audio", "audio");  KEY_ALIAS.put("音频", "audio");  KEY_ALIAS.put("音乐", "audio");  KEY_ALIAS.put("音效", "audio");
        // 视频素材：设了就由读取器用视频播放器渲染该节点（可当“会动的背景图”用）
        KEY_ALIAS.put("video", "video"); KEY_ALIAS.put("视频", "video"); KEY_ALIAS.put("影片", "video");
        KEY_ALIAS.put("text", "text");    KEY_ALIAS.put("文本", "text");   KEY_ALIAS.put("文字", "text");   KEY_ALIAS.put("内容", "text");
        KEY_ALIAS.put("style", "style");  KEY_ALIAS.put("样式", "style");  KEY_ALIAS.put("内联样式", "style");
        KEY_ALIAS.put("event", "event");  KEY_ALIAS.put("事件", "event");
        KEY_ALIAS.put("action", "action");KEY_ALIAS.put("动作", "action"); KEY_ALIAS.put("行为", "action");
        KEY_ALIAS.put("target", "target");KEY_ALIAS.put("目标", "target"); KEY_ALIAS.put("目标场景", "target");
        KEY_ALIAS.put("visible", "visible"); KEY_ALIAS.put("可见", "visible"); KEY_ALIAS.put("显示", "visible");
        KEY_ALIAS.put("fontSize", "fontSize"); KEY_ALIAS.put("字号", "fontSize");
        KEY_ALIAS.put("align", "align");  KEY_ALIAS.put("对齐", "align");
        KEY_ALIAS.put("opacity", "opacity"); KEY_ALIAS.put("透明度", "opacity");
        KEY_ALIAS.put("type", "type"); KEY_ALIAS.put("id", "id"); KEY_ALIAS.put("visible", "visible");
        KEY_ALIAS.put("fontSize", "fontSize"); KEY_ALIAS.put("align", "align"); KEY_ALIAS.put("opacity", "opacity");
        // 逐字显示开关（主要作用于对话/文本节点；null 表示按类型默认：对话=开）
        KEY_ALIAS.put("typewriter", "typewriter"); KEY_ALIAS.put("逐字", "typewriter");
        KEY_ALIAS.put("逐字显示", "typewriter"); KEY_ALIAS.put("打字机", "typewriter");
        // 文本列表：textList/台词列表 是 text 的别名 —— 用独立一行的 --- 分隔多段台词
        KEY_ALIAS.put("textList", "text"); KEY_ALIAS.put("台词列表", "text");
        KEY_ALIAS.put("文本列表", "text"); KEY_ALIAS.put("多段台词", "text");
        // 信号与槽（可重复出现，逐行追加）
        KEY_ALIAS.put("signal", "signal"); KEY_ALIAS.put("信号", "signal");
        KEY_ALIAS.put("slot", "slot"); KEY_ALIAS.put("槽", "slot"); KEY_ALIAS.put("槽位", "slot");
        // 过渡动画：如 scale/opacity:300ms
        KEY_ALIAS.put("transition", "transition"); KEY_ALIAS.put("过渡", "transition");
        KEY_ALIAS.put("过渡动画", "transition"); KEY_ALIAS.put("动画", "transition");
        // 层级顺序（0 = 最底层；数值越大越靠上），可在属性窗口直接改
        KEY_ALIAS.put("index", "index"); KEY_ALIAS.put("层级", "index");
        KEY_ALIAS.put("顺序", "index"); KEY_ALIAS.put("序号", "index"); KEY_ALIAS.put("层", "index");
        // 文本框（TEXTBOX）专用：多行开关 + 绑定的存档变量名
        KEY_ALIAS.put("multiline", "multiline"); KEY_ALIAS.put("多行", "multiline");
        KEY_ALIAS.put("多行文本", "multiline"); KEY_ALIAS.put("多行模式", "multiline");
        KEY_ALIAS.put("bind", "bind"); KEY_ALIAS.put("绑定变量", "bind");
        KEY_ALIAS.put("变量", "bind"); KEY_ALIAS.put("绑定", "bind");
    }

    private NodeType type = NodeType.TEXT;
    private String id = "";
    private double x, y, width, height;
    private String path = "";    // 图片/立绘相对路径（相对地图根目录）
    private String video = "";   // 视频相对路径（设了就代替图片渲染该节点，可循环播放）
    private String audio = "";   // 音效/背景音乐路径
    private String text = "";    // 显示文字（支持富文本标记）
    private String style = "";   // 内联 CSS 样式（JavaFX -fx-* 属性）
    private String event = "";   // 该节点触发时运行的插件 ID
    private String action = "";  // 按钮内置动作：target/skip/save/load/speed/log/event
    private String target = "";  // action=target 时跳转的场景名
    private boolean visible = true;
    private double fontSize = 0; // 0 = 使用全局/默认字号
    private String align = "left";
    private double opacity = 1.0;
    /** null=按类型默认（对话开启逐字），true/false=强制开/关 */
    private Boolean typewriter = null;

    /** 层级顺序：0 = 最底层，数值越大越靠上（与场景内节点列表顺序保持一致） */
    private int index = 0;

    /** 文本框专用：是否多行（true 用多行输入框，false 用单行输入框） */
    private boolean multiline = false;

    /** 文本框专用：绑定的存档变量名（输入内容实时写入该变量，留空表示不绑定） */
    private String bind = "";

    /** 本节点可发出的信号（鼠标点击/释放、按键等） */
    private final java.util.ArrayList<SignalDef> signals = new java.util.ArrayList<>();
    /** 本节点订阅的槽（收到信号时执行的动作 / 转交逻辑层） */
    private final java.util.ArrayList<SlotDef> slots = new java.util.ArrayList<>();

    /**
     * 默认过渡动画（本节点属性被信号/槽/逻辑改变时生效），如 {@code scale/opacity:300ms}；
     * 留空 = 立即生效。
     */
    private String transition = "";

    /** 未知键保留区（按读取顺序），保证脚本无损往返 */
    private final LinkedHashMap<String, String> extras = new LinkedHashMap<>();

    // ---------------- 构造 ----------------

    public StoryNode() { }

    public StoryNode(NodeType type, double x, double y, double width, double height) {
        this.type = type;
        this.x = x; this.y = y; this.width = width; this.height = height;
    }

    public StoryNode copy() {
        StoryNode c = new StoryNode();
        c.type = type; c.id = id; c.x = x; c.y = y; c.width = width; c.height = height;
        c.path = path; c.video = video; c.audio = audio; c.text = text; c.style = style; c.event = event;
        c.action = action; c.target = target; c.visible = visible;
        c.fontSize = fontSize; c.align = align; c.opacity = opacity;
        c.typewriter = typewriter;
        c.transition = transition;
        c.index = index;
        c.multiline = multiline;
        c.bind = bind;
        for (SignalDef s : signals) c.signals.add(s.copy());
        for (SlotDef s : slots) c.slots.add(s.copy());
        c.extras.putAll(extras);
        return c;
    }

    // ---------------- 通用属性 ----------------

    public NodeType getType() { return type; }
    public void setType(NodeType type) { this.type = type; }

    public String getTypeCode() { return type.code(); }
    public void setTypeCode(String code) { this.type = NodeType.from(code); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id == null ? "" : id.trim(); }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path == null ? "" : path; }

    /** 视频素材路径（相对地图根目录）；非空时读取器用视频播放器渲染该节点 */
    public String getVideo() { return video; }

    public void setVideo(String video) { this.video = video == null ? "" : video.trim(); }

    public String getAudio() { return audio; }
    public void setAudio(String audio) { this.audio = audio == null ? "" : audio; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text == null ? "" : text; }

    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style == null ? "" : style; }

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event == null ? "" : event; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action == null ? "" : action.trim(); }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target == null ? "" : target; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public double getFontSize() { return fontSize; }
    public void setFontSize(double fontSize) { this.fontSize = fontSize; }

    public String getAlign() { return align; }
    public void setAlign(String align) { this.align = align == null ? "left" : align; }

    public double getOpacity() { return opacity; }
    public void setOpacity(double opacity) { this.opacity = opacity; }

    public Map<String, String> extras() { return extras; }

    /** 本节点可发出的信号列表（编辑器/引擎共用；随 scenario.txt 读写） */
    public java.util.List<SignalDef> signals() { return signals; }

    /** 默认过渡动画规格（如 {@code scale/opacity:300ms}）；空=立即生效 */
    public String getTransition() { return transition; }

    public void setTransition(String transition) {
        this.transition = transition == null ? "" : transition.trim();
    }

    /** 本节点订阅的槽列表 */
    public java.util.List<SlotDef> slots() { return slots; }

    // ---------------- 层级顺序 ----------------

    /** 层级下标（0 = 最底层，越大越靠上） */
    public int getIndex() { return index; }

    public void setIndex(int index) { this.index = Math.max(0, index); }

    // ---------------- 文本框（TEXTBOX） ----------------

    /** 是否多行文本（文本框节点专用） */
    public boolean isMultiline() { return multiline; }

    public void setMultiline(boolean multiline) { this.multiline = multiline; }

    public Boolean getMultiline() { return multiline; }

    /** 绑定的存档变量名（文本框节点专用；输入内容写入该变量） */
    public String getBind() { return bind; }

    public void setBind(String bind) { this.bind = bind == null ? "" : bind.trim(); }

    /** 是否是“可输入文本框”节点 */
    public boolean isTextBox() { return type == NodeType.TEXTBOX; }

    // ---------------- 便捷查询 ----------------

    /** 是否为纯音频轨（不占画面） */
    public boolean isMusicOnly() { return type == NodeType.MUSIC; }

    /** 是否拥有需要画面的控件 */
    public boolean needsVisual() { return type != NodeType.MUSIC; }

    /** 按规范键设置属性（解析器使用，遇未知键存入 extras） */
    public void setScriptProperty(String canonicalKey, String value) {
        switch (canonicalKey) {
            case "type"     -> setTypeCode(value);
            case "id"       -> setId(value);
            case "x"        -> setX(parseDoubleSafe(value, 0));
            case "y"        -> setY(parseDoubleSafe(value, 0));
            case "width"    -> setWidth(parseDoubleSafe(value, defaultByType()));
            case "height"   -> setHeight(parseDoubleSafe(value, defaultByType2()));
            case "path"     -> setPath(value);
            case "video"    -> setVideo(value);
            case "audio"    -> setAudio(value);
            case "text"     -> setText(value);
            case "style"    -> setStyle(value);
            case "event"    -> setEvent(value);
            case "action"   -> setAction(value);
            case "target"   -> setTarget(value);
            case "visible"  -> setVisible(parseBoolSafe(value, true));
            case "fontSize" -> setFontSize(parseDoubleSafe(value, 0));
            case "align"    -> setAlign(value);
            case "opacity"  -> setOpacity(parseDoubleSafe(value, 1.0));
            case "typewriter" -> setTypewriter(parseBoolSafe(value, true));
            case "transition" -> setTransition(value);
            case "index"    -> setIndex((int) parseDoubleSafe(value, 0));
            case "multiline" -> setMultiline(parseBoolSafe(value, false));
            case "bind"     -> setBind(value);
            default         -> extras.put(canonicalKey, value);
        }
    }

    /** 将节点导出为有序的“规范键 → 值”映射（序列化器使用），默认值会被省略以保持脚本干净 */
    public LinkedHashMap<String, String> toScriptMap() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("type", type.code());
        if (!id.isEmpty()) m.put("id", id);
        // 层级下标：显式落盘，便于手改脚本后用“按 index 排序”精确调层
        m.put("index", String.valueOf(index));
        m.put("x", trimDouble(x));
        m.put("y", trimDouble(y));
        if (width > 0 && width != type.defaultWidth()) m.put("width", trimDouble(width));
        if (height > 0 && height != type.defaultHeight()) m.put("height", trimDouble(height));
        if (!path.isEmpty()) m.put("path", path);
        if (!video.isEmpty()) m.put("video", video);
        if (!audio.isEmpty()) m.put("audio", audio);
        if (!text.isEmpty()) m.put("text", text);
        if (multiline) m.put("multiline", "true");
        if (!bind.isEmpty()) m.put("bind", bind);
        if (!style.isEmpty()) m.put("style", style);
        if (!event.isEmpty()) m.put("event", event);
        if (!action.isEmpty()) m.put("action", action);
        if (!target.isEmpty()) m.put("target", target);
        if (!visible) m.put("visible", "false");
        if (fontSize > 0) m.put("fontSize", trimDouble(fontSize));
        if (!"left".equals(align)) m.put("align", align);
        if (opacity < 1.0) m.put("opacity", trimDouble(opacity));
        if (typewriter != null) m.put("typewriter", typewriter ? "true" : "false");
        if (!transition.isEmpty()) m.put("transition", transition);
        // 未知键按原顺序补在尾部
        m.putAll(extras);
        return m;
    }

    // ---------------- 逐字显示 ----------------

    public Boolean getTypewriter() { return typewriter; }

    public void setTypewriter(Boolean typewriter) { this.typewriter = typewriter; }

    /**
     * 实际是否开启逐字显示。
     * 未显式设置时：对话(DIALOG)默认开启，其余类型默认关闭。
     */
    public boolean typewriterEffective() {
        if (typewriter != null) return typewriter;
        return type == NodeType.DIALOG;
    }

    private double defaultByType() { return type != null ? type.defaultWidth() : 0; }
    private double defaultByType2() { return type != null ? type.defaultHeight() : 0; }

    /** 把小数整理成尽可能短的字符串（整数值不带小数点） */
    public static String trimDouble(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        String s = String.valueOf(v);
        return s;
    }

    public static double parseDoubleSafe(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }

    public static boolean parseBoolSafe(String s, boolean def) {
        if (s == null) return def;
        String v = s.trim();
        if (v.equalsIgnoreCase("true") || v.equals("1") || v.equals("是")) return true;
        if (v.equalsIgnoreCase("false") || v.equals("0") || v.equals("否")) return false;
        return def;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StoryNode other)) return false;
        return Double.compare(other.x, x) == 0 && Double.compare(other.y, y) == 0
                && type == other.type && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(type, id, x, y); }

    @Override
    public String toString() {
        return type.display() + (id.isEmpty() ? "" : "[" + id + "]") + "@(" + trimDouble(x) + "," + trimDouble(y) + ")";
    }
}
