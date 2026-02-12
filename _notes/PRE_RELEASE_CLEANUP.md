# Pre-Release Cleanup Tracker

Items to address before formal production release. This is a living document — add items as they are identified during code review and testing.

---

## Code Quality / Dead Code

- [ ] **Unused `ImageResourceDTO.kt`** — `app/src/main/java/com/mydeck/app/io/rest/model/ImageResourceDTO.kt` defines a separate `ImageResourceDTO` with nullable fields, but `BookmarkDto.kt` uses its own `ImageResource` class. Determine if `ImageResourceDTO` is referenced anywhere; if not, remove it.

## Error Handling / Resilience

- [ ] **Bookmark sync resilience** — `LoadBookmarksUseCase.execute()` deserializes an entire page of bookmarks at once. If any single bookmark in the page has unexpected data, the whole sync aborts. Consider adding per-bookmark error handling so one malformed bookmark doesn't prevent the rest from loading. Options:
  - Catch deserialization errors per-page and skip/log the problematic page
  - Switch to streaming/individual bookmark parsing
  - Add a retry mechanism that narrows down to the problematic bookmark

## Testing

- [ ] **BookmarkRepositoryImpl test coverage** — Tests cover ~40% of functionality. See memory/notes for detailed phase plan covering: `createBookmark()`, `refreshBookmarkFromApi()`, `updateLabels()`, `updateReadProgress()`, `renameLabel()`, `deleteLabel()`, observe/flow methods, and search.
- [ ] **Re-enable CI test execution** — Tests currently disabled in CI due to coverage gaps.

## Bugs Fixed (Pending Verification)

- [x] **ImageResource deserialization crash (Stefan's issue #143)** — `width`/`height` fields in `BookmarkDto.ImageResource` were required with no defaults, causing `MissingFieldException` when Readeck omits them. Fixed by adding `= 0` defaults. Awaiting Stefan's confirmation.
- [x] **Welcome screen icon** — Was showing legacy ReadeckApp vector drawable; changed to `R.mipmap.ic_launcher_foreground` (MyDeck three-card icon).

## Items to Review Before Release

- [ ] **Legacy ReadeckApp drawable vector** — `res/drawable/ic_launcher_foreground.xml` still contains the old two-shape ReadeckApp icon. Consider replacing it with a vector version of the MyDeck three-card icon or removing it if nothing else references it.
- [ ] **Rename misleading sync status field** — Sync Settings shows “My List” (non-archived) but currently threads that value through a field named `unread` in the sync status model/UI wiring. Rename to something like `myList` (and update related UI/resource keys) to avoid future confusion/regressions.
- [ ] **OAuth migration branch merge** — Awaiting Stefan's testing confirmation before merging `feature/OAuth-migration` into main.
- [ ] **Full code review pass** — Systematic review of all changes since fork from ReadeckApp for naming, style, and correctness.
