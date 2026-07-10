# "What's New" on update + first-launch guide nudge (spec)

Status: Implemented (shipped in PR #226, `feat/whats-new-page`) · Ported from Readeck for Android · Author aid: Claude

## Problem

After a user updates the app, there is no in-app surface that tells them what
changed. The official iOS app shows a "What's New" sheet on first launch after an
update. We want the same for Android, but with two refinements the iOS version
lacks:

1. **Scope to the delta.** Show only *this release's* highlights, not the entire
   accumulated history every time.
2. **Explanatory tone.** The copy should be able to say more (and say it more
   plainly) than a terse changelog line — and it should be free to *omit*
   changelog entries (internal fixes, refactors) that aren't worth a user's
   attention.

Separately, a **brand-new user** on first install has nothing to be "updated"
about — they're in onboarding. Instead of a What's New sheet, they should get an
optional, dismissible nudge toward the User Guide so they can learn the app
before diving in.

## Reference implementations

### iOS (../readeck-ios)

- `RELEASE_NOTES.md` — a single hand-curated markdown resource, **separate from
  any changelog**, grouped by version newest-first with feature sub-headings and
  bold lead-ins (`readeck/UI/Resources/RELEASE_NOTES.md`).
- `VersionManager` — stores `lastSeenAppVersion` in `UserDefaults`; `isNewVersion`
  is true on first launch *or* when stored ≠ current
  (`readeck/UI/Utils/VersionManager.swift`).
- `ReleaseNotesView` — a modal sheet rendering the markdown, shown from
  `TabView.onAppear` when `isNewVersion`, and also reachable manually from
  Settings (`readeck/UI/Settings/ReleaseNotesView.swift`).
- Limitation we're deliberately not copying: it shows the **whole** file every
  time it fires, and it fires on first install too.

### Android — existing infrastructure to reuse

- **Markdown:** Markwon via `ui/userguide/MarkdownRenderer.kt` and
  `ui/userguide/MarkdownAssetLoader.kt` — already loads locale-scoped markdown
  assets, strips frontmatter, and rewrites image/link paths. The User Guide is
  the direct analog.
- **Version:** `util/AppVersion.kt` reads `versionName`/`versionCode` from
  `PackageInfo` at runtime. **Use this** — it deliberately avoids the inlined
  `BuildConfig.VERSION_*` constants, which go stale across version bumps until a
  clean build.
- **Persistence:** `io/prefs/SettingsDataStoreImpl.kt` (Preferences DataStore).
- **Nav/entry points:** `ui/navigation/Routes.kt` (`AboutRoute`,
  `FontLicensesRoute`, `UserGuideRoute`, …). **Gotcha:** there are two nav graphs
  — compact and expanded/tablet (see commit `695209a`, which fixed a route added
  to only one). Any manual-entry route must be registered in **both**.
- **Asset listing:** `AssetManager.list("whatsnew/en")` enumerates bundled files,
  so a history screen needs no separate manifest.

## Decisions (locked)

| Question | Decision |
| --- | --- |
| Content source | **Curated, decoupled** from `CHANGELOG.md`; hand-synced at release-prep time. |
| Scope shown on update | **Only the new release**, with an optional "See previous releases" link. |
| Localization | **Full locale set** (en + placeholder files for all supported locales), following the guide pattern. |
| Fresh install | **Suppress** What's New; show an optional dismissible **User Guide nudge** instead. A null marker is disambiguated from "upgrade from a pre-feature build" via `isInitialSyncPerformed()` — see Resolved decisions §6. |
| Doc status | This spec stays **untracked** for now (active unrelated branch in flight). |
| Release dates | Each `whatsnew/*.md` carries a `date:` YAML frontmatter field, shown in the history list (not the popup sheet — see Resolved decisions §7). |

## Design

### 1. Content assets

Per-version, per-locale markdown files:

```
app/src/main/assets/whatsnew/<locale>/<versionName>.md
```

- File name is the exact `versionName` the notes should first appear on, e.g.
  `1.0.0.md`. (A `-snapshot`/`-rc` build has no matching file and therefore never
  fires — a natural gate.)
- Locales mirror the guide: `en, de, es, fr, gl, pl, pt, ru, uk, zh`. English is
  authored; the others ship as **English placeholders** until translated (same
  policy as `strings.xml` and the guide). Locale selection + `en` fallback reuse
  the logic in `MarkdownAssetLoader.getLocalePath()`.
- Content is authored in the explanatory/marketing tone of iOS's
  `RELEASE_NOTES.md`: a short intro line optional, then feature sub-headings with
  bold lead-ins. Frontmatter and a leading `# H1` are stripped by the loader, so
  an optional `# What's New in 1.0.0` title is fine.

### 2. Persistence

Add to `SettingsDataStoreImpl`:

- `stringPreferencesKey("last_seen_whatsnew_version")` — the `versionName` whose
  notes were last shown (or recorded silently).
- `booleanPreferencesKey("welcome_guide_prompt_shown")` — whether the first-launch
  User Guide nudge has been shown.

Expose reads as flows and suspend writes, matching the existing DataStore surface.

### 3. Trigger logic (on update)

Evaluated once per app entry into the authenticated shell (a `LaunchedEffect` in
`AppShell`, on the *authenticated* path only — never on `WelcomeRoute`).

```
current = AppVersion.versionName(context)
lastSeen = prefs.last_seen_whatsnew_version   // may be null

if lastSeen == null:
    prefs.last_seen_whatsnew_version = current
    if settingsDataStore.isInitialSyncPerformed():
        // No marker, but this account already has synced data: an upgrade
        // from a build that predates this feature, not a fresh install.
        // Treat like any other version change — see Resolved decisions §6.
        if assetExists("whatsnew/<locale-or-en>/$current.md"):
            show What's New sheet for `current`
    else:
        // Genuinely fresh install. Do NOT show What's New.
        maybeShowWelcomeGuidePrompt()          // see §5
else if current != lastSeen AND assetExists("whatsnew/<locale-or-en>/$current.md"):
    show What's New sheet for `current`
    prefs.last_seen_whatsnew_version = current
else if current != lastSeen:
    // Updated to a build with no notes (rc/snapshot). Advance the marker so we
    // don't nag on the next real update — see Resolved decisions §1.
    prefs.last_seen_whatsnew_version = current
```

- Keying on exact `versionName` equality + file existence means **no version
  ordering/parsing** is required.
- The marker is advanced when the sheet is *shown* (not on dismiss), so a
  mid-session process death can't re-trigger it. Acceptable trade-off: a crash
  before the user reads it means they miss it, but it's always available from
  About.

### 4. Presentation (the sheet)

- A `ModalBottomSheet` whose visibility is **state in `AppShell`**, not a nav
  route. This sidesteps the dual-nav-graph trap entirely and keeps it as an
  overlay above whatever screen is active.
- Body: `MarkdownRenderer` on the loaded doc. Header: "What's New" + the version.
  Primary action: **Got it**. Any dismissal path — tapping it, swipe-away, or
  tap-outside — is equivalent, since the marker was already advanced at
  show-time (§3); no extra dismiss-tracking state needed.
- Reuse the guide's markdown styling for visual consistency.
- No "See previous releases" link in v1 — History is deferred (§6).

### 5. First-launch User Guide nudge (fresh-install alternative)

Shown once, only on the fresh-install branch of §3 (or the first time the
authenticated shell is reached with `welcome_guide_prompt_shown == false`).

- Presentation: a small dismissible dialog or compact bottom sheet — **not** the
  full-screen guide. Copy: a one-line welcome + "New here? Take a quick tour of
  the User Guide to get the most out of MyDeck."
- Actions:
  - **Open the User Guide** → navigate to `UserGuideRoute` (existing).
  - **Not now** → dismiss.
- Either action sets `welcome_guide_prompt_shown = true` so it never reappears.
- This is intentionally lightweight (reuses the existing guide); it is **not** an
  interactive coached overlay. An overlay-style tutorial is noted as a possible
  future enhancement in Open decisions.

### 6. Manual entry + history (both shipped)

- **History screen:** `WhatsNewHistoryRoute` (`ui/whatsnew/WhatsNewHistoryScreen.kt`
  + `WhatsNewHistoryViewModel.kt`) lists every version with notes for the
  resolved locale via `WhatsNewAssetLoader.listAvailableVersions()`
  (`context.assets.list(...)`, `.md` suffix stripped), sorted newest-first with
  a small numeric-aware `compareVersions` comparator (a plain string sort would
  put `"1.0.10"` before `"1.0.9"`, and would rank a release below its own `-rc`
  builds). Tapping a row loads that version's notes and shows them in the same
  `WhatsNewSheet` used elsewhere. Registered in **both** `NavHost` blocks in
  `AppShell.kt` (the inline one in `CompactAppShell` and the shared
  `AppShellNavHost` used by `MediumAppShell`/`ExpandedAppShell`).
- **About screen:** the "What's New" row now navigates to `WhatsNewHistoryRoute`
  (via the existing `NavigationEvent` channel pattern) instead of opening the
  current version's sheet directly — the top entry in history *is* the current
  version, so this subsumes the original v1 behavior without a second affordance.
  The row is gated on `AboutViewModel.UiState.hasWhatsNewHistory`
  (`listAvailableVersions().isNotEmpty()`), not on the current version having
  notes specifically.
- **Auto-triggered sheet:** the on-update `WhatsNewSheet` shown from `AppShell`
  gained the `onSeePreviousReleases` link (dismisses the sheet, then navigates to
  `WhatsNewHistoryRoute`).

## Files touched

- **New:** `app/src/main/assets/whatsnew/<locale>/<version>.md` (all locales).
- **New:** `ui/whatsnew/WhatsNewAssetLoader.kt`, `WhatsNewViewModel.kt`,
  `WhatsNewSheet.kt`, `WelcomeGuideNudgeDialog.kt`, `WhatsNewHistoryViewModel.kt`,
  `WhatsNewHistoryScreen.kt`.
- **Edit:** `io/prefs/SettingsDataStore.kt` + `SettingsDataStoreImpl.kt` — two
  new keys.
- **Edit:** `ui/navigation/Routes.kt` — `WhatsNewHistoryRoute`.
- **Edit:** `ui/shell/AppShell.kt` — trigger `LaunchedEffect` + sheet/dialog
  overlays (once, top-level); `WhatsNewHistoryRoute` registered in both
  `NavHost` blocks.
- **Edit:** `ui/about/AboutViewModel.kt` + `AboutScreen.kt` — "What's New" row
  navigating to history.
- **New strings** in `values/strings.xml` **and every** `values-*/strings.xml`
  (English placeholders): sheet title, "Got it", "See previous releases",
  welcome-nudge title/body, "Open the User Guide", "Not now", About row
  label/subtitle, history screen title/empty-state.
- **New test:** `WhatsNewAssetLoaderTest.kt` — covers `normalizeVersion` and the
  `compareVersions` numeric/pre-release ordering (pure functions, no Robolectric
  needed).

## Workflow / process changes

- Add a release-prep step (CLAUDE.md + `docs/WORKFLOW.md`): when the release PR
  moves `[Unreleased]` into a versioned heading, it must also **author
  `whatsnew/en/<version>.md`** (curated from, but not a copy of, the changelog)
  and drop English placeholder copies into every other locale.
- Update the User Guide (`assets/guide/en/…`, likely `settings.md` or
  `getting-started.md`) to mention the What's New surface and the About entry.

## MyDeck port notes

- The Android package is already `com.mydeck.app`, so this is largely a content +
  branding swap, not a code fork.
- Keep all logic asset-driven and free of hardcoded product names; the only
  MyDeck-specific artifacts are the `whatsnew/**` copy and any product-name
  strings.

## Resolved decisions

1. **rc/snapshot marker advance (§3, third branch):** the marker always advances,
   whether or not the current version has a notes file. The trigger only ever
   checks for a file matching the *exact current running version*, and never
   looks backward across skipped versions — so "advance always" vs. "advance
   only when shown" are functionally equivalent here; always-advance is simpler.
   Note this means a user who updates straight through several versions (e.g.
   1.0.0 → 1.0.3) only ever auto-sees 1.0.3's notes, never the skipped-over ones
   — those remain reachable manually via the History screen (below).
2. **History shipped as a fast-follow (§6).** `WhatsNewHistoryRoute` lists every
   version with notes, newest-first via a numeric-aware comparator, and is
   registered in both nav graphs. **Refined after initial ship:** the About row
   opens the *current* version's note directly if one exists (falling back
   straight to history if not) — closer to the original v1 behavior than pure
   history-first, since that's the more common case. The sheet itself (both the
   auto-triggered one and About's) carries a "See previous releases" link into
   history; tapping any past entry there reopens it in the same sheet.
3. **Sheet dismissal:** any dismissal — "Got it", swipe-away, or tap-outside —
   is equivalent, since the marker is already advanced at show-time (§3). No
   extra state needed; standard `ModalBottomSheet` dismiss behavior applies.
4. **Welcome nudge vs. What's New collision:** on fresh install we record the
   version silently and show only the guide nudge — never both surfaces at once.
5. **Future (out of scope):** an optional interactive coached-overlay tutorial
   (spotlight tips on real UI) as a richer alternative to the guide-link nudge.
6. **Null-marker disambiguation via `isInitialSyncPerformed()` (2026-07-09).**
   The original design collapsed "fresh install" and "upgrade from a
   pre-feature build" into one null-marker branch that always suppressed the
   sheet, since a bare `null` can't tell them apart on its own — but it doesn't
   have to be guessed blindly. `isInitialSyncPerformed()` (existing sync-state
   flag) is already a reliable proxy: a genuinely new user hasn't completed
   their first sync yet at this point, while an existing account upgrading
   from a pre-feature build has been syncing for a while. So on `lastSeen ==
   null`: if `isInitialSyncPerformed()` is true, treat it as a normal version
   change (show the sheet if a note exists for the current version); if false,
   treat it as fresh (silent record + guide nudge). No new state — reuses an
   existing flag. Edge case: a user who signed in but hadn't completed their
   first sync yet (e.g., offline) on the old build, then upgrades, incorrectly
   gets the nudge once instead of the sheet — rare and harmless.
7. **Release dates via frontmatter (2026-07-09).** Convention check: iOS's
   `RELEASE_NOTES.md` and typical App/Play Store "what's new" text don't date
   their entries — the OS/store already timestamps "updated on," making an
   in-app date redundant for the *current*-version popup sheet. The **history**
   list is different: it's changelog-style browsing spanning months (including
   the MyDeck → Readeck rebrand), where "how long ago" is genuinely useful
   context, and `CHANGELOG.md` already records a date per version anyway. So:
   no date in `WhatsNewSheet` itself; each `whatsnew/*.md` carries a `date:
   YYYY-MM-DD` YAML frontmatter field, parsed by `WhatsNewAssetLoader
   .parseFrontmatterDate` (a pure, unit-tested companion function; malformed or
   missing dates degrade to "no date shown," never a crash) and displayed
   under the version in each `WhatsNewHistoryScreen` row.

## Implementation checklist

- [ ] DataStore keys + interface/read/write plumbing.
- [ ] `WhatsNewAssetLoader` (locale-fallback markdown load, null on missing file).
- [ ] `WhatsNewViewModel` (trigger evaluation + persisted marker).
- [ ] `WhatsNewSheet` composable (Markwon render, header, "Got it" via `dismissSheet`).
- [ ] `WelcomeGuideNudgeDialog` composable.
- [ ] `AppShell` trigger `LaunchedEffect` + overlay wiring — **once**, at the
      top-level `AppShell` composable (not duplicated per shell tier).
- [ ] About row (`AboutViewModel` + `AboutScreen`) opening current version's notes.
- [ ] `whatsnew/en/<current-version>.md` authored; placeholders in all locales.
- [ ] New strings in all `strings.xml` files.
- [ ] User Guide + CHANGELOG `[Unreleased]` updated.
- [ ] `./scripts/ci-verify.sh` green.

---

## Implementation plan (finalized 2026-07-08)

This section is the concrete build plan, refined after reviewing the actual
`AppShell.kt` / `AboutScreen.kt` / `SettingsDataStoreImpl.kt` code. It supersedes
the file/route assumptions above where they differ (notably: **no new nav
route is needed for v1** — see §4 below).

### Key discovery that simplifies the design

`AppShell.kt` has a **single top-level `AppShell()` composable**
(`ui/shell/AppShell.kt:88-179`) that dispatches to `CompactAppShell` /
`MediumAppShell` / `ExpandedAppShell` based on window size — each of those has
its *own* `NavHost`. Rather than duplicating the trigger/sheet logic three
times, or adding a nav route that would need registering in three separate
`NavHost` blocks, the trigger + overlays live **once, at the top of
`AppShell()`**, as siblings placed *after* the `when (layoutTier) { ... }`
block. `ModalBottomSheet` and `AlertDialog` render into their own window, so
this placement works regardless of which tier is active — no layout
restructuring required.

### 1. Content assets

`app/src/main/assets/whatsnew/<locale>/<versionName>.md` — one file per version
per locale (`en, de, es, fr, gl, pl, pt, ru, uk, zh`; English authored, others
placeholders per `CLAUDE.md`'s localization rule). No file for a version = the
auto-trigger silently skips it (rc/snapshot builds naturally excluded). Author
the `en` file for the next real release as part of this work, sourced from —
not copied verbatim from — the `CHANGELOG.md` `[Unreleased]` section, in the
iOS `RELEASE_NOTES.md` tone (bold lead-ins, short explanatory phrasing, skip
internal-only entries).

### 2. New: `WhatsNewAssetLoader` (`ui/whatsnew/WhatsNewAssetLoader.kt`)

`@Singleton class WhatsNewAssetLoader @Inject constructor(@ApplicationContext context)`.
One method: `fun loadNotesForVersion(version: String): String?`. Mirrors
`MarkdownAssetLoader.getLocalePath()`'s locale-list/`en`-fallback logic (a
small parallel implementation, not a shared base class — `MarkdownAssetLoader`
returns a rendered "Error Loading Content" placeholder string on failure,
whereas this needs a clean `null` to gate the trigger; coupling the two through
inheritance for ~15 lines of locale logic isn't worth it). Strips frontmatter /
leading H1 the same way as the guide loader. Returns `null` on `IOException`
(missing file for this version — expected, not an error).

### 3. New: `WhatsNewViewModel` (`ui/whatsnew/WhatsNewViewModel.kt`)

```kotlin
@HiltViewModel
class WhatsNewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loader: WhatsNewAssetLoader,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {
    var uiState by mutableStateOf(WhatsNewUiState())
        private set
    private var evaluated = false

    fun evaluateIfNeeded() {
        if (evaluated) return
        evaluated = true
        viewModelScope.launch {
            val current = AppVersion.versionName(context)
            val lastSeen = settingsDataStore.getLastSeenWhatsNewVersion()
            if (lastSeen == null) {
                settingsDataStore.saveLastSeenWhatsNewVersion(current)
                if (!settingsDataStore.isWelcomeGuidePromptShown()) {
                    uiState = uiState.copy(showGuideNudge = true)
                }
                return@launch
            }
            if (current != lastSeen) {
                val notes = loader.loadNotesForVersion(current)
                settingsDataStore.saveLastSeenWhatsNewVersion(current)
                if (notes != null) {
                    uiState = uiState.copy(whatsNewContent = notes, whatsNewVersion = current)
                }
            }
        }
    }

    fun onWhatsNewDismissed() { uiState = uiState.copy(whatsNewContent = null) }
    fun onGuideNudgeDismissed() {
        uiState = uiState.copy(showGuideNudge = false)
        viewModelScope.launch { settingsDataStore.saveWelcomeGuidePromptShown(true) }
    }
    fun onGuideNudgeOpenGuide() = onGuideNudgeDismissed() // marking-seen is identical either way
}

data class WhatsNewUiState(
    val whatsNewContent: String? = null,
    val whatsNewVersion: String = "",
    val showGuideNudge: Boolean = false,
)
```

`evaluateIfNeeded()` is idempotent per ViewModel instance — a fresh evaluation
only happens if the process/ViewModelStore is actually recreated (correct
"once per cold start" granularity).

### 4. Wire into `AppShell` (`ui/shell/AppShell.kt`)

At the top of `AppShell()`, alongside the existing `layoutTier` computation:

```kotlin
val whatsNewViewModel: WhatsNewViewModel = hiltViewModel()
val whatsNewState = whatsNewViewModel.uiState
val token = settingsDataStore?.tokenFlow?.collectAsState()?.value
LaunchedEffect(token) {
    if (!token.isNullOrBlank()) whatsNewViewModel.evaluateIfNeeded()
}
```

After the existing `when (layoutTier) { ... }` block:

```kotlin
whatsNewState.whatsNewContent?.let { content ->
    WhatsNewSheet(
        version = whatsNewState.whatsNewVersion,
        content = content,
        onDismiss = whatsNewViewModel::onWhatsNewDismissed,
    )
}
if (whatsNewState.showGuideNudge) {
    WelcomeGuideNudgeDialog(
        onOpenGuide = {
            whatsNewViewModel.onGuideNudgeOpenGuide()
            navController.navigate(UserGuideRoute) { launchSingleTop = true }
        },
        onDismiss = whatsNewViewModel::onGuideNudgeDismissed,
    )
}
```

This is the **only** change to `AppShell.kt` — no nav-graph edits, applies
uniformly across all three tiers.

### 5. New: `WhatsNewSheet` (`ui/whatsnew/WhatsNewSheet.kt`)

`ModalBottomSheet` + `rememberModalBottomSheetState()`. Header: "What's New" +
version subtitle. Body: `rememberMarkwon(onSectionNavigate = {})` +
the same `AndroidView(TextView)` pattern as
`UserGuideSectionScreen.kt:150-174` (selectable text, `LinkMovementMethod`,
`applyMarkwonColors`). Footer: single **Got it** button routed through
`ui/components/BottomSheetDismiss.kt`'s `dismissSheet(sheetState) { onDismiss()
}` (the #25 consistency helper), and `onDismissRequest` wired the same way, so
button/swipe/scrim all animate identically. No history link in v1.

### 6. New: `WelcomeGuideNudgeDialog` (`ui/whatsnew/WelcomeGuideNudgeDialog.kt`)

Simple `AlertDialog`: title + one-line body, confirm **Open the User Guide**
(`onOpenGuide`), dismiss **Not now** (`onDismiss`).

### 7. About row (manual re-open)

- `AboutViewModel.kt`: inject `WhatsNewAssetLoader`, add `whatsNewContent:
  String?` + `showWhatsNewSheet: Boolean` to `UiState`, add `onClickWhatsNew()`
  (loads via `viewModelScope.launch`, mirroring `loadAndRefreshServerInfo`'s
  style) and `onDismissWhatsNewSheet()`.
- `AboutScreen.kt`: add a "What's New" `Row` after the existing Font Licenses
  row (`AboutScreen.kt:445-468` is the template), wired to `onClickWhatsNew`.
  Render `WhatsNewSheet` conditionally at the bottom of `AboutScreenContent`.
- No `Routes.kt` / `NavHost` changes for v1.

### 8. `SettingsDataStore` additions

```kotlin
suspend fun getLastSeenWhatsNewVersion(): String?
suspend fun saveLastSeenWhatsNewVersion(version: String)
suspend fun isWelcomeGuidePromptShown(): Boolean
suspend fun saveWelcomeGuidePromptShown(shown: Boolean)
```

New keys in `SettingsDataStoreImpl`, stored in `userPreferences` (non-sensitive
tier, matching `KEY_LAYOUT_MODE`/`KEY_SHOW_COMPACT_FAVICONS`):
`stringPreferencesKey("last_seen_whatsnew_version")`,
`booleanPreferencesKey("welcome_guide_prompt_shown")`. No migration entries
(brand-new keys, no legacy counterpart). Update any test fake implementing
`SettingsDataStore` with the two new methods.

### 9. Strings & docs

New strings (sheet title, "Got it", nudge title/body, "Open the User Guide",
"Not now", About row label+subtitle) in `values/strings.xml` and every
`values-*/strings.xml`. Update `CHANGELOG.md` `[Unreleased]`, a User Guide page
(English only), and add the release-prep step (author `whatsnew/en/<version>.md`)
to `docs/WORKFLOW.md`/`CLAUDE.md`.

### Files touched

**New:** `assets/whatsnew/<locale>/<version>.md`,
`ui/whatsnew/WhatsNewAssetLoader.kt`, `ui/whatsnew/WhatsNewViewModel.kt`,
`ui/whatsnew/WhatsNewSheet.kt`, `ui/whatsnew/WelcomeGuideNudgeDialog.kt`.
**Edited:** `ui/shell/AppShell.kt`, `io/prefs/SettingsDataStore.kt` +
`SettingsDataStoreImpl.kt`, `ui/about/AboutViewModel.kt` + `AboutScreen.kt`,
all `strings.xml`, `CHANGELOG.md`, a guide page, `docs/WORKFLOW.md`/`CLAUDE.md`.
**Not touched:** `ui/navigation/Routes.kt`, both `NavHost` blocks (no new
route needed in v1).

### Verification

1. `./gradlew :app:assembleDebugAll`, `:app:testDebugUnitTestAll`,
   `:app:lintDebugAll` — compiles, existing unit tests (incl. any
   `SettingsDataStore` fake) still pass, all-locales string lint passes.
2. Manual, on-device:
   - Fresh install (clear app data) → sign in → guide nudge appears once;
     "Open the User Guide" navigates; "Not now" dismisses; never reappears.
   - Simulate an update (rewrite the stored marker to an older value, or bump
     `versionName` locally to one with a matching `whatsnew/en/<version>.md`)
     → sheet shows only that version's notes; button/swipe/tap-outside all
     dismiss identically; doesn't reappear next launch.
   - About → "What's New" reopens current version's notes on demand.
   - A version with no matching file → no sheet, no crash, marker still
     advances.
3. Dark/light theme rendering of the sheet's markdown matches the User Guide's
   existing look (shared `MarkdownRenderer` styling).

---

## Addendum (2026-07-09): null-marker refinement, release dates, rc1–rc5 content

Covers the two logic refinements in Resolved decisions §6–§7, plus authoring
real content for the backlog of already-shipped versions.

### Code changes

- `ui/whatsnew/WhatsNewViewModel.kt` — `evaluateIfNeeded()`'s null-marker branch
  now checks `settingsDataStore.isInitialSyncPerformed()` before deciding
  between "show the sheet" and "show the guide nudge" (§6). Extracted a
  `showNotesIfAvailable(version)` helper shared by both the null-marker and
  normal version-change paths.
- `ui/whatsnew/WhatsNewAssetLoader.kt` — new `WhatsNewHistoryEntry(version,
  date: LocalDate?)` data class; `listAvailableVersions()` now returns
  `List<WhatsNewHistoryEntry>` instead of `List<String>` (still newest-first);
  new pure companion function `parseFrontmatterDate(raw): LocalDate?`; shared
  `readRawFile(version)` private helper so `loadNotesForVersion` and the new
  date parsing don't duplicate the asset-open/try-catch.
- `ui/whatsnew/WhatsNewHistoryViewModel.kt` / `WhatsNewHistoryScreen.kt` —
  updated for the `WhatsNewHistoryEntry` type; each history row now shows the
  parsed date (ISO `YYYY-MM-DD`, locale-neutral) under the version, when present.
- `WhatsNewAssetLoaderTest.kt` — added `parseFrontmatterDate` cases: reads a
  valid date, returns null with no frontmatter, returns null with frontmatter
  but no `date:` field, returns null (not a crash) for a malformed date.
- `docs/WORKFLOW.md` — release-prep step 4 now shows the `date:` frontmatter
  block in its `whatsnew/en/X.Y.Z.md` template.

### Content authored

Real (not placeholder-only) English content for every version since the
MyDeck → Readeck rebrand, each with `date:` frontmatter matching its
`CHANGELOG.md` heading, curated (not copy-pasted) per the workflow rule —
internal-only entries (F-Droid build changes, dead-code removal) dropped:

- `whatsnew/en/1.0.0-rc1.md` (2026-06-08) — the rebrand itself: new name, new
  home on Codeberg.
- `whatsnew/en/1.0.0-rc2.md` (2026-06-21) — offline pinning, storage-pool split,
  foreground-service downloads.
- `whatsnew/en/1.0.0-rc3.md` (2026-06-27) — quick label-edit, UI-customization
  toggles, label search ranking, error/no-content badges, assorted fixes.
- `whatsnew/en/1.0.0-rc4.md` (2026-06-29) — nightly-server sign-in fix.
- `whatsnew/en/1.0.0-rc5.md` (2026-07-03) — added `date:` frontmatter to the
  file authored during initial development (content unchanged).

English-placeholder copies (verbatim, per the localization rule) created for
all five versions across all nine other locales (`de, es, fr, gl, pl, pt, ru,
uk, zh`) — 45 additional files.

**v1.0.0 (production release) is deliberately not authored yet** — see the
"What to do with v1.0.0" discussion (2026-07-09, not written into this doc
verbatim): plan is a short milestone-style note ("official 1.0.0 release, now
on Google Play," with a one-line nod to the MyDeck rebrand) rather than a
bare "no changes" note, authored at actual release-prep time once versioning
is finalized.

### Note on auto-popup semantics for already-installed testers

Because this feature didn't exist before rc5, any tester already on rc5 will
hit the null-marker branch the first time they update to whichever version
ships this feature. With the §6 refinement, since they'll have
`isInitialSyncPerformed() == true`, they *will* see that version's sheet (if a
note exists) rather than just the nudge — the original gap this refinement
was built to close.
