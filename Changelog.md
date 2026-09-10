# 更新日志 (Changelog)
## [v1.0-adapt]（PR#1 吸收改造）

### Changed
- 按《需求规格说明书 v1.1》与实训检查项对齐：编译目标 Java 21 → 17，JavaFX 21.0.5 → 17.0.20（LTS 17）
- 回归 JUnit 5 + surefire（P0-8：mvn test 通过），新增 ScriptParserTest（解析/往返/未知键透传/告警 4 项）
- 恢复 Maven Wrapper（mvnw.cmd）、docs/ds-adventrue 玩法文档、assets 目录；恢复 .gitignore 的 docs/私人/ 忽略行
- artifactId 对齐仓库名：ds-adventure

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
