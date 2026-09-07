# ds-adventure（Galgame + 小游戏融合）

JavaFX 实训小游戏项目：**文字剧情（Galgame）主框架 + 小游戏特殊演出**。
剧情推进到关键节点时，以"特殊演出"方式切入小游戏（贪吃蛇、飞机大战、2048、打砖块、记忆翻牌、连连看、扫雷、推箱子、五子棋），小游戏保留独立玩法，玩完回到剧情。

## 环境

- JDK 17+（本机 JDK 21 即可，编译目标为 17）
- 不需要全局安装 Maven：项目自带 Maven Wrapper（`mvnw.cmd`）

## 依赖（pom.xml）

| 模块 | 版本 | 用途 |
|------|------|------|
| javafx-controls | 21.0.12 | 窗口、Scene、控件 |
| javafx-fxml | 21.0.12 | 剧情界面 / 菜单 FXML |
| javafx-media | 21.0.12 | BGM / 音效 |
| JUnit 5 | 5.11.4 | 纯逻辑单测 |

## 运行

```bat
mvnw.cmd clean compile   :: 编译（检查项：mvn clean compile 通过）
mvnw.cmd test            :: 跑单测
mvnw.cmd javafx:run      :: 启动游戏窗口
```

或在 IntelliJ 打开本文件夹，等 Maven 导入完成后点运行。

操作（沙盒阶段）：WASD/方向键移动方块，空格暂停，R 重置。

## 目录结构（按职责分包）

```
src/main/java/com/lab/galgame/
  GameApplication.java   入口
  config/                配置（窗口、参数）
  controller/            游戏循环、剧情/小游戏调度
  model/                 游戏状态、剧情数据、存档
  view/                  渲染、FXML 界面
  util/                  输入抽象等工具
src/main/resources/
  css/                   样式
  assets/                精灵图 / 音效
src/test/java/           单测
docs/
  ds-adventrue/          9 个小游戏玩法说明
  私人/                  检查项、教案、AI 核对记录
```

## AI 使用与核对说明

- 本项目脚手架由 AI 辅助生成（Gradle 版 → 按检查项改为 Maven 版）。
- 人工核对内容：JDK 编译目标 17、JavaFX 21.0.12 与 JDK 17+ 兼容、依赖无多余项（仅 controls/fxml/media + JUnit）、包结构按 controller/model/view/config/util 重排。
- 核对记录见 `docs/私人/XXX项目-AI核对-9.7.docx`。
