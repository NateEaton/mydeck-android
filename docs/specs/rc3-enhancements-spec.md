# rc3 Enhancements — Implementation Spec

**Branch:** `feature/rc3-enhancements`  
**Target:** v1.0.0 (production Play Store release)  
**Scope:** readeck-android; all changes must also be ported to the MyDeck repo when complete (see [Porting to MyDeck](#porting-to-mydeck)).

---

## Status (as shipped in RC3)

This spec is retained (not archived) so the implemented decisions and the deferred ideas
stay discoverable.

**Implemented in RC3:**
- ✅ **BF-1** — Compact card favorite button size/alignment fix
- ✅ **BF-2** — Undo snackbar action color (`inversePrimary`)
- ✅ **E-4a** — FAB bottom content-padding fix
- ✅ **E-1** — "Show source icons" toggle (compact view)
- ✅ **E-4b** — "Show add-bookmark button" (FAB) toggle
- ✅ **E-2** — Quick label-edit button on bookmark cards (all layouts)
- ✅ **E-3b** — Shipped in two stages: first **two icon changes** (open-in-new icon on the Original
  View top bar; gallery link icon → globe), then — after the requester confirmed the Custom Tab was
  never the problem — a scoped **"Internal browser" switch** (Settings → User Interface → Reading).
  When off, the Original View path (card/overflow "View web page", and the no-content fallback) uses
  the external browser, with a clean title/description + "No content available" placeholder for the
  no-content case. In-article links, gallery links, and explicit Open-in-browser are unaffected. See
  [E-3b](#e-3b-open-external-content--revised-shipped-as-two-icon-changes).
- ✅ **E-5** — Label search ranking. Built as Settings controls, then **moved into the label-picker
  sheet header** as two glyph toggles (`∗a∗`/`a∗`, `abc`/`123`) with tooltips; choice persists globally.
  The Settings section was removed. See [E-5](#e-5-label-search-ranking--shipped-as-in-sheet-toggles-not-settings).

**Deferred for future consideration:**
- ⏸️ **E-3a** — Extraction-error badge on cards (needs new list-projection fields; see note below)

---

## Implementation Order

Ordered simplest → most complex. Each item is fully built, tested, and verified before moving to the next.

| # | Item | Type | Complexity |
|---|------|------|------------|
| 1 | [BF-1] Compact card favorite button size | Bug fix | Trivial |
| 2 | [BF-2] Undo snackbar bold action label | Bug fix | Trivial |
| 3 | [E-4a] FAB content padding fix | Bug fix | Trivial |
| 4 | [E-1] Hide source icons in compact view | Setting | Low |
| 5 | [E-4b] FAB visibility toggle | Setting | Low |
| ~~6~~ | ~~[E-3a] Error badge on bookmark cards~~ | **DEFERRED** | — |
| 6 | [E-2] Label quick-apply button on cards | Enhancement | Medium |
| 7 | [E-3b] Open external content — **revised to two icon changes** (no setting) | Icon change | Low |
| 8 | [E-5] Label search ranking settings | Settings (×2) | Medium |

> **E-3a deferred (back burner):** The error badge requires adding new fields to the
> list-display models. The error data is stored on `BookmarkEntity` (`hasServerErrors`,
> `errors`) — no DB migration or sync work needed — but the bookmark list uses a separate
> hand-written projection (`BookmarkListItemEntity` via the query builders in `BookmarkDao`)
> that does not carry any error column, and the `BookmarkListItem` domain model the cards
> receive has no error field. Surfacing the badge means: add `b.hasServerErrors` to both
> projection queries, add the field to `BookmarkListItemEntity` and `BookmarkListItem`, update
> the mapper, then the four card layouts. Deferred per decision to avoid adding new error
> fields to the list models for v1.0.0. The E-3b external-browser setting is unaffected and
> proceeds as planned.

---

## Bug Fixes

### BF-1: Compact Card Narrow — Favorite Button Size Mismatch

**Location:** `BookmarkCard.kt`, `BookmarkCompactCardNarrow`

**Root cause:** The favorite `IconButton` is hardcoded at 32dp/18dp while every other button in the same row is 36dp/20dp. Neither the outer nor inner `Row` specifies `verticalAlignment`, so the shorter button top-aligns and appears visually off.

**Fix:**
- `Modifier.size(32.dp)` → `Modifier.size(36.dp)` on the favorite `IconButton`
- `Modifier.size(18.dp)` → `Modifier.size(20.dp)` on the heart icon
- Add `verticalAlignment = Alignment.CenterVertically` to the inner action `Row`

**Verification:** Compact narrow card — heart icon is visually aligned with archive, globe, and delete icons.

**No strings. No settings. No localization impact.**

---

### BF-2: Undo Snackbar Action — Low-Contrast Label

**Problem:** The "Undo" action in snackbars uses `primary` as its color. On a dark snackbar surface in Paper dark mode, `primary` is a muted mid-tone with insufficient contrast. Bold text on the same muted color does not solve this — the root issue is color, not weight.

**Fix:** Set action text color to `MaterialTheme.colorScheme.inversePrimary` on the `Text(actionLabel)` call inside the existing custom snackbar composable at `BookmarkListScreen.kt:1753`. This is M3's intended action color for snackbar surfaces — high contrast on dark surfaces, clearly readable on light ones. No new composable wrapper needed.

**Fallback (hold in reserve):** If `inversePrimary` alone proves insufficient after visual testing, add `FontWeight.Bold` as a secondary distinction signal.

**Verification:** Undo label is clearly legible in both light and dark modes across all three appearance variants (Default, Paper, Dark); no layout shift or truncation.

**No strings. No settings. No localization impact.**

---

### E-4a: FAB Content Padding Fix

**Problem:** The `LazyColumn` in `BookmarkListScreen` has no bottom content padding, allowing the FAB (and the system navigation bar) to overlap the last list item.

**Fix:** Add `contentPadding = PaddingValues(bottom = 88.dp)` to the `LazyColumn`. 88dp = FAB height (56dp) + top margin (16dp) + breathing room (16dp). This is unconditional — the padding is applied even when the FAB is hidden (see E-4b), because the system nav bar can still overlap content.

**Verification:** Last bookmark card is fully visible when scrolled to the bottom; no overlap from FAB or nav bar.

**No strings. No settings. No localization impact.**

---

## Enhancements

### E-1: Hide Source Icons in Compact View

**User story:** In compact list mode, the favicon column consumes ~32dp of horizontal space. Users who find it unhelpful want to reclaim that width for article title/metadata.

**Setting:** `showCompactFavicons: Boolean`, default `true`, persisted in `SettingsDataStore`.

**Behavior when off:**
- `CompactStatusRail` skips the favicon `AsyncImage` entirely.
- Rail width collapses so the card's text content fills the freed space.
- Status icons (reading progress, offline pin, error badge) remain unaffected — they are separate from the favicon slot.

**Implementation sites:**
- `SettingsDataStore` — add `showCompactFaviconsFlow`, `saveShowCompactFavicons`, `isShowCompactFavicons`
- `UiSettingsViewModel` — expose as state
- `BookmarkCard.kt`, `CompactStatusRail` — accept and act on the flag
- `UiSettingsScreen` — new Switch row (see Settings Page Organization)

**Strings needed:**
```xml
<string name="ui_settings_show_compact_favicons">Show source icons</string>
<string name="ui_settings_show_compact_favicons_desc">Display website icons in compact list view</string>
```

**Verification:** Toggle off → favicon gone, title text fills to left edge. Toggle on → favicon restored. All other compact card elements unaffected.

---

### E-4b: FAB Visibility Toggle

**User story:** Users who exclusively save bookmarks via the browser share sheet find the add-bookmark FAB unnecessary; it also reduces visible list area even after the padding fix.

**Setting:** `showAddBookmarkFab: Boolean`, default `true`, persisted in `SettingsDataStore`.

**Behavior when off:** The `FloatingActionButton` in `BookmarkListScreen` is not composed. The add-bookmark workflow remains available via the Android share sheet in any browser.

**Implementation sites:**
- `SettingsDataStore` — add `showAddBookmarkFabFlow`, `saveShowAddBookmarkFab`, `isShowAddBookmarkFab`
- `BookmarkListViewModel` or `BookmarkListScreen` — consume the preference
- `UiSettingsScreen` — new Switch row (see Settings Page Organization)

**Strings needed:**
```xml
<string name="ui_settings_show_add_fab">Show add-bookmark button</string>
<string name="ui_settings_show_add_fab_desc">Display the + button in the bookmark list</string>
```

**Verification:** Toggle off → FAB absent, list still scrolls to bottom with full padding. Toggle on → FAB restored. Add-bookmark share sheet still works when FAB is hidden.

---

### E-3a: Extraction Error Badge on Bookmark Cards

**User story:** When Readeck fails to extract an article, the bookmark silently opens in a WebView. Users want a visual indicator in the list that a bookmark has errors before tapping it.

**Data:** `BookmarkDto.errors: List<String>` is already present. Non-empty list = has errors.

**Indicator:** `Icons.Outlined.Block` (circle-with-slash), rendered in the same overlay slot used by offline-pin and download-progress indicators. When errors are present, this icon takes priority over the download-state icon in that slot.

**Color:** `MaterialTheme.colorScheme.error` to make it immediately distinguishable.

**Placement by layout:**
- Grid / Mosaic: bottom-right overlay on thumbnail
- Compact (both wide and narrow): within the status rail, same position as offline indicator
- Mobile portrait: same overlay position as grid

**Accessibility:** Content description `bookmark_has_errors` on the icon.

**No new setting.** The badge is always shown when errors are present.

**Strings needed:**
```xml
<string name="bookmark_has_errors">This bookmark has extraction errors</string>
```

**Verification:** Bookmark with non-empty `errors` shows badge on all four card layouts. Bookmark with empty `errors` shows no badge. Offline pin and download indicator still appear correctly on error-free bookmarks.

---

### E-2: Label Quick-Apply Button on Bookmark Cards

**User story:** The workflow of opening a bookmark → overflow menu → Details → edit labels is slow when processing a large inbox. A direct label button on each card opens the label selection sheet immediately.

**UI:** Add `Icons.Outlined.Label` button to the card action row, positioned between Archive and the Web/globe icon.

**Button spec:** 36dp `IconButton`, 20dp icon — matches all other action row buttons.

**Behavior:** Tapping opens the existing label-editing sheet scoped to that bookmark. Sheet dismisses on confirm; card labels update in place via the existing ViewModel update path.

**Implementation sites:**
- `BookmarkCardActionRow` (shared reusable row) — covers grid, mosaic, wide compact, and mobile-portrait layouts
- `BookmarkCompactCardNarrow` action row — separate code path, needs its own addition
- The label editing sheet invocation already exists in `BookmarkDetailsDialog`; extract into a shared callable or duplicate the call with a single-bookmark scope

**No new setting.**

**Strings needed:**
```xml
<string name="action_edit_labels">Edit labels</string>
```

**Verification:** Label button visible on all card layouts in all list modes. Tapping opens sheet, labels save correctly, card reflects changes without full reload. Multi-select mode unaffected.

---

### E-3b: Open External Content — REVISED (shipped as two icon changes)

**Original ask:** the requester wanted (1) unextracted bookmarks to be openable in the system
browser rather than the in-app view, and (2) the same for the globe icon. The spec generalized this
into an "Open external content" setting toggling in-app ↔ external browser across every URL path.

**What actually shipped in RC3:** two small icon changes, **no setting**. See "Final decision" below.
The full setting was analyzed, partially built, then deliberately discarded. The analysis is kept
here because it's the substance of the decision and the basis for any future revisit.

#### Terminology (four distinct surfaces)

Settled during review so the discussion is unambiguous:

| Label | What it is |
|-------|-----------|
| **Article View** | Extracted/reader content in an internal WebView, with Readeck's reader top bar |
| **Original View** | The original URL in an internal WebView **with Readeck's app bar + overflow still present** (fav/archive/delete/Details/labels all reachable) |
| **Custom Tab** | Full-screen Android Custom Tab — no Readeck UI; X closes, down-arrow → PIP. Served by the user's **default browser** if it implements Custom Tabs; on browsers that don't (e.g. DuckDuckGo) it hands off to that browser app instead |
| **External Browser** | Full hand-off to the separate browser app — no overlay, Readeck backgrounded |

#### Current behavior, corrected (the map the decision was based on)

| Touchpoint | Surface today |
|-----------|---------------|
| List **globe** icon | **Original View** |
| In-article link tap (reader) | **Custom Tab** |
| Gallery image link | **Custom Tab** (but used an open-in-new/external-link icon — misleading) |
| No-content bookmark tap | **Original View** |
| About-screen / docs links | **Custom Tab** |
| Long-press list card → open | **External Browser** |
| Long-press article link → "Open in Browser" | **External Browser** |
| Reader → Detail page → site link (external-link icon) | **External Browser** |

Notes: there is **no** "Open in Browser" item on the list or reader overflow menus; Share-menu
options implicitly go external and were out of scope; sub-links tapped inside Original View load
in place (stay in Original View).

#### Key learnings that changed the conclusion

1. **Custom Tab is not the problem.** The "Custom Tab" is served by the user's *default browser*
   when that browser implements Custom Tabs (Chrome/Firefox/Brave/Edge do; DuckDuckGo doesn't and
   instead launches its app). So a Firefox-default user already gets a Firefox-backed tab, and a
   DDG-default user already gets a full external launch — both "for free," with no setting. The
   requester's own client ("Eckard") even routes its in-article links to a Custom Tab. The
   ad/cookie complaint in the original report was specifically about **Original View** (a chrome-less
   embedded page with no ad-blocking), **not** the Custom Tab paths.
2. **The real divergence is just Original View.** Everything else already behaved acceptably or as
   the requester wanted. The app already matches Eckard for in-article links (both Custom Tab).
3. **A full setting created more problems than it solved.** Routing the globe / no-content tap
   through the reader and then launching a Custom Tab leaves reader content sitting behind the tab
   (weird overflow-menu state) and makes "X" take two taps to get back to the list. Avoiding those
   required either re-routing navigation or building a new "no content" reader placeholder — a large,
   behavior-changing surface for a request whose core was small.
4. **Original View has value the requester's approach throws away.** Eckard shows *nothing* for
   error bookmarks (no original access at all). Readeck's Original View keeps the full Readeck app
   bar + overflow, so fav/archive/delete/labels/Details all stay reachable while viewing the page.
   Losing that to force a Custom Tab/External Browser was judged a net regression for most users.

#### Final decision (shipped)

Two icon changes only, no setting, default behavior otherwise unchanged:

1. **External-link (open-in-new) icon added to the Original View top bar.** Shown only when
   `contentMode == ContentMode.ORIGINAL` (`BookmarkDetailTopBar`). Tapping it opens the current
   page's URL in the **External Browser** (`onClickOpenInBrowser` → `ACTION_VIEW`), consistent with
   the same icon's meaning elsewhere (Detail-page site link, long-press "Open in browser"). This is
   the clearly-labeled, user-initiated escape hatch from Original View → the user's real browser —
   which is what the requester actually wanted, without the app ever auto-ejecting.
2. **Gallery image-link icon changed open-in-new → globe** (`ImageGalleryOverlay`,
   `Icons.Filled.Language`) to match the in-app (Custom Tab) behavior and the list globe's icon
   language (globe = view web content in-app; open-in-new = leave to the external browser).

**Guiding principle adopted:** the app should never *force-launch* another app on an implicit action
(e.g. a normal card tap). External hand-off happens only via clearly-labeled, user-initiated actions
(the open-in-new icon, "Open in browser" menu items) — not silently.

#### Alternative recommendation (future consideration, NOT built)

If the no-content experience is revisited, the recommended shape — cleaner than both the discarded
setting and the shipped icon-only change — is:

- **No-content bookmark tap** → open the reader but, instead of auto-loading the chrome-less
  Original View, show a **clean Readeck placeholder** in the content area (title + description +
  "No content was extracted") with the full top bar/overflow intact, plus a clearly-labeled
  **"Open original"** affordance that launches a **Custom Tab**. This removes the requester's actual
  annoyance (the auto-loaded ad/cookie-ridden page), keeps every Readeck action, and keeps more than
  Eckard (which shows nothing).
- **List globe** → open a **Custom Tab directly over the list** (not via the reader). Single-tap
  back to the list; nothing hidden behind the tab.

This could ship with or without a setting; because the Custom Tab already degrades to a full external
launch on browsers without Custom Tab support, it satisfies the "I want my browser" persona without an
explicit eject. It was deferred only to minimize behavior change so close to the v1.0.0 release.

#### Update — "Internal browser" switch shipped (after requester feedback)

The requester tested the two-icon change and asked for the setting back: in their words, they "can't
think of any situation where one would want to first open an article in Chrome and then continue to the
browser that's already configured as the preferred browser systemwide." Crucially, they confirmed the
**Custom Tab was never the problem** — the issue was always the in-app **Original View**. That made the
"clean placeholder" alternative above the right shape, so a tightly-scoped setting shipped.

**Setting:** `OpenWebPagesIn` enum (`IN_APP` / `EXTERNAL_BROWSER`), default `IN_APP`, persisted in
`SettingsDataStore`. Presented as an **Internal browser** switch (on = `IN_APP`) at the top of
**Settings → User Interface → Reading**: *"Web pages open in-app for an immersive experience. When off,
they open in your external browser."*

**Scope — Original View only.** When the switch is **off**:

| Trigger | Behavior |
|---------|----------|
| Card **View web page** icon | Renders the open-in-new icon; opens the original URL in the external browser (skips Original View). |
| Reader overflow **⋮ → View web page** | Same — open-in-new icon, opens externally. |
| Tapping a **no-content** bookmark | Opens the Original View shell (top bar + overflow), but the content area shows a native **title + description + "No content available"** placeholder instead of the in-app WebView. The top-bar open-in-new icon opens it externally; overflow keeps fav/archive/read/share/Details/delete. |

When **on** (default), all of the above is today's in-app behavior. **Explicitly out of scope, unchanged
in both modes:** in-article link taps and gallery links (Custom Tab), and the explicit Open-in-browser /
long-press actions (already external).

**Implementation notes:**
- `domain/model/OpenWebPagesIn.kt`; `SettingsDataStore` flow + save/get.
- List card icon driven by a `LocalOpenWebPageExternally` CompositionLocal (mirrors `LocalIsWideLayout`)
  provided from `BookmarkListView`; the click is routed in `BookmarkListViewModel.onClickBookmarkOpenOriginal`.
- Detail: `BookmarkDetailMenu` swaps the "View web page" item's icon/action; `BookmarkDetailContent`
  renders `OriginalViewNoContentPlaceholder` instead of `BookmarkDetailOriginalWebView` when off.
- The placeholder is **native Compose** tuned to the reader header CSS (title ~24px/600, description
  ~15px italic at 0.75 opacity). It matches the reader's *default* typography; it does not track a user's
  custom reader font/size (the reader renders in a WebView). Rendering the placeholder through the reader
  WebView pipeline would be pixel-perfect but was judged unnecessary.

**Strings:** `ui_settings_internal_browser_title`, `ui_settings_internal_browser_desc`,
`reader_no_content_available`.

---

### E-5: Label Search Ranking — shipped as in-sheet toggles (not Settings)

**User story:** With 650+ labels, the current infix/alphabetical search is nearly useless — typing "ge" surfaces "Argentina" and "burger king" before "geography." Two orthogonal controls address this.

**Design evolution:** First built as two segmented controls in a "Label Search" section of UI Settings.
On review that was judged too buried for a per-search action, so the controls were **moved into the
label-picker sheet header itself** and the Settings section was removed. The two preferences are still
**persisted globally** (`SettingsDataStore`) so the choice sticks across sessions and stays consistent
across every label picker.

#### Control A — Search Matching Mode

`labelSearchMatching: LabelSearchMatching` enum, default `CONTAINS`, persisted.

| Value | Behavior | Sheet glyph |
|-------|----------|-------------|
| `CONTAINS` | Label name contains query anywhere (infix) | `∗a∗` |
| `STARTS_WITH` | Only labels whose name begins with the query | `a∗` |

#### Control B — Search Sort Order

`labelSearchSort: LabelSearchSort` enum, default `ALPHABETICAL`, persisted.

| Value | Behavior | Sheet glyph |
|-------|----------|-------------|
| `ALPHABETICAL` | A→Z | `abc` |
| `BY_FREQUENCY` | Descending by label bookmark count | `123` |

**Combined ranking (implemented in `LabelPickerBottomSheet`):** filter by the matching mode, then sort.
For `BY_FREQUENCY`, prefix matches are surfaced first (moot for `STARTS_WITH` / blank query), then by
count descending, then alphabetical as a stable tiebreak:

```kotlin
val matched = if (query.isBlank()) entries
    else entries.filter { when (matching) {
        STARTS_WITH -> it.key.startsWith(query, true)
        CONTAINS    -> it.key.contains(query, true)
    } }
when (sort) {
    ALPHABETICAL -> matched.sortedWith(compareBy({ it.key.lowercase() }, { it.key }))
    BY_FREQUENCY -> matched.sortedWith(
        compareByDescending<Entry> { query.isNotBlank() && it.key.startsWith(query, true) }
            .thenByDescending { it.value }
            .thenBy { it.key.lowercase() })
}
```

**UI details (sheet header):**
- Two monospace **glyph toggles** in the `TopAppBar` `actions`, before Done so they shift left to make
  room for it on the multi-select sheet (rightmost on single-select/filter sheets).
- Each shows the **current mode** and flips on tap; **long-press shows a `PlainTooltip`** describing the
  current mode (MD3 — chosen over a 2-segment segmented button for compactness; the immediate re-rank
  resolves the current-state-vs-next-action ambiguity).
- **Tap the sheet title** animates the results list to the top (mirrors the main list's tap-title
  affordance). Note: changing sort does **not** auto-scroll — consistent with the main bookmark list,
  which re-sorts in place.

**Implementation sites (as built):**
- `domain/model/LabelSearchPreferences.kt` — the two enums
- `SettingsDataStore` (+ impl) — flows and save/get for both
- `LabelSearchSettingsViewModel` — read flows + `toggleMatching()` / `toggleSort()` (the sheet is a
  shared component used from 6 call sites; a tiny `hiltViewModel()` keeps the wiring in one place)
- `LabelPickerBottomSheet` (`LabelsBottomSheet.kt`) — filter/sort logic + the two toggles + title-tap

**Strings:** `label_search_matching_prefix`, `label_search_matching_contains`, `label_search_sort_alpha`,
`label_search_sort_count` (used as tooltips + accessibility content descriptions). The earlier
`ui_settings_label_search_*` strings were removed with the Settings section.

**Verification:** All four matching×sort combinations correct; tested against a large label set. Choice
persists and applies to every picker (add-bookmark, card label edit, details, filter). Tooltips and
title-tap-to-top confirmed.

---

## Settings Page Organization

Current `UiSettingsScreen` is an unsectioned list. With five new preferences, add named section headers using a `Text` in `labelSmall` / `onSurfaceVariant` + `HorizontalDivider` pattern.

**Proposed structure:**

```
[Appearance controls — theme, light/dark appearance]
[Swipe actions]

── Bookmark List ──────────────────────────────────
  Show source icons in compact view          [Switch]
  Show add-bookmark button                   [Switch]

[Reading section — keep screen on, fullscreen]
[Share format]

Note (as shipped): the "Open external content" row and the "Label Search" section shown above were
NOT built. E-3b became two icon changes (no setting); E-5's controls live in the label-picker sheet
header, not Settings. The Bookmark List section ships with just the two switches.
```

Section headers are a reusable `SettingsSectionHeader` composable (Text + optional Divider above) to keep the pattern DRY as the screen grows.

**Strings needed:**
```xml
<string name="ui_settings_section_bookmark_list">Bookmark List</string>
<string name="ui_settings_section_label_search">Label Search</string>
```

---

## Localization

All new string keys must be added to:
- `values/strings.xml` (English, authoritative text)
- All nine language files with the English text as placeholder:
  - `values-de-rDE`, `values-es-rES`, `values-fr`, `values-gl-rES`, `values-pl`, `values-pt-rPT`, `values-ru`, `values-uk`, `values-zh-rCN`

Run `./gradlew :app:lintDebugAll` after each item to catch missing string keys early.

---

## Verification Gate

Before merging, run the full CI script:

```
./scripts/ci-verify.sh
```

Per-item checklist:

- [ ] BF-1: Compact narrow card — heart icon aligned with siblings
- [ ] BF-2: Undo label bold in light and dark modes; no truncation
- [ ] E-4a: Last list item fully visible at bottom; no FAB/nav overlap
- [ ] E-1: Favicon toggle off → text fills to edge; on → restored
- [ ] E-4b: FAB toggle off → absent; share-sheet add still works
- [ ] E-3a: Error badge on all four card layouts for error bookmarks; absent otherwise
- [ ] E-2: Label button on all layouts; sheet opens, saves, updates card in place
- [ ] E-3b: External setting → error bookmark tap opens system browser with correct URL
- [ ] E-5: All four matching×sort combos correct; large label set tested

---

## Porting to MyDeck

When all items are complete and verified in `readeck-android`, port the full changeset to the `/Users/nathan/development/MyDeck` repo. The repos share the same package structure (`com.mydeck.app`) and UI codebase, so the diff should apply cleanly.

**Porting steps (to be performed after merge):**

1. Identify all changed files via `git diff main --name-only` on the completed branch.
2. For each changed file, apply equivalent edits in the MyDeck repo — do not blindly copy, as MyDeck may have diverged in some files.
3. Add all new string keys to MyDeck's `values/strings.xml` and all language files.
4. Run MyDeck's own build + lint verification.
5. Install on Pixel 9 via `./scripts/install-phone.sh` and do a manual smoke test of all new settings.
