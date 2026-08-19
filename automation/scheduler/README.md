# Scheduler compatibility prompts

The V3 normal path does not require recurring Scheduler jobs. After contract
approval, `orchestrate-task.sh` launches a task-specific Coder and a fresh
Reviewer immediately and remains in control until `AWAITING_HUMAN` or a hard
failure state.

The two prompt files remain only for diagnostics or a deliberately designed
external schedule:

- `coder-prompt.txt` selects exactly one `PENDING` task.
- `reviewer-prompt.txt` selects exactly one task already handed off to
  `REVIEWING` by the orchestrator.

Do not install both prompts as independent recurring jobs: a timer cannot own
the contract approval, persistent repository lease, transactional task branch,
sealed reviewer handoff, bounded repair cycle, or final integration
transaction. Do not use
`--dangerously-skip-permissions`; agent denies are part of the quality gate.
