---
name: scheduled-quality-coder
description: Use when an unattended OpenCode coder must execute exactly one human-approved Android task under project state, scope, TDD, and quality gates
compatibility: opencode
metadata:
  audience: automation
  workflow: scheduled-coding
---

# Scheduled quality coder

Execute one task contract. This skill narrows Superpowers into a deterministic,
non-interactive Android workflow. The scripts are the source of truth for state;
your prose is never proof of completion.

## Required input

The prompt must contain either one task ID matching `TASK-[A-Z0-9-]+` or the
exact selector token `NEXT_PENDING`. For `NEXT_PENDING`, first run
`./scripts/automation/select-task.sh PENDING`; continue only if it returns one
task ID. Zero or multiple matches are a clean stop, not permission to choose.
The resolved contract must exist at `automation/tasks/<TASK-ID>.json` and must
have `designApproved: true`.

If neither accepted input form is present, stop without editing.
When a blocker is discovered after the task has been queued or claimed, record
it with `./scripts/automation/block-task.sh <TASK-ID> <reason>` before stopping.

## Mandatory sequence

1. Load `test-driven-development` and
   `verification-before-completion`. Do not load any other implementation
   workflow skill.
2. Run `./scripts/automation/status.sh <TASK-ID>` and read the contract.
3. Run `./scripts/automation/claim-task.sh <TASK-ID>`. This command performs
   preflight, requires a clean dedicated worktree, captures the baseline, and
   atomically changes `PENDING` to `CODING`. If it fails, stop.
4. Add or change the smallest behavior test permitted by `allowedPaths`.
5. Capture a genuine RED result with:

   `./scripts/automation/record-red.sh <TASK-ID> <expected-failure-text> -- <test-filter>`

   The final argument after `--` is a Gradle `--tests` filter, not an arbitrary
   command. Confirm the test failed for the missing behavior, not a typo or
   environment error.
6. Implement the minimum product change needed to make that test pass. Stay
   inside the contract's path and file-count limits. Do not refactor unrelated
   code.
7. Run `./scripts/automation/quality-gate.sh <TASK-ID>`.
8. If the first gate attempt fails while state remains `CODING`, load
   `systematic-debugging`, diagnose the root cause, and make at most one fix
   loop. Then run the gate once more. If it fails again, stop in
   `TEST_FAILED`.
9. When the gate succeeds, report the changed files and evidence paths. Do not
   write `READY_FOR_REVIEW`; only the gate script may do that.

## Stop conditions

Stop immediately when any of these occur:

- requirement ambiguity or conflict;
- dirty worktree before claim;
- missing plugin, skill, tool, device, or dependency;
- a requested edit outside `allowedPaths` or inside protected paths;
- no meaningful failing test can be written;
- more than one fix loop would be needed;
- a test must be deleted, ignored, weakened, or changed merely to accept the
  implementation;
- the contract asks for push, merge, rebase, worktree creation, dependency
  upgrades, or automation-rule changes.

Do not ask a question during a scheduled run. State the blocker and stop so a
human can revise and requeue the contract.

## Forbidden capabilities in V1

Do not invoke `brainstorming`, `writing-plans`, `using-git-worktrees`,
`finishing-a-development-branch`, `requesting-code-review`, parallel agents, or
subagent-driven development. Planning and approval happen before scheduling;
review happens in a separate read-only session.
