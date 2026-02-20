# UX Improvements: Label Autocomplete & Long-Press Context Actions

**Date:** 2026-02-19
**Branch:** `claude/bookmark-features-design-C1m9G`
**Status:** Draft

---

## Overview

This document covers two complementary UX improvements to the bookmark list and detail screens:

1. **Label Autocomplete (Pre-fill):** Type-ahead suggestions when entering labels in the "Add Bookmark" sheet and the "Bookmark Details" dialog.
2. **Long-Press Context Actions:** Differentiated context menus triggered by long-pressing the image area or non-image body of a `BookmarkGridCard` or `BookmarkCompactCard`.

Both changes are self-contained and can be implemented independently, though they touch overlapping files.

---

## Part 1: Label Autocomplete

### Current State

Label entry currently exists in two places:

- **`AddBookmarkSheet.kt`** — `CreateBookmarkLabelsSection()` composable (lines ~192–260): a `TextField` + `FlowRow` of chips. Accepts `newLabelInput` and `onNewLabelChange` as parameters. When the user presses enter or the add button, the label is committed.
- **`BookmarkDetailsDialog.kt`** — analogous label input section in the details sheet.

Neither location offers suggestions. The full set of existing labels with counts is available in `BookmarkListViewModel.labelsWithCounts: StateFlow<Map<String, Int>>`.

### Design

The autocomplete pattern uses a floating suggestion list anchored below the label `TextField`. When the user types:
1. Filter `existingLabels` to those that contain the input text (case-insensitive).
2. Exclude labels already added to the current bookmark.
3. Show up to 5 results.
4. Tapping a suggestion commits it immediately (same as pressing the add button).

**Implementation choice:** Use a `Box` with an `AnimatedVisibility` dropdown list positioned below the `TextField` rather than `ExposedDropdownMenuBox`. `ExposedDropdownMenuBox` is designed for a fixed set of options — it does not naturally support filtering as the user types, and it captures focus in a way that conflicts with the existing "press enter to add" behaviour. A manually positioned dropdown list gives full control.

### Data Flow

The suggestion list (`existingLabels`) must reach both label entry sites:

- **`AddBookmarkSheet`:** `BookmarkListViewModel.labelsWithCounts` is already accessible at the call site in `BookmarkListScreen.kt`. Pass `existingLabels = labelsWithCounts.keys.toList()` into `AddBookmarkSheet`.
- **`BookmarkDetailsDialog`:** Called from `BookmarkDetailScreen.kt`, which currently does not have access to label data. Two options:
  - **Option A (preferred):** Pass `existingLabels` down from `BookmarkListScreen` through the navigation graph/parent composable. This avoids coupling `BookmarkDetailViewModel` to label data.
  - **Option B:** Add `labelsWithCounts: StateFlow<Map<String, Int>>` to `BookmarkDetailViewModel`, backed by `BookmarkRepository.observeAllLabelsWithCounts()`.

  Option A is simpler if the detail screen is always reachable from the list screen. If the detail screen can be launched independently (e.g. from a deeplink), Option B is safer. **Implement Option B** for robustness.

### Shared Composable: `LabelAutocompleteTextField`

Extract the label input portion of `CreateBookmarkLabelsSection` into a new reusable composable:

```kotlin
// ui/components/LabelAutocompleteTextField.kt

@Composable
fun LabelAutocompleteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onLabelSelected: (String) -> Unit,  // called for both typed + suggested label
    existingLabels: List<String>,        // all labels in the user's library
    currentLabels: List<String>,         // labels already on this bookmark (excluded from suggestions)
    modifier: Modifier = Modifier,
)
```

**Internal logic:**

```kotlin
val suggestions = remember(value, existingLabels, currentLabels) {
    if (value.isBlank()) emptyList()
    else existingLabels
        .filter { it.contains(value.trim(), ignoreCase = true) && !currentLabels.contains(it) }
        .take(5)
}
```

**Layout:**

```
┌─────────────────────────────────────┐
│  [TextField: "pyt"                ] │
└─────────────────────────────────────┘
  ┌───────────────────────────────────┐
  │  python                           │  ← SuggestionItem (tappable)
  │  python-beginner                  │
  │  pytorch                          │
  └───────────────────────────────────┘
```

Use a `Card` with `elevation` wrapping a `Column` of `DropdownMenuItem`-style rows. Use `AnimatedVisibility(visible = suggestions.isNotEmpty())` to animate the list in/out.

Each suggestion row shows the label text. Tapping calls `onLabelSelected(label)` which should clear the input and add the label to the current bookmark's list — the same as the existing add-button behaviour.

### Changes to Existing Composables

**`CreateBookmarkLabelsSection` in `AddBookmarkSheet.kt`:**

- Replace the raw `TextField` + "add" icon with `LabelAutocompleteTextField`.
- Thread `existingLabels` as a new parameter into `CreateBookmarkLabelsSection` and `AddBookmarkSheet`.
- `AddBookmarkSheet` callers must provide `existingLabels`.

**`BookmarkDetailsDialog.kt`:**

- Replace the label `TextField` with `LabelAutocompleteTextField`.
- Add `existingLabels: List<String>` parameter.
- Callers provide the list from `BookmarkDetailViewModel.labelsWithCounts`.

**`BookmarkDetailViewModel`** (if Option B chosen):

```kotlin
val labelsWithCounts: StateFlow<Map<String, Int>> = bookmarkRepository
    .observeAllLabelsWithCounts()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
```

### String Resources

No new strings required — the `TextField` hint and add button already exist.

---

## Part 2: Long-Press Context Actions for Bookmark Cards

### Current State

Both `BookmarkGridCard` and `BookmarkCompactCard` in `BookmarkCards.kt` use `.clickable { onClickCard(bookmark.id) }` on the `Card` composable. The image `Box` inside `BookmarkGridCard` has no separate click handler.

An overflow menu (`BookmarkCardActions`) already exists in both cards, providing: Delete, Archive/Unarchive, Favourite/Unfavourite, Share, Open URL, Open in Browser. Long-press is not currently implemented.

### Design

Long-press on a bookmark card opens a `DropdownMenu` context menu. The menu contents differ depending on which zone is long-pressed:

**Zone A — Card Body (non-image area):**
- Copy Link Address
- Share Link (shares the Readeck server URL for the content)

**Zone B — Card Image (image area only, when an image exists):**
- Copy Image URL
- Download Image
- Share Image

On `BookmarkCompactCard` the image is a small 64×64dp thumbnail in a `Row`. Both zones are present but the image zone is smaller.

**Why separate zones?** The image actions (download/share/copy image) are meaningless when pressed on text content. Keeping the menus focused reduces cognitive overhead.

### Gesture Implementation

Replace `.clickable {}` on the outer `Card` with `combinedClickable`:

```kotlin
// import androidx.compose.foundation.combinedClickable

Card(
    modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp)
        .combinedClickable(
            onClick = { onClickCard(bookmark.id) },
            onLongClick = { showBodyContextMenu = true }
        )
) { ... }
```

Add `combinedClickable` to the image `Box` inside `BookmarkGridCard`:

```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)
        .combinedClickable(
            onClick = { onClickCard(bookmark.id) },
            onLongClick = { if (bookmark.imageSrc != null) showImageContextMenu = true }
        )
) { ... }
```

> **Note:** `combinedClickable` requires adding `androidx.compose.foundation:foundation` with the `combinedClickable` API. This is already present in the project.

### Context Menu State

Add local state within each card composable:

```kotlin
var showBodyContextMenu by remember { mutableStateOf(false) }
var bodyContextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

var showImageContextMenu by remember { mutableStateOf(false) }
var imageContextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
```

Use `onLongClickLabel` for accessibility and `pointerInput` to capture the tap position for menu offset if needed, or anchor the menu to the component bounds.

```kotlin
DropdownMenu(
    expanded = showBodyContextMenu,
    onDismissRequest = { showBodyContextMenu = false },
    offset = bodyContextMenuOffset
) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.action_copy_link)) },
        leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
        onClick = {
            showBodyContextMenu = false
            onClickCopyLink(bookmark.url)
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.action_share_link)) },
        leadingIcon = { Icon(Icons.Outlined.Share, null) },
        onClick = {
            showBodyContextMenu = false
            onClickShareLink(bookmark.url)
        }
    )
}

DropdownMenu(
    expanded = showImageContextMenu,
    onDismissRequest = { showImageContextMenu = false }
) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.action_copy_image_url)) },
        leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
        onClick = {
            showImageContextMenu = false
            onClickCopyImageUrl(bookmark.imageSrc!!)
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.action_download_image)) },
        leadingIcon = { Icon(Icons.Outlined.Download, null) },
        onClick = {
            showImageContextMenu = false
            onClickDownloadImage(bookmark.imageSrc!!)
        }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.action_share_image)) },
        leadingIcon = { Icon(Icons.Outlined.Share, null) },
        onClick = {
            showImageContextMenu = false
            onClickShareImage(bookmark.imageSrc!!)
        }
    )
}
```

### New Callback Parameters

Add to both `BookmarkGridCard` and `BookmarkCompactCard`:

```kotlin
onClickCopyLink: (url: String) -> Unit,
onClickShareLink: (url: String) -> Unit,
// Image actions — only needed when imageSrc is non-null
onClickCopyImageUrl: (imageUrl: String) -> Unit,
onClickDownloadImage: (imageUrl: String) -> Unit,
onClickShareImage: (imageUrl: String) -> Unit,
```

These are implemented in the parent (`BookmarkListScreen`) and threaded from `BookmarkListViewModel` event handlers.

### Action Implementations

Implement the actual operations in `BookmarkListScreen.kt` or a utility file. Each action is triggered from the ViewModel via a navigation/event channel (following the existing pattern for `onClickShareBookmark`).

**Copy Link Address**

```kotlin
fun onClickCopyLink(url: String) {
    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    val clip = ClipData.newPlainText("link", url)
    clipboardManager.setPrimaryClip(clip)
    // On Android 12 (API 32) and below, show a confirmation Toast
    // On API 33+ the OS shows its own confirmation toast
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
    }
}
```

**Share Link**

`bookmark.url` is the original saved URL. For sharing the Readeck server URL, use `bookmark.href` (the `href` field present on `BookmarkListItem` — verify this field is present; if not, add it).

```kotlin
fun onClickShareLink(url: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, url)
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, null))
}
```

**Copy Image URL**

```kotlin
fun onClickCopyImageUrl(imageUrl: String) {
    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    val clip = ClipData.newPlainText("image url", imageUrl)
    clipboardManager.setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        Toast.makeText(context, R.string.image_url_copied, Toast.LENGTH_SHORT).show()
    }
}
```

**Download Image**

Use the system `DownloadManager` to queue the download. The image will appear in the device's Downloads folder.

```kotlin
fun onClickDownloadImage(imageUrl: String) {
    val filename = Uri.parse(imageUrl).lastPathSegment ?: "image.jpg"
    val request = DownloadManager.Request(Uri.parse(imageUrl)).apply {
        setTitle(filename)
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
        // Forward auth header from the existing Retrofit client if the image is on the Readeck server
        // Check if the URL host matches the configured server host; if so, add Authorization header
    }
    val downloadManager = context.getSystemService(DownloadManager::class.java)
    downloadManager.enqueue(request)
    Toast.makeText(context, R.string.download_started, Toast.LENGTH_SHORT).show()
}
```

> **Auth consideration:** Images served by the Readeck server require authentication. The `imageUrl` in `BookmarkListItem.imageSrc` may be a proxied URL through the Readeck server. If so, the `DownloadManager` request needs the same `Authorization` header used by Retrofit. Retrieve the token from `SettingsDataStore` and add it via `request.addRequestHeader("Authorization", "Bearer $token")`.

**Share Image**

Sharing an image requires a content URI, not a URL. The approach depends on whether the image is already in Coil's disk cache:

1. **Preferred:** Download the image into a local cache file using a coroutine + Coil's `ImageLoader.execute(ImageRequest)` with `allowHardware = false`, then share via `FileProvider`.
2. **Fallback:** Share the image URL as plain text if the download fails.

```kotlin
fun onClickShareImage(imageUrl: String, context: Context) {
    // Launch coroutine to fetch image from Coil cache or network
    scope.launch {
        val loader = context.imageLoader
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .allowHardware(false)
            .build()
        val result = loader.execute(request)
        val bitmap = (result as? SuccessResult)?.image?.toBitmap()
        if (bitmap != null) {
            // Save bitmap to cache file
            val file = File(context.cacheDir, "shared_image.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "image/jpeg"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, null))
        } else {
            // Fallback: share the URL as text
            onClickShareLink(imageUrl)
        }
    }
}
```

> **FileProvider:** The app's `AndroidManifest.xml` must declare a `FileProvider` authority pointing to `cache-path`. If one already exists (check `AndroidManifest.xml`), reuse it. If not, add the necessary `<provider>` entry and `file_paths.xml` resource.

### `BookmarkListViewModel` Changes

Add ViewModel functions to emit events for the new actions. Follow the existing pattern (navigation events via `Channel`):

```kotlin
fun onClickCopyLink(url: String) { /* emit event to UI layer */ }
fun onClickShareLink(url: String) { /* emit event to UI layer */ }
fun onClickCopyImageUrl(imageUrl: String) { /* emit event to UI layer */ }
fun onClickDownloadImage(imageUrl: String) { /* emit event to UI layer */ }
fun onClickShareImage(imageUrl: String) { /* emit event to UI layer */ }
```

The event channel approach keeps Android-specific APIs (`ClipboardManager`, `DownloadManager`, `Intent`) out of the ViewModel.

### `BookmarkListItem` — `href` field

The "Share Link" action should share the Readeck server's URL for the content (the canonical `href`), not the original saved URL. Verify `BookmarkListItem` includes `href`. If it does not, add it:
- Add `val href: String` to `BookmarkListItem`.
- Populate it from `BookmarkEntity.href` in the mapping function.
- Pass `bookmark.href` (not `bookmark.url`) to `onClickShareLink`.

### Accessibility

- Set `onLongClickLabel` on the `combinedClickable` modifier to announce the action to TalkBack users (e.g. `"Long press for options"`).
- The `DropdownMenu` items are announced by their `text` parameter automatically.

### `BookmarkCompactCard` — Image Zone

In `BookmarkCompactCard`, the image is a 64×64dp thumbnail in a `Row`. Apply `combinedClickable` to the `AsyncImage` composable specifically (not the whole card), using the same image-zone menu pattern as `BookmarkGridCard`. When no image is present, the image zone does not exist and no long-press action is registered for it.

---

## String Resources

Add to `values/strings.xml` and all 10 locale files:

```xml
<!-- Label autocomplete -->
<string name="label_suggestion_hint">Suggestions</string>

<!-- Long-press body actions -->
<string name="action_copy_link">Copy Link Address</string>
<string name="action_share_link">Share Link</string>
<string name="link_copied">Link copied</string>

<!-- Long-press image actions -->
<string name="action_copy_image_url">Copy Image URL</string>
<string name="action_download_image">Download Image</string>
<string name="action_share_image">Share Image</string>
<string name="image_url_copied">Image URL copied</string>
<string name="download_started">Download started</string>
```

---

## Error Handling

| Scenario                             | Handling                                                          |
|--------------------------------------|-------------------------------------------------------------------|
| Image download fails for sharing     | Fallback to sharing the image URL as plain text                   |
| Image download via DownloadManager fails | System notification handles failure; no additional in-app handling needed |
| ClipboardManager not available       | Wrap in try/catch; log error; show error `Toast`                  |
| `href` field missing in `BookmarkListItem` | Use `url` as fallback with a code comment flagging the gap   |

---

## Implementation Sequence

### Label Autocomplete

1. Create `ui/components/LabelAutocompleteTextField.kt` with suggestion filtering and dropdown.
2. Update `CreateBookmarkLabelsSection` in `AddBookmarkSheet.kt` to use the new composable; add `existingLabels` parameter.
3. Update `AddBookmarkSheet` signature; thread `existingLabels` from `BookmarkListScreen`.
4. Add `labelsWithCounts` to `BookmarkDetailViewModel`.
5. Update `BookmarkDetailsDialog` to use `LabelAutocompleteTextField`; thread `existingLabels` from the label map.
6. Add string resources to all 10 locale files.
7. Write Compose UI tests for the autocomplete dropdown (filter logic, suggestion tap).

### Long-Press Context Actions

1. Verify `BookmarkListItem` includes `href`; add it if missing.
2. Add new callback parameters to `BookmarkGridCard` and `BookmarkCompactCard`.
3. Replace `.clickable` with `combinedClickable` on both cards and the image zone of `BookmarkGridCard`.
4. Add context menu `DropdownMenu` components and local state for both cards.
5. Add ViewModel event functions in `BookmarkListViewModel`.
6. Implement action handlers in `BookmarkListScreen` (copy, share, download, share image).
7. Add `FileProvider` to `AndroidManifest.xml` if not already present.
8. Handle auth header for image download if images are served through Readeck.
9. Add string resources to all 10 locale files.
10. Manual test: verify both context menus on grid and compact cards, with and without images.
