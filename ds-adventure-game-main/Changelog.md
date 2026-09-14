# 更新日志 (Changelog)
## [v1.13] 双击即玩：启动器与免安装 exe

### Added
- 根目录三个启动器（纯 ASCII 批处理，避免 cmd.exe 对非 ASCII 字节的串行解析）：`启动游戏.bat`（剧情版）、`启动编辑器.bat`（Studio）、`打包EXE.bat`（jpackage 免安装 exe）
- `tools/launcher-hints-zh.txt`：中文上手提示（UTF-8，由批处理用 `type` 打印）
- 启动器首次运行自动 `package` + `dependency:copy-dependencies` 到 `target/lib`，之后直接以 `java -cp target/ds-adventure.jar;target/lib/*` 启动，无需每次走 Maven

### Changed
- `MainApp` 支持 `-Dapp.mode=player|editor`：双击打包后的 exe（无命令行参数）时决定进游戏还是进编辑器（默认编辑器）
- `.gitignore` 增加 `dist/`、`build/`：打包产物（约 400 MB）不入库

### Verified
- `启动游戏.bat` 实测：自动构建 → 启动 → 剧情推进（[Player] 对话结束→跳转场景）+ 自动信号 + **章末自动存档写入 `maps/story/saves/slot_ch0_depart.txt`（项目数=7）**
- `打包EXE.bat` 实测：生成 `dist/ds-adventure/ds-adventure.exe`（304 文件 / 406.7 MB）；双击启动后进程存活、窗口标题 `剧情播放器 — story`、存档目录自动生成

## [v1.12] 剧情编译器 + 小游戏结果契约（试验：序章 / 第1章 / 第2章）

### Added
- `tools/build_story.mjs`：剧本 DSL → `scenario.txt` 编译器。一个「拍点」= 一个场景，用 `dialog` 的 `target` 串联推进；立绘换表情复用同一节点 id（不叠图）；`@flag`/`@save`/`goto` 编译为「逻辑拍点」（`slot = 场景进入 | …` 后自动 goto）；`*choice` 编译为按钮 + 每选项一个逻辑拍点；`@minigame` 编译为 `event = <id>` + `mg.*` 场景属性。未编译章节的 `goto` 统一导向「本章待实现」占位场景
- `maps/story/scenario.txt`（生成产物）：序章 + 第1章 + 第2章，134 场景 / 550 节点 / 0 解析警告；BFS 走查全部场景可达
- `com.studio.plugin.MiniGameResult`：小游戏结果（胜负 + 分数）
- `GamePlugin.PARAM_RESULT_SINK`（结果回传口）与 `PARAM_FLAGS`（承接 `@minigame with:` 列出的 flag 当前值）

### Changed
- `FxAssets.loadRooted`：地图内找不到图片时**回退 classpath**（共享素材 `assets/sprites/**`），避免把几十 MB 素材复制进每张地图
- `ReaderView`：场景事件不再无条件「回到进入前场景」，而是读取触发场景的 `mg.onWin` / `mg.onLose`，按插件回传的胜负路由（未回传结果按胜利处理；`retry` / `ending` 暂按 `normal` 并记日志）
- `SnakePlugin` / `PlanePlugin`：接入结果回传（本局结束回传胜负；局中主动退出按需求记为失败；重开局重置）
- 素材归档：`archive_assets.py` 新增 `official_*` 批次规则（新角色进 `<cid>/<expr>.png`，已有主套的角色进 `<cid>/official/<expr>.png` 不覆盖），已归档 reimu / bugs / miku / amiya / paimon / pikachu / creeper / sai / gelili 等

### Notes
- 仍缺素材（工作区也没有）：`snake_expert`（第1章守鳞人）、`sclerk`（序章司秤吏）、背景 `star_rift` / `server_pipe` / `hakurei_shrine`（`server_hall` 由 `bg_tech_serverroom` 顶替）——均先走占位图

## [v1.11] 合并 PR #10（飞机大战 F5）

### Added
- 合并 PR #10：飞机大战插件 `com.studio.plugin.demo.plane`（`PlaneConfig` / `PlaneGame` 纯规则 / `PlanePlugin` 嵌入视图），事件 ID `plane`
- 演示地图 `docs/demo-maps/plane/scenario.txt`（场景级 `event` 与按钮级触发两种写法）
- 单元测试 `PlaneGameTest`（17 项）；测试总数 60

### Changed
- F5 数值对齐需求：3 条命、击落 20 架通关、每架 10 分、护盾 3 秒、敌机生成间隔 3 秒起每秒递减 0.1 秒（下限 0.5 秒）
- 操作形态确定为**键盘**（方向键 / WASD 移动，空格 / J 射击）—— 组内确认不需要虚拟摇杆；README 需求对照与 CONTRIBUTING 事件 ID 表同步
- 同时合并 PR #9（其自带条目为 v1.9 / v1.10）；本条目只补充 PR #10 的内容

## [v1.10] 场景自动信号三件套 + 节点信号/槽开关 + 倒计时时钟示例 + 若干修复

### Added
- **引擎自动信号扩成三个**（新增定义类 `AutoSignals`，编辑器与文档都从它取）：
  · `场景进入`（别名 进入场景 / 场景开始）：新场景渲染完后发，参数 `scene`、**`from`**（上一幕，第一幕为空）
  · `场景离开`（别名 离开场景 / 场景结束）：即将切走、旧场景还在时发，参数 `scene`、**`to`**（即将进入的场景）
  · **`上一个场景离开`（别名 上一幕离开 / 上个场景离开）**：新场景渲染完后（紧跟「场景进入」）发到**新的一幕**，
    参数 `scene`=被离开的场景、`to`=当前场景 —— “离开”这件事也能被**下一个场景**捕获（记录来路、接续上一幕的倒计时）
  · 顺序：场景离开（旧幕）→ 场景进入（新幕）→ 上一个场景离开（新幕）；`goto` 防重入照旧；别名与规范名一起发
- **节点属性 `signalsEnabled` / `slotsEnabled`**（默认都开，只在关掉时落盘；中文别名 信号开关 / 槽开关）：
  关掉信号 = 该节点的鼠标/键盘/按钮信号不再发出（也含槽里以它名义 emit 的信号）；
  关掉槽 = 挂在**本节点上**的槽不再执行（场景级槽不受影响）
- **内置插件族 `@plugin(switch)`**（别名 开关 / 节点开关 / 启用 / 禁用 / enable / disable）：
  `@plugin(switch) | on|off|toggle | 节点id | signals|slots|all` —— 运行时随时开关某个节点的信号/槽
  （过场锁交互、选项只能点一次、先停住某个提示节点的槽）
- **场景属性窗口「信号 / 槽」页补上快捷按钮**：常用鼠标/键盘信号（点击/释放/按键 F/空格/方向键↑↓）、
  🔔 三个自动信号一键订阅（带“什么时候发/参数/用途”提示）、以及
  `＋发信号槽 / ＋定时槽(after) / ＋循环槽(every) / ＋停定时槽 / ＋改属性槽 / ＋逻辑槽 / ＋跳场景槽 / ＋日志槽`
- **倒计时时钟示例地图** `maps/demo_clock`（文件 → 🎁 生成演示地图 → 倒计时时钟）：
  倒计时 / 正常时钟双模式（`M`）、`↑↓` 或按钮调 1~60 分、空格开始暂停、`R` 重置、`F` 快进到剩 5 秒；
  **走到 0 自动进「时间到」**，**没走完就走进「提前离开」**（两条分支是不同场景）；演示
  场景自动信号（`from` / 上一个场景离开）、`after`/`every`/`stoptimer` 定时器发信号、5 秒自动返回与取消
- 编辑器：节点属性窗口新增「**启用信号 / 启用槽**」复选框；画布给关掉开关的节点加角标（🔇 信号关 / ⛔ 槽关）

### Changed
- **左侧层级树点一下就选中**：以前只加 `.selected` 边框，而节点内容层铺满整个包装、把边框整个盖住，
  于是“在树里点了节点，画布上没有任何变化”；现在选中样式是单独一层**高亮圈画在内容之上**
  （内联样式，不依赖 studio.css），并且选中后**自动把节点滚进可视区**（放大/平移过也看得见）
- **左侧栏被选中的那一行明显变样式**：背景变淡（`#cdd6ff`）+ 字色变深（`#171b2e`）+ 加粗，悬停有一层淡色；
  并且**焦点在画布上时仍然保持该样式**（JavaFX 默认失焦会把选中行画得很淡，所以 `:selected` 与
  `.tree-view:focused .tree-cell:selected` 两条规则都写了）—— 以前是 `#3d4060` 深蓝灰画在深色侧栏上，几乎看不出来
- **左右双向同步**：画布 / 右键菜单 / 撤销重做里选中节点 → 左侧栏对应那一行自动选中并滚动到可见；
  在左侧栏点节点 → 画布高亮 + 滚进可视区；树重建（增删节点、换场景）后也会把当前选中的行恢复出来

### Fixed
- 修复**左侧栏「选中节点后点场景行选不中 / 要点两次场景样式才变 / 场景与节点样式分不清」**（四个连带问题）：
  · 在左侧栏点场景行会走 `switchScene + selectNode(null)`，而 `selectNode(null)` 把树的选中**清空**了 ——
    于是“点了一下那一行反而不亮”，必须再点一次才看到样式；现在没有选中节点时，选中行落在**当前场景那一行**，
    一次点击即生效
  · **选中场景时同时清掉节点选中**（画布高亮、右侧检查器、节点行都收掉）
  · **场景行与节点行两种颜色**：场景行选中 = 偏青亮蓝 `#8fe3ff` + 深字（平时加粗），
    节点行选中 = 淡蓝 `#cdd6ff` + 深字；实现是给 `TreeCell` 按“是场景还是节点”打
    `tree-scene` / `tree-node` 样式类
  · 悬停反馈、失焦仍保持选中色等细节一并补上
- 修复 **`@plugin(every)` 只响一次**：`ControlPlugin.schedule()` 把 `0 = 无限` 误判成“只发一次”
  （日志却写着“无限”），导致定时器/倒计时只跳一下 —— 现在 0 才是无限、1 才是单次、n 就是 n 次
- 修复 **`set` 槽的 `valueVar=变量名` 不生效**：只认 `valueVar=@var(名)`，直接写变量名会被当字面量
  （例如把节点文本写成“显示文本”四个字本身）；现在会去变量表取值，取不到才退回字面量并记告警
- 修复**节点属性窗口里「透明度」与「启用信号 / 启用槽」控件叠在一起**：加新行时误用了已被
  「逐字显示」占用的 GridPane 行号；现在行号唯一，并加了“同一行控件不得重叠”的探针断言
- 修复**控制台一直刷「找不到槽插件」长告警**：插件总目录会把注册表每一项都试加载一次，
  而 `plugins/plugins.ini` 里登记的 6 个是事件插件（`GamePlugin`），失败时对每个都打一条带
  全部自带插件 ID 的长告警，且目录**没有缓存**（下拉框建一次+提示一次、属性窗口、关于窗口各扫一遍 → 反复刷）。
  现在：`PluginRuntime.plugin(id, quiet)` 提供**探测模式**（真正执行槽时找不到插件**仍然告警**）、
  `PluginCatalog` 只汇总一句话、插件目录**按文件修改时间缓存**（改插件会立刻重扫）——
  一次会话长告警 0 条、汇总 1 条
- 修复**选中节点后画面自己抖动、屏幕上无关节点忽隐忽现**：把节点滚进可视区的对齐逻辑会**来回拉锯** ——
  `ensureVisible()` 单轴上先看左边再看右边（`if/else if`），而比可视区还大的节点两边都越界，
  左边对齐完、下一次调用又把它拉回右边；选中之后 `ensureVisibleSoon()` 每 60ms 再对齐一次、连追 6 帧，
  正好把这套拉锯播出来（实测平移量在 -1942 ↔ +1945 之间甩了 6 次、单帧位移约 3900px，
  地图被甩出屏幕，边上的节点就跟着忽隐忽现）。现在对齐改成**不动点**：装不下时已经把可视区盖住就**不动**、
  否则只对齐左边/上边，反复调用结果一致；“再追几帧”也只在**这一帧真的动了**时才继续；
  并去掉 `layoutBounds` 上“尺寸一变就再对齐一次”的监听（布局何时变不确定，每次对齐都会挪画面 = 画面自己跳）。
  另外**在画布上直接点节点不再自动挪画面**（节点本来就在眼皮底下，再对齐只会把用户脚下的画面拖走，
  点一下画面就跳），左侧栏点节点仍然会滚进可视区
- 修复**左侧栏某一行的样式可能短暂挂错（看起来就是“无关节点闪了一下”）**：`TreeCell.updateItem()`
  以前用单元格自己缓存的 `getTreeItem()` 判断“这一行是场景还是节点”，而 JavaFX 是**先 `updateIndex`
  再换 treeItem** —— 滚动回收 / 整棵重建的那一帧拿到的还是旧项，查不到 owner 就落到 `tree-root` 兜底分支，
  那一行会闪一下根节点的样式；现在改用**行号 → 树项**的权威映射（`TreeView.getTreeItem(getIndex())`），
  查不到就**不打任何类名**。同时：在左侧栏点节点不再**整棵重建层级树**（同一幕以前也会走 `switchScene`，
  每点一行都要重建树 + 重画画布、选中行先消失再出现），`selectItemFor()` 也只有该行**没完整露出来**时才滚动
  （以前每选一次就把那一行猛拉到顶端）

## [v1.9] 音乐节点更名为「系统提示节点」+ 废除节点 audio 属性

### Changed
- 原「音乐节点」**更名为「系统提示节点」**（`type = toast`，中文 `系统提示` / `提示` 也能解析）：
  屏幕角上的提示条，常用于右上角显示「存档中… / 已保存 / 获得道具」
  - **保留自带默认样式**：深色半透明圆角底 + 淡蓝描边 + 投影 + 白字，不需要任何美术素材；节点 `style` 的内联样式叠加在默认样式之后
  - 拖出来即摆在逻辑画面（1280×720）右上角（边距 24）
  - 它就是**普通节点**：显隐 / 位置 / 层级 / 过渡 / 信号槽 / 插件全部用现有属性（`visible`、`x/y`、`index`、`transition = opacity:300ms`…）
  - **没有新增任何节点属性，也没有新增任何界面按钮**（属性窗口 / 检查器 / 画布预览都不加东西）
  - 编辑器工具箱与右键「添加节点」里的名字同步改为「系统提示」，画布预览按阅读器的样子绘制（自带默认样式）
- 音频统一走 `@plugin(audio)`（多通道 bgm/se，支持 loop/play/stop/pause/resume/volume/stopall）；
  新建地图模板与三个演示地图工厂（模板 / 分支 / 打砖块）改用
  `slot = 场景进入 | @plugin(audio) | loop | resources/audio/theme.wav | bgm` 放 BGM

### Removed
- 节点 `audio` 属性（模型 / 序列化 / 编辑器字段 / 属性读写全部移除）
- `MUSIC` 节点类型（`type = music`）

### Fixed
- 老地图兼容：`type = music` / `audio = …` 不再播放，但**内容不丢** —— `audio` 键收进 `extras` 原样写回，解析时按行给出迁移提示（编辑器日志与解析警告里可见）

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
