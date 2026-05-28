# Spec: GitHub Actions Release Lanes for v0.13.2

## Status

Proposal accepted 2026-05-27. Implementation complete.

- [x] Phase 1 — Cleanup + PR signing fix (merged 2026-05-27, PR #168)
- [x] Phase 2 — Snapshot rework (merged 2026-05-27, PR #169)
- [x] Phase 3 — Release candidate handling + SHA pins (merged 2026-05-28, PR #172)
- [x] Phase 4 — Branch protection, Dependabot, docs (this PR)

## Context

The current GitHub Actions setup is functional, but it has grown around two
different needs:

- Remote AI coding environments needed an easy way to trigger Android builds,
  because they could edit code but could not reliably build the app.
- The project now has a more mature local development and release process, and
  the next release cycle needs a cleaner path for pre-release builds.

There are three active workflows:

- `.github/workflows/checks.yml`
- `.github/workflows/snapshot.yml`
- `.github/workflows/release.yml`

`checks.yml` and `snapshot.yml` both list many branch prefixes under `push`, but
then use job-level `if` conditions so most non-main push runs create a workflow
run with skipped jobs. This works, but it adds noise to the Actions page.

Separately, `snapshot.yml` currently triggers on `pull_request` and decodes the
release signing keystore on every PR push (the default `build_type` is `release`,
which takes the signed code path). For same-repo PRs — the common case for this
project, including AI-agent branch PRs — this exposes signing secrets on every
PR run. Forked PRs do not get secrets, but same-repo PRs do. This is a
present-tense issue, not a future contributor-readiness concern.

The repository does not yet have many external contributors, but the workflow
model should be ready for forked PRs and non-AI contributor branches before that
becomes common.

## Current State

### Quality Checks

`checks.yml` triggers on:

- pushes to `main`
- pushes to `feature/**`, `enhancement/**`, `fix/**`, `chore/**`,
  `claude/**`, and `codex/**`
- pull requests targeting `main`
- manual dispatch

The only job runs when:

- the event is `workflow_dispatch`
- the event is `pull_request`
- the ref is `refs/heads/main`

As a result, pushes to supported development branches create skipped workflow
runs.

### Snapshot Build

`snapshot.yml` uses the same push/PR/manual trigger model. Its build job has the
same job-level gate, so branch pushes also create skipped workflow runs.

When the build does run, it can build either:

- `assembleGithubSnapshotRelease`
- `assembleGithubSnapshotDebug`

The default build type is `release`. Release snapshot builds decode the signing
keystore and sign the APK. Main branch pushes publish a moving
`latest-snapshot` GitHub prerelease.

### Release Build

`release.yml` runs on `v*` tags. It builds `assembleGithubReleaseRelease`, signs
the APK, uploads it as a workflow artifact, then creates a draft GitHub Release.

The created GitHub Release is currently marked:

- `draft: true`
- `prerelease: true`

There is no distinction between final release tags and pre-release tags such as
release candidates.

## Goals

- Reduce Actions page clutter by avoiding workflow runs whose jobs are expected
  to skip.
- Preserve a way to build Android artifacts from cloud-created branches.
- Introduce a clear pre-release lane for v0.13.2.
- Keep signing secrets out of untrusted PR code paths.
- Support future forked/non-AI contributors without redesigning the workflow
  model later.
- Keep release artifacts and checksums consistent across snapshot, prerelease,
  and final release paths.

## Non-goals

- Changing Gradle variants, signing config, or package IDs.
- Introducing device/emulator jobs.
- Publishing automatically to an app store.
- Replacing the existing manual release review step.

## Proposed Workflow Model

### 1. Quality checks become main/PR/manual only

`checks.yml` should trigger on:

```yaml
on:
  push:
    branches:
      - main
  pull_request:
    branches:
      - main
  workflow_dispatch:
```

Remove the development branch prefixes from `on.push.branches`.

The `verify` job no longer needs the current job-level event/ref gate, because
the workflow itself expresses the intended trigger policy.

The workflow should continue to run the existing serial Gradle commands:

```bash
./gradlew :app:assembleDebugAll
./gradlew :app:lintDebugAll
./gradlew :app:testDebugUnitTestAll
```

### 2. Snapshot builds become explicit for branches

`snapshot.yml` should support four paths:

1. Automatic debug-signed APK on `pull_request` to `main` (see §3).
2. Automatic signed snapshot on push to `main`, publishing to `latest-snapshot`.
3. Scheduled nightly signed snapshot from `main`, also publishing to
   `latest-snapshot`.
4. Manual branch/ref snapshot build via `workflow_dispatch` (debug or signed).

Recommended triggers:

```yaml
on:
  push:
    branches:
      - main
  pull_request:
    branches:
      - main
  schedule:
    # 07:23 UTC daily. Off-the-hour to avoid scheduled-runner queue spikes.
    # Cron in GitHub Actions runs in UTC and does not observe local DST shifts,
    # so the local equivalent moves by an hour twice a year (≈ 01:23 CST /
    # 02:23 CDT). Document as UTC to avoid confusion.
    - cron: '23 7 * * *'
  workflow_dispatch:
    inputs:
      build_type:
        description: 'Build type'
        required: false
        default: 'debug'
        type: choice
        options:
          - debug
          - release
```

The scheduled job must be guarded so forks that enable Actions do not produce
nightly builds in their own repos:

```yaml
jobs:
  build-snapshot:
    if: github.event_name != 'schedule' || github.repository == 'NateEaton/mydeck-android'
```

The manual dispatch default is `debug` rather than `release`. This matches the
common AI-agent and contributor use case ("does it build and run") and avoids
exposing signing secrets to anyone with workflow_dispatch permission. A signed
snapshot can still be produced by explicitly selecting `release`.

This preserves the cloud-environment escape hatch: Claude Code, Codex cloud, or
any branch can be built manually by dispatching the workflow against that
branch/ref. It avoids creating skipped workflow runs for every development
branch push.

#### AI-agent fallback when dispatch is not possible

If a cloud AI agent cannot directly invoke `gh workflow run` from its sandbox
(authentication scope, network restrictions, etc.), the fallback is:

1. The agent pushes its branch and opens a PR as usual.
2. The agent notes in the PR description that a snapshot build is needed and
   names the branch.
3. The repo owner dispatches `Snapshot Build` against that branch manually.

This convention should be documented in `AGENTS.md` and `CLAUDE.md` so agents
know to surface the request rather than failing silently.

### 3. Pull requests build a debug-signed APK, never release-signed

`snapshot.yml` must not decode signing secrets on PR runs. This closes the
present-tense exposure described in Context, and keeps the design ready for
forked PRs later.

Recommended policy:

- PR runs build `assembleGithubSnapshotDebug` only — no keystore decode, no
  signing secrets in scope.
- The resulting debug-signed APK is uploaded as a workflow artifact (not a
  GitHub Release) so the PR author / reviewer can install it for testing
  without an extra manual dispatch step.
- Quality validation (lint, tests, debug variants) remains the responsibility
  of `checks.yml`.
- Main and nightly snapshot builds continue to sign with the release keystore,
  because they run trusted code from the repository after merge.
- Manual `workflow_dispatch` runs may produce a release-signed snapshot when
  the dispatcher explicitly selects `release` as the build type.

#### Signing env must not be in scope on the PR Gradle step

Skipping the keystore decode step is not sufficient on its own. The current
`snapshot.yml` "Build Snapshot APK" step passes `KEY_ALIAS`,
`KEYSTORE_PASSWORD`, `KEY_PASSWORD`, and `KEYSTORE` into Gradle's environment
unconditionally. If implementation only swaps the Gradle task to the debug
variant on PR runs, those env vars are still readable by anything Gradle
executes — including a `build.gradle.kts` or task modified inside the PR
itself.

Required implementation shape: **split the Gradle invocation into two
separate steps**, each guarded by an `if:` expression, with the signing env
only on the signed step:

```yaml
- name: Build Snapshot APK (debug, no signing env)
  if: github.event_name == 'pull_request' || steps.build_vars.outputs.APK_DIR == 'debug'
  run: ./gradlew assembleGithubSnapshotDebug
  env:
    SNAPSHOT_VERSION_NAME: ${{ steps.set_env.outputs.TIMESTAMP }}-${{ steps.set_env.outputs.COMMIT_SHA }}-debug

- name: Build Snapshot APK (signed release)
  if: github.event_name != 'pull_request' && steps.build_vars.outputs.APK_DIR == 'release'
  run: ./gradlew assembleGithubSnapshotRelease
  env:
    SNAPSHOT_VERSION_NAME: ${{ steps.set_env.outputs.TIMESTAMP }}-${{ steps.set_env.outputs.COMMIT_SHA }}
    KEY_ALIAS: ${{ secrets.ALIAS }}
    KEYSTORE_PASSWORD: ${{ secrets.KEY_STORE_PASSWORD }}
    KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
    KEYSTORE: ${{ github.workspace }}/keystore.jks
```

This way the PR-mode build runs in a Gradle process that has never seen the
signing variables, regardless of what the PR's build scripts do.

#### Side-by-side install limitation

Both `githubSnapshotDebug` and `githubSnapshotRelease` use the same
`applicationId` — `com.mydeck.app.snapshot` — because the
`applicationIdSuffix = ".snapshot"` is set on the `githubSnapshot` flavor and
the debug build type adds no further suffix
([app/build.gradle.kts:113-120](app/build.gradle.kts#L113-L120)). They differ
only in signing key (debug keystore vs. release keystore).

Practical consequence: a tester cannot have both a PR debug APK and the
`latest-snapshot` release-signed APK installed at the same time. Installing
one over the other fails with Android's signature-mismatch error
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). To test a PR build, the tester must
first uninstall the current snapshot install.

This is honest about the existing flavor model; per the Non-goals section,
this spec does not change `applicationId` or build variants. If the
uninstall-to-install step becomes a recurring tester complaint, the
follow-up is to add a separate `applicationIdSuffix` to the debug build
type (e.g. `com.mydeck.app.snapshot.debug`), which is a small Gradle change
but explicitly out of scope here.

PR-mode artifact retention: `retention-days: 30`. Long enough that a PR can sit
open for a few weeks without the artifact disappearing under the reviewer, but
short enough that stale artifacts age out automatically.

### 4. Nightly builds use the existing snapshot flavor

The nightly lane should reuse the existing `githubSnapshot` flavor. That gives a
tester-installable APK with a separate application ID from production.

Publication model: use **one** moving `latest-snapshot` GitHub prerelease,
updated by both main pushes and the scheduled nightly job. The release body
must clearly identify the source of the current artifact, e.g.:

> **Source:** main push @ `abc1234` — 2026-05-27 14:02 UTC

or

> **Source:** scheduled nightly @ `abc1234` — 2026-05-27 07:23 UTC

This avoids confusing testers about which of two moving prereleases to install.

### 5. Release workflow distinguishes final and pre-release tags

Use tag naming to decide whether a GitHub Release is final or pre-release:

| Tag | Meaning | GitHub Release |
|---|---|---|
| `v0.13.2-rc.1` | Release candidate | prerelease |
| `v0.13.2-beta.1` | Beta | prerelease |
| `v0.13.2` | Final release | final release |

`release.yml` can still trigger on `v*`, but it should compute release metadata
from the tag.

Concrete rule (bash):

```bash
TAG="${GITHUB_REF#refs/tags/}"
if [[ "$TAG" == *-* ]]; then
  IS_PRERELEASE=true
else
  IS_PRERELEASE=false
fi
echo "is_prerelease=$IS_PRERELEASE" >> "$GITHUB_OUTPUT"
```

- If the tag contains a hyphen anywhere after the leading `v`, set
  `prerelease: true`. Examples: `v0.13.2-rc.1`, `v0.13.2-beta.1`,
  `v0.13.2-rc.1+ci.42` (semver `+build.meta` after a prerelease segment still
  counts as prerelease).
- If the tag is plain `vX.Y.Z` with no hyphen, set `prerelease: false`.
- Keep `draft: true` for both paths. Manual release-note review remains part of
  the release process for final releases (decided 2026-05-27), and drafting
  prereleases avoids accidentally publishing an RC before it has been smoke
  tested.

This supports using v0.13.2 release candidates without creating a separate
workflow.

### 6. Release artifacts should include checksums

`snapshot.yml` already creates `checksums.txt`. `release.yml` should do the same
for signed release APKs.

The final GitHub Release should upload:

- APK artifact
- `checksums.txt`

This makes final releases consistent with snapshots and gives users a stable
integrity-check path.

### 7. Default workflow permissions should be explicit

Set least-privilege permissions at the workflow or job level:

```yaml
permissions:
  contents: read
```

Then override only publishing jobs:

```yaml
permissions:
  contents: write
```

Recommended application:

- `checks.yml`: `contents: read`
- `snapshot.yml` build job: `contents: read`
- `snapshot.yml` publish job: `contents: write`
- `release.yml` build job: `contents: read`
- `release.yml` create-release job: `contents: write`

Note: `release.yml` currently has no `permissions:` block at all on the
`build-and-sign` job, meaning it inherits the repository default. Adding the
explicit `contents: read` is a present-tense least-privilege fix, not just
hygiene for future workflows.

### 8. Clean up signing material after the build step

In both `snapshot.yml` and `release.yml`, the decoded `keystore.jks` is written
to `${{ github.workspace }}/keystore.jks` and is never explicitly removed. The
GitHub-hosted runner is ephemeral and discarded at the end of the job, so the
practical exposure is low — but cleaning it up explicitly is cheap, defends
against accidental upload via `actions/upload-artifact` patterns, and reads
better in audits.

Add an `always()` cleanup step after the signed Gradle task in any job that
decodes the keystore:

```yaml
- name: Remove keystore
  if: always()
  run: rm -f "${{ github.workspace }}/keystore.jks"
```

Apply to the signed paths in `snapshot.yml` (main / nightly / dispatch with
`build_type: release`) and to `release.yml` `build-and-sign`.

### 9. Add job timeouts

Add `timeout-minutes` to all Gradle/build jobs so hung builds fail cleanly.

Suggested defaults:

- Quality checks: `timeout-minutes: 45`
- Snapshot build: `timeout-minutes: 45`
- Release build: `timeout-minutes: 45`
- Publishing-only jobs: `timeout-minutes: 10`

### 10. Action update hygiene and SHA pinning

Add Dependabot for GitHub Actions updates:

```yaml
version: 2
updates:
  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
```

**Pin all `uses:` references by commit SHA**, not by version tag. Version tags
on GitHub are mutable: the action owner can move `@v1.16.0` to point at
different code (including malicious code) at any time, and the next workflow
run would silently execute it. SHA pinning makes the workflow always run the
exact reviewed commit. Dependabot understands SHA pins and will open PRs to
bump them when new versions ship.

Format:

```yaml
uses: ncipollo/release-action@2c91864c4d1b34ba50fb59c40fc4d2bea7f6df8c  # v1.16.0
```

Actions to pin (this is every `uses:` reference across the three workflows;
look up the actual SHA for each pinned version at implementation time):

Third-party (highest priority — they publish releases and move tags):

- `joutvhu/create-tag@v1` (snapshot.yml)
- `ncipollo/release-action@v1.16.0` (snapshot.yml, release.yml)

First-party (`actions/*` and `gradle/actions/*` and `android-actions/*`,
maintained by GitHub or vendor orgs — lower risk but pinned for consistency):

- `actions/checkout@v4`
- `actions/setup-java@v4`
- `actions/upload-artifact@v4`
- `actions/download-artifact@v4`
- `gradle/actions/setup-gradle@v4`
- `android-actions/setup-android@v3`

Pin these in Phase 3 alongside the other `release.yml` and `snapshot.yml`
edits, not deferred to a later phase.

## v0.13.2 Pre-release Flow

This flow assumes all four implementation phases below have already landed
on `main` (per the "all four phases land before any tag" note in the
Implementation Plan). The phase PRs themselves go through the normal PR
process; this section is only about cutting the v0.13.2 release once the
workflow rework is in place.

1. Confirm `latest-snapshot` is updating cleanly from `main` and nightly.
2. Create tag `v0.13.2-rc.1` on the desired `main` commit and push it.
3. Confirm `release.yml` creates a draft prerelease for `v0.13.2-rc.1` with
   APK and `checksums.txt` attached.
4. Install and test the RC APK from the draft prerelease.
5. If needed, fix on `main` and create additional RC tags such as
   `v0.13.2-rc.2`. Each RC produces its own draft prerelease.
6. When an RC is accepted, create tag `v0.13.2` on the same commit and push.
7. Confirm `release.yml` creates a draft final release with
   `prerelease: false` and APK + `checksums.txt` attached.
8. Review release notes on the draft, then click **Publish release**.

## Implementation Plan

All phases must preserve the existing `concurrency:` blocks
(`group: <workflow>-${{ github.event.pull_request.number || github.ref }}`,
`cancel-in-progress: true`) and the existing
`env: FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: 'true'` setting on each workflow.
These are present in the current files and should not be dropped during the
trigger rewrites.

### Phase 1: Remove skipped-run clutter and close PR signing exposure

- Update `checks.yml` triggers to main/PR/manual only.
- Remove the redundant job-level `if` gate in `checks.yml`.
- Add explicit `contents: read` to `checks.yml`.
- Add `timeout-minutes` to `checks.yml`.
- Add explicit `contents: read` to the `release.yml` `build-and-sign` job (it
  has no `permissions:` block today). Final release flow stays unchanged in
  this phase — Phase 3 handles tag-derived metadata.
- In `snapshot.yml`, close the PR signing-secret exposure:
    - Skip the "Decode Keystore" step on PR runs (`if:` guard).
    - **Split the Gradle invocation into two steps** (see §3 "Signing env
      must not be in scope on the PR Gradle step"). PR runs invoke the
      debug task in a step whose `env:` does **not** include
      `KEY_ALIAS` / `KEYSTORE_PASSWORD` / `KEY_PASSWORD` / `KEYSTORE`.
      Signed paths invoke the release task in a separate step that does
      include them.
    - Skip the artifact-upload step's `release` branch on PR runs (the
      build vars already mark debug/release; just ensure the PR debug
      artifact is what gets uploaded).
  Only swapping the task name is not enough — without the env-scope split,
  PR-modified build scripts could still read signing variables. The rest of
  the snapshot rework lands in Phase 2.

### Phase 2: Rework snapshots

- Update `snapshot.yml` triggers to main/PR/schedule/manual.
- Remove branch-prefix push triggers.
- Remove the now-redundant job-level `if` gate on `build-snapshot` (the
  workflow-level trigger policy expresses the intent).
- Change `workflow_dispatch` default for `build_type` from `release` to
  `debug`.
- Add scheduled nightly behavior at `cron: '23 7 * * *'` (07:23 UTC; the
  cron is UTC and does not shift with DST).
- Add fork guard so the scheduled job only runs in this repository.
- Set `retention-days: 30` on the PR-mode debug APK artifact (the PR code
  path itself was already switched to debug in Phase 1; this just extends
  retention from the default 7 days).
- Publish one moving `latest-snapshot` GitHub prerelease, updated by both
  main pushes and scheduled nightly runs. Release body identifies the source
  (main push vs. nightly) and the commit SHA.
- Add the `if: always()` keystore cleanup step (§8) to the signed code path.
- Add explicit permissions and timeouts.

### Phase 3: Add release candidate handling and pin actions

- Update `release.yml` to derive `version`, `is_prerelease`, and release name
  from the tag using the bash rule in §5.
- Keep `draft: true` for both prerelease and final paths.
- Add checksums to release artifacts.
- Add the `if: always()` keystore cleanup step (§8) to `release.yml`
  `build-and-sign`.
- Add explicit permissions and timeouts to all jobs.
- Pin every `uses:` reference across all three workflows by SHA (see §10 for
  the list). Include a trailing `# vX.Y.Z` comment on each pin so reviewers
  can see which version each SHA corresponds to.

### Phase 4: Contributor readiness

- Add a branch protection ruleset on `main` (see "Branch Protection" below).
- Add Dependabot for GitHub Actions updates.
- Update `docs/WORKFLOW.md` — the existing CI/CD section describes the current
  branch-prefix push triggers and signed PR builds, which both go away. Replace
  with the trigger and artifact model described in this spec, and add a
  "Scenarios" subsection (see "WORKFLOW.md Scenarios" below) so maintainers
  and contributors can look up the expected CI behavior for any common
  situation without reading the YAML.
- Update `AGENTS.md` and `CLAUDE.md` with the AI-agent dispatch fallback (§2):
  if the agent cannot run `gh workflow run` itself, it should note in the PR
  description that a snapshot build is requested and name the branch.

All four phases should land before any `v0.13.2` (or `v0.13.2-rc.*`) tag is
cut. The v0.13.2 pre-release flow below assumes the entire workflow rework is
already in place.

### Branch Protection

GitHub's "you don't have branch protection" warning links to a long page about
rulesets that is dense for first-time readers. In plain terms:

**What branch protection does.** A *ruleset* is a set of restrictions you
attach to a branch (or branches matching a pattern, like `main`). When someone
tries to push or merge into a protected branch, GitHub enforces the rules
before letting the change land. Without a ruleset, anyone with write access
can push directly to `main`, force-push over history, or merge a PR that
hasn't been reviewed or that has failing checks.

**What to turn on for this repo:**

| Rule | Setting | Why |
|---|---|---|
| Restrict deletions | On | Prevents `main` from being deleted, by accident or otherwise. |
| Block force pushes | On | Prevents history rewrites on `main`. |
| Require a pull request before merging | On | Every change to `main` goes through a PR, including your own. |
| Require status checks to pass | On — select `Quality Checks / verify` | Blocks merges if `checks.yml` fails. |
| Require branches to be up to date before merging | On | Forces re-running checks against the latest `main` before merge. |
| Require linear history | On (you use squash-and-merge per WORKFLOW.md) | Keeps `main` clean. |
| Require signed commits | Off | Optional; nice-to-have but adds setup friction. Skip for now. |
| Require review from Code Owners | Off | Useful once there are co-maintainers; not needed yet. |
| Bypass list | Add yourself as a "bypass actor" (optional) | Lets you push emergency hotfixes without going through a PR. Use sparingly; every bypass shows up in the audit log. |

**Rulesets vs. classic branch protection.** GitHub has two systems: the older
"Branch protection rules" tab and the newer "Rulesets" tab. They do mostly
the same thing. Rulesets are the supported direction going forward, support
patterns (`main`, `release/*`, etc.), and log every enforcement event. Use
rulesets, not the classic UI.

**One-time setup:**

1. Repo → Settings → Rules → Rulesets → New ruleset → "New branch ruleset".
2. Name: "Main branch protection".
3. Enforcement status: "Active".
4. Target branches: Include default branch (`main`).
5. Bypass list: add yourself if you want emergency-bypass capability.
6. Branch rules: tick the items in the table above.
7. Save.

Once `Quality Checks / verify` is added as a required status check, the check
must have run at least once on the branch before GitHub will let a PR merge.
This is fine because every PR runs `checks.yml` under the trigger model in
§1.

## Validation

Because these are workflow changes, validation should focus on GitHub Actions
behavior rather than local Gradle execution alone:

- Run `Quality Checks` on a PR and confirm it runs once without skipped sibling
  workflow noise.
- Push a normal development branch and confirm no Actions run is created unless
  the workflow is manually dispatched.
- Open a PR and confirm `Snapshot Build` runs in PR mode: produces a
  debug-signed APK as a workflow artifact, and the keystore decode step does
  not run (inspect the run log).
- Confirm no signing secrets appear in the PR run environment.
- From a Claude Code or Codex cloud session, attempt `gh workflow run
  snapshot.yml --ref <branch>` and confirm whether dispatch is possible from
  that environment. If not, confirm the AGENTS.md / CLAUDE.md fallback
  convention is documented.
- Manually dispatch `Snapshot Build` against a non-main branch with
  `build_type: release` and confirm a signed APK artifact is produced.
- Merge to `main` and confirm the `latest-snapshot` release is updated with a
  body line identifying the source as a main push.
- Let the scheduled workflow run once (or manually test the same code path)
  and confirm `latest-snapshot` is updated with a body line identifying the
  source as a nightly run.
- Tag `v0.13.2-rc.1` and confirm a draft prerelease is created.
- Tag `v0.13.2` and confirm a draft final release is created with
  `prerelease: false`.
- Confirm APK checksums are uploaded for snapshot, prerelease, and final
  release artifacts.

### Validation Results (2026-05-28)

Phases 1–3 validated post-merge on `main` at commit `cc638a5`.

**Phase 1 + 2 (checks.yml, snapshot.yml) — confirmed via PR #172 CI run:**

- `Quality Checks` and `Snapshot Build` both ran on the PR with all SHA-pinned
  actions resolving cleanly. Runner logs confirmed every action was downloaded
  by exact commit SHA (e.g. `actions/checkout@34e114876b...`).
- Post-merge main push (run 26587177877): `build-snapshot` succeeded, keystore
  cleanup ran, `publish-latest-snapshot` updated `latest-snapshot` release. All
  steps green.

**Phase 3 (release.yml) — confirmed via throwaway tag `v0.0.0-ci.test`:**

- Tag pushed to `cc638a5`; `release.yml` triggered (run 26589287125), both
  `build-and-sign` and `create-release` jobs succeeded.
- Draft release produced: `draft: true`, `prerelease: true` (hyphen in tag
  → IS_PRERELEASE=true rule fired correctly), name `MyDeck Release v0.0.0-ci.test`.
- Assets attached: signed APK (~5.2 MB) + `checksums.txt` (128 B). ✓
- New `Remove keystore` step (`if: always()`) ran successfully. ✓
- All SHA-pinned actions resolved (same runner-log pattern as snapshot). ✓
- Draft release and tag deleted after inspection.

**Open item for Phase 4 agent:** The draft release stored `tag_name:
"refs/tags/v0.0.0-ci.test"` (the literal value of `${{ github.ref }}`) and
the draft preview URL appeared as `releases/tag/untagged-...`. Published
releases (e.g. v0.13.1) show a clean `tag_name: "v0.13.1"`, suggesting the
prefix is stripped at publish time — but this was not directly verified. This
is pre-existing behaviour (`tag: ${{ github.ref }}` was in the original
workflow and was not changed in Phase 3). Before cutting the first RC, verify
this is acceptable or replace `tag: ${{ github.ref }}` with
`tag: ${{ github.ref_name }}` in `release.yml`'s `create-release` step.
`github.ref_name` resolves to just the tag name (e.g. `v0.13.2-rc.1`) without
the `refs/tags/` prefix.

**Remaining validation (requires real tags):**

- Final-release (no-hyphen) `prerelease: false` path — deferred to `v0.13.2` cut.
- Scheduled nightly `latest-snapshot` source line — deferred to next nightly run.

## Resolved Decisions (2026-05-27)

- Scheduled builds and main pushes both update one moving `latest-snapshot`
  prerelease. Release body distinguishes the two sources. (§4)
- Manual `workflow_dispatch` snapshot builds default to `debug`. Signed
  `release` snapshots require explicit opt-in via the dispatch input. (§2)
- PR runs auto-upload a **debug-signed APK** (signed with the auto-generated
  Android debug keystore, not the release keystore) as a workflow artifact.
  No release-signing secrets touched on PR runs. (§3)
- Third-party publishing actions are SHA-pinned in Phase 3, alongside the
  other release/snapshot changes. First-party `actions/*` references are
  pinned at the same time for consistency. (§10)
- Final release tags (`vX.Y.Z`) stay as `draft: true`, like prerelease tags.
  Manual release-note review remains part of the release process. (§5)

## Open Decisions

None at present. Re-open this section if implementation surfaces new
trade-offs.

## WORKFLOW.md Scenarios

The following content is the target shape of the "CI/CD Scenarios" subsection
that should land in `docs/WORKFLOW.md` during Phase 4. Use this verbatim
(adjusting wording for that doc's voice as needed) so maintainers and
contributors have a quick lookup for "what happens when I do X."

### CI/CD Scenarios

Every common situation, what CI does, and where to find the artifact.

#### Local feature work, no PR yet

You're iterating on a `feature/*` (or `enhancement/*`, `fix/*`, `chore/*`)
branch and want a tester to install your build.

1. Commit and push the branch. **Nothing in CI runs automatically.** This is
   intentional — branch pushes used to create skipped workflow runs that
   cluttered the Actions page.
2. Go to **Actions → Snapshot Build → Run workflow** and select your branch.
3. Choose `build_type`: `debug` (default, debug-signed) or `release`
   (release-signed snapshot APK).
4. When the run finishes, the APK is in the workflow run's **Artifacts**
   section. Download, unzip, install. Retention: 7 days (manual dispatch
   path). PR-triggered builds use 30 days — see "Opening a PR" below.

#### Opening (or updating) a PR to `main`

Includes draft PRs.

- **`Quality Checks` runs**: assembles all debug variants, lint, unit tests.
- **`Snapshot Build` runs in PR mode**: builds the **debug-signed APK**
  (`assembleGithubSnapshotDebug`, signed with the auto-generated Android
  debug keystore) and uploads it as a workflow artifact. No release
  keystore is decoded; no release-signing secrets are exposed. Retention:
  30 days.
- Where to find the APK: the PR's **Checks** tab or the workflow run's
  **Artifacts** section.
- **Install limitation:** the PR debug APK and the `latest-snapshot`
  release-signed APK share the same `applicationId`
  (`com.mydeck.app.snapshot`) but use different signing keys. They cannot
  coexist on a device. To install a PR APK, first uninstall any existing
  `latest-snapshot` install. Both still install alongside the production
  app (`com.mydeck.app`).

#### PR from a fork

Same as above. Forked PRs do not receive repository secrets from GitHub by
default; since we are not signing on PRs anyway, this is safe. Debug APK
shows up as a workflow artifact like any other PR.

#### PR merges to `main`

- **`Snapshot Build` runs in main-push mode**: builds a **signed release
  snapshot** APK and updates the single moving `latest-snapshot` GitHub
  prerelease.
- Release body identifies the source, e.g.
  `**Source:** main push @ abc1234 — 2026-05-27 14:02 UTC`.
- Where to find the APK: Releases tab → `latest-snapshot`.

#### Scheduled nightly build

- Runs daily at **07:23 UTC**. GitHub Actions cron is UTC and does not
  follow DST, so the local equivalent shifts by an hour twice a year
  (≈ 01:23 CST / 02:23 CDT). The UTC time is the stable one.
- Builds a signed release snapshot from `main` and updates the same
  `latest-snapshot` prerelease.
- Release body identifies the source as nightly, e.g.
  `**Source:** nightly @ abc1234 — 2026-05-27 07:23 UTC`.
- The release name and tag do not change. There is **no** separate
  `latest-nightly` release — `latest-snapshot` is the one moving prerelease.
- Only runs in `NateEaton/mydeck-android`. Forks that enable Actions do not
  inherit the scheduled build.

#### Pushing a pre-release tag

Tag pattern: `vX.Y.Z-<anything>`, e.g. `v0.13.2-rc.1`, `v0.13.2-beta.1`.

- **`Release Build` runs**: builds a signed release APK, computes
  `prerelease: true` from the tag (any hyphen after the version), creates a
  **draft GitHub Release** named `MyDeck Release vX.Y.Z-...`.
- Artifacts attached to the release: APK + `checksums.txt`.
- The release is created as a **draft**. Review the release notes, then
  click **Publish release** when ready.

#### Pushing a final release tag

Tag pattern: `vX.Y.Z` with no hyphen, e.g. `v0.13.2`.

- Same as the pre-release flow, but the draft release is marked
  `prerelease: false` (final).
- Still a draft until you publish.

#### Tagging the wrong commit

`Release Build` will still run — the workflow trusts the tag. Catch it during
the draft review and either delete the tag (and re-push to the correct
commit) or edit the draft release.

If you re-push a tag after deleting it, the workflow runs again, but the
release-creation step is configured with `allowUpdates: false`, so it will
fail rather than silently overwrite the existing draft. Delete the previous
draft first.

#### Force-pushing a feature branch

Allowed. Branch protection only protects `main`. Under the new trigger
model, plain branch pushes do not start any CI run, so there is usually
nothing to cancel. The `concurrency` block only matters when a run is
already in flight from another trigger on the same ref — typically an open
PR (pushes to a branch with an open PR re-trigger `pull_request` runs that
share a concurrency group keyed on the PR number) or a manual
`workflow_dispatch`. In those cases the in-flight run is cancelled in favor
of the new commit.

#### Pushing directly to `main`

Blocked by the branch protection ruleset. All changes to `main` go through a
PR (squash-and-merge). If you genuinely need to bypass — emergency hotfix,
broken CI, etc. — and you've added yourself to the ruleset's bypass list,
the push is allowed but logged in the audit log.

#### AI agent (Claude Code, Codex cloud) cannot trigger a workflow itself

If the agent's sandbox does not allow `gh workflow run`, the convention is:

1. The agent pushes its branch and opens a PR as normal.
2. The agent notes in the PR description that a snapshot build is needed and
   names the branch.
3. A maintainer dispatches **Snapshot Build** against that branch manually.

This is documented in `AGENTS.md` and `CLAUDE.md` so the agent knows to ask
rather than fail silently.
