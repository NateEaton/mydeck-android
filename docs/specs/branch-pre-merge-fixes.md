# Pre-Merge Fix Specifications

Findings from final code review of feature/sync-multipart-v012 before merge to main.

---

## Fix 1 (Required): `BookmarkDetailViewModel` — unconditional `deleteResources()` call

**File:** `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailViewModel.kt`
**Approx. line:** 583–618 (the `hasRes == true` branch of the image toggle handler)

**Problem:**
`contentPackageManager.deleteResources(bookmarkId)` is called unconditionally after the
`fetchTextOnly` call, regardless of whether the HTML update succeeded. Two failure modes:
1. `fetchTextOnly` returns `Error` or `NetworkError` → old relative-URL HTML remains, resources
   are deleted → broken images when opening cached content offline
2. `fetchTextOnly` returns `Success` but `pkg.html == null` → same outcome

The code has the correct intent (fetch absolute-URL HTML first, then strip resources) but the
execution doesn't enforce it.

**Current code:**
```kotlin
val result = multipartSyncClient.fetchTextOnly(listOf(bookmarkId))
if (result is com.mydeck.app.io.rest.sync.MultipartSyncClient.Result.Success) {
    val pkg = result.packages.firstOrNull { it.bookmarkId == bookmarkId }
    if (pkg?.html != null) {
        contentPackageManager.updateHtml(bookmarkId, pkg.html)
    }
}
contentPackageManager.deleteResources(bookmarkId)   // ← always runs
```

**Required change:**
Move `deleteResources()` inside the `pkg?.html != null` branch AND gate it on
`updateHtml()` returning `true`. `updateHtml()` returns `Boolean` — it can fail on I/O
error, in which case the old relative-URL HTML is still on disk. Deleting resources after
a failed HTML write causes the same broken-images outcome as the original bug.

```kotlin
val result = multipartSyncClient.fetchTextOnly(listOf(bookmarkId))
if (result is com.mydeck.app.io.rest.sync.MultipartSyncClient.Result.Success) {
    val pkg = result.packages.firstOrNull { it.bookmarkId == bookmarkId }
    if (pkg?.html != null) {
        val updated = contentPackageManager.updateHtml(bookmarkId, pkg.html)
        if (updated) {
            contentPackageManager.deleteResources(bookmarkId)
            Timber.i("Removed images for bookmark $bookmarkId (text retained)")
        } else {
            Timber.w("Image toggle-off aborted for $bookmarkId: HTML write failed")
        }
    } else {
        Timber.w("Image toggle-off aborted for $bookmarkId: fetchTextOnly returned no HTML")
    }
} else {
    Timber.w("Image toggle-off aborted for $bookmarkId: fetchTextOnly failed ($result)")
}
```

Also remove the now-redundant `Timber.i("Removed images for bookmark $bookmarkId...")` line
that currently follows the unconditional `deleteResources()` call.

---

## Fix 2 (Recommended): Wrong KDoc in `rewriteToRelativeResourceUrls()`

**File:** `app/src/main/java/com/mydeck/app/domain/usecase/LoadContentPackageUseCase.kt`
**Approx. lines:** 358–368

**Problem:**
The KDoc comment describes the offline storage path as
`offline_content/{bookmarkId}/_resources/{filename}` and the rewrite target as
`./_resources/{filename}`. Both are wrong — resources are stored flat at
`offline_content/{bookmarkId}/{filename}` and the rewrite produces `./filename`.
The comment describes the pre-fix (buggy) state, not the current correct behavior.
A future developer reading this would implement changes incorrectly.

**Current comment:**
```
 * The offline content directory stores them at:
 *   `offline_content/{bookmarkId}/_resources/{filename}`
 *
 * Rewriting to `./_resources/{filename}` allows the WebView's offline base URL
 *   (`https://offline.mydeck.local/{bookmarkId}/`) to resolve them through the
 *   OfflineContentPathHandler.
```

**Replace with:**
```
 * The offline content directory stores them flat at:
 *   `offline_content/{bookmarkId}/{filename}`
 *
 * Rewriting to `./{filename}` allows the WebView's offline base URL
 *   (`https://offline.mydeck.local/{bookmarkId}/`) to resolve them through the
 *   OfflineContentPathHandler.
 *
 * Note: the legacy endpoint uses `_resources/{filename}` in its absolute URLs but
 * the multipart sync endpoint sends flat filenames in the `path` header. This
 * function bridges that inconsistency when refreshing HTML via the legacy endpoint.
```

---

## Supplementary Finding: Partial Enrichment Permanence in `AnnotationHtmlEnricher`

**File:** `app/src/main/java/com/mydeck/app/domain/content/AnnotationHtmlEnricher.kt`
**Line:** 41

**Problem (not a merge blocker but worth tracking):**
The early-exit guard `if (html.contains("data-annotation-id-value")) return html` prevents
re-enrichment of HTML that already contains at least one enriched annotation. If initial
enrichment matched only some annotations (e.g., 2 of 5 due to text-match failure), the
remaining bare tags are permanently stuck — subsequent enrichment attempts exit immediately
on seeing the already-enriched tags.

This is distinct from the mismatch-threshold guardrail described in `branch-guardrails.md`
(which is about aborting when too many fail). This is a post-enrichment lock-in issue: once
any annotation is enriched and committed, partial results become permanent.

**Impact:** Low in practice — the legacy article endpoint is the preferred annotation refresh
path and returns fully attributed HTML, avoiding enrichment entirely. Only affects users on
multipart-only paths with partial text-match failures.

**Recommendation:** Add a TODO comment directly in `AnnotationHtmlEnricher.kt` at line 41
(the early-exit guard) to make the limitation visible to anyone working in that file
post-merge, in addition to the test spec entry in `branch-validation-tests.md`.

Suggested comment to add immediately above the early-exit guard:
```kotlin
// TODO (post-merge): This early-exit fires if ANY annotation is already enriched,
// permanently preventing re-enrichment of remaining bare tags if initial enrichment
// was only partial. Fix: replace with a check for whether bare tags still exist
// (BARE_ANNOTATION_PATTERN) rather than whether enriched tags exist.
// Tracked in branch-validation-tests.md (partiallyEnrichedHtmlReEnriched test case).
if (html.contains("data-annotation-id-value")) return html
```

The fix itself (changing the early-exit logic) is part of Guardrail 1 implementation
in `branch-pre-merge-guardrails-impl.md` — the TODO bridges the gap if the guardrail
work is deferred.

---

## Fix to Test Spec: Wrong Attribute Names

**File:** `docs/specs/branch-validation-tests.md`
**Line:** 15

**Problem:**
The test spec says to verify restoration of `data-id` and `data-color` attributes. The actual
implementation writes `data-annotation-id-value` and `data-annotation-color`. Tests written
from this spec will have wrong assertion values.

**Fix:** Update line 15 to read:
```
- Matches single `<rd-annotation>` text and restores `data-annotation-id-value`, `data-annotation-color`, and `title`/`data-annotation-note` (when note present).
```
