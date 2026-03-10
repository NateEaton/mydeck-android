# Code Review: Annotations/Highlights Feature

**Branch:** `codex/annotations-highlights-feature`
**Reviewed against:** `annotations-highlights-design-v2.md`, `reader-details-management-tweaks-spec.md`
**Date:** 2026-03-10

---

## Critical Issues (Should Fix)

### 1. Annotation snapshot fetch failure poisons article download state

**File:** `app/src/main/java/com/mydeck/app/domain/usecase/LoadArticleUseCase.kt` lines 164-172

In `storeDownloadedArticle()`, after successfully fetching and persisting article content, the method calls `fetchAnnotationSnapshot()` (line 164). If that `/annotations` call throws a network exception, it propagates up to `execute()` (line 76-86), which catches it and marks the bookmark as `DIRTY` — even though the article HTML was already saved successfully. This means a transient annotation-endpoint failure makes a successful article download appear failed, triggering unnecessary retries.

**Fix:** Wrap the `fetchAnnotationSnapshot()` call inside `storeDownloadedArticle()` in its own try-catch so annotation snapshot failures are logged but don't affect article download state. The snapshot is a convenience cache, not load-critical.

### 2. Root URL builder drops port number

**File:** `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailsDialog.kt` lines 602-613

```kotlin
private fun String.toRootUrl(): String {
    return try {
        val uri = URI(this)
        if (uri.scheme != null && uri.host != null) {
            "${uri.scheme}://${uri.host}"  // <-- drops uri.port
        } else {
            this
        }
    } catch (_: Exception) { this }
}
```

Self-hosted Readeck instances commonly run on non-default ports (e.g., `https://host:8443/path`). This strips the port, producing the wrong origin URL.

**Fix:** Include port when present and non-default:
```kotlin
val portSuffix = if (uri.port > 0) ":${uri.port}" else ""
"${uri.scheme}://${uri.host}$portSuffix"
```

---

## Acceptable V2 Departures (Working Solutions, No Action Required)

### 3. Annotation cache in SharedPreferences (per-bookmark keys)

**Files:** `SettingsDataStoreImpl.kt` line 164, `AnnotationCache.kt`

V2 said no local annotation persistence was needed. The implementation stores a JSON snapshot per bookmark in SharedPreferences under keys like `cached_annotation_snapshot_{bookmarkId}`. This works but has a scaling concern: SharedPreferences loads the entire file into memory, so hundreds of bookmarks with annotations could bloat it. However, for typical usage patterns (dozens, not thousands, of annotated bookmarks) this is acceptable.

#### Scaling path: Room table with hash-only change detection

If scale became an issue, the recommended approach is a lightweight Room table storing only a hash of the snapshot rather than the full JSON payload:

```kotlin
@Entity(tableName = "annotation_cache")
data class AnnotationCacheEntity(
    @PrimaryKey
    val bookmarkId: String,
    val snapshotHash: String,   // SHA-256 of the normalized snapshot
    val annotationCount: Int,   // quick check: 0 means no annotations
    val updatedAt: Long         // epoch millis of last check
)
```

The flow would remain the same — fetch `GET /annotations` on open, normalize and serialize to JSON, then SHA-256 hash the result (~32 bytes per bookmark instead of the full payload). Compare `snapshotHash` to the stored value; if different, re-fetch article HTML and update the hash.

**Why this scales better:**
- Room uses SQLite, so each bookmark's cache row is independent — no "load everything into memory" problem like SharedPreferences
- The hash is fixed-size regardless of annotation count or text length
- `annotationCount` lets you skip the hash comparison entirely for bookmarks with zero annotations (common case)
- `updatedAt` enables a staleness TTL if you later want to skip the API call for recently-checked bookmarks (e.g., "don't re-check within 5 minutes")
- Deleting a bookmark can cascade-delete its cache row via a foreign key

**Migration path:**
- Add the Room entity and a database migration
- On first launch after upgrade, either iterate existing SharedPreferences keys matching `cached_annotation_snapshot_*`, compute hashes, insert into Room, then clear those keys — or simply clear all cached snapshots and let them rebuild naturally on next bookmark open (simpler, low cost since it just triggers one extra article re-fetch per annotated bookmark)

The key insight is that the current design stores the full snapshot for comparison when all you actually need is a change-detection signal. A hash gives you that in constant space.

### 4. Dual-source annotation model (API + DOM)

**Files:** `BookmarkDetailViewModel.kt` line 617, `WebViewAnnotationBridge.kt` line 31

V2 treated the highlights sheet as a simple API list with DOM colors merged in. The implementation is more explicitly dual-source: the API provides annotation membership and identity, the DOM provides color and note text. The merge in `fetchAnnotations()` (ViewModel line 627-638) maps API DTOs to domain `Annotation` objects, filling in `color` and `note` from `RenderedAnnotation` objects extracted from the DOM.

This is a sound approach given the API doesn't expose color. The fallback to `"yellow"` when no rendered match is found (line 635) is reasonable since yellow is Readeck's default.

### 5. Outer-scroll navigation model for highlight jumps

**Files:** `BookmarkDetailScreen.kt` line 646, `WebViewAnnotationBridge.kt` line 347

V2 assumed `scrollIntoView()` would suffice. The implementation correctly identified that the WebView sits inside a Compose scroll container, so `scrollIntoView()` only scrolls the WebView's internal viewport — not the outer Compose `ScrollState`. The solution uses `getAnnotationViewportInfo()` to get a `centerRatio` from the DOM, then computes the target scroll position in the outer Compose `ScrollState`.

### 6. Overlap-as-edit behavior for selections spanning existing highlights

**Files:** `Annotation.kt` line 14-22, `WebViewAnnotationBridge.kt` line 255-276

V2 didn't detail overlap handling. The JS `getSelectedAnnotationIds()` checks if the selection range intersects existing `rd-annotation` elements and returns all overlapped annotation IDs. The native side then treats these as edit targets. `AnnotationEditState.annotationIds` (ViewModel line 1176) supports multiple IDs, and `updateAnnotationColors()` / `deleteAnnotations()` iterate over all of them.

This is well-designed. The only concern is the sequential API calls in `updateAnnotationColors()` (line 809) — if one fails mid-batch, some annotations get updated and others don't. But given typical overlap counts (2-3), this is acceptable.

### 7. Note display from DOM (read-only)

**Files:** `WebViewAnnotationBridge.kt` lines 46-52, `AnnotationEditSheet.kt` lines 81-98

V2 excluded notes. The implementation correctly extracts note text from `title` attributes on note `rd-annotation` elements and displays them read-only in the edit sheet. The `onNoteClicked` callback provides a hook for future note editing. This is clean and forward-looking.

### 8. Refresh-preserving content update

**Files:** `BookmarkDetailWebViews.kt` lines 369-383

The `update` block uses `tag` comparison to avoid reloading when content hasn't changed. When `articleContent` changes (e.g., after annotation mutation → article re-fetch), Compose recomposes and the `content` state updates, which triggers the `update` lambda to call `loadDataWithBaseURL`. This preserves the WebView instance and transitions directly from old HTML to new HTML without an intermediate null, avoiding the "blanking" problem.

---

## Minor / Style Observations (Nice-to-Fix)

### 9. Duplicate color mapping functions

**Files:** `AnnotationsBottomSheet.kt` line 174, `AnnotationEditSheet.kt` line 195

Both files define nearly identical `highlightColorForName()` / `annotationColorForName()` functions. These should be consolidated into a shared utility.

### 10. `ReadeckApi` injected directly into ViewModel

**File:** `BookmarkDetailViewModel.kt` line 69

The ViewModel calls `readeckApi` directly for annotation CRUD rather than going through the repository. The rest of the ViewModel's data access goes through `BookmarkRepository`. This is acceptable for operations that don't need local persistence (annotations are server-only), but it creates an inconsistency in the data access pattern.

### 11. `WebViewAnnotationBridge.scrollToAnnotation()` appears orphaned

**File:** `WebViewAnnotationBridge.kt` line 18

This method uses `scrollIntoView()` but the actual navigation flow uses `getAnnotationViewportInfo()` + outer Compose scroll. If `scrollToAnnotation()` is truly unused, it should be removed.

### 12. `AnnotationCache` snapshot is larger than needed

**File:** `app/src/main/java/com/mydeck/app/io/rest/model/AnnotationCache.kt`

The snapshot includes the full annotation body (selectors, offsets, text). For change detection, only `id` + `created` would suffice. This makes the snapshot larger than necessary but doesn't cause correctness issues.

### 13. Missing test for critical issue #1

**Files:** `LoadArticleUseCaseTest.kt`, `BookmarkDetailViewModelTest.kt`

The tests cover the annotation sync refresh path but don't test the failure scenario where annotation snapshot fetch fails during article download. A test that mocks a failing `getAnnotations` after a successful `getArticle` would validate the fix for issue #1.

---

## Files to Modify

| File | Change | Priority |
|------|--------|----------|
| `LoadArticleUseCase.kt` | Wrap `fetchAnnotationSnapshot()` in `storeDownloadedArticle()` with try-catch | Critical |
| `BookmarkDetailsDialog.kt` | Include port in `toRootUrl()` | Critical |
| `LoadArticleUseCaseTest.kt` | Add failure scenario test | Recommended |
| `AnnotationsBottomSheet.kt` / `AnnotationEditSheet.kt` | Extract shared color utility | Minor |
| `WebViewAnnotationBridge.kt` | Remove orphaned `scrollToAnnotation()` | Minor |

---

## Overall Assessment

The implementation is architecturally sound and handles the real-world complexity well — particularly the three-source-of-truth coordination (cached HTML, API membership, DOM presentation), the outer-scroll navigation model, and the overlap-as-edit behavior. The V2 departures are justified by practical constraints discovered during implementation. The two critical issues are straightforward to fix.
