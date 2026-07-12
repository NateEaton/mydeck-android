# Claude Code Guidelines for MyDeck Android

This document provides guidelines for AI-assisted development on the MyDeck Android project.

## Usage Efficiency & Token Conservation

**Strict Anti-Bloat Protocol:**
1. **Sequential Only:** Never run tasks in parallel. Execute steps one at a time.
2. **Scout Before Reading:** Before reading a file, use `grep` or `ls -R` to confirm it contains relevant logic.
3. **File Size Limit:** Do not `cat` or read any file over 30KB without explicit permission. Summarize large files using `grep` or `sed` to extract only relevant functions.
4. **Research Mode Constraint:** In "Plan" or "Research" mode, do not read more than 3 files per turn. Present a summary of findings after every 3 files and wait for user direction.
5. **Memory Management:** If a session involves reading more than 10 files, recommend a "Summary & Reset" where you provide a concise report of findings and ask the user to start a fresh chat to clear the context.

## Localization Requirements

**Important:** This project maintains translations for multiple languages. Any code changes that add new string resources must be accompanied by English placeholder strings in all language files.

### When Adding New Strings

1. Add the new string to `app/src/main/res/values/strings.xml` with the English text
2. Add the **same string** with English text as a placeholder to all language-specific files:
   - `app/src/main/res/values-de-rDE/strings.xml` (German)
   - `app/src/main/res/values-es-rES/strings.xml` (Spanish)
   - `app/src/main/res/values-fr/strings.xml` (French)
   - `app/src/main/res/values-gl-rES/strings.xml` (Galician)
   - `app/src/main/res/values-pl/strings.xml` (Polish)
   - `app/src/main/res/values-pt-rPT/strings.xml` (Portuguese)
   - `app/src/main/res/values-ru/strings.xml` (Russian)
   - `app/src/main/res/values-uk/strings.xml` (Ukrainian)
   - `app/src/main/res/values-zh-rCN/strings.xml` (Simplified Chinese)

### Example

If adding a new string:
```xml
<!-- In values/strings.xml -->
<string name="my_new_feature">My new feature text</string>

<!-- In values-fr/strings.xml and all other language files -->
<string name="my_new_feature">My new feature text</string>
```

The English text serves as a placeholder until professional translators provide translations for each language.

### Why This Matters

- **Lint validation:** The build system validates that all strings exist in all language files
- **User experience:** Users in other locales won't see missing string errors
- **Translation ready:** Professional translators can easily identify strings that need translation

---

## Documentation Requirements

**Important:** All user-visible features must be documented in the user guide.

### When Adding or Modifying User-Facing Features

1. Update the relevant user guide file(s) in `app/src/main/assets/guide/en/`:
   - `getting-started.md` — initial setup and authentication
   - `your-bookmarks.md` — bookmark list, cards, layouts, actions, filtering, sorting
   - `reading.md` — article/video/picture view, typography, search, lightbox
   - `organising.md` — favorites, archive, labels, deletion
   - `settings.md` — app settings and preferences

2. Keep documentation:
   - **Clear and concise** — describe what the feature does and how to use it
   - **Action-oriented** — focus on what users can do, not implementation details
   - **Consistent** — match the tone and style of existing documentation
   - **Up-to-date** — update docs in the same commit/PR as the feature change

3. For multi-language support, update only the English (`en`) files. Translations are handled separately.

### Examples of User-Visible Changes Requiring Documentation

- New UI elements (buttons, menus, dialogs)
- Changed interaction patterns (tap vs long-press, swipe gestures)
- New or modified features (filters, sorting, reading modes)
- Behavior changes that affect user workflow (deletion UX, navigation flow)

---

## Changelog Maintenance

**Important:** Every PR commit must update the `## [Unreleased]` section of `CHANGELOG.md` with its user-facing changes, in the same commit as the change. At release time, the release-prep PR moves `[Unreleased]` into a new versioned heading (see `docs/WORKFLOW.md`).

---

## Branching

- **Always verify the current branch before creating a new one.** Run `git branch --show-current` (or `git status`) first. Do not assume the branch the session started on, or the branch a previous task left you on, is the right base.
- **Always branch from `main` (after `git pull`) unless the user has explicitly told you to branch from something else for this task.** Branching off the wrong ref silently inherits whatever is on it — unmerged PR commits, abandoned work, or a stale tip. If a task seems to require branching off something other than `main` (e.g., stacking work on top of an unmerged PR), confirm with the user first before creating the branch.
- This applies whether the new branch is for code, docs, specs, or workflow changes — there is no "small enough to skip the check" case.

---

## Cross-Repo Fetches — Never Import Tags

When adding the sibling repo (MyDeck ⇄ Readeck) as a scratch remote for a port,
ALWAYS configure it to never fetch tags, in the same command that creates it:

    git remote add --no-tags <name> <path>
    git fetch <name>

Never run a bare `git fetch` against a remote that lacks `tagOpt = --no-tags`
(check with `git config remote.<name>.tagOpt`). Git's default fetch auto-follows
tags into the local shared tag namespace, polluting this repo with the sibling's
tags — and `git remote remove` does NOT clean fetched tags up afterward. This
applies to the durable `codeberg` remote too (its `tagOpt` is set locally —
verify before fetching it).

---

## Never Bulk-Delete Refs

Never delete tags or branches by pattern/filter (`grep | xargs git tag -d`,
wildcard `git push --delete`, etc.). If stray refs are found, present the
explicit, complete list of names and get the maintainer's approval before
deleting anything — one literal command, no patterns.

---

## GitHub Interactions

The `gh` CLI is installed and authenticated locally. Use it freely for **read-only** inspection — `gh pr view`, `gh pr list`, `gh pr diff`, `gh run view`, `gh api` GETs, etc. This is the preferred way to verify PR/CI state instead of guessing from local git.

**Do not run `gh` commands that change state in GitHub.** That includes (non-exhaustive): creating, merging, closing, or commenting on PRs and issues; pushing or deleting branches/tags via `gh`; editing releases; running workflows; changing labels/assignees/reviewers.

For any state-changing action, **list the exact command(s) for the user to run**, with a brief note on what each does. The user performs them.

This applies to `git push`, branch/tag deletions, and force pushes as well — propose, don't execute.

**`gh` default repo.** This working copy has both `origin` (`NateEaton/mydeck-android`) and `upstream` (`jensomato/ReadeckApp`). `gh` may resolve the default to the upstream, which causes `gh pr view`, `gh pr list`, `gh run view`, and `gh api repos/...` calls to silently return data from the wrong repo. Always pass `--repo NateEaton/mydeck-android` (or rely on `gh repo set-default NateEaton/mydeck-android` having been run in this working copy). When in doubt, run `gh repo view --json nameWithOwner` and confirm it says `NateEaton/mydeck-android` before trusting `gh` output.

---

## CI/CD — Triggering Snapshot Builds

If your environment does not allow `gh workflow run` (authentication scope, network restrictions, etc.):

1. Push your branch and open a PR as normal.
2. Note in the PR description that a snapshot build is needed and name the branch.
3. The repo maintainer will dispatch **Snapshot Build** against that branch manually from the Actions tab.

Do not fail silently — surface the request in the PR description so the maintainer can act.

---

## Providing Copyable Deliverables (prompts, PR descriptions, discussion posts)

When asked to produce an artifact the user will copy elsewhere — an implementation prompt for another agent, a PR/issue description, a discussion/release post, a commit message, etc. — **always present it as raw markdown inside a fenced code block** so it can be copied verbatim without rendering. Do not render it as normal prose. If the artifact itself contains fenced code blocks, use a longer outer fence (e.g. four backticks) so nesting is preserved.

---

## Other Guidelines

- Follow existing code style and patterns
- Run the aggregate debug verification tasks before committing:
  - `./gradlew :app:assembleDebugAll`
  - `./gradlew :app:testDebugUnitTestAll`
  - `./gradlew :app:lintDebugAll`
- Keep commits focused and well-documented
