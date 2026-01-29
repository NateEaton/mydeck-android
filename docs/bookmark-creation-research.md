# Research: How Other Readeck Clients Handle Bookmark Creation Delays

## Background

When a bookmark is created via `POST /api/bookmarks`, the Readeck server returns **HTTP 202 Accepted** with a `bookmark-id` header. The server then asynchronously extracts the page content. For long articles (10-20+ minutes reading time), this extraction can take **15-20 seconds**. During that time the bookmark exists on the server but has `state: 2` (LOADING) and `loaded: false`.

MyDeck's current approach: after creating the bookmark, it immediately fetches it once via `GET /bookmarks/{id}` and inserts it into the local database. If extraction hasn't finished, the bookmark is inserted with `state: LOADING`. Since the bookmark list filters by `state == LOADED`, the bookmark either doesn't appear at all, or appears in an incomplete/invalid state.

## Clients Researched

### 1. Eckard (Flutter/Dart — codeberg.org/gollyhatch/eckard)

**Eckard solves this problem with a polling loop.**

After `POST /api/bookmarks` returns the bookmark ID, Eckard enters a **blocking poll loop** that waits for the server to finish extraction before inserting the bookmark locally:

```dart
// new_bookmark_screen.dart, lines 98-133
API.saveNewBookmark(url, title: title, labels: labels)
    .then((String bookmarkID) async {
        Bookmark? bookmark;
        try {
            while (true) {
                bookmark = await API.getBookmark(bookmarkID);
                if (bookmark.isLoaded || bookmark.errors.isNotEmpty) {
                    break;
                }
                await Future.delayed(Duration(seconds: 1));
            }

            bookmark.synced = DateTime.now();
            db.storeBookmark(bookmark);
            Navigator.of(context).pop();
            Navigator.push(context, MaterialPageRoute(
                builder: (context) => BookmarkView(bookmark!),
            ));
        } catch (error) {
            setState(() {
                this.error = error.toString();
                savingInProgress = false;
            });
        }
    });
```

**How it works:**
1. POST to create bookmark → server returns bookmark ID
2. Show a `CircularProgressIndicator` (full-screen spinner)
3. Every 1 second, `GET /bookmarks/{bookmarkID}` to check status
4. Loop exits when `bookmark.isLoaded == true` OR `bookmark.errors.isNotEmpty`
5. Only then is the bookmark stored locally and the user navigated to view it

**UX:** The user sees a loading spinner for the entire duration (potentially 15-20 seconds). Simple but reliable — the bookmark is never in an invalid state locally.

**Limitations:**
- No timeout — if the server never finishes, the spinner runs forever
- No progress indication beyond the spinner
- User can't navigate away during the wait (though they can press back)

### 2. ReadeckApp (Kotlin/Android — github.com/jensomato/ReadeckApp)

**ReadeckApp does NOT solve this problem.** It uses a fire-and-forget approach:

```kotlin
// BookmarkRepositoryImpl.kt
override suspend fun createBookmark(title: String, url: String): String {
    val response = readeckApi.createBookmark(CreateBookmarkDto(title = title, url = url))
    if (response.isSuccessful) {
        return response.headers()[ReadeckApi.Header.BOOKMARK_ID]!!
    } else {
        throw Exception("Failed to create bookmark")
    }
}
```

After creation, the returned bookmark ID is **discarded** — no immediate fetch, no polling. The bookmark only appears locally on the next sync (pull-to-refresh or auto-sync). The list view filters by `state = Bookmark.State.LOADED`, so bookmarks still being extracted are hidden.

**UX:** User creates a bookmark, sees a "Success" toast, but the bookmark doesn't appear in the list until they manually pull-to-refresh AND the server has finished extraction.

### 3. readeck_related_app (Flutter/Dart — github.com/linkalls/readeck_related_app)

**This client also does NOT solve the problem.** It takes a slightly better approach than ReadeckApp — it immediately fetches the bookmark after creation — but doesn't wait for extraction to complete:

```dart
// readeck_api_client.dart
Future<BookmarkInfo> createBookmark(BookmarkCreate bookmarkCreate) async {
    final response = await _makeRawRequest(() => _httpClient.post(...));
    if (response.statusCode == 202) {
        final bookmarkId = response.headers['bookmark-id'];
        if (bookmarkId != null) {
            return getBookmark(bookmarkId);  // single fetch, no polling
        }
    }
}
```

After creating, the Flutter UI calls `loadBookmarks(reset: true)` to refresh the entire list. If extraction hasn't finished, the bookmark appears with `loaded: false` and incomplete metadata.

## Comparison

| Aspect | Eckard | ReadeckApp | readeck_related_app | MyDeck (current) |
|--------|--------|------------|---------------------|------------------|
| Polls for readiness | Yes (1s interval) | No | No | No |
| Shows loading state | Spinner until ready | Toast only | No indication | No indication |
| Bookmark inserted when | Fully loaded | Next sync | Immediately (may be incomplete) | Immediately (may be incomplete) |
| Handles long extraction | Yes (waits indefinitely) | No | No | No |
| Handles extraction errors | Yes (checks `errors`) | N/A | No | No |

## Recommended Solution for MyDeck

Eckard's polling approach is the only one that reliably handles the extraction delay. Adapt it for MyDeck:

### Implementation Outline

**In `BookmarkRepositoryImpl.createBookmark()`:**

```kotlin
override suspend fun createBookmark(title: String, url: String): String {
    val createBookmarkDto = CreateBookmarkDto(title = title, url = url)
    val response = readeckApi.createBookmark(createBookmarkDto)
    if (!response.isSuccessful) {
        throw Exception("Failed to create bookmark")
    }

    val bookmarkId = response.headers()[ReadeckApi.Header.BOOKMARK_ID]!!

    // Poll until the server finishes content extraction
    val maxAttempts = 60  // 60 seconds max wait
    var attempts = 0
    while (attempts < maxAttempts) {
        val bookmarkResponse = readeckApi.getBookmarkById(bookmarkId)
        if (bookmarkResponse.isSuccessful && bookmarkResponse.body() != null) {
            val bookmark = bookmarkResponse.body()!!.toDomain()

            if (bookmark.state == Bookmark.State.LOADED ||
                bookmark.state == Bookmark.State.ERROR) {
                // Extraction finished (success or failure) — insert locally
                insertBookmarks(listOf(bookmark))
                return bookmarkId
            }
        }

        delay(1000)  // wait 1 second before next poll
        attempts++
    }

    // Timed out — insert whatever we have (state = LOADING)
    // so the user at least sees it after a sync
    val finalResponse = readeckApi.getBookmarkById(bookmarkId)
    if (finalResponse.isSuccessful && finalResponse.body() != null) {
        insertBookmarks(listOf(finalResponse.body()!!.toDomain()))
    }

    return bookmarkId
}
```

**In `BookmarkListViewModel.createBookmark()`:**

The ViewModel already shows a loading state (`CreateBookmarkUiState.Loading`). Since the repository call now blocks until extraction is done (or timeout), the UI spinner naturally stays visible for the full duration. Consider adding a message like "Waiting for server to process article..." to the loading state.

### Key Design Decisions

1. **Timeout**: 60 seconds is a reasonable upper bound. Eckard has no timeout, which could hang forever.
2. **Poll interval**: 1 second, matching Eckard. Could use exponential backoff (1s, 2s, 4s...) but the simplicity of fixed 1s is fine for this use case.
3. **On timeout**: Insert the bookmark in whatever state it's in. The next sync will update it when extraction completes.
4. **Error state**: If the server returns `state: ERROR`, insert it anyway so the user can see the error and delete/retry.
5. **List filtering**: The existing `state == LOADED` filter means LOADING bookmarks won't appear in the list. This is correct — if the poll times out, the bookmark will appear after the next sync once extraction completes.
