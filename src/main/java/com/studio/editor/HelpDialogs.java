package com.studio.editor;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.stage.Window;

/**
 * 「帮助 → 使用帮助」窗口：把编辑器需要知道的东西一次讲清楚。
 *
 * <p>四个页签：地图脚本语法 / 内联 CSS 样式 / 信号·槽·表达式·插件 / 快捷键与操作。</p>
 */
final class HelpDialogs {

    private HelpDialogs() { }

    static void show(Window owner) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("使用帮助 — 剧情编辑器 Studio");
        dialog.setHeaderText("📖 脚本语法 · 内联 CSS · 信号/槽/表达式/插件 · 快捷键");
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("关闭", ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefSize(960, 660);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                tab("📜 地图脚本", scriptHelp()),
                tab("🎨 内联 CSS 样式", cssHelp()),
                tab("🔗 信号 / 槽 / 表达式 / 插件", flowHelp()),
                tab("⌨ 快捷键与操作", shortcutHelp()));
        dialog.getDialogPane().setContent(tabs);
        dialog.showAndWait();
    }

    private static Tab tab(String title, String text) {
        TextArea area = new TextArea(text);
        area.setEditable(false);
        area.setWrapText(false);
        area.setStyle("-fx-font-family: 'Consolas', 'Microsoft YaHei', monospace; -fx-font-size: 12.5px;");
        Tab t = new Tab(title);
        t.setContent(area);
        return t;
    }

    // =====================================================================
    // 1) 地图脚本
    // =====================================================================

    private static String scriptHelp() {
        return """
                ============ 地图 = 文件夹 ============
                地图文件夹/
                ├── scenario.txt          脚本（下面这些内容）
                ├── resources/            素材（图片/音频…，脚本里用相对路径引用）
                ├── saves/                存档（运行时生成）
                └── logic/                该地图专属逻辑（可选）

                ============ [option] 全局设置（只能一个，放最前面） ============
                # 井号开头是注释
                [option]
                initialScene = 运算台          ← 初始场景（播放器从这里开始）
                background   = #101322         ← 背景色（十六进制或颜色名）
                volume       = 0.8             ← 全局音量 0..1
                typewriterSpeed = 16           ← 打字机速度（毫秒/字，0 = 关闭逐字）
                savevar = 金币 | int | 0        ← 存档变量（可写多行）：名称 | 类型 | 初值
                savevar = 玩家名 | str | 无名氏    类型：int / long / double / bool / str
                savevar = 已通关 | bool | false    变量随存档保存，槽里用 @var(名称) 读写

                ============ [场景名] 场景 ============
                [运算台]
                next  = 结算                   ← 场景属性：没有按钮时的“下一场景”
                event = minesweeper            ← 场景属性：进入本场景时运行的插件事件
                signal = 重置变量 | key | R | press                          ← 场景级信号（键盘）
                slot   = 重置变量 | set | 结果文本 | text | value=@var(num2)  ← 场景级槽
                {                              ← 节点块：一个 { } 就是一个画面对象
                ...
                }

                ============ { } 节点 ============
                {
                type  = textbox                ← 节点类型（见下表）
                id    = 名字输入                ← 节点名：信号/槽里用它指定目标
                index = 3                      ← 层级：0 = 最底层，越大越靠上
                x = 90
                y = 150
                width = 420
                height = 48
                text  = 请输入你的名字           ← 显示文字（对话/文本支持富文本标记）
                text  = <<<                    ← 多行文本用 Heredoc：<<< 换行 内容 换行 <<<
                第一行
                第二行
                <<<
                style = -fx-text-fill: #8fe3ff;    ← 内联 CSS（见“内联 CSS 样式”页）
                visible = true
                fontSize = 18
                align = center                 ← left / center / right
                opacity = 0.8
                transition = scale/opacity:300ms   ← 属性变化时的过渡动画
                multiline = true               ← 文本框专用：多行
                bind = 玩家名                   ← 文本框专用：输入内容实时写入该存档变量
                path  = resources/images/bg.png    ← 图片/立绘素材（相对地图文件夹）
                video = resources/video/opening.mp4 ← 视频素材：设了就代替图片渲染该节点（可当“会动的背景图”）
                audio = resources/sounds/bgm.wav   ← 【已废弃】进场景自动循环播放；建议改用 @plugin(audio)
                action = target                ← 按钮动作：target 跳场景 / save / load / event
                target = 结算
                signal = 点击 | mouse | click
                slot   = 点击 | set | 结果文本 | text | value=num2 = @var(num2)
                }

                ============ 节点类型 type ============
                bg      背景（铺满 1280×720）
                char    立绘（图片，等比适配）
                text    文本标签
                textbox 文本框（可输入，单行/多行 + 绑定存档变量）
                name    人物名字牌
                dialog  对话（支持 --- 分段 + 打字机）
                button  按钮（可绑动作 / 事件 / 信号槽）
                music   音乐轨（只播音频，不占画面）

                ============ 中文别名也能直接写 ============
                类型 / 名称 / 层级 / 坐标x / 坐标y / 宽度 / 高度 / 图片 / 音频 / 文字 / 内容 /
                样式 / 事件 / 动作 / 目标 / 可见 / 字号 / 对齐 / 透明度 / 逐字 / 过渡 /
                信号 / 槽 / 多行 / 绑定变量 … 都会被解析器自动归一化。

                富文本标记（text 里可用）：<b>粗</b> <i>斜</i> <u>下划线</u>
                <color:#ffd76a>彩色</color> <size:22>字号</size> <br> 换行
                """;
    }

    // =====================================================================
    // 2) 内联 CSS
    // =====================================================================

    private static String cssHelp() {
        return """
                ============ 节点属性 style = 一行内联 CSS ============
                style = -fx-text-fill: #8fe3ff; -fx-background-color: rgba(20,22,40,0.55); -fx-background-radius: 12;

                规则：多个属性用分号分隔；属性名必须以 -fx- 开头；颜色里的 # 不是注释，
                所以尽量给每条属性都写上结尾分号（漏写分号会导致后面整段失效）。

                ============ 常用属性速查 ============
                文字
                  -fx-text-fill: #f2f3ff;              文字颜色
                  -fx-font-size: 20px;                 字号（也可用节点的 fontSize 属性）
                  -fx-font-family: "Microsoft YaHei";  字体
                  -fx-font-weight: bold;               粗体
                  -fx-font-style: italic;              斜体
                  -fx-underline: true;                 下划线
                  -fx-text-alignment: center;          多行文本对齐
                背景
                  -fx-background-color: #171822;                纯色底色
                  -fx-background-color: rgba(20,22,40,0.55);    半透明底色（0.55 = 透明度）
                  -fx-background-radius: 12;                    圆角
                  -fx-background-insets: 0 4 0 4;               背景内缩（上右下左）
                  -fx-background-image: url("resources/images/x.png");
                  -fx-background-size: cover;                    cover / contain / 100% 100%
                边框
                  -fx-border-color: #6f79b8;
                  -fx-border-width: 2;
                  -fx-border-radius: 8;
                  -fx-border-style: dashed;            solid（默认）/ dashed / dotted
                间距与阴影
                  -fx-padding: 6 10 6 10;                              内边距：上 右 下 左
                  -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 12, 0.3, 0, 4);
                  -fx-effect: innershadow(gaussian, rgba(0,0,0,0.5), 8, 0.2, 0, 0);
                其它
                  -fx-opacity: 0.6;                                    整体透明度（也可用 opacity 属性）
                  -fx-cursor: hand;                                    鼠标指针（hand / move / text）
                  -fx-wrap-text: true;                                 文本自动换行

                ============ 颜色写法 ============
                #8fe3ff        十六进制（可写 #8fe3ff / #8f8 简写）
                rgb(255, 0, 0)
                rgba(0, 0, 0, 0.5)     最后一位是透明度 0..1
                transparent / white / black / gold / skyblue …（JavaFX 内置颜色名）

                ============ 小贴士 ============
                · 想“整块半透明底板 + 亮色文字”：-fx-background-color: rgba(20,22,40,0.55); -fx-text-fill: #f2f3ff;
                · 想“圆角按钮感”：-fx-background-color: #3d4060; -fx-background-radius: 12;
                  -fx-border-color: rgba(255,255,255,0.28); -fx-border-radius: 12;
                · 编辑器和播放器用的是同一套样式，改完即时生效、所见即所得。
                · 对话/文本节点的 style 会作用在文字层；背景类节点（bg）会作用在图片上。
                """;
    }

    // =====================================================================
    // 3) 信号 / 槽 / 表达式 / 插件
    // =====================================================================

    private static String flowHelp() {
        return """
                ============ 一、一行式写法 ============
                信号：signal = 名称 | mouse|key | click|release|按键码 | press|release | 参数k=v
                槽：  slot   = 信号名 | 动作 | 目标 | 参数 | 附加参数

                节点级信号（本节点被点到/按键时发出）：
                  signal = 点击 | mouse | click              鼠标左键点击
                  signal = 松开 | mouse | release            鼠标释放
                  signal = 点亮键L | key | L | press         键盘 L 按下（本节点收到）
                场景级信号（地图全局监听器收到后按名字分发）：
                  signal = 重置变量 | key | R | press        写在 [场景] 段里

                槽：写在节点块或场景段里，按“信号名”订阅：
                  slot = 点击   | call   | | signallab                             转交 Java 逻辑层
                  slot = 点击   | emit   | 灯 | 亮灯                                向目标节点发信号
                  slot = 亮灯   | set    | @self | style | value=-fx-opacity:1;    改属性
                  slot = 快捷键F | toggle | 提示 | visible                          布尔切换
                  slot = 点击   | goto   | | 森林                                   跳转场景
                  slot = 点击   | save   | | slot1                                 写存档
                  slot = 点击   | load   | | slot1                                 读存档
                  slot = 点击   | log    | | 已保存到 slot1                         日志+顶部提示
                  slot = 加号   | @plugin(add) | @var(num1) | @double(1.05) | @var(num2)

                ============ 二、动作一览 ============
                set        改节点属性：参数写 value=值 或 valueVar=变量名；
                           目标写成 @var(变量名) 时，改成“把结果写进存档变量”
                toggle     布尔切换（如 visible）
                emit       向 目标节点（留空 = 场景）发出 参数 里的信号，参数继续传递
                goto       跳转到 参数 里的场景
                save/load  读写 参数 指定的存档槽位（地图/saves/槽位.txt）
                call       调用 maps 工程师写的 Java 逻辑（logic/ 目录，见 README 第六节）
                log        输出到日志 + 顶部提示
                @plugin(名) 调用插件：动作之后的所有字段按顺序求值成数组传给插件，
                           只有写成 @var(x) 的位置会把插件返回的值写回变量

                参数合并优先级：事件上下文 → 信号附带参数 → 槽附带参数

                ============ 三、表达式（任何槽字段都能用，可嵌套） ============
                @var(名称)                 取存档变量（@var(名称, 默认值) 可给默认值）
                @int(x) @long(x) @double(x) @bool(x) @str(x)
                                           强制类型转换：转换失败退回默认值，绝不报错
                                           （@int(abc) → 0、@int(12px) → 12）
                @node(属性名)              触发本次信号的节点自身的属性
                @node(节点id, 属性名)      指定节点的属性
                @param(名称)               信号事件附带的参数
                例：value=@int(@var(分数))      value=金币: @var(金币) 个      value=@node(width)
                布尔与数字可以互转：@int(@var(开关变量)) → true=1 / false=0；
                                    @bool(@var(数量))     → 0=false / 非 0=true

                ============ 四、重要：槽是“按信号名”全场景订阅的 ============
                同一个场景里，所有同名信号的槽都会一起触发（不只属于发出信号的那个节点）。
                所以多个按钮请各用各的信号名（例：加号 / 加金币 / 取整 / 回显），
                否则点一个按钮会把别的按钮的槽也一起跑了。

                ============ 五、插件 ============
                编辑器自带（随编辑器发行，直接写 ID，无需注册、也不用改任何外部文件）：

                  算术：add sub mul div mod pow min max（二元）
                        abs round floor ceil neg（一元）
                        set（复制） inc（+1） dec（-1）
                  逻辑：and（逻辑乘/与） or（逻辑加/或） xor（异或） not（逻辑非，一元）
                  比较：gt(>) lt(<) ge(>=) le(<=) eq(==) ne(!=)
                  音频：audio（放音频，不再依赖节点的 audio 属性）
                        slot = 场景进入 | @plugin(audio) | loop | resources/audio/theme.mp3 | bgm
                        slot = 点击     | @plugin(audio) | play | resources/audio/click.wav | se
                        动作：loop 循环 / play 一次性 / stop / stopall / pause / resume / volume
                        通道名可省略（循环默认 bgm、一次性默认 se）；音量默认取 [option] volume
                  视频：video（把视频挂到节点上，代替它的图片/背景）
                        slot = 场景进入 | @plugin(video) | play  | 背景 | resources/video/opening.mp4 | loop
                        slot = 点击     | @plugin(video) | pause | 电视
                        动作：play 嵌入 / pause / resume / volume / stop（清空后回到图片）
                        也可以在节点属性里直接填「视频 (video)」，进场景即自动循环

                例：
                  slot = 加号 | @plugin(add) | @var(num1) | @double(1.05) | @var(num2)   → num2 = num1 + 1.05
                  slot = 刷新 | @plugin(and) | @var(灯1) | @var(灯2) | @var(灯3)          → 两盏都亮才让灯3 亮
                  slot = 刷新 | @plugin(gt)  | @var(点亮数) | @int(1) | @var(两盏都亮)     → 点亮数 > 1
                  slot = 开关1按下 | @plugin(not) | @var(灯1) | @var(灯1)                   → 点一下翻转灯1

                参数约定：**最后一个参数是输出位置，前面的都是输入**。
                回写规则：输出位一定会写回；其它位置只有当插件确实改过它的值时才写回
                （所以 @int(@var(灯1)) 这种“带转换的输入”不会被结果覆盖）。
                逻辑/比较的结果写成 true / false，配合 bool 类型的存档变量使用。

                自定义插件（工程师）：
                  1) 实现 com.studio.flow.SlotPlugin（模板见 plugins/examples/VarPluginTemplate.java）
                  2) 编译到 plugins/classes
                  3) 在 plugins/varplugins.ini 注册：我的插件 = com.example.MyPlugin
                     （也可以直接写全限定类名：@plugin(com.example.MyPlugin)）
                  可用能力：ctx.var/setVar（按声明类型强制转换）、ctx.readSave/writeSave（读写存档文件）、
                  ctx.emit/subscribe（发出/接收信号）、ctx.setProperty/setText（引擎负责渲染）、
                  ctx.locked(...)（多步原子操作，引擎保证线程安全）。
                """;
    }

    // =====================================================================
    // 4) 快捷键
    // =====================================================================

    private static String shortcutHelp() {
        return """
                ============ 快捷键 ============
                Ctrl+Z              撤销（编辑菜单里也能点，会显示将要撤销的操作名）
                Ctrl+Y              重做
                Ctrl+Shift+Z        重做（同 Ctrl+Y）
                Delete              删除选中节点
                Ctrl+D              复制选中节点
                Ctrl+E              编辑选中节点的完整属性
                Ctrl+S              保存地图
                Ctrl+O              打开地图文件夹
                Ctrl+R              在播放器中测试当前地图
                Ctrl+↑ / Ctrl+↓     选中节点上移一层 / 下移一层
                Ctrl+= / Ctrl+-     放大 / 缩小
                Ctrl+0              适应窗口

                ============ 鼠标操作 ============
                画布右键（节点上或空白处都是同一个菜单，会先选中右键处的节点）：
                  ➕ 添加节点（子菜单选类型，生成在右键位置）
                  ✏️ 编辑属性… / 📋 复制节点（Ctrl+D） / ⬆ 上移一层 / ⬇ 下移一层 / 🗑 删除节点
                  🧹 清空本场景全部节点（最后一项）
                  说明：没选中节点时，中间几项会置灰；背景节点（bg）不参与命中，
                        所以在整屏背景上右键仍算“空白处”。
                左侧层级树右键       场景：设为当前 / 重命名 / 删除 / 添加子节点
                                     节点：编辑属性 / 复制 / 删除
                双击节点             打开完整属性窗口（改完即时生效，【取消】可整体还原）；
                                     里面的「信号 / 槽」都是可增删的表格，选中一行可载入一行式文本再改
                拖动节点             改坐标（一次拖动 = 一步撤销）
                工具箱               画布左上角常驻；按住条目拖到画布上即生成节点
                点击界面任意位置     收起已打开的右键菜单

                ============ 撤销 / 恢复的覆盖范围 ============
                添加/删除/复制节点、拖动节点、上下移层级、清空场景、
                修改节点属性、地图全局设置（含存档变量增删）、
                新增/重命名/删除场景 —— 以上操作都能撤销与重做（默认保留最近 80 步）。

                ============ 快捷入口 ============
                文件 → 打开存档变量演示地图（变量/表达式/插件）…   一键生成并打开示例，边看边学
                场景 → 地图全局设置 [option]…                     初始场景 / 背景色 / 音量 / 存档变量
                场景 → 🧩 场景属性（属性 / 节点 / 信号槽）…         一个窗口看全场景：属性表、节点列表、场景信号与槽
                   · 属性页：直接改 / 增删 [场景名] 段的键值（next、event 等）
                   · 节点页：点一行就在画布选中它并在下方显示该节点全部属性，双击直接编辑
                   · 信号/槽页：用一行式文本查看、新增、修改、删除场景级信号与槽
                帮助 → 脚本语法速查…                              只用速查时看这个更短
                """;
    }
}
