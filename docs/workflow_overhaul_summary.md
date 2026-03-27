# CI/CD Workflow Overhaul Summary
**Conversation Date:** March 27, 2026

## 1. Initial Objectives
The user requested a review and modernization of the GitHub Workflows for the **MyDeck** Android project. The goals were:
1.  **Transition to Main-First**: Move away from the legacy `develop` branch.
2.  **Define Branching Patterns**: Support `feature/*`, `enhancement/*`, `fix/*`, and `chore/*` branches merging into `main`.
3.  **Automate Quality Checks**: Run Lint and Unit Tests on every push.
4.  **Create "Tester Builds"**: Provide a **Release-optimized** (fast) build that can be installed **side-by-side** with the production version (not overwriting it).

---

## 2. Analysis of Legacy Workflows
We reviewed the following existing workflows:
*   **`build.yml`**: Built Debug APKs (including snapshot flavor) and ran Lint. (Both were Debug builds).
*   **`dev-release.yml`**: Automatically created a GitHub Release for the `develop` branch.
*   **`run-tests.yml`**: Redundantly ran only Unit Tests.
*   **`test-build.yml`**: Manual trigger for a snapshot release artifact.
*   **`release.yml`**: Triggered by version tags (`v*`) for official production releases.

**Finding:** The "snapshot" builds were currently **Debug** builds, which are slower and contain debug tools. The user wanted a production-like experience for their functional testers.

---

## 3. The "Golden Path" Strategy
We agreed on a strategy that provides high-performance testing artifacts while maintaining repository hygiene:

### Branching & Merging
*   **Target**: `main`
*   **Source**: `feature/**`, `enhancement/**`, `fix/**`, `chore/**`
*   **Merge Method**: **Squash and Merge** (to maintain linear, readable history on `main`).

### CI/CD Logic
1.  **Dev-Push**: Every push triggers **Lint + Tests**.
2.  **Pull Request to Main**: Every PR builds a **Release Snapshot** (minified, optimized). This is uploaded as a 7-day GitHub Action artifact for the tester.
3.  **Merge to Main**: Updates a revolving **"MyDeck Snapshot"** release on GitHub with a side-by-side installable APK.
4.  **Official Release**: Tag-triggered (`v*`) builds for the final production install.

---

## 4. Implemented Changes

### File Removals
Deleted redundant/legacy files to prevent trigger collisions:
*   `.github/workflows/build.yml`
*   `.github/workflows/run-tests.yml`
*   `.github/workflows/test-build.yml`
*   `.github/workflows/dev-release.yml`

### New Workflows Created
1.  **`checks.yml`**: Runs `./gradlew :app:lintDebugAll` and `./gradlew :app:testDebugUnitTestAll` on all push events.
2.  **`snapshot.yml`**: 
    *   Builds `:app:assembleGithubSnapshotRelease`.
    *   Uses **Release** build type for production performance.
    *   Uses the **Snapshot** flavor (package ID: `com.mydeck.app.snapshot`) for side-by-side installation.
    *   Handles both PR artifacts and the continuous "Latest Snapshot" GitHub Release.

### Documentation Updated
*   **`docs/WORKFLOW.md`**: Fully rewritten to clearly document the new branch naming, release formulas, and tester build locations.

---

## 5. Comparison to Industry Standards
*   **Standard**: Most teams use **GitHub Flow** (direct to `main`) and **PR Validation**.
*   **Our Setup**: Our "Release Snapshot" setup is a high-performance variant. It provides the **R8-shrunk** performance of a production build while keeping the **Snapshot ID** so users don't have to uninstall their stable copy. It represents a "Safety-First" approach by catching optimizer (R8) errors *before* the PR is merged.

---
**Status:** Completed and Verified.
