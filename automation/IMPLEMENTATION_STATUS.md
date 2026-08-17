# OpenCode orchestration implementation status

Updated 2026-08-17 in `/Users/zhanglong/files/program/cctest`.

## Implemented

- Portable V2 configuration is enabled in `orchestrated` mode; a task no longer
  requires editing and committing an absolute `dedicatedWorktree` value.
- `scheduled-planner` is the single interactive front door and recognizes
  separate proposal, contract-execution, and final-acceptance approvals. After
  proposal approval it automatically displays the sealed contract-review card
  and offers the contract gate through an explicit `question` selection.
- The new `scheduled-quality-orchestrator` skill prevents Planner from
  performing direct Git mutations while allowing the two audited transaction
  scripts.
- Runtime state, evidence, locks, and workspace metadata live in the Git common
  directory so the original, task, and integration worktrees share one source
  of truth.
- Contract approval records the original branch/HEAD and artifact hashes,
  commits only the plan and contract, creates an outside task worktree, and
  launches Coder/Reviewer sessions until a hard stop or `AWAITING_HUMAN`.
- Coder remains restricted to contract paths and TDD gates. Reviewer remains
  read-only and receives an explicit sealed `REVIEWING` handoff. One bounded
  review-fix cycle is supported.
- Reaching `AWAITING_HUMAN` actively displays a SHA-verified acceptance card
  with prioritized behavior, regression/scope, evidence, binding, and
  remaining-risk checks, followed by an explicit three-option `question`.
  `/acceptance <TASK-ID>` and `show-acceptance-review.sh` redisplay the same
  card without changing state.
- Final acceptance is bound to task ID, sealed diff SHA, and original branch.
  Product changes are committed on the task branch, applied and verified in a
  candidate worktree, then fast-forwarded into the recorded original branch.
- Successful cleanup removes temporary worktrees but preserves Git commits and
  evidence. No script runs `git push`; integration evidence records
  `pushed: false`.

## Verification status

- The deterministic shell suite covers 31 positive and negative checks,
  including a complete temporary-repository integration flow.
- Bash syntax and JSON parsing are included in the repository verification
  pass.
- With `ANDROID_HOME=/Users/zhanglong/Library/Android/sdk`, the standalone
  `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`, and
  `./gradlew lint` commands all completed with `BUILD SUCCESSFUL`.

## First-use boundary

The current automation implementation itself must first be reviewed and
committed on the intended source branch. Planner preflight intentionally
rejects a dirty source worktree, because later it must distinguish the newly
generated plan/contract from unrelated edits. No recurring Scheduler job is
needed for the normal V2 flow.
