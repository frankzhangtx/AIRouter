# Human-approved automation plans

Every scheduled coding contract must reference one plan in this directory.
Planning is interactive and happens before a task is queued; unattended agents
must not create, broaden, or reinterpret these plans.

A plan should contain:

- one observable behavior change;
- acceptance criteria and edge cases;
- allowed and forbidden repository paths;
- the focused test class/filter that will provide RED evidence;
- explicit non-goals;
- device/emulator requirements, if any;
- a dated human approval statement.

The matching contract belongs in `automation/tasks/TASK-<ID>.json`. A missing
plan, placeholder text, or mismatched task ID causes contract validation to
fail.
