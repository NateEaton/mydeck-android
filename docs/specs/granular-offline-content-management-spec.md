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

## Sync Settings Screen Redesign

### Current Layout (Problems)

The current screen has four sections:

1. **Bookmark Sync** — frequency, manual sync button
2. **Content Sync** — mode (automatic/manual/date-range), constraints (wifi-only, battery)
3. **Sync Status** — bookmark counts, content counts, timestamps
4. **Storage** — usage size, clear all button

Problems with this layout when adding new settings:

- **"Content Sync" conflates three concerns:** *when* to sync (mode), *what* to sync
  (currently implicit), and *under what conditions* (constraints). Adding image download
  toggle, sync scope, and auto-clear here would make it unwieldy.
- **Constraints are visually coupled to content sync mode** but actually apply to both
  automatic sync and date-range sync — the nesting is misleading.
- **Storage cleanup is isolated** from the content settings that determine what gets stored.
- **No clear grouping around archive behavior** — the auto-clear-on-archive setting and the
  sync scope filter both relate to how archived bookmarks interact with offline content, but
  there's no natural home for them in the current structure.

### Proposed Layout

The redesigned screen groups settings by concern: scheduling, content policy, and storage.
Sync status is kept for diagnostics but is deliberately placed last — it's informational,
not actionable.

```
┌─────────────────────────────────────────────┐
│  ← Sync Settings                            │
├─────────────────────────────────────────────┤
│                                             │
│  SCHEDULE                                   │
│                                             │
│  Bookmark sync frequency         [1 hour ▼] │
│  Next run: in 45 minutes                    │
│  [       Sync bookmarks now        ]        │
│                                             │
│  Content sync mode                          │
│  ○ Automatic                                │
│    Downloaded during bookmark sync          │
│  ○ Manual                                   │
│    ○ On demand                              │
│    ○ By date range                          │
│      [Past week ▼]                          │
│      [       Download content       ]       │
│                                             │
│─────────────────────────────────────────────│
│                                             │
│  CONTENT                                    │
│                                             │
│  What to download                           │
│  Sync scope                    [My List ▼]  │
│    Which bookmarks to download content for  │
│    when syncing automatically or by date    │
│    range. On-demand downloads are always    │
│    available regardless of this setting.    │
│  Download images                     [ON ]  │
│    Download images alongside article text.  │
│    When off, images load from the network.  │
│                                             │
│  Constraints                                │
│  Wi-Fi only                          [OFF]  │
│  Allow on battery saver              [ON ]  │
│                                             │
│─────────────────────────────────────────────│
│                                             │
│  STORAGE                                    │
│                                             │
│  Offline storage usage: 142 MB              │
│                                             │
│  Cleanup                                    │
│  Clear content when archiving        [OFF]  │
│    Automatically remove downloaded text     │
│    and images when a bookmark is archived.  │
│  [     Clear all offline content      ]     │
│                                             │
│─────────────────────────────────────────────│
│                                             │
│  STATUS                                     │
│                                             │
│  Bookmarks                                  │
│  Total: 1,234                               │
│  Unread: 56                                 │
│  Archived: 1,100                            │
│  Favorites: 42                              │
│  Last sync: 10 minutes ago                  │
│                                             │
│  Content                                    │
│  Downloaded: 78                             │
│  Available: 156                             │
│  Needs refresh: 3                           │
│  No content: 12                             │
│  Last content sync: 1 hour ago              │
│                                             │
└─────────────────────────────────────────────┘
```

### Design Rationale

**Schedule** combines both bookmark sync and content sync scheduling. These are the "when"
questions: how often does metadata sync run, and when does content get downloaded (auto vs.
manual vs. date range). The user thinks about these together — "I want my bookmarks to sync
every hour and content to download automatically" is a single mental model.

Bookmark sync frequency and the manual sync button stay at the top because they're the most
frequently adjusted settings and the primary entry point for "why aren't my bookmarks
showing up?"

**Content** answers "what gets downloaded and under what conditions." This groups:
- **Sync scope** (all vs. My List) — scopes which bookmarks are eligible
- **Download images** toggle — controls what's included in each download
- **Constraints** (Wi-Fi only, battery saver) — conditions under which content download occurs

These are all "content download policy" settings. They apply uniformly to automatic,
date-range, and (in the case of images) on-demand downloads.

**Important:** Constraints apply only to content sync (downloading article text and images),
not to bookmark metadata sync. Metadata sync (titles, URLs, labels, read status, etc.)
should always run whenever any internet connection is available, regardless of Wi-Fi or
battery state. The rationale is that metadata is small and essential for the app to function,
while content packages are large and deferrable. This is already the current implementation
behavior — `FullSyncUseCase` only requires `NetworkType.CONNECTED` with no additional
constraints. The constraints settings should be labeled and described in a way that makes
clear they govern content downloads specifically.

The sync scope selector deserves a brief supporting text to clarify that it only affects
automatic and date-range syncs — on-demand downloads (tapping into an article) always work
regardless of scope. This prevents confusion where a user sets "My List only," archives an
article, then can't figure out why tapping it still loads content.

**Storage** groups everything related to disk usage and cleanup:
- Current usage display
- Auto-clear on archive (the automated cleanup mechanism)
- Clear all button (the manual nuclear option)

This answers "how much space is used, and how do I manage it?" Auto-clear-on-archive belongs
here rather than under Content because its purpose is storage management, not download policy.
The user's mental model is: "I'm concerned about storage → here are my cleanup options."

**Status** moves to the bottom. It's diagnostic/informational — users check it occasionally
to verify sync is working, not as a primary interaction. Placing it last keeps the actionable
settings front-and-center.

### Interaction Notes

- **Sync scope + auto-clear on archive:** These two settings are complementary but independent.
  A user might want to sync only My List (don't download archived content) but NOT auto-clear
  (keep existing downloaded content when archiving, just don't re-download it on next sync).
  Or they might sync everything but auto-clear on archive (download archived content initially,
  clean up when they're done with it). Both combinations are valid.

- **Download images + sync scope:** When "Download images" is off and a date-range sync runs,
  it downloads text-only packages. If the user later turns "Download images" on, existing
  text-only packages are NOT retroactively upgraded — only new downloads include images.
  The per-article image toggle on the detail page handles upgrading individual articles.

- **Clear all offline content:** Continues to work as before — removes all text and images for
  all bookmarks, resets all content state. The confirmation dialog should mention that this
  affects all bookmarks regardless of sync scope or archive status.

### Open Layout Questions

1. **Sync scope selector style:** Dropdown (compact) vs. radio buttons (more explicit)?
   Only two options currently (All / My List only), so either works. A dropdown is more
   compact and leaves room for future options (e.g., "Favorites only").

2. **Section headers:** The current design uses `titleMedium` text for section headers. The
   proposed layout uses all-caps section labels (SCHEDULE, CONTENT, STORAGE, STATUS) which
   is a common M3 pattern for settings screens. Either style works — should match the rest
   of the app's settings screens.

3. **Constraints placement:** Constraints could alternatively stay in the Schedule section
   (they affect *when* sync runs). However, they're really about *whether* to download, which
   is more of a content policy concern. Placing them under Content keeps the Schedule section
   focused on timing.

## Content Sync Constraint Handling

### Current Behavior (Problems)

Content sync constraints (Wi-Fi only, battery saver) are handled inconsistently across
sync trigger paths:

| Trigger | Metadata sync | Content sync constraint handling |
|---------|--------------|--------------------------------|
| Periodic auto-sync (`FullSyncWorker`) | `CONNECTED` only (correct) | Enqueues `BatchArticleLoadWorker` via `enqueueContentSyncIfNeeded()`. The `ContentSyncPolicyEvaluator` checks constraints before deciding to enqueue, but the WorkManager request itself only requires `CONNECTED` — constraints are not applied to the work request. |
| "Sync now" button (`FullSyncWorker`) | `CONNECTED` only (correct) | Same as periodic — relies on evaluator gate, no constraints on work request. |
| App open (`LoadBookmarksWorker`) | `CONNECTED` only (correct) | Same — calls `enqueueContentSyncIfNeeded()` with evaluator gate. |
| Pull to refresh (`LoadBookmarksWorker`) | `CONNECTED` only (correct) | Same as app open. |
| Date range download (`DateRangeContentSyncWorker`) | N/A | **Correct** — `SyncSettingsViewModel` checks constraints, shows override dialog if blocked, and applies constraints to the WorkManager request. |

**Problems identified:**

1. **Silent failure on automatic content sync.** When `ContentSyncPolicyEvaluator.shouldAutoFetchContent()`
   returns false due to a constraint (e.g., battery saver active, not on Wi-Fi), the content
   sync simply doesn't run. The user gets no feedback — bookmarks sync but content doesn't
   download, and there's no indication why. This is likely what caused confusion during testing
   on the multipart sync branch.

2. **Race condition on evaluator gate.** The evaluator checks constraints at enqueue time, but
   conditions can change between the check and when WorkManager actually runs the worker. If
   the evaluator approves enqueue and then battery saver activates, the worker runs anyway
   because the work request has no battery constraint.

3. **`BatchArticleLoadWorker.enqueue()` ignores user constraint preferences.** It hardcodes
   `NetworkType.CONNECTED` without reading Wi-Fi-only or battery-saver settings. The evaluator
   gate is the only protection, and it has the race condition described above.

4. **No user feedback mechanism for automatic triggers.** The date-range path has a clean UX
   (override dialog), but automatic sync has nothing comparable. The user opens the app,
   metadata syncs fine, but content silently doesn't download.

### Proposed Behavior

**Principle:** Metadata sync always runs on any internet connection. Content sync respects
user constraints. The user should always understand why content isn't downloading.

#### Fix 1: Apply constraints to WorkManager requests

`enqueueBatchArticleLoader()` in `LoadBookmarksUseCase` should read the user's constraint
preferences and apply them to the WorkManager request:

```kotlin
private suspend fun enqueueBatchArticleLoader() {
    val syncConstraints = settingsDataStore.getContentSyncConstraints()
    val constraintsBuilder = Constraints.Builder()

    if (syncConstraints.wifiOnly) {
        constraintsBuilder.setRequiredNetworkType(NetworkType.UNMETERED)
    } else {
        constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
    }

    if (!syncConstraints.allowOnBatterySaver) {
        constraintsBuilder.setRequiresBatteryNotLow(true)
    }

    val request = OneTimeWorkRequestBuilder<BatchArticleLoadWorker>()
        .setConstraints(constraintsBuilder.build())
        .build()

    workManager.enqueueUniqueWork(
        BatchArticleLoadWorker.UNIQUE_WORK_NAME,
        ExistingWorkPolicy.KEEP,
        request
    )
}
```

This eliminates the race condition — WorkManager itself enforces the constraints, so even
if conditions change after enqueue, the worker won't run until constraints are satisfied.
When conditions are met later (e.g., phone is plugged in, Wi-Fi connects), WorkManager
automatically runs the pending work.

This also means `ContentSyncPolicyEvaluator.shouldAutoFetchContent()` no longer needs to
check constraints — it only needs to check whether the content sync mode is `AUTOMATIC`.
The constraint enforcement moves from the decision layer to the execution layer, which is
where it belongs.

#### Fix 2: User feedback for constraint-blocked content sync

When content sync is automatic but constraints prevent it from running, the user should
see a subtle, non-intrusive indication. Options considered:

**Option A: Passive indicator in the bookmark list.**
Show a small informational banner or icon in the list screen when content sync is pending
but constraint-blocked. Something like a subtle bar: "Content download waiting for Wi-Fi"
or "Content download paused (battery saver)". Dismissible, non-blocking. This is the
recommended approach — it provides awareness without interrupting the user's flow.

**Option B: Snackbar on app open.**
When app-open sync completes metadata but skips content due to constraints, show a brief
snackbar: "Content download waiting for Wi-Fi." Low friction but easy to miss.

**Option C: Notification.**
If the user has automatic content sync enabled and constraints block it for an extended
period, post a low-priority notification. This is the most discoverable option but also the
most intrusive — probably overkill for a non-urgent situation.

**Option D: Status in sync settings only.**
Add a "Content sync status" line in the sync settings STATUS section showing the current
state: "Waiting for Wi-Fi", "Paused (battery saver)", "Up to date", etc. This is the
least intrusive — the user would only see it if they go to sync settings. Good as a
baseline that complements one of the other options.

**Recommendation:** Option B (snackbar on app open) + Option D (status in sync settings).
The snackbar provides timely, one-time awareness when the user opens the app. The sync
settings status provides a persistent place to check. Neither interrupts the reading
experience.

#### Fix 3: No override dialog for automatic sync

Unlike the date-range download (which is user-initiated and warrants an override dialog),
automatic content sync should simply wait for constraints to be satisfied. The user set
these constraints deliberately — prompting them to override on every app open would be
annoying. The correct behavior is:

1. Enqueue the content sync work with proper constraints on the WorkManager request.
2. WorkManager holds the work until constraints are met.
3. When the user connects to Wi-Fi or exits battery saver, the pending work runs
   automatically.
4. The user gets a snackbar and/or status indicator explaining the wait.

This is consistent with how other Android apps handle constrained background work — the
work is deferred, not cancelled or prompted.

#### Fix 4: Pull-to-refresh and "Sync now" should behave like date-range override

When the user explicitly triggers a sync via pull-to-refresh or the "Sync now" button, and
content sync mode is automatic, and constraints would block content download:

- **Metadata sync** proceeds immediately (no constraints).
- **Content sync** should show the same override dialog as date-range downloads, since the
  user has explicitly requested a sync and deserves feedback about why content isn't
  downloading. If they override, the content work is enqueued without constraints. If they
  cancel, content waits for constraints to be met.

This gives a consistent pattern: user-initiated sync triggers → override dialog if blocked.
Background/periodic sync → silent wait with passive indication.

### Summary of Constraint Handling by Trigger

| Trigger | Metadata | Content | If constrained |
|---------|----------|---------|----------------|
| Periodic auto-sync | Always runs | WorkManager constraints | Deferred silently; passive indicator |
| App open | Always runs | WorkManager constraints | Deferred; snackbar on app open |
| Pull to refresh | Always runs | Check constraints | Override dialog (user-initiated) |
| "Sync now" button | Always runs | Check constraints | Override dialog (user-initiated) |
| Date range download | N/A | Check constraints | Override dialog (existing behavior) |

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
6. **Constraint feedback mechanism:** Which combination of snackbar, banner, notification,
   and settings status best communicates why content isn't downloading? See Fix 2 options above.

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
