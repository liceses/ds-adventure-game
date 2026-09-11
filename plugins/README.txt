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
| `plugins.ini`             | 插件注册表：事件ID = 类名                           |
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
    default void onDetach() { }
}
```
params 约定键：`plugin.id`、`map.folder`(File)、`embedded`(Boolean)、`host.stage`、`back.callback`(Runnable)。

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
