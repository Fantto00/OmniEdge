---
name: git-workflow
description: Commit and push each completed logical code change in this repository. Use whenever Codex implements a feature, fixes a bug, refactors code, changes tests, modifies build tooling, or makes standalone documentation changes; create an English Conventional Commit message, stage only the current change, commit it, and push it to the configured GitHub remote.
---

# Git Workflow

Apply this workflow after each completed, self-contained change. A change may be a feature slice, a bug fix, a focused refactor, a test addition, a build/tooling update, or a standalone documentation update. Do not defer all Git work until the end of a multi-step task.

## 1. Define the commit boundary

Use one commit for one independently reviewable and reversible behavior change. Include directly related production code, tests, resources, and documentation in that commit. Split unrelated changes into separate commits.

Before staging, run:

```powershell
git status --short
git diff -- <paths-touched>
```

Preserve pre-existing user changes. Identify the files created or modified for the current change and stage only those paths. Never use `git add .`, `git add -A`, or `git commit -a` in a dirty worktree.

If a current change overlaps an unowned user modification, stop and ask for direction rather than staging it accidentally.

## 2. Verify before committing

Run the smallest relevant verification before each code commit. For Android production changes, prefer the checks required by the project `AGENTS.md`:

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Run a narrower relevant command when the full suite is disproportionate, and run focused tests for the modified behavior when available. Inspect failures before committing.

Do not commit or push a known regression. If verification is blocked by a clearly pre-existing or environmental failure, report the command and evidence accurately, attempt a safe targeted alternative, and ask before committing code that cannot be verified.

## 3. Stage and inspect the exact change

Stage only the files belonging to the completed change:

```powershell
git add -- <path-1> <path-2>
git diff --cached --check
git diff --cached
```

Confirm the staged diff contains no credentials, generated build output, model binaries, user media, or unrelated edits. Unstage an accidentally staged path with:

```powershell
git restore --staged -- <path>
```

Do not alter, discard, or stage any other worktree changes.

## 4. Commit with an English Conventional Commit message

Use exactly one of these lower-case types followed by a colon, a space, and a concise English imperative summary:

| Type | Use for | Example |
|---|---|---|
| `feat` | New user-visible capability | `feat: add image OCR ingestion` |
| `fix` | Bug correction | `fix: reopen cached URL input stream` |
| `docs` | Documentation-only change | `docs: add multimodal development guide` |
| `style` | Formatting-only change with no runtime effect | `style: format document import code` |
| `refactor` | Code restructuring without feature or bug behavior | `refactor: isolate content ingestion orchestration` |
| `test` | Tests only | `test: cover Chinese text chunking` |
| `chore` | Build, dependency, tooling, or auxiliary maintenance | `chore: configure objectbox test task` |

Commit with the selected message:

```powershell
git commit -m "<type>: <concise English summary>"
```

Do not use generic messages such as `update`, `fix bug`, `changes`, or `wip`. Do not amend, squash, rebase, reset, or rewrite history unless the user explicitly asks.

## 5. Push immediately after a successful commit

Confirm the current branch and push remote before pushing:

```powershell
git branch --show-current
git remote get-url --push origin
git push origin HEAD
```

Push every successful commit immediately. If the push is rejected, fails authentication, or fails network delivery, do not force-push, retry with destructive history changes, or claim the commit reached GitHub. Report the local commit SHA, branch, exact failure, and the remaining safe next step.

## 6. Confirm the clean boundary

After a successful push, run:

```powershell
git status --short
git log -1 --oneline
```

Report the commit SHA, message, branch, push result, verification evidence, and any intentionally preserved user changes. Do not create branches, pull requests, tags, or releases unless the user separately requests them.
