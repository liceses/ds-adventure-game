# 更新日志 (Changelog)
## [v1.8] 合并 PR #6（内置插件全家桶 + 场景自动信号 + 序章地图）

### Added
- 合并 PR #6：21 个内置插件（select/after/every/stoptimer、rand、confirm/input、文本与格式、cast/bg/fx、audio/video、存档槽位、json/list、系统含 fullscreen、http/llm、clock/debug/trace/dump）与 BuiltinCatalog 插件目录
- 引擎：新增「场景进入 / 场景离开」自动信号（带防重入）；宿主新增 animate / snapshot / toggleFullscreen；PluginContext.rawArgs()/outputVarName()
- 编辑器：打开地图列表窗口、拖放打开、右键平移、外部编辑工作流、个性化节点模板（node-presets.txt）
- 地图：`maps/prologue_404`（序章 15 幕 / 142 节点，零素材可玩）

### Changed
- `plugins/README.txt` → `plugins/README.md`（含上游全部内容 + 插件手册章节）；同步修正 CONTRIBUTING / README 中的 5 处引用
- 《引擎对接说明》升至 v1.1：缺口①「场景进入」已实现、缺口⑥ 由 `select` 覆盖、缺口④ 多数由 `fx`/`bg`/`cast` 覆盖；剩余工作收窄为「编译器 + 小游戏结果契约 + `@ending` + UI 栏」
- `.gitignore`：`maps/` → `maps/*` + `!maps/prologue_404/`（只放开这一张地图）

### Fixed
- 槽给「别的场景」的节点设属性被静默丢弃 → 记为属性覆盖，该幕渲染时生效（PR #6）

## [v1.7] 合并 PR #3 / #4，并将 PR #5 改造为贪吃蛇插件

### Added
- 合并 PR #4：记忆翻牌插件 `com.studio.plugin.demo.memory`（事件 ID `memory`，演示地图 `docs/demo-maps/memory/`）
- 合并 PR #3：打砖块插件 `com.studio.plugin.demo.breakout`（事件 ID `breakout`，编辑器菜单可生成演示地图）
- 基于 PR #5 完成贪吃蛇插件化：`com.studio.plugin.demo.snake`（`SnakeConfig` / `SnakeGame` 纯规则 / `SnakePlugin`），事件 ID `snake`，演示地图 `docs/demo-maps/snake/`
- 新增单元测试 `SnakeGameTest`（13 项）；合并后测试总数 43（4 + 12 + 14 + 13）

### Changed
- PR #5 原始实现（`com.example.snake` + 独立 `Application` 入口 + `pom.xml` 的 `snake` profile）改造为内置插件：删除独立入口与 profile，注册进 `plugins/plugins.ini`
- 贪吃蛇对齐需求 F4：10×10 格、初始长度 3、累计 97 豆通关、速度 = 初速 + 0.01 × 已玩秒数、允许 180° 反向（反向致蛇头与身体重叠判失败）
- 关闭超纲玩法：限时（默认 0 = 不限时，避免与 97 豆通关冲突）、障碍（默认 0 个，配置项保留）

### Fixed
- `plugins/plugins.ini` 合并冲突：`breakout` 与 `memory` 两行共存

## [v1.6] 同步编辑器（Studio）v0.5：节点体系 · 存档变量 · 信号槽 · 插件 · 易用性

### Added
编辑器：
- 新增 **文本框节点**（`type = textbox`）：单行/多行，可绑定存档变量（玩家输入实时写入变量）
- 节点新增 **`index` 层级属性**（落盘，读入按 index 排序），属性窗口与检查器均可直接改
- 右侧检查器新增「**➕ 新增节点模板**」栏：右键“添加节点”生成的节点与模板状态一致
- 「地图全局设置 [option]」新增**存档变量表格**（名称 / 类型 / 初值，可增删，含重名·空名·非法字符校验）
- 新增「**🧩 场景属性**」窗口：属性表 + 节点列表（点选即联动画布）+ 信号与槽列表（可增删改）
- 新增 **撤销 / 恢复**（工程快照双栈，默认 80 步）：`Ctrl+Z` / `Ctrl+Y` / `Ctrl+Shift+Z`，
  编辑菜单显示将要撤销/重做的操作名并随历史启用禁用
- 编辑菜单补齐「**⬆ 上移一层** / **⬇ 下移一层**」（`Ctrl+↑` / `Ctrl+↓`）
- 新增「帮助 → **📖 使用帮助**」四页签窗口：地图脚本语法 / 内联 CSS 样式 / 信号·槽·表达式·插件 / 快捷键
- 新增一键生成演示地图：`demo_var_plugin`（存档变量 / 表达式 / `@plugin`）、
  `demo_logic_gate`（两个开关控制三盏灯：灯3 = 灯1 且 灯2，全部在编辑器内搭成，无需外部文件）

引擎与变量：
- 新增**存档变量体系**：`[option]` 中 `savevar = 名称 | 类型 | 初值`（`int`/`long`/`double`/`bool`/`str`），随存档保存；
  新增强制类型转换 `@int` `@long` `@double` `@bool` `@str`（转换失败不报错；布尔与数字可互转）
- 新增**表达式**：`@var(名)`、`@var(名, 默认值)`、`@node(属性)`、`@node(节点id, 属性)`、`@param(名)`，
  可嵌套，也可混排在文字里
- 插件事件整合进信号与槽：新增槽动作 **`@plugin(插件名)`**（动作之后的字段按序求值成字符串数组传入，
  返回同长度数组后回写：**输出位一定回写，其它位置仅当插件确实改过才回写**）
- 新增**编辑器自带插件**（随程序发行，无需注册）：算术 `add`…`dec`；逻辑与比较 `and` `or` `xor` `not` `gt`…`ne`；
  **音频 `audio`**（多通道、循环/一次性/暂停/音量，不再依赖节点 `audio` 属性）；
  **视频 `video`**（挂在节点上代替图片，素材缺失自动退化为占位块）
- 节点新增 **`video` 视频属性**（可写中文「视频」）
- 新增插件开发接口 `SlotPlugin` + `PluginContext`（变量/存档读写、信号收发、渲染接口），
  并由一把可重入锁串行化“插件执行 + 变量读写 + 存档读写 + 信号派发”，渲染自动切回 JavaFX 线程
- 新增 `plugins/varplugins.ini` 变量插件注册表与 `plugins/examples/VarPluginTemplate.java` 开发模板

### Fixed
- **插件 `onDetach()` 的触发路径补齐到四条**（v1.5 只覆盖前两条）：
  · 玩家点【← 返回剧情】 · 读档收起插件层（v1.5 已有）
  · **被另一个插件替换**（`runPlugin()` 检测到实例不同 → 先 detach 旧实例）
  · **关闭播放器窗口**（`shutdown()` → 当前事件插件 detach，并让所有 `@plugin(...)` 槽插件收到 `SlotPlugin.onDetach()`）
  引擎先清引用再回调，保证每运行一次最多回调一次；`onDetach()` 内部异常改为记错误日志（含堆栈）后继续
- 修复打开地图后**画布不显示初始场景**（左侧树已选中、画布却空白）
- 修复点击层级树里**已选中**的场景/节点没有反应
- 修复**节点上右键弹出的其实是空白处菜单**（两个菜单重叠，现合并为一种并先命中该处节点）
- 修复**插件输入参数被结果覆盖**（例如 `@int(@var(灯1))` 会把 灯1 写成 0）
- 修复**布尔变量不能当数字用**（`@int(布尔变量)` 之前恒为 0）
- 修复**下拉弹层浅字浅底看不清**（ComboBox / ChoiceBox / DatePicker / ColorPicker 统一深色高对比配色）
- 修复「地图全局设置」中**字段文字与深色底相近**、**背景色的色值显示不全**
- 修复**右键菜单必须点一下画布才收起**（现在点击界面任意位置都会收起）
- 修复存档变量**「类型」下拉点不开 / 选不中**（选值后立刻刷新表格会把弹层顶掉）
- 修复**节点属性窗口只有信号/槽文本框、没有列表**（现与场景属性窗口一致，两张可增删表格）

### Docs
- `plugins/README.txt` 新增「**插件生命周期**」小节（`execute` / `createEmbeddedView` / `onDetach` 四条触发路径一览）
- `CONTRIBUTING.md` §2.6 触发路径表同步补齐为四条，并说明 `onDetach()` 异常的兜底行为

## [v1.5] 修复插件 onDetach 不被回调

### Fixed
- `ReaderView` 现在会在插件被移除时回调 `GamePlugin.onDetach()`：
  - 玩家点【← 返回剧情】（`leavePlugin()`）
  - 读档时收起插件层（`exitPluginIfShown()`）
  - 新增 `activePlugin` 字段与 `detachActivePlugin()` 统一收尾（捕获插件异常、打日志）
- 影响：使用 `AnimationTimer` / `Timeline` 的实时小游戏（贪吃蛇、飞机大战等）返回剧情后
  循环不再残留后台运行（此前 `onDetach()` 定义了但引擎从不调用）

### Docs
- `CONTRIBUTING.md` §2.6 由「已知缺陷 + 对策」更新为正式的引擎回调契约（含触发路径表、
  幂等 `stopLoop()` 示例、兜底监听、`onDetach()` 内禁止事项）；§7.4/§8 同步更新
- `README.md` 插件接口与「界面包装」说明补充 `onDetach()` 回调时机

## [v1.4] 新增协作规范 CONTRIBUTING.md

### Added
- `CONTRIBUTING.md`：面向组员的小游戏/剧情/公共代码协作规范
  - 环境与版本硬约束（Java 17 目标、JavaFX 17.0.20、禁止 Java 21 专有 API、依赖需全组同意）
  - 小游戏接入：内置插件（推荐）vs 外部插件、最小插件骨架、`GamePlugin` 参数表、
    `plugins/plugins.ini` 注册规则、剧情触发写法、事件 ID 命名表（与需求 F 编号对应）
  - 生命周期说明：**`onDetach()` 当前不会被引擎调用**的已知缺陷与两种对策
  - 素材规范、演示地图共享（`maps/` 被忽略的三种解法）、分支/提交/PR 模板、
    提交前自检清单、常见问题排查表、明确禁止事项

### Fixed
- `plugins/README.txt` 更新过期内容：产物名 `visual-novel-studio.jar` → `ds-adventure.jar`、
  JavaFX 21.0.5 → 17.0.20、`release 21` → 17、依赖坐标 `visual-novel-studio` → `ds-adventure`、
  命令 `mvn` → `mvnw.cmd`；并加指引指向 `CONTRIBUTING.md`

## [v1.3] 构建坐标统一与测试插件固定

### Changed
- `artifactId`：`visual-novel-studio` → **`ds-adventure`**（与仓库名一致；`<finalName>` 原已为 ds-adventure）
- 显式声明 `maven-surefire-plugin 3.2.5`，避免随 Maven 版本漂移（原依赖 Maven 默认绑定）
- `pom.xml` 注释中的命令示例统一改为 `mvnw.cmd`（原为 `mvn`）

### Docs
- README（技术栈表 / 构建坐标 / 说明）同步更新

## [v1.2] README 重写为全项目说明

### Changed
- README 由「Studio/Player 子模块说明」重写为覆盖整个仓库的项目文档：
  项目定位与成员、技术栈版本表、运行命令（改用 mvnw，无需全局 Maven）、完整目录结构、
  需求实现对照（P0 六项 / P1 / P2）、小游戏池与优先级、包职责与检查项分层映射、
  协作与 Git 规范、AI 使用与核对说明、FAQ、修订记录
- 快速开始修正：删除「JDK 21 + Maven 3.8+」，统一为 `mvnw.cmd`（含 `test`）
- 目录结构注释修正：`JDK21 + JavaFX21` → `JDK 17 目标 + JavaFX 17`
- 补记 JUnit 测试：`ParserSelfTest`（自测 45 项）与 `ScriptParserTest`（JUnit 4 项）分开表述

### Fixed
- 修正上一版 Changelog 中「artifactId 对齐仓库名」的不实记录（artifactId 实为未改动）

## [v1.0-adapt]（PR#1 吸收改造）

### Changed
- 按《需求规格说明书 v1.1》与实训检查项对齐：编译目标 Java 21 → 17，JavaFX 21.0.5 → 17.0.20（LTS 17）
- 回归 JUnit 5 + surefire（P0-8：mvn test 通过），新增 ScriptParserTest（解析/往返/未知键透传/告警 4 项）
- 恢复 Maven Wrapper（mvnw.cmd）、docs/ds-adventrue 玩法文档、assets 目录；恢复 .gitignore 的 docs/私人/ 忽略行
- artifactId 未改动（仍为 `visual-novel-studio`）；仅 `<name>` 与 `<finalName>` 改为 ds-adventure，包名与坐标统一列为待办

### Fixed
- Java 21 API 降级为 17：Math.clamp → Math.max/min、SequencedCollection.getFirst() → get(0)

## [v0.4]

### Added
- v0.3添加对话框点击后可出现下一段对话
- v0.3添加对话框新属性可使文字逐个显示
- v0.3添加右键后可删除选中节点的功能
- v0.3添加存档读档功能（仍在测试）

- v0.4添加地图逻辑模块
- v0.4为节点属性添加槽与信号列表
- v0.4存档与读档功能编辑器内置可实现无需插件

### Fixed
- v0.2修复v0.1按钮点击无效问题

- v0.3修复v0.2右键弹窗出现点击空白处无法消除的bug
- v0.3修复v0.2对话框文字显示问题

- v0.4修复部分快捷键失效问题

### Changed
- v0.3将鼠标移动到左侧出现的工具栏弹窗（未实现）改为常驻

- v0.4完整属性窗口改为滑条拉动查看

### Deprecated
- 
