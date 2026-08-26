# 规则解析器识别「打开工作台」为家居看板 WORK 标签 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `RuleBasedIntentParser` 把「打开工作台」识别为打开家居看板的 `WORK` 标签。

**Architecture:** 在现有规则解析器的看板意图判定函数 `looksLikeDashboard` 中加入「工作台」关键字，使含「工作台」的输入进入 `OpenHouseDashboard` 分支；标签提取复用 `TextParsingSupport.extractDashboardTab` 的既有「工作」→ `WORK` 规则，不改动其它解析分支、网关或 UI。

**Tech Stack:** Kotlin（Android，Gradle Kotlin DSL）、JUnit4、kotlinx-coroutines。

## Global Constraints

- 只允许修改 2 个文件：
  - `app/src/main/java/com/example/cctest/routing/parser/RuleBasedIntentParser.kt`
  - `app/src/test/java/com/example/cctest/routing/RuleBasedIntentParserTest.kt`
- 禁止修改 `.opencode/**`、`.automation-plugin/**`、`automation/**`、`scripts/automation/**`、`opencode.json`、`opencode.jsonc`、`AGENTS.md`、`gradle/**`、`gradlew`、`gradlew.bat`、`settings.gradle(.kts)`、`build.gradle(.kts)`、`**/build.gradle(.kts)`、`app/build.gradle.kts`。
- 不改动 `TextParsingSupport.kt`、LLM/远程网关（`RemoteIntentParsingGateway.kt`）、`FakeIntentParsingGateway.kt`、`strings.xml`、`destinations.json`、`journey_graph.json` 及任何导航逻辑。
- 沿用仓库现有子串关键字匹配风格（如「看板」「列表」「记录」）；不引入正则白名单或精确短语列表。
- 用户可见文本必须来自 `res/values/strings.xml`；本任务不新增任何用户可见文本。

## 可观察行为

- **当前行为：** `RuleBasedIntentParser.parse("打开工作台")` 返回 `UserGoal.Unknown`（置信度 0.24），因为 `looksLikeDashboard`（`RuleBasedIntentParser.kt:87-89`）只匹配「看板」或「dashboard」。
- **目标行为：** `RuleBasedIntentParser.parse("打开工作台")` 返回 `ParseOutcome.Success`，`userGoal == UserGoal.OpenHouseDashboard`，`slots.dashboardTab == HouseDashboardTab.WORK`。

## 验收条件与边界

1. 输入「打开工作台」→ `Success`，`userGoal == OpenHouseDashboard`，`dashboardTab == WORK`。
2. 回归：输入「打开 Work 看板」仍 → `WORK`；输入「打开家居看板」仍 → `HOME`（新增 HOME 断言锁定优先级）。
3. 聚焦测试先 RED（当前为 `Unknown`）后 GREEN。
4. 边界：「工作台」单独出现（无动词）同样命中 `WORK`；「打开工作台看板」仍 → `WORK`（现状不变）。
5. 极端人造输入如「打开工作台灯」会命中 `WORK` —— 与现有「看板」关键字匹配风格一致，属可接受的关键字别名语义。
6. 分支优先级不变：`when` 中 dashboard 分支仍最先判断；含「家居」+「工作」的文本维持既有 `WORK` 优先语义。

## 聚焦测试与设备策略

- 聚焦测试（RED 证据来源）：`com.example.cctest.routing.RuleBasedIntentParserTest`
- 测试策略：required —— 新增 RED→GREEN 单测 + WORK/HOME 回归断言。
- 设备测试：不需要。改动位于纯 JVM 规则解析器，本地单测可完整覆盖；既有 `IntelligentRoutingFlowTest` 设备用例不改动。

## 非目标

- 不改动 `TextParsingSupport.extractDashboardTab`、LLM/远程网关提示词或 `FakeIntentParsingGateway`。
- 不更新 UI 提示文案（`strings.xml`）、`destinations.json`、`journey_graph.json` 或导航逻辑。
- 不新增 HOME 标签别名。
- 不扩展 androidTest 设备用例。

---

### 任务 1：规则解析器识别「打开工作台」为 WORK 标签

**文件:**
- Modify: `app/src/main/java/com/example/cctest/routing/parser/RuleBasedIntentParser.kt:87-89`（`looksLikeDashboard`）
- Test: `app/src/test/java/com/example/cctest/routing/RuleBasedIntentParserTest.kt`

**接口:**
- Consumes: `RuleBasedIntentParser.parse(ParseRequest)`，`UserGoal.OpenHouseDashboard`，`HouseDashboardTab.WORK/HOME`，`ParseOutcome.Success`。
- Produces: `looksLikeDashboard("打开工作台") == true`；`parse("打开工作台")` 返回 `OpenHouseDashboard` + `dashboardTab == WORK`；`parse("打开家居看板")` 返回 `HOME` 不变。

- [ ] **步骤 1：编写失败测试**

在 `RuleBasedIntentParserTest` 中新增以下两个测试（`HouseDashboardTab`、`UserGoal`、`ParseOutcome`、`ParseRequest` 均已导入）：

```kotlin
@Test
fun parseDashboardIntent_openWorkbench_mapsToWorkTab() = runBlocking {
    val outcome = parser.parse(ParseRequest(inputText = "打开工作台", entrySource = "test"))
    assertTrue(outcome is ParseOutcome.Success)
    val result = (outcome as ParseOutcome.Success).result
    assertEquals(UserGoal.OpenHouseDashboard, result.userGoal)
    assertEquals(HouseDashboardTab.WORK, result.slots.dashboardTab)
}

@Test
fun parseDashboardIntent_houseDashboard_keepsHomeTab() = runBlocking {
    val outcome = parser.parse(ParseRequest(inputText = "打开家居看板", entrySource = "test"))
    assertTrue(outcome is ParseOutcome.Success)
    val result = (outcome as ParseOutcome.Success).result
    assertEquals(UserGoal.OpenHouseDashboard, result.userGoal)
    assertEquals(HouseDashboardTab.HOME, result.slots.dashboardTab)
}
```

- [ ] **步骤 2：运行测试确认 RED**

Run: `./gradlew testDebugUnitTest --tests "com.example.cctest.routing.RuleBasedIntentParserTest"`

Expected: `parseDashboardIntent_openWorkbench_mapsToWorkTab` FAIL（当前返回 `Unknown`）；`parseDashboardIntent_houseDashboard_keepsHomeTab` 与既有 `parseDashboardIntent_extractsWorkTab` PASS。

- [ ] **步骤 3：最小实现**

修改 `RuleBasedIntentParser.kt` 的 `looksLikeDashboard`：

```kotlin
private fun looksLikeDashboard(text: String): Boolean {
    return text.contains("看板") ||
        text.contains("dashboard", ignoreCase = true) ||
        text.contains("工作台")
}
```

- [ ] **步骤 4：运行测试确认 GREEN**

Run: `./gradlew testDebugUnitTest --tests "com.example.cctest.routing.RuleBasedIntentParserTest"`

Expected: 全部 PASS（含既有 `parseDashboardIntent_extractsWorkTab` 与新增的 WORK/HOME 断言）。

- [ ] **步骤 5：全量验证**

Run: `./gradlew testDebugUnitTest assembleDebug lint`

Expected: 全部通过。提交与本地集成由 orchestrator 的确定性集成脚本在人工验收后统一完成；禁止手工 `git` 操作。

## 方案批准记录

- 日期: 2026-08-26
- 批准方式: 用户在 OpenCode 单选 `方案确认` 问题中选择「批准方案，生成计划和任务合同。」（`multiple: false, custom: false` 的选项选择，非聊天文本）。
