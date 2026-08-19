# TASK-PHONE-NORMALIZATION-001: Normalize separated mobile numbers

## Requirement

The rule-based intent parser must recognize a mainland China mobile number when
the 11 digits are written either continuously or in common `3-4-4` groups
separated by spaces or ASCII hyphens. The parsed slot must always contain the
digits-only form.

Examples:

- `13800001022` becomes `13800001022`.
- `138-0000-1022` becomes `13800001022`.
- `138 0000 1022` becomes `13800001022`.

## Acceptance criteria

1. A personal-information form request containing `138-0000-1022` is parsed as
   `FillPersonalInfo`, with `personalInfoFields.phone == "13800001022"`.
2. A personal-information form request containing `138 0000 1022` is parsed as
   `FillPersonalInfo`, with `personalInfoFields.phone == "13800001022"`.
3. Existing contiguous 11-digit mobile-number parsing remains unchanged.
4. A focused local unit test and the complete local unit-test suite pass.
5. The debug APK builds and Android lint passes.

## Edge cases

- Only ASCII spaces and ASCII hyphens are accepted as separators in this task.
- The number must still contain exactly 11 digits and start with `1`.
- Partial, overlong, or alphabetically separated values are not accepted.

## Allowed implementation paths

- `app/src/main/java/com/example/cctest/routing/parser/TextParsingSupport.kt`
- `app/src/test/java/com/example/cctest/routing/RuleBasedIntentParserTest.kt`

## Non-goals

- Do not change UI, navigation, Gradle, dependencies, or remote parsing.
- Do not add international telephone-number parsing.
- Do not validate carrier prefixes beyond the existing leading `1` rule.

## Verification

Focused test class:
`com.example.cctest.routing.RuleBasedIntentParserTest`

Commands:

```text
./gradlew testDebugUnitTest --tests com.example.cctest.routing.RuleBasedIntentParserTest
./gradlew testDebugUnitTest assembleDebug lint
```

## Approval

Approved on 2026-08-09 through the repository user's interactive request to
define and implement one small programming task based on the current project.
