# OpenCode coding orchestration V3

This directory contains the versioned policy for a low-intervention coding
flow. The user stays in one `scheduled-planner` conversation; deterministic
scripts own transactional branch preparation, persistent workspace leasing,
state transitions, evidence, agent launches, commits, and local integration.

## One-time setup

Start OpenCode from a clean, attached branch with the Android SDK exported in
the same Terminal session:

```bash
cd /Users/zhanglong/files/program/cctest
export ANDROID_HOME=/Users/zhanglong/Library/Android/sdk
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
./scripts/automation/preflight.sh --source
opencode --agent scheduled-planner .
```

To persist the SDK variables for future Terminal windows, put the two `export`
lines in `~/.zshrc` and run `source ~/.zshrc` once. The automation never writes
`local.properties` and never prepends environment assignments to Gradle.

## Workspace strategy

`workspaceStrategy` defaults to `inPlaceExclusive`. Contract approval seals but
does not commit the plan and contract, acquires a persistent repository lease,
creates the task branch from the unchanged original HEAD, and switches the
existing source directory to that branch. The two planning artifacts remain
uncommitted and hash-protected while Coder, Reviewer, and all gates operate in
the same path, so the normal flow does not copy tracked files or create
duplicate Gradle build directories.

While the lease exists, do not edit the source directory or start another
automation task. The lease remains active across process exits and the human
acceptance wait; it is released only by successful integration or an explicit
deterministic abort.

Set `workspaceStrategy` to `isolatedWorktree` only when the original directory
must remain available. That mode creates one task worktree. It still verifies
the combined task commit in that task root and never creates a second integration
candidate worktree. Successful completion removes the task worktree when
`autoCleanupWorktrees` is enabled.

Both strategies use `originalBranchDriftPolicy: "block"`. If the original
branch no longer equals the recorded pre-task baseline, integration enters
`INTEGRATION_BLOCKED`; the automation does not cherry-pick, rebase, or resolve
conflicts automatically.

## Normal interaction

Inside OpenCode, use `/change` for one natural-language requirement. Planner
renders all three normal human gates as `question` single-select controls:

| Gate | Control | Approve option |
| --- | --- | --- |
| Proposal | `方案确认` | `批准方案，生成计划和任务合同。` |
| Contract | `合同复核` | `合同已复核，批准自动执行到人工验收阶段。` |
| Acceptance | `成果验收` | `验收通过，提交到原分支。` |

The user clicks an option and never needs to type an approval phrase. Direct
chat text does not approve any of these three gates, even when it repeats an
option label exactly. The configured phrases remain internal tokens passed by
Planner to deterministic scripts only after the matching option is selected.

After the first approve option is selected, Planner creates and seals exactly one plan and one JSON contract,
displays their review card, and asks for gate 2. After gate 2,
`approve-and-run.sh` records the actual original branch, prepares the configured
task workspace, and continuously runs the restricted Coder and a fresh
read-only Reviewer. It stops at `AWAITING_HUMAN` or a hard failure state.

At `AWAITING_HUMAN`, Planner immediately displays a SHA-verified acceptance
card organized by behavior, regression/scope, evidence, branch drift, and
remaining risk. `/acceptance <TASK-ID>` displays it again. After the third approve option is selected,
`accept-and-integrate.sh` creates exactly one combined commit containing the
approved plan, task contract, code, tests, and other authorized product
changes. It verifies that exact commit in the task root and fast-forwards the
recorded original branch only when it still equals the pre-task baseline. The
plan and contract never receive a standalone normal-path commit. It never
pushes.

If a read-only Reviewer exits before submitting a decision, use
`/resume-review <TASK-ID>`. The recovery verifies baseline, RED/ready evidence,
task branch, HEAD, scope, and diff SHA, then goes directly from `BLOCKED` to
`REVIEWING` without rerunning Coder or consuming a repair cycle.

For a supported stopped state, `/abort-task <TASK-ID>` presents a separate
explicit confirmation. `abort-task.sh` refuses out-of-contract changes,
archives the current diff and, when product changes exist, one combined
recovery commit. If the task is aborted before product editing, it archives the
planning diff without creating a planning-only commit. It then restores the
original branch without moving its ref, releases the lease, and records
`ABORTED` with `pushed: false`.

## Components

- `config.json` is portable V3 policy with no per-task absolute path.
- `config.schema.json` and `task-contract.schema.json` document configuration
  and task-contract formats.
- `tasks/` contains approved contracts; `docs/plans/` contains their plans.
- `.opencode/agents/` separates interactive Planner, write-limited Coder, and
  read-only Reviewer permissions.
- `.opencode/commands/acceptance.md`, `resume-review.md`, and `abort-task.md`
  expose bounded, state-checked recovery and review paths.
- `scripts/automation/` implements state, scope, evidence, workspace, and
  integration operations. Models never own Git mutations.

Runtime data lives under the shared Git common directory:

```text
<git-common-dir>/automation-runtime/
├── state/
├── evidence/
├── locks/
│   └── repository.workspace.lease/
└── workspaces/
```

The optional isolated worktree base defaults to the sibling directory
`<repository>-worktrees`. Absolute paths are discovered at runtime and are not
committed to `config.json`.

## State ownership

```text
CONTRACT_REVIEW → APPROVED_CONTRACT → PREPARING → PENDING → CODING
→ READY_FOR_REVIEW → REVIEWING → AWAITING_HUMAN → INTEGRATING → COMPLETED
```

Hard failures stop in `BLOCKED`, `TEST_FAILED`, `NEEDS_HUMAN`, or
`INTEGRATION_BLOCKED`. Explicit archival ends in `ABORTED`. Coder and Reviewer
cannot create worktrees, commit, merge, rebase, or push. The integrator cannot
update a different branch, accept a changed diff, skip candidate verification,
resolve drift automatically, or push.

`maxReviewCycles` bounds code repair after a Reviewer finding;
`maxReviewerRestarts` separately bounds no-code Reviewer restarts.

## Verification

```bash
./scripts/automation/tests/run-tests.sh
./scripts/automation/shadow-run.sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew lint
```

The shell suite exercises approval rejection, persistent lease exclusion,
in-place preparation without extra worktrees, optional single-worktree mode,
TDD gates, diff sealing, reviewer handoff/recovery, product verification,
single-commit plan/contract integration, original-branch drift blocking,
explicit archival without planning-only commits, cleanup, and the no-push
invariant.
