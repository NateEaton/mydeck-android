# Clipboard URL Pre-population for Add Bookmark Dialog

## Feature Summary
When the user adds a bookmark (via FAB+ or ACTION_SEND intent), automatically check the Android clipboard for URLs and pre-populate the add bookmark dialog with extracted URL and title.

## Current State Analysis

### Existing Infrastructure
1. **URL Extraction**: `extractUrlAndTitle()` function already exists in `Utils.kt:25-79`
   - Uses regex `(https?://[^\s]+)` to find URLs
   - Extracts title from text surrounding the URL
   - Returns `SharedText(url: String, title: String?)`
   - Handles multi-line text and complex parsing scenarios

2. **Intent Sharing**: Already pre-populates dialog when receiving ACTION_SEND intents
   - Implementation in `BookmarkListViewModel.kt:115-129`
   - Uses same `extractUrlAndTitle()` logic
   - Pre-fills dialog with extracted URL and title

3. **Clipboard Access**: App already uses `LocalClipboardManager` (Jetpack Compose)
   - Currently used in `BookmarkDetailsDialog.kt:283-308` for copying
   - No clipboard reading implemented yet

4. **Data Model**: `SharedText` model at `domain/model/SharedText.kt`
   ```kotlin
   data class SharedText(
       val url: String,
       val title: String? = null
   )
   ```

5. **Dialog State**: Managed by `CreateBookmarkUiState.Open` in BookmarkListViewModel
   - Fields: `title`, `url`, `urlError`, `isCreateEnabled`

### Entry Points for Add Bookmark
1. **FAB+ Button**: `BookmarkListScreen.kt:424-430`
   - Calls `viewModel.openCreateBookmarkDialog()`

2. **ACTION_SEND Intent**: `MainActivity.kt:72-92`
   - Extracts shared text and passes via navigation
   - Already pre-populates dialog

## Technical Design

### High-Level Approach
Reuse existing `extractUrlAndTitle()` logic for clipboard contents, mirroring the pattern already implemented for ACTION_SEND intents.

### Implementation Details

#### 1. Clipboard Reading (BookmarkListScreen.kt)

**Location**: Within `BookmarkListScreen` composable, when FAB is clicked

**Code Changes**:
```kotlin
// In BookmarkListScreen.kt, modify FAB onClick handler
val clipboardManager = LocalClipboardManager.current

floatingActionButton = {
    FloatingActionButton(
        onClick = {
            val clipboardText = clipboardManager.getText()?.text
            viewModel.openCreateBookmarkDialog(clipboardText)
        }
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(id = R.string.add_bookmark)
        )
    }
}
```

**Notes**:
- `LocalClipboardManager.getText()` returns `AnnotatedString?`
- Access `.text` property to get plain String
- No permissions required for reading clipboard
- Pass null-safe clipboard text to ViewModel

#### 2. ViewModel Update (BookmarkListViewModel.kt)

**Location**: `BookmarkListViewModel.kt:420`

**Current Signature**:
```kotlin
fun openCreateBookmarkDialog()
```

**New Signature**:
```kotlin
fun openCreateBookmarkDialog(clipboardText: String? = null)
```

**Implementation**:
```kotlin
fun openCreateBookmarkDialog(clipboardText: String? = null) {
    val sharedText = clipboardText?.extractUrlAndTitle()

    _createBookmarkUiState.value = if (sharedText != null) {
        CreateBookmarkUiState.Open(
            title = sharedText.title?.take(MAX_TITLE_LENGTH) ?: "",
            url = sharedText.url,
            urlError = null,
            isCreateEnabled = true
        )
    } else {
        CreateBookmarkUiState.Open(
            title = "",
            url = "",
            urlError = null,
            isCreateEnabled = false
        )
    }
}
```

**Notes**:
- Use existing `extractUrlAndTitle()` extension function
- Returns `SharedText?` (null if no valid URL found)
- If null, show empty dialog (current behavior)
- If valid, pre-populate with URL and truncated title
- Title truncated to `MAX_TITLE_LENGTH` constant

#### 3. Title Truncation Constant

**Location**: Add to `Utils.kt` or `BookmarkListViewModel.kt`

```kotlin
private const val MAX_TITLE_LENGTH = 500
```

**Rationale**:
- Database has no explicit constraint (String field)
- UI text fields should have reasonable limits
- 500 characters matches common title length limits
- Truncation uses `String.take()` which safely handles shorter strings

### Parsing Behavior (Already Implemented)

The `extractUrlAndTitle()` function handles all example cases:

**Example 1**: Title before URL
```
Input: "The Best Cookware Sets for Glass Stoves, Tested https://share.google/ytlVFo2TpBzM1ftgW"
Output: SharedText(
    url = "https://share.google/ytlVFo2TpBzM1ftgW",
    title = "The Best Cookware Sets for Glass Stoves, Tested"
)
```

**Example 2**: Multi-line with title before URL
```
Input: "This foliage map tells you when to see peak colors across the U.S.
https://www.washingtonpost.com/travel/tips/fall-foliage-map-2025-peak-colors/"
Output: SharedText(
    url = "https://www.washingtonpost.com/travel/tips/fall-foliage-map-2025-peak-colors/",
    title = "This foliage map tells you when to see peak colors across the U.S."
)
```

**Example 3**: URL only
```
Input: "https://a.co/d/5ZZJzay"
Output: SharedText(
    url = "https://a.co/d/5ZZJzay",
    title = null  // Dialog shows empty title field
)
```

**Edge Cases**:
- Empty clipboard → Dialog opens with empty fields
- Non-URL text → Dialog opens with empty fields
- Invalid URL → Dialog opens with empty fields
- Multiple URLs → Uses first valid URL, rest becomes title

### URL Validation
Uses existing `String.isValidUrl()` function in `Utils.kt:10-17`:
```kotlin
fun String?.isValidUrl(): Boolean {
    return try {
        URL(this).toURI()
        true
    } catch (e: Exception) {
        false
    }
}
```

## User Experience Flow

### Scenario 1: Clipboard Contains URL with Title
1. User copies: `"The Best Cookware Sets https://share.google/ytlVFo2TpBzM1ftgW"`
2. User taps FAB+
3. Dialog opens with:
   - URL field: `"https://share.google/ytlVFo2TpBzM1ftgW"`
   - Title field: `"The Best Cookware Sets"`
   - Create button: **Enabled**

### Scenario 2: Clipboard Contains URL Only
1. User copies: `"https://a.co/d/5ZZJzay"`
2. User taps FAB+
3. Dialog opens with:
   - URL field: `"https://a.co/d/5ZZJzay"`
   - Title field: **Empty** (user can optionally add title)
   - Create button: **Enabled**

### Scenario 3: Clipboard Empty or No URL
1. Clipboard is empty or contains `"grocery list: milk, eggs"`
2. User taps FAB+
3. Dialog opens with:
   - URL field: **Empty**
   - Title field: **Empty**
   - Create button: **Disabled** (requires valid URL)

### Scenario 4: Title Exceeds Max Length
1. User copies text with 800-character title before URL
2. User taps FAB+
3. Dialog opens with:
   - URL field: Extracted URL
   - Title field: First 500 characters of extracted title
   - Create button: **Enabled**

## Technical Considerations

### Permissions
- **None required**: Reading clipboard does not require permissions in Android
- Note: Android 12+ shows toast notification when app reads clipboard (system behavior, not configurable)

### Privacy
- Clipboard access only occurs when user explicitly taps FAB+ to add bookmark
- No background clipboard monitoring
- Clipboard content never sent to server or stored (only parsed locally)

### Performance
- `extractUrlAndTitle()` uses simple regex and string operations
- Negligible performance impact (< 1ms for typical clipboard content)
- Clipboard read is synchronous and immediate

### Backwards Compatibility
- Changes are additive (default parameter on existing function)
- Existing ACTION_SEND intent flow unchanged
- No breaking changes to public API

## Testing Scenarios

### Unit Tests
1. `extractUrlAndTitle()` with various input formats (already exists)
2. Title truncation at MAX_TITLE_LENGTH boundary
3. Null/empty clipboard handling

### Integration Tests
1. FAB+ click with URL in clipboard → dialog pre-populated
2. FAB+ click with empty clipboard → empty dialog
3. ACTION_SEND intent flow still works (regression test)

### Manual Testing
1. Copy each example format, tap FAB+, verify dialog state
2. Test with very long titles (> 500 chars)
3. Test with multiple URLs in clipboard
4. Test with clipboard containing only text (no URLs)
5. Test ACTION_SEND sharing from external app (regression)

## Files Modified

| File | Changes | Lines (Approx) |
|------|---------|----------------|
| `ui/list/BookmarkListScreen.kt` | Add clipboard manager, pass text to ViewModel | ~5 |
| `ui/list/BookmarkListViewModel.kt` | Add clipboardText parameter, parse and pre-populate | ~20 |
| `util/Utils.kt` (or ViewModel) | Add `MAX_TITLE_LENGTH` constant | 1 |

**Total Estimated Changes**: ~26 lines of code

## Dependencies
- **New**: None
- **Existing**:
  - `androidx.compose.ui.platform.LocalClipboardManager`
  - Existing `extractUrlAndTitle()` function
  - Existing `SharedText` data model

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Clipboard contains sensitive data | Low - user initiated action | Only parse when user explicitly adds bookmark |
| URL extraction fails | Low - fallback to empty dialog | Null-safe handling throughout |
| Very long clipboard content | Low - parsing performance | Regex is efficient; title truncated |
| Breaking ACTION_SEND flow | Medium - existing feature breaks | Use default parameter; thorough testing |

## Success Criteria
1. ✅ Clipboard with URL pre-populates dialog correctly
2. ✅ Title extracted and truncated if needed
3. ✅ Empty/invalid clipboard shows empty dialog
4. ✅ No permissions required
5. ✅ ACTION_SEND intent flow unaffected
6. ✅ No performance degradation

## Future Enhancements (Out of Scope)
- Support for `http://` URLs without scheme (add to regex)
- Clipboard history (multiple items)
- User preference to disable auto-fill
- Smart title cleanup (remove trailing punctuation, normalize whitespace)
