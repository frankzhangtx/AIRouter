# Scheduled coding quality gate V1

This directory implements the state and evidence layer described in
`代码结构文档/scheduled-coding-quality-gate-explained-v1.html`.

The implementation is intentionally installed in **shadow mode**:

- `automation/config.json` has `enabled: false` and `mode: "shadow"`;
- there is no active task contract;
- no launchd job is installed by the repository files;
- no scheduled job may edit code until a dedicated clean worktree exists and a
  human-approved task is queued.

## Components

- `../opencode.json` pins OpenCode Scheduler 1.3.0 and Superpowers 6.2.0 for
  this project.
- `../.opencode/agents/` separates the write-capable coder from the read-only
  reviewer.
- `../.opencode/skills/` wraps the general Superpowers workflow in project
  rules.
- `tasks/` contains versioned, human-approved JSON contracts.
- `state/`, `evidence/`, and `locks/` are ignored runtime directories.
- `../scripts/automation/` contains deterministic state and quality gates.

JSON contracts are used instead of YAML because this machine already provides
`jq`; this keeps scheduled parsing deterministic without adding `yq`.

## Safe activation order

1. Verify plugin and agent discovery:

   ```bash
   opencode debug config
   opencode debug skill
   opencode debug agent scheduled-coder
   opencode debug agent scheduled-reviewer
   ```

2. Run script tests and the shadow preflight:

   ```bash
   ./scripts/automation/tests/run-tests.sh
   ./scripts/automation/shadow-run.sh
   ```

3. Commit or otherwise establish an immutable baseline containing these
   automation files. Do not use the current dirty main worktree for scheduling.

4. Create a dedicated worktree outside this repository directory and set its
   absolute path in `config.json`.

5. Copy `TASK-TEMPLATE.json.example` to `tasks/TASK-<ID>.json`, replace every
   placeholder, obtain human design approval, and validate it:

   ```bash
   ./scripts/automation/validate-contract.sh TASK-<ID>
   ```

6. In the dedicated worktree only, change `enabled` to `true` and `mode` to
   `active`, then queue the task:

   ```bash
   ./scripts/automation/queue-task.sh TASK-<ID>
   ```

7. Manually invoke coder and reviewer once before creating recurring Scheduler
   jobs. Use the prompts under `scheduler/` and pass the matching OpenCode
   agents with `--agent scheduled-coder` and `--agent scheduled-reviewer`.

8. Create Scheduler jobs only after the manual success and intentional-failure
   drills pass. Keep push and merge manual in V1.

## State ownership

`APPROVED_CONTRACT -> PENDING` is a human queue action. The coder launcher owns
`PENDING -> CODING`. Only `quality-gate.sh` can produce
`READY_FOR_REVIEW`. Only the independent review submission can produce
`AWAITING_HUMAN`. There is no automated transition after that state.

Runtime transitions are append-only in `evidence/<TASK-ID>/transitions.jsonl`.
State, locks, and generated evidence are local runtime data and are not intended
for source control.
