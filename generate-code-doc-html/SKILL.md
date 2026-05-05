---
name: generate-code-doc-html
description: Generate standalone HTML code explanation documents with annotated source snippets, Chinese explanations, and inline SVG flowcharts for a requested class, function, or feature. Use when the user asks to 给某个类/函数/功能生成代码解释文档, create an HTML explanation page, document execution flow visually, or save explanation docs under a repo folder such as `代码结构文档`.
---

# Generate Code Doc Html

Generate a single-file HTML explanation document for the requested code target. The document should be readable by humans without opening the source code and should include both annotated code and a visual flowchart.

## Output Contract

- Write the final HTML file under the current repository root in `代码结构文档/`.
- If `代码结构文档/` does not exist, create it in the repository root.
- If the repository already contains `代码结构文档/submit-user-input-explained.html`, use it as the primary style and structure reference.
- If that file does not exist, search the repo for `submit-user-input-explained.html`. If still missing, use `assets/explained-doc-template.html` from this skill as the fallback base.
- Keep the output self-contained: inline CSS, inline SVG, no external assets, no JavaScript dependency.

## Workflow

1. Identify the target precisely.

- Confirm whether the request is about a class, a function, or a multi-file feature.
- Resolve the concrete source file paths before writing the document.
- If the request is ambiguous, infer the most likely target from the repo and explain that choice in the final answer.

2. Read enough code to explain real behavior.

- Read the target implementation first.
- Read immediate collaborators: callers, callees, models, registries, validators, executors, and tests.
- Prefer `rg` for discovery and `sed` for focused reads.
- Do not explain code from memory when you can confirm it from the repo.

3. Extract the explanation model.

- Identify responsibilities, inputs, outputs, state changes, side effects, and failure branches.
- Derive the actual control flow that deserves the SVG flowchart.
- For feature-level docs, reduce multiple files into one main execution path plus the critical branch points.
- If the code is large, explain the requested method or the narrowest slice that answers the user's request instead of dumping the full file.

4. Author the HTML page.

- Follow the same visual language as `submit-user-input-explained.html`: warm light palette, rounded cards, hero header, code panel, reading guide, and diagram section.
- Use Chinese prose unless the repository or user context clearly requires English.
- Include a hero section with target name, source path, and document purpose.
- Include an annotated code section. Add explanatory comments for reading only; do not imply that the comments were added to the source file.
- Include a "reading hooks" or "reading guide" section that summarizes the key takeaways before the reader studies details.
- Include an inline SVG flowchart that mirrors the real execution path and labels the major decision branches.
- End the diagram section with a short caption that explains how to read the diagram.

5. Save with predictable naming.

- Use kebab-case output names ending in `-explained.html`.
- For a class, default to `<class-name-kebab>-explained.html`.
- For a function, default to `<function-name-kebab>-explained.html`.
- For a feature, default to a short feature slug such as `routing-session-explained.html`.
- If the user explicitly gives an output filename, follow that instead.

6. Verify before finishing.

- Check that the HTML file exists in `代码结构文档/`.
- Check that it contains `<!DOCTYPE html>`, a `<title>`, and at least one `<svg`.
- Make sure source paths and method names mentioned in the prose match the code you read.

## Content Rules

- Prefer explanation over exhaustiveness.
- Keep the doc anchored to real file paths and real method names.
- Do not claim line numbers unless you verified them from the current file.
- Do not change application source files when the user only asked for a documentation page.
- Do not embed giant raw files. Select the most relevant code slice and annotate that slice.

## References

- Read `references/doc-workflow.md` when you need the detailed checklist for scoping the code, selecting supporting files, and deciding what the flowchart should cover.
- Use `assets/explained-doc-template.html` as a starter when the repo does not already provide a closer HTML sample.
