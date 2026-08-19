# TASK-FIELD-SEMICOLON-001: 用中文分号「；」分隔个人信息字段

## Requirement

规则解析器在填写个人信息时，必须把中文全角分号 `；` 当作与 `，` 一致的字段分隔符。
输入「更新个人信息，地址是上海市浦东新区；公司是星河科技」时：

- 地址必须解析为 `上海市浦东新区`；
- 公司必须解析为 `星河科技`。

## Acceptance criteria

1. `更新个人信息，地址是上海市浦东新区；公司是星河科技` 解析为
   `FillPersonalInfo`，且 `personalInfoFields.address == "上海市浦东新区"`、
   `personalInfoFields.company == "星河科技"`。
2. 先新增聚焦测试并确认为 RED（当前地址会贪婪捕获到
   `上海市浦东新区；公司是星河科技` 而失败），再添加实现。
3. 已有 `，` 分隔行为不变，例如「帮我补全个人资料，我叫李晨曦，29岁」仍按原样解析。
4. 聚焦测试、完整本地单元测试、`assembleDebug` 与 `lint` 全部通过。

## Edge cases

- 段尾分隔符：「地址是上海市浦东新区；」→ 地址仍为 `上海市浦东新区`，无尾部残留。
- 仅中文全角 `；` 作为分隔符；ASCII `;` 不被视为分隔符，保持为字段值的一部分。
- 其它个人信息字段（如电话、职业）被 `；` 分隔时同样在边界截断。
- `；` 后为空段不产生空字段（`hasAnyValue` 逻辑不变）。

## Allowed implementation paths

- `app/src/main/java/com/example/cctest/routing/parser/TextParsingSupport.kt`
- `app/src/test/java/com/example/cctest/routing/RuleBasedIntentParserTest.kt`

最大改动文件数：2。

## Forbidden paths

`.opencode/**`、`automation/**`、`scripts/automation/**`、`opencode.json`、
`AGENTS.md`、`gradle/**`、`gradlew`、`gradlew.bat`、`settings.gradle.kts`、
`build.gradle.kts`、`app/build.gradle.kts`，以及
`app/src/main/java` 下除 `routing/parser/TextParsingSupport.kt` 外的任何文件
（覆盖 UI、导航、远程/LLM 解析、CompositeIntentParser 等）。

## Non-goals

- 不改动 UI、导航、Gradle、依赖或远程/LLM 解析。
- 不把 ASCII `;` 或其它标点加入分隔符集合。
- 不调整 `RuleBasedIntentParser` 的意图分类顺序或 confidence。
- 不为其它意图分支新增测试。

## Verification

聚焦测试类：
`com.example.cctest.routing.RuleBasedIntentParserTest`

Commands:

```text
./gradlew testDebugUnitTest --tests com.example.cctest.routing.RuleBasedIntentParserTest
./gradlew testDebugUnitTest assembleDebug lint
```

设备/模拟器测试：不要求。纯 JVM 规则解析变更，无设备相关行为。

## Approval

Approved on 2026-08-19 by the interactive `方案确认` selection
「批准方案，生成计划和任务合同。」in the scheduled-quality workflow.
