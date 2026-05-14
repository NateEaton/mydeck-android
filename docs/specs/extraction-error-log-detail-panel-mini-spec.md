# Extraction Error Box and Log Viewer on Bookmark Details

## Goal

When Readeck reports extraction errors for a bookmark, MyDeck should surface those errors in the Bookmark Details full-screen panel and allow the user to open the server-provided extraction log, matching the Readeck web Detail panel behavior shown in the reference screenshot.

This is a UI plus API slice. The database and domain model already preserve the relevant server metadata:

- `BookmarkDto.errors`
- `BookmarkDto.resources.log`
- `Bookmark.errors`
- `Bookmark.log`
- `BookmarkEntity.errors`
- `BookmarkEntity.hasServerErrors`
- embedded `log_src`

Do not add a Room migration unless implementation chooses to cache fetched log text locally. The recommended implementation fetches log text on demand and does not persist it.

## API Contract

Readeck bookmark detail responses use `GET /api/bookmarks/{id}` and return `bookmarkInfo`. For failed extraction cases, the response can contain:

- `state = 1`, mapped today to `Bookmark.State.ERROR`
- `errors: List<String>`
- `resources.log.src`, usually a server-relative API path for the extraction log

MyDeck already stores the relative log path as `bookmark.log.src`. Add a log-fetching API that can request this resource directly.

Recommended Retrofit shape:

```kotlin
@GET
suspend fun getBookmarkLog(@Url url: String): Response<ResponseBody>
```

Callers should pass the exact `bookmark.log.src` from the server when it is non-blank. Retrofit will resolve relative paths against the configured API base URL. If a server ever returns an absolute URL, Retrofit can fetch it as-is.

Expected response body is plain text. Treat non-2xx responses as displayable failures, not fatal detail-screen errors.

## UX

Show an error section near the top of `BookmarkDetailsDialog`, before the thumbnail/title metadata, when either condition is true:

- `bookmark.errors.isNotEmpty()`
- the bookmark state is `ERROR`

Add the minimal fields needed to the UI bookmark model:

- `errors: List<String>`
- `hasExtractionError: Boolean`
- `logSrc: String`

Recommended visual treatment:

- `OutlinedCard` or `Surface` with an error-colored border/container.
- Leading error icon.
- Title: `Errors occurred during extraction.`
- If errors exist, show them as bullet rows. If no specific error text exists but state is `ERROR`, show `Extraction failed.`
- Body helper text: `Depending on the error, Readeck's browser extension may provide a workaround.`
- `Learn more` button opens Readeck help in the browser. Use the same help URL Readeck uses if quickly discoverable; otherwise use `https://readeck.org/en/docs/browser-extension` or omit this button in the first implementation.
- `View log` text button appears only when `logSrc` is non-blank.

Tapping `View log` opens a modal dialog:

- Title: `Extraction log`
- Loading state while fetching.
- Error state with retry and close.
- Success state shows selectable, scrollable monospaced text.
- Include Copy and Share actions if straightforward; Copy is sufficient for v1.

Do not block rendering the details panel while the log fetch runs.

## Data Flow

1. `BookmarkRepository.observeBookmark(id)` already provides persisted bookmark metadata.
2. `BookmarkDetailViewModel` maps domain bookmark to UI bookmark and includes `errors`, `state`/`hasExtractionError`, and `logSrc`.
3. Add a ViewModel state holder for the log dialog, for example:

```kotlin
data class ExtractionLogState(
    val visible: Boolean = false,
    val isLoading: Boolean = false,
    val text: String? = null,
    val errorMessage: String? = null
)
```

4. `onViewExtractionLog()` sets visible/loading and calls a repository/use-case method.
5. Repository fetches `bookmark.log.src` through `ReadeckApi.getBookmarkLog(url)`.
6. ViewModel updates the dialog state without mutating the bookmark.

Preferred layering:

- Add `suspend fun fetchExtractionLog(bookmarkId: String): ExtractionLogResult` to `BookmarkRepository`.
- Repository reads the current bookmark from Room to get `log.src`; if blank, return `Unavailable`.
- Repository performs the network call and returns `Success(text)`, `HttpError(code)`, `NetworkError`, or `Unavailable`.

This keeps token/base URL concerns inside the existing network stack and avoids putting raw Retrofit calls deeper into UI code.

## Files Likely Touched

- `app/src/main/java/com/mydeck/app/io/rest/ReadeckApi.kt`
- `app/src/main/java/com/mydeck/app/domain/BookmarkRepository.kt`
- `app/src/main/java/com/mydeck/app/domain/BookmarkRepositoryImpl.kt`
- `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailViewModel.kt`
- `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailsDialog.kt`
- `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailScreen.kt`
- `app/src/main/res/values*/strings.xml`
- `app/src/main/assets/guide/en/reading.md`
- unit tests under `app/src/test/java/com/mydeck/app/domain/` and `app/src/test/java/com/mydeck/app/ui/detail/`

## String Resources

Add English placeholders to every localized `strings.xml`, per `CLAUDE.md`.

Suggested keys:

- `extraction_error_title`
- `extraction_error_fallback`
- `extraction_error_help`
- `extraction_error_learn_more`
- `extraction_error_view_log`
- `extraction_log_title`
- `extraction_log_loading`
- `extraction_log_unavailable`
- `extraction_log_load_failed`
- `extraction_log_copy`
- `extraction_log_retry`

## User Guide

Update `app/src/main/assets/guide/en/reading.md` in the Bookmark Details section. Mention that bookmarks with extraction errors show an error box and, when available, a View log action for diagnostics.

## Tests

Repository tests:

- `fetchExtractionLog` returns `Unavailable` when no log resource exists.
- It returns `Success` for 2xx text bodies.
- It returns an HTTP failure for non-2xx responses.
- It returns a network failure on thrown `IOException`.

ViewModel tests:

- UI bookmark exposes extraction errors and log availability when domain bookmark contains `errors` and `log.src`.
- `onViewExtractionLog` transitions through loading to success.
- Failed log fetch leaves the details screen intact and shows dialog error state.

Compose/UI preview:

- Add or adjust a preview with errors present and a log link.
- Ensure the card works on narrow mobile widths and does not push title text out of bounds.

Manual verification:

- Open a bookmark whose server `state` is error and has `errors`.
- Confirm the error box appears above thumbnail/title.
- Tap View log, verify the modal loads text.
- Toggle dark/light themes and verify error colors remain readable.

## Verification

Run the project default tasks serially:

```sh
./gradlew :app:assembleDebugAll
./gradlew :app:testDebugUnitTestAll
./gradlew :app:lintDebugAll
```

`lintDebugAll` is required because this touches UI, resources, and guide-visible strings.

## Branch Impact

This feature will overlap with files already changed on `feature/highlights-nav-drawer`, especially:

- `ReadeckApi.kt`
- `BookmarkRepositoryImpl.kt`
- `BookmarkDetailViewModel.kt`
- `BookmarkDetailScreen.kt`
- `BookmarkDetailsDialog.kt`
- `strings.xml` locale files
- `app/src/main/assets/guide/en/reading.md`
- detail/repository tests

Implement on top of the current branch and avoid rebasing or reverting unrelated highlight/navigation changes.

## Recommended Model

Use GPT-5.5 high. The work is not huge, but it crosses API, repository, ViewModel state, Compose UI, localization, docs, and tests in a branch that already touched overlapping modules. Medium could implement it, but high is the better risk/reward point. Very high is unnecessary unless the branch has unresolved merge conflicts or the API behavior turns out to differ from the checked-in OpenAPI dump.
