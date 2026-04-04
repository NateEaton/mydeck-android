# Pre-Merge Guardrail Implementation Specs

Detailed implementation instructions for Guardrails 1 and 2, plus unit test specs.
All changes are to be implemented locally and verified with a build + unit test run.

---

## Guardrail 1: Annotation Enrichment — Fix Lock-in + Add Mismatch Threshold

### Background

Two related but distinct problems in `AnnotationHtmlEnricher.enrich()`:

**Problem A — Partial enrichment lock-in (line 41):**
The early-exit guard `if (html.contains("data-annotation-id-value")) return html` fires as
soon as ANY enriched tag is present. If a previous enrichment matched 2 of 5 annotations,
subsequent enrichment attempts skip entirely — the 3 remaining bare tags are permanently stuck.

**Problem B — No mismatch threshold (missing):**
When enrichment fails to match a significant fraction of annotations, the result is silently
written through. The intent from `branch-guardrails.md` is to abort and return the original
HTML when match quality is too poor.

### Changes to `AnnotationHtmlEnricher.kt`

**File:** `app/src/main/java/com/mydeck/app/domain/content/AnnotationHtmlEnricher.kt`

#### 1. Add a `MISMATCH_THRESHOLD` constant (alongside the existing patterns):
```kotlin
// If fewer than this fraction of annotations are matched, abort enrichment
// and return the original HTML unchanged.
private const val MIN_MATCH_FRACTION = 0.5
```
0.5 (50%) is the recommended starting value — conservative enough to avoid discarding
useful partial enrichment when only one or two annotations exist, strict enough to catch
systematic text-matching failures. Can be tuned post-release based on telemetry.

#### 2. Replace the early-exit guard (line 41) with a bare-tag-specific check:

**Replace:**
```kotlin
// Check if HTML already has enriched annotations (has data-annotation-id-value)
if (html.contains("data-annotation-id-value")) return html
```

**With:**
```kotlin
// Only skip if there are no bare (un-enriched) annotation tags remaining.
// Do NOT exit on the presence of enriched tags — the HTML may be partially
// enriched from a previous pass and still have bare tags that need enrichment.
if (!BARE_ANNOTATION_PATTERN.matcher(html).find()) return html
```

Note: remove the now-redundant explicit bare-tag check on line 44 (`if (!BARE_ANNOTATION_PATTERN.matcher(html).find()) return html`) since the replacement above subsumes it.

#### 3. Add mismatch threshold check after the matching loop (after line 73, before `return result.toString()`):

```kotlin
// Abort if match quality is below threshold — return original HTML unchanged
// rather than committing a poorly-enriched version.
if (annotations.isNotEmpty() && matched.size.toFloat() / annotations.size < MIN_MATCH_FRACTION) {
    Timber.w(
        "Annotation enrichment aborted: matched ${matched.size}/${annotations.size} " +
            "(below ${(MIN_MATCH_FRACTION * 100).toInt()}% threshold) — returning original HTML"
    )
    return html
}
```

Place this block immediately after the `if (matched.isNotEmpty())` log block and before
`return result.toString()`.

#### 4. Update the KDoc on `enrich()` to reflect the new behavior:

Add to the `@return` line:
```
 * @return The enriched HTML, the original HTML if enrichment quality was below threshold,
 *   or the original if no enrichment was needed
```

### Summary of the `enrich()` flow after changes

1. Return null if html is null
2. Return html unchanged if annotations list is empty
3. Return html unchanged if **no bare** `<rd-annotation>` tags exist (new combined gate)
4. Build text→annotation lookup, run matching loop
5. If match fraction < 0.5, log warning and return **original html**
6. Otherwise log match count and return enriched result

---

## Guardrail 2: `ContentPackageManager.deleteResources()` — Safety Check

### Background

`deleteResources()` has an advisory doc comment saying callers should update HTML to
absolute URLs first, but no enforcement. The `BookmarkDetailViewModel` image-toggle
path (Fix 1 in `branch-pre-merge-fixes.md`) addresses the primary caller, but a
defensive check inside `deleteResources()` itself is the correct long-term guardrail —
it protects against any future caller that doesn't follow the convention.

### Change to `ContentPackageManager.kt`

**File:** `app/src/main/java/com/mydeck/app/domain/content/ContentPackageManager.kt`

#### Add a URL-safety check at the top of `deleteResources()`, after the early-returns:

**Current code (line 290–293):**
```kotlin
suspend fun deleteResources(bookmarkId: String) {
    val pkg = contentPackageDao.getPackage(bookmarkId) ?: return
    val resources = contentPackageDao.getResources(bookmarkId)
    if (resources.isEmpty()) return
```

**After the `if (resources.isEmpty()) return` line, add:**
```kotlin
    // Safety check: abort if HTML still contains relative resource URLs.
    // Relative URLs (./<filename>) only resolve when local files exist — deleting
    // resources while they are still referenced will cause broken images offline.
    val currentHtml = getHtmlContent(bookmarkId)
    if (currentHtml != null && containsRelativeResourceUrls(currentHtml)) {
        Timber.w(
            "deleteResources aborted for $bookmarkId: HTML still contains relative " +
                "resource URLs. Re-fetch HTML with absolute URLs before deleting resources."
        )
        return
    }
```

#### Add the private helper function at the bottom of `ContentPackageManager` (before the closing brace):

```kotlin
/**
 * Returns true if the HTML contains relative resource URL patterns that would break
 * if local resource files were deleted. Relative URLs follow the pattern `./filename`
 * (as produced by the offline asset loader convention).
 */
private fun containsRelativeResourceUrls(html: String): Boolean {
    // Match src="./..." or poster="./..." — the offline-relative URL pattern
    return html.contains(Regex("""(src|poster)=["']\./"""))
}
```

#### Update the KDoc on `deleteResources()` to reflect the guardrail:

Replace the existing advisory comment with:
```kotlin
/**
 * Delete only the resource files (images, etc.) for a bookmark while
 * keeping the HTML entry document. Updates the content_package row to
 * set hasResources=false and clears the content_resource rows.
 *
 * Safety: if the current HTML contains relative resource URLs (`./filename`),
 * deletion is aborted and a warning is logged. Callers must ensure HTML has
 * been updated to use absolute URLs before calling this method.
 *
 * @see executeTextOnlyOverwrite for the safe eviction path
 */
```

---

## Unit Test Specs

### Test 1: `AnnotationHtmlEnricherTest`

**File to create:**
`app/src/test/java/com/mydeck/app/domain/content/AnnotationHtmlEnricherTest.kt`

Test cases (use `@Test` + AssertJ or standard JUnit assertions — match existing test style):

```
singleAnnotationEnriched()
  - HTML with one bare <rd-annotation>text</rd-annotation>
  - Annotations list with matching AnnotationDto (same text, has id/color/note)
  - Assert result contains data-annotation-id-value, data-annotation-color, title, data-annotation-note
  - Assert original inner text is preserved

multipleAnnotationsAllMatched()
  - HTML with 3 bare annotation tags, each with distinct text
  - 3 matching AnnotationDtos
  - Assert all 3 enriched correctly

partialMatchAboveThreshold()
  - HTML with 2 bare annotations, annotations list has 3 entries (one extra not in HTML)
  - matched=2, total=3 → fraction=0.67 > 0.5 → enrichment should proceed
  - Assert 2 tags enriched, result returned

partialMatchBelowThreshold()
  - HTML with 5 bare annotations, annotations list has 5 entries, only 1 text matches
  - matched=1, total=5 → fraction=0.2 < 0.5 → abort
  - Assert returned HTML equals original (unchanged)

missingAnnotationInApiList()
  - HTML with 2 bare annotations, annotations list only has 1 match
  - 1/2 = 0.5 → exactly at threshold (boundary: verify behavior — suggest ≥ passes)
  - Assert no exception; unmatched tag left as bare

alreadyEnrichedHtmlSkipped()
  - HTML already contains data-annotation-id-value (no bare tags)
  - Assert returned HTML is identical reference to input (no processing)

partiallyEnrichedHtmlReEnriched()
  - HTML has 1 enriched tag AND 1 bare tag (was previously partially enriched)
  - OLD behavior: early-exit would skip → bare tag stays forever
  - NEW behavior: bare tag still present → enrichment runs → bare tag gets enriched
  - Assert the bare tag is now enriched after calling enrich()
  - (This is the lock-in regression test)

annotationTextWithEntitiesMatches()
  - AnnotationDto.text contains & or < characters
  - HTML bare tag inner text uses &amp; / &lt; entities
  - Assert match succeeds (stripHtmlTags handles entity decoding)

emptyAnnotationsList()
  - Assert html returned unchanged, no crash

nullHtml()
  - Assert null returned
```

**Fixture helper** (add as a private fun in the test class):
```kotlin
private fun makeAnnotation(id: String, text: String, color: String = "yellow", note: String = "") =
    AnnotationDto(id = id, text = text, color = color, note = note)
```

---

### Test 2: `LoadContentPackageUseCaseTest` — `refreshHtmlForAnnotations`

**File to create:**
`app/src/test/java/com/mydeck/app/domain/usecase/LoadContentPackageUseCaseTest.kt`

This test requires mocking `ReadeckApi`, `MultipartSyncClient`, and `ContentPackageManager`.
Use MockK (matches existing project test tooling — verify with existing test files).

Test cases:

```
legacyEndpointPreferredWhenAvailable()
  - readeckApi.getArticle() returns 200 with HTML body
  - Assert contentPackageManager.updateHtml() called with that HTML
  - Assert multipartSyncClient NOT called

legacyEndpointFallsBackToMultipartOnFailure()
  - readeckApi.getArticle() returns 404
  - multipartSyncClient.fetchHtmlOnly() returns Success with HTML
  - Assert contentPackageManager.updateHtml() called with multipart HTML

urlRewriteAppliedWhenHasResources()
  - readeckApi.getArticle() returns HTML containing:
      src="https://server/bm/01/bookmark123/_resources/image.jpg"
  - Call refreshHtmlForAnnotations(bookmarkId = "bookmark123", hasResources = true)
  - Assert contentPackageManager.updateHtml() called with HTML containing src="./image.jpg"
  - Assert NOT containing _resources/

urlRewriteNotAppliedWhenNoResources()
  - Same HTML as above, hasResources = false
  - Assert updateHtml() called with original absolute URL preserved

refreshFailureDoesNotOverwriteHtml()
  - readeckApi.getArticle() throws exception
  - multipartSyncClient.fetchHtmlOnly() returns Error
  - Assert contentPackageManager.updateHtml() NOT called
  - Assert return value is null
```

---

### Test 3: `ContentPackageManagerTest` — `deleteResources`

**File to create:**
`app/src/test/java/com/mydeck/app/domain/content/ContentPackageManagerTest.kt`

This test requires a temporary directory and mocked DAOs. Use a `@TempDir` JUnit5 rule
for `offlineContentDir`, and MockK for the DAOs.

Test cases:

```
deleteResourcesAbortedWhenHtmlHasRelativeUrls()
  - Write index.html to tempDir/bookmarkId/ containing src="./image.jpg"
  - Call deleteResources(bookmarkId)
  - Assert contentPackageDao.deleteResources() NOT called
  - Assert file still exists

deleteResourcesSucceedsWhenHtmlHasAbsoluteUrls()
  - Write index.html containing src="https://server/image.jpg"
  - Create a dummy resource file at tempDir/bookmarkId/image.jpg
  - Mock contentPackageDao.getResources() to return one resource entry
  - Call deleteResources(bookmarkId)
  - Assert resource file deleted
  - Assert contentPackageDao.deleteResources() called

deleteResourcesNoOpWhenNoPackageExists()
  - Mock contentPackageDao.getPackage() to return null
  - Assert no crash, no DAO mutations

deleteResourcesNoOpWhenResourcesListEmpty()
  - Mock getResources() to return emptyList()
  - Assert no file operations, no crash

deleteResourcesNoOpWhenNoHtmlFile()
  - Content dir exists but no index.html
  - Mock getResources() to return one entry
  - Verify deletion proceeds (no HTML = no relative URL risk)
  - Assert deleteResources() called on DAO
```

---

## Pre-Merge Build Verification Checklist

After implementing all changes locally:

1. `./gradlew :app:testDebugUnitTestAll` — all unit tests pass including new ones
2. `./gradlew :app:assembleDebugAll` — clean build
3. `./gradlew :app:lintDebugAll` — no new lint errors
4. Manual smoke: toggle images off on a cached bookmark with a working network → images
   removed cleanly. Toggle images off with airplane mode → toggle fails gracefully,
   images still display from local cache.
5. Manual smoke: open cached bookmark that has annotations → highlights display with
   correct colors. Add a new annotation → refresh → new highlight appears.
