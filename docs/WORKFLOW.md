# Development & Release Workflow

## 1. Repository Structure & Hygiene

### Directory Organization
*   **`docs/`**: The single source of truth for project documentation.
*   **`docs/specs/`**: **Active** design documents. Keep specs here while they are under development or pending implementation.
*   **`docs/archive/`**: **Inactive** documents. Move implemented specs, old notes, and superseded designs here. Do not delete them; archive them to preserve context for AI tools.
*   **`CLAUDE.md`**: Keep in **root**. Used for AI project context.

### File Hygiene
*   **Logs**: `*.log` and `*-log.txt` files are for local debugging only. Add to `.gitignore`.
*   **Dead-end Branches**:
    *   If a branch is a failed experiment you want to remember: **Tag it, then delete the branch.**
    *   *Tag Pattern:* `archive/branch-name` (e.g., `archive/feature/failed-experiment`)

---

## 2. Branching & Commits

### Branch Naming Conventions
Use lowercase with hyphens.
*   `feature/short-description` (New functionality)
*   `enhancement/short-description` (Improvements to existing features)
*   `fix/short-description` (Bug fixes)
*   `chore/short-description` (Maintenance, dependencies)
*   `release-candidate` (Temporary integration branch)

### Commit Strategy
*   **In Branches:** "Messy" commits are fine (`wip`, `try again`, `oops`).
*   **Merging to Main:** Always use **Squash and Merge**.
    *   This compresses your messy branch history into **one** clean commit on `main`.

### Conventional Commits (The "Main" Branch Rule)
When squashing and merging, rename the final commit message to follow this format:
`type(scope): description`

*   `feat(ui): add material 3 styling`
*   `fix(db): resolve migration crash`
*   `chore(release): bump version to 0.9.0`

---

## 3. The Development Loop

### Standard Workflow (Single Feature)
1.  **Start:** `git checkout -b feature/my-feature main`
2.  **Code:** Work with AI tools. Commit often.
3.  **PR:** Push to GitHub. Open Pull Request.
4.  **Merge:** Squash and Merge into `main`.
5.  **Cleanup:** Delete branch on GitHub. Run `git gone` (see alias below) locally.

### Complex Workflow (Multiple Features / Release Candidate)
Use this when you have multiple features (e.g., `feature/A` and `feature/B`) that need to be tested together before reaching `main`.

1.  **Create Candidate:** `git checkout -b release-candidate main`
2.  **Merge Foundation:** Merge the most impactful branch first (e.g., UI Refactors).
    *   `git merge feature/A`
3.  **Merge & Fix:** Merge the second branch.
    *   `git merge feature/B`
    *   *Resolve conflicts here.* Use local AI to adapt Feature B to Feature A's changes.
4.  **Verify:** Test the app thoroughly.
5.  **Ship:**
    *   Push `release-candidate`.
    *   **Squash and Merge** `release-candidate` into `main`.
    *   Commit message: `feat: release 0.9.0 (Feature A + Feature B)`
6.  **Cleanup:** Delete `release-candidate`, `feature/A`, and `feature/B`.

---

## 4. Safety Checks & Cleanup

### "Is this safe to delete?"
If you are unsure if a branch is fully merged (because Squash/Merge changes commit IDs), use this command.

*   **Command:** `git diff main..my-old-branch`
    *   **No output?** Code is identical. Safe to delete.
    *   **Green lines?** The branch has unique code. Investigate.

### The Archive Protocol
If you want to delete a branch but keep the history "just in case":
1.  **Tag it:** `git tag archive/feature/my-old-feature feature/my-old-feature`
2.  **Push Tag:** `git push --tags`
3.  **Delete Branch:** `git branch -D feature/my-old-feature`

### The "Git Gone" Alias
Run this once to set up the alias:
```bash
git config --global alias.gone "! git fetch -p && git for-each-ref --format '%(refname:short) %(upstream:track)' | awk '\$2 == \"[gone]\" {print \$1}' | xargs -r git branch -D"
```
**Usage:** Run `git gone` after merging PRs to clean up local branches.

---

## 5. Release Process

### Versioning (Semantic)
Before tagging a release, update `app/build.gradle.kts`:
1.  **`versionCode`**: Increment by +1 (Integer).
2.  **`versionName`**: Use SemVer (`X.Y.Z`).

### Shipping
1.  **Commit:** `chore(release): bump version to 0.9.0`
2.  **Tag:** `git tag v0.9.0`
3.  **Push:** `git push origin v0.9.0`
    *   *Automation:* GitHub Actions will build the signed APK and create a Release.
    *   *Distribution:* IzzyOnDroid (if configured) will pick up the release automatically.