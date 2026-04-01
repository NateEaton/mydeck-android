# Branch Validation Tests: Annotation + Offline Resource Guardrails

## Purpose
Define targeted tests for the riskiest behavior introduced/solidified in the multipart/offline-content work: annotation enrichment/refresh and safe image-resource deletion.

## Scope
- Annotation enrichment and refresh behavior for multipart HTML.
- Resource deletion sequencing when toggling image downloads off.
- Content package atomicity and DIRTY recovery signals.

## Recommended Unit Tests

### 1) AnnotationHtmlEnricher
**Goal:** ensure attributes are restored correctly when multipart HTML lacks annotation attributes.
- Matches single `<rd-annotation>` text and restores `data-id`, `data-color`, `data-note`.
- Multiple annotations with same text (verify deterministic match strategy).
- Missing annotation in API list leaves tag unchanged (no crash).
- Annotation text with entities/whitespace normalization still matches.

**Suggested test file:**
- `app/src/test/java/.../AnnotationHtmlEnricherTest.kt`

### 2) LoadContentPackageUseCase.refreshHtmlForAnnotations
**Goal:** correct selection of legacy vs multipart, and URL rewrite safety.
- Prefers legacy `/bookmark/{id}/article` HTML when available.
- Multipart HTML fallback is enriched before commit.
- URL rewrite: absolute -> relative only when local resources exist.
- Refresh failure does not overwrite existing HTML.

**Suggested test file:**
- `app/src/test/java/.../LoadContentPackageUseCaseTest.kt`

### 3) ContentPackageManager.deleteResources
**Goal:** ensure resources are removed only after HTML is safe to render without local files.
- When HTML contains relative/local resource URLs, deletion is rejected or no-op.
- When HTML contains absolute URLs, deletion succeeds and leaves HTML intact.
- On failure, content state becomes DIRTY and cleanup is scheduled.

**Suggested test file:**
- `app/src/test/java/.../ContentPackageManagerTest.kt`

### 4) BatchArticleLoadWorker text-only transition
**Goal:** ensure toggle-off images workflow is safe for existing packages.
- HTML is refreshed to absolute URLs before resource deletion.
- Transition keeps text content and metadata.
- Transition leaves no broken relative URLs.

**Suggested test file:**
- `app/src/test/java/.../BatchArticleLoadWorkerTest.kt`

## Integration / End-to-End Tests (Optional)
- Open cached article, toggle images off, confirm images still load online and no broken icons offline.
- Open cached article with annotations; confirm colors/notes persist after background sync.

## Fixture Assets
- Small HTML fixture with:
  - Multiple `<rd-annotation>` tags
  - Mixed absolute and relative image URLs
  - Inline note text with special characters

## Expected Outcome
These tests should catch regressions in enrichment logic, HTML refresh order, and resource deletion sequencing before PR merge.
