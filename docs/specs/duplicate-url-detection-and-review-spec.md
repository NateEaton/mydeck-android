# Duplicate URL Detection and Review — Technical Specification

Status: Draft for implementation  
Date: 2026-03-15

## 1. Purpose

Define an implementation plan for duplicate URL awareness during bookmark creation, plus a dedicated in-app duplicate review workflow.

This spec covers two phases:

1. V1: Add-sheet duplicate detection and in-sheet review
2. V2: Global duplicate review screen for collection-wide inspection

## 2. Definitions and Confirmed Decisions

## 2.1 Duplicate definition

A duplicate is any bookmark whose `url` field is an exact string match with the URL currently in the add sheet.

- No URL normalization
- No canonicalization
- No host/path/query rewriting
- No case folding

## 2.2 Data source and consistency model

Duplicate checks are local-first, using the synced local bookmark database.

Assumptions accepted for this feature:

- The app is typically a single-user, single-client workflow
- Local metadata sync is strong enough for practical duplicate awareness
- Rare cross-client lag cases are acceptable and treated as normal eventual consistency

## 2.3 Entry point parity

Duplicate behavior must be consistent across both bookmark-add entry paths:

- In-app FAB add sheet
- Share-intent add sheet

## 3. Goals

1. Provide subtle duplicate awareness while adding bookmarks
2. Support one or many duplicates
3. Keep duplicate creation allowed, since intentional duplicate saves are valid
4. Provide enough context in review for the user to decide whether to proceed
5. Avoid forcing navigation away from add flow in V1

## 4. Non-Goals

1. Preventing duplicate creation
2. Enforcing uniqueness in Room or via API
3. Fuzzy URL matching or canonical URL comparison
4. Automatic duplicate cleanup or merge
5. Treating duplicates as errors

## 5. UX Specification — V1 Add Sheet Review

## 5.1 Duplicate indicator placement

When duplicates exist for a valid URL, display a subtle duplicate indicator icon to the right of the URL field, outside the text field itself.

- Hidden when URL is empty
- Hidden when URL is invalid
- Hidden when no duplicates exist
- Visible when duplicate count is at least 1
- Subtle visually, but clearly tappable

## 5.2 Check timing and trigger behavior

### 5.2.1 Immediate checks

Run a duplicate check immediately when the URL field is already populated when the sheet appears, including:

1. Share-intent populated URL
2. Clipboard-derived prefill when opening the add sheet
3. Reopened add sheet state that already contains a valid URL

### 5.2.2 Edit checks

When the user edits the URL manually, run checks with debounce and only when the URL is syntactically valid.

Recommended debounce: about 300 ms.

Rationale:

- Avoid noisy per-keystroke queries
- Preserve responsiveness for paste-heavy workflows
- Still handle manual edits correctly

## 5.3 Indicator interaction

Tapping the duplicate indicator opens an in-sheet "Existing matches" panel rather than navigating away to a list screen.

- The add sheet remains the active context
- URL, title, labels, and favorite state are preserved
- The user can close the panel and continue adding the bookmark

## 5.4 Existing matches panel content

For each matching bookmark instance, show:

1. Added date and time
2. Location state: `My List` or `Archive`
3. Title match status compared to the current title field:
- `Match`
- `Different`
- `No title entered`

The panel should also show aggregate count at the top, for example: "3 existing bookmarks for this URL".

## 5.5 Existing matches ordering

Sort matches by creation date descending, newest first.

Reason: the newest entry is most likely to reflect the most relevant current capture.

## 5.6 Existing matches actions

Each existing match row should support at least:

- Open bookmark detail

Secondary actions can be deferred to V2.

## 5.7 Share-intent timer interaction

In share-intent mode, opening or interacting with duplicate review must cancel the existing auto-save countdown, just like any other explicit user interaction.

Reason: the user has shifted from quick-save to review mode.

## 6. Data and Logic Specification — V1

## 6.1 Query criteria

Duplicate lookup criteria:

- `bookmarks.url == inputUrl`
- `isLocalDeleted = 0`

Returned fields for V1:

- `id`
- `title`
- `created`
- `isArchived`

## 6.2 Title match semantics

Title comparison is informational only and does not affect duplicate detection.

Recommended V1 title comparison behavior:

- Trim both titles
- Compare case-insensitively

If the add-sheet title is blank, show `No title entered` for every duplicate row.

## 6.3 Suggested UI state additions

The add-sheet state should include derived duplicate info, for example:

- `duplicateCount: Int`
- `duplicateMatches: List<DuplicateMatchUi>`
- `isDuplicatePanelOpen: Boolean`

Each `DuplicateMatchUi` should contain:

- `bookmarkId: String`
- `title: String`
- `created: LocalDateTime`
- `isArchived: Boolean`
- `titleMatchState: TitleMatchState`

## 6.4 Performance approach

V1 should start without schema changes.

Use debounced valid-URL checks first. If real-world profiling shows latency on larger bookmark collections, add an index on `url` in a follow-up database migration.

## 7. Navigation and State Handling — V1

## 7.1 In-app add sheet

Opening an existing match should navigate to bookmark detail using the existing bookmark detail flow.

Expected behavior:

- Add sheet closes when user explicitly chooses to open an existing bookmark
- If the user later wants to add the bookmark anyway, they reopen the sheet manually

## 7.2 Share-intent add sheet

Opening an existing match should hand off to the existing `MainActivity` bookmark-detail navigation flow, similar to current share-view behavior.

Expected behavior:

- Share activity finishes after explicit navigation handoff
- Draft preservation is not required after the user intentionally leaves the add flow

## 7.3 Preserve context inside add flow

As long as the user stays inside the add sheet and only opens/closes the in-sheet matches panel, preserve URL, title, labels, and favorite state unchanged.

## 8. Phase 2 Feature — Global Duplicate Review

## 8.1 Why this should not live in the main filter sheet

Do not add duplicate detection to the current filter sheet in V1.

Reason:

- The filter sheet already combines many orthogonal constraints
- Duplicate review is a collection-health workflow, not just a view refinement
- A duplicate-focused workflow should show complete groups, not a subset accidentally narrowed by unrelated filters

## 8.2 V2 entry point

Add a dedicated entry point for duplicate review, for example:

- Settings or sync tools
- List overflow menu

This should open a dedicated screen that shows only URL groups where the count is greater than 1.

## 8.3 V2 top-level list model

Top-level items are duplicate URL groups.

Per group show:

- URL
- Duplicate count
- My List count
- Archive count
- Newest added date
- Oldest added date

## 8.4 V2 group detail model

Within a URL group, list each bookmark instance with:

- Added date and time
- My List or Archive state
- Title
- Actions: open, archive or unarchive, delete

No default bulk delete in initial V2.

Reason: multiple copies of the same URL may represent intentionally saved snapshots over time.

## 8.5 V2 safety principles

1. No automatic removal
2. No implicit "clean all duplicates" action
3. User stays in control per bookmark instance

## 9. Relationship to `withErrors`

This feature is separate from `withErrors`.

- Duplicates are not extraction errors
- Duplicates are not content-load failures
- Any future redesign or removal of `withErrors` should be handled independently
- Duplicate review should remain a dedicated workflow even if `withErrors` changes

## 10. Implementation Impact Areas

Primary code areas expected to change for V1:

- `app/src/main/java/com/mydeck/app/ui/list/AddBookmarkSheet.kt`
- `app/src/main/java/com/mydeck/app/ui/list/BookmarkListViewModel.kt`
- `app/src/main/java/com/mydeck/app/ui/list/BookmarkListScreen.kt`
- `app/src/main/java/com/mydeck/app/ui/share/ShareActivity.kt`
- `app/src/main/java/com/mydeck/app/domain/BookmarkRepository.kt`
- `app/src/main/java/com/mydeck/app/domain/BookmarkRepositoryImpl.kt`
- `app/src/main/java/com/mydeck/app/io/db/dao/BookmarkDao.kt`

For V2 later:

- New route, screen, and view model for duplicate review
- Additional DAO query for grouped duplicates using `GROUP BY url HAVING COUNT(*) > 1`

## 11. Localization and Documentation Requirements

When implemented, add new string resources with English placeholders to all locale `strings.xml` files per project rule.

User-visible behavior updates will also require English guide updates under:

- `app/src/main/assets/guide/en/your-bookmarks.md`
- `app/src/main/assets/guide/en/organizing.md`

## 12. Test Plan

## 12.1 V1 behavior tests

1. Indicator hidden when URL is empty
2. Indicator hidden when URL is invalid
3. Indicator hidden when URL is valid and no duplicates exist
4. Indicator shown when one duplicate exists
5. Indicator shown when multiple duplicates exist
6. Prefill paths trigger immediate duplicate check
7. Manual URL edits trigger debounced duplicate check
8. Tapping indicator opens in-sheet matches panel
9. Panel rows show added date, location state, and title match status
10. Share-intent auto-save is cancelled by duplicate-review interaction
11. Tapping a match opens the correct bookmark detail

## 12.2 V2 behavior tests

1. Duplicate groups include only URLs with count greater than 1
2. Group counts and My List or Archive splits are correct
3. Group detail shows all instances for the selected URL
4. Per-instance actions work without bulk deletion side effects

## 13. Rollout Plan

1. Implement V1 only
2. Gather feedback on whether duplicate awareness is sufficient inside the add flow
3. Implement V2 if users need collection-wide review and cleanup tools
4. Reassess whether a `url` index is needed after observing real-world performance

## 14. Open Decisions to Confirm Before Build

1. Final icon asset choice for duplicate indicator
2. Exact debounce interval
3. Final date format in match rows: relative, absolute, or both
4. Final V2 entry location: settings or sync tools versus list overflow menu
