# TASK-POSITION-DETAIL-OPEN-001: Open personal info detail after locating a unique list record by position

## Requirement

The rule-based intent parser and the list target planner must support two
phrasings that first locate the unique record at position N in the personal-info
list and then automatically open its detail page:

- `查看第12条记录并打开详情`
- `查看第十二条记录的详情`

Ordinary `查看第12条记录` must still only locate (focus) the record in the list
without opening the detail page. Out-of-bounds positions or non-unique matches
must never auto-open the detail page.

## Current behavior

- `RuleBasedIntentParser` sets `autoOpenDetail` only from
  `input.contains("打开详情") || input.contains("直接看详情")`
  (`RuleBasedIntentParser.kt:44`). So `查看第十二条记录的详情` parses with
  `autoOpenDetail = false`, and `查看第12条记录并打开详情` parses with
  `autoOpenDetail = true`.
- `extractPosition` already supports both Arabic and Chinese numerals:
  `第12条` → 12 and `第十二条` → 12 via `parseChineseNumber`
  (`TextParsingSupport.kt:36-45,130-147`).
- `TargetPlanner.planListTarget` drops `result.slots.autoOpenDetail`: every
  branch hardcodes `autoOpenDetail = false` when building the
  `ListFocusRequest` (`TargetPlanner.kt:78,82,89`). So even when the parser
  says `autoOpenDetail = true`, the list page never receives it and detail is
  never opened from a position lookup.
- Safety nets already exist and are unchanged by this task:
  `RecordResolution.toListFocusRequest` computes
  `autoOpenDetail = autoOpenDetail && isUnique`
  (`PersonalInfoRecordResolver.kt:149`), and
  `PersonalInfoListFragment.applyFocusRequest` only opens detail when
  `focusRequest.autoOpenDetail && resolution.isUnique`
  (`PersonalInfoListFragment.kt:100-102`).
- The repository contains 30 mock records; position 12 is `record-12`
  (`PersonalInfoRepository.kt:13-20`).

## Desired observable behavior

1. `查看第12条记录并打开详情` parses to `BrowsePersonalInfoList` with
   `slots.listPosition == 12` and `slots.autoOpenDetail == true`; the list
   target planner produces a focus request with `autoOpenDetail = true` that
   locates the unique record and opens its detail page.
2. `查看第十二条记录的详情` parses to `BrowsePersonalInfoList` with
   `slots.listPosition == 12` and `slots.autoOpenDetail == true`; same planner
   behavior as above.
3. `查看第12条记录` still parses to `BrowsePersonalInfoList` with
   `slots.listPosition == 12` and `slots.autoOpenDetail == false`; the list
   target planner locates the record without opening detail.
4. Out-of-bounds positions (for example `查看第99条记录并打开详情`) and
   non-unique matches never produce a focus request with
   `autoOpenDetail = true`.

## Acceptance criteria

1. `查看第12条记录并打开详情` parses to `BrowsePersonalInfoList` with
   `listPosition == 12` and `autoOpenDetail == true`.
2. `查看第十二条记录的详情` parses to `BrowsePersonalInfoList` with
   `listPosition == 12` and `autoOpenDetail == true`.
3. `查看第12条记录` parses to `BrowsePersonalInfoList` with
   `listPosition == 12` and `autoOpenDetail == false`.
4. `planListTarget` with a unique position match and
   `slots.autoOpenDetail == true` produces `listFocusRequest.autoOpenDetail == true`.
5. `planListTarget` with out-of-range `listPosition = 99` and
   `slots.autoOpenDetail == true` produces `listFocusRequest.autoOpenDetail == false`.
6. `toListFocusRequest(autoOpenDetail = true)` on an ambiguous resolution
   (`candidates.size > 1`) produces `autoOpenDetail == false`.
7. A focused test is added and confirmed RED before implementation.
8. The focused tests, the complete local unit-test suite, the debug APK build,
   and Android lint all pass.

## Edge cases

- The `…的详情` phrasing is recognized only when the input has both a position
  locator (`extractPosition != null`) and the keyword `详情`; a plain
  `查看第12条记录` (no `详情`) is never auto-opened.
- `查看第5条的详情` (no `记录` character) is also recognized because the
  position locator exists; this matches the same open-detail semantics.
- An out-of-range position stays in the `result.slots.listPosition != null`
  branch of `planListTarget`, which keeps `autoOpenDetail = false`; the list
  page additionally guards with `resolution.isUnique`.
- A non-unique match flows through `toListFocusRequest`, whose
  `autoOpenDetail && isUnique` forces `autoOpenDetail = false`.
- Chinese numerals are already extracted by `extractPosition` /
  `parseChineseNumber`; this task does not touch them.
- `extractLookupName` may capture position text such as `第十二条` as a person
  name (`TextParsingSupport.kt:74`, second pattern), but `resolve` in
  `PersonalInfoRecordResolver` checks phone, then position, then name, so the
  position match wins; this does not affect any acceptance behavior and is a
  declared non-goal.
- Existing phrasings stay unchanged: `查看手机号尾号1001的详情` keeps routing to
  `OpenPersonalInfoDetail` (no position locator, so list/detail routing is
  unaffected), `更新个人资料，电话13800001001` keeps parsing to
  `FillPersonalInfo`, and `查看张雨桐的信息` keeps its instrumented detail flow.

## Allowed implementation paths

- `app/src/main/java/com/example/cctest/routing/parser/RuleBasedIntentParser.kt`
- `app/src/main/java/com/example/cctest/routing/workflow/TargetPlanner.kt`
- `app/src/test/java/com/example/cctest/routing/RuleBasedIntentParserTest.kt`
- `app/src/test/java/com/example/cctest/routing/TargetPlannerTest.kt` (new)

Implementation guidance:

- In `RuleBasedIntentParser.kt:44`, extend `autoOpenDetail` to:
  `input.contains("打开详情") || input.contains("直接看详情") ||
  (extractPosition(input) != null && input.contains("详情"))`.
- In `TargetPlanner.kt:78`, change the `resolution.isUnique` branch of
  `planListTarget` to
  `resolution.toListFocusRequest(autoOpenDetail = result.slots.autoOpenDetail)`.
  The remaining branches (out-of-range position, ambiguous, no locator) keep
  `autoOpenDetail = false` exactly as today.
- In `RuleBasedIntentParserTest.kt`, add tests for: `查看第12条记录并打开详情`
  (`listPosition == 12`, `autoOpenDetail == true`), `查看第十二条记录的详情`
  (`listPosition == 12`, `autoOpenDetail == true`), and `查看第12条记录`
  (`autoOpenDetail == false`).
- Create `TargetPlannerTest.kt` with the same fixture style as
  `RoutePlannerTest` (real `DestinationContractRegistry`,
  `PersonalInfoRecordResolver(PersonalInfoRepository())`, `WorkflowRegistry`,
  `WorkflowEngine`) and assert `planListTarget`-derived
  `TargetPlan.listFocusRequest.autoOpenDetail` for: unique position with
  `slots.autoOpenDetail = true` (true), out-of-range position 99 with
  `slots.autoOpenDetail = true` (false), and plain position 12 with
  `slots.autoOpenDetail = false` (false).

## Forbidden paths

- `.opencode/**`, `automation/**`, `scripts/automation/**`, `opencode.json`,
  `AGENTS.md`, `gradle/**`, `gradlew`, `gradlew.bat`, `settings.gradle.kts`,
  `build.gradle.kts`, `app/build.gradle.kts`.
- Any file not listed under Allowed implementation paths, including
  `TextParsingSupport.kt`, `PersonalInfoRecordResolver.kt`,
  `PersonalInfoListFragment.kt`, `RemoteIntentParsingGateway.kt`,
  `FakeIntentParsingGateway.kt`, `CompositeIntentParser.kt`,
  `SlotNormalizer.kt`, `ParseResultValidator.kt`, `RoutePlanner.kt`,
  `JourneyPlanner.kt`, navigation sources, UI/resources, Gradle files, other
  test files, and all `androidTest` files.

## Non-goals

- Do not change `extractPosition`, `parseChineseNumber`, or
  `TextParsingSupport.kt`; Chinese numeral extraction already works.
- Do not change `PersonalInfoRecordResolver`, `PersonalInfoListFragment`,
  list UI copy, navigation, Gradle, dependencies, or resource strings.
- Do not change `RemoteIntentParsingGateway`, `FakeIntentParsingGateway`,
  `CompositeIntentParser`, `SlotNormalizer`, `ParseResultValidator`, or the
  `OpenPersonalInfoDetail` flow.
- Do not add device/emulator tests; the existing instrumented test
  `inputListIntent_focusesRequestedRecord` (plain positioning, no auto-open)
  is unchanged and out of scope.
- Do not fix `extractLookupName` capturing position text (for example
  `第十二条`) as a person name; the resolver's position-first ordering keeps
  this task's behavior correct.

## Verification

Focused test classes:

- `com.example.cctest.routing.RuleBasedIntentParserTest`
- `com.example.cctest.routing.TargetPlannerTest`

Commands:

```text
./gradlew testDebugUnitTest --tests "com.example.cctest.routing.RuleBasedIntentParserTest" --tests "com.example.cctest.routing.TargetPlannerTest"
./gradlew testDebugUnitTest assembleDebug lint
```

## Device/emulator requirements

None. The change is pure JVM-testable parser and planner logic; the list page
already consumes `autoOpenDetail` with its own uniqueness guard
(`PersonalInfoListFragment.kt:100-102`).

## Approval

Approved on 2026-08-18 through the repository user's interactive request to
define and implement one small programming task based on the current project.
The user's exact proposal-approval phrase was
`批准方案，生成计划和任务合同。`.
