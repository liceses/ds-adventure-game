# plugins 插件目录使用说明

> 📘 完整的协作规范（分支 / 提交 / PR / 自检清单 / 常见问题）见仓库根目录 [`CONTRIBUTING.md`](../CONTRIBUTING.md)。
> **课程作业推荐走「内置插件」形态**（源码放 `src/main/java/com/studio/plugin/demo/`，
> 在 `plugins.ini` 注册），因为本目录下的编译产物 `classes/`、`*.jar` 已被 `.gitignore` 忽略、无法入库。

本目录存放“外部 Java 程序”插件：工程师只需实现 `com.studio.plugin.GamePlugin`
接口，把编译产物放进来，读取器(Player)解析到场景/节点的 `event` 属性时就会
**动态加载**并把它嵌入主舞台中央（顶部自动带【返回】标题栏）。

## 目录职责
| 路径                      | 说明                                              |
|---------------------------|---------------------------------------------------|
| `plugins.ini`             | 事件插件注册表：事件ID = 类名（嵌入界面的那种插件）  |
| `varplugins.ini`          | **变量插件注册表**：`@plugin(名字)` = 类名          |
| `examples/`               | 外部插件示例源码（不会参与主工程编译，按下面方法自编译）|
| `classes/`（自建）        | 编译产物根目录（按包结构放置 *.class）              |
| `*.jar`（自建）           | 或打包成 jar 直接放这里                            |

## GamePlugin 接口约定（务必只依赖本接口，勿 import 内部实现类）
```java
public interface GamePlugin {
    // 引擎要求实现的入口（每次触发都会调用一次）
    void execute(javafx.stage.Stage stage, java.util.Map<String, Object> params);

    // 可选：返回要嵌入主舞台的界面；返回 null 则按“窗口模式”运行
    default javafx.scene.Parent createEmbeddedView(java.util.Map<String, Object> params) { return null; }

    default String displayName() { return getClass().getSimpleName(); }

    // 插件被移出主舞台时回调（见下方“生命周期”）
    default void onDetach() { }
}
```
params 约定键：`plugin.id`、`map.folder`(File)、`embedded`(Boolean)、`host.stage`、`back.callback`(Runnable)。

### 生命周期（重要）
| 时机 | 引擎行为 |
|------|----------|
| 进入 event=你的事件ID 的场景 / 点击 action=event 的按钮 | `execute()` 一次；随后若 `createEmbeddedView()` 返回界面则挂到主舞台中央 |
| 玩家点【← 返回剧情】 | `onDetach()` 一次，插件界面从主舞台移除 |
| 同一局里加载了**另一个**事件插件 | 旧实例先 `onDetach()`，再 `execute()` 新实例 |
| 关闭播放器窗口 | 当前事件插件 `onDetach()`；同时所有 `@plugin(...)` 变量插件收到 `SlotPlugin.onDetach()` |

`onDetach()` 里抛异常会被引擎兜住（只写日志），不会影响【返回剧情】和关闭流程；
但请在这里停掉自己起的线程 / `Timeline` / 媒体，否则它们会跟到下一局。
另外，插件处于“嵌入模式”时整个主舞台被插件层覆盖，剧情里的信号与按钮会自动暂停派发。

## 方式一：javac 快速编译（无需 Maven 工程）
先 `mvnw.cmd -q -DskipTests package` 产出 target/ds-adventure.jar，
然后（Windows 示例，注意替换 JDK/JavaFX 版本号；本工程为 JavaFX 17.0.20）：

```bat
set JFX=C:\Users\xxx\.m2\repository\org\openjfx
set CP=target\ds-adventure.jar;%JFX%\javafx-base\17.0.20\javafx-base-17.0.20-win.jar;%JFX%\javafx-graphics\17.0.20\javafx-graphics-17.0.20-win.jar;%JFX%\javafx-controls\17.0.20\javafx-controls-17.0.20-win.jar

javac -encoding UTF-8 -cp "%CP%" -d plugins\classes ^
      plugins\examples\ClockDemoPlugin.java
```

之后编辑 plugins.ini 取消 `clock = com.studio.external.ClockDemoPlugin` 的注释，
启动播放器打开示例地图 [Clock] 场景（或任何 event=clock 的场景）即可看到效果。

## 方式二：Maven 独立插件工程
```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId>
  <artifactId>my-plugin</artifactId>
  <version>1.0</version>
  <properties><maven.compiler.release>17</maven.compiler.release></properties>
  <dependencies>
    <dependency>
      <groupId>com.studio</groupId>
      <artifactId>ds-adventure</artifactId>
      <version>1.0.0</version>
      <scope>provided</scope>
    </dependency>
  </dependencies>
</project>
```
先在本工程根目录 `mvnw.cmd install -DskipTests`，再在插件工程 `mvn package`，
把 `my-plugin-1.0.jar` 丢进 plugins 即可。

## 扫雷 Demo
完整示例见主工程源码 `com.studio.plugin.demo.MinesweeperPlugin`：
默认事件ID `minesweeper`（内置注册），演示了嵌入视图、棋类交互与胜利/失败遮罩。
运行播放器打开示例地图 → [Start] → 点击【▶ 前往森林】即可体验插件跳转；
点上方【← 返回剧情】即无缝回到主界面。

---

# 变量插件（@plugin）—— 信号 / 槽里调用，用来算存档变量

事件插件（上面那种）负责“嵌入一个小游戏界面”；**变量插件**负责“在剧情里做运算与读写数据”，
它被整合进信号与槽：槽的动作写成 `@plugin(插件名)`。

## 脚本写法
```
# 槽：信号名 | 动作 | 参数1 | 参数2 | …（动作之后的所有字段按顺序求值成 args 字符串数组）
slot = 点击 | @plugin(add) | @var(num1) | @double(1.05) | @var(num2)   # num2 = num1 + 1.05
```
约定：**最后一个参数是输出位置，前面的都是输入**。插件返回同长度数组后，
宿主**只把原本写成 `@var(x)`（或 `@double(@var(x))`）的位置**写回变量，
写回时会按外层强制转换或 `[option]` 里声明的类型规范化。

## 接口约定（务必只依赖这些公开类型）
```java
public interface com.studio.flow.SlotPlugin {
    String[] execute(PluginContext ctx, String[] args);   // 必须实现；args 永不为 null
    default String name() { ... }
    default String description() { ... }
    default String usage() { ... }
    default void onAttach(PluginContext ctx) { }          // 首次加载
    default void onSignal(PluginContext ctx, SignalEvent ev) { }  // 订阅到的信号
    default void onDetach() { }
}
```
`PluginContext` 提供：
- **存档变量**：`var/intVar/doubleVar/boolVar`、`setVar/addVar`（按声明类型强制转换，随存档保存）；
- **存档文件**：`readSave/writeSave/deleteSave/listSaves/saveVar/setSaveVar`（直接读写 `地图/saves/*.txt`）；
- **信号**：`emit/emitScene` 发出；`subscribe("信号名")`（支持 `*` 通配）后实现 `onSignal` 接收；
- **渲染**：`setProperty/setText/setStyle/setVisible/gotoScene/toast/log`；
- **线程安全**：引擎用一把可重入锁串行化“插件执行 + 变量读写 + 存档读写 + 信号派发”，
  多步原子操作请用 `ctx.locked(() -> ...)`；渲染操作会被自动切回 JavaFX 线程
  （`ctx.isUiThread()` / `ctx.onUi(...)`），插件作者不必自己处理线程问题。

## 编译与注册
```bat
:: 1) 编译（classpath 指向主工程编译输出，或打好的 jar）
javac -encoding UTF-8 -cp target\classes -d plugins\classes plugins\examples\VarPluginTemplate.java
:: 2) 注册：编辑 plugins\varplugins.ini
::    我的插件 = com.example.VarPluginTemplate
:: 3) 脚本里用：slot = 点击 | @plugin(我的插件) | @var(a) | @double(1.05) | @var(b)
```
也可以不注册，直接写全限定类名：`slot = 点击 | @plugin(com.example.VarPluginTemplate) | @var(a)`。

## 自带插件（随编辑器/读取器发行，直接可用）
`add` `sub` `mul` `div` `mod` `pow` `min` `max`（二元）、
`abs` `round` `floor` `ceil` `neg`（一元）、`set` `inc` `dec`（赋值类）。
实现见主工程源码 `com.studio.plugin.builtin.MathPlugin`。
除零返回 0、非法输入按 0 计，**绝不抛异常中断剧情**。

逻辑与比较：`and` `or` `xor` `not` `gt` `lt` `ge` `le` `eq` `ne`
（实现见 `com.studio.plugin.builtin.LogicPlugin`；结果写 `true`/`false`，配合 bool 变量用）。

音频播放：`audio`（实现见 `com.studio.plugin.builtin.AudioPlugin`）——**不再依赖节点的 audio 属性**
```
slot = 场景进入 | @plugin(audio) | loop   | resources/audio/theme.mp3 | bgm
slot = 点击     | @plugin(audio) | play   | resources/audio/click.wav | se
slot = 静音     | @plugin(audio) | stop   | | bgm
slot = 暂停     | @plugin(audio) | pause  | | bgm
slot = 音量     | @plugin(audio) | volume | 0.5 | bgm
slot = 全停     | @plugin(audio) | stopall
```
参数：`动作 | 路径或数值 | 通道名`；通道名可省略（loop 默认 `bgm`、play 默认 `se`），
相对路径按**地图文件夹**解析，音量默认取 `[option] volume`。

视频嵌入：`video`（实现见 `com.studio.plugin.builtin.VideoPlugin`）——把视频挂到节点上，**代替该节点的图片**
```
slot = 场景进入 | @plugin(video) | play  | 背景 | resources/video/opening.mp4 | loop
slot = 点击     | @plugin(video) | pause | 电视
slot = 点击     | @plugin(video) | stop  | 电视
```
参数：`动作 | 节点id | 路径 | loop|once | 音量`；素材缺失或格式不支持时会自动退化为 🎬 占位块。
也可以在节点属性里直接填「视频 (video)」，进场景即自动循环播放（等价于会动的背景图）。

详见 `examples/VarPluginTemplate.java`（带完整中文注释的模板）。
