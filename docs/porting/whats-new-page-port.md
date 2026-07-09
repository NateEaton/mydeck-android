# Port checklist — What's New sheet + release history

**Source:** Readeck for Android branch `feat/whats-new-page` (commits `9ebe5bf`
"feat: What's New sheet on update + first-launch guide nudge", `b241612`
"feat: What's New release history screen", `1b43fa1` "feat: open current
whats new from about") — **not yet merged to `main` or PR'd**; this port can
proceed straight from the branch tip.
**Target:** MyDeck `main`
**Methodology:** `docs/porting/mydeck-readeck-port.md`
**Design reference:** `docs/specs/whats-new-page-spec.md` (recommend copying
this into MyDeck's `docs/specs/` too — see Branding deltas below — since the
new `docs/WORKFLOW.md` step references it by path).

## What this feature does

A curated, per-version "What's New" sheet appears the first time the app is
opened after an update (content lives in
`app/src/main/assets/whatsnew/<locale>/<version>.md`, decoupled from
`CHANGELOG.md`). Fresh installs never see it — instead they get a one-time
dismissible nudge toward the User Guide. **About → What's New** opens the
*current* version's notes if they exist (falling back straight to history if
not); from that sheet, "See previous releases" — and the About row itself when
there's no current-version note — leads to a full release-history list,
newest-first via a numeric-aware comparator (a plain string sort would rank
`"1.0.10"` before `"1.0.9"`, and would rank a release below its own `-rc`
builds).

## Branding deltas applied (§2)

1. **`whats_new_guide_nudge_title`** — Readeck: `"Welcome to Readeck"` → MyDeck:
   `"Welcome to MyDeck"`.
2. **`whats_new_guide_nudge_body`** — Readeck: `"New here? Take a quick tour of
   the User Guide to get the most out of Readeck."` → MyDeck: `"...get the most
   out of MyDeck."` These are the only two of the twelve new string values that
   name the app; the other ten (sheet title, "Got it", "See previous releases",
   history title/empty-state, "Open the User Guide", "Not now", About row
   label/subtitle) are brand-neutral and port as-is. All are still
   English-placeholder text pending translation in the non-`en` locale files —
   port the same placeholder pattern, just with "MyDeck" substituted in the two
   strings above.
3. **Do NOT port `app/src/main/assets/whatsnew/en/1.0.0-rc5.md`.** It's
   Readeck's own curated notes for Readeck's `1.0.0-rc5` — content, not
   branding, and tied to a version number MyDeck (currently `0.14.5`) will
   never reach. MyDeck ships this feature with an **empty `whatsnew/`
   directory** — by design, a version with no matching file simply never
   triggers the sheet (see spec, "no file = silent skip"). Author MyDeck's own
   first entry at MyDeck's own next release-prep (see delta 5 below), not as
   part of this port.
4. **`CHANGELOG.md` bullet** and the **`app/src/main/assets/guide/en/settings.md`**
   sentence are brand-neutral — no app name in either — port the wording as-is.
5. **`docs/WORKFLOW.md` step does NOT copy verbatim** — Readeck's release-prep
   numbering/structure differs from MyDeck's (`docs/WORKFLOW.md` §"Step 1: Prep
   the Release", items 1–6, vs. Readeck's §"Step 1 — Prep", items 1–5). Insert
   an **analogous new step** in MyDeck's list — after item 4 ("Update
   `CHANGELOG.md`"), before the existing "Commit" item — instructing the
   release-prep author to write `app/src/main/assets/whatsnew/en/X.Y.Z.md` (+
   locale placeholders) from the `CHANGELOG.md` section just written, in
   explanatory/curated language, skipping internal-only entries; renumber the
   remaining items. Reference `docs/specs/whats-new-page-spec.md` (copy that
   spec doc into MyDeck too, swapping "Readeck" → "MyDeck" in its prose, so the
   cross-reference resolves).

## Files changed

### New files (verbatim — no repo-specific logic)
- `app/src/main/java/com/mydeck/app/ui/whatsnew/WhatsNewAssetLoader.kt` —
  locale-fallback markdown loader (`loadNotesForVersion`, `normalizeVersion`
  strips a snapshot build's `-snapshot` suffix so snapshot builds can preview
  upcoming release notes, `listAvailableVersions` + numeric-aware
  `compareVersions` for history sorting).
- `app/src/main/java/com/mydeck/app/ui/whatsnew/WhatsNewViewModel.kt` — the
  on-launch trigger (`evaluateIfNeeded`, idempotent per instance): fresh
  install → silent marker + optional guide nudge; version change with a
  matching notes file → shows the sheet; version change with no file → marker
  still advances (see spec's "Resolved decisions" for why this is safe).
- `app/src/main/java/com/mydeck/app/ui/whatsnew/WhatsNewSheet.kt` — the
  `ModalBottomSheet`, reusing `ui/userguide`'s `rememberMarkwon`/
  `applyMarkwonColors` render pattern and `ui/components`'
  `dismissSheet` helper (from the earlier bottom-sheet-consistency port) so
  "Got it" / swipe / tap-outside all animate identically. Optional
  `onSeePreviousReleases` param.
- `app/src/main/java/com/mydeck/app/ui/whatsnew/WelcomeGuideNudgeDialog.kt` —
  simple `AlertDialog`, fresh-install-only nudge to the User Guide.
- `app/src/main/java/com/mydeck/app/ui/whatsnew/WhatsNewHistoryViewModel.kt` +
  `WhatsNewHistoryScreen.kt` — lists every version with notes (newest-first),
  tapping one loads and shows it in the same `WhatsNewSheet`.
- `app/src/test/java/com/mydeck/app/ui/whatsnew/WhatsNewAssetLoaderTest.kt` —
  pure-function tests for `normalizeVersion` and `compareVersions` (numeric
  ordering, rc-vs-release ranking, sort direction). No Robolectric needed.

### Modified files — confirmed byte-identical baselines (§0.2), copy/cherry-pick cleanly
- `app/src/main/java/com/mydeck/app/io/prefs/SettingsDataStore.kt` +
  `SettingsDataStoreImpl.kt` — two new non-sensitive `userPreferences` keys
  (`last_seen_whatsnew_version` string, `welcome_guide_prompt_shown` boolean) +
  four accessor methods. **Pre-port sanity:** grep MyDeck's
  `SettingsDataStoreImpl.kt` for those two key strings first — should be
  absent (new keys), confirm before adding.
- `app/src/main/java/com/mydeck/app/ui/navigation/Routes.kt` —
  `WhatsNewHistoryRoute` (one new `@Serializable object`).
- `app/src/main/java/com/mydeck/app/ui/shell/AppShell.kt` — the trigger
  `LaunchedEffect` + sheet/dialog overlays live **once**, at the top-level
  `AppShell()` composable (siblings after the `when (layoutTier) {...}` block)
  — not duplicated per layout tier. `WhatsNewHistoryRoute` is registered in
  **both** `NavHost` blocks (the inline one in `CompactAppShell`, and the
  shared `AppShellNavHost` used by `MediumAppShell`/`ExpandedAppShell`) — check
  MyDeck's `AppShell.kt` has the same two-NavHost shape before assuming the
  same two insertion points apply.
- `app/src/main/java/com/mydeck/app/ui/about/AboutViewModel.kt` — confirmed
  byte-identical baseline; copies/cherry-picks cleanly. Adds:
  `hasWhatsNewHistory` (gates the About row on *any* history existing, not
  just the current version), `onClickWhatsNew()` (loads current version's
  notes; if none, falls straight to `NavigateToWhatsNewHistory`),
  `onDismissWhatsNewSheet()`, `onClickWhatsNewHistory()`.

### Modified file — **diverged, do NOT copy wholesale**
- `app/src/main/java/com/mydeck/app/ui/about/AboutScreen.kt` — MyDeck's
  version differs from Readeck's pre-branch baseline **only** in the
  "Project Links" section (the fork/original/server repo rows — MyDeck has its
  own fork-info block with its own URLs, in a different row order; see
  `FORK_INFO_START`/`END` markers). That section is **not touched** by this
  feature — the What's New changes land between the Description and Credits
  sections, and near (previously: after) the Font Licenses row, both well
  clear of Project Links. A patch/cherry-pick merge (not `cp`/`git checkout
  <ref> --`) will apply cleanly despite the divergence; just don't overwrite
  the whole file. Adds: the "What's New" row (positioned just above the
  Credits section, gated on `uiState.hasWhatsNewHistory`), and inline
  `WhatsNewSheet` rendering driven by `uiState.whatsNewVersion`/`whatsNewContent`
  with `onSeePreviousReleases` wired to `onClickWhatsNewHistory`.
- `CHANGELOG.md`, `app/src/main/assets/guide/en/settings.md` — brand-neutral,
  port wording as-is (see Branding deltas §4).
- `docs/WORKFLOW.md` — do not copy verbatim; adapt per Branding deltas §5.
- `app/src/main/res/values/strings.xml` + all locale files — insert the twelve
  new keys surgically (never `git checkout <ref> -- strings.xml`, per the
  methodology's §5 warning about clobbering independently-added keys). Swap
  "Readeck" → "MyDeck" in the two nudge strings (delta 2).

## §4 data-model check
No Room schema changes — the two new preference keys are Preferences DataStore
(`SharedPreferences`-backed), not Room. §4 is a no-op.

## Pre-port sanity (§0.2)
- `AboutViewModel.kt`, `AppShell.kt`, `Routes.kt`, `SettingsDataStore.kt`,
  `SettingsDataStoreImpl.kt` — confirmed byte-identical to Readeck's
  pre-branch `main` as of this writing. Re-confirm at port time in case MyDeck
  has moved since.
- `AboutScreen.kt` — confirmed diverged, but only in a non-overlapping section
  (see above). Re-confirm the divergence is still confined there before
  merging.
- MyDeck is currently at `versionName = "0.14.5"` — nowhere near
  `1.0.0-rc5`, reinforcing why the sample `whatsnew/en/1.0.0-rc5.md` content
  must not be copied (delta 3).

## Verification
```
./gradlew :app:assembleDebugAll :app:testDebugUnitTestAll :app:lintDebugAll
./scripts/install-phone.sh
```
On-device / emulator:
- Fresh install (clear app data) → sign in → guide nudge appears once, "Open
  the User Guide" navigates correctly, "Not now" dismisses, never reappears.
- Simulate an update to exercise the trigger: temporarily set
  `versionName` to a fake older value (e.g. `"0.14.4"`) in
  `app/build.gradle.kts`, build+install, sign in, close the app; then revert
  to the real `versionName`, rebuild, and install over it (same
  `versionCode`, so no downgrade-install issue) — the sheet should now fire
  for the real version. (Author a matching `whatsnew/en/<real-version>.md`
  first, or you'll correctly see nothing — see delta 3.)
- About → What's New opens the current version's notes (or history, if none
  exist for the current version); "See previous releases" from the sheet, and
  the history screen's own list, both work; tapping a past entry reopens its
  notes.
