---
name: firstskilltest
description: Use this skill when the user wants to create a folder in the current project based on an input name. It creates exactly one directory under the current working project directory and fails clearly if the name is missing or the folder already exists.
---

# First Skill Test

Use this skill when the task is: "create a folder in the current project from a provided name."

## Workflow

1. Confirm the target folder name from the user's input.
2. Run `scripts/create_folder.sh "<name>"` from the project root.
3. Report the created folder path, or report why creation was skipped.

## Rules

- Treat the current working directory as the project root.
- Create only one folder per invocation unless the user explicitly asks for more.
- Do not create nested paths from raw user input.
- If the folder already exists, tell the user instead of overwriting anything.
- If the name is empty after trimming, stop and ask for a valid name.

## Script

Use the bundled script for deterministic behavior:

```bash
bash firstskilltest/scripts/create_folder.sh "<name>"
```
