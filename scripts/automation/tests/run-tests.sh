#!/usr/bin/env bash
set -euo pipefail

TEST_DIR="$(cd "$(dirname "$0")" && pwd)"
SCRIPT_DIR="$(cd "$TEST_DIR/.." && pwd)"
fixture="$(mktemp -d "${TMPDIR:-/tmp}/scheduled-quality-gate-tests.XXXXXX")"
trap 'rm -rf "$fixture"' EXIT

pass_count=0

pass() {
    pass_count=$((pass_count + 1))
    printf 'ok %d - %s\n' "$pass_count" "$1"
}

fail() {
    printf 'not ok - %s\n' "$1" >&2
    exit 1
}

mkdir -p \
    "$fixture/automation/tasks" \
    "$fixture/automation/state" \
    "$fixture/automation/evidence" \
    "$fixture/automation/locks" \
    "$fixture/docs/plans" \
    "$fixture/app/src/main/java/com/example/cctest" \
    "$fixture/app/src/test/java/com/example/cctest"

printf '%s\n' '/automation/state/*' '/automation/evidence/*' '/automation/locks/*' > "$fixture/.gitignore"
printf '%s\n' '# Approved test plan' > "$fixture/docs/plans/TASK-TEST-001.md"
printf '%s\n' 'class ExistingFeature' > "$fixture/app/src/main/java/com/example/cctest/ExistingFeature.kt"

printf '%s\n' \
    '#!/usr/bin/env bash' \
    'set -euo pipefail' \
    'task="${1:-}"' \
    'if [[ "$task" == "testDebugUnitTest" && "${2:-}" == "--tests" && "${AUTOMATION_FAKE_GREEN:-0}" != "1" ]]; then' \
    '    printf "%s\n" "expected missing behavior"' \
    '    exit 1' \
    'fi' \
    'case "$task" in' \
    '    testDebugUnitTest|assembleDebug|lint|connectedDebugAndroidTest) printf "%s\n" "BUILD SUCCESSFUL" ;;' \
    '    *) printf "%s\n" "unexpected Gradle task: $task" >&2; exit 9 ;;' \
    'esac' \
    > "$fixture/gradlew"
chmod +x "$fixture/gradlew"

jq -n \
    --arg worktree "$fixture" \
    '{
        schemaVersion: 1,
        enabled: true,
        mode: "active",
        requireDedicatedWorktree: true,
        dedicatedWorktree: $worktree,
        maxFixLoops: 1,
        plugins: {
            scheduler: "opencode-scheduler@1.3.0",
            superpowers: "superpowers@git+https://github.com/obra/superpowers.git#v6.2.0"
        },
        requiredSkills: [
            "scheduled-quality-coder",
            "scheduled-quality-reviewer",
            "test-driven-development",
            "systematic-debugging",
            "verification-before-completion"
        ],
        protectedPaths: [
            ".opencode/",
            "automation/",
            "scripts/automation/",
            "opencode.json",
            "AGENTS.md",
            "gradle/",
            "gradlew",
            "gradlew.bat",
            "settings.gradle.kts",
            "build.gradle.kts",
            "app/build.gradle.kts"
        ]
    }' > "$fixture/automation/config.json"

jq -n '{
    schemaVersion: 1,
    id: "TASK-TEST-001",
    title: "Add an observable greeting behavior",
    designApproved: true,
    planPath: "docs/plans/TASK-TEST-001.md",
    ambiguityPolicy: "BLOCKED",
    maxFixLoops: 1,
    maxChangedFiles: 4,
    allowedPaths: [
        "app/src/main/java/com/example/cctest/**",
        "app/src/test/java/com/example/cctest/**"
    ],
    forbiddenPaths: [
        ".opencode/**",
        "automation/**",
        "scripts/automation/**",
        "opencode.json",
        "AGENTS.md",
        "gradle/**",
        "gradlew",
        "gradlew.bat",
        "settings.gradle.kts",
        "build.gradle.kts",
        "app/build.gradle.kts"
    ],
    allowedSuperpowers: [
        "test-driven-development",
        "systematic-debugging",
        "verification-before-completion"
    ],
    acceptanceCriteria: ["Greeting returns the approved value"],
    nonGoals: ["No unrelated refactoring"],
    targetTests: ["com.example.cctest.GreetingTest"],
    deviceTestsRequired: false,
    testPolicy: "required",
    testPolicyReason: "The behavior requires a focused regression test"
}' > "$fixture/automation/tasks/TASK-TEST-001.json"

(
    cd "$fixture"
    git init -q
    git config user.email 'automation-tests@example.invalid'
    git config user.name 'Automation Tests'
    git add .
    git commit -qm 'Create automation fixture'
)

export AUTOMATION_PROJECT_ROOT="$fixture"
export AUTOMATION_TEST_MODE=1

"$SCRIPT_DIR/validate-contract.sh" TASK-TEST-001 >/dev/null
pass 'valid contract is accepted'

if AUTOMATION_HUMAN_APPROVED=0 "$SCRIPT_DIR/queue-task.sh" TASK-TEST-001 >/dev/null 2>&1; then
    fail 'queue accepted without explicit human approval'
fi
pass 'queue rejects missing human approval'

AUTOMATION_HUMAN_APPROVED=1 "$SCRIPT_DIR/queue-task.sh" TASK-TEST-001 >/dev/null
[[ "$(jq -r '.state' "$fixture/automation/state/TASK-TEST-001.json")" == "PENDING" ]] || fail 'queue did not create PENDING state'
pass 'human queue creates PENDING state'

[[ "$("$SCRIPT_DIR/select-task.sh" PENDING)" == "TASK-TEST-001" ]] || fail 'selector did not return the only pending task'
pass 'selector returns exactly one eligible task'

jq '.taskId = "TASK-TEST-999"' \
    "$fixture/automation/state/TASK-TEST-001.json" \
    > "$fixture/automation/state/TASK-TEST-999.json"
if "$SCRIPT_DIR/select-task.sh" PENDING >/dev/null 2>&1; then
    fail 'selector chose among multiple pending tasks'
fi
rm "$fixture/automation/state/TASK-TEST-999.json"
pass 'selector refuses to choose among multiple eligible tasks'

if "$SCRIPT_DIR/transition-state.sh" TASK-TEST-001 PENDING READY_FOR_REVIEW human invalid >/dev/null 2>&1; then
    fail 'illegal transition was accepted'
fi
pass 'illegal state transition is rejected'

"$SCRIPT_DIR/claim-task.sh" TASK-TEST-001 >/dev/null
[[ "$(jq -r '.state' "$fixture/automation/state/TASK-TEST-001.json")" == "CODING" ]] || fail 'claim did not create CODING state'
[[ -f "$fixture/automation/evidence/TASK-TEST-001/baseline.json" ]] || fail 'baseline metadata missing'
pass 'claim requires clean preflight and records green baseline'

printf '%s\n' 'class GreetingTest { fun expectedBehavior() = Unit }' > "$fixture/app/src/test/java/com/example/cctest/GreetingTest.kt"
"$SCRIPT_DIR/record-red.sh" TASK-TEST-001 'expected missing behavior' -- com.example.cctest.GreetingTest >/dev/null
[[ "$(jq -r '.exitCode' "$fixture/automation/evidence/TASK-TEST-001/red.json")" -ne 0 ]] || fail 'RED evidence exit code was not captured'
pass 'focused failing test records genuine RED evidence'

printf '%s\n' 'unsafe' > "$fixture/automation/forbidden-change.txt"
if "$SCRIPT_DIR/scope-gate.sh" TASK-TEST-001 >/dev/null 2>&1; then
    fail 'scope gate accepted protected path change'
fi
rm "$fixture/automation/forbidden-change.txt"
pass 'scope gate rejects protected path changes'

printf '%s\n' 'class GreetingTest { fun expectedBehavior() { assertTrue(true) } }' > "$fixture/app/src/test/java/com/example/cctest/GreetingTest.kt"
printf '%s\n' 'class Greeting { fun value() = "hello" }' > "$fixture/app/src/main/java/com/example/cctest/Greeting.kt"
if "$SCRIPT_DIR/scope-gate.sh" TASK-TEST-001 >/dev/null 2>&1; then
    fail 'scope gate accepted weakened test'
fi
pass 'scope gate rejects obvious test weakening'

printf '%s\n' 'class GreetingTest { fun expectedBehavior() { check(Greeting().value() == "hello") } }' > "$fixture/app/src/test/java/com/example/cctest/GreetingTest.kt"
export AUTOMATION_FAKE_GREEN=1
if ! "$SCRIPT_DIR/quality-gate.sh" TASK-TEST-001 >/dev/null; then
    cat "$fixture/automation/evidence/TASK-TEST-001/gate-attempt-1.log" >&2
    fail 'quality gate verification failed unexpectedly'
fi
[[ "$(jq -r '.state' "$fixture/automation/state/TASK-TEST-001.json")" == "READY_FOR_REVIEW" ]] || fail 'quality gate did not create READY_FOR_REVIEW state'
pass 'G1-G6 produce READY_FOR_REVIEW only after deterministic verification'

"$SCRIPT_DIR/submit-review.sh" TASK-TEST-001 APPROVED 'Independent diff review found no material issue.' >/dev/null
[[ "$(jq -r '.state' "$fixture/automation/state/TASK-TEST-001.json")" == "AWAITING_HUMAN" ]] || fail 'review did not create AWAITING_HUMAN state'
pass 'independent approval stops at AWAITING_HUMAN'

transition_count="$(wc -l < "$fixture/automation/evidence/TASK-TEST-001/transitions.jsonl" | tr -d ' ')"
[[ "$transition_count" -eq 5 ]] || fail "unexpected transition audit count: $transition_count"
pass 'append-only transition audit contains the full lifecycle'

(
    cd "$fixture"
    git add app
    git commit -qm 'Merge first fixture task'
)

printf '%s\n' '# Approved failure-loop plan' > "$fixture/docs/plans/TASK-TEST-002.md"
jq \
    '.id = "TASK-TEST-002" |
     .title = "Exercise the bounded verification failure loop" |
     .planPath = "docs/plans/TASK-TEST-002.md" |
     .targetTests = ["com.example.cctest.FailureLoopTest"]' \
    "$fixture/automation/tasks/TASK-TEST-001.json" \
    > "$fixture/automation/tasks/TASK-TEST-002.json"
(
    cd "$fixture"
    git add automation/tasks/TASK-TEST-002.json docs/plans/TASK-TEST-002.md
    git commit -qm 'Add second approved fixture task'
)

AUTOMATION_HUMAN_APPROVED=1 "$SCRIPT_DIR/queue-task.sh" TASK-TEST-002 >/dev/null
"$SCRIPT_DIR/claim-task.sh" TASK-TEST-002 >/dev/null
printf '%s\n' 'class FailureLoopTest { fun expectedBehavior() = Unit }' > "$fixture/app/src/test/java/com/example/cctest/FailureLoopTest.kt"
unset AUTOMATION_FAKE_GREEN
"$SCRIPT_DIR/record-red.sh" TASK-TEST-002 'expected missing behavior' -- com.example.cctest.FailureLoopTest >/dev/null
printf '%s\n' 'class FailureLoop { fun value() = "still failing" }' > "$fixture/app/src/main/java/com/example/cctest/FailureLoop.kt"

if "$SCRIPT_DIR/quality-gate.sh" TASK-TEST-002 >/dev/null 2>&1; then
    fail 'first failing gate attempt unexpectedly passed'
fi
[[ "$(jq -r '.state' "$fixture/automation/state/TASK-TEST-002.json")" == "CODING" ]] || fail 'first failure did not preserve CODING for one fix loop'
pass 'first verification failure allows exactly one systematic fix loop'

if "$SCRIPT_DIR/quality-gate.sh" TASK-TEST-002 >/dev/null 2>&1; then
    fail 'second failing gate attempt unexpectedly passed'
fi
[[ "$(jq -r '.state' "$fixture/automation/state/TASK-TEST-002.json")" == "TEST_FAILED" ]] || fail 'second failure did not create TEST_FAILED state'
pass 'second verification failure deterministically stops in TEST_FAILED'

(
    cd "$fixture"
    git add app
    git commit -qm 'Preserve second fixture outcome'
)
printf '%s\n' '# Approved preflight-block plan' > "$fixture/docs/plans/TASK-TEST-003.md"
jq \
    '.id = "TASK-TEST-003" |
     .title = "Verify dirty worktree preflight blocking" |
     .planPath = "docs/plans/TASK-TEST-003.md" |
     .targetTests = ["com.example.cctest.PreflightBlockTest"]' \
    "$fixture/automation/tasks/TASK-TEST-001.json" \
    > "$fixture/automation/tasks/TASK-TEST-003.json"
(
    cd "$fixture"
    git add automation/tasks/TASK-TEST-003.json docs/plans/TASK-TEST-003.md
    git commit -qm 'Add preflight-block fixture task'
)
AUTOMATION_HUMAN_APPROVED=1 "$SCRIPT_DIR/queue-task.sh" TASK-TEST-003 >/dev/null
printf '%s\n' 'dirty' > "$fixture/unapproved-change.txt"
if "$SCRIPT_DIR/claim-task.sh" TASK-TEST-003 >/dev/null 2>&1; then
    fail 'claim accepted a dirty worktree'
fi
[[ "$(jq -r '.state' "$fixture/automation/state/TASK-TEST-003.json")" == "BLOCKED" ]] || fail 'preflight failure did not create BLOCKED state'
pass 'preflight failure blocks the task and prevents implicit cron retries'

printf '1..%d\n' "$pass_count"
