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
*   **Changelog Maintenance:** Every PR/merge to `main` records its user-facing changes in the `## [Unreleased]` section of `CHANGELOG.md` in the same commit. (At release time, Step 1 of the release process moves `[Unreleased]` into the new version heading.)

---

## 2. CI/CD & Automation

We use GitHub Actions to automate testing and build delivery.

### Quality Checks (`checks.yml`)
Runs on pushes to `main`, Pull Requests targeting `main`, and manual dispatch.
*   **Tasks:** Runs `:app:assembleDebugAll`, `:app:lintDebugAll`, and `:app:testDebugUnitTestAll` in sequence.
*   **Goal:** Catch compile, lint, and unit-test regressions early without waiting for release packaging.
*   Plain development branch pushes (e.g., `feature/*`, `fix/*`) do **not** trigger this workflow automatically — use a PR or manual dispatch.

### Snapshots (`snapshot.yml`)
Provides a side-by-side installable APK (`com.mydeck.app.snapshot`) for functional testing without overwriting the production app. Four trigger paths:

1.  **Pull Requests to `main`**: Builds a **debug-signed APK** (`assembleGithubSnapshotDebug`) and uploads it as a workflow artifact with 30-day retention. No release keystore is decoded; no signing secrets are in scope for PR runs.
    *   **Install limitation:** The PR debug APK and the `latest-snapshot` release-signed APK share the same `applicationId` but different signing keys. They cannot coexist on a device — uninstall the existing snapshot first. Both install alongside the production app.
2.  **Push to `main`**: Builds a **release-signed snapshot** and updates the single moving `latest-snapshot` GitHub prerelease. The release body identifies the source commit.
3.  **Scheduled nightly** (07:23 UTC daily): Same as a main push — updates `latest-snapshot`. Only runs in `NateEaton/mydeck-android`; forks do not inherit this schedule.
4.  **Manual dispatch** (Actions → Snapshot Build → Run workflow): Build any branch with `build_type: debug` (default) or `release`. The APK appears in the workflow run's Artifacts section with 7-day retention.

### Releases (`release.yml`)
Runs when a tag matching `v*` is pushed.
*   **Build Type:** Release-signed, `com.mydeck.app`.
*   **Pre-release detection:** Tags containing a hyphen (e.g., `v0.13.2-rc.1`, `v0.13.2-beta.1`) create a draft GitHub Release marked `prerelease: true`. Tags without a hyphen (e.g., `v0.13.2`) create a draft marked `prerelease: false`.
*   **Artifacts:** APK + `checksums.txt` attached to the draft release.
*   All draft releases require a manual **Publish release** click after review.

### CI/CD Scenarios

Every common situation, what CI does, and where to find the artifact.

#### Local feature work, no PR yet

You're iterating on a `feature/*` (or `enhancement/*`, `fix/*`, `chore/*`) branch and want a tester to install your build.

1. Commit and push the branch. **Nothing in CI runs automatically.** This is intentional — branch pushes used to create skipped workflow runs that cluttered the Actions page.
2. Go to **Actions → Snapshot Build → Run workflow** and select your branch.
3. Choose `build_type`: `debug` (default, debug-signed) or `release` (release-signed snapshot APK).
4. When the run finishes, the APK is in the workflow run's **Artifacts** section. Download, unzip, install. Retention: 7 days (manual dispatch path). PR-triggered builds use 30 days — see "Opening a PR" below.

#### Opening (or updating) a PR to `main`

Includes draft PRs.

- **`Quality Checks` runs**: assembles all debug variants, lint, unit tests.
- **`Snapshot Build` runs in PR mode**: builds the **debug-signed APK** (`assembleGithubSnapshotDebug`, signed with the auto-generated Android debug keystore) and uploads it as a workflow artifact. No release keystore is decoded; no release-signing secrets are exposed. Retention: 30 days.
- Where to find the APK: the PR's **Checks** tab or the workflow run's **Artifacts** section.
- **Install limitation:** the PR debug APK and the `latest-snapshot` release-signed APK share the same `applicationId` (`com.mydeck.app.snapshot`) but use different signing keys. They cannot coexist on a device. To install a PR APK, first uninstall any existing `latest-snapshot` install. Both still install alongside the production app (`com.mydeck.app`).

#### PR from a fork

Same as above. Forked PRs do not receive repository secrets from GitHub by default; since we are not signing on PRs anyway, this is safe. Debug APK shows up as a workflow artifact like any other PR.

#### PR merges to `main`

- **`Snapshot Build` runs in main-push mode**: builds a **signed release snapshot** APK and updates the single moving `latest-snapshot` GitHub prerelease.
- Release body identifies the source, e.g. `**Source:** main push @ abc1234 — 2026-05-27 14:02 UTC`.
- Where to find the APK: Releases tab → `latest-snapshot`.

#### Scheduled nightly build

- Runs daily at **07:23 UTC**. GitHub Actions cron is UTC and does not follow DST, so the local equivalent shifts by an hour twice a year (≈ 01:23 CST / 02:23 CDT). The UTC time is the stable one.
- Builds a signed release snapshot from `main` and updates the same `latest-snapshot` prerelease.
- Release body identifies the source as nightly, e.g. `**Source:** nightly @ abc1234 — 2026-05-27 07:23 UTC`.
- The release name and tag do not change. There is **no** separate `latest-nightly` release — `latest-snapshot` is the one moving prerelease.
- Only runs in `NateEaton/mydeck-android`. Forks that enable Actions do not inherit the scheduled build.

#### Pushing a pre-release tag

Tag pattern: `vX.Y.Z-<anything>`, e.g. `v0.13.2-rc.1`, `v0.13.2-beta.1`.

- **`Release Build` runs**: builds a signed release APK, computes `prerelease: true` from the tag (any hyphen after the version), creates a **draft GitHub Release** named `MyDeck Release vX.Y.Z-...`.
- Artifacts attached to the release: APK + `checksums.txt`.
- The release is created as a **draft**. Review the release notes, then click **Publish release** when ready.

#### Pushing a final release tag

Tag pattern: `vX.Y.Z` with no hyphen, e.g. `v0.13.2`.

- Same as the pre-release flow, but the draft release is marked `prerelease: false` (final).
- Still a draft until you publish.

#### Tagging the wrong commit

`Release Build` will still run — the workflow trusts the tag. Catch it during the draft review and either delete the tag (and re-push to the correct commit) or edit the draft release.

If you re-push a tag after deleting it, the workflow runs again, but the release-creation step is configured with `allowUpdates: false`, so it will fail rather than silently overwrite the existing draft. Delete the previous draft first.

#### Force-pushing a feature branch

Allowed. Branch protection only protects `main`. Under the new trigger model, plain branch pushes do not start any CI run, so there is usually nothing to cancel. The `concurrency` block only matters when a run is already in flight from another trigger on the same ref — typically an open PR (pushes to a branch with an open PR re-trigger `pull_request` runs that share a concurrency group keyed on the PR number) or a manual `workflow_dispatch`. In those cases the in-flight run is cancelled in favor of the new commit.

#### Pushing directly to `main`

Blocked by the branch protection ruleset. All changes to `main` go through a PR (squash-and-merge). If you genuinely need to bypass — emergency hotfix, broken CI, etc. — and you've added yourself to the ruleset's bypass list, the push is allowed but logged in the audit log.

#### AI agent (Claude Code, Codex cloud) cannot trigger a workflow itself

If the agent's sandbox does not allow `gh workflow run`, the convention is:

1. The agent pushes its branch and opens a PR as normal.
2. The agent notes in the PR description that a snapshot build is needed and names the branch.
3. A maintainer dispatches **Snapshot Build** against that branch manually.

This is documented in `AGENTS.md` and `CLAUDE.md` so the agent knows to ask rather than fail silently.

---

## 3. The Release Process (vX.Y.Z)

### Step 1: Prep the Release
1.  Create `chore/prepare-vX.Y.Z` from `main`.
2.  Update version in `app/build.gradle.kts`:
    *   `versionCode`: `(major × 1,000,000) + (minor × 1,000) + patch`
    *   `versionName`: `"X.Y.Z"`
3.  Add changelog: `metadata/en-US/changelogs/<versionCode>.txt`.
4.  Update `CHANGELOG.md`: move items from `[Unreleased]` into a new `## [X.Y.Z] - YYYY-MM-DD` section.
5.  Commit: `chore(release): bump version to X.Y.Z`.
6.  Open PR -> Merge to `main`.

### Step 2: Pre-release Validation

Before tagging, install the `latest-snapshot` build (updated automatically on merge to `main`) and run the smoke test below against **both** your day-to-day stable Readeck instance and a freshly pulled Readeck nightly build container. The nightly test catches server-side API changes before they reach users.

#### Smoke test

| Area | What to verify |
|---|---|
| **Authentication** | Sign out; complete the full device-auth flow (enter server URL → get code → confirm in browser → app lands on bookmark list) |
| **Bookmark list** | List loads; switch between card, compact, and grid layouts; pull-to-refresh works |
| **Filtering & sorting** | Apply a label filter, the Unread filter, and the With Errors filter; confirm counts match expectations |
| **Add bookmark** | Share a URL from the browser; confirm it appears in the list after sync |
| **Reading** | Open an article; scroll; use the in-app reader; open original page via external browser |
| **Organising** | Toggle favourite; archive a bookmark; apply and remove a label via the quick-edit button |
| **Settings** | Toggle at least one UI setting (e.g. show/hide add button) and confirm it takes effect |

If any step fails against the nightly build, determine whether it is a server regression or an app compatibility issue before tagging.

### Step 3: Tag and Publish
1.  💻 `git checkout main && git pull`
2.  💻 `git tag vX.Y.Z`
3.  💻 `git push origin vX.Y.Z`
4.  ☁️ Finish the draft release on GitHub.

`release.yml` derives prerelease vs. final from the tag: any hyphen after the version (e.g. `v0.13.2-rc.1`) creates a draft **prerelease**; a plain `vX.Y.Z` creates a draft **final release**. Both are drafts until you publish them by hand.

### Optional: Release Candidate Process

Most releases skip RCs. Cut one when:
*   You have testers other than yourself who need a stable build to validate against.
*   The release is large or risky (schema migration, sync rework, multi-feature drop).
*   You want a soak period — let the build run on real devices for days before locking it in.

If none of those apply, tag final and ship. `latest-snapshot` already acts as a continuous candidate for solo testing.

**The RC process is a modification of the standard flow, not a replacement.** Steps that change:

*   **Step 1 (Prep the Release):** unchanged. Bump to the **target** version (e.g. `versionName = "0.13.2"`, `versionCode = 13002`). The `-rc.N` suffix lives only in the git tag, never in `build.gradle.kts` or the changelog.

*   **Step 3 (Tag and Publish):** replaced with the loop below.

#### Step 3 (RC variant): Iterate, Then Promote

1.  💻 `git checkout main && git pull`
2.  💻 `git tag vX.Y.Z-rc.1`
3.  💻 `git push origin vX.Y.Z-rc.1` — `release.yml` produces a draft **prerelease** with the APK and `checksums.txt`.
4.  📱 Install the RC APK. Run the smoke test above against both stable and nightly Readeck builds.
5.  If issues are found: fix on `main` via the normal PR flow, then cut the next RC from the new tip (`vX.Y.Z-rc.2`, push, retest). Each RC produces its own independent draft prerelease.
6.  When an RC is accepted, **tag the final from the same commit as that RC**:
    *   💻 `git tag vX.Y.Z <commit-sha-of-accepted-rc>`
    *   💻 `git push origin vX.Y.Z`
    *   This produces a final-release draft that is byte-identical to the tested RC.
7.  ☁️ Finish the final draft on GitHub. Delete or leave the RC prerelease drafts as preferred.

**Caveat — versionCode across RCs.** All RCs and the final release built from a single version bump report the same `versionCode` (e.g. `13002`) on the device. Practical consequences:
*   `rc.2` will not install as an upgrade over `rc.1` — Android refuses (same versionCode). Testers must uninstall the prior RC first.
*   Testers cannot distinguish RC builds from the final in the app's "About" screen; they look identical except by install date or APK checksum.

For a small tester pool this is friction, not a blocker. If RC iteration becomes painful for a larger pool, bump `versionCode` manually per RC in a short version-bump PR before each RC tag.

---

## 4. Local Verification
Before pushing, always run:
*   `./gradlew :app:assembleDebugAll` (Compile check)
*   `./gradlew :app:testDebugUnitTestAll` (Logic check)
*   `./gradlew :app:lintDebugAll` (Quality check)
