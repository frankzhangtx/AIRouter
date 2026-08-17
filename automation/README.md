# OpenCode coding orchestration V2

This directory contains the versioned policy for a low-intervention coding
flow. The user stays in one `scheduled-planner` conversation; deterministic
scripts own worktree creation, state transitions, evidence, agent launches,
commits, and local integration.

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

## Normal interaction

Inside OpenCode, use `/change` for one natural-language requirement. The three
intentional human gates are:

1. `批准方案，生成计划和任务合同。`
2. Select `合同已复核，批准自动执行到人工验收阶段。` from the
   automatically displayed contract-review question. Typing the same exact
   phrase remains valid.
3. Review the automatically displayed result card, then select
   `验收通过，提交到原分支。` from the final question. Typing the same exact
   phrase remains valid.

After gate 1, Planner creates and seals exactly one plan and one JSON contract,
automatically displays their review card, and asks for gate 2. The user does
not need to enter a task ID or request contract fields manually.
After gate 2, `approve-and-run.sh` commits those planning artifacts, records the
actual current branch, creates an outside task worktree, and continuously runs
the restricted Coder and a fresh read-only Reviewer. It stops at
`AWAITING_HUMAN` or a hard failure state. At `AWAITING_HUMAN`, Planner
immediately displays a SHA-verified acceptance card organized by behavior,
regression/scope, evidence, and remaining risk, then opens the final approval
question. The user does not need to compose a report request or inspect raw
JSON. If the card is missed, `/acceptance <TASK-ID>` displays it again. After
gate 3,
`accept-and-integrate.sh` creates one product commit, verifies it in a separate
integration-candidate worktree, and fast-forwards the recorded original branch
only if all checks pass.

There is no normal-path Terminal command for worktree creation, queueing,
Coder launch, Reviewer launch, commit, or merge. Final acceptance never grants
push permission; successful integration records `pushed: false`.

## Components

- `config.json` is portable versioned policy. It contains no per-task absolute
  worktree path.
- `config.schema.json` and `task-contract.schema.json` document configuration
  and task-contract formats.
- `tasks/` contains approved contracts; `docs/plans/` contains their plans.
- `.opencode/agents/` separates interactive Planner, write-limited Coder, and
  read-only Reviewer permissions.
- `.opencode/skills/scheduled-quality-orchestrator/` defines the three approval
  boundaries; the coder/reviewer skills define their narrower workflows.
- `.opencode/commands/acceptance.md` provides the read-only
  `/acceptance <TASK-ID>` fallback for redisplaying the final review card.
- `scripts/automation/` implements all state, scope, evidence, worktree, and
  integration operations. `show-acceptance-review.sh` verifies the live sealed
  diff and renders the human review focus without changing task state.

Runtime data is shared by all linked worktrees under:

```text
<git-common-dir>/automation-runtime/
├── state/
├── evidence/
├── locks/
└── workspaces/
```

The default task worktree base is a sibling directory named
`<repository>-worktrees`. Absolute paths are discovered at runtime and are not
committed to `config.json`.

## State ownership

```text
CONTRACT_REVIEW → APPROVED_CONTRACT → PREPARING → PENDING → CODING
→ READY_FOR_REVIEW → REVIEWING → AWAITING_HUMAN → INTEGRATING → COMPLETED
```

Hard failures stop in `BLOCKED`, `TEST_FAILED`, `NEEDS_HUMAN`, or
`INTEGRATION_BLOCKED`. Coder and Reviewer cannot create worktrees, commit,
merge, rebase, or push. The integrator cannot update a different branch,
accept a changed diff, skip candidate verification, resolve conflicts
automatically, or push.

## Verification

```bash
./scripts/automation/tests/run-tests.sh
./scripts/automation/shadow-run.sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew lint
```

The shell suite exercises approval rejection, shared runtime state, TDD gates,
diff sealing (including untracked files), reviewer handoff, outside worktree
creation, product commit, candidate verification, original-branch integration,
cleanup, and the no-push invariant.
