# logic —— 地图工程师“逻辑层”目录

引擎负责渲染，你只写逻辑。把 Java 类放进对应的 `classes/`（或打成 jar 放本目录），
在 `logic.ini` 里登记 ID，然后在编辑器里给节点的**槽**写 `call` 动作即可。

## 目录约定（两处都会扫描，地图内同名优先）
```
工程根/logic/           ← 全局逻辑（所有地图可用）
├── logic.ini           ← ID = 全限定类名
├── classes/            ← 编译产物（按包结构）
└── *.jar               ← 也可以放 jar
地图根/logic/           ← 这张地图专属的逻辑（同样规则）
```

## 逻辑类两种写法
```java
// 写法一：实现接口（推荐）
public class MyLogic implements com.studio.flow.LogicHandler {
    public void onSignal(FlowContext ctx, SignalEvent ev) {
        ctx.setVar("金币", ctx.intVar("金币", 0) + 10);      // 变量（随存档保存）
        ctx.setText("金币文本", "金币: " + ctx.var("金币", "0")); // 立即重绘
        ctx.setStyle("宝箱", "-fx-opacity: 0.3;");            // 改样式并即时渲染
        ctx.emit("提示", "获得金币", Map.of("数量", 10));      // 向其它节点发信号
        ctx.toast("获得 10 金币！");
    }
}

// 写法二：普通方法（槽里用 ID#方法名 调用）
public class MyLogic {
    public void openChest(FlowContext ctx, SignalEvent ev) { ... }
}
```

## 槽可用的内置动作（无需写代码，编辑器里配置即可）
| 动作 | 写法示例 | 说明 |
|---|---|---|
| set | `点击 \| set \| 灯 \| style \| value=-fx-opacity:0.3;` | 改属性并即时重绘（valueVar=变量名 可从变量取值） |
| toggle | `点击 \| toggle \| 提示 \| visible` | 布尔切换 |
| emit | `点击 \| emit \| 提示 \| 亮灯` | 向目标节点/场景发信号（可带参数） |
| goto | `点击 \| goto \| \| 森林` | 跳场景 |
| save / load | `点击 \| save \| \| slot2` | 读写 saves/ 槽位 |
| call | `点击 \| call \| \| mylogic` | 转交逻辑层（ID 或 类名#方法名） |
| log | `点击 \| log \| \| 你好` | 日志 + 顶部提示 |

## 信号定义
- 节点信号：`名称 | mouse | click`（或 `release`）、`名称 | key | F | press`
- 场景信号：在 [场景名] 段写 `signal = 快捷键F | key | F | press`，
  地图级全局监听器收到按键后按信号名分发给场景槽与各节点槽。

## 编译示例
```bat
javac -encoding UTF-8 -cp ..\target\classes -d classes com\example\MyLogic.java
```
然后将 `logic.ini` 里登记 `mylogic = com.example.MyLogic`，重启播放器即可。
