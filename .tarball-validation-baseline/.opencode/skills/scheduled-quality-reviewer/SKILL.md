---
name: scheduled-quality-reviewer
description: Use when an unattended read-only OpenCode reviewer must independently assess one READY_FOR_REVIEW task and submit a gated decision
compatibility: opencode
metadata:
  audience: automation
  workflow: scheduled-review
---

# Scheduled quality reviewer

Review exactly one task from a fresh session. The coder's summary is a claim,
not evidence. You have no repository write authority and must not fix the code
you review.

## Mandatory sequence

1. Load `verification-before-completion`.
2. Require exactly one task ID or the exact token
   `NEXT_READY_FOR_REVIEW`. For the selector token, first run
   `./scripts/automation/select-task.sh READY_FOR_REVIEW` and continue only if
   exactly one task ID is returned. Then run
   `./scripts/automation/status.sh <TASK-ID>`.
3. Continue only when state is `READY_FOR_REVIEW`.
4. Read the approved contract, baseline metadata, RED evidence, gate logs, and
   actual Git diff.
5. Check each acceptance criterion against observable behavior. Inspect for
   regression risk, missing edge cases, out-of-scope changes, test deletion,
   ignored tests, relaxed assertions, and implementation-shaped tests.
6. Decide independently:

   - approve only when the diff is correct and evidence is sufficient;
   - request changes for every material finding, with a concrete file/behavior
     explanation.
7. Submit one decision:

   `./scripts/automation/submit-review.sh <TASK-ID> APPROVED <summary>`

   or

   `./scripts/automation/submit-review.sh <TASK-ID> CHANGES_REQUESTED <summary>`

   The script reruns deterministic verification before accepting `APPROVED`.

## Independence rules

- Never edit code, tests, contracts, agent definitions, skills, scripts, or
  evidence produced by the coder.
- Never dispatch a reviewer subagent; this scheduled session is the independent
  reviewer.
- Never approve because the coder says tests passed. Use fresh command output.
- Never push, merge, rebase, create a worktree, or move beyond
  `AWAITING_HUMAN`.
- If verification cannot run, submit `CHANGES_REQUESTED` with the environmental
  blocker. Do not manufacture approval.
