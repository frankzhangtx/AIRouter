#!/usr/bin/env bash
set -euo pipefail

TEST_DIR="$(cd "$(dirname "$0")" && pwd)"
SOURCE_SCRIPT_DIR="$(cd "$TEST_DIR/.." && pwd)"
fixture="$(mktemp -d "${TMPDIR:-/tmp}/scheduled-quality-gate-tests.XXXXXX")"
runtime_root="$(mktemp -d "${TMPDIR:-/tmp}/scheduled-quality-runtime.XXXXXX")"
worktree_base="$(mktemp -d "${TMPDIR:-/tmp}/scheduled-quality-worktrees.XXXXXX")"
fixture="$(cd "$fixture" && pwd -P)"
runtime_root="$(cd "$runtime_root" && pwd -P)"
worktree_base="$(cd "$worktree_base" && pwd -P)"

cleanup() {
    if [[ -d "$fixture/.git" ]]; then
        while IFS= read -r path; do
            [[ -n "$path" && "$path" != "$fixture" ]] || continue
            git -C "$fixture" worktree remove --force "$path" >/dev/null 2>&1 || true
        done < <(git -C "$fixture" worktree list --porcelain 2>/dev/null | awk '/^worktree / { sub(/^worktree /, ""); print }')
    fi
    rm -rf "$fixture" "$runtime_root" "$worktree_base"
}
trap cleanup EXIT

pass_count=0

pass() {
    pass_count=$((pass_count + 1))
    printf 'ok %d - %s\n' "$pass_count" "$1"
}

fail() {
    printf 'not ok - %s\n' "$1" >&2
    exit 1
}

run_fixture() {
    (
        cd "$fixture"
        unset AUTOMATION_PROJECT_ROOT
        export AUTOMATION_TEST_MODE=1
        export AUTOMATION_RUNTIME_ROOT="$runtime_root"
        export AUTOMATION_WORKTREE_BASE="$worktree_base"
        "$@"
    )
}

run_task() {
    local task_root="$1"
    shift
    (
        cd "$task_root"
        unset AUTOMATION_PROJECT_ROOT
        export AUTOMATION_TEST_MODE=1
        export AUTOMATION_RUNTIME_ROOT="$runtime_root"
        export AUTOMATION_WORKTREE_BASE="$worktree_base"
        "$@"
    )
}

write_workspace() {
    local task_id="$1"
    local head branch
    head="$(git -C "$fixture" rev-parse HEAD)"
    branch="$(git -C "$fixture" symbolic-ref --short HEAD)"
    mkdir -p "$runtime_root/workspaces"
    jq -n \
        --arg taskId "$task_id" \
        --arg sourceRoot "$fixture" \
        --arg originalBranch "$branch" \
        --arg baselineHead "$head" \
        --arg taskWorktree "$fixture" \
        --arg taskBranch "$branch" \
        --arg worktreeBase "$worktree_base" \
        '{taskId: $taskId, sourceRoot: $sourceRoot,
          originalBranch: $originalBranch, baselineHead: $baselineHead,
          taskWorktree: $taskWorktree, taskBranch: $taskBranch,
          worktreeBase: $worktreeBase, codingCycle: 0, reviewCycles: 0}' \
        > "$runtime_root/workspaces/$task_id.json"
}

mkdir -p \
    "$fixture/automation/tasks" \
    "$fixture/docs/plans" \
    "$fixture/scripts/automation" \
    "$fixture/app/src/main/java/com/example/cctest" \
    "$fixture/app/src/test/java/com/example/cctest"

cp "$SOURCE_SCRIPT_DIR"/*.sh "$fixture/scripts/automation/"
chmod +x "$fixture/scripts/automation/"*.sh
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

jq -n '{
    schemaVersion: 2,
    enabled: true,
    mode: "orchestrated",
    requireDedicatedWorktree: true,
    worktreeBase: "",
    maxFixLoops: 1,
    maxReviewCycles: 1,
    autoCleanupWorktrees: true,
    pushAfterAcceptance: false,
    approvalPhrases: {
        proposal: "批准方案，生成计划和任务合同。",
        contract: "合同已复核，批准自动执行到人工验收阶段。",
        acceptance: "验收通过，提交到原分支。"
    },
    plugins: {
        scheduler: "opencode-scheduler@1.3.0",
        superpowers: "superpowers@git+https://github.com/obra/superpowers.git#v6.2.0"
    },
    requiredSkills: [
        "brainstorming",
        "writing-plans",
        "scheduled-quality-orchestrator",
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

run_fixture ./scripts/automation/validate-contract.sh TASK-TEST-001 >/dev/null
pass 'valid version-2 configuration and task contract are accepted'

if run_fixture env AUTOMATION_HUMAN_APPROVED=0 ./scripts/automation/queue-task.sh TASK-TEST-001 >/dev/null 2>&1; then
    fail 'legacy queue accepted without explicit human approval'
fi
pass 'legacy queue rejects missing human approval'

run_fixture env AUTOMATION_HUMAN_APPROVED=1 ./scripts/automation/queue-task.sh TASK-TEST-001 >/dev/null
write_workspace TASK-TEST-001
[[ "$(jq -r '.state' "$runtime_root/state/TASK-TEST-001.json")" == "PENDING" ]] || fail 'queue did not create PENDING state'
pass 'explicit queue creates one auditable PENDING state'

[[ "$(run_fixture ./scripts/automation/select-task.sh PENDING)" == "TASK-TEST-001" ]] || fail 'selector did not return the only pending task'
pass 'selector returns exactly one eligible task'

jq '.taskId = "TASK-TEST-999"' \
    "$runtime_root/state/TASK-TEST-001.json" \
    > "$runtime_root/state/TASK-TEST-999.json"
if run_fixture ./scripts/automation/select-task.sh PENDING >/dev/null 2>&1; then
    fail 'selector chose among multiple pending tasks'
fi
rm "$runtime_root/state/TASK-TEST-999.json"
pass 'selector refuses to choose among multiple eligible tasks'

mkdir -p "$runtime_root/locks/TASK-TEST-001.state.lock"
printf '%s\n' '99999999' > "$runtime_root/locks/TASK-TEST-001.state.lock/pid"
if run_fixture ./scripts/automation/transition-state.sh TASK-TEST-001 PENDING READY_FOR_REVIEW human invalid >/dev/null 2>&1; then
    fail 'illegal transition was accepted'
fi
[[ ! -d "$runtime_root/locks/TASK-TEST-001.state.lock" ]] || fail 'stale state lock was not recovered'
pass 'stopped-process locks are recovered without bypassing state rules'

if run_fixture ./scripts/automation/transition-state.sh TASK-TEST-001 PENDING READY_FOR_REVIEW human invalid >/dev/null 2>&1; then
    fail 'illegal transition was accepted after lock recovery'
fi
pass 'illegal state transition is rejected'

run_fixture ./scripts/automation/claim-task.sh TASK-TEST-001 >/dev/null
[[ "$(jq -r '.state' "$runtime_root/state/TASK-TEST-001.json")" == "CODING" ]] || fail 'claim did not create CODING state'
[[ -f "$runtime_root/evidence/TASK-TEST-001/baseline.json" ]] || fail 'baseline metadata missing'
pass 'claim verifies workspace identity and records a green baseline'

printf '%s\n' 'class GreetingTest { fun expectedBehavior() = Unit }' > "$fixture/app/src/test/java/com/example/cctest/GreetingTest.kt"
run_fixture ./scripts/automation/record-red.sh TASK-TEST-001 'expected missing behavior' -- com.example.cctest.GreetingTest >/dev/null
[[ "$(jq -r '.exitCode' "$runtime_root/evidence/TASK-TEST-001/red.json")" -ne 0 ]] || fail 'RED evidence exit code was not captured'
pass 'focused failing test records genuine RED evidence'

printf '%s\n' 'unsafe' > "$fixture/automation/forbidden-change.txt"
if run_fixture ./scripts/automation/scope-gate.sh TASK-TEST-001 >/dev/null 2>&1; then
    fail 'scope gate accepted protected path change'
fi
rm "$fixture/automation/forbidden-change.txt"
pass 'scope gate rejects protected path changes'

printf '%s\n' 'class GreetingTest { fun expectedBehavior() { assertTrue(true) } }' > "$fixture/app/src/test/java/com/example/cctest/GreetingTest.kt"
printf '%s\n' 'class Greeting { fun value() = "hello" }' > "$fixture/app/src/main/java/com/example/cctest/Greeting.kt"
if run_fixture ./scripts/automation/scope-gate.sh TASK-TEST-001 >/dev/null 2>&1; then
    fail 'scope gate accepted weakened test'
fi
pass 'scope gate rejects obvious test weakening'

printf '%s\n' 'class GreetingTest { fun expectedBehavior() { check(Greeting().value() == "hello") } }' > "$fixture/app/src/test/java/com/example/cctest/GreetingTest.kt"
run_fixture env AUTOMATION_FAKE_GREEN=1 ./scripts/automation/quality-gate.sh TASK-TEST-001 >/dev/null
[[ "$(jq -r '.state' "$runtime_root/state/TASK-TEST-001.json")" == "READY_FOR_REVIEW" ]] || fail 'quality gate did not create READY_FOR_REVIEW state'
pass 'G1-G6 seal all tracked and untracked product changes'

run_fixture ./scripts/automation/begin-review.sh TASK-TEST-001 >/dev/null
[[ "$(jq -r '.state' "$runtime_root/state/TASK-TEST-001.json")" == "REVIEWING" ]] || fail 'review handoff did not create REVIEWING state'
pass 'orchestrator creates an explicit sealed reviewer handoff'

run_fixture env AUTOMATION_FAKE_GREEN=1 ./scripts/automation/submit-review.sh TASK-TEST-001 APPROVED 'Independent diff review found no material issue.' >/dev/null
[[ "$(jq -r '.state' "$runtime_root/state/TASK-TEST-001.json")" == "AWAITING_HUMAN" ]] || fail 'review did not create AWAITING_HUMAN state'
pass 'independent approval stops at AWAITING_HUMAN'

transition_count="$(wc -l < "$runtime_root/evidence/TASK-TEST-001/transitions.jsonl" | tr -d ' ')"
[[ "$transition_count" -eq 6 ]] || fail "unexpected transition audit count: $transition_count"
pass 'append-only transition audit contains the complete gated lifecycle'

(
    cd "$fixture"
    git add app
    git commit -qm 'Preserve first fixture task'
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
run_fixture env AUTOMATION_HUMAN_APPROVED=1 ./scripts/automation/queue-task.sh TASK-TEST-002 >/dev/null
write_workspace TASK-TEST-002
run_fixture ./scripts/automation/claim-task.sh TASK-TEST-002 >/dev/null
printf '%s\n' 'class FailureLoopTest { fun expectedBehavior() = Unit }' > "$fixture/app/src/test/java/com/example/cctest/FailureLoopTest.kt"
run_fixture ./scripts/automation/record-red.sh TASK-TEST-002 'expected missing behavior' -- com.example.cctest.FailureLoopTest >/dev/null
printf '%s\n' 'class FailureLoop { fun value() = "still failing" }' > "$fixture/app/src/main/java/com/example/cctest/FailureLoop.kt"

if run_fixture ./scripts/automation/quality-gate.sh TASK-TEST-002 >/dev/null 2>&1; then
    fail 'first failing gate attempt unexpectedly passed'
fi
[[ "$(jq -r '.state' "$runtime_root/state/TASK-TEST-002.json")" == "CODING" ]] || fail 'first failure did not preserve CODING'
pass 'first verification failure allows one systematic fix attempt'

if run_fixture ./scripts/automation/quality-gate.sh TASK-TEST-002 >/dev/null 2>&1; then
    fail 'second failing gate attempt unexpectedly passed'
fi
[[ "$(jq -r '.state' "$runtime_root/state/TASK-TEST-002.json")" == "TEST_FAILED" ]] || fail 'second failure did not create TEST_FAILED'
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
run_fixture env AUTOMATION_HUMAN_APPROVED=1 ./scripts/automation/queue-task.sh TASK-TEST-003 >/dev/null
write_workspace TASK-TEST-003
printf '%s\n' 'dirty' > "$fixture/unapproved-change.txt"
if run_fixture ./scripts/automation/claim-task.sh TASK-TEST-003 >/dev/null 2>&1; then
    fail 'claim accepted a dirty worktree'
fi
[[ "$(jq -r '.state' "$runtime_root/state/TASK-TEST-003.json")" == "BLOCKED" ]] || fail 'preflight failure did not create BLOCKED'
rm "$fixture/unapproved-change.txt"
pass 'preflight blocks dirty task worktrees without retrying implicitly'

printf '%s\n' '# Approved reviewer-fix plan' > "$fixture/docs/plans/TASK-TEST-005.md"
jq \
    '.id = "TASK-TEST-005" |
     .title = "Exercise the bounded reviewer repair cycle" |
     .planPath = "docs/plans/TASK-TEST-005.md" |
     .targetTests = ["com.example.cctest.ReviewerFixTest"]' \
    "$fixture/automation/tasks/TASK-TEST-001.json" \
    > "$fixture/automation/tasks/TASK-TEST-005.json"
(
    cd "$fixture"
    git add automation/tasks/TASK-TEST-005.json docs/plans/TASK-TEST-005.md
    git commit -qm 'Add reviewer-fix fixture task'
)
run_fixture env AUTOMATION_HUMAN_APPROVED=1 ./scripts/automation/queue-task.sh TASK-TEST-005 >/dev/null
write_workspace TASK-TEST-005
run_fixture ./scripts/automation/claim-task.sh TASK-TEST-005 >/dev/null
printf '%s\n' 'class ReviewerFixTest { fun expectedBehavior() = Unit }' > "$fixture/app/src/test/java/com/example/cctest/ReviewerFixTest.kt"
run_fixture ./scripts/automation/record-red.sh TASK-TEST-005 'expected missing behavior' -- com.example.cctest.ReviewerFixTest >/dev/null
printf '%s\n' 'class ReviewerFix { fun value() = "first pass" }' > "$fixture/app/src/main/java/com/example/cctest/ReviewerFix.kt"
run_fixture env AUTOMATION_FAKE_GREEN=1 ./scripts/automation/quality-gate.sh TASK-TEST-005 >/dev/null
run_fixture ./scripts/automation/begin-review.sh TASK-TEST-005 >/dev/null
if run_fixture ./scripts/automation/submit-review.sh TASK-TEST-005 CHANGES_REQUESTED 'Reviewer found one material in-contract behavior issue.' >/dev/null 2>&1; then
    fail 'changes-requested review unexpectedly returned success'
fi
[[ "$(jq -r '.state' "$runtime_root/state/TASK-TEST-005.json")" == "CHANGES_REQUESTED" ]] || fail 'review finding did not create CHANGES_REQUESTED'
pass 'reviewer findings are recorded as a distinct state'

run_fixture ./scripts/automation/resume-review-fix.sh TASK-TEST-005 >/dev/null
[[ "$(jq -r '.state' "$runtime_root/state/TASK-TEST-005.json")" == "CODING" ]] || fail 'bounded repair did not return to CODING'
[[ "$(jq -r '.codingCycle' "$runtime_root/workspaces/TASK-TEST-005.json")" -eq 1 ]] || fail 'repair did not create a fresh gate cycle'
pass 'one configured reviewer repair gets a fresh quality-gate cycle'

printf '%s\n' 'class ReviewerFix { fun value() = "second pass" }' > "$fixture/app/src/main/java/com/example/cctest/ReviewerFix.kt"
run_fixture env AUTOMATION_FAKE_GREEN=1 ./scripts/automation/quality-gate.sh TASK-TEST-005 >/dev/null
run_fixture ./scripts/automation/begin-review.sh TASK-TEST-005 >/dev/null
if run_fixture ./scripts/automation/submit-review.sh TASK-TEST-005 CHANGES_REQUESTED 'Fresh reviewer still found a material behavior issue.' >/dev/null 2>&1; then
    fail 'second changes-requested review unexpectedly returned success'
fi
if run_fixture ./scripts/automation/resume-review-fix.sh TASK-TEST-005 >/dev/null 2>&1; then
    fail 'review repair limit was bypassed'
fi
[[ "$(jq -r '.state' "$runtime_root/state/TASK-TEST-005.json")" == "NEEDS_HUMAN" ]] || fail 'repair exhaustion did not create NEEDS_HUMAN'
pass 'reviewer repair limit prevents an unbounded agent loop'

(
    cd "$fixture"
    git add app
    git commit -qm 'Preserve reviewer-fix fixture outcome'
)

printf '%s\n' '# End-to-end orchestrated plan' > "$fixture/docs/plans/TASK-TEST-004.md"
jq \
    '.id = "TASK-TEST-004" |
     .title = "Exercise automatic worktree integration" |
     .planPath = "docs/plans/TASK-TEST-004.md" |
     .targetTests = ["com.example.cctest.OrchestratedFlowTest"]' \
    "$fixture/automation/tasks/TASK-TEST-001.json" \
    > "$fixture/automation/tasks/TASK-TEST-004.json"

if run_fixture ./scripts/automation/prepare-contract-review.sh TASK-TEST-004 '确认' >/dev/null 2>&1; then
    fail 'proposal preparation accepted an unbound confirmation'
fi
pass 'proposal gate requires the configured explicit approval phrase'

run_fixture ./scripts/automation/prepare-contract-review.sh TASK-TEST-004 '批准方案，生成计划和任务合同。' >/dev/null
[[ "$(jq -r '.state' "$runtime_root/state/TASK-TEST-004.json")" == "CONTRACT_REVIEW" ]] || fail 'proposal gate did not enter CONTRACT_REVIEW'
pass 'approved proposal seals plan, contract, branch, HEAD, and hashes'

if run_fixture ./scripts/automation/approve-and-run.sh TASK-TEST-004 '确认执行' >/dev/null 2>&1; then
    fail 'contract execution accepted an unbound confirmation'
fi
pass 'contract gate rejects execution without the exact approval'

run_fixture env AUTOMATION_SKIP_AGENT_RUN=1 ./scripts/automation/approve-and-run.sh TASK-TEST-004 '合同已复核，批准自动执行到人工验收阶段。' >/dev/null
task_worktree="$(jq -er '.taskWorktree' "$runtime_root/workspaces/TASK-TEST-004.json")"
[[ -d "$task_worktree" ]] || fail 'orchestrator did not create the external task worktree'
[[ "$(jq -r '.state' "$runtime_root/state/TASK-TEST-004.json")" == "PENDING" ]] || fail 'prepared task did not become PENDING'
pass 'contract approval commits its baseline and creates an outside task worktree'

run_task "$task_worktree" ./scripts/automation/claim-task.sh TASK-TEST-004 >/dev/null
printf '%s\n' 'class OrchestratedFlowTest { fun expectedBehavior() = Unit }' > "$task_worktree/app/src/test/java/com/example/cctest/OrchestratedFlowTest.kt"
run_task "$task_worktree" ./scripts/automation/record-red.sh TASK-TEST-004 'expected missing behavior' -- com.example.cctest.OrchestratedFlowTest >/dev/null
printf '%s\n' 'class OrchestratedFlow { fun value() = "integrated" }' > "$task_worktree/app/src/main/java/com/example/cctest/OrchestratedFlow.kt"
run_task "$task_worktree" env AUTOMATION_FAKE_GREEN=1 ./scripts/automation/quality-gate.sh TASK-TEST-004 >/dev/null
run_task "$task_worktree" ./scripts/automation/begin-review.sh TASK-TEST-004 >/dev/null
run_task "$task_worktree" env AUTOMATION_FAKE_GREEN=1 ./scripts/automation/submit-review.sh TASK-TEST-004 APPROVED 'Fresh review confirms the sealed behavior and scope.' >/dev/null
run_task "$task_worktree" ./scripts/automation/acceptance-report.sh TASK-TEST-004 >/dev/null
[[ "$(jq -r '.changedPaths | length' "$runtime_root/evidence/TASK-TEST-004/acceptance-report.json")" -eq 2 ]] || fail 'acceptance package omitted product paths'
[[ "$(jq -r '.evidence.qualityGate' "$runtime_root/evidence/TASK-TEST-004/acceptance-report.json")" == "PASSED" ]] || fail 'acceptance package omitted quality-gate status'
acceptance_card="$(run_fixture ./scripts/automation/show-acceptance-review.sh TASK-TEST-004)"
[[ "$acceptance_card" == *"人工验收提醒"* ]] || fail 'acceptance review did not actively identify the human gate'
[[ "$acceptance_card" == *"P0 · 真实行为是否满足合同"* ]] || fail 'acceptance review omitted behavioral focus'
[[ "$acceptance_card" == *"P0 · 旧行为与范围是否被误伤"* ]] || fail 'acceptance review omitted regression and scope focus'
[[ "$acceptance_card" == *"sealed diff SHA"* ]] || fail 'acceptance review omitted sealed binding'
pass 'automated evidence becomes one focused, SHA-verified human acceptance card'

printf '%s\n' 'class OrchestratedFlow { fun value() = "tampered after review" }' > "$task_worktree/app/src/main/java/com/example/cctest/OrchestratedFlow.kt"
if run_fixture ./scripts/automation/show-acceptance-review.sh TASK-TEST-004 >/dev/null 2>&1; then
    fail 'acceptance review displayed a diff changed after sealing'
fi
printf '%s\n' 'class OrchestratedFlow { fun value() = "integrated" }' > "$task_worktree/app/src/main/java/com/example/cctest/OrchestratedFlow.kt"
[[ "$(jq -r '.state' "$runtime_root/state/TASK-TEST-004.json")" == "AWAITING_HUMAN" ]] || fail 'read-only acceptance display changed task state'
pass 'acceptance display rejects a changed diff and never advances state'

original_branch="$(git -C "$fixture" symbolic-ref --short HEAD)"
if run_fixture env AUTOMATION_FAKE_GREEN=1 ./scripts/automation/accept-and-integrate.sh TASK-TEST-004 '拒绝' >/dev/null 2>&1; then
    fail 'integrator accepted an invalid final confirmation'
fi
pass 'integrator requires final acceptance bound to the sealed diff'

run_fixture env AUTOMATION_FAKE_GREEN=1 ./scripts/automation/accept-and-integrate.sh TASK-TEST-004 '验收通过，提交到原分支。' >/dev/null
[[ "$(jq -r '.state' "$runtime_root/state/TASK-TEST-004.json")" == "COMPLETED" ]] || fail 'final integration did not reach COMPLETED'
[[ "$(git -C "$fixture" symbolic-ref --short HEAD)" == "$original_branch" ]] || fail 'integrator changed the original branch identity'
[[ -f "$fixture/app/src/main/java/com/example/cctest/OrchestratedFlow.kt" ]] || fail 'product change was not integrated into original branch'
[[ "$(jq -r '.pushed' "$runtime_root/evidence/TASK-TEST-004/integration.json")" == "false" ]] || fail 'integration evidence did not forbid push'
[[ ! -d "$task_worktree" ]] || fail 'successful flow did not clean the task worktree'
pass 'verified candidate is committed to the recorded original branch without push'

printf '%s\n' '# Advanced-original integration plan' > "$fixture/docs/plans/TASK-TEST-006.md"
jq \
    '.id = "TASK-TEST-006" |
     .title = "Integrate onto an advanced original branch" |
     .planPath = "docs/plans/TASK-TEST-006.md" |
     .targetTests = ["com.example.cctest.AdvancedOriginalTest"]' \
    "$fixture/automation/tasks/TASK-TEST-001.json" \
    > "$fixture/automation/tasks/TASK-TEST-006.json"
run_fixture ./scripts/automation/prepare-contract-review.sh TASK-TEST-006 '批准方案，生成计划和任务合同。' >/dev/null
run_fixture env AUTOMATION_SKIP_AGENT_RUN=1 ./scripts/automation/approve-and-run.sh TASK-TEST-006 '合同已复核，批准自动执行到人工验收阶段。' >/dev/null
advanced_task_worktree="$(jq -er '.taskWorktree' "$runtime_root/workspaces/TASK-TEST-006.json")"
run_task "$advanced_task_worktree" ./scripts/automation/claim-task.sh TASK-TEST-006 >/dev/null
printf '%s\n' 'class AdvancedOriginalTest { fun expectedBehavior() = Unit }' > "$advanced_task_worktree/app/src/test/java/com/example/cctest/AdvancedOriginalTest.kt"
run_task "$advanced_task_worktree" ./scripts/automation/record-red.sh TASK-TEST-006 'expected missing behavior' -- com.example.cctest.AdvancedOriginalTest >/dev/null
printf '%s\n' 'class AdvancedOriginal { fun value() = "integrated after drift" }' > "$advanced_task_worktree/app/src/main/java/com/example/cctest/AdvancedOriginal.kt"
run_task "$advanced_task_worktree" env AUTOMATION_FAKE_GREEN=1 ./scripts/automation/quality-gate.sh TASK-TEST-006 >/dev/null
run_task "$advanced_task_worktree" ./scripts/automation/begin-review.sh TASK-TEST-006 >/dev/null
run_task "$advanced_task_worktree" env AUTOMATION_FAKE_GREEN=1 ./scripts/automation/submit-review.sh TASK-TEST-006 APPROVED 'Independent review approves the advanced-branch fixture.' >/dev/null

printf '%s\n' 'unrelated original-branch advance' > "$fixture/original-branch-note.txt"
(
    cd "$fixture"
    git add original-branch-note.txt
    git commit -qm 'Advance original branch independently'
)
[[ "$(git -C "$fixture" rev-parse HEAD)" != "$(jq -r '.baselineHead' "$runtime_root/workspaces/TASK-TEST-006.json")" ]] || fail 'original branch did not advance for drift test'
pass 'a clean descendant commit is recognized as original-branch drift'

run_fixture env AUTOMATION_FAKE_GREEN=1 ./scripts/automation/accept-and-integrate.sh TASK-TEST-006 '验收通过，提交到原分支。' >/dev/null
[[ "$(jq -r '.method' "$runtime_root/evidence/TASK-TEST-006/integration.json")" == "cherry-pick-onto-advanced-original" ]] || fail 'advanced integration did not use a verified candidate cherry-pick'
[[ -f "$fixture/original-branch-note.txt" && -f "$fixture/app/src/main/java/com/example/cctest/AdvancedOriginal.kt" ]] || fail 'advanced integration lost source or product changes'
pass 'verified cherry-pick integrates product code without losing an advanced original branch'

printf '1..%d\n' "$pass_count"
