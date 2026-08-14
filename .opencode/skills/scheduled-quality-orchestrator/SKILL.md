---
name: scheduled-quality-orchestrator
description: Use when the interactive planner must turn one approved request into a sealed contract, run isolated coder/reviewer automation, pause for human acceptance, and integrate locally
compatibility: opencode
metadata:
  audience: interactive-planner
  workflow: end-to-end-coding-orchestration
---

# Scheduled quality orchestrator

Keep the user in one conversational flow while deterministic scripts own every
Git mutation and runtime transition. Human prose grants intent; state files,
hashes, tests, and Git checks grant execution.

## Before planning

1. Run `./scripts/automation/preflight.sh --source` before creating artifacts.
   If `ANDROID_HOME` is missing, the worktree is dirty, the branch is detached,
   Git identity is missing, or OpenCode discovery is unsafe, report the exact
   blocker and stop.
2. Use brainstorming and writing-plans to produce one bounded proposal. Do not
   create files until the user replies exactly:

   `批准方案，生成计划和任务合同。`

## Contract-review boundary

After proposal approval, create only `docs/plans/<TASK-ID>.md` and
`automation/tasks/<TASK-ID>.json`, validate the contract, then run:

`./scripts/automation/prepare-contract-review.sh <TASK-ID> "批准方案，生成计划和任务合同。"`

Show the full plan and the contract's acceptance criteria, allowed paths,
forbidden paths, test policy, file limit, and recorded original branch. Do not
start execution until the user replies exactly:

`合同已复核，批准自动执行到人工验收阶段。`

Then run only:

`./scripts/automation/approve-and-run.sh <TASK-ID> "合同已复核，批准自动执行到人工验收阶段。"`

The command may take time. It commits only the sealed planning artifacts,
creates an outside worktree, launches the restricted Coder, launches a fresh
read-only Reviewer, performs at most the configured review-fix cycle, and stops
at `AWAITING_HUMAN` or a hard failure state. Do not reproduce any of those Git
or agent operations manually.

## Human acceptance boundary

At `AWAITING_HUMAN`, present the generated acceptance package and explain how
the user can exercise the behavior. The acceptance is bound to the task ID,
sealed diff SHA, and recorded original branch. Wait for the exact reply:

`验收通过，提交到原分支。`

Then run only:

`./scripts/automation/accept-and-integrate.sh <TASK-ID> "验收通过，提交到原分支。"`

Report the resulting local branch, integrated commit, verification result, and
`pushed: false`. Never treat acceptance as permission to push.

## Hard stops

- Never manufacture, paraphrase, or infer one of the three approvals.
- Never call an approval script before the matching user message.
- Never directly run `git add`, `commit`, `worktree`, `cherry-pick`, `merge`,
  `rebase`, or `push`.
- Never bypass a blocked state, alter runtime evidence, resolve an integration
  conflict automatically, or broaden a contract after approval.
- For `BLOCKED`, `TEST_FAILED`, `NEEDS_HUMAN`, or `INTEGRATION_BLOCKED`, show
  the state and evidence path and wait for a revised contract or explicit
  recovery action.
