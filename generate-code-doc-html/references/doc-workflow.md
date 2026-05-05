# Code Explanation HTML Workflow

Use this checklist when creating an explanation page.

## 1. Scope the target

- Class request: explain class role, important fields, public methods, and the one or two flows that matter most.
- Function request: explain the exact function, its inputs, early returns, state mutation, emitted effects, and downstream calls.
- Feature request: explain the cross-file path that a user action takes through the system.

## 2. Build code context

Read in this order:

1. Target file
2. Direct callers
3. Direct dependencies used in the main path
4. Tests that lock in behavior

Stop expanding when the behavior is already defensible. Do not turn a narrow documentation task into a full codebase audit.

## 3. Decide what code to quote

- Prefer one focused excerpt over an entire file.
- Keep the excerpt large enough to preserve control flow.
- Insert explanation comments directly inside the HTML code block, not into the source file.
- If a feature spans multiple methods, use the highest-value excerpt and summarize the rest in prose cards.

## 4. Decide what the flowchart should show

Choose one primary flow:

- Request entry to completion
- Planner decision path
- Registry lookup and fallback path
- Validation success/failure path

The flowchart should mirror actual code branches, not a vague architecture diagram, unless the user explicitly asked for architecture.

## 5. Keep the HTML structure stable

Default sections:

1. Hero
2. Annotated code panel
3. Reading guide / summary cards
4. SVG flowchart
5. Diagram caption

Optional additions:

- Related collaborators
- Input/output table
- Fallback behavior section
- Multi-file call chain summary

## 6. Naming and output

- Save into repo-root `代码结构文档/`.
- Use kebab-case file names ending with `-explained.html`.
- Reuse existing naming patterns in the repository when a nearby document already sets the convention.

## 7. Final verification

- The target name in the title matches the code.
- The hero mentions the real source file.
- The code excerpt still parses as code visually after annotation.
- The SVG has labels for decision branches.
- The explanation distinguishes read-only comments from source code.
