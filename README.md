# ds-adventure（Galgame + 小游戏融合）

JavaFX 实训小游戏项目。以**文字剧情（Galgame）为主框架**，剧情推进到关键节点时，以"特殊演出"方式切入小游戏；小游戏保留独立玩法，玩完回到剧情继续。

## 项目简介

- **主框架**：Galgame 文字剧情引擎（剧情脚本驱动、场景切换、选项分支、存档）
- **特殊演出**：剧情关键节点嵌入小游戏，小游戏以独立场景运行，结束后返回剧情
- **小游戏池**（9 个，玩法说明见 `docs/ds-adventrue/`）：

| 小游戏 | 玩法要点 |
|--------|----------|
| 贪吃蛇 | 方向键控制，吃豆变长，撞墙/撞自己死亡 |
| 飞机大战 | 射击敌机得分，可捡道具，关卡递增难度 |
| 2048 | 数字合并，合成 2048 |
| 打砖块 | 挡板反弹小球，清空砖块过关 |
| 记忆翻牌 | 翻牌配对，考验记忆 |
| 连连看 | 相同图案连线消除 |
| 扫雷 | 数字推理，避开地雷 |
| 推箱子 | 推动箱子到目标点 |
| 五子棋 | 五子连珠获胜 |

- **当前阶段**：项目脚手架 + 沙盒窗口（可运行验证），剧情框架与小游戏逐个开发中

## 技术栈与版本

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 21（本机 `C:\Program Files\Java\jdk-21`） | 编译目标为 17，JDK 17+ 均可运行 |
| 编译目标 | Java 17（`maven.compiler.release=17`） | 对齐检查项要求 |
| Maven | 3.9.9（Wrapper 自动下载） | 无需全局安装 |
| Maven Wrapper | 3.3.2 | `mvnw.cmd` 启动 |
| JavaFX | 21.0.12 | 见下方模块表 |
| JUnit | 5.11.4（Jupiter） | 单测 |
| javafx-maven-plugin | 0.0.8 | `mvnw.cmd javafx:run` 启动窗口 |
| maven-surefire-plugin | 3.2.5 | 跑 JUnit 5 测试 |

### JavaFX 模块（pom.xml 中声明）

| 模块 | 版本 | 用途 |
|------|------|------|
| javafx-controls | 21.0.12 | 窗口、Scene、控件 |
| javafx-fxml | 21.0.12 | 剧情界面 / 菜单 FXML |
| javafx-media | 21.0.12 | BGM / 音效 |

> 版本兼容说明：JavaFX 21 要求 JDK 17+，本机 JDK 21 编译 `--release 17` 产物可在 JDK 17/21 上运行，满足检查项"JDK 17"要求。

## 环境要求

- JDK 17 或更高（推荐 21，本机已装）
- 无需全局安装 Maven / Gradle（项目自带 Maven Wrapper）
- Windows：直接使用 `mvnw.cmd`；macOS/Linux：使用 `./mvnw`

## 运行

在项目根目录 `ds-adventure/` 下执行：

```bat
:: 首次运行会自动下载 Maven 3.9.9 与全部依赖（需联网）

mvnw.cmd clean compile   :: 编译（检查项：mvn clean compile 通过）
mvnw.cmd test            :: 运行单元测试
mvnw.cmd javafx:run      :: 启动游戏窗口
```

或在 IntelliJ IDEA 中：`File → Open` 选择 `ds-adventure` 文件夹，等待 Maven 导入完成，运行 `GameApplication` 主类。

### 当前沙盒操作

| 按键 | 功能 |
|------|------|
| WASD / 方向键 | 移动方块 |
| 空格 | 暂停 / 继续 |
| R | 重置位置 |

## 目录结构（按职责分包）

```
ds-adventure/
├── pom.xml                        Maven 构建配置（版本集中管理）
├── mvnw.cmd / mvnw                Maven Wrapper 启动脚本
├── .mvn/wrapper/                  Wrapper 配置与 jar
├── src/
│   ├── main/
│   │   ├── java/com/lab/galgame/
│   │   │   ├── GameApplication.java   入口（Stage/Scene 装配）
│   │   │   ├── config/                配置（窗口尺寸、玩家参数）
│   │   │   ├── controller/            游戏循环、剧情/小游戏调度
│   │   │   ├── model/                 游戏状态、剧情数据、存档
│   │   │   ├── view/                  渲染（Canvas）、FXML 界面
│   │   │   └── util/                  输入抽象等工具
│   │   └── resources/
│   │       ├── css/                   样式表
│   │       └── assets/                精灵图 / 音效（sprites/、sounds/）
│   └── test/java/com/lab/galgame/     单元测试
└── docs/
    ├── ds-adventrue/                 9 个小游戏玩法说明
    └── 私人/                          检查项、教案、AI 核对记录（不入版本库）
```

## 开发计划（规划）

1. 剧情框架：剧情脚本格式、场景切换、选项分支
2. 小游戏接入：每个小游戏独立 controller + view，通过"特殊演出"调度切入/返回
3. 存档与排行榜
4. 打包发布（jpackage）

## AI 使用与核对说明

- 本项目脚手架由 AI 辅助生成（初版 Gradle → 按检查项改为 Maven）。
- 人工核对内容：
  - JDK 编译目标 17（`maven.compiler.release=17`）
  - JavaFX 21.0.12 与 JDK 17+ 兼容
  - 依赖无多余项（仅 controls/fxml/media + JUnit）
  - 包结构按 controller/model/view/config/util 重排
- 核对记录见 `docs/私人/XXX项目-AI核对-9.7.docx`（本地，不入版本库）。
