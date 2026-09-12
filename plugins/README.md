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
`add`、`减法`（别名 `minus`）、`mul` `div` `mod` `pow` `min` `max`（二元）——
`abs` `round` `floor` `ceil` `neg`（一元）、`set` `inc` `dec`（赋值类）。
实现见主工程源码 `com.studio.plugin.builtin.MathPlugin`。
除零返回 0、非法输入按 0 计，**绝不抛异常中断剧情**。

> ⚠ 减法为什么叫 `减法` 而不是 `sub`：`sub` 这个短名被**文本插件**的「截取」也注册了
> （`TextPlugin`），而自带插件表是“后注册者生效”，所以 `@plugin(sub)` 实际执行的是文本截取。
> 为了不让下拉框里列出来的写法被顶掉，数学减法改登记为 `@plugin(减法)` / `@plugin(minus)`。
> 现在「插件目录自检」（`BuiltinCatalog.selfCheck()`）会检查主 ID 撞名，撞了就会在探针里失败。

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

系统操作：`quit` `open` `pick` `reveal`（实现见 `com.studio.plugin.builtin.SystemPlugin`）——
让剧情菜单能退出游戏、打开文件，**不用写一行 Java 代码**：
```
slot = 退出游戏 | @plugin(quit)
slot = 看攻略   | @plugin(open) | 文档/攻略.txt      # 相对地图文件夹；也支持 http(s):// 链接
slot = 打开     | @plugin(open)                     # 不给路径 → 先弹文件选择框，再打开
slot = 选立绘   | @plugin(pick) | png;jpg | @var(选中立绘)   # 把选中的绝对路径写回变量
slot = 存档目录 | @plugin(reveal) | saves/slot1.txt  # 在资源管理器里打开所在目录并选中它
```
| ID | 中文别名 | 说明 |
|----|----------|------|
| `quit` | `exit` `退出` `退出游戏` | 退出游戏：交宿主收尾（释放媒体/插件）后关闭播放器窗口；<br>编辑器里的预览窗口只关预览本身，**宿主不支持时不会擅自结束进程** |
| `open` | `打开` `打开文件` | 用系统默认程序打开文件；参数留空则先弹文件选择框 |
| `pick` | `选择文件` `选文件` | 弹文件选择框，把选中的绝对路径写回输出位（玩家取消时**保持原值**） |
| `reveal` | `打开目录` `打开文件夹` `所在目录` | 在文件管理器里定位文件（Windows 下会选中它） |

文件不存在、系统没有默认程序、宿主无法退出等情况都只记日志 + 顶部提示，**不会中断剧情**；
文件选择框一定在 JavaFX 线程上弹出（后台线程调用会自动切回）。
示例地图 `maps/prologue_404` 的收尾幕就用这三个 ID 做了「存档 / 打开说明 / 定位文件 / 退出游戏」菜单。

## 自带插件全家福（P0/P1/P2）

除了上面这些，编辑器还自带下面这些插件族。**完整清单与每个 ID 的用法不用查文档**：
编辑器里打开「节点属性 → 槽」或「场景属性 → 信号·槽」，用「**插件**」下拉框选一个再点「＋ 插入插件槽」，
就会把一行可用的模板填进输入框。

> 这个下拉框列的是**所有可用插件**，不只是自带插件：
> ① 自带内置插件（含中文别名，见下表）；
> ② 你在 `plugins/varplugins.ini` / `plugins/plugins.ini` 里登记过的外部插件（工程根与地图目录两处都会扫）；
> ③ 放进 `plugins/classes/` 或 `plugins/*.jar`、**即使还没登记**的插件（按全限定类名列出，直接就能用）。
> 列表里的名字/说明/用法都是**真加载一次插件后从它自己身上取的**，不会和代码脱节；
> 只实现了 `GamePlugin` 的事件插件（如 `minesweeper`/`2048`/`breakout`…）不能用在 `@plugin` 槽里，
> 所以**不会**出现在这个列表里（它们要用节点/场景的 `event` 属性），编辑器会在日志里说明原因。

> **选中之前可以先打字筛选**：输入框里打 `full`、`全屏`、`截图`、`截` 之类都能立刻把列表缩小
> （匹配范围是 ID / 中文别名 / 分组 / 说明），七十多个插件里找 `fullscreen` 不用再滚动半天。

这个列表还有两道**自动保证**，避免“某个插件在下拉框里找不到”或“列出来了其实不能用”：

1. **兜底**：`BuiltinCatalog.all()` 会拿运行时的自带插件登记表（`PluginRuntime.builtinIds()`）对账，
   凡是运行时注册了、而手写清单里漏登记的 ID，自动补一条兜底项进列表（界面照常能看到、能选）；
2. **自检**：`BuiltinCatalog.selfCheck()` 会把“漏登记”“目录里有但运行时认不出”“两个插件撞同一个主 ID”
   都报出来，探针 `PluginPickerProbe` 断言它为空 —— 漏了/撞了就会当场失败。
   （`fullscreen` 就曾经因为整族漏登记而在下拉框里找不到，`sub` 则曾经被文本插件顶掉，两处现在都有回归断言。）

「帮助 → 使用帮助 → 插件」里的手册同样是**从代码自动生成**的
（`com.studio.plugin.builtin.BuiltinCatalog`），所以永远不会和代码不一致。

| 分组 | ID（别名） | 干什么 |
|------|-----------|--------|
| 流程 | `select`（如果/三目）、`after`（延时）、`every`（定时）、`stoptimer`（停止定时） | 条件选择、延时发信号、循环计时器 —— 给剧情补上「如果 / 稍后 / 每隔一会儿」 |
| 随机 | `rand`（随机） | 整数 / 小数 / 概率判定 / 从候选里随机挑一个 / 设定种子复现 |
| 交互 | `confirm`（确认）、`input`（输入） | 弹确认框（写 bool）、弹输入框（写文本），把玩家的回答变成变量 |
| 文本 | `concat` `upper` `lower` `trim` `len` `sub` `replace` `pad` `repeat` `split` | 拼接、大小写、长度、截取、替换、补齐、重复、切分 |
| 格式 | `num` `money` `percent` `duration` `bar` | 小数位、千分位、百分比、`mm:ss`、方块进度条 |
| 演出 | `cast`（立绘）、`bg`（背景）、`fx`（特效） | 立绘入退场/换表情、背景纯色/渐变/图片/淡入淡出、抖屏闪白心跳等特效预设 |
| 存档 | `slots` `hasslot` `delslot` `copyslot` `readslot` `writeslot` `saveinfo` | 存档槽位列表、存在判断、删除、复制、读写别的存档里的变量 |
| 数据 | `json`（数据） | 把一个 str 变量当 JSON 对象：写/读/累加/判断/删/列键/计数 |
| 列表 | `list`（列表） | 把一个 str 变量当数组：追加/弹出/删除/去重/清空/计数/取第 N 项/连接 |
| 系统 | `clipboard`（剪贴板）、`notify`（通知）、`screenshot`（截图）、`fullscreen`（全屏） | 剪贴板读写、系统通知、截图存到地图目录、切换全屏 |
| 网络 | `http`（请求）、`llm`（大模型） | 异步 HTTP 请求 / 调用 OpenAI 兼容接口（配置见 `[option]` 的 `AI密钥`/`AI地址`/`AI模型`），把结果写进变量 |
| 时间 | `clock`（时间） | 当前时间/日期、打点、耗时、星期、时段 |
| 调试 | `debug`（变量表）、`trace`（跟踪）、`dump`（导出） | 一键打印场景与全部变量、开关信号跟踪、把变量表导出成文件 |

### 两条新手最容易踩的约定

1. **参数是“值”，不是“名字”**：引擎会先把 `@var(背包)` 解析成它的当前值，再交给插件。
   所以「要改某个变量」的插件（`list` / `json`）必须把同一个变量**写两次**：
   ```ini
   slot = 捡到 | @plugin(list) | push | @var(背包) | 苹果 | @var(背包)
   ```
   前面的 `@var(背包)` 是读进来，最后的输出位是写回去。只读的查询（`get`/`has`/`count`）把结果写到别的变量即可。
2. **输出位永远是最后一个参数**：其它位置只有在插件确实改过它的值时才回写，
   所以 `@int(@var(灯1))` 这种“带转换的输入”不会被结果覆盖。

### 引擎自动信号（不需要声明 `signal`）

```ini
[某个场景]
slot = 场景进入 | @plugin(inc) | @var(进入次数) | @var(进入次数)
slot = 场景进入 | set | @var(上次进入) |  | value=@param(scene)
slot = 场景离开 | log |  | 要离开 @param(scene) 了
```
引擎在每次渲染场景时自动发 `场景进入`（本节渲染完成后，槽可以立刻改节点属性）与
`场景离开`（离开前，旧场景的节点还在），参数里带 `scene`。
这是「进场景自动存档 / 自动刷新派生变量 / 自动放一段演出」最常用的挂载点；
槽里如果再 `goto` 别的场景，引擎会防重入，不会无限递归。

### 异步插件怎么把结果写回变量

`http` / `llm` 的请求是异步的（不阻塞界面），返回时本次槽早就执行完了，因此：
- 结果会写进**输出位那个变量**（引擎把原始参数里的 `@var(名)` 交给了插件，见 `PluginContext.outputVarName()`）；
- 同时会向场景发一个**完成信号**（默认 `网络完成`，可以自己指定），参数里带 `结果`，
  需要的话再写一条槽把结果转存 / 触发后续剧情：
  ```ini
  slot = 问一问 | @plugin(llm) | 用一句话安慰玩家 | 回复到了 | @var(回复)
  slot = 回复到了 | set | 对话框 | text | value=@var(回复)
  ```

详见 `examples/VarPluginTemplate.java`（带完整中文注释的模板）。
