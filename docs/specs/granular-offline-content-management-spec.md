# Spec: Granular Offline Content Management (DRAFT)

## Status

Draft — under discussion. Not yet approved for implementation.

## Origin

User feedback on the `feature/sync-multipart-v012` branch identified that the current offline
content system downloads everything (text + images) with no way to manage storage granularly.
The only cleanup mechanism is a global "clear all offline content" action. Users want finer
control over what is downloaded, how much space is used, and when content is cleaned up.

## Summary

This spec extends the offline content architecture shipped in `v0.12.0` with:

1. A global setting controlling whether images are downloaded alongside article text
2. Per-article controls for downloading or removing images and content
3. Automatic content cleanup on archive
4. Scope filtering for content sync (all bookmarks vs. My List only)
5. A visual indicator in bookmark lists showing offline content state

These features work together to give users control over offline storage without requiring
manual per-article management in the common case.

## Goals

1. Let users control whether article images are stored locally or lazy-loaded from the network.
2. Provide per-article controls for downloading images and removing offline content.
3. Automatically clean up offline content for archived bookmarks (opt-in).
4. Let users scope content sync to unarchived bookmarks only.
5. Show offline content state visually in bookmark lists.
6. Keep the UX simple — avoid overwhelming users with granular options.

## Non-Goals

- Text-only mode with images stripped from HTML (deferred; see Design Decisions)
- Multi-select bulk download/delete (deferred; will be part of the multi-select list feature)
- Auto-prune by age, count, or size thresholds (potential future enhancement)
- Swipe actions for download/delete (separate feature)
- Offline video playback or video content download
- Changes to how Picture bookmarks are handled (always download the image)
- Changes to how Video bookmarks are handled (embed-based, no change)

## Design Decisions

### Two download tiers, not three

The system supports two tiers for article content:

| Tier | What's stored | Image behavior |
|------|--------------|----------------|
| **Full** | HTML + all images on disk | Images render from local files |
| **Text + lazy images** | HTML on disk, original absolute image URLs | Images load from network when online; broken-image icons when offline |

A third tier — "text only" with `<img>` tags stripped from HTML — was considered and deferred.
The added complexity of HTML post-processing, the need for a third icon state, and the
difficulty of cleanly reversing the transformation (requires re-fetch) outweigh the marginal
benefit over the lazy-images approach. The lazy-images tier already solves the primary storage
concern (images are the bulk of offline content size) while remaining simple to implement and
fully reversible.

### Per-article image toggle is not sticky across syncs

When a user manually cycles the image download state for a specific article, that change takes
effect immediately but is not persisted as a durable per-article override. The next time a
background or date-range sync processes that bookmark, the global "download images" setting
determines whether images are fetched.

Rationale: users may have thousands of bookmarks, and tracking per-article overrides adds
data model complexity for a low-value edge case. The more common storage management
mechanisms — auto-clear on archive, global image download setting, and the sync scope filter —
handle the bulk of the use case.

### Sync scope filter applies to content sync only

The "sync content for: All bookmarks / My List only" setting controls which bookmarks are
eligible for content download during automatic and date-range syncs. It does not affect
metadata sync — bookmark metadata (title, URL, labels, read status, etc.) is always synced
for all bookmarks regardless of this setting.

### Auto-clear on archive removes all offline content

When auto-clear-on-archive is enabled, archiving a bookmark deletes both text and images
from local storage and resets the bookmark's content state to NOT_ATTEMPTED. There is no
option to clear only images on archive — the feature is intentionally simple.

### Picture and video bookmarks are unaffected by the image download setting

The global "download images" setting applies only to article bookmarks. Picture bookmarks
always download their image (it's the entire content). Video bookmarks are unchanged
(embed-based, metadata only).

## Feature Details

### 1. Global Setting: Download Images with Articles

**Location:** Sync Settings screen, in the Content Sync section.

**UI:** Toggle — "Download images" or similar. Default: on (preserves current behavior).

**Behavior:**

- When **on**: content sync fetches HTML + images via `fetchContentPackages()` with
  `withResources=true, resourcePrefix="."`. Current behavior, no change.
- When **off**: content sync fetches HTML only via a new fetch variant with
  `withHtml=true, withResources=false, resourcePrefix=null`. HTML is stored with
  original absolute image URLs. No resource files are written to disk.

**Affects:** `BatchArticleLoadWorker`, `DateRangeContentSyncWorker`, and on-demand loading
via `LoadContentPackageUseCase` (for new downloads only — existing content is not retroactively
modified when the setting changes).

### 2. Per-Article Image Download Toggle

**Location:** Bookmark detail page. Exact placement TBD — candidates include the overflow
menu, the planned multi-icon pill (if the detail page is redesigned), or a dedicated icon
in the top app bar.

**UI:** A cycling icon with two states:

- **Images downloaded** (image icon, normal): images are stored locally with the article.
  Clicking deletes image files from disk and updates the content package to reflect
  text-only storage. The HTML is re-fetched without `resourcePrefix` so image URLs revert
  to absolute. The current WebView session is unaffected (already-rendered images remain
  visible); the change takes effect on next open.
- **Images lazy-loaded** (image icon, greyed/variant): images are not stored locally.
  Clicking triggers a background download of images for this article. A progress indicator
  (e.g., M3 circular progress around the icon) shows download progress. Non-blocking —
  the user continues reading. The icon updates when the download completes.

**State determination:** Derived from `ContentPackageEntity.hasResources`. No new database
field is needed for this control.

### 3. Remove Downloaded Content (Per-Article)

**Location:** Bookmark detail overflow menu, replacing the current "Refresh content" button.

**UI:** Menu item — "Remove downloaded content" (or similar).

**Behavior:** Deletes the content package (HTML + images) from disk and database. Resets the
bookmark's `contentState` to `NOT_ATTEMPTED`. If the user opens the article again, it will
be fetched on demand per the normal loading flow.

**Also available in:** Long-press context menu on bookmark cards in the list view.

### 4. Auto-Clear on Archive

**Location:** Sync Settings screen, new toggle in the Content Sync section.

**UI:** Toggle — "Clear offline content when archiving" or similar. Default: off.

**Behavior:** When enabled, archiving a bookmark (from any surface — detail menu, list
context menu, future multi-select, future swipe action) triggers deletion of that bookmark's
offline content package and resets its content state to NOT_ATTEMPTED.

**Implementation:** Hook into the archive toggle action. After the archive state is persisted,
check the preference and call the per-bookmark content deletion if enabled.

### 5. Sync Scope Filter

**Location:** Sync Settings screen, in the Content Sync section.

**UI:** Setting — "Sync content for: All bookmarks / My List only". Default: All bookmarks
(preserves current behavior).

**Behavior:** When set to "My List only", the eligibility queries for `BatchArticleLoadWorker`
and `DateRangeContentSyncWorker` add `AND isArchived = 0` to exclude archived bookmarks
from content sync.

**Edge case:** If a user archives an article (auto-clear removes content), then un-archives it,
the content is gone and will be re-fetched on demand or by the next sync cycle. This is
acceptable — un-archiving is rare, and on-demand loading handles it transparently.

### 6. Visual Indicator in Bookmark Lists

**Location:** Bookmark cards in all list views (My List, Archive, Favorites, Labels, etc.).

**UI:** A small icon on the bookmark card indicating offline content state. Options:

- Downloaded (full — text + images): solid download/cloud-done icon
- Downloaded (text only — lazy images): variant icon (TBD)
- Not downloaded: no icon (default state, no visual clutter)

**State source:** `BookmarkEntity.contentState` (already available to list items) combined
with `ContentPackageEntity.hasResources` (may need to be surfaced to the list item model
via a JOIN or denormalized field).

**Design note:** The exact icon treatment and placement on the card are TBD. Should be subtle
enough not to clutter the card but visible enough to be useful.

## Data Model Changes

### ContentPackageEntity

No new fields required. The existing `hasResources` boolean distinguishes full packages from
text-only packages. The `packageKind` and `hasHtml` fields remain as-is.

### BookmarkEntity

No changes to the entity itself. The `contentState` enum (NOT_ATTEMPTED, DOWNLOADED, DIRTY,
PERMANENT_NO_CONTENT) remains unchanged.

### SettingsDataStore

New preference keys:

- `downloadImagesWithArticles: Boolean` (default: true)
- `clearContentOnArchive: Boolean` (default: false)
- `contentSyncScope: ContentSyncScope` (enum: ALL, MY_LIST_ONLY; default: ALL)

### List item model

The bookmark list item model (used by bookmark cards) may need a new field to surface the
`hasResources` state from the content package, so the visual indicator can distinguish
"downloaded with images" from "downloaded text only." This could be:

- A JOIN in the list query adding `hasResources` from `content_package`
- A denormalized `hasResources` field on `BookmarkEntity` updated by `ContentPackageManager`

The denormalized approach is simpler for the list query but adds a field to keep in sync.
The JOIN is more normalized but may affect list query performance. TBD during implementation.

## Implementation Approach

### MultipartSyncClient

Add a new fetch method (or parameterize the existing one):

```kotlin
suspend fun fetchTextOnly(bookmarkIds: List<String>): Result {
    return fetchBatched(bookmarkIds, CONTENT_BATCH_SIZE) { batch ->
        SyncRequest(
            id = batch,
            withJson = true,
            withHtml = true,
            withResources = false
            // resourcePrefix omitted — server returns absolute URLs
        )
    }
}
```

### ContentPackageManager

Add a per-bookmark deletion method:

```kotlin
suspend fun deleteContentForBookmark(bookmarkId: String) {
    contentPackageDao.deleteResources(bookmarkId)
    contentPackageDao.deletePackage(bookmarkId)
    cachedAnnotationDao.deleteAnnotationsForBookmark(bookmarkId)
    File(offlineContentDir, bookmarkId).deleteRecursively()
    bookmarkDao.updateContentState(
        bookmarkId,
        BookmarkEntity.ContentState.NOT_ATTEMPTED.value,
        null
    )
}
```

Add a method to delete only image resources for a bookmark (for the image toggle):

```kotlin
suspend fun deleteResourcesForBookmark(bookmarkId: String) {
    // Delete resource files from disk (keep index.html)
    val dir = File(offlineContentDir, bookmarkId)
    contentPackageDao.getResources(bookmarkId).forEach { resource ->
        File(dir, resource.localRelativePath).delete()
    }
    // Remove resource records from DB
    contentPackageDao.deleteResources(bookmarkId)
    // Update package to reflect no resources
    contentPackageDao.getPackage(bookmarkId)?.let { pkg ->
        contentPackageDao.insertPackage(pkg.copy(hasResources = false))
    }
}
```

Note: when cycling from full → lazy-images, the stored HTML has relative URLs
(`resourcePrefix="."` was used at download time). This HTML needs to be re-fetched without
`resourcePrefix` so image URLs revert to absolute, or alternatively the URLs could be
rewritten client-side. Re-fetching the HTML is simpler and avoids fragile URL rewriting.

### Workers

`BatchArticleLoadWorker` and `DateRangeContentSyncWorker` read the `downloadImagesWithArticles`
and `contentSyncScope` preferences and adjust their fetch calls and eligibility queries
accordingly.

### Archive Action

The archive toggle handler (in `BookmarkListViewModel`, `BookmarkDetailViewModel`, and any
future surfaces) checks `clearContentOnArchive` after persisting the archive state change
and calls `ContentPackageManager.deleteContentForBookmark()` if enabled and the bookmark
is being archived (not un-archived).

### BookmarkDetailMenu

- Remove "Refresh content" menu item.
- Add "Remove downloaded content" menu item (visible when `contentState == DOWNLOADED`).
- Add per-article image download toggle (placement TBD — see Feature Details §2).

### BookmarkCard Context Menu

- Add "Remove downloaded content" to the long-press context menu (visible when
  `contentState == DOWNLOADED`).

## Sync Settings Screen Changes

The Content Sync section gains:

1. **Download images** toggle (below the sync mode selector)
2. **Sync content for** selector: All bookmarks / My List only
3. **Clear offline content when archiving** toggle

The existing storage section (offline storage usage + clear all button) remains unchanged.

## Open Questions

1. **Visual indicator design:** What icon and placement on bookmark cards best communicates
   offline state without adding clutter?
2. **Detail page redesign interaction:** The per-article image toggle and "remove downloaded
   content" action may move to different locations if the detail page is redesigned with a
   persistent/swipeable details panel and multi-icon pill. This spec should be updated to
   reflect the final detail page design.
3. **Broken-image icon styling:** When articles are in text+lazy-images mode and opened
   offline, should we invest in a JS error handler to replace broken-image icons with styled
   placeholders? This is independent of the download tier feature and could be done later.
4. **Default for sync scope:** Should "My List only" be the default rather than "All bookmarks"?
   It's arguably the more sensible default, especially when combined with auto-clear on archive.
5. **Re-fetch HTML on image removal:** When cycling from full → lazy-images for a specific
   article, the stored HTML has relative image URLs. The simplest fix is to re-fetch HTML
   without `resourcePrefix`. Is a lightweight HTML-only fetch acceptable here, or should we
   explore client-side URL rewriting?

## Relationship to Other Features

- **Multi-select in list view:** Bulk download/delete of offline content will be an action
  in the multi-select toolbar, alongside archive, favorite, read status, label, and delete.
  That feature has its own design (selection mode, modified top bar, etc.) and is not covered
  by this spec.
- **Swipe actions on bookmark cards:** Configurable swipe actions are a separate feature.
  "Clear downloaded content" could be a swipe action option but is low-value compared to
  archive, favorite, delete, and read status.
- **Detail page redesign:** A planned redesign may make the details panel persistent in
  tablet-landscape and swipeable from the right on other form factors. The per-article
  controls described here would move to that new layout.
