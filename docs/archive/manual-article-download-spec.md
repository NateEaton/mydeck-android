# Feature Spec: Manual Article Download Button for Reading View

## Problem Statement

When bookmarks are created but article content fails to download (due to timeout, network issues, or server processing delays), users have no way to manually retry the download from the reading view. They must navigate away and use "Sync Now" or pull-to-refresh on the list view, which is not intuitive.

## Proposed Solution

Add a "Download Article" button to the empty state in the reading view that allows users to manually fetch article content on demand.

## Implementation Overview

### ViewModel Changes (BookmarkDetailViewModel.kt)

**Add LoadArticleUseCase injection:**
```kotlin
@HiltViewModel
class BookmarkDetailViewModel @Inject constructor(
    private val updateBookmarkUseCase: UpdateBookmarkUseCase,
    private val bookmarkRepository: BookmarkRepository,
    private val assetLoader: AssetLoader,
    private val settingsDataStore: SettingsDataStore,
    private val loadArticleUseCase: LoadArticleUseCase,  // Add this
    savedStateHandle: SavedStateHandle
) : ViewModel()
```

**Add loading state:**
```kotlin
private val _isLoadingArticle = MutableStateFlow(false)
val isLoadingArticle: StateFlow<Boolean> = _isLoadingArticle.asStateFlow()
```

**Add refresh method:**
```kotlin
fun onRefreshArticleContent() {
    if (bookmarkId == null) return

    viewModelScope.launch {
        _isLoadingArticle.value = true
        try {
            loadArticleUseCase.execute(bookmarkId)
            Timber.d("Article content refreshed for bookmark: $bookmarkId")
        } catch (e: Exception) {
            Timber.e(e, "Error refreshing article content: ${e.message}")
        } finally {
            _isLoadingArticle.value = false
        }
    }
}
```

### UI Changes (BookmarkDetailScreen.kt)

**Update BookmarkDetailContent signature:**
```kotlin
@Composable
fun BookmarkDetailContent(
    modifier: Modifier = Modifier,
    uiState: BookmarkDetailViewModel.UiState.Success,
    onClickOpenUrl: (String) -> Unit,
    onScrollProgressChanged: (Int) -> Unit = {},
    initialReadProgress: Int = 0,
    onRefreshArticleContent: () -> Unit = {},  // Add this
    isLoadingArticle: Boolean = false          // Add this
)
```

**Pass callbacks from Scaffold:**
```kotlin
BookmarkDetailContent(
    modifier = Modifier.padding(padding),
    uiState = uiState,
    onClickOpenUrl = onClickOpenUrl,
    onScrollProgressChanged = onScrollProgressChanged,
    initialReadProgress = initialReadProgress,
    onRefreshArticleContent = { viewModel.onRefreshArticleContent() },
    isLoadingArticle = viewModel.isLoadingArticle.collectAsState().value
)
```

**Update EmptyBookmarkDetailArticle:**
```kotlin
@Composable
fun EmptyBookmarkDetailArticle(
    modifier: Modifier,
    onRefreshArticleContent: () -> Unit = {},
    isLoadingArticle: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.detail_view_no_content),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (isLoadingArticle) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Downloading article...",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Button(
                onClick = onRefreshArticleContent
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Download article"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download Article")
            }
        }
    }
}
```

## User Experience

### When Article Content is Missing

User sees:
1. Bookmark metadata (title, site name, description, image)
2. Empty content area with message: "Article content not available"
3. "Download Article" button with icon

### When User Taps Button

1. Button is replaced with:
   - CircularProgressIndicator (spinner)
   - "Downloading article..." text
2. Article downloads in background
3. When complete, content appears automatically
4. Button/spinner disappears

### States

| Article State | UI Display |
|---------------|------------|
| `articleContent == null && !isLoadingArticle` | "Download Article" button |
| `articleContent == null && isLoadingArticle` | Loading spinner + message |
| `articleContent != null` | Article content displayed |

## Use Cases

This feature helps users in these scenarios:

1. **Bookmark created offline** - User adds bookmark without network, can manually download later
2. **Polling timeout** - Background polling gives up after 60 seconds, user can retry
3. **Server processing delay** - Article extraction takes longer than expected
4. **Initial `hasArticle == false`** - Server hasn't finished processing yet
5. **Network failure during creation** - Content download failed, user wants to retry
6. **Stale content** - User wants to refresh old article content

## Design Decision: Button vs Pull-to-Refresh

**Chosen: Button**

Advantages:
- More intuitive for detail view (pull-to-refresh typically used for lists)
- Clear affordance - explicitly shows what will happen
- Works in any scroll position - no need to scroll to top
- Simpler implementation - no gesture handling complexity
- Better accessibility - easier to discover and activate

Pull-to-refresh would be less intuitive here since:
- Detail view is not a refreshable list
- Gesture might conflict with scroll behavior
- User might accidentally trigger it while reading

## Alternative Considered: Automatic Retry

Could automatically retry failed downloads, but this has downsides:
- May waste bandwidth if article is genuinely unavailable
- No user control over timing
- Could be frustrating if repeatedly failing
- Button gives user agency and clear feedback

## Testing Checklist

- [ ] Bookmark with no article content shows button
- [ ] Tapping button triggers download
- [ ] Loading spinner appears during download
- [ ] Content appears when download completes
- [ ] Error handling if download fails (button reappears)
- [ ] Multiple taps don't trigger multiple downloads
- [ ] Works after app restart / navigation
- [ ] Accessibility labels are correct

## Build Issue

Note: Initial implementation had a build failure that needs to be resolved if this feature is pursued. The ViewModelimport and dependency injection may need adjustment.

## Priority

Nice-to-have enhancement. Current workaround: use "Sync Now" from list view.
