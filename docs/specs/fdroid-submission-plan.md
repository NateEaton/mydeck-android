# F-Droid Submission Plan — MyDeck

**Status:** Draft — not started
**Date:** 2026-07-11
**Related:** `docs/archive/fdroid-build-prep-port-spec.md` (build-prep work, already merged to `main`)

## 1. Where things stand

The build-prep work (removing the JitPack-sourced Treessence dependency and the
unused `BUILD_TIME` field) already landed on `main`. That was gating item #1.
A survey of the current repo state shows most of the rest of the checklist is
already satisfied incidentally:

| Requirement | Status |
|---|---|
| FOSS license | ✅ GPL-3.0 (`LICENSE`, full text) |
| Public source repo | ✅ `github.com/NateEaton/mydeck-android`, public |
| No proprietary deps (Firebase, Play Services, JitPack, ads, analytics) | ✅ confirmed via grep, none present |
| `dependenciesInfo` (Play "Play Install Referrer" block) disabled | ✅ already `includeInApk/Bundle = false` |
| No NDK / native code / prebuilt `.jar`/`.aar` blobs | ✅ none found — pure Gradle/Kotlin |
| Reproducible version fields without CI secrets | ✅ `githubRelease` flavor falls back to `defaultConfig` versionCode/Name when `RELEASE_VERSION_*` env vars are unset |
| Minimal permissions (no location/camera/tracking) | ✅ INTERNET, POST_NOTIFICATIONS, FOREGROUND_SERVICE(_DATA_SYNC) only |
| Fastlane-style store metadata already in-repo | ✅ `metadata/en-US/` (title, descriptions, changelogs per versionCode, phone+tablet screenshots) — F-Droid can consume this directly, no duplicate write-up needed |
| Tagged releases | ✅ `v0.14.6` exists and is pushed to GitHub (commit `fe895b8`) |

This means the app itself needs **no further code changes** to be F-Droid-ready.
What's left is entirely on the submission side: local tooling, drafting the
`fdroiddata` metadata recipe, and opening the inclusion request.

One pre-existing item worth a quick look separately (not an F-Droid blocker):
`metadata/en-US/full_description.txt` still says "OAuth Device Code Grant
authentication" and doesn't mention the browser PKCE flow or reading fonts
added in 0.14.6. Since F-Droid's listing pulls this same text, it'd be worth
refreshing before or shortly after submission — but it doesn't block anything.

## 2. Decisions made in this plan (flag if you'd prefer otherwise)

- **Flavor to submit:** `githubRelease` (the secure/default flavor — HTTPS-only,
  no cleartext, no user CA trust). The `githubReleaseHttp` variant is a
  self-hosting convenience for GitHub-direct users and isn't something we need
  to also carry through F-Droid; it stays GitHub-only.
- **Build task:** `assembleGithubReleaseRelease`, `subdir: app`.
- **First version to submit:** tag `v0.14.6` (versionCode `14006`), since it's
  the latest tagged, pushed release and matches the in-repo changelog/metadata.

## 3. Phase A — Local tooling (one-time setup on the MBP)

```
brew install fdroidserver
```

This pulls in the `fdroid` CLI and its Python dependencies (androguard, etc.).
It uses your existing Android SDK — confirm `ANDROID_HOME` (or
`ANDROID_SDK_ROOT`) is set in your shell profile, since `fdroidserver` reads it
for `fdroid build`/`fdroid readmeta`.

You'll also need a **GitLab.com account** — F-Droid's metadata repo
(`fdroiddata`) and its inclusion-request workflow live on GitLab, not GitHub.
If you don't have one, create it now; the rest of the plan assumes you do.

## 4. Phase B — Fork and clone `fdroiddata`

1. On gitlab.com, fork `https://gitlab.com/fdroid/fdroiddata` into your account.
2. Clone your fork locally (pick a working directory outside `MyDeck`, e.g.
   next to your other repos):

```
git clone git@gitlab.com:<your-gitlab-username>/fdroiddata.git
cd fdroiddata
git remote add upstream https://gitlab.com/fdroid/fdroiddata.git
git checkout -b add-com.mydeck.app
```

## 5. Phase C — Draft the metadata recipe

Create `metadata/com.mydeck.app.yml` in the `fdroiddata` fork. Draft content:

```yaml
Categories:
  - Reading
  - Internet
License: GPL-3.0-only
AuthorName: Nate Eaton
SourceCode: https://github.com/NateEaton/mydeck-android
IssueTracker: https://github.com/NateEaton/mydeck-android/issues
Changelog: https://github.com/NateEaton/mydeck-android/blob/main/CHANGELOG.md

RepoType: git
Repo: https://github.com/NateEaton/mydeck-android.git

Builds:
  - versionName: 0.14.6
    versionCode: 14006
    commit: v0.14.6
    subdir: app
    gradle:
      - githubRelease

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 0.14.6
CurrentVersionCode: 14006
```

Notes to verify against the current `fdroiddata` docs/examples at submission
time (the schema evolves and reviewers are picky about it):
- Confirm `Reading` and `Internet` are still valid F-Droid category names
  (check `categories.yml` in the `fdroiddata` repo you just cloned).
- Confirm whether `UpdateCheckMode: Tags` needs a pattern
  (e.g. `Tags ^v[0-9.]+$`) given the repo also has non-release tags like
  `v1.0.0-rc*` and `wip/*` branches that shouldn't be picked up.
- Bundled fonts under SIL OFL / CC BY 4.0 (documented in-app under
  About → Font licenses) don't need special YAML handling — F-Droid only
  wants the top-level `License:` to reflect the app's own code license — but
  it's worth calling out in the MR description for the reviewer's benefit.

## 6. Phase D — Local validation before opening the MR

Run these from inside the `fdroiddata` checkout:

```
fdroid readmeta
fdroid rewritemeta com.mydeck.app
fdroid lint com.mydeck.app
fdroid checkupdates com.mydeck.app
fdroid build -v -l com.mydeck.app:14006
```

- `readmeta`/`rewritemeta` normalize formatting (run `rewritemeta` and let it
  reformat the file — this is expected and avoids a reviewer nitpick).
- `lint` catches metadata schema issues.
- `checkupdates` confirms the tag/version detection works.
- `fdroid build -l` (local, non-buildserver mode) does a real Gradle build of
  `assembleGithubReleaseRelease` from the tagged commit in an isolated
  checkout — this is the closest local approximation of what F-Droid's build
  server will do, and it's the step most likely to surface a problem (missing
  env var assumption, a repository not being mirrorable, etc.).

Fix anything that surfaces here before moving on.

## 7. Phase E — Open the inclusion request (GitLab web UI, not CLI)

Same principle as your GitHub/Codeberg workflow: I'll give you the exact
branch-push command and the MR title/description to paste into GitLab's web
UI; you open the MR yourself.

```
git add metadata/com.mydeck.app.yml
git commit -m "New app: MyDeck"
git push -u origin add-com.mydeck.app
```

Then open a Merge Request on GitLab from your fork's `add-com.mydeck.app`
branch into `fdroid/fdroiddata`'s default branch. `fdroiddata`'s MR template
auto-populates a checklist — fill it in based on the table in §1 of this doc.

## 8. Phase F — Review cycle

F-Droid's bots (`fdroid checkupdates`/CI) and human reviewers will comment on
the MR — commonly requesting metadata tweaks, not code changes, given the
build-prep work is already done. Iterate on the same branch until merged.

## 9. Phase G — After merge (ongoing, low-effort)

Once merged, F-Droid's build server builds new versions automatically based on
`UpdateCheckMode: Tags` — each future `vX.Y.Z` tag you push to GitHub (as
you already do at release time) gets picked up without a new MR, as long as
versionCode/versionName continue to resolve correctly from `defaultConfig`
without secrets. No change to the existing release workflow in
`docs/WORKFLOW.md` is needed.

## 10. Open items to revisit

- Refresh `metadata/en-US/full_description.txt` to mention browser OAuth
  (PKCE) sign-in and reading fonts — cosmetic, not a submission blocker.
- Confirm current F-Droid category list and tag-matching pattern at
  submission time (schema/policy details drift; re-check against the live
  `fdroiddata` repo rather than trusting this doc verbatim if much time has
  passed).
