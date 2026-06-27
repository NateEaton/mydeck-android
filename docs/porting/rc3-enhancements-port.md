# Port checklist — rc3 enhancements (Readeck for Android → MyDeck)

**Uses:** the methodology in [`mydeck-readeck-port.md`](mydeck-readeck-port.md) (roles, divergence map, gotchas). Read that first.
**Status — FINAL.** Port complete; cherry-pick of `85ddf2a` applied cleanly; fixup commit removed SOURCE-only spec and corrected guide branding; dead code cleanup commit followed (§3).
**Source change set:** squash commit **`85ddf2a`** on Readeck for Android `main` ("rc3 enhancements + UI polish (toward v1.0.0) (#12)") — **35 files**. Durable port source.
**Direction:** SOURCE = Readeck for Android → TARGET = MyDeck (opposite of the offline-content-rework port).

> No DB migration. All new string keys are brand-neutral (§4). The only mandatory transform is the 4 user-guide docs (§4.2).

---

## 1. Change set at a glance (logical units)

| Unit | Essence |
|---|---|
| **BF-1** | `BookmarkCompactCardNarrow` favorite button: `32dp → 36dp` icon, `verticalAlignment = CenterVertically` on inner Row |
| **E-1** | "Show source icons" pref + `CompactStatusRail` early-return when off; `LocalOpenWebPageExternally` CompositionLocal added |
| **E-2** | `onClickEditLabels` param on all 8 card composables; label `IconButton` added to all 5 action rows; `EditLabelsTarget` state + `onOpenEditLabels` / `onDismissEditLabels` / `onSetLabelsForBookmark` in ViewModel; `LabelPickerBottomSheet` in `BookmarkListScreen` with `remember(target)` fix |
| **E-3b** | `OpenWebPagesIn` enum + `Internal browser` Switch in Reading section; `LocalOpenWebPageExternally` drives card globe vs ↗ icon; `OriginalViewNoContentPlaceholder`; `BookmarkDetailTopBar` open-in-new button in Original View; `BookmarkDetailMenu` external-open path; gallery globe icon fix (`ImageGalleryOverlay`) |
| **E-4a** | `LazyColumn` `contentPadding = PaddingValues(bottom = 88.dp)` |
| **E-4b** | `showAddBookmarkFab` pref; FAB wrapped in `if (!isMultiSelectMode && showAddBookmarkFab.value)` |
| **E-5** | `LabelSearchSettingsViewModel` (new); `LabelSearchMatching` / `LabelSearchSort` enums (new); matching + sort flows in `LabelsBottomSheet`; glyph toggles (`a∗`/`∗a∗`, `abc`/`123`) + `TooltipBox` in sheet `actions`; sheet-title clickable scrolls to top |
| **UI-reorg** | `SettingsSectionHeader` composable; Settings screen reorganized into Appearance / Bookmark List (+ Swipe sub-group) / Reading / Sharing sections |
| **Accessibility** | All card action `IconButton` `48dp` / icon `24dp`; Mosaic View-web-page icon guarded by `!isWideLayout`; grid landscape image height `100dp` on `smallestScreenWidthDp < 600`; Wide Grid labels Row moved below `Spacer(weight(1f))` |
| **Guides** | `settings.md`, `your-bookmarks.md`, `organizing.md`, `reading.md` — new sections and updated descriptions |

---

## 2. New production files to create in TARGET (absent both sides — add verbatim)

- `domain/model/LabelSearchPreferences.kt` — `LabelSearchMatching` enum (`STARTS_WITH`, `CONTAINS`), `LabelSearchSort` enum (`ALPHABETICAL`, `BY_FREQUENCY`)
- `domain/model/OpenWebPagesIn.kt` — `OpenWebPagesIn` enum (`IN_APP`, `EXTERNAL_BROWSER`)
- `ui/list/LabelSearchSettingsViewModel.kt` — `@HiltViewModel`; reads `labelSearchMatchingFlow` / `labelSearchSortFlow`; `toggleMatching()` / `toggleSort()` via `viewModelScope`

Tests to add (within existing test files, not new files):
- `UiSettingsScreenUnitTest`: new state fields + callback no-ops
- `UiSettingsViewModelTest`: mockk stubs for `isShowCompactFavicons`, `isShowAddBookmarkFab`, `getOpenWebPagesIn`, and their save counterparts
- `BookmarkListViewModelTest`: stubs for `showCompactFaviconsFlow`, `showAddBookmarkFabFlow`, `openWebPagesInFlow`
- `BookmarkDetailViewModelTest`: stub for `openWebPagesInFlow` returning `MutableStateFlow(OpenWebPagesIn.IN_APP)`

---

## 3. Files to REMOVE in TARGET

The rc3 change set itself adds no deletions, but the cherry-pick carried two dead composables that rc3 made unreachable by consolidating all cards onto `BookmarkDownloadStatusIndicator`:

- `OfflineStateIndicator` (thumbnail badge style) — in `ui/list/BookmarkCard.kt`
- `CompactOfflineStateIndicator` (compact icon style) — in `ui/list/BookmarkCard.kt`

Both are `@Composable private fun` with zero call sites. Delete them as a follow-up commit after the cherry-pick lands. (They were removed from the SOURCE repo at the same time.)

---

## 4. Divergence transforms (the must-change points)

### 4.1 New strings — 15 brand-neutral keys

Insert surgically into TARGET's `values/strings.xml` and all MyDeck locale folders (confirm the locale set in TARGET — MyDeck may differ from Readeck's 9 locales):

| Key | English value |
|---|---|
| `ui_settings_section_appearance` | "Appearance" |
| `ui_settings_section_bookmark_list` | "Bookmark List" |
| `ui_settings_section_reading` | "Reading" |
| `ui_settings_section_sharing` | "Sharing" |
| `ui_settings_show_compact_favicons` | "Show source icons" |
| `ui_settings_show_compact_favicons_desc` | "Display website icons in compact list view" |
| `ui_settings_show_add_fab` | "Show add-bookmark button" |
| `ui_settings_show_add_fab_desc` | "Display the + button in the bookmark list" |
| `ui_settings_internal_browser_title` | "Internal browser" |
| `ui_settings_internal_browser_desc` | "Web pages open in-app for an immersive experience. When off, they open in your external browser." |
| `label_search_matching_prefix` | "Match start of label" |
| `label_search_matching_contains` | "Match anywhere in label" |
| `label_search_sort_alpha` | "Sort alphabetically" |
| `label_search_sort_count` | "Sort by most used" |
| `edit_labels` | "Edit labels" |

**None of these values contain "Deck", "MyDeck", or "Readeck" — port verbatim.** Before inserting, verify `edit_labels` and the `ui_settings_section_*` keys don't already exist in TARGET (MyDeck may have them under the same names).

### 4.2 Guide docs — adapt for TARGET branding

All four guide files need updating for MyDeck wording. Key replacements:
- "Deck" (the view name) → "My List"
- Any "Readeck for Android" references → "MyDeck"
- Any paths to settings that use TARGET-specific section names → verify they match MyDeck's settings structure

The guide content is otherwise feature-identical; no new concepts to invent.

### 4.3 No DB migration

rc3 is entirely UI and preferences — no Room schema changes, no version bump, no migration to register or renumber.

### 4.4 `docs/specs/rc3-enhancements-spec.md`

The spec file is in Readeck for Android's `docs/specs/`. It is **SOURCE-only** — do not copy it to TARGET. (It was authored against Readeck for Android's repo structure and audience.)

---

## 5. Design decisions the porter MUST preserve (don't "fix" these)

- **`remember(target)` in E-2 (label picker recomposition fix).** `val editLabelsMode = remember(target) { LabelPickerMode.MultiSelect(...) }` — the `remember` key **must be `target`**, not the inline `LabelPickerMode` object. Constructing inline causes the picker's in-progress selection to reset on every recomposition (labels uncheck on tap). This was a real bug caught in rc3; do not simplify it.
- **`LabelSearchSettingsViewModel` via `hiltViewModel()` in the sheet.** All 6 label-picker call sites share the same persisted settings because each call site gets a Hilt-scoped ViewModel rather than the settings being threaded through parameters. Do not refactor this to param-threading.
- **`CompositionLocalProvider(LocalOpenWebPageExternally)` in `BookmarkListScreen`.** The `Boolean` is pushed via a `CompositionLocal` so it reaches all card composables without threading a parameter through 8 composable signatures. Preserve the Local approach; do not add a parameter.
- **Mosaic `!isWideLayout` guard on the View-web-page `IconButton`.** The icon is kept on single-column portrait (phone) but dropped on multi-column (tablet / landscape). This was a deliberate density decision, not an oversight.
- **`smallestScreenWidthDp < 600` for landscape grid image height.** The `100dp` grid image height applies on phones in landscape; tablets stay at `140dp`. The `isInGrid` guard is also required.
- **`OriginalViewNoContentPlaceholder` path (E-3b).** When `openWebPageExternally = true` and content mode is ORIGINAL, the screen shows the title/description placeholder — it does NOT open an external browser automatically. The top-bar ↗ icon is how the user opens it. Do not change this flow.

---

## 6. Drift assessment — complete before applying (§4 of methodology)

This spec was written from the SOURCE before the TARGET assessment was run. The implementing agent must complete the assessment before choosing cherry-pick vs manual re-apply.

**Shared history check:**

```
git -C /path/to/MyDeck merge-base HEAD /path/to/readeck-android/85ddf2a
```

If a merge-base exists and the key files (below) are byte-identical between MyDeck HEAD and readeck-android's pre-rc3 base (`731a753`), cherry-pick the squash commit `85ddf2a`, then resolve only the §4 transforms. If significant drift exists, manual re-apply file by file using the source diff as spec is safer.

**Files most likely to have drift — check these first:**

| File | Why it might diverge |
|---|---|
| `ui/settings/UiSettingsScreen.kt` | MyDeck may have a different settings structure or additional prefs |
| `ui/settings/UiSettingsViewModel.kt` | Same |
| `io/prefs/SettingsDataStore.kt` / `Impl.kt` | MyDeck may have prefs not in readeck-android |
| `ui/list/BookmarkCard.kt` | Largest rc3 change; MyDeck may have card-level differences |
| `ui/list/LabelsBottomSheet.kt` | Label picker differences possible |
| `ui/list/BookmarkListScreen.kt` | FAB and multi-select integration |
| `ui/list/BookmarkListViewModel.kt` | ViewModel state additions |

Files less likely to drift (touched only by pin/unpin which was ported verbatim):
- `ui/detail/BookmarkDetailScreen.kt`
- `ui/detail/components/BookmarkDetailMenu.kt`
- `ui/detail/components/BookmarkDetailTopBar.kt`
- `ui/detail/BookmarkDetailViewModel.kt`
- `ui/detail/ImageGalleryOverlay.kt`

---

## 7. On-device verifications to repeat in TARGET

After build/test/lint green, install via `./scripts/install-phone.sh` and check:

(a) **E-1:** Toggle "Show source icons" off → Compact view favicon column disappears, text uses full width; toggle on → favicon returns.
(b) **E-2:** Tap label icon on a card → picker opens with current labels checked; add/remove; tap Done → card label row updates without leaving the list.
(c) **E-3b internal browser on:** Tap globe on any card → Original View opens in-app. Tap overflow "View web page" from reader → in-app. No-content bookmark opens placeholder in-app with ↗ button.
(d) **E-3b internal browser off:** Card icon changes from globe to ↗; tap opens external browser. Original View shows placeholder with ↗ button.
(e) **E-4b:** Toggle "Show add-bookmark button" off → FAB disappears; toggle on → FAB returns.
(f) **E-5:** Open label picker, tap `a∗` glyph → switches to `∗a∗` (contains matching); list re-filters. Tap `abc` → switches to `123` (frequency sort); list re-sorts. Tooltips show on long-press.
(g) **Accessibility:** On Grid portrait card, all 5 action icons visually ~48dp touch target and 24dp glyph. On Mosaic multi-column, globe/↗ icon is absent; on Mosaic single-column phone portrait it is present.
(h) **Landscape grid:** On phone in landscape Grid layout, card image is shorter (`100dp`) than tablet (`140dp`); labels appear just above action row regardless of title length.

---

## 8. Final reconciliation (before declaring the port done)

- Confirm all 12 production-code files modified in SOURCE are updated in TARGET.
- Confirm 3 new domain/ViewModel files created.
- Confirm 15 string keys inserted across all TARGET locale files (surgical insert, not whole-file restore).
- Confirm 4 guide docs adapted for TARGET branding.
- `assembleDebugAll` / `testDebugUnitTestAll` / `lintDebugAll` green.
- §7 on-device checks pass.
- Push branch; open GitHub PR to trigger CI.
