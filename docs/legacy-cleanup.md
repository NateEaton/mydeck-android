# Legacy Code Cleanup — ReadeckApp Holdovers

**Status:** Proposal / minispec for review.
**Scope:** Identify and remove Compose code carried over from the ReadeckApp fork that is no longer rendered by the live app but still lives in the source tree.

## Why this exists

While grounding a web-console UI redesign against the Android app's current card design, we found two files in `ui/list/components/` that duplicate composable names defined in the live `ui/list/BookmarkCard.kt` and use interaction patterns (a `BookmarkCardActions` 3-dot dropdown, a 5-second timed delete snackbar) that don't match what the app actually renders (inline action icons on each card, indefinite snackbar until tap/gesture).

The live `BookmarkListScreen.kt` is in package `com.mydeck.app.ui.list` and its call sites — e.g.:

- `BookmarkListScreen.kt:1039` — `LayoutMode.GRID -> BookmarkGridCard(...)`
- `BookmarkListScreen.kt:1059` — `LayoutMode.COMPACT -> BookmarkCompactCard(...)`
- `BookmarkListScreen.kt:1078` — `LayoutMode.MOSAIC -> BookmarkMosaicCard(...)`

resolve to the same-package definitions in `BookmarkCard.kt`, not the `.components.` versions.

## Candidates for removal

### 1. `ui/list/components/BookmarkCards.kt`

- Defines `BookmarkGridCard`, `BookmarkCompactCard`, `BookmarkMosaicCard`, and a private `BookmarkCardActions` that renders a `MoreVert` dropdown — a pattern the current grid/compact cards do not use.
- Shadow-defines composable names that already exist in `ui/list/BookmarkCard.kt` (same base names, different package).
- Grep shows no external import of `com.mydeck.app.ui.list.components.BookmarkGridCard` or siblings.

### 2. `ui/list/components/BookmarkListComponents.kt`

- Calls into the shadow `BookmarkGridCard`/`BookmarkCompactCard`/`BookmarkMosaicCard` from the `.components.` package.
- No external references — grep across `app/src/main/java` returns no callers.
- Appears to be an alternate list-rendering path from the fork's pre-refactor era.

### 3. `ui/components/TimedDeleteSnackbar.kt`

- Hardcodes `DELETE_SNACKBAR_DURATION_MS = 5000L` and animates a timed progress bar.
- `BookmarkListScreen.kt:209` sets `duration = SnackbarDuration.Indefinite` for the pending-delete snackbar, so the timed component is not used for the live flow.
- Verify no other screen references it (e.g. reading list, label view) before deleting.

## Verification checklist (before removing)

Run these from `/mnt/projects/mydeck-android`:

- [ ] `grep -rn "ui.list.components.BookmarkGridCard\|ui.list.components.BookmarkCompactCard\|ui.list.components.BookmarkMosaicCard" app/src` — expect no results.
- [ ] `grep -rn "BookmarkListComponents\|BookmarkListContent\|BookmarkListLayout" app/src` — expect only the self-reference inside the file.
- [ ] `grep -rn "TimedDeleteSnackbar" app/src` — expect only the definition; remove if unreferenced elsewhere.
- [ ] `grep -rn "BookmarkCardActions" app/src` — expect only the private definition inside `BookmarkCards.kt`; confirm no sibling file leaks it.
- [ ] `./gradlew :app:assembleDebugAll :app:lintDebugAll :app:testDebugUnitTestAll` — must remain green after deletions.

## Removal steps

1. Delete `app/src/main/java/com/mydeck/app/ui/list/components/BookmarkCards.kt`.
2. Delete `app/src/main/java/com/mydeck/app/ui/list/components/BookmarkListComponents.kt`.
3. If the `TimedDeleteSnackbar` grep confirms no callers, delete `app/src/main/java/com/mydeck/app/ui/components/TimedDeleteSnackbar.kt` and any dependent string resources in `values/strings.xml` (and mirrors — see CLAUDE.md localization rules).
4. Remove any now-orphaned imports flagged by lint.
5. Run the aggregate debug verification tasks listed in the project's CLAUDE.md.

## Out of scope

- Any design change to the live card or delete-snackbar behavior. This cleanup is strictly removal of unreachable code.
- Touching `ui/list/BookmarkCard.kt` or `ui/list/BookmarkListScreen.kt`.
- Other `ui/*/components/` directories — review each on its own merit in a separate pass if needed.

## Risk notes

- The shadow composables compile only because they live in a different package; deleting them cannot change byte-code emitted for the live card path.
- `TimedDeleteSnackbar` removal is the only one with a non-zero chance of breaking a lesser-used screen; the grep gate protects that.
