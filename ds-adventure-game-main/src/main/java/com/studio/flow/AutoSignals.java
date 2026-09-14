package com.studio.flow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <b>引擎自动信号</b>：场景切换时由读取器自动发出，地图里不用声明 {@code signal = …}，
 * 直接写 {@code slot = 场景进入 | …} 就能挂上逻辑。
 *
 * <p>三个信号的分工（也是它们在时间轴上的顺序）：</p>
 * <pre>
 *   点按钮 goto / 对话走完 next
 *        │
 *        ├─ ①「场景离开」发给<b>正要离开的那一幕</b>（它的节点还在，槽还能改它们／存状态／停定时器）
 *        │     参数：scene=被离开的场景, to=即将进入的场景
 *        │
 *        ├─ ②「场景进入」发给<b>新渲染好的那一幕</b>（节点已建好，槽可以立刻改属性）
 *        │     参数：scene=当前场景, from=上一个场景名（第一幕为空）
 *        │
 *        └─ ③「上一个场景离开」发给<b>新的一幕</b>（＝把“离开”这件事也告诉下一幕）
 *              参数：scene=被离开的场景, to=当前场景
 * </pre>
 *
 * <p>为什么要有 ③：①只发给了旧场景，旧场景的槽在新场景里早就不在了；
 * 如果新场景想对“刚离开的那一幕”做反应（记录来路、播转场、接续上一幕的倒计时……），
 * 就需要一个落在<b>新场景</b>头上的“离开”信号 —— 这就是「上一个场景离开」。</p>
 *
 * <p>别名会与规范名<b>一起发出</b>（同一组里写哪个都能收到）；参数用 {@code @param(名)} 取。</p>
 */
public final class AutoSignals {

    private AutoSignals() { }

    /** 一个自动信号的定义 */
    public static final class Def {
        private final String name;
        private final List<String> aliases;
        private final String when;
        private final String params;
        private final String hint;

        Def(String name, List<String> aliases, String when, String params, String hint) {
            this.name = name;
            this.aliases = Collections.unmodifiableList(new ArrayList<>(aliases));
            this.when = when;
            this.params = params;
            this.hint = hint;
        }

        /** 规范名（推荐写法） */
        public String name() { return name; }
        /** 别名（也会一起发出） */
        public List<String> aliases() { return aliases; }
        /** 什么时候发 */
        public String when() { return when; }
        /** 参数说明 */
        public String params() { return params; }
        /** 一句话用途 */
        public String hint() { return hint; }
        /** 规范名 + 别名 */
        public List<String> allNames() {
            List<String> out = new ArrayList<>();
            out.add(name);
            out.addAll(aliases);
            return out;
        }
    }

    /** ① 进入新场景（节点已建好） */
    public static final Def ENTER = new Def("场景进入",
            List.of("进入场景", "场景开始"),
            "切换到某一幕、渲染完成后",
            "scene=当前场景　from=上一个场景名（第一幕为空）",
            "进场景时初始化变量、放 BGM、启动定时器");

    /** ② 离开旧场景（旧场景的节点还在） */
    public static final Def LEAVE = new Def("场景离开",
            List.of("离开场景", "场景结束"),
            "即将切走、旧场景还没被清掉时",
            "scene=被离开的场景　to=即将进入的场景",
            "离开前存档、停定时器、收尾演出");

    /** ③ 把“离开”告诉新的一幕 */
    public static final Def PREV_LEAVE = new Def("上一个场景离开",
            List.of("上一幕离开", "上个场景离开"),
            "新场景渲染完成后（紧跟在「场景进入」之后）",
            "scene=被离开的场景　to=当前场景",
            "在新场景里对“刚离开的那一幕”做反应（记录来路、接续上一幕的定时器）");

    /** 全部自动信号（编辑器快捷按钮与帮助文档按这个顺序列） */
    public static final List<Def> ALL = List.of(ENTER, LEAVE, PREV_LEAVE);

    /** 这个名字是不是自动信号（含别名；编辑器据此提示“不用声明”） */
    public static boolean isAuto(String name) {
        if (name == null) return false;
        String n = name.trim();
        for (Def d : ALL) {
            for (String s : d.allNames()) {
                if (s.equals(n)) return true;
            }
        }
        return false;
    }

    /** 这个场景槽/信号名字是否属于某个自动信号的任意别名 */
    public static Def find(String name) {
        if (name == null) return null;
        String n = name.trim();
        for (Def d : ALL) {
            for (String s : d.allNames()) {
                if (s.equals(n)) return d;
            }
        }
        return null;
    }
}
