# Design Spec: Grid Card Layout Refinement & UX Improvements

**Date**: 2025-02-21

---

## Overview

This spec defines four functional improvements to the MyDeck Android app: a redesigned mobile portrait grid card layout, a collapsing top bar in reading mode, Gmail-style staged bookmark deletion, and visual polish across card layouts. The goal is a clean, information-dense mobile reading experience that follows Material Design 3 conventions.

---

## 1. Mobile Portrait Grid Card Redesign

### Motivation
The current mobile portrait grid layout uses a top-image / bottom-content card borrowed from the tablet/landscape grid. On a narrow phone screen in portrait, this wastes vertical space on tall images and pushes content (title, metadata, labels, actions) into a cramped area below. Cards are visually heavy and inconsistent in height when labels wrap to multiple rows.

### Design
Replace the vertical card with a **horizontal layout**: a left thumbnail (~25% width) beside right-side content, at a fixed height of `160dp`.

**Content structure (top to bottom within the right column):**
- **Title**: Up to 2 lines, ellipsis overflow. Aligned to top of card.
- **Metadata**: Site favicon (16dp) + site name (ellipsis) + reading time estimate (right-justified). Positioned immediately below title with adequate breathing room.
- **Flexible spacer**: Absorbs remaining vertical space between metadata and labels.
- **Labels**: Single-row horizontal scroll (`LazyRow`). Fixed 32dp row height reserved even when empty, ensuring consistent card height. Sits just above action icons.
- **Action icons**: Favorite, archive, open original, delete. Anchored at card bottom.

**Thumbnail:**
- `ContentScale.Crop`, fills full card height for a clean edge-to-edge look.
- Reading progress indicator (circular arc or checkmark) at top-right corner.
- Bookmark type icon (video/picture) at top-left corner.

**Key decisions:**
- Fixed card height ensures uniform list appearance regardless of title length or label count.
- Labels scroll horizontally instead of wrapping — keeps card height constant while showing all labels (since tapping a chip is a filter action, all must be accessible).
- The flexible spacer between metadata and labels means 1-line titles pull metadata up but labels stay anchored above icons, avoiding a "hollow" center.

### Scope
Mobile portrait grid only. No changes to:
- Mobile landscape grid (uses `BookmarkGridCardWide`)
- Tablet portrait/landscape grid (uses `BookmarkGridCardWide`)
- Compact layout (all form factors)
- Mosaic layout (all form factors)

---

## 2. Collapsing Top Bar in Reading Mode

### Motivation
In reader mode, the top app bar (back arrow, favorite, archive, typography, search icons) occupies ~56dp of vertical space permanently. On mobile, this reduces the readable content area. Material Design 3 apps with long-form reading content (Google News, Chrome reader) hide the top bar while the user scrolls down and reveal it on scroll-up, maximizing immersion.

### Design
- **Behavior**: `enterAlwaysScrollBehavior` — the top bar slides up out of view when the user scrolls content downward and slides back into view on any upward scroll gesture.
- **End-of-article reveal**: When the user reaches the bottom of the article, the top bar smoothly animates back into view so the back arrow is accessible without requiring a reverse scroll. This is essential for usability — without it, finishing an article would require an unintuitive upward swipe just to access navigation.
- **Scope**: Reader mode only (Compose `ScrollState`-based content). Original mode (WebView) is excluded since the WebView handles its own scroll and the nested scroll connection doesn't apply.

---

## 3. Gmail-Style Staged Bookmark Deletion

### Motivation
The current delete flow shows a timed snackbar (5-second countdown with progress bar). If the timer expires, the bookmark is deleted. This pattern has two problems:
1. If the user isn't watching the timer, they miss the undo window.
2. The countdown bar creates time pressure that feels aggressive for a destructive action.

Gmail's approach — snackbar persists until the user interacts with something else — is more forgiving and familiar to Android users.

### Design

**List view deletion:**
1. User taps delete icon on a bookmark card.
2. Bookmark immediately disappears from the list (filtered out via `_pendingDeletionBookmarkId` in the ViewModel's flow pipeline).
3. An indefinite snackbar appears: "Bookmark deleted" with an "UNDO" action.
4. **UNDO tapped**: Bookmark reappears in the list. Snackbar closes. No API call.
5. **Any other interaction** (scroll, tap another bookmark, open drawer, tap FAB, change sort/layout, tap topbar icons, minimize/close app): Snackbar dismisses, delete API call executes.

**Reading view deletion:**
1. User taps delete in the reader's overflow menu.
2. Reader immediately closes, returning to list view.
3. Bookmark is hidden from the list.
4. Delete snackbar appears on the list view.
5. Same UNDO/dismiss behavior as list view deletion.

**Implementation approach:**
- `_pendingDeletionBookmarkId` (`MutableStateFlow<String?>`) in `BookmarkListViewModel` holds the staged bookmark ID.
- The main bookmark list flow `.combine(_pendingDeletionBookmarkId)` filters it out, so the bookmark vanishes from the UI without any database change.
- Snackbar uses `SnackbarDuration.Indefinite`. The coroutine suspends on `showSnackbar()` and branches on `ActionPerformed` (undo) vs `Dismissed` (confirm delete).
- `dismissPendingDeleteSnackbar()` must be wired into all major interaction points: bookmark clicks, favorites, archives, scroll events, drawer open, FAB, sort/layout menus, label chip taps, lifecycle ON_STOP.
- Reader-to-list handoff uses `savedStateHandle` to pass the pending delete ID back via navigation.

---

## 4. Visual Polish Across Card Layouts

### Card Border
Add a thin border to all card variants (grid, compact, mosaic) using `outlinedCardBorder()` with `outlineVariant` color at 40% alpha. This prevents card edges from bleeding into the background when thumbnail images have white (light theme) or black (dark theme) edges.

### Separator Removal
Remove the `HorizontalDivider` between cards in the grid layout (mobile and tablet). Cards will be visually separated by their borders and vertical padding alone, giving a cleaner appearance.

### Compact Layout Label Scrolling
Switch labels in the compact card layout to horizontal scrolling (matching the grid layout) instead of `FlowRow` wrapping. This prevents cards from varying in height based on label count.

### Mosaic Gradient Enhancement
Strengthen the gradient overlay on mosaic cards: start the transition higher (124dp) and reach higher opacity (0.95 black at bottom), ensuring readable text contrast over light-colored thumbnail images.

---

## Files Affected

| File | Changes |
|------|---------|
| `BookmarkCard.kt` | New `BookmarkGridCardMobilePortrait` composable, card border on all variants, separator removal, compact label scroll, mosaic gradient |
| `BookmarkListScreen.kt` | `useMobilePortraitGridLayout` detection, Gmail-style `stageDeleteWithSnackbar`, `onUserInteraction` wiring, `PendingDeleteFromDetailKey` handling |
| `BookmarkListViewModel.kt` | `_pendingDeletionBookmarkId` flow, `onDeleteBookmark`/`onConfirmDeleteBookmark`/`onCancelDeleteBookmark`, pending-deletion filtering in main flow pipeline, `UiState.Loading` initial state |
| `BookmarkDetailScreen.kt` | `enterAlwaysScrollBehavior`, `nestedScroll` on Scaffold, animated `onReachedBottom` reveal, reader-to-list delete handoff |
| `BookmarkDetailTopBar.kt` | `scrollBehavior` parameter threaded through to `TopAppBar` |
| `BookmarkDetailViewModel.kt` | Delete-from-reader navigation event changes |
