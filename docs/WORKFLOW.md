# Development & Release Workflow

## 1. Repository Structure & Hygiene

### Branching Strategy
We use a **Main-First** (Trunk-based) workflow.
*   **`main`**: The primary source of truth. Always compilable and represents the last stable set of features.
*   **`feature/*`**: For new functionality.
*   **`enhancement/*`**: For improvements to existing features.
*   **`fix/*`**: For bug fixes.
*   **`chore/*`**: For maintenance, dependency updates, and release preparation.

> [!NOTE]
> The `develop` branch is deprecated and is no longer used for active development.

### Commit Strategy
*   **In Branches:** Commit often. Messy history is fine.
*   **To Main:** Always use **Squash and Merge**. This keeps the `main` history clean and linear.
*   **Conventional Commits:** Use `feat:`, `fix:`, `chore:`, etc., when merging to `main`.

---

## 2. CI/CD & Automation

We use GitHub Actions to automate testing and build delivery.

### Automated Checks (`checks.yml`)
Runs on pushes to `main`, `feature/**`, `enhancement/**`, `fix/**`, `chore/**`, `claude/**`, and `codex/**`, plus Pull Requests targeting `main`.
*   **Tasks:** Runs `:app:assembleDebugAll`, `:app:lintDebugAll`, and `:app:testDebugUnitTestAll` in sequence.
*   **Goal:** Catch compile, lint, and unit-test regressions early without waiting for release packaging.

### Tester Builds & Snapshots (`snapshot.yml`)
To provide a production-like, side-by-side installable APK for functional testing without overwriting your daily-driver app.

1.  **Development Branch Pushes**: Automatically builds a **Release Snapshot** (minified/optimized) and uploads it as a GitHub Actions artifact.
    *   **Use case:** Share the latest branch build with a tester before opening or updating a PR.
2.  **Pull Requests to `main`**: Automatically builds the same **Release Snapshot** and uploads it as a GitHub Actions artifact.
    *   **Side-by-Side:** Installs as `com.mydeck.app.snapshot`.
    *   **Behavior:** Uses the Release build type, so performance and shrinking are closer to production than a Debug APK.
    *   **Usage:** Download from the workflow run's "Artifacts" section, unzip, and install.
3.  **Pushes to `main`**: Automatically rebuilds the Release Snapshot and updates the revolving **"MyDeck Continuous Snapshot"** GitHub release with the latest merged code.
    *   **Direct pushes to `main`:** Treated the same as a post-merge `main` update. The same checks and snapshot packaging still run.
4.  **Manual Trigger**: You can trigger a Snapshot build on any branch from the Actions tab.

### Official Releases (`release.yml`)
Runs when a tag starting with `v` is pushed (e.g., `v0.12.0`).
*   **Build Type:** Official Release (non-debug).
*   **Package ID:** `com.mydeck.app` (standard).
*   **Distribution:** Creates a GitHub Release draft for publication.

---

## 3. The Release Process (vX.Y.Z)

### Step 1: Prep the Release
1.  Create `chore/prepare-vX.Y.Z` from `main`.
2.  Update version in `app/build.gradle.kts`:
    *   `versionCode`: `(major × 1,000,000) + (minor × 1,000) + patch`
    *   `versionName`: `"X.Y.Z"`
3.  Add changelog: `metadata/en-US/changelogs/<versionCode>.txt`.
4.  Commit: `chore(release): bump version to X.Y.Z`.
5.  Open PR -> Merge to `main`.

### Step 2: Tag and Publish
1.  💻 `git checkout main && git pull`
2.  💻 `git tag vX.Y.Z`
3.  💻 `git push origin vX.Y.Z`
4.  ☁️ Finish the draft release on GitHub.

---

## 4. Local Verification
Before pushing, always run:
*   `./gradlew :app:assembleDebugAll` (Compile check)
*   `./gradlew :app:testDebugUnitTestAll` (Logic check)
*   `./gradlew :app:lintDebugAll` (Quality check)
