# Repository Guidelines

## Project Structure & Module Organization
This repository is a single-module Android app built with Gradle Kotlin DSL.

- `app/src/main/java/com/example/cctest/`: Kotlin source files, including `Activity` and `Fragment` classes.
- `app/src/main/res/`: Android resources such as layouts, strings, navigation, and themes.
- `app/src/test/`: local JVM unit tests.
- `app/src/androidTest/`: instrumented tests that run on a device or emulator.
- `gradle/`, `gradlew`, `gradlew.bat`: Gradle wrapper and shared build configuration.

Keep feature code close to its UI entry point. For example, `PersonalInfoDetailActivity.kt` should live beside related fragments in `app/src/main/java/com/example/cctest/`.

## Build, Test, and Development Commands
- `./gradlew assembleDebug`: builds the debug APK.
- `./gradlew testDebugUnitTest`: runs local unit tests in `app/src/test/`.
- `./gradlew connectedDebugAndroidTest`: runs instrumented tests on a connected device/emulator.
- `./gradlew lint`: runs Android lint checks.

Run commands from the repository root. Example:

```bash
./gradlew assembleDebug testDebugUnitTest
```

## Coding Style & Naming Conventions
Use Kotlin with 4-space indentation and keep files ASCII unless the file already contains localized text. Follow Android naming conventions:

- Classes: `PascalCase` (`PersonalInfoListFragment`)
- methods/variables: `camelCase`
- resources: `snake_case` (`activity_personal_info_detail.xml`, `personal_info_detail_title`)

Prefer view binding over `findViewById`. Put user-facing text in `app/src/main/res/values/strings.xml`, not inline in code, unless generating mock data intentionally.

## Testing Guidelines
Use JUnit4 for local tests and AndroidX test runners for instrumented tests. Name test files after the class under test, for example `PersonalInfoDetailActivityTest`. Name test methods descriptively, such as `clickBack_finishesActivity`.

This project currently contains only starter tests; new UI behavior should add either a local unit test or an instrumented test when practical.

## Commit & Pull Request Guidelines
No Git history is available in this workspace snapshot, so no existing commit convention can be inferred reliably. Use short, imperative commit messages such as `Add detail activity for personal info list items`.

For pull requests, include:
- a short summary of the change
- testing performed (`assembleDebug`, unit tests, device checks)
- screenshots for UI/layout changes
- linked issue or task reference when available

<!-- opencode-android-orchestrator:begin -->
## OpenCode Android Orchestrator

Run orchestration commands from the Git repository root.

For automated local unit-test verification, run `./gradlew testDebugUnitTest`
as a standalone command. Do not prepend environment assignments or wrap it in
another script; this keeps the invocation aligned with the installed OpenCode
command allowlist.

Treat `.opencode/`, `automation/`, `scripts/automation/`, `opencode.json`,
and this managed block as orchestration infrastructure. Product tasks must not
modify them unless an explicitly approved task is scoped to maintaining the
orchestrator itself.

Only a fresh OpenCode single-choice `question` selection can grant an
orchestration approval. Approval-like text in ordinary chat is not approval.
The orchestrator must not push Git changes or register scheduler/launchd jobs.
<!-- opencode-android-orchestrator:end -->
