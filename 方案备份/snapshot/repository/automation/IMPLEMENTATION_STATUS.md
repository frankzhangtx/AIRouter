# OpenCode orchestration implementation status

Updated 2026-08-19 in `/Users/zhanglong/files/program/cctest`.

## Implemented

- Portable V3 configuration is enabled in `orchestrated` mode with
  `workspaceStrategy: "inPlaceExclusive"` and
  `originalBranchDriftPolicy: "block"`.
- Proposal approval, contract approval, and final acceptance are presented as
  three OpenCode `question` single-select controls. Direct chat messages never
  authorize these normal-path transitions; exact option labels are passed to
  scripts internally only after a fresh selection.
- Contract approval records the original branch/HEAD and artifact hashes but
  does not commit the approved plan or contract. It acquires a persistent
  repository workspace lease, creates a task branch from the unchanged
  pre-task HEAD, and keeps both planning artifacts uncommitted and sealed until
  product integration. The default path creates no additional worktree.
- `isolatedWorktree` remains an explicit configuration fallback. It creates one
  task worktree, but final verification happens there and no second integration
  candidate worktree is created.
- Runtime state, evidence, locks, and workspace metadata live in the Git common
  directory. The persistent lease survives process exit and the human
  acceptance wait, preventing another automation task from taking the same
  repository workspace.
- Coder remains restricted to contract paths and TDD gates. Reviewer remains
  read-only and receives an explicit sealed `REVIEWING` handoff. One bounded
  review-fix cycle is supported.
- `/resume-review <TASK-ID>` resumes only a verified Reviewer interruption from
  the unchanged sealed diff and never reruns Coder.
- Reaching `AWAITING_HUMAN` displays a SHA-verified acceptance card with
  behavior, regression/scope, evidence, live original-branch drift, binding,
  and remaining-risk checks. `/acceptance <TASK-ID>` refreshes the card.
- Final acceptance creates and verifies exactly one combined commit containing
  code, tests, the approved plan, and the task contract in the existing task
  root. If the original branch still equals the pre-task baseline, the
  deterministic integrator fast-forwards it locally; otherwise it stops in
  `INTEGRATION_BLOCKED` without cherry-pick, rebase, conflict resolution, or
  branch modification. After a successful fast-forward it safely deletes the
  integrated local task branch; blocked and failed tasks retain their branch
  for recovery.
- `/abort-task <TASK-ID>` requires a separate exact confirmation, rejects
  out-of-contract changes, and archives diff/recovery evidence. Product changes
  are preserved with the sealed planning artifacts in one recovery commit;
  aborting before product editing creates no planning-only commit. It restores
  the original branch, releases the lease, and records `ABORTED`.
- No script runs `git push`; integration and abort evidence record
  `pushed: false`.

## Verification status

- The deterministic shell suite covers in-place execution, lease exclusion,
  optional isolated execution, TDD/review gates, diff tamper rejection,
  single combined integration commits, drift blocking, planning-only aborts
  without commits, abort recovery, successful task-branch deletion, blocked
  task-branch retention, cleanup, and the no-push invariant.
- Bash syntax and JSON parsing are included in the repository verification
  pass.
- Android verification is performed with standalone Gradle wrapper commands as
  documented in `AGENTS.md`.

## First-use boundary

This automation implementation must be reviewed and committed on the intended
source branch before Planner preflight can pass. Preflight intentionally
rejects a dirty source directory. During an in-place task, the repository lease
and task branch are the visible indication that the project directory belongs
to the automation until completion or explicit abort.
