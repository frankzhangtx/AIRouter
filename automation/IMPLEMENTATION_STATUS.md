# Scheduled coding quality gate implementation status

Verified on 2026-08-09 in `/Users/zhanglong/files/program/cctest`.

## Implemented and verified

- Project-level OpenCode configuration pins `opencode-scheduler@1.3.0` and
  `obra/superpowers` `v6.2.0`.
- OpenCode downloaded the pinned Superpowers package and discovered the two
  project wrapper skills plus `test-driven-development`,
  `systematic-debugging`, and `verification-before-completion`.
- `scheduled-planner`, `scheduled-coder`, and `scheduled-reviewer` resolve as
  separate primary agents. The planner accepts a natural-language request,
  requires interactive C0 approval, and can write only plan/contract files.
- Both agents default every unlisted tool to `deny`; Scheduler management tools
  and subagents are disabled. The coder can edit only Android source/test
  paths, while the reviewer cannot edit any repository file.
- Contract validation, atomic state transitions, deterministic task selection,
  clean-worktree preflight, baseline capture, RED capture, scope/test-integrity
  checks, bounded verification retry, quality gates, diff sealing, and
  independent review submission are implemented under `scripts/automation/`.
- The shell test suite passes 16 lifecycle and negative cases.
- `./gradlew testDebugUnitTest assembleDebug lint` completes with
  `BUILD SUCCESSFUL`.
- The shadow preflight discovers plugins, skills, and agents, reports current
  blockers, and performs no mutation.

## Intentionally not activated

`automation/config.json` remains:

```json
{
  "enabled": false,
  "mode": "shadow",
  "dedicatedWorktree": ""
}
```

No new recurring Scheduler job has been installed. This is required because:

1. the current main worktree contains pre-existing/user changes;
2. the new automation implementation has not yet been committed into a clean
   baseline;
3. no real human-approved task contract exists;
4. no dedicated worktree path or preferred schedule has been approved.

The two pre-existing Scheduler jobs (`daily-version-log` and
`daily-unit-test`) were left unchanged.

## Activation boundary

Activation is a separate human-controlled step. Follow `README.md` and
`scheduler/README.md` only after establishing a clean committed baseline. Do
not bypass this boundary by setting `enabled: true` in the current dirty
worktree or by using `--dangerously-skip-permissions`.
