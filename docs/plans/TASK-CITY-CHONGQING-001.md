# TASK-CITY-CHONGQING-001：为列表意图的城市识别增加「重庆」

## 需求

规则意图解析器 `RuleBasedIntentParser` 在列表分支调用 `extractCity(input)`（定义于
`TextParsingSupport.kt`）来填充 `slots.city`。当前 `knownCities` 名单为
`["上海", "北京", "深圳", "杭州", "成都", "南京", "武汉", "苏州", "厦门", "广州"]`，
其中没有「重庆」。

因此输入 `查看重庆的记录列表` 虽然会命中列表意图（文本含「记录」/「列表」），
`userGoal == BrowsePersonalInfoList`，但 `extractCity` 返回 `null`，导致
`slots.city == null`。

本任务通过 TDD 增加「重庆」的城市识别支持。

## 当前行为

- 输入 `查看重庆的记录列表` → `ParseOutcome.Success`
- `userGoal == BrowsePersonalInfoList`
- `slots.city == null`（因为「重庆」不在 `knownCities` 中）

## 期望可观测行为

- 输入 `查看重庆的记录列表` → `ParseOutcome.Success`
- `userGoal == BrowsePersonalInfoList`
- `slots.city == "重庆"`

## 验收标准

1. `查看重庆的记录列表` 解析为 `BrowsePersonalInfoList`，且 `slots.city == "重庆"`。
2. 已有城市行为保持不变：`查看上海的记录列表` 解析后 `slots.city == "上海"`（回归断言）。
3. 先新增聚焦测试并确认为 RED（因 `slots.city == null` 而失败），再添加实现代码。
4. 聚焦测试、完整本地单元测试套件、`assembleDebug` 与 `lint` 全部通过。

## 边界情况

- 仅要求精确中文城市名「重庆」，不扩展区/县、别名、拼音或模糊搜索。
- `extractCity` 继续使用 `contains` 匹配，因此「重庆市」等仍可命中（与其它城市一致的既有行为，不改动）。
- 「重庆」与现有任何城市名不存在包含/子串冲突，列表顺序无关紧要。
- 「重庆」加入共享的 `knownCities` 后会同时修复详情（detail）分支的城市抽取，但本任务不对此分支单独测试（见非目标）。

## 允许的实现与测试路径

- `app/src/main/java/com/example/cctest/routing/parser/TextParsingSupport.kt`：在 `knownCities` 中增加 `"重庆"`。
- `app/src/test/java/com/example/cctest/routing/RuleBasedIntentParserTest.kt`：新增聚焦测试。

## 禁止修改的路径

- `.opencode/**`、`automation/**`、`scripts/automation/**`、`opencode.json`、`AGENTS.md`
- `gradle/**`、`gradlew`、`gradlew.bat`、`settings.gradle.kts`、`build.gradle.kts`、`app/build.gradle.kts`

## 非目标

- 不改动 UI、导航、Gradle、依赖或远程/LLM 解析。
- 不新增其它城市、区县、别名或模糊城市匹配。
- 不为详情（detail）意图分支单独编写测试。
- 不调整 `RuleBasedIntentParser` 的意图分类顺序。

## 验证

聚焦测试类（提供 RED 证据）：

```text
com.example.cctest.routing.RuleBasedIntentParserTest
```

命令：

```text
./gradlew testDebugUnitTest --tests com.example.cctest.routing.RuleBasedIntentParserTest
./gradlew testDebugUnitTest assembleDebug lint
```

## 设备/模拟器要求

无。本任务仅需本地 JVM 单元测试（`./gradlew testDebugUnitTest`），不需要设备或模拟器。

## 批准

已于 2026-08-13 经仓库用户在交互式会话中明确批准本任务的范围与验收标准；描述语言采用中文。
