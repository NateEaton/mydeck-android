# Swipe Actions for Bookmark Cards — Implementation Plan

Companion to:
- [Functional spec](swipe-actions-for-bookmark-cards-spec.md)
- [Architecture spec](swipe-actions-for-bookmark-cards-arch-spec.md)

## Resolved design questions

| # | Question | Decision |
|---|----------|----------|
| 1 | Global enable toggle vs per-direction "None" | **Keep both.** Global enable is the master kill switch. Per-direction "None" disables only one direction. |
| 2 | Add archive undo as part of this feature? | **No.** Out of scope. Only delete keeps its existing snackbar+undo flow. |
| 3 | Reveal-and-tap vs commit-only? | **Commit-only.** Reveal-and-tap duplicates the existing icon-tap UX and introduces an ambiguity window where tapping the card body opens the reader instead of confirming. Single elastic snap-back with commit threshold at 0.5 × card width. |
| 4 | Ship `requireFullSwipe` setting? | **Obsolete.** Commit-only makes this the only mode; no setting needed. |
| 5 | Active in multi-column layouts? | **No.** Disabled in multi-column grid/mosaic (tablet, mobile landscape). No major app applies row-swipe to a card grid; cards are narrower, neighbouring cells own the horizontal space, and multi-select is the right batch UX for tablet. Compact layout always swipes (it's single-column even on tablet). |

## Visual treatment (Gmail-style)

The action surface is a rounded colored card that grows from the trailing
edge of the swiped bookmark card, matching the card's height and corner
radius. The action icon is centered inside the growing surface. Width
tracks `abs(offset)`; alpha and scale ramp against the commit threshold,
so the icon is fully formed at the commit point. This avoids the
"thin band along the top" appearance of a naive full-width background.

## Reference: existing entry points

- Card action callbacks already plumbed through `BookmarkListView` → each card composable:
  - `onClickDelete(id: String)` — stages pending deletion (snackbar + undo handled in screen).
  - `onClickArchive(id: String, isArchived: Boolean)` — takes desired new state.
  - `onClickFavorite(id: String, isFavorite: Boolean)` — takes desired new state.
- ViewModel layer: `BookmarkListViewModel.onDeleteBookmark`, `onToggleArchiveBookmark`, `onToggleFavoriteBookmark`.
- Screen-level snackbar plumbing: `BookmarkListScreen.stageDeleteWithSnackbar` and `pendingDeletionBookmarkIds` flow.
- Drawer gesture flag: `AppShell.kt` line ~218, `gesturesEnabled = isOnBookmarkList`.
- Settings persistence interface: `SettingsDataStore` (Flow + suspend setter pattern).
- Settings UI: `UiSettingsScreen` + `UiSettingsViewModel`.

Swipe must reuse `onClickDelete`/`onClickArchive`/`onClickFavorite` exactly — do not call the ViewModel directly, or the existing pending-delete snackbar pipeline is bypassed.

## Slices

Slices are sequential; each is independently testable. Model recommendations reflect risk and novelty.

### Status

- Slice 1 — **DONE**
- Slice 2 — **DONE** (original two-anchor reveal+commit; superseded behaviorally by Slice 3.5a)
- Slice 3 — **DONE**
- Slice 3.5a — **DONE** (visual + behavior overhaul + multi-column gating)
- Slice 3.5b — **DONE** (pending-delete snackbar auto-confirm bug)
- Slice 4 — **DONE** (drawer gesture flip + settings UI; one regression patch landed mid-Slice-7 — see note below)
- Slice 5 — **DONE** (user guide documentation)
- Slice 6 — **DONE** (tests; surfaced a spec/code discrepancy on commit timing — specs updated to match implementation)
- Slice 7 — **DONE** (final verification; cross-theme reveal-color tuning landed during Slice 7 — see note below)

**Feature complete.** Branch ready to PR.

### Slice 7 reveal-color tuning (post-verification polish)

During the Phase C manual matrix, the reveal colors looked inconsistent
across themes:

- Sepia Delete used Sepia's `errorContainer`, which read darker than
  Paper's softened red.
- Dark/Black Archive used each theme's `secondaryContainer` (a slate),
  not the wallpaper-derived primary that Light themes used.
- Initial fix attempt hardcoded a static teal, which broke on Pixel 9
  where the live primary is dynamic (wallpaper-sourced blue, not the
  `PaperColorScheme` fallback teal). It also incorrectly overrode
  Sepia Archive (which should stay on its curated palette).

Final resolution in `SwipeableCardContainer.kt`:

- Resolve `lightDynamic = dynamicLightColorScheme(LocalContext.current)`
  once at composable scope (with `PaperColorScheme` fallback for API <31).
- `backgroundColorFor` / `iconTintFor` rewritten as explicit
  `when (appearance)` blocks so every theme/action cell is auditable.
- Archive uses `lightDynamic.primary` / `.onPrimary` for **Paper, Dark,
  Black** — identical wallpaper-derived shade across all three.
- Archive uses `cs.secondaryContainer` / `.onSecondaryContainer` for
  **Sepia** — preserves the curated sepia palette.
- Delete uses `lerp(cs.error, cs.errorContainer, 0.2f)` for both
  **Paper and Sepia** (light themes get the softened red, each tinted
  by its own palette); `cs.errorContainer` for Dark/Black.
- Favorite mappings unchanged from pre-Slice-7 (Paper → `tertiary`,
  others → `tertiaryContainer`).

### Slice 4 regression patch (post-landing fix)

Device testing during Slice 7 surfaced a regression in the drawer-gesture
flip. `gesturesEnabled = false` on `ModalNavigationDrawer` disables not
only swipe-to-open but also scrim-tap-to-close and swipe-to-close, so
with swipe actions enabled the drawer could only be closed by selecting
a drawer item. Fixed in `AppShell.kt` by gating the suppression on
`drawerState.isOpen`:

```kotlin
gesturesEnabled = drawerState.isOpen
    || !isOnBookmarkList
    || !swipeConfig.value.enabled,
```

Drawer-swipe-to-open is still suppressed when the drawer is closed on
the bookmark list with swipe enabled; once the drawer is open, gestures
go live again so scrim-tap and swipe-close work. Slice 7 matrix C3
gained two new rows to cover this.

### Slice 1 — Settings model + persistence (no UI)

**Model: Sonnet**

- New file `SwipeAction.kt` (enum: `ARCHIVE`, `DELETE`, `FAVORITE`, `NONE`).
- New file `SwipeConfig.kt` (data class: `enabled: Boolean`, `leftAction: SwipeAction`, `rightAction: SwipeAction`).
- Defaults: `enabled = true`, `leftAction = DELETE`, `rightAction = ARCHIVE`.
- Extend `SettingsDataStore`:
  - `val swipeConfigFlow: StateFlow<SwipeConfig>`
  - `suspend fun saveSwipeEnabled(enabled: Boolean)`
  - `suspend fun saveSwipeLeftAction(action: SwipeAction)`
  - `suspend fun saveSwipeRightAction(action: SwipeAction)`
- Implement in `SettingsDataStoreImpl` following the existing key/flow pattern (see e.g. `keepScreenOnWhileReadingFlow`).
- Unit-test serialization / round-trip / defaults.

**Out of scope this slice:** any UI, any wiring into cards or the drawer.

### Slice 2 — `SwipeableCardContainer` composable

**Model: Opus**

This is the highest-risk slice (gesture arbitration with `combinedClickable`, system-gesture insets, commit dispatch).

- New file `app/src/main/java/com/mydeck/app/ui/list/SwipeableCardContainer.kt`.
- API:
  ```kotlin
  @Composable
  fun SwipeableCardContainer(
      config: SwipeConfig,
      onCommitLeft: () -> Unit,
      onCommitRight: () -> Unit,
      a11yLeftLabel: String,
      a11yRightLabel: String,
      modifier: Modifier = Modifier,
      content: @Composable () -> Unit,
  )
  ```
- Architecture:
  - Outer `Box` owns `Modifier.anchoredDraggable(Orientation.Horizontal)` so it wraps the existing card. Inner cards' `combinedClickable` modifiers are untouched — horizontal slop crossing cancels the parent long-press automatically.
  - Anchors: `Idle: 0f`, `RevealedLeft: -revealPx`, `RevealedRight: +revealPx`. Use `cardWidthPx` from `BoxWithConstraints` to compute `revealPx = 0.25 * width`, `commitPx = 0.65 * width`.
  - Commit dispatch as a one-shot effect, not state. Use a `var commitConsumed: Boolean by remember(state.settledValue)` flag, reset when `settledValue == Idle`. When `abs(state.offset) >= commitPx` and not consumed, set consumed and call the handler — addresses arch spec issue #4 (no `Committed(direction)` state).
  - Edge gating: in `pointerInput`, read pointer-down position and the start-side `WindowInsets.systemGestures` in px; abort tracking if down is inside that inset.
  - Background layer behind the card: a `Row` filling the box, aligned by drag direction, with action color (`secondaryContainer` / `errorContainer` / `tertiaryContainer`) and icon. Icon `alpha` follows `(abs(offset) / revealPx).coerceIn(0f, 1f)`, scale `lerp(0.8f, 1.0f, fraction)`.
  - On revealed state, the visible action icon is part of the background layer; it's tappable and calls the same commit handler then animates closed.
  - Haptic feedback (`HapticFeedbackType.LongPress`) fires on commit.
  - `Modifier.semantics { customActions = listOf(...) }` for TalkBack — only emit actions whose direction has a non-`NONE` mapping.
  - Pass-through behavior when `config.enabled == false` (just emit `content()` with the supplied modifier).
- Resolution helper: `resolveAction(SwipeAction, onCommitLeft, onCommitRight, ...)` mapping action enum to one of the existing on-click handlers.

**Out of scope this slice:** wiring into the list — keep this composable standalone + previewable.

### Slice 3 — Wire `SwipeableCardContainer` into the list

**Model: Sonnet**

- `BookmarkListScreen.kt` `BookmarkListView`: collect the `swipeConfig` from the ViewModel (add a state-collecting `viewModel.swipeConfig.collectAsStateWithLifecycle()` upstream and plumb it through to `BookmarkListView`).
- In both the `LazyVerticalGrid.itemsIndexed` and `LazyColumn.itemsIndexed` blocks, wrap the existing `Box(modifier = Modifier.alpha(...))` in `SwipeableCardContainer`. Card composables stay unchanged.
- Resolve handlers per direction:
  - `SwipeAction.ARCHIVE` → `{ onClickArchive(bookmark.id, !bookmark.isArchived) }`
  - `SwipeAction.DELETE` → `{ onClickDelete(bookmark.id) }`
  - `SwipeAction.FAVORITE` → `{ onClickFavorite(bookmark.id, !bookmark.isFavorite) }`
  - `SwipeAction.NONE` → no-op (and direction should be elided from accessibility actions and from anchor set)
- ViewModel: add `val swipeConfig: StateFlow<SwipeConfig>` sourced from `settingsDataStore.swipeConfigFlow`.

### Slice 3.5a — Visual overhaul + commit-only behavior + multi-column gating

**Model: Opus**

Triggered by device testing after Slice 3 landed. Combines three changes
in one pass because they all touch the same files.

**Scope:**
- `SwipeableCardContainer`:
  - Drop `RevealedLeft` / `RevealedRight` anchors — single `Idle: 0f` anchor only.
  - Drop the revealed-icon `clickable` from the background layer (no tap-to-confirm).
  - Lower commit threshold from `0.65 * width` to `0.5 * width`.
  - Rework the background layer to Gmail-style growing colored surface:
    `Modifier.matchParentSize()` box behind the draggable; inside it, a
    rounded surface (matching card corner radius) aligned to the
    away-from-finger edge, with `width = abs(offset)`. Icon centered
    inside. Icon alpha/scale ramps against the new commit threshold.
  - Update the `@Preview` to reflect the new visual.
- `BookmarkListScreen.kt`:
  - In the `LazyVerticalGrid` branch (multi-column path), pass
    `swipeConfig.copy(enabled = false)` into `SwipeWrappedBookmark`.
    The `LazyColumn` branch (single-column path, includes Compact on
    tablet) continues to pass the user's actual config.

**Verification:** install on Pixel 9 and verify on each layout (Mosaic/
Grid/Compact in portrait, landscape, and on a tablet emulator if
available) that swipe is active or absent per the gating rule, and that
the visual matches the Gmail mental model.

### Slice 3.5b — Pending-delete snackbar auto-confirm bug

**Model: Sonnet**

Separate from swipe per se but surfaced by device testing. Investigate
`BookmarkListScreen.stageDeleteWithSnackbar` and the
`SnackbarHostState.showSnackbar` result handling:

- Confirm whether the `SnackbarResult.Dismissed` branch calls
  `viewModel.onConfirmDeleteBookmark(id)` or whether dismissal silently
  strands the pending item.
- Confirm whether the swipe-delete path goes through the same
  `dismissPendingDeleteSnackbar(); stageDeleteWithSnackbar(id)`
  sequence that the icon path uses.
- Reproduce both paths (icon-then-icon, swipe-then-swipe, mixed) to
  determine whether the bug is swipe-specific or pre-existing.
- Fix so that any new delete (swipe or icon) confirms the prior
  pending deletion.

### Slice 4 — Drawer gesture flip + Settings UI

**Model: Sonnet**

- `AppShell.kt`: change `gesturesEnabled = isOnBookmarkList` to read from `swipeConfig.enabled` — when swipe is enabled, drawer-swipe is disabled; when swipe is disabled, drawer-swipe stays enabled (preserves current behavior for users who turn the feature off).
  - Cleanest: inject the config flow into `CompactAppShell` (medium/expanded shells don't use a swipe-out drawer anyway).
- `UiSettingsScreen` + `UiSettingsViewModel`: add "Swipe actions" section.
  - Master Switch (enable / disable).
  - Two pickers (dropdown menus or M3 segmented buttons): "Swipe left action" and "Swipe right action", options Archive / Delete / Favorite / None.
- Add 10 new strings to all 10 locale files per CLAUDE.md (English placeholders in non-English locales):
  - `swipe_settings_section_title` ("Swipe actions")
  - `swipe_settings_enable` ("Enable swipe actions")
  - `swipe_settings_left_action` ("Swipe left action")
  - `swipe_settings_right_action` ("Swipe right action")
  - `swipe_action_archive`, `swipe_action_delete`, `swipe_action_favorite`, `swipe_action_none`
  - `swipe_a11y_archive` ("Archive"), `swipe_a11y_delete` ("Delete") — for semantics custom actions
  - (`swipe_a11y_favorite` if needed)

### Slice 5 — Documentation

**Model: Sonnet**

- `app/src/main/assets/guide/en/your-bookmarks.md`: add a "Swipe actions" subsection — defaults, reveal vs commit, customizing in settings, note that the drawer is opened by the hamburger button only (assuming swipe is enabled).
- `app/src/main/assets/guide/en/settings.md`: document the new section under UI Settings.
- English only — translations handled separately.

### Slice 6 — Tests

**Model: Opus** (gesture tests are tricky; basic unit tests could be Sonnet — split if needed)

- Unit tests:
  - `SwipeConfig` defaults.
  - `SettingsDataStoreImpl` swipe pref persistence round-trip.
  - `resolveAction` mapping.
- Compose UI tests (one suite for `SwipeableCardContainer`):
  - Tap on content still propagates to inner `clickable`.
  - Long-press on content still propagates (no swipe interference under slop).
  - Horizontal drag past commit fires the corresponding handler exactly once.
  - Drag-down inside system-gestures start-inset does not start tracking.
  - `config.enabled == false` → no draggable behavior; pure pass-through.
  - Revealed-state tap on action icon fires handler and snaps closed.
- Integration smoke (BookmarkListScreen): swipe-delete shows the existing pending-delete snackbar with Undo.

### Slice 7 — Verify

**Model: Haiku**

Run before any commit per CLAUDE.md:
- `./gradlew :app:assembleDebugAll`
- `./gradlew :app:testDebugUnitTestAll`
- `./gradlew :app:lintDebugAll`

Then `./scripts/install-phone.sh` and manually verify:
- Tap, long-press, vertical scroll unaffected.
- Swipe-archive / swipe-delete (with undo) fire.
- Partial-reveal + tap revealed icon fires.
- Edge-back gesture from screen edge still works.
- Hamburger drawer open still works (drawer swipe-open suppressed when swipe is enabled).
- Settings toggle disables swipe and re-enables drawer swipe.

## Model selection summary

| Slice | Model | Why |
|-------|-------|-----|
| 1. Settings model + persistence | Sonnet | Pattern-heavy, low novelty. |
| 2. SwipeableCardContainer | Opus | Novel gesture work; arbitration with existing modifiers; edge-inset handling; highest defect cost. |
| 3. Wire into list | Sonnet | Mechanical; reuses existing handlers. |
| 3.5a. Visual + commit-only + multi-column gate | Opus | Visual refactor (Gmail-style growing surface) plus single-anchor refactor; touches the highest-risk file again. |
| 3.5b. Snackbar auto-confirm bug | Sonnet | Investigation in screen-level snackbar handling; well-defined surface area. |
| 4. Drawer flip + Settings UI | Sonnet | Multi-locale string work plus straightforward UI. |
| 5. Docs | Sonnet | Tone/style alignment with existing guide. |
| 6. Tests | Opus | Compose gesture tests, slop/anchor verification. |
| 7. Verify | Haiku | Shell commands + manual checks. |
