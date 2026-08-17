# TASK-PHONE-DETAIL-LOOKUP-001: Open personal info detail by full phone or last four digits

## Requirement

The rule-based intent parser must route a personal-info detail request that is
identified by a phone number to `OpenPersonalInfoDetail`, and the record
resolver must uniquely locate the matching record either by a full 11-digit
mainland mobile number or by its last four digits (尾号).

Examples:

- `查看手机号13800001001的详情` must open 张雨桐 (record-2, phone `13800001001`).
- `查看手机号尾号1001的详情` must open 张雨桐 by the last four digits `1001`.
- A non-existent tail such as `查看手机号尾号9999的详情` must fall back to the
  personal-info list with the existing no-match prompt.
- `更新个人资料，电话13800001001` must still enter the personal-info form workflow.

## Current behavior

- `RuleBasedIntentParser.looksLikeForm` returns `true` whenever any structured
  field is extractable, even without a form keyword. Because `extractPhone`
  matches the 11-digit number in `查看手机号13800001001的详情`, that request is
  currently parsed as `FillPersonalInfo` instead of `OpenPersonalInfoDetail`.
- There is no tail-number extraction. `查看手机号尾号1001的详情` yields
  `slots.phone == null`, so the resolver cannot match it.
- `PersonalInfoRecordResolver` matches phone by exact equality of digit-only
  strings, so a 4-digit tail never equals an 11-digit record phone and the
  resolution is `none()`.

## Desired observable behavior

1. `查看手机号13800001001的详情` parses to `OpenPersonalInfoDetail` with
   `slots.phone == "13800001001"`, and the resolver uniquely matches 张雨桐.
2. `查看手机号尾号1001的详情` parses to `OpenPersonalInfoDetail` with
   `slots.phone == "1001"`, and the resolver uniquely matches 张雨桐 by suffix.
3. A non-existent tail (for example `9999`) resolves to `none()`, so the
   existing `TargetPlanner` detail fallback routes to the list with the existing
   no-match prompt.
4. `更新个人资料，电话13800001001` still parses to `FillPersonalInfo`.

## Acceptance criteria

1. `查看手机号13800001001的详情` parses to `OpenPersonalInfoDetail` and
   `slots.phone == "13800001001"`.
2. `查看手机号尾号1001的详情` parses to `OpenPersonalInfoDetail` and
   `slots.phone == "1001"`.
3. `resolveFromSlots(ParseSlots(phone = "13800001001"))` and
   `resolveFromSlots(ParseSlots(phone = "1001"))` both uniquely match 张雨桐
   (`record-2`).
4. `resolveFromSlots(ParseSlots(phone = "9999"))` returns `none()` (not unique
   and no candidates).
5. `更新个人资料，电话13800001001` still parses to `FillPersonalInfo`.
6. A focused local unit test is added and confirmed RED before implementation.
7. The focused tests, the complete local unit-test suite, the debug APK build,
   and Android lint all pass.

## Edge cases

- A tail must be exactly four digits following `尾号`. Three digits or five or
  more digits (for example `尾号10010`) are not recognized as a tail.
- An 11-digit query is matched by exact equality; any shorter digit string is
  matched by `endsWith` suffix against the record phone.
- A tail that matches more than one record follows the existing `ambiguous`
  resolution and falls back to the list; this task does not change ambiguity
  handling.
- `查看张雨桐的信息` (covered by the existing instrumented test) keeps routing
  to `OpenPersonalInfoDetail`.
- `更新个人资料，电话13800001001` (no `详情`) is not diverted to the detail
  goal by the new disambiguation rule.

## Allowed implementation paths

- `app/src/main/java/com/example/cctest/routing/parser/TextParsingSupport.kt`
- `app/src/main/java/com/example/cctest/routing/parser/RuleBasedIntentParser.kt`
- `app/src/main/java/com/example/cctest/feature/personalinfo/data/PersonalInfoRecordResolver.kt`
- `app/src/test/java/com/example/cctest/routing/RuleBasedIntentParserTest.kt`
- `app/src/test/java/com/example/cctest/routing/PersonalInfoRecordResolverTest.kt`

Implementation guidance:

- In `TextParsingSupport.kt`, add `extractPhoneTail(text: String): String?`
  recognizing `尾号\s*(\d{4})(?!\d)` on the normalized input and returning the
  four-digit group.
- In `RuleBasedIntentParser.kt`:
  - In the `looksLikeDetail` branch, set
    `phone = extractPhone(input) ?: extractPhoneTail(input)`.
  - Let `looksLikeDetail` treat a non-null `extractPhoneTail(text)` as a phone
    signal alongside `extractPhone(text)`.
  - In `looksLikeForm`, return `false` when the text contains both a viewing
    verb (`查看` / `看看` / `想看` / `帮我看` / `帮我看看` / `打开` / `进入`)
    and the keyword `详情`, so explicit detail-view requests are not swallowed
    by the form goal. All other `looksLikeForm` logic stays unchanged.
- In `PersonalInfoRecordResolver.kt`, when `normalizedPhone` is non-blank, match
  by exact equality when its length is at least 11, otherwise by
  `normalizePhone(it.phone).endsWith(normalizedPhone)`. The match-mode values,
  candidate handling, and the rest of the resolution order stay unchanged.

## Forbidden paths

- `.opencode/**`, `automation/**`, `scripts/automation/**`, `opencode.json`,
  `AGENTS.md`, `gradle/**`, `gradlew`, `gradlew.bat`, `settings.gradle.kts`,
  `build.gradle.kts`, `app/build.gradle.kts`.
- Any file not listed under Allowed implementation paths, including
  `TargetPlanner.kt`, `RoutePlanner.kt`, `JourneyPlanner.kt`, `SlotNormalizer.kt`,
  `ParseResultValidator.kt`, navigation sources, UI/resources, Gradle files, and
  other test files.

## Non-goals

- Do not change UI, navigation, Gradle, dependencies, or remote/LLM parsing.
- Do not change `TargetPlanner` fallback copy; the existing
  `未直接命中详情记录，已回退到列表页。` message serves as the no-match prompt.
- Do not add tail matching to the list intent or the form intent; form-field
  phone extraction continues to use `extractPhone` only.
- Do not change the existing `extractPhone` 11-digit pattern or
  `extractLookupName`.
- Do not add international telephone-number parsing or carrier-prefix
  validation.
- Do not add device/emulator tests.

## Verification

Focused test classes:

- `com.example.cctest.routing.RuleBasedIntentParserTest`
- `com.example.cctest.routing.PersonalInfoRecordResolverTest`

Commands:

```text
./gradlew testDebugUnitTest --tests "com.example.cctest.routing.RuleBasedIntentParserTest" --tests "com.example.cctest.routing.PersonalInfoRecordResolverTest"
./gradlew testDebugUnitTest assembleDebug lint
```

## Device/emulator requirements

None. The change is pure JVM-testable parser and resolver logic. The existing
instrumented flow `查看张雨桐的信息` is unchanged and is not in scope.

## Approval

Approved on 2026-08-17 through the repository user's interactive request to
define and implement one small programming task based on the current project.
