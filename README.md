# 🎬 剧情游戏编辑器（Studio）与 剧情游戏读取器（Player）

一款基于 **Java 17 + JavaFX 17**（纯 Java 构建 UI、无 FXML；编译目标 17，对齐《需求规格说明书》3.5 与实训检查项）的可视化视觉小说 / 剧情游戏编辑器与播放器。

- **Studio（编辑器）**：把地图当成“文件夹工程”，以场景为单位可视化排版节点（背景 / 立绘 / 文本 / 人物名 / 对话 / 按钮 / 音乐轨），所见即所得地编辑 `scenario.txt`。
- **Player（读取器）**：轻量级剧情引擎，加载地图文件夹并按脚本渲染；支持打字机、加速、伪类 hover/pressed 动画、场景音乐，以及**插件化事件**：把外部 Java 程序（如扫雷）动态加载并“嵌入主舞台中央”，一键返回剧情（浏览器标签页式跳转）。

---

## 一、快速开始

```bash
# 前置：JDK 21 + Maven 3.8+（Windows 默认 platform=win）

mvn javafx:run            # 启动 编辑器 Studio
mvn -Pplayer javafx:run   # 启动 播放器 Player（首次自动生成示例地图 maps/demo_map）
```

IDEA 中直接运行：
- `com.studio.launcher.EditorApp.main` → 编辑器
- `com.studio.launcher.PlayerApp.main` → 播放器
- `com.studio.launcher.MainApp.main` （参数含 `player` 时进入播放器）

换平台（Linux/Mac）用 `-Djavafx.platform=linux` / `mac` 覆盖（见 pom.xml 顶部注释）。

### 体验示例
1. 播放器模式首次运行会自动生成示例地图 `maps/demo_map`（含占位背景/立绘/音频），
   打开后进入 [Start] 场景，点下方的 **▶ 前往森林** → 场景事件触发 **扫雷小游戏插件**，
   游戏内玩完后点顶栏 **← 返回剧情** 无缝回到 Start。
2. **分支剧情 + 2048 示例**：编辑器 文件 → “打开分支剧情示例（含 2048）…” 会生成并打开
   `maps/branch_demo_2048`（也可命令行运行 `com.studio.util.BranchDemoBootstrapMain` 生成）。
   内含 [起点] 分岔 → [Forest2048]（<b>场景级</b> event=2048，进入即嵌入 2048 小游戏）、
   [湖畔]（含“现场来一局 2048”的<b>按钮级</b> action=event 调用）→ [湖心岛] 分支，
   可走向【结局·秘宝】/【结局·晚霞】两个结局 —— 支持方向键/WASD/屏幕按钮玩 2048，
   合出 2048 有胜利提示、无路可走有失败重开；点顶部【← 返回剧情】随时回到岔路。
3. 编辑器打开 `maps/demo_map`，尝试：用画布左上角常驻工具箱拖放/放置节点、双击节点改属性、
   拖动画布物体、场景树右键、`Ctrl+R` 播放测试、文件→导出生成独立地图文件夹。
4. **三槽存档演示**：编辑器 文件 → “打开存档演示地图（3 槽存档台）…” 生成并打开
   `maps/demo_save_room`。三个场景里都能点“💾 打开存档台”呼出存档台插件：
   槽 1/2/3 各配 [保存][读取][删除]——先在某场景保存、再走到别的场景、回来“读取”
   即跳回存档时的场景并自动关闭存档台。存档写入 `maps/demo_save_room/saves/*.txt`
   （`{变量: 数值}` 语法、支持 `#` 注释，可手改）。
5. **信号 / 槽 + 逻辑层演示**：编辑器 文件 → “打开信号演示地图（信号/槽+逻辑层）…” 生成并打开
   `maps/demo_signal_lab`：点“启动按钮”触发鼠标**信号**→槽 `call`→`logic/SignalLabLogic`
   逻辑层改变量与样式；点“手动灯”走纯编辑器槽 `emit`；按 `F`/`L` 演示场景级与节点级**键盘信号**；
   存档/读档 slot1 可验证变量与样式覆盖一起恢复。详见 README 第六节。
6. **自包含示例地图（双立绘轮流高亮）**：`maps/demo_signal_characters` 是**纯地图文件夹**——
   脚本 + 素材 + 地图自带逻辑（`logic/src` 源码与已编译的 `logic/classes/*.class`）全在文件夹内，
   与编辑器工程零耦合。用 编辑器 文件 → **打开地图文件夹…** 选中它即可编辑/运行：
   点击对话框段落推进时，两位立绘**轮流高亮并略微变大**（说话者 opacity 1.0 / scale 1.08，
   另一位 0.55 / 1.0，名牌同步明暗），对话走完后按对话节点 `target` **自动跳转**到 [尾声]。

---

## 二、目录结构

```
project-root/
├── pom.xml                          # JDK21 + JavaFX21 + javafx-maven-plugin（player profile）
├── config.ini                       # 播放器地图目录 / 插件目录 / 窗口与打字速度
├── plugins/
│   ├── plugins.ini                  # 插件注册表：事件ID = 类名
│   ├── README.txt                   # 外部插件开发/编译/接入说明
│   └── examples/ClockDemoPlugin.java# 外部插件示例源码
├── maps/demo_map/                   # 运行时生成的示例地图（scenario.txt + resources）
├── maps/branch_demo_2048/           # 分支剧情+2048 示例地图（编辑器一键生成）
└── src/main/
    ├── java/com/
    │   ├── studio.model/            # 数据模型：NodeType/StoryNode/GameScene/GameOption/GameProject
    │   ├── studio.parser/           # ScriptParser（读）/ ScriptWriter（写）/ 往返自测
    │   ├── studio.editor/           # 编辑器：EditorPane/EditorCanvas/EditorPanels/NodeDialogs/...
    │   ├── studio.reader/           # 读取器引擎：ReaderView（渲染/打字机/插件嵌入）
    │   ├── studio.plugin/           # GamePlugin 接口 / PluginLoader / demo/(Minesweeper|Game2048)
    │   ├── studio.ui/               # FX 工具：Ui 弹窗、RichText、FxAssets、FxAnim
    │   ├── studio.saves/            # 存档：SaveData/SaveFileCodec/GameSaveManager/SaveHook
    │   ├── studio.util/             # 纯 JDK 工具：Logs/AppConfig/MapAssets/MapTemplateFactory
    │   └── studio.launcher/         # MainApp（模式分流）/ EditorApp / PlayerApp
    └── resources/styles/            # studio.css（编辑器）/ player.css（读取器/扫雷）
```

> 设计要点：`model / parser / util(核心)` 不依赖 JavaFX —— 解析与模板生成可在无图形环境单独编译运行，
> 也便于日后把“引擎层”做成独立 core 库。命令行自测：
> `mvn -q exec:java -Dexec.mainClass=com.studio.parser.ParserSelfTest`

---

## 三、scenario.txt 脚本语法（Parser 双向同步）

```
# 以 # 开头的行为注释
[option]                     ← 全局设置段
initialScene = Start
background = #0d0f1c
volume = 0.8
typewriterSpeed = 14
任意自定义键 = 保留值        ← 未知键原样保留（往返不丢）

[Start]                      ← 场景段
event = minesweeper          ← 场景级属性：进入该场景时运行的插件 ID（可选）
next = Forest                ← 可选：无按钮时的“下一场景”
{
type = bg                    ← 节点对象 { }，每个 {} 是一个节点
x = 0
y = 0
width = 1280                 ← 默认尺寸可省略
path = resources/images/background.png
style = -fx-background-color: white;
}
```

### 节点属性表（键名大小写不敏感，支持中文别名：类型/文本/图片/坐标X/事件…）
| 键 | 含义 | 备注 |
|---|---|---|
| type | bg背景 \| char立绘 \| text文本 \| name人物名 \| dialog对话 \| button按钮 \| music音乐 | 必填 |
| x / y | 逻辑坐标（画布 1280×720） | |
| width / height | 显示尺寸 | 缺省 = 类型默认 |
| path | 图片/立绘相对路径（相对地图根） | 缺失时自动显示“占位渐变图” |
| audio | 音频路径 | music 节点循环播放 |
| text | 显示文本（**富文本**，见下） | 多行自动转为 `text = <<<…<<<` |
| style | 内联 CSS（`-fx-*`） | |
| event | 节点事件（action=event 时触发插件） | |
| action | 按钮动作：target/skip/save/load/speed/event | **save/load 为真实存档读写** |
| target | action=target 填场景名；action=save/load 填存档文件名 | save/load 留空=slot1.txt；**对话节点填“对话结束后的下一场景”，点完最后一段自动跳转** |
| visible / fontSize / align / opacity | 可见性/字号/对齐/透明度 | 缺省=默认 |
| typewriter / 逐字显示 | 对话逐字开关：默认/开/关（对话默认开） | 逐字时点击对话=显示全文 |
| 其它任意键 | 存入 extras 原样往返 | 保证无损同步 |

**富文本标记**：`<b>加粗</b>` `<i>斜体</i>` `<u>下划线</u>` `<color:#ffcc00>彩色</color>` `<color:yellow>命名色</color>` `<size:26>字号</size>` `<br>`换行。

**多段台词**：`text` 中**独立一行的 `---`** 分隔多段，播放时点击对话框：逐字中=显示全文，已显示=切下一段。

**双向同步机制**：解析（`ScriptParser`）与序列化（`ScriptWriter`）共享“规范键顺序 + 顺序保持 + 未知键透传”约定。
解析采用“行状态机”：`[段头]`→ 键值行 / `{`→节点块 / `<<<…<<<`→多行文本（Heredoc）。
宽容模式：未知类型、多余 `}`、重复场景（合并并告警）均只记警告不崩溃。
自测 45 个用例全部通过（`ParserSelfTest`）。

### 存档与读档（saves/）
- 每张地图在自身文件夹下建 `saves/` 目录，可放**多个 .txt 存档文件**（文件名=槽位），内容由地图工程师决定。
- 存档语法（支持 `#` 注释与多行、行内 `#` 注释）：
  ```
  # 手写存档示例
  {scene: 湖畔}
  {金币: 12, 100}
  {已开宝箱: 1}    # 行内注释
  ```
- 无需专用界面：地图内放“存档/读档”**按钮**即可 —— 按钮 `action=save` 写、`action=load` 读；
  按钮 `target` 填存档文件名（留空默认 `slot1.txt`）。引擎写入 `scene` 变量，读档时自动跳回该场景。
- 地图工程师后端扩展：实现 `com.studio.saves.SaveHook`（`onEngineSave/onEngineLoad`）即可往快照里增删自定义变量；
  插件每次加载时若 `instanceof SaveHook` 会被引擎自动注册。插件参数还带 `GamePlugin.PARAM_SAVES`
  （`GameSaveManager`），可直接 `listSaveFiles/read/write/delete` 任意槽位。

---

## 四、编辑器 Studio 功能

- 顶部菜单：**文件**（打开/新建(标准模板)/新建示例/保存 Ctrl+S/导出独立文件夹/删除当前地图/退出）、
  **编辑**、**场景**（增/删/重命名、[option] 全局设置）、**视图**（缩放/网格）、**运行**（播放器测试 Ctrl+R）、**帮助**。
- 布局：左侧=场景与节点层级树；中间=画布；右侧=属性检查器；底部=状态栏。
- 画布左上角**常驻工具箱**（视图→显示工具箱 可开关）：按住“文本/图片/按钮/背景/立绘/人物名/对话/音乐”项拖到画布即生成节点；单击某项也在可视中心放置。
- 节点：拖拽改坐标；单击选中（金色描边）；双击或右键“编辑属性”弹出完整属性窗口，**修改即时刷新画布**；
  属性窗口保持原来的**单列平铺样式**，内容过多时用**鼠标滚轮上下滚动**查看（也支持拖拽平移）；
  右键还有 复制/删除/上移/下移 层级。空白处右键可“在此处添加…”（再次点击任意区域自动收起菜单）。
- 常用快捷键：**Delete** 删除选中节点、**Ctrl+S** 保存、**Ctrl+O** 打开、**Ctrl+R** 播放测试、
  **Ctrl+E** 编辑选中节点、**Ctrl+D** 复制节点、**Ctrl+= / Ctrl+- / Ctrl+0** 缩放/适应窗口
  （均已做场景级快捷键处理，不受焦点影响）。
- **新建地图**自动生成标准 AVG 模板：左右立绘、左下/右下人物名字牌、中央对话区、底部“跳过/存档/读档/加速”按钮
  （存档/读档为真实 saves/ 读写，跳过/加速为播放器控制）。
- **导出**：DirectoryChooser 选择位置后生成 `地图名/{scenario.txt, resources/}`；缺失素材按节点尺寸自动补生成占位 PNG/WAV。
- 地图 = 文件夹命名规则；脚本引用 `resources/…` 相对路径。

## 五、读取器 Player 与插件化架构

- 启动时读 `config.ini`（`map.folder` / `plugins.dir` / 窗口尺寸 / 打字机速度），无地图则生成示例。
- 逻辑分辨率 1280×720，窗口等比缩放（信箱式）；入场动画、按钮 hover/pressed 缩放（`FxAnim` 用
  `ScaleTransition` 模拟 CSS transition —— JavaFX 原生无 CSS 过渡）。
- 节点渲染即“所见即所得”：`dialog/name/text` 用 TextFlow 渲染富文本，`dialog` 带打字机（可点按全文显示、[加速]变速）。
- **GamePlugin 事件接口**（`com.studio.plugin`）：

```java
public interface GamePlugin {
    void execute(Stage stage, Map<String, Object> params);              // 引擎要求实现
    default Parent createEmbeddedView(Map<String, Object> params) { return null; } // 返回界面则“嵌入”主舞台
    default String displayName() { ... }
    default void onDetach() { }
}
```

- **动态加载**：`PluginLoader` 先查 `plugins/plugins.ini` 注册表与内置白名单，再按
  双亲委派（父加载器=应用类加载器）尝试 classpath，找不到时用 `URLClassLoader` 扫描
  `plugins/` 下 `.jar` 与 `classes/` 目录。
- **界面包装**：插件返回的 `Parent` 会被放进读取器主舞台中央的嵌入层，顶部自动生成
  “🎮 插件名 … ← 返回剧情”标题栏；返回后恢复进入前的场景（场景事件不会重复触发）。
  扫雷 Demo 见 `plugins/demo/MinesweeperPlugin`（左键翻开/右键插旗、首击保安全、胜利失败遮罩）。
- 外部插件接入流程见 `plugins/README.txt`。

### 如何规避 Java 模块化（JPMS）限制
1. 工程**不写 module-info.java**（非模块化应用）：主程序与 JavaFX 都处于“未命名模块”。
   未命名模块可读取引导层全部已解析模块并访问其导出包，运行期被 URLClassLoader 加载的插件类
   因而也能直接引用 `javafx.*`；
2. URLClassLoader 的**父加载器设为应用类加载器**：`GamePlugin` 接口等永远由父加载器解析，
   插件类只补位自身新增类，`instanceof` 判定稳定、无同名类冲突；
3. 若日后把主程序模块化（加 module-info），才需要额外 `exports com.studio.plugin` 或为插件
   建立 ModuleLayer / 注入 `--add-reads`、`--add-exports` —— 本设计默认 classpath 模式，天然规避。

---

## 六、信号 / 槽 与逻辑层（渲染由引擎负责，工程师只写逻辑）

节点与场景都带有 **信号列表** 与 **槽列表**，随 `scenario.txt` 双向读写，可在编辑器里直接编辑。

### 脚本一行式（编辑器同样用这种写法）
```
# 节点/场景信号：名称 | mouse|key | click|release|按键码 | 附带参数
signal = 点击 | mouse | click
signal = 点亮键L | key | L | press
# 槽：信号名 | 动作 | 目标 | 参数 | 附加参数
slot = 点击   | call   | | signallab
slot = 点击   | emit   | 灯 | 亮灯
slot = 亮灯   | set    | @self | style | value=-fx-background-color: #f4d06f;
slot = 快捷键F | toggle | 提示 | visible
```
- **信号**：鼠标 `click / release`、键盘 `press / release`（节点级与**场景级**都支持；场景级按键由
  **地图全局事件监听器**接收后按名字分发）；支持在信号上挂静态**附带参数**。
- **槽动作**（全部由引擎执行，天然即时渲染）：`set`（改属性，`value=` 字面值 / `valueVar=` 取变量）、
  `toggle`（布尔切换）、`emit`（向目标节点/场景发信号，参数继续传递）、`goto`（跳场景）、
  `save` / `load`（读写 saves 槽位）、`call`（转交逻辑层）、`log`（日志+顶部提示）。
  参数合并优先级：事件上下文 → 信号附带参数 → 槽附带参数。
- **过渡动画 `transition`**：给属性变化加简单补间（JavaFX Timeline 驱动）。
  ```java
  // 节点属性（该节点所有属性变化默认走补间）
  transition = scale/opacity:300ms
  // 或写在槽的附加参数里（只对本次 set 生效，优先级高于节点默认）
  slot = 点击 | set | @self | scale | value=1.08 | transition=scale:300ms
  ```
  支持动画的属性：`scale`、`opacity`、`rotation`、`x`、`y`（毫秒省略时默认 300；
  多个属性用 `/` 或 `,` 分隔，也可逐属性写时长如 `scale:200ms,opacity:400ms`）。
  逻辑层同样可用：`ctx.setProperty(id, "scale", "1.08", "scale:300ms")`。
  动画结束后最终值会记录为“属性覆盖”，随存档保存并在重绘/读档时重放。

### 逻辑层目录 `logic/`（工程师只写逻辑）
```
工程根/logic/            全局逻辑：logic.ini(ID=类名) + classes/ 或 *.jar
地图根/logic/            该地图专属逻辑（同样规则，优先加载）
```
```java
public class MyLogic implements com.studio.flow.LogicHandler {
    public void onSignal(FlowContext ctx, SignalEvent ev) {
        ctx.setVar("金币", ctx.intVar("金币", 0) + 10);        // 变量（随存档保存）
        ctx.setText("金币文本", "金币: " + ctx.var("金币", "0")); // 引擎立即重绘
        ctx.setStyle("宝箱", "-fx-opacity: 0.35;");            // 改样式
        ctx.emit("提示", "获得金币", Map.of("数量", 10));       // 向另一节点发信号
    }
}
```
- 槽写 `call` 时可用注册 ID、`ID#方法名` 或全限定类名；逻辑类的加载沿用“父加载器优先 + URLClassLoader 兜底”，
  同样规避模块化限制；
- **逻辑层对地图存档数据有读写权限**：`ctx.setVar/nodeVar`（地图/节点变量）、`ctx.setProperty`（节点属性覆盖）
  都会随存档写入 `saves/*.txt`（`var.*` / `nodevar.*` / `prop.*`），读档后自动恢复并**重放渲染**，
  变量本身又可作为后续逻辑输入；
- 内置示例：`com.studio.logic.demo.SignalLabLogic`（ID `signallab`），配套地图
  **`maps/demo_signal_lab`**（编辑器 文件 → “打开信号演示地图（信号/槽+逻辑层）…”）：
  点按钮 → 逻辑层改变量/样式/文本；按 `F` 切提示显隐；按 `L` 让灯变蓝；存档/读档验证恢复；
- 编辑器里：节点属性窗口与右侧检查器都能编辑“信号 / 槽”文本（带模板按钮），场景区可编辑**场景信号(键盘)/场景槽**。

---

## 七、运行注意事项 / FAQ

- **依赖下载**：首次构建需联网拉取 openjfx 依赖；离线环境无法 `mvn`，可用本机 `javac` 先编译
  核心层（model/parser/util）验证（见第三节命令行自测）。
- **平台分类器**：pom 默认 `win`；换平台加 `-Djavafx.platform=linux`。
- **IDEA 直接运行**：等待 Maven 导入完成，直接右键运行 `EditorApp.main`（classpath 模式即可，
  无需额外 VM options；若 IDE 强制 module 模式，按 IDEA 提示补 JavaFX VM 参数即可）。
- 素材为“相对路径引用 + 缺失时占位”策略：美术资源放进 `resources/` 同名覆盖即生效，
  编辑器里选中素材文件会自动复制进地图 resources 并改为相对引用。
- `删除当前地图` 会**永久删除**文件夹（弹窗二次确认）；请谨慎。
- 存档/读档：引擎内置 `scene` 变量与 `FlowVariables`（`var.*` / `nodevar.*` / `prop.*`）随存档写入，
  故事按钮 `action=save/load` + `target=槽位名`，或插件/逻辑层通过 `SavePortal` / `FlowContext` 调用。

## 七、License / 说明
示例地图的美术资源为代码生成的占位图；业务用途请替换为原创素材。
祝玩得开心，期待你发布的第一张剧情地图 🎮
