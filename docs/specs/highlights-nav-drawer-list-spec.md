# Highlights Nav Drawer & List View — Technical Specification

**Status:** Ready for implementation
**Date:** 2026-04-04
**Supersedes:** `_notes/highlights-nav-drawer-list-spec.md`

---

## 1. Overview

Add a "Highlights" item to the navigation drawer that opens a full-screen list of all highlights across all bookmarks, fetched from the `GET /bookmarks/annotations` API endpoint. Tapping a highlight card navigates to the bookmark's reading view and scrolls to the specific annotation.

This spec also covers two prerequisite bug fixes that must land in the same PR:
- Relax the `ARTICLE`-only gate on annotation UI so Video/Photo bookmarks can also use it.
- Fix annotation CRUD for video bookmarks (post-mutation HTML refresh currently silently no-ops).

---

## 2. Design Decision: Full-Screen Destination

The Highlights screen is a **navigation destination** (like Settings or About), not a `DrawerPreset`. Tapping the drawer item closes the drawer and navigates to `HighlightsRoute`. Pressing back from Highlights returns to the previous screen.

This is the correct pattern because: (a) the user is browsing a cross-bookmark index and will navigate into individual bookmarks, (b) a bottom-sheet would leave the user stranded when pressing back from a reading view for an archived bookmark they never navigated to, and (c) the Readeck web UI gives Highlights its own dedicated page.

---

## 3. Prerequisites / Bug Fixes

These bugs are uncovered by the Highlights feature (a user can navigate to a Photo/Video bookmark from the Highlights list and expect annotation UI to work) and must be fixed as part of this PR.

### 3.1 ARTICLE-only gate on annotation UI

**Files:** `BookmarkDetailMenu.kt` and `BookmarkDetailScreen.kt`

Three hard-coded `type == ARTICLE` guards restrict annotation UI to articles only. The underlying `HighlightActionWebView` already works for all content types; Readeck's backend supports annotations on photos and videos.

#### `BookmarkDetailMenu.kt` (line 66–67)

Change:
```kotlin
if (uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.ARTICLE &&
    contentMode == ContentMode.READER) {
```
To:
```kotlin
if ((uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.ARTICLE ||
     uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.VIDEO ||
     uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.PHOTO) &&
    contentMode == ContentMode.READER) {
```

#### `BookmarkDetailScreen.kt` — `AnnotationsBottomSheet` guard (line ~692)

Same change: replace the `type == ARTICLE` check with the three-way OR.

#### `BookmarkDetailScreen.kt` — `AnnotationEditSheet` guard (line ~704)

Same change.

### 3.2 Video bookmark annotation HTML refresh

**File:** `BookmarkDetailViewModel.kt`

When a user creates, updates, or deletes an annotation on a video bookmark, `refreshAnnotationHtml()` is called. For video bookmarks:
- `hasResources` is `false` (no offline content package exists for videos)
- The code falls through to `loadArticleUseCase.refreshHtmlForAnnotations(bookmarkId)`
- That method checks `bookmark.hasArticle`; video bookmarks do not have articles, so it returns `null`
- With `enrichedHtml == null` and `hasResources == false`, the method exits silently with no DOM update and no user-visible error

The annotation CRUD API call itself succeeded, but the WebView DOM is not updated, and the cached annotation entity is not refreshed. The annotation will not visually appear until the user closes and re-opens the bookmark.

**Fix:** At the start of `refreshAnnotationHtml`, short-circuit for video bookmarks by re-fetching the per-bookmark annotation list and refreshing the cached entity, then returning without attempting an HTML refresh (since video embeds do not have injectable `<rd-annotation>` DOM elements):

```kotlin
private suspend fun refreshAnnotationHtml(bookmarkId: String) {
    // Video bookmarks have no extractable article HTML. Re-fetch the annotation
    // list from the API to keep the local cache consistent, then return.
    val bookmarkType = (_uiState.value as? UiState.Success)?.bookmark?.type
    if (bookmarkType == Bookmark.Type.VIDEO) {
        try {
            val response = readeckApi.getAnnotations(bookmarkId)
            if (response.isSuccessful) {
                val annotations = response.body() ?: emptyList()
                cacheAnnotationSnapshot(bookmarkId)
                // Update the in-memory annotations state so the sheet reflects the change
                _annotationsState.value = AnnotationsState(
                    annotations = annotations.map { it.toDomain() },
                    isLoading = false
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to refresh video annotation cache for $bookmarkId")
        }
        return
    }

    // Existing logic for articles and photos below...
    val hasResources = cachedHasResources ?: contentPackageManager.hasResources(bookmarkId) ?: false
    // ... remainder unchanged
}
```

> **Note:** `AnnotationDto.toDomain()` should already exist (used elsewhere in the ViewModel). If the extension doesn't exist as a standalone, inline the mapping. The `_annotationsState` field type and update pattern should match however it is already updated in `loadAnnotations()`.

---

## 4. API Layer

### 4.1 New endpoint in `ReadeckApi.kt`

```kotlin
@GET("bookmarks/annotations")
suspend fun getAnnotationSummaries(
    @Query("limit") limit: Int,
    @Query("offset") offset: Int,
): Response<List<AnnotationSummaryDto>>
```

### 4.2 New DTO: `AnnotationSummaryDto.kt`

**File:** `app/src/main/java/com/mydeck/app/io/rest/model/AnnotationSummaryDto.kt`

The OpenAPI spec for this endpoint is incomplete. Actual API response (confirmed via live Readeck API documentation UI) includes `color` and `note` fields not shown in the schema:

```kotlin
package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnnotationSummaryDto(
    val id: String,
    val href: String,
    val text: String,
    val color: String,
    val note: String,
    val created: String,                   // ISO 8601 date-time string
    val bookmark_id: String,
    val bookmark_href: String,
    val bookmark_url: String,
    val bookmark_title: String,
    val bookmark_site_name: String,
)
```

Use `ignoreUnknownKeys = true` on the Retrofit converter (already set app-wide) to be safe against additional undocumented fields.

---

## 5. Domain Models

### 5.1 `HighlightSummary.kt`

**File:** `app/src/main/java/com/mydeck/app/domain/model/HighlightSummary.kt`

```kotlin
package com.mydeck.app.domain.model

import kotlinx.datetime.Instant

data class HighlightSummary(
    val id: String,
    val text: String,
    val color: String,        // "yellow", "red", "blue", "green", or "none"
    val note: String,         // empty string if no note
    val created: Instant,
    val bookmarkId: String,
    val bookmarkTitle: String,
    val bookmarkSiteName: String,
)
```

Mapping from `AnnotationSummaryDto`:

```kotlin
fun AnnotationSummaryDto.toDomain(): HighlightSummary = HighlightSummary(
    id = id,
    text = text,
    color = color,
    note = note,
    created = Instant.parse(created),
    bookmarkId = bookmark_id,
    bookmarkTitle = bookmark_title,
    bookmarkSiteName = bookmark_site_name,
)
```

### 5.2 `BookmarkHighlightGroup.kt`

**File:** `app/src/main/java/com/mydeck/app/domain/model/BookmarkHighlightGroup.kt`

```kotlin
package com.mydeck.app.domain.model

data class BookmarkHighlightGroup(
    val bookmarkId: String,
    val bookmarkTitle: String,
    val bookmarkSiteName: String,
    val highlights: List<HighlightSummary>,  // ordered by created descending
)
```

No Room entity is needed. The list is network-only, fetched fresh on each screen entry. The expected data size (tens to low hundreds of items) makes full-page-load-before-display acceptable for v1.

---

## 6. Repository

**File:** `app/src/main/java/com/mydeck/app/domain/HighlightsRepository.kt`

```kotlin
package com.mydeck.app.domain

import com.mydeck.app.domain.model.HighlightSummary
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.toDomain
import javax.inject.Inject
import javax.inject.Singleton

interface HighlightsRepository {
    suspend fun getAllHighlights(): Result<List<HighlightSummary>>
}

@Singleton
class HighlightsRepositoryImpl @Inject constructor(
    private val readeckApi: ReadeckApi,
) : HighlightsRepository {

    override suspend fun getAllHighlights(): Result<List<HighlightSummary>> {
        return try {
            val pageSize = 50
            val all = mutableListOf<HighlightSummary>()
            var offset = 0
            while (true) {
                val response = readeckApi.getAnnotationSummaries(
                    limit = pageSize,
                    offset = offset
                )
                if (!response.isSuccessful) {
                    return Result.failure(
                        IllegalStateException("HTTP ${response.code()}")
                    )
                }
                val page = response.body() ?: break
                all.addAll(page.map { it.toDomain() })
                if (page.size < pageSize) break
                offset += pageSize
            }
            Result.success(all)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

Bind `HighlightsRepositoryImpl` to `HighlightsRepository` in the Hilt module that handles other repository bindings (follow the existing pattern for e.g. `BookmarkRepository`).

---

## 7. Navigation Route

**File:** `app/src/main/java/com/mydeck/app/ui/navigation/Routes.kt`

Add:

```kotlin
@Serializable
object HighlightsRoute
```

Also add `annotationId` to `BookmarkDetailRoute` to support scroll-to-annotation navigation from the Highlights list:

```kotlin
@Serializable
data class BookmarkDetailRoute(
    val bookmarkId: String,
    val showOriginal: Boolean = false,
    val annotationId: String? = null,   // NEW: scroll to annotation after load
)
```

---

## 8. ViewModel

**File:** `app/src/main/java/com/mydeck/app/ui/highlights/HighlightsViewModel.kt`

```kotlin
package com.mydeck.app.ui.highlights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydeck.app.domain.HighlightsRepository
import com.mydeck.app.domain.model.BookmarkHighlightGroup
import com.mydeck.app.domain.model.HighlightSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HighlightsViewModel @Inject constructor(
    private val highlightsRepository: HighlightsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HighlightsUiState>(HighlightsUiState.Loading)
    val uiState: StateFlow<HighlightsUiState> = _uiState.asStateFlow()

    init {
        loadHighlights()
    }

    fun loadHighlights() {
        viewModelScope.launch {
            _uiState.value = HighlightsUiState.Loading
            highlightsRepository.getAllHighlights()
                .onSuccess { highlights ->
                    if (highlights.isEmpty()) {
                        _uiState.value = HighlightsUiState.Empty
                    } else {
                        _uiState.value = HighlightsUiState.Success(group(highlights))
                    }
                }
                .onFailure { error ->
                    _uiState.value = HighlightsUiState.Error(
                        error.message ?: "Failed to load highlights"
                    )
                }
        }
    }

    /**
     * Groups flat highlights by bookmarkId. Groups are sorted by most-recent
     * highlight descending. Within each group, highlights are newest-first.
     */
    private fun group(highlights: List<HighlightSummary>): List<BookmarkHighlightGroup> {
        return highlights
            .sortedByDescending { it.created }
            .groupBy { it.bookmarkId }
            .map { (bookmarkId, items) ->
                BookmarkHighlightGroup(
                    bookmarkId = bookmarkId,
                    bookmarkTitle = items.first().bookmarkTitle,
                    bookmarkSiteName = items.first().bookmarkSiteName,
                    highlights = items,  // already sorted descending from outer sort
                )
            }
    }
}

sealed interface HighlightsUiState {
    data object Loading : HighlightsUiState
    data object Empty : HighlightsUiState
    data class Success(val groups: List<BookmarkHighlightGroup>) : HighlightsUiState
    data class Error(val message: String) : HighlightsUiState
}
```

---

## 9. UI: HighlightsScreen

**File:** `app/src/main/java/com/mydeck/app/ui/highlights/HighlightsScreen.kt`

### 9.1 Entry point composable

```kotlin
@Composable
fun HighlightsScreen(
    navController: NavHostController,
    viewModel: HighlightsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    HighlightsContent(
        uiState = uiState,
        onNavigateBack = { navController.popBackStack() },
        onNavigateToBookmark = { bookmarkId, annotationId ->
            navController.navigate(BookmarkDetailRoute(bookmarkId, annotationId = annotationId))
        },
        onRetry = { viewModel.loadHighlights() }
    )
}
```

### 9.2 Layout

A `Scaffold` with:
- **Top app bar:** Title `stringResource(R.string.highlights_screen_title)`, back-navigation icon.
- **Content:** State-driven:
  - `Loading` → `CircularProgressIndicator` centred
  - `Empty` → centred `Text(stringResource(R.string.highlights_empty))`
  - `Error` → centred error message + `Button(onClick = onRetry) { Text(stringResource(R.string.highlights_retry)) }`
  - `Success` → `LazyColumn` of grouped items (see 9.3)

### 9.3 LazyColumn structure

Each `BookmarkHighlightGroup` is rendered as:

```
[HighlightCard for highlights[0]]
[HighlightCard for highlights[1]]
...
[BookmarkTitleLine]
[Spacer 8dp]
```

Use `items(groups)` with an inner loop (or nested `items` calls) rather than flattening, to keep the group relationship clear.

### 9.4 HighlightCard

```kotlin
@Composable
private fun HighlightCard(
    highlight: HighlightSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = annotationColor(highlight.color)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = highlight.created.toLocalDateString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = highlight.text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (highlight.note.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = highlight.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
```

**Annotation color mapping** (use `MaterialTheme.colorScheme` alpha-blended surfaces for theme-awareness):

```kotlin
@Composable
private fun annotationColor(color: String): Color {
    val isDark = isSystemInDarkTheme()
    return when (color) {
        "yellow" -> if (isDark) Color(0xFFFFEB3B).copy(alpha = 0.20f) else Color(0xFFFFEB3B).copy(alpha = 0.30f)
        "red"    -> if (isDark) Color(0xFFEF5350).copy(alpha = 0.20f) else Color(0xFFEF5350).copy(alpha = 0.18f)
        "blue"   -> if (isDark) Color(0xFF42A5F5).copy(alpha = 0.20f) else Color(0xFF42A5F5).copy(alpha = 0.18f)
        "green"  -> if (isDark) Color(0xFF66BB6A).copy(alpha = 0.20f) else Color(0xFF66BB6A).copy(alpha = 0.18f)
        else     -> MaterialTheme.colorScheme.surfaceVariant
    }
}
```

Blend the result with the `Card`'s `containerColor`. The alpha values should be tuned visually; the above are starting points.

### 9.5 BookmarkTitleLine

```kotlin
@Composable
private fun BookmarkTitleLine(
    group: BookmarkHighlightGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = buildString {
        if (group.bookmarkSiteName.isNotBlank()) {
            append(group.bookmarkSiteName)
            append(" — ")
        }
        append(group.bookmarkTitle)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
```

Tapping the title line navigates to the bookmark **without** an annotation scroll target (opens at top or last reading position). Pass `annotationId = null` to `BookmarkDetailRoute`.

---

## 10. Drawer Integration

### 10.1 `AppDrawerContent.kt`

Add `onClickHighlights: () -> Unit` parameter to both the outer `AppDrawerContent` composable and the inner private composable (mirroring the existing pattern for `onClickLabels`). Insert the item **between** the Labels item and the divider before Settings:

```kotlin
// After Labels NavigationDrawerItem and before the HorizontalDivider before Settings:
NavigationDrawerItem(
    label = {
        Text(
            style = MaterialTheme.typography.titleMedium,
            text = stringResource(R.string.highlights_drawer_item)
        )
    },
    icon = { Icon(Icons.Outlined.EditNote, contentDescription = null) },
    selected = false,
    colors = prominentItemColors,
    onClick = onClickHighlights,
)
```

The `selected = false` is intentional: Highlights is a navigation destination, not a selectable bookmark-list filter preset. The divider between Labels and Settings stays in place (now appearing after Highlights).

### 10.2 `AppNavigationRailContent.kt`

Add `onClickHighlights: () -> Unit` parameter. Add a `NavigationRailItem` for Highlights after the Labels item:

```kotlin
NavigationRailItem(
    selected = false,
    onClick = onClickHighlights,
    icon = { Icon(Icons.Outlined.EditNote, contentDescription = null) },
)
```

### 10.3 `AppShell.kt`

In all three NavHost variants (`CompactAppShell`, `MediumAppShell`, `ExpandedAppShell`), add the `composable<HighlightsRoute>` destination and wire the drawer callback:

```kotlin
composable<HighlightsRoute> {
    HighlightsScreen(navController = navController)
}
```

In the `AppDrawerContent` and `AppNavigationRailContent` call sites, add:

```kotlin
onClickHighlights = {
    navController.navigate(HighlightsRoute)
    scope.launch { drawerState.close() }
}
```

---

## 11. Scroll-to-Annotation on Entry

When the user taps a highlight card and `BookmarkDetailRoute` is created with a non-null `annotationId`, the reading view must scroll to that annotation after content loads.

**In `BookmarkDetailScreen.kt`**, read the route argument at the composable entry point (alongside the existing `bookmarkId` and `showOriginal` reads) and trigger the scroll:

```kotlin
// At the top of BookmarkDetailScreen composable, after toRoute():
val route = navBackStackEntry.toRoute<BookmarkDetailRoute>()
val initialAnnotationId = route.annotationId

// Trigger annotation scroll once, after the screen enters composition:
LaunchedEffect(initialAnnotationId) {
    val id = initialAnnotationId ?: return@LaunchedEffect
    viewModel.scrollToAnnotation(id)
}
```

The existing `pendingAnnotationScrollId` / `LaunchedEffect` machinery in `BookmarkDetailArticleContent` handles the rest: it fires when both `pendingAnnotationScrollId` is set and `readerWebView` is non-null, so it correctly waits for the content to finish loading before scrolling.

No changes to `BookmarkDetailViewModel.scrollToAnnotation()` are needed.

---

## 12. String Resources

Add to `values/strings.xml` and all 9 language-variant files (with English as placeholder text):

```xml
<string name="highlights_drawer_item">Highlights</string>
<string name="highlights_screen_title">Highlights</string>
<string name="highlights_empty">No highlights yet. Highlight text in articles to see them here.</string>
<string name="highlights_load_error">Failed to load highlights</string>
<string name="highlights_retry">Retry</string>
```

---

## 13. Files Created or Modified

| File | Change |
|------|--------|
| `io/rest/ReadeckApi.kt` | Add `getAnnotationSummaries()` endpoint |
| `io/rest/model/AnnotationSummaryDto.kt` | **New** — DTO for global annotations list |
| `domain/model/HighlightSummary.kt` | **New** — domain model |
| `domain/model/BookmarkHighlightGroup.kt` | **New** — grouped UI model |
| `domain/HighlightsRepository.kt` | **New** — interface + implementation |
| `ui/navigation/Routes.kt` | Add `HighlightsRoute`; add `annotationId` to `BookmarkDetailRoute` |
| `ui/highlights/HighlightsViewModel.kt` | **New** |
| `ui/highlights/HighlightsScreen.kt` | **New** |
| `ui/shell/AppDrawerContent.kt` | Add `onClickHighlights` param + drawer item |
| `ui/shell/AppNavigationRailContent.kt` | Add `onClickHighlights` param + rail item |
| `ui/shell/AppShell.kt` | Register `HighlightsRoute`; wire `onClickHighlights` in all three shell variants |
| `ui/detail/BookmarkDetailScreen.kt` | Read `annotationId` from route; call `viewModel.scrollToAnnotation()` |
| `ui/detail/components/BookmarkDetailMenu.kt` | Relax `ARTICLE` gate to include `VIDEO` and `PHOTO` |
| `ui/detail/BookmarkDetailViewModel.kt` | Fix `refreshAnnotationHtml()` for video bookmarks |
| `res/values/strings.xml` + 9 language files | Add 5 new strings |
| Hilt module (existing) | Bind `HighlightsRepositoryImpl` |

---

## 14. Implementation Plan

Execute steps in order. Each step builds on the last and leaves the project in a buildable state.

### Step 1 — Bug fix: ARTICLE type gates

Edit `BookmarkDetailMenu.kt` and `BookmarkDetailScreen.kt` to relax the three `type == ARTICLE` checks to include `VIDEO` and `PHOTO`. Build and verify annotations UI appears on a video bookmark in reader mode.

### Step 2 — Bug fix: video annotation refresh

Edit `refreshAnnotationHtml()` in `BookmarkDetailViewModel.kt` with the short-circuit described in section 3.2. Verify by creating a highlight on a video bookmark — the annotation list should update without an error.

### Step 3 — Route changes

In `Routes.kt`: add `HighlightsRoute`; add `annotationId: String? = null` to `BookmarkDetailRoute`. Build — existing code that constructs `BookmarkDetailRoute` without `annotationId` still compiles because the new parameter has a default value.

### Step 4 — Data layer

Create `AnnotationSummaryDto.kt`. Add `getAnnotationSummaries()` to `ReadeckApi.kt`. Create `HighlightSummary.kt` and `BookmarkHighlightGroup.kt`. Create `HighlightsRepository.kt`. Bind the implementation in the appropriate Hilt module.

**Verify:** Project compiles. Write a trivial unit test for the `group()` function in `HighlightsViewModel` if desired.

### Step 5 — HighlightsViewModel

Create `HighlightsViewModel.kt`. No UI yet.

**Verify:** Project compiles.

### Step 6 — HighlightsScreen

Create `HighlightsScreen.kt` with the full UI from section 9. Run the app, navigate to `HighlightsRoute` manually (or via deep link) to verify loading/empty/error/success states render correctly.

### Step 7 — Drawer integration

Add `onClickHighlights` to `AppDrawerContent.kt` and `AppNavigationRailContent.kt`. Register `composable<HighlightsRoute>` and wire the callback in all three `AppShell` variants.

**Verify:** Tap "Highlights" in the drawer — screen opens. Back button returns to previous screen.

### Step 8 — Scroll-to-annotation on entry

Add the `LaunchedEffect(initialAnnotationId)` block to `BookmarkDetailScreen`. Verify end-to-end: tap a highlight card in `HighlightsScreen` → reading view opens → scrolls to the highlighted annotation.

### Step 9 — String resources

Add the 5 new strings to all 10 language files.

### Step 10 — Build validation

```
./gradlew :app:assembleDebugAll
./gradlew :app:testDebugUnitTestAll
./gradlew :app:lintDebugAll
```

---

## 15. Out of Scope

- Editing annotation notes (note field is displayed read-only; full note editing is a separate spec).
- Adding notes during highlight creation (POST endpoint `note` field support not yet confirmed).
- Offline caching of the highlights list (network-only in v1).
- Paging / infinite scroll (full fetch before display is acceptable at expected data sizes).
- Deleting highlights from the Highlights list (tap a card → reading view → use existing annotation editor).
- Collections (this establishes the secondary-list-from-drawer navigation pattern that Collections will reuse).

---

## 16. Model Complexity Assessment

This is an **Opus-class task** (high-capability model required). The implementation spans 15+ files across data, domain, and UI layers. The Highlights list itself is straightforward, but it is entangled with three non-trivial concerns:

1. **Typed navigation with a new optional route parameter** — adding `annotationId` to `BookmarkDetailRoute` requires verifying all existing construction sites compile and that the `toRoute()` deserialization is correct.
2. **Cross-screen scroll coordination** — the scroll-to-annotation mechanism depends on a chain of three async steps (route read → ViewModel state → WebView ready → scroll) that must be wired correctly without introducing duplicate scrolls or races.
3. **Video annotation fix** — understanding the existing `refreshAnnotationHtml` call graph (`hasResources` branch, two separate use-case paths, the `AnnotationRefreshEvent` channel) is required to make a correct, minimal change.

A mid-tier model can likely handle Steps 1–2 (bug fixes) and Steps 3–7 (data + drawer wiring) independently. Steps 8 and the video annotation fix require deeper reasoning about async state and call-graph understanding that benefits from Opus-level capability.
