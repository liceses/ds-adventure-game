# 贡献指南（CONTRIBUTING）

> 本文件是 **ds的奇妙冒险** 项目的统一协作规范，面向"开发小游戏 / 剧情 / 工具"的全体组员。
> 目标：任何人按本规范提交，**拉下来就能编译、能跑、能验收**，且不破坏他人工作。

---

## 0. 适用范围

| 你在做什么 | 看哪几节 |
|---|---|
| 开发一个小游戏（贪吃蛇、飞机大战、打砖块…） | §1 → §2 → §3 → §4 → §5 → §6 |
| 写/改剧情、地图、剧本 | §1 → §4 → §5 → §6 |
| 改引擎、编辑器、插件加载器等公共代码 | 全部 + §7.3 |
| 维护者（组长）收 PR | §7 |

---

## 1. 环境与版本约束（硬约束，先看这条）

```bat
git clone https://github.com/liceses/ds-adventure-game.git
cd ds-adventure-game
mvnw.cmd clean compile        :: 首次会联网拉依赖 + Maven
```

| 约束项 | 值 | 说明 |
|---|---|---|
| 编译目标 | **Java 17**（`<release>17</release>`） | 本机可装 JDK 17 或 21，但**不得使用 Java 21 专有 API** |
| JavaFX | **17.0.20** | 不是 21.x，API 以 17 为准 |
| 构建 | **Maven Wrapper**（`mvnw.cmd` / `./mvnw`） | 无需全局装 Maven；**不要用裸 `mvn`** 写文档/脚本 |
| 依赖 | 仅 openjfx 四模块 + JUnit 5 | **新增第三方依赖需全组同意并同步改 `pom.xml`** |

> ⚠️ **最容易踩的坑**：用 JDK 21 的新 API 会直接编译失败。已发生过一次，典型例子：
> `Math.clamp(...)`（21 新增）、`list.getFirst()`（SequencedCollection，21 新增）。
> 自检就是跑 `mvnw.cmd clean compile`——报错会精确指向行号。

---

## 2. 小游戏接入规范

### 2.1 选择接入形态

| | **形态 A：内置插件（推荐）** | 形态 B：外部插件 |
|---|---|---|
| 源码位置 | `src/main/java/com/studio/plugin/demo/`（或自建子包） | `plugins/examples/` 或你的独立工程 |
| 如何进库 | 源码直接提交，随主程序编译 | 只提交**源码**；`plugins/classes/**`、`plugins/*.jar` **被 `.gitignore` 忽略，无法入库** |
| 队友体验 | 拉下来 `mvnw.cmd -Pplayer javafx:run` 即可玩 | 队友需自行编译（见 `plugins/README.md`） |
| 适用 | 课程作业、需要评审验收的小游戏 | 独立分发、不想进主工程 |

**结论：课程作业一律走形态 A。**

### 2.2 最小插件骨架（形态 A）

```java
package com.studio.plugin.demo;              // 或你的子包，见 §2.8

import com.studio.plugin.GamePlugin;
import javafx.animation.AnimationTimer;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.Map;

/** 事件 ID: snake（在 plugins/plugins.ini 注册，见 §2.4） */
public class SnakePlugin implements GamePlugin {

    private AnimationTimer loop;             // 实时游戏循环
    private StackPane view;                  // 嵌入主舞台的界面

    @Override
    public void execute(Stage stage, Map<String, Object> params) {
        // 嵌入模式下无需额外动作（引擎随后会调用 createEmbeddedView）
    }

    @Override
    public Parent createEmbeddedView(Map<String, Object> params) {
        view = new StackPane(/* 你画的棋盘 Canvas / GridPane … */);
        startLoop();

        // 兜底：视图被摘除时再停一次（主路径是引擎回调 onDetach，见 §2.6；stopLoop 需幂等）
        view.parentProperty().addListener((obs, old, now) -> {
            if (now == null) stopLoop();
        });
        return view;
    }

    @Override
    public String displayName() { return "贪吃蛇"; }

    @Override
    public void onDetach() { stopLoop(); }   // 引擎移除插件时回调（见 §2.6）

    private void startLoop() {
        loop = new AnimationTimer() {
            @Override public void handle(long now) { /* 每帧：读输入 → 更新 → 重绘 */ }
        };
        loop.start();
    }

    private void stopLoop() {
        if (loop != null) { loop.stop(); loop = null; }
    }
}
```

**两种运行模式**（引擎按返回值自动判定）：

1. **嵌入模式（推荐）**：覆写 `createEmbeddedView()` 返回 `Parent` → 引擎把它放进主舞台中央，顶部自动生成「🎮 插件名（事件: id） … ← 返回剧情」标题栏；
2. **窗口模式**：只实现 `execute()`、`createEmbeddedView()` 返回 `null` → 插件自行弹出并管理 `Stage`（引擎仅打印日志）。

### 2.3 引擎传入的参数（`params` 约定键）

引擎在**每次触发**时调用一次 `execute(stage, params)`，随后调用 `createEmbeddedView(params)`：

| 常量 | 键 | 类型 | 含义 |
|---|---|---|---|
| `GamePlugin.PARAM_MAP_FOLDER` | `map.folder` | `File` | 当前地图文件夹 |
| `GamePlugin.PARAM_PLUGIN_ID` | `plugin.id` | `String` | 触发本插件的事件 ID |
| `GamePlugin.PARAM_EMBEDDED` | `embedded` | `Boolean` | `true` 表示以嵌入模式托管 |
| `GamePlugin.PARAM_HOST_STAGE` | `host.stage` | `Stage` | 宿主舞台 |
| `GamePlugin.PARAM_BACK_CALLBACK` | `back.callback` | `Runnable` | 请求「返回剧情」（等价于点标题栏的返回按钮） |
| `GamePlugin.PARAM_SAVES` | `saves` | `SavePortal` | 存档门户，可读写地图 `saves/` 任意槽位 |

用法示例（插件内自己放一个返回按钮）：

```java
Runnable back = (Runnable) params.get(GamePlugin.PARAM_BACK_CALLBACK);
Button btn = new Button("结束本局并返回");
btn.setOnAction(e -> {
    stopLoop();      // 先自己收尾，再让引擎切回剧情
    back.run();
});
```

### 2.4 注册到 `plugins/plugins.ini`（必须）

```ini
# plugins/plugins.ini
snake = com.studio.plugin.demo.SnakePlugin
plane = com.studio.plugin.demo.PlanePlugin
```

- 格式：`事件ID = 全限定类名`（`#` 开头为注释）。
- **不要修改 `PluginLoader.BUILTIN`**：那是硬编码白名单（`minesweeper` / `2048` / `savepanel`），
  而 `plugins.ini` 的同名条目优先级更高。走 ini 注册可避免多人同时改公共代码产生冲突。
- 加载顺序（了解即可）：`plugins.ini` → 内置白名单 → 应用 classpath → `URLClassLoader`
  扫 `plugins/` 根、`plugins/classes/`、`plugins/*.jar`。

### 2.5 在剧情里触发小游戏

```ini
# 场景级：进入该场景即触发（进入小游戏前的场景 = 返回目标）
[Forest]
event = snake

# 节点级：按钮点击触发（返回当前场景）
{
type = button
text = 来一局贪吃蛇
action = event
event = snake
}
```

引擎行为：同一个插件**不会重复进入**（已在插件模式时新的触发被忽略）；返回时**不会重复触发**进入它的场景事件（引擎内部已做抑制）。

### 2.6 生命周期与资源释放（务必按此写）

引擎在**插件从主舞台移除时**会回调 `GamePlugin.onDetach()`，下列四条路径都会触发（每运行一次插件最多回调一次）：

| 触发路径 | 引擎行为 |
|---|---|
| 玩家点标题栏【← 返回剧情】 | `ReaderView.leavePlugin()` → 回调 `onDetach()` → 回到进入插件前的场景 |
| 读档时收起插件层 | `ReaderView.exitPluginIfShown()` → 回调 `onDetach()` |
| 同一局里又触发了**另一个**插件事件 | `ReaderView.runPlugin()` 检测到实例不同 → 先给旧插件回调 `onDetach()`，再 `execute()` 新插件 |
| 关闭播放器窗口 | `ReaderView.shutdown()` → 回调当前事件插件的 `onDetach()`；同时所有 `@plugin(...)` 槽插件收到 `SlotPlugin.onDetach()` |

因此**实时小游戏必须在 `onDetach()` 里停掉自己的游戏循环**：

```java
private AnimationTimer loop;

@Override
public void onDetach() { stopLoop(); }        // ★ 引擎会调用，勿留空

private void stopLoop() {                      // 必须幂等（onDetach 与兜底监听都可能触发）
    if (loop != null) { loop.stop(); loop = null; }
}
```

补充约定：

1. `onDetach()` 抛异常不会导致引擎卡住（引擎捕获后记错误日志，含堆栈），但**请勿在其中做耗时操作或弹窗**；
2. 同一时刻只有一个插件被托管：重复触发会被忽略（`runPlugin` 有重入保护），托管新插件前会先 detach 上一个；
   引擎清引用后才回调，因此 `onDetach()` 内的异常或（极端情况下）再次触发事件都不会造成重复回调；
3. **建议再加一层兜底**，以防将来有其他路径移除插件视图：
   ```java
   view.parentProperty().addListener((obs, old, now) -> { if (now == null) stopLoop(); });
   ```
4. 自建「返回」按钮时，先 `stopLoop()` 再调 `back.callback`，语义更清晰。

### 2.7 事件 ID 命名建议（与需求编号对应）

| 事件 ID | 小游戏 | 需求编号 | 优先级 |
|---|---|---|---|
| `snake` ✅ 已接入 | 贪吃蛇 | F4 | **P0** |
| `plane` | 飞机大战 | F5 | **P0** |
| `2048` ✅ 已接入 | 2048 | — | P1 |
| `minesweeper` ✅ 已接入 | 扫雷 | — | P1 |
| `breakout` ✅ 已接入 | 打砖块 | — | P1 |
| `memory` ✅ 已接入 | 记忆翻牌 | — | P1 |
| `linkgame` | 连连看 | — | P1 |
| `sokoban` | 推箱子 | — | P1 |
| `gomoku` | 五子棋 | — | P1 |

> ID 一旦被地图引用就不要改名，否则已写好的 `event = xxx` 会失效。

> **已接入**：`snake`、`breakout`、`memory`（另有 `minesweeper` / `2048` / `savepanel` 三个内置 demo）。
> 其中 `snake` 的实现可作为范例：`SnakeGame` 为纯规则（零 JavaFX 依赖）+ `SnakeGameTest` 单元测试 + `docs/demo-maps/snake/` 演示地图。

### 2.8 一个游戏一个包，别互相踩

- 多人并行开发时，各自使用独立子包，例如：
  `com.studio.plugin.demo.snake.SnakePlugin`、`com.studio.plugin.demo.plane.PlanePlugin`；
- 已有 `demo` 包内是 `MinesweeperPlugin` / `Game2048Plugin` / `SavePanelPlugin`，**不要改动它们的公共命名**；
- 类名建议 `<Game>Plugin`，避免与他人同名。

---

## 3. 素材规范

| 类型 | 放置位置 | 说明 |
|---|---|---|
| 小游戏自己的图/音 | `src/main/resources/assets/minigames/<游戏>/` | 例如 `.../snake/apple.png`；代码用 `getClass().getResource("/assets/minigames/snake/apple.png")` |
| 剧情地图素材 | 地图文件夹内 `resources/` | 脚本用相对路径 `resources/…` 引用；缺失时引擎显示占位图 |
| 通用立绘/背景 | `src/main/resources/assets/sprites/` | 已由美术管线产出 |

**要求**：
- 单个文件建议 **≤ 2 MB**，整批素材建议 **≤ 50 MB**（仓库是纯 Git，大文件不可回收）；
- **不要提交**：录屏（`.mp4`/`.gif`）、生成中间产物、`target/`、`.class`、`.jar`、IDE 配置（`.idea/`）；
- 命名用小写+下划线（`snake_head.png`），避免中文文件名（跨平台编码问题）。

---

## 4. 演示地图怎么共享（`maps/` 被忽略的坑）

`.gitignore` 里 `maps/` 被忽略，**运行时示例地图默认提交不上去**。三种解法：

| 方案 | 做法 | 评价 |
|---|---|---|
| **A（推荐）** | 把演示地图放 `docs/demo-maps/<名字>/`，本地用编辑器「打开地图文件夹…」选中它 | 不动 `.gitignore`，评审也能看到 |
| B | 改 `.gitignore`：把 `maps/` 改为 `maps/*`，再加 `!maps/<你的地图>/` | ⚠️ 注意 Git 规则：**父目录被忽略时，无法用 `!` 重新包含其子项**，必须先写成 `maps/*` 才能白名单 |
| C | `git add -f maps/<名字>` 强制加入 | 不推荐：状态仍显示为 ignored，队友容易困惑 |

> 无论哪种方案，`config.ini` 里的 `map.folder` 是**仓库内共享文件**，改成你的地图会影响其他人；
> 请只在本地临时修改，或把演示地图路径写进 PR 说明让大家自己切。

---

## 5. 分支 / 提交 / PR 规范

### 5.1 分支

```bat
git checkout main
git pull                                  :: ★ 先同步，别在旧代码上开发
git checkout -b feat/snake                :: 命名的三种前缀
```

| 前缀 | 用途 | 示例 |
|---|---|---|
| `feat/` | 新功能、新小游戏 | `feat/snake`、`feat/story-engine` |
| `fix/` | 修 bug | `fix/plugin-detach` |
| `docs/` | 只改文档 | `docs/contributing` |

- **一个功能一个分支**，分支名用英文小写与连字符；
- 不要直接在 `main` 上开发、不要 `git push --force` 到 `main`。

### 5.2 提交信息（Conventional Commits）

```
<type>: <中文简述>

- 要点 1
- 要点 2
```

| type | 用途 |
|---|---|
| `feat` | 新功能 / 新小游戏 |
| `fix` | 修 bug |
| `docs` | 文档 |
| `chore` | 构建、依赖、配置 |
| `refactor` | 重构（不改行为） |

示例：

```
feat: 新增贪吃蛇小游戏插件（F4）

- 10×10 棋盘、吃豆 97 通关、速度每秒 +0.01
- 嵌入模式接入，经 PARAM_BACK_CALLBACK 返回剧情
- 事件 ID: snake，已在 plugins/plugins.ini 注册
```

**要求**：提交要**聚焦**（一个提交一件事），不要把素材、脚本、源码、文档混在一个提交里。

### 5.3 PR 流程与模板

```bat
git add <只加你改的文件，别用 git add -A 一把梭>
git status                                :: ★ 确认没有误加 target/、.class、私人文档
git commit -m "feat: 新增贪吃蛇小游戏插件（F4）"
git push -u origin feat/snake
```

在 GitHub 开 PR：**base = `main`，compare = 你的分支**。描述请包含：

```
## 改了什么
（一段话 + 涉及的文件范围）

## 接入方式
- [ ] 内置插件（源码进主工程）
- [ ] 外部插件（仅源码，产物不入库）
- 事件 ID：snake
- 触发方式：场景级 event / 按钮 action=event

## 怎么验证
- [ ] mvnw.cmd clean compile 通过
- [ ] mvnw.cmd test 通过（4 项）
- [ ] mvnw.cmd -Pplayer javafx:run 实际玩通：进入 → 游玩 → 返回剧情（无残留、无卡顿）
- 验收要点：吃满 97 豆通关 / 撞墙撞自身判负

## 是否改动公共代码
- [ ] 无
- [ ] 有（请列出文件并说明原因：pom.xml / PluginLoader / ReaderView / plugins.ini 已有条目 …）

## 截图 / 录屏
（可选，建议附一张游戏内截图）
```

> ⚠️ **改动公共代码必须在 PR 描述中明确标注**，并在群里同步。
> 历史教训：曾有 PR 一次性重写了全仓库（删掉既有脚手架与文档），因为没标注、没同步，导致验收口径被打乱。

### 5.4 合并方式

由维护者合并，保留分支历史：

```bat
git checkout main
git merge --no-ff feat/snake -m "Merge PR #N: 贪吃蛇小游戏插件（F4）"
git push origin main
```

---

## 6. 提交前自检清单

| # | 检查项 | 命令 / 做法 | 通过标准 |
|---|---|---|---|
| 1 | 已同步主线 | `git pull` | 无冲突、基于最新 `main` |
| 2 | 编译通过 | `mvnw.cmd clean compile` | `BUILD SUCCESS`，无 Java 21 API 报错 |
| 3 | 单测通过 | `mvnw.cmd test` | `Tests run: 4, Failures: 0` |
| 4 | 实机可玩 | `mvnw.cmd -Pplayer javafx:run` | 能进游戏 → 能玩 → 能返回剧情，**返回后无残留循环/无报错** |
| 5 | 注册正确 | 检查 `plugins/plugins.ini` | 事件 ID 有对应一行，类名拼写正确 |
| 6 | 无垃圾入库 | `git status` | 不含 `target/`、`.class`、`.jar`、`docs/私人/`、大素材、录屏 |
| 7 | 分支/提交规范 | 见 §5.1 / §5.2 | 分支名合规、提交信息带 type 前缀 |

---

## 7. 维护者（组长）流程

### 7.1 收 PR

```bat
git fetch origin
git checkout <PR 分支>            :: 或 git fetch origin pull/N/head:pr-N
mvnw.cmd clean compile
mvnw.cmd test
mvnw.cmd -Pplayer javafx:run      :: 按 PR 描述的验收要点试玩
```

### 7.2 合并后

```bat
git checkout main
git merge --no-ff <PR 分支> -m "Merge PR #N: <说明>"
git push origin main
git branch -d <本地分支>
```

### 7.3 公共代码改动的裁决

以下文件/目录属于**公共资产**，任何人改动都应在 PR 描述标注 + 群里同步：

```
pom.xml                        构建、版本、依赖
plugins/plugins.ini            插件注册表（新增自己的一行是允许的）
src/main/java/com/studio/plugin/PluginLoader.java      插件加载器
src/main/java/com/studio/reader/ReaderView.java        剧情渲染与插件宿主
src/main/java/com/studio/parser/**                     脚本解析（改动会影响所有地图）
src/main/java/com/studio/flow/**                       信号/槽引擎
```

### 7.4 待修事项（已知）

- `maven-surefire-plugin` 已显式固定 `3.2.5`；升级需全组同步。
- P0 F3 的「小游戏结果回传剧情引擎（通关/失败 → 三分支调度）」尚未落地，需扩展 `GamePlugin` 接口（见 README §5）。

---

## 8. 常见问题排查

| 症状 | 可能原因 | 处理 |
|---|---|---|
| `找不到符号: Math.clamp` / `getFirst()` | 用了 Java 21 专有 API | 换成 Java 17 写法：`Math.max/min`、`list.get(0)` |
| `在 classpath 与 plugins 目录中都找不到插件类` | 没注册 / 类名或包名写错 / 没重新编译 | 检查 `plugins/plugins.ini` 的 `全限定类名`；`mvnw.cmd clean compile` |
| 触发了但什么都没出现 | `createEmbeddedView()` 返回了 `null`（走了窗口模式），或抛异常 | 看控制台日志（`Logs.plugin` / `插件运行异常`）；确认返回了 `Parent` |
| 返回剧情后仍在后台跑、卡顿 | `onDetach()` 里没停循环 | 在 `onDetach()` 中 `loop.stop()`（§2.6）；再加 `view.parentProperty()` 兜底 |
| 点了按钮没反应 / 又掉回小游戏 | 场景事件在返回时被重复触发 | 引擎已做抑制；若自建返回逻辑，注意别直接调 `renderScene` |
| 地图改了没生效 | 编辑器没保存 / 看的是别的地图 | 编辑器 `Ctrl+S`；确认 `config.ini` 的 `map.folder` |
| 图片显示为占位方块 | `resources/` 路径不对或文件缺失 | 路径相对**地图根**；确认文件名与大小写 |
| 编译提示找不到 JavaFX | 平台分类器不对 | 换平台加 `-Djavafx.platform=linux` / `mac` |

---

## 9. 明确禁止

1. 提交 `target/`、`*.class`、`plugins/*.jar`、`plugins/classes/`（已在 `.gitignore`，也别 `-f` 强推）。
2. 提交 `docs/私人/` 下的任何内容（检查项、教案、AI 核对记录、成员信息）。
3. 提交录屏、大体积素材、生成中间产物。
4. 直接改 `main` 或 `git push --force` 到 `main`。
5. 未同步就开发（在旧 `main` 上写代码 → PR 一合并就覆盖别人的工作）。
6. 未标注就改公共代码（见 §7.3）。
7. 用 `git add -A` 一把梭提交（极易把素材、脚本、私人文档混进来）。

---

## 10. 相关文档

| 文档 | 位置 |
|---|---|
| 项目总览、需求实现对照 | [`README.md`](README.md) |
| 插件手册（内置插件全家桶 + 外部插件接入） | [`plugins/README.md`](plugins/README.md) |
| 逻辑层（信号/槽）说明 | [`logic/README.md`](logic/README.md) |
| 剧情脚本语法 | README §7 |
| 9 个小游戏玩法说明 | `docs/ds-adventrue/*.md` |
| 更新日志 | [`Changelog.md`](Changelog.md) |
