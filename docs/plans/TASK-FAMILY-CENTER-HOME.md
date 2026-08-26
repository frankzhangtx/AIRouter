# 规则解析器识别「进入家庭中心」为家居看板 HOME 标签 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `RuleBasedIntentParser` 把「进入家庭中心」识别为打开家居看板的 `HOME` 标签。

**Architecture:** 在现有规则解析器的看板意图判定函数 `looksLikeDashboard` 中加入「家庭中心」关键字，使含「家庭中心」的输入进入 `OpenHouseDashboard` 分支；标签提取复用 `TextParsingSupport.extractDashboardTab` 的既有「家庭」→ `HOME` 规则（`extractDashboardTab("进入家庭中心")` 已返回 `HOME`），不改动其它解析分支、网关或 UI。

**Tech Stack:** Kotlin（Android，Gradle Kotlin DSL）、JUnit4、kotlinx-coroutines。

## Global Constraints

- 只允许修改 2 个文件：
  - `app/src/main/java/com/example/cctest/routing/parser/RuleBasedIntentParser.kt`
  - `app/src/test/java/com/example/cctest/routing/RuleBasedIntentParserTest.kt`
- 禁止修改 `.opencode/**`、`.automation-plugin/**`、`automation/**`、`scripts/automation/**`、`opencode.json`、`opencode.jsonc`、`AGENTS.md`、`gradle/**`、`gradlew`、`gradlew.bat`、`settings.gradle(.kts)`、`build.gradle(.kts)`、`**/build.gradle(.kts)`、`app/build.gradle.kts`。
- 不改动 `TextParsingSupport.kt`、LLM/远程网关（`RemoteIntentParsingGateway.kt`）、`FakeIntentParsingGateway.kt`、`strings.xml`、`destinations.json`、`journey_graph.json` 及任何导航逻辑。
- 沿用仓库现有子串关键字匹配风格（如「看板」「列表」「记录」「工作台」）；不引入正则白名单或精确短语列表。
- 用户可见文本必须来自 `res/values/strings.xml`；本任务不新增任何用户可见文本。

## 可观察行为

- **当前行为：** `RuleBasedIntentParser.parse("进入家庭中心")` 返回 `ParseOutcome.Success` + `UserGoal.Unknown`（置信度 0.24），因为 `looksLikeDashboard`（`RuleBasedIntentParser.kt:87-91`）只匹配「看板」「dashboard」「工作台」；该输入也不命中 form/list/detail 分支（无结构化字段、无「第N」、无「信息/资料/详情」后缀），落入 else 分支。
- **目标行为：** `RuleBasedIntentParser.parse("进入家庭中心")` 返回 `ParseOutcome.Success`，`userGoal == UserGoal.OpenHouseDashboard`，`slots.dashboardTab == HouseDashboardTab.HOME`。

## 验收条件与边界

1. 输入「进入家庭中心」→ `Success`，`userGoal == OpenHouseDashboard`，`dashboardTab == HOME`。
2. 回归：输入「打开 Work 看板」仍 → `WORK`；输入「打开工作台」仍 → `WORK`；输入「打开家居看板」仍 → `HOME`；list/form 既有用例全部保持通过。
3. 聚焦测试先 RED（当前为 `Unknown`）后 GREEN。
4. 边界：无动词的「家庭中心」单独出现同样命中 `HOME`（与「工作台」单独出现一致）；「打开家庭中心看板」仍 → `HOME`（`extractDashboardTab` 无「工作」命中，走「家庭」→ `HOME`）。
5. 标签优先级不变：含「家庭中心」又含「工作/工作台」的文本维持既有 `WORK` 优先语义（`extractDashboardTab` 先查「工作」）。
6. 关键字别名语义：人造输入如「进入家庭中心查看李晨曦的信息」会优先归类为看板 —— 与 `TASK-WORKTAB-ALIAS` 记录的「打开工作台灯」→ `WORK` 同一策略（`when` 中 dashboard 分支最先判定），属可接受的关键字别名语义。
7. 空输入仍返回 `InvalidSchema` 失败；置信度 0.96 与 `ParserMetadata` 保持不变。

## 聚焦测试与设备策略

- 聚焦测试（RED 证据来源）：`com.example.cctest.routing.RuleBasedIntentParserTest`
- 测试策略：required —— 新增 RED→GREEN 单测 + 既有 WORK/HOME/表单回归断言。
- 设备测试：不需要。改动位于纯 JVM 规则解析器，本地单测可完整覆盖；既有设备用例不改动。

## 非目标

- 不改动 `TextParsingSupport.extractDashboardTab`、LLM/远程网关提示词或 `FakeIntentParsingGateway`。
- 不更新 UI 提示文案（`strings.xml`）、`destinations.json`、`journey_graph.json` 或导航逻辑。
- 不新增 WORK 标签别名。
- 不扩展 androidTest 设备用例。

---

### 任务 1：规则解析器识别「进入家庭中心」为 HOME 标签

**文件:**
- Modify: `app/src/main/java/com/example/cctest/routing/parser/RuleBasedIntentParser.kt:87-91`（`looksLikeDashboard`）
- Test: `app/src/test/java/com/example/cctest/routing/RuleBasedIntentParserTest.kt`

**接口:**
- Consumes: `RuleBasedIntentParser.parse(ParseRequest)`，`UserGoal.OpenHouseDashboard`，`HouseDashboardTab.HOME`，`ParseOutcome.Success`。
- Produces: `looksLikeDashboard("进入家庭中心") == true`；`parse("进入家庭中心")` 返回 `OpenHouseDashboard` + `dashboardTab == HOME`；既有 dashboard/list/form 输入行为不变。

- [ ] **步骤 1：编写失败测试**

在 `RuleBasedIntentParserTest` 中新增以下测试（`HouseDashboardTab`、`UserGoal`、`ParseOutcome`、`ParseRequest` 均已导入）：

```kotlin
@Test
fun parseDashboardIntent_familyCenter_mapsToHomeTab() = runBlocking {
    val outcome = parser.parse(ParseRequest(inputText = "进入家庭中心", entrySource = "test"))
    assertTrue(outcome is ParseOutcome.Success)
    val result = (outcome as ParseOutcome.Success).result
    assertEquals(UserGoal.OpenHouseDashboard, result.userGoal)
    assertEquals(HouseDashboardTab.HOME, result.slots.dashboardTab)
}
```

- [ ] **步骤 2：运行测试确认 RED**

Run: `./gradlew testDebugUnitTest --tests "com.example.cctest.routing.RuleBasedIntentParserTest"`

Expected: `parseDashboardIntent_familyCenter_mapsToHomeTab` FAIL（当前返回 `Unknown`）；既有 `parseDashboardIntent_extractsWorkTab`、`parseDashboardIntent_openWorkbench_mapsToWorkTab`、`parseDashboardIntent_houseDashboard_keepsHomeTab` 与 list/form 用例 PASS。

- [ ] **步骤 3：最小实现**

修改 `RuleBasedIntentParser.kt` 的 `looksLikeDashboard`：

```kotlin
private fun looksLikeDashboard(text: String): Boolean {
    return text.contains("看板") ||
        text.contains("dashboard", ignoreCase = true) ||
        text.contains("工作台") ||
        text.contains("家庭中心")
}
```

- [ ] **步骤 4：运行测试确认 GREEN**

Run: `./gradlew testDebugUnitTest --tests "com.example.cctest.routing.RuleBasedIntentParserTest"`

Expected: 全部 PASS（含既有回归用例与新增 `parseDashboardIntent_familyCenter_mapsToHomeTab`）。

- [ ] **步骤 5：全量验证**

Run: `./gradlew testDebugUnitTest assembleDebug lint`

Expected: 全部通过。提交与本地集成由 orchestrator 的确定性集成脚本在人工验收后统一完成；禁止手工 `git` 操作。

## 方案批准记录

- 日期: 2026-08-26
- 批准方式: 用户在 OpenCode 单选 `方案确认` 问题中选择「批准方案，生成计划和任务合同。」（`multiple: false, custom: false` 的选项选择，非聊天文本）。
