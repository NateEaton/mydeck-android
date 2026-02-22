# Development & Release Workflow

## 1. Repository Structure & Hygiene

### Directory Organization
*   **`docs/`**: The single source of truth for project documentation.
*   **`docs/specs/`**: **Active** design documents. Keep specs here while they are under development or pending implementation.
*   **`docs/archive/`**: **Inactive** documents. Move implemented specs, old notes, and superseded designs here. Do not delete them; archive them to preserve context for AI tools.
*   **`CLAUDE.md`**: Keep in **root**. Used for AI project context.

### File Hygiene
*   **Logs**: `*.log` and `*-log.txt` files are for local debugging only. Added to `.gitignore`.
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
*   `chore/short-description` (Maintenance, dependencies, release prep)
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

### Standard Workflow (Feature/Fix)
1.  💻 **Start:** `git checkout -b feature/my-feature main`
2.  💻 **Code:** Work with AI tools. Commit often.
3.  ☁️ **PR:** Push to GitHub. Open Pull Request.
4.  ☁️ **Merge:** **Squash and Merge** into `main`.
5.  ☁️ **Cleanup:** Delete branch on GitHub.
6.  💻 **Cleanup:** Run `git gone` locally.

### Complex Workflow (Integration / Release Candidate)
Use this when you have multiple features (e.g., `feature/A` and `feature/B`) that need to be tested together *before* reaching `main`.

1.  💻 **Create Candidate:** `git checkout -b release-candidate main`
2.  💻 **Merge Foundation:** Merge the most impactful branch first.
    *   `git merge feature/A`
3.  💻 **Merge & Fix:** Merge the second branch.
    *   `git merge feature/B`
    *   *Resolve conflicts here.* Use local AI to adapt Feature B to Feature A's changes.
4.  💻 **Verify:** Test the app thoroughly locally.
5.  ☁️ **Ship:**
    *   Push `release-candidate`.
    *   Open PR (`release-candidate` -> `main`).
    *   **Squash and Merge**.
    *   Commit message: `feat: release 0.9.0 (Feature A + Feature B)`
6.  ☁️ **Cleanup:** Delete `release-candidate`, `feature/A`, and `feature/B`.

---

## 4. Safety Checks & Cleanup

### "Is this safe to delete?"
If you are unsure if a branch is fully merged (because Squash/Merge changes commit IDs), use this command.

*   **Command:** `git diff main..my-old-branch`
    *   **No output?** Code is identical. Safe to delete.
    *   **Green lines?** The branch has unique code. Investigate.

### The Archive Protocol
If you want to delete a branch but keep the history "just in case":
1.  💻 `git tag archive/feature/my-old-feature feature/my-old-feature`
2.  💻 `git push --tags`
3.  💻 `git branch -D feature/my-old-feature`

### The "Git Gone" Alias
Run this once to set up the alias:
```bash
git config --global alias.gone "! git fetch -p && git for-each-ref --format '%(refname:short) %(upstream:track)' | awk '\$2 == \"[gone]\" {print \$1}' | xargs -r git branch -D"
```
**Usage:** Run `git gone` after merging PRs to clean up local branches.

---

## 5. Release Planning

Use GitHub's built-in **Milestones** to track work toward a version target.

1.  ☁️ Go to your repo > **Issues** > **Milestones** > **New Milestone**.
    *   **Title:** `v1.0.0`
    *   **Description:** Brief summary of the release goal.
2.  ☁️ For every task, create a **GitHub Issue** and assign it to the milestone.
3.  `main` is the working branch for the current milestone. It contains the last release plus any completed features. 

> ### What requires a Release?
> 
> `main` may include changes that are not tied to a released APK. A GitHub Release is  only needed to publish a new installable build (tagged vX.Y.Z) for testers/users.
> 
> No release needed: documentation changes (docs/*, README), spec updates, repo hygiene, CI tweaks, formatting/typos, and other non-shipping changes.
> 
> Release recommended: user-visible behavior/UI changes, bug fixes intended for testers/users, dependency/runtime changes, or anything to be distributed as an APK.

---

## 6. The Release Process (vX.Y.Z)

**Crucial:** The version number must be updated in the code *before* it is merged to Main.

### Step 1: Prepare the Release (Local) 💻
1.  Checkout the branch you are about to merge (or create `chore/prepare-vX.Y.Z`).
2.  **Update Version:** Open `app/build.gradle.kts`.
    *   Increment `versionCode` (Integer +1).
    *   Update `versionName` (String "X.Y.Z").
    *   Sync Gradle.
3.  **Commit:** `chore(release): bump version to X.Y.Z`.
4.  **Verify:** Generate a signed APK locally and install it to ensure the "About" screen shows the correct version.

### Step 2: Merge to Main (GitHub) ☁️
1.  Push the branch.
2.  Open/Update PR.
3.  **Squash and Merge** into `main`.

### Step 3: Tag and Publish (Local -> GitHub)
1.  💻 **Sync:** Switch to main and pull the merged code.
    ```bash
    git checkout main
    git pull
    ```
2.  💻 **Tag:** Create the tag locally.
    ```bash
    git tag vX.Y.Z
    ```
3.  💻 **Push Tag:**
    ```bash
    git push origin vX.Y.Z
    ```
4.  ☁️ **Monitor Build:**
    *   Go to **Actions** tab on GitHub.
    *   Watch the "Build and Publish Release" workflow.
    *   When green, a draft Release is created.
5.  ☁️ **Publish:**
    *   Go to **Releases**.
    *   Edit the draft. Add release notes.
    *   Click **Publish release**.
    *   *Distribution:* IzzyOnDroid will detect this and update within ~24 hours.

---

## 7. Hotfix Procedure

Use this when a critical bug needs to be fixed in a released version while `main` has unreleased work.

1.  💻 **Checkout the released tag:**
    ```bash
    git checkout tags/vX.Y.Z
    ```
2.  💻 **Create a hotfix branch from there:**
    ```bash
    git checkout -b hotfix/vX.Y.Z-description
    ```
3.  💻 **Fix the bug.** Update `versionCode` and `versionName` in `app/build.gradle.kts`. Commit.
4.  💻 **Tag and push:**
    ```bash
    git tag vX.Y.Z+1  # e.g., v0.9.1
    git push origin hotfix/vX.Y.Z-description --tags
    ```
5.  ☁️ **Release:** Handle the release on GitHub as usual (monitor build, publish draft).
6.  💻 **Bring the fix forward to `main`:**
    ```bash
    git checkout main
    git cherry-pick vX.Y.Z+1
    ```

---

## 8. CI/CD & Secrets Setup

To enable GitHub Actions to sign your APK, these secrets must be set in **Settings > Secrets and variables > Actions**:

| Secret Name | Value | Description |
| :--- | :--- | :--- |
| `SIGNING_KEY` | `base64 -i my-key.jks \| pbcopy` | The Base64 encoded content of your `.jks` file. |
| `KEY_STORE_PASSWORD` | (User defined) | The password for the keystore file. |
| `ALIAS` | (User defined) | The alias name (e.g., `mydeck`). |
| `KEY_PASSWORD` | (User defined) | The password for the specific key alias. |

**Manual Fallback:**
If CI fails, build locally using **Build > Generate Signed Bundle / APK > APK > `githubReleaseRelease`**. Rename the output file and upload manually to GitHub Releases.