# Scheduler activation

OpenCode Scheduler 1.3.0 is already pinned by the project configuration and
supports an `agent` parameter. The recurring jobs are deliberately **not
installed while `automation/config.json` is disabled**: even a read-only
OpenCode run consumes model time, and the current main worktree is not the
dedicated clean worktree required by V1.

After the activation checklist in `../README.md` passes, open OpenCode from the
dedicated worktree and create these two jobs through the Scheduler plugin:

## Coder job

- name: `scheduled quality coder`
- schedule: `0 2 * * *` (daily 02:00 local time; adjust explicitly if needed)
- prompt: the exact content of `coder-prompt.txt`
- agent: `scheduled-coder`
- workdir: the configured dedicated worktree
- timeoutSeconds: `3600`

## Reviewer job

- name: `scheduled quality reviewer`
- schedule: `30 3 * * *` (daily 03:30 local time; adjust explicitly if needed)
- prompt: the exact content of `reviewer-prompt.txt`
- agent: `scheduled-reviewer`
- workdir: the same dedicated worktree
- timeoutSeconds: `3600`

The fixed prompts contain selector tokens rather than task IDs. The agents must
call `select-task.sh`, which succeeds only when exactly one eligible state file
exists. This prevents the model from choosing among multiple queued tasks.

Before creating recurring jobs, run both prompts manually with:

```bash
opencode run --agent scheduled-coder -- "$(<automation/scheduler/coder-prompt.txt)"
opencode run --agent scheduled-reviewer -- "$(<automation/scheduler/reviewer-prompt.txt)"
```

Do not use `--dangerously-skip-permissions`. The explicit Agent denies are part
of the quality gate.
