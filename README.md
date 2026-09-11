# ds的奇妙冒险（ds-adventure）

> **Galgame 文字剧情 + 小游戏特殊演出** 的 JavaFX 实训项目。
> 剧情以脚本驱动推进，行至关键节点时以「特殊演出」方式切入小游戏（贪吃蛇、飞机大战、2048 …）；
> 小游戏保留独立玩法，结束后把结果回传剧情引擎，由引擎决定后续叙事分支。

仓库同时内置一套自研工具链：**Studio（可视化剧情编辑器）** 与 **Player（剧情播放器 / 插件宿主）**，
用于编排与运行以「地图」为单位的剧情工程（脚本 + 素材 + 逻辑）。

| 项目信息 | 内容 |
|---|---|
| 仓库 | https://github.com/liceses/ds-adventure-game |
| 开发团队 | 项目组 5 人（角色轮换，分工见组内《分工计划》） |
| 实训周期 | 10 个工作日，按迭代批次 IT-1～IT-6 推进 |
| 当前阶段 | 脚手架与工具链就绪；P0 功能（剧情引擎 / 选项分支 / 特殊演出调度 / 贪吃蛇 / 飞机大战 / 主菜单）推进中 |

---

## 一、项目简介

本项目有**两个形态**，共用同一套数据模型与脚本格式：

1. **游戏本体（ds的奇妙冒险）**——文字剧情主框架，按《需求规格说明书》实现 P0 六项功能：剧情引擎、选项分支、特殊演出调度、贪吃蛇、飞机大战、主菜单。
2. **工具链（Studio / Player）**——把剧情写成「地图文件夹工程」并可视化编辑、播放：Studio 负责所见即所得地排版节点，Player 负责渲染剧情、驱动插件化小游戏。

两者关系：Player 已提供剧情渲染（打字机、富文本、场景跳转）与插件宿主（小游戏嵌入主舞台并返回），
即 P0 中「剧情引擎」「特殊演出调度」的技术底座；P0 余下部分（选项超时、结果路由、贪吃蛇、飞机大战、主菜单）在此基础上补齐。

**文档索引**（均在 `docs/` 下）：

| 文档 | 位置 |
|---|---|
| 9 个小游戏玩法说明 | `docs/ds-adventrue/*.md`（贪吃蛇 / 飞机大战 / 2048 / 打砖块 / 记忆翻牌 / 连连看 / 扫雷 / 推箱子 / 五子棋） |
| 剧情大纲 / 人物设定 / 剧本 | `docs/ds-adventrue/剧情大纲*.md`、`人物设定集.md`、`剧本/`、`剧本-原声/`、`剧本-v0.1-候选/` |
| 需求与设计图 | `docs/ds-adventrue/需求文档.txt`、`docs/ds-adventrue/图/` |
| 素材清单 | `docs/ds-adventrue/资产清单.md` |

---

## 二、技术栈与版本

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 本机 21（LTS） | **编译目标 17**：`maven-compiler-plugin 3.13.0` + `<release>17</release>`，对齐《需求规格说明书》3.5 与实训检查项 |
| JavaFX | **17.0.20**（LTS 17） | `javafx-base` / `graphics` / `controls` / `media`，按平台分类器引入（默认 `win`） |
| Maven | 3.9.9（由 Wrapper 自动下载） | **无需全局安装 Maven** |
| Maven Wrapper | 3.3.2 | `mvnw.cmd`（Windows）/ `./mvnw`（macOS、Linux） |
| JUnit | 5.11.4（Jupiter） | `mvnw.cmd test`，见 `src/test/java/com/studio/parser/ScriptParserTest.java` |
| maven-surefire-plugin | 3.2.5（`pom.xml` 显式声明） | 运行 JUnit 5 |
| javafx-maven-plugin | 0.0.8 | `javafx:run` 启动 Studio / Player |
| exec-maven-plugin | 3.2.0 | 无图形环境下运行核心层自测 `ParserSelfTest` |
| 构建坐标 | `com.studio : ds-adventure : 1.0.0` | 产物名 `ds-adventure`（`<finalName>`），与仓库名一致 |

> 说明：`groupId` / 包名沿用 `com.studio`，`artifactId` 已统一为 `ds-adventure`（与仓库名一致）。

---

## 三、环境要求与运行

**前置**：JDK 17 或更高（推荐 21）；无需全局安装 Maven（项目自带 Wrapper）；首次构建需联网拉取 openjfx 依赖。

```bat
mvnw.cmd clean compile          :: 编译（实训检查项：mvn clean compile 通过）
mvnw.cmd test                   :: 单元测试（JUnit 5）
mvnw.cmd javafx:run             :: 启动 Studio 编辑器
mvnw.cmd -Pplayer javafx:run    :: 启动 Player 播放器（首次自动生成示例地图 maps/demo_map）
```

macOS / Linux 将 `mvnw.cmd` 换成 `./mvnw`。

**IntelliJ IDEA 直接运行**：

- `com.studio.launcher.EditorApp.main` → 编辑器
- `com.studio.launcher.PlayerApp.main` → 播放器
- `com.studio.launcher.MainApp.main`（参数含 `player` 时进入播放器）

换平台（macOS / Linux）加 `-Djavafx.platform=mac` / `-Djavafx.platform=linux` 覆盖（见 `pom.xml` 顶部注释）。

---

## 四、目录结构

```
ds-adventure/
├── pom.xml                          # 构建：JDK 17 目标 + JavaFX 17 + javafx-maven-plugin（player profile）
├── mvnw / mvnw.cmd / .mvn/          # Maven Wrapper（无需全局 Maven）
├── config.ini                       # Player 配置：地图目录 / 插件目录 / 窗口尺寸 / 打字机速度
├── Changelog.md                     # 更新日志
├── docs/
│   └── ds-adventrue/                # 玩法说明、剧情大纲、人物设定、剧本、需求文档、图、资产清单
├── plugins/
│   ├── plugins.ini                  # 插件注册表：事件 ID = 全限定类名
│   ├── README.txt                   # 外部插件开发/编译/接入说明
│   └── examples/ClockDemoPlugin.java# 外部插件示例源码
├── logic/                           # 逻辑层（渲染由引擎负责，工程师只写逻辑）
│   ├── logic.ini                    # 全局逻辑注册：ID = 类名
│   ├── README.md
│   └── examples/MyLogicTemplate.java
├── tools/                           # 辅助脚本：剧本检查、立绘表情检查/修正、素材生成、语音
├── src/main/
│   ├── java/com/studio/
│   │   ├── model/                   # 数据模型：NodeType / StoryNode / GameScene / GameOption / GameProject
│   │   ├── parser/                  # ScriptParser（读）/ ScriptWriter（写）/ ParserSelfTest
│   │   ├── reader/                  # 剧情渲染引擎 ReaderView（打字机 / 富文本 / 插件嵌入层）
│   │   ├── editor/                  # 编辑器：EditorPane / EditorCanvas / EditorPanels / NodeDialogs / AssetImport
│   │   ├── plugin/                  # GamePlugin 接口 / PluginLoader / demo(Minesweeper | Game2048 | SavePanel)
│   │   ├── flow/                    # 信号-槽调度：SignalBus / SignalCodec / SlotDef / FlowContext / FlowVariables / LogicLoader
│   │   ├── saves/                   # 存档：SaveData / SaveFileCodec / GameSaveManager / SaveHook / SavePortal
│   │   ├── logic/                   # 内置示例逻辑 SignalLabLogic
│   │   ├── ui/                      # FX 工具：Ui 弹窗 / RichText / FxAssets / FxAnim
│   │   ├── util/                    # 纯 JDK 工具：AppConfig / Logs / MapAssets / MapTemplateFactory / 示例地图工厂
│   │   └── launcher/                # MainApp（模式分流）/ EditorApp / PlayerApp
│   └── resources/
│       ├── styles/                  # studio.css（编辑器）/ player.css（读取器）
│       └── assets/                  # sprites/ 立绘与背景、sounds/ 音效
└── src/test/java/com/studio/parser/ # JUnit 5 单元测试
```

`maps/` 为运行时生成的示例/业务地图，不入版本库。

### 包职责与实训检查项分层要求的对应

检查项要求包结构体现 `controller / model / view / config / util` 职责。本项目按功能域命名，对应关系如下：

| 检查项分层 | 本项目对应包 | 说明 |
|---|---|---|
| model | `com.studio.model`、`com.studio.parser`、`com.studio.saves`、`com.studio.flow` | 数据模型、脚本解析、存档读写、信号/槽数据结构 |
| view | `com.studio.reader`、`com.studio.editor`、`com.studio.ui` | 剧情渲染、编辑器界面、FX 通用组件 |
| controller | `com.studio.plugin`、`com.studio.flow`（槽执行）、`com.studio.launcher` | 插件调度、信号分发与槽动作执行、应用装配 |
| config | `config.ini` + `com.studio.util.AppConfig` | 外部配置集中读取 |
| util | `com.studio.util` | 纯 JDK 工具（不依赖 JavaFX） |

> 设计要点：`model / parser / util(核心)` **不依赖 JavaFX**，解析与模板生成可在无图形环境下单独编译运行：

```bat
mvnw.cmd -q exec:java "-Dexec.mainClass=com.studio.parser.ParserSelfTest"
```

> 若需与检查项字面一致（包名改为 `controller/model/view/config/util`），是一次涉及 60+ 文件的独立改造，当前未做。

---

## 五、需求实现对照（对齐《需求规格说明书 v1.1》）

跟进标识沿用《详细设计说明书 v3.1》的迭代批次 IT-1～IT-6 与跟踪号 TODO-01～TODO-10。

### P0（验收底线）

| 需求 | 内容要点 | 当前状态 | 现有落点 | 跟进 |
|---|---|---|---|---|
| F1 剧情引擎 | 脚本解析、文本逐字显示、节点跳转、无效跳转不崩溃 | **部分具备** | `ScriptParser`/`ScriptWriter` + `ReaderView`（打字机、富文本、场景跳转、宽容模式告警） | TODO-05 · IT-2 |
| F2 选项分支 | 多选项跳转；玩家长时间不决策时执行第三种隐藏默认选项 | **待实现** | 现有 `button` 节点 `action=target` 可做基础跳转；**超时默认选项机制未实现** | TODO-06 · IT-2 |
| F3 特殊演出调度 | 节点触发小游戏、结束后结果回传、按结果三分支调度（普通叙事 / 强制重试 / 关键结局） | **部分具备** | `GamePlugin` + 嵌入层 + 场景级 `event`（可切入并返回剧情）；**结果回传与三分支路由未实现** | TODO-07 / TODO-04 / TODO-01 · IT-2 |
| F4 贪吃蛇 | 10×10、吃豆 97 通关、速度每秒 +0.01、允许 180° 反向 | **待实现** | 需按 `GamePlugin` 实现（接口设计见详细设计说明书 `SnakeGame`） | TODO-09 · IT-3 |
| F5 飞机大战 | 左摇杆移动 / 右按钮射击、击落 20 架、3 条命、护盾 3 秒 | **待实现** | 无 | TODO-10 · IT-4 |
| F16 主菜单 | 开始 / 继续 / 回忆收藏馆 / 设置 / 退出（覆盖确认、无存档置灰） | **待实现** | 现有 Player 启动即进地图，无五项主菜单 | TODO-02 / TODO-03 · IT-5 |

### P1 / P2（进度）

| 需求 | 优先级 | 当前状态 | 现有落点 |
|---|---|---|---|
| 2048 小游戏 | P1 | **已具备（demo）** | `com.studio.plugin.demo.Game2048Plugin` |
| 扫雷小游戏 | P1 | **已具备（demo）** | `com.studio.plugin.demo.MinesweeperPlugin` |
| 存档系统 | P1 | **部分具备** | `saves/` 三槽存档 + `SavePortal` + `FlowVariables`；「进入小游戏前自动存档」「跨周目全局记录」待补 | 
| 打砖块 / 记忆翻牌 / 连连看 / 推箱子 / 五子棋 | P1 | 待实现 | 玩法说明见 `docs/ds-adventrue/*.md` |
| 回忆收藏馆 / 结局收集 | P1 | 待实现 | — |
| 音频（BGM / 音效 / 静音） | P2 | 部分具备 | 已依赖 `javafx-media`；`music` 节点支持循环播放 |
| 设置界面 / CG 收集 | P2 | 待实现 | — |

---

## 六、小游戏池与优先级

按需求规格说明书，小游戏共 **9 种**，以「特殊演出」方式嵌入剧情：

| 小游戏 | 优先级 | 实现状态 |
|---|---|---|
| 贪吃蛇 | **P0** | 待实现（IT-3） |
| 飞机大战 | **P0** | 待实现（IT-4） |
| 2048 | P1 | 已具备（demo 插件） |
| 扫雷 | P1 | 已具备（demo 插件） |
| 打砖块 | P1 | 待实现 |
| 记忆翻牌 | P1 | 待实现 |
| 连连看 | P1 | 待实现 |
| 推箱子 | P1 | 待实现 |
| 五子棋 | P1 | 待实现 |

统一接入约定：小游戏实现 `com.studio.plugin.GamePlugin`，返回的 `Parent` 会被嵌入 Player 主舞台中央，
顶部自动生成「🎮 插件名 … ← 返回剧情」标题栏；外部插件开发流程见 `plugins/README.txt`。

---

## 七、剧情脚本 `scenario.txt` 语法（Parser 双向同步）

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

### 节点属性表（键名大小写不敏感，支持中文别名：类型 / 文本 / 图片 / 坐标X / 事件…）

| 键 | 含义 | 备注 |
|---|---|---|
| type | bg背景 \| char立绘 \| text文本 \| name人物名 \| dialog对话 \| button按钮 \| music音乐 | 必填 |
| x / y | 逻辑坐标（画布 1280×720） | |
| width / height | 显示尺寸 | 缺省 = 类型默认 |
| path | 图片 / 立绘相对路径（相对地图根） | 缺失时自动显示“占位渐变图” |
| audio | 音频路径 | music 节点循环播放 |
| text | 显示文本（**富文本**，见下） | 多行自动转为 `text = <<<…<<<` |
| style | 内联 CSS（`-fx-*`） | |
| event | 节点事件（`action=event` 时触发插件） | |
| action | 按钮动作：`target` / `skip` / `save` / `load` / `speed` / `event` | save / load 为真实存档读写 |
| target | `action=target` 填场景名；`action=save/load` 填存档文件名 | 留空默认 `slot1.txt`；**对话节点填“对话结束后的下一场景”，点完最后一段自动跳转** |
| visible / fontSize / align / opacity | 可见性 / 字号 / 对齐 / 透明度 | 缺省 = 默认 |
| typewriter / 逐字显示 | 对话逐字开关：默认 / 开 / 关（对话默认开） | 逐字时点击对话 = 显示全文 |
| 其它任意键 | 存入 extras 原样往返 | 保证无损同步 |

**富文本标记**： `<b>加粗</b>` `<i>斜体</i>` `<u>下划线</u>` `<color:#ffcc00>彩色</color>` `<color:yellow>命名色</color>` `<size:26>字号</size>` `<br>`换行。

**多段台词**：`text` 中**独立一行的 `---`** 分隔多段；播放时点击对话框：逐字中 = 显示全文，已显示 = 切下一段。

**双向同步机制**：解析（`ScriptParser`）与序列化（`ScriptWriter`）共享「规范键顺序 + 顺序保持 + 未知键透传」约定。
解析采用行状态机：`[段头]` → 键值行 / `{` → 节点块 / `<<<…<<<` → 多行文本（Heredoc）。
宽容模式：未知类型、多余 `}`、重复场景（合并并告警）均只记警告不崩溃。

**测试覆盖**：
- `ParserSelfTest`（`main` 方法自测，45 项用例）：`mvnw.cmd -q exec:java "-Dexec.mainClass=com.studio.parser.ParserSelfTest"`
- `ScriptParserTest`（JUnit 5，4 项用例：解析 / 多段分隔 / 往返未知键透传 / 重复场景告警）：`mvnw.cmd test`

---

## 八、存档系统（`saves/`）

- 每张地图在自身文件夹下建 `saves/` 目录，可放**多个 `.txt` 存档文件**（文件名 = 槽位）。
- 存档语法（支持 `#` 注释、行内注释、多行）：

```
# 手写存档示例
{scene: 湖畔}
{金币: 12, 100}
{已开宝箱: 1}    # 行内注释
```

- 无需专用界面：地图内放「存档 / 读档」**按钮**即可——按钮 `action=save` 写、`action=load` 读；
  `target` 填存档文件名（留空默认 `slot1.txt`）。引擎写入 `scene` 变量，读档时自动跳回该场景。
- 后端扩展：实现 `com.studio.saves.SaveHook`（`onEngineSave` / `onEngineLoad`）即可往快照里增删自定义变量；
  插件加载时若 `instanceof SaveHook` 会被引擎自动注册。插件参数还带 `GamePlugin.PARAM_SAVES`
  （`GameSaveManager`），可直接 `listSaveFiles` / `read` / `write` / `delete` 任意槽位。
- 逻辑层写入的变量与属性覆盖（`var.*` / `nodevar.*` / `prop.*`）随存档保存，读档后自动恢复并重放渲染。

---

## 九、Studio 编辑器与 Player 播放器

### 9.1 Studio（编辑器）

- 顶部菜单：**文件**（打开 / 新建标准模板 / 新建示例 / 保存 `Ctrl+S` / 导出独立文件夹 / 删除当前地图 / 退出）、**编辑**、**场景**（增删改、`[option]` 全局设置）、**视图**（缩放 / 网格）、**运行**（播放器测试 `Ctrl+R`）、**帮助**。
- 布局：左 = 场景与节点层级树；中 = 画布；右 = 属性检查器；底 = 状态栏。
- 画布左上角**常驻工具箱**：把「文本 / 图片 / 按钮 / 背景 / 立绘 / 人物名 / 对话 / 音乐」拖到画布即生成节点（单击也可在可视中心放置）。
- 节点：拖拽改坐标；单击选中；双击或右键「编辑属性」弹出完整属性窗口，**修改即时刷新画布**（内容过多时可滚轮滚动 / 拖拽平移）；右键支持复制 / 删除 / 上移 / 下移。
- 快捷键：`Delete` 删除、`Ctrl+S` 保存、`Ctrl+O` 打开、`Ctrl+R` 播放测试、`Ctrl+E` 编辑、`Ctrl+D` 复制、`Ctrl+= / Ctrl+- / Ctrl+0` 缩放与适应窗口。
- **新建地图**自动生成标准 AVG 模板：左右立绘、人物名牌、中央对话区、底部「跳过 / 存档 / 读档 / 加速」按钮。
- **导出**：生成 `地图名/{scenario.txt, resources/}`，缺失素材按节点尺寸自动补占位 PNG / WAV。

### 9.2 Player（播放器）

- 启动读 `config.ini`（`map.folder` / `plugins.dir` / 窗口尺寸 / 打字机速度），无地图则生成示例。
- 逻辑分辨率 1280×720，窗口等比缩放（信箱式）；入场动画与按钮 hover / pressed 缩放由 `FxAnim` 用 `ScaleTransition` 实现（JavaFX 原生无 CSS 过渡）。
- 富文本由 `TextFlow` 渲染；`dialog` 节点带打字机（点击显示全文、`[加速]` 变速）。
- **插件接口**（`com.studio.plugin`）：

```java
public interface GamePlugin {
    void execute(Stage stage, Map<String, Object> params);                          // 引擎要求实现
    default Parent createEmbeddedView(Map<String, Object> params) { return null; }  // 返回界面则“嵌入”主舞台
    default String displayName() { ... }
    default void onDetach() { }                                                     // 插件被移除时回调（停循环/计时器）
}
```

- **动态加载**：`PluginLoader` 先查 `plugins/plugins.ini` 注册表与内置白名单，再按双亲委派（父加载器 = 应用类加载器）尝试 classpath，找不到时用 `URLClassLoader` 扫描 `plugins/` 下 `.jar` 与 `classes/`。
- **界面包装**：插件返回的 `Parent` 放入主舞台中央嵌入层，顶部自动生成「🎮 插件名 … ← 返回剧情」标题栏；返回后恢复进入前的场景（场景事件不重复触发），并在移除时回调插件 `onDetach()` 以停止其游戏循环/计时器（读档收起插件层时同样回调）。
- 内置 demo 插件：`MinesweeperPlugin`（扫雷）、`Game2048Plugin`（2048）、`SavePanelPlugin`（三槽存档台）。

### 9.3 如何规避 Java 模块化（JPMS）限制

1. 工程**不写 `module-info.java`**：主程序与 JavaFX 同处「未命名模块」，可读取引导层全部已解析模块并访问其导出包，因此运行期由 `URLClassLoader` 加载的插件类也能直接引用 `javafx.*`；
2. `URLClassLoader` 的**父加载器设为应用类加载器**：`GamePlugin` 等接口永远由父加载器解析，插件类只补位自身新增类，`instanceof` 判定稳定、无同名类冲突；
3. 若日后模块化主程序，才需额外 `exports com.studio.plugin` 或为插件建立 `ModuleLayer` / 注入 `--add-reads`、`--add-exports`——本设计默认 classpath 模式，天然规避。

### 9.4 体验示例（开箱可跑）

1. **扫雷嵌入**：Player 首次运行生成 `maps/demo_map`，进入 `[Start]` 点「▶ 前往森林」→ 场景事件触发扫雷插件 → 玩完点顶栏「← 返回剧情」回到 `Start`。
2. **分支剧情 + 2048**：编辑器 文件 →「打开分支剧情示例（含 2048）…」生成 `maps/branch_demo_2048`；含场景级 `event=2048` 与按钮级 `action=event` 两种接入方式，可走向两个结局。
3. **三槽存档台**：「打开存档演示地图（3 槽存档台）…」生成 `maps/demo_save_room`；槽 1/2/3 各配保存 / 读取 / 删除，读档会跳回存档时场景。
4. **信号 / 槽 + 逻辑层**：「打开信号演示地图（信号/槽+逻辑层）…」生成 `maps/demo_signal_lab`；按钮触发信号 → 槽 `call` → `logic/SignalLabLogic` 改变量与样式；按 `F`/`L` 演示场景级与节点级键盘信号。
5. **双立绘轮流高亮**：`maps/demo_signal_characters` 是纯地图文件夹（脚本 + 素材 + 自带逻辑），点击推进时说话者高亮（opacity 1.0 / scale 1.08），对话走完按 `target` 自动跳转。

---

## 十、信号 / 槽 与逻辑层

节点与场景都带**信号列表**与**槽列表**，随 `scenario.txt` 双向读写，可在编辑器内编辑。

### 10.1 脚本一行式

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

- **信号**：鼠标 `click` / `release`、键盘 `press` / `release`（节点级与场景级都支持；场景级按键由地图全局事件监听器接收后按名字分发），可挂静态附带参数。
- **槽动作**（全部由引擎执行，天然即时渲染）：`set`（改属性，`value=` 字面值 / `valueVar=` 取变量）、`toggle`（布尔切换）、`emit`（向目标发信号，参数继续传递）、`goto`（跳场景）、`save` / `load`（读写存档槽位）、`call`（转交逻辑层）、`log`（日志 + 顶部提示）。
  参数合并优先级：事件上下文 → 信号附带参数 → 槽附带参数。

### 10.2 过渡动画 `transition`

```
transition = scale/opacity:300ms                                  # 节点级默认补间
slot = 点击 | set | @self | scale | value=1.08 | transition=scale:300ms   # 仅本次 set 生效
```

支持 `scale` / `opacity` / `rotation` / `x` / `y`（毫秒省略默认 300；多属性用 `/` 或 `,` 分隔，也可逐属性写时长）。
逻辑层同样可用：`ctx.setProperty(id, "scale", "1.08", "scale:300ms")`。动画终值会记录为「属性覆盖」，随存档保存并在重绘 / 读档时重放。

### 10.3 逻辑层目录 `logic/`

```
工程根/logic/     全局逻辑：logic.ini(ID=类名) + classes/ 或 *.jar
地图根/logic/     该地图专属逻辑（同样规则，优先加载）
```

```java
public class MyLogic implements com.studio.flow.LogicHandler {
    public void onSignal(FlowContext ctx, SignalEvent ev) {
        ctx.setVar("金币", ctx.intVar("金币", 0) + 10);          // 变量（随存档保存）
        ctx.setText("金币文本", "金币: " + ctx.var("金币", "0"));  // 引擎立即重绘
        ctx.setStyle("宝箱", "-fx-opacity: 0.35;");              // 改样式
        ctx.emit("提示", "获得金币", Map.of("数量", 10));         // 向另一节点发信号
    }
}
```

- 槽写 `call` 时可用注册 ID、`ID#方法名` 或全限定类名；逻辑类加载沿用「父加载器优先 + `URLClassLoader` 兜底」，同样规避模块化限制。
- 内置示例：`com.studio.logic.demo.SignalLabLogic`（ID `signallab`），配套地图 `maps/demo_signal_lab`。
- 编辑器内：节点属性窗口与右侧检查器都能编辑「信号 / 槽」文本（带模板按钮），场景区可编辑场景信号（键盘）与场景槽。

---

## 十一、协作与 Git 规范

> 📘 **完整协作规范见 [`CONTRIBUTING.md`](CONTRIBUTING.md)**：小游戏插件接入步骤与代码骨架、`plugins.ini` 注册、素材规范、演示地图共享、分支/提交/PR 模板、提交前自检清单、常见问题排查、禁止事项。

- **主分支**：`main`；功能开发开分支（如 `feat/snake`、`feat/story-engine`），完成后提 PR 合并。
- **提交信息**：遵循 `feat:` / `fix:` / `docs:` / `chore:` / `refactor:` 前缀，中文描述可读。
- **`.gitignore` 要点**：`target/`、`maps/`（运行时地图）、`plugins/classes/`、`plugins/*.jar`、`logic/classes/`、`logic/*.jar`、`.idea/`、`.out-*/`，以及本地文档目录 `docs/私人/`（**不进入版本库**）。
- **素材**：美术资源放 `src/main/resources/assets/`（`sprites/`、`sounds/`），地图内使用 `resources/…` 相对路径引用。

---

## 十二、AI 使用与核对说明

本项目开发过程中使用 AI 编程助手辅助，遵循「AI 生成 → 人工核对 → 验证留痕」流程。

**AI 辅助范围**

- 项目脚手架：Maven 工程与 Maven Wrapper、包结构、游戏循环与输入抽象骨架
- 构建配置：`pom.xml`（编译目标、依赖、插件）与依赖精简
- 单元测试：`ScriptParserTest`（4 项用例）
- 文档：README、需求规格说明书与详细设计说明书的整理与修订（含 `[待实现]` 进度标记补注）

**由组员实现（非 AI 生成）**

- Studio / Player 工具链、剧情渲染引擎、插件系统与内置小游戏 demo（扫雷、2048、存档台）、信号/槽与逻辑层、存档系统
- 剧情大纲、人物设定、剧本与美术素材

**人工核对项**

| 核对项 | 结论 |
|---|---|
| JDK 与编译目标 | 编译目标 17（`<release>17</release>`），本机 JDK 21 编译通过 |
| JavaFX 版本 | 17.0.20（LTS 17），与编译目标兼容；四个模块版本统一 |
| 依赖精简 | 仅 openjfx 四模块 + JUnit 5，无多余依赖 |
| 构建与测试 | `mvnw.cmd clean compile` 通过；`mvnw.cmd test` 4 项用例全通过 |
| 版本错配修正 | 原 PR 的 JDK 21 / JavaFX 21.0.5 已下调至 17 / 17.0.20；Java 21 专有 API（`Math.clamp`、`SequencedCollection.getFirst()`）已降级为 17 可用写法 |
| 误删恢复 | 恢复 `mvnw`、`docs/ds-adventrue` 玩法文档、`assets/`，并恢复 `.gitignore` 中的 `docs/私人/` 忽略规则 |

**后续注意事项**：AI 生成代码须先编译验证并人工 review 后再合入 `main`；
提示词中先声明分层约束（模型层不 `import javafx.*`、业务规则不写在控制器）；生成后检查硬编码字面量与常量复用。

---

## 十三、运行注意事项 / FAQ

- **依赖下载**：首次构建需联网拉取 openjfx 依赖；离线环境无法使用 Maven，可先用本机 `javac` 编译核心层（`model` / `parser` / `util`）验证。
- **平台分类器**：`pom.xml` 默认 `win`；换平台加 `-Djavafx.platform=linux` / `mac`。
- **IDEA 直接运行**：等待 Maven 导入完成后直接运行 `EditorApp.main`（classpath 模式即可，无需额外 VM options；若 IDE 强制 module 模式，按提示补 JavaFX VM 参数）。
- **素材策略**：相对路径引用 + 缺失时占位；美术资源放进地图 `resources/` 同名覆盖即生效，编辑器选中素材会自动复制进地图并改为相对引用。
- **删除当前地图** 会**永久删除**文件夹（弹窗二次确认），请谨慎。
- **存档**：引擎内置 `scene` 变量与 `FlowVariables`（`var.*` / `nodevar.*` / `prop.*`）随存档写入；故事按钮 `action=save/load` + `target=槽位名`，或插件 / 逻辑层通过 `SavePortal` / `FlowContext` 调用。

---

## 十四、修订记录

| 版本 | 日期 | 修订说明 |
|---|---|---|
| v1.0 | 2026-09-07 | 初版：项目简介、技术栈版本表、运行方式、目录结构、AI 使用与核对说明 |
| v1.1 | 2026-09-10 | 技术栈对齐：编译目标 17、JavaFX 17.0.20、回归 JUnit 与 Maven Wrapper |
| v1.2 | 2026-09-11 | **重写为全项目说明**：补项目定位与成员、技术栈版本表、运行命令（mvnw）、完整目录结构、需求实现对照（P0/P1）、小游戏池与优先级、包职责与检查项分层映射、协作与 Git 规范、AI 使用与核对说明、FAQ 与修订记录；修正原 README 仅覆盖 Studio/Player 子模块、版本自相矛盾、章节编号重复等问题 |

更细的变更记录见 [`Changelog.md`](Changelog.md)。

> 示例地图的美术资源为代码生成的占位图；业务用途请替换为原创素材。
