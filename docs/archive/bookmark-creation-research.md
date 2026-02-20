# Research: Bookmark Creation, Sync API Issues, and Incremental Ingestion Strategy

## Part 1: The 500 Error on the Sync Endpoint

### The Problem

Calls to `GET /api/bookmarks/sync?since={timestamp}` return HTTP 500 Internal Server Error. The Eckard client (Flutter/Dart) reports the same issue. The AI Studio analysis identifies the likely cause: **sub-second timestamp precision causes a server-side SQL error when the Readeck instance uses SQLite**.

### Evidence: Both Clients Send Sub-Second Timestamps

**MyDeck (Kotlin):**
```kotlin
// BookmarkRepositoryImpl.kt:447
val sinceParam = since?.toString()  // Instant.toString() → "2025-04-03T19:00:13.646333268Z"
```
`kotlinx.datetime.Instant.toString()` produces RFC 3339 with nanosecond precision.

**Eckard (Dart):**
```dart
// api.dart:245
final String sinceString = Uri.encodeComponent(since.toIso8601String());
// DateTime.toIso8601String() → "2025-04-03T19:00:13.000Z"
```
Dart's `toIso8601String()` produces millisecond precision.

Both send sub-second fractions. The Readeck server (Go) passes this timestamp into a SQL query. When the backend is **SQLite** (as on your NAS), the timestamp comparison fails because SQLite's datetime functions don't handle fractional seconds the same way PostgreSQL does.

### The Fix

**Strip sub-second precision before sending the `since` parameter.** Truncate to whole seconds.

**In `BookmarkRepositoryImpl.performDeltaSync()`:**
```kotlin
// CURRENT
val sinceParam = since?.toString()

// FIX: Truncate to seconds to avoid SQLite timestamp parsing errors
val sinceParam = since?.let {
    // Truncate to seconds: "2025-04-03T19:00:13Z" (no fractional part)
    val truncated = Instant.fromEpochSeconds(it.epochSeconds)
    truncated.toString()  // Produces "2025-04-03T19:00:13Z"
}
```

`Instant.fromEpochSeconds(it.epochSeconds)` drops the nanosecond component entirely, producing a clean `"2025-04-03T19:00:13Z"` string.

### Validation

After applying this fix:
1. `GET /api/bookmarks/sync?since=2025-04-03T19:00:13Z` should return a JSON array of sync status objects instead of a 500 error.
2. If it still 500s with no `since` parameter at all (i.e., `GET /api/bookmarks/sync`), the issue is something else entirely (e.g., server bug with the empty-since case). Test both.

---

## Part 2: POST /bookmarks/sync — Empty Payload Guard

The AI Studio doc identifies a second 500 trigger: calling `POST /api/bookmarks/sync` with an empty ID list causes a SQL syntax error (`IN ()`).

**Current code in `BookmarkRepositoryImpl`:** The `performDeltaSync()` method currently only calls `GET /bookmarks/sync` and processes deletions. It does not yet call `POST /bookmarks/sync` for bulk content download. When it is implemented, add this guard:

```kotlin
// Never call POST /bookmarks/sync with empty IDs
if (updatedIds.isNotEmpty()) {
    val contentResponse = readeckApi.syncContent(SyncContentRequest(ids = updatedIds))
    // ... process streamed response
}
```

---

## Part 3: Incremental Bookmark Ingestion Strategy

### Goal

When the user adds a URL, show the bookmark in the list as quickly as possible, even before the server finishes content extraction. Provide a visual indicator that content is still loading, and allow the user to trigger content download manually.

### How the Readeck API Supports This

From the API documentation:
1. `POST /api/bookmarks` returns immediately with the bookmark object. The response includes the `bookmark-id` header. The bookmark object itself may have `state: 2` (LOADING) and empty content if extraction is still running.
2. `GET /api/bookmarks/{id}` returns the bookmark's current state. Once extraction completes, `state` changes to `0` (LOADED) and `has_article` becomes true.
3. `GET /api/bookmarks/{id}/article` returns the article HTML content.

### Proposed Three-Phase Flow

#### Phase 1: Create and Insert Metadata Immediately

After `POST /api/bookmarks` succeeds:

1. Extract the `bookmark-id` from response headers.
2. Immediately call `GET /api/bookmarks/{id}` to fetch whatever metadata the server has (title, site name, description, state, etc.).
3. Insert the bookmark into the local database **regardless of state**. Even if `state == LOADING`, insert it.
4. Return success to the UI.

**Key change:** The bookmark list currently filters by `state == LOADED`. To show LOADING bookmarks, either:
- **Option A:** Remove the state filter entirely and handle LOADING/ERROR states in the UI.
- **Option B:** Insert the bookmark with `state = LOADED` locally as an optimistic override, but track that content is missing separately (via the `article_content` table being empty).

**Recommendation:** Option B is simpler. The bookmark already has a separate `article_content` table. A bookmark with metadata in `bookmarks` but no row in `article_content` naturally represents "metadata present, content pending." No filter changes needed.

#### Phase 2: Poll for Metadata Completion

If the initial fetch returned `state: LOADING` (state == 2):

1. Schedule a polling task (coroutine or WorkManager) that calls `GET /api/bookmarks/{id}` every 2 seconds, up to 30 attempts (60 seconds max).
2. On each poll, check:
   - If `state == 0` (LOADED): Update the local bookmark metadata (title may have changed, `has_article` is now true). Move to Phase 3.
   - If `state == 1` (ERROR): Update the local bookmark with error state. Stop polling.
3. If timeout: Stop polling. The bookmark remains in the list with whatever metadata it has. The next sync will update it.

**Why poll metadata, not content?** The metadata call (`GET /bookmarks/{id}`) is lightweight. We just need to know when `has_article` flips to true so we know content is available for download.

#### Phase 3: Content Download (On-Demand or Automatic)

Once the bookmark's `has_article == true`:

1. **Automatic:** Enqueue a `LoadArticleWorker` to fetch `GET /api/bookmarks/{id}/article` and store in `article_content`. This is what the app already does during sync.
2. **On-demand (download button):** If the user taps before auto-download completes, trigger the same `LoadArticleUseCase.execute(bookmarkId)` immediately.

### UI Changes

#### List View: Download Indicator

For any bookmark in the local DB that has no corresponding row in `article_content`:

- Show a **download icon** (e.g., `Icons.Default.CloudDownload`) on the bookmark card.
- Tapping the icon triggers `LoadArticleUseCase.execute(bookmarkId)`.
- Once content is fetched, the icon disappears (the `article_content` row now exists).
- If the bookmark's server-side `state` is still LOADING (extraction not done), show a **spinner** instead of the download icon — content isn't available yet.

**Implementation approach:**

The list item entity (`BookmarkListItemEntity`) currently doesn't include content status. Add a field:

```kotlin
data class BookmarkListItemEntity(
    // ... existing fields ...
    val hasLocalContent: Boolean  // true if article_content row exists
)
```

Update the DAO query:
```sql
SELECT b.id, ...,
    EXISTS (SELECT 1 FROM article_content ac WHERE ac.bookmarkId = b.id) AS hasLocalContent
FROM bookmarks b
WHERE ...
```

#### Detail View: Content Not Yet Available

If the user opens a bookmark that has no local content:
- Show the header (title, site, description) from metadata.
- In place of the article body, show either:
  - "Content is being downloaded..." with a progress indicator (if download is in progress).
  - A "Download Article" button (if not yet started).
- After content is fetched, recompose to show the full article.

The existing `BookmarkDetailContent` already handles `articleContent == null` with `EmptyBookmarkDetailArticle()`. Enhance that to include a download action.

### Revised `createBookmark()` Implementation

```kotlin
override suspend fun createBookmark(title: String, url: String): String {
    val createBookmarkDto = CreateBookmarkDto(title = title, url = url)
    val response = readeckApi.createBookmark(createBookmarkDto)
    if (!response.isSuccessful) {
        throw Exception("Failed to create bookmark")
    }

    val bookmarkId = response.headers()[ReadeckApi.Header.BOOKMARK_ID]!!

    // Phase 1: Fetch and insert metadata immediately
    try {
        val bookmarkResponse = readeckApi.getBookmarkById(bookmarkId)
        if (bookmarkResponse.isSuccessful && bookmarkResponse.body() != null) {
            val bookmark = bookmarkResponse.body()!!.toDomain()
            insertBookmarks(listOf(bookmark))
            Timber.d("Bookmark created and inserted locally: $bookmarkId")

            // Phase 2: If not yet loaded, poll in the background
            if (bookmark.state == Bookmark.State.LOADING) {
                pollForBookmarkReady(bookmarkId)
            } else if (bookmark.hasArticle) {
                // Phase 3: Content available, enqueue download
                enqueueArticleDownload(bookmarkId)
            }
        }
    } catch (e: Exception) {
        Timber.w(e, "Failed to fetch created bookmark metadata")
    }

    return bookmarkId
}

private fun pollForBookmarkReady(bookmarkId: String) {
    // Launch in application scope (not viewModelScope) so it survives navigation
    applicationScope.launch {
        var attempts = 0
        val maxAttempts = 30
        val delayMs = 2000L

        while (attempts < maxAttempts) {
            delay(delayMs)
            attempts++

            try {
                val response = readeckApi.getBookmarkById(bookmarkId)
                if (response.isSuccessful && response.body() != null) {
                    val bookmark = response.body()!!.toDomain()
                    insertBookmarks(listOf(bookmark))  // Update local metadata

                    when (bookmark.state) {
                        Bookmark.State.LOADED -> {
                            if (bookmark.hasArticle) {
                                enqueueArticleDownload(bookmarkId)
                            }
                            return@launch  // Done
                        }
                        Bookmark.State.ERROR -> {
                            Timber.w("Bookmark extraction failed: $bookmarkId")
                            return@launch  // Done (with error)
                        }
                        else -> { /* still loading, continue polling */ }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Poll attempt $attempts failed for $bookmarkId")
            }
        }

        Timber.w("Polling timed out for bookmark $bookmarkId")
    }
}

private fun enqueueArticleDownload(bookmarkId: String) {
    val request = OneTimeWorkRequestBuilder<LoadArticleWorker>()
        .setInputData(
            Data.Builder()
                .putString(LoadArticleWorker.PARAM_BOOKMARK_ID, bookmarkId)
                .build()
        )
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .build()
    workManager.enqueue(request)
}
```

**Note on `applicationScope`:** The repository needs access to a coroutine scope that outlives any single ViewModel. Inject a `@Singleton`-scoped `CoroutineScope` via Hilt:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
```

### Comparison with Other Clients

| Approach | Eckard | ReadeckApp | Proposed (MyDeck) |
|----------|--------|------------|-------------------|
| After POST | Poll until LOADED (blocking UI) | Fire-and-forget | Insert metadata immediately |
| Bookmark visible | Only after fully loaded | After next sync | Immediately |
| Content available | Immediately (waited for it) | After sync + article worker | After background poll + download |
| Download icon | N/A | N/A | Yes — tap to fetch content |
| Timeout handling | None (infinite loop) | N/A | 60s timeout, graceful fallback |

This approach combines the reliability of Eckard's polling with the responsiveness of an optimistic UI. The user sees the bookmark immediately, content downloads in the background, and a download button provides manual override if needed.
