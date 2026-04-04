# Branch Guardrails: Annotation Refresh + Offline Resource Deletion

## Purpose
Define lightweight guardrails to prevent the two most failure‑prone paths from causing broken reader experiences:
1) annotation refresh/enrichment mismatches
2) deleting local resources before HTML is safe to render without them

## Guardrail 1: Annotation Refresh Safety
**Risk:** multipart HTML may lack annotation attributes because it reflects stored extraction HTML;
the article endpoint enriches annotations server-side. Client enrichment depends on text matching
when attributes are missing.

**Guardrail behavior**
- If annotation enrichment fails to match more than a threshold (e.g., >20%) of annotations, do **not** overwrite existing cached HTML; log a warning and mark the bookmark for retry.
- If `GET /bookmarks/{id}/article` is available, prefer it for refresh to avoid enrichment mismatch.

**Suggested implementation points**
- `LoadContentPackageUseCase.refreshHtmlForAnnotations`
- `AnnotationHtmlEnricher`

**Telemetry (debug log)**
- `ANNOTATION_REFRESH_SKIPPED` with counts: expected, matched, failed
- `ANNOTATION_REFRESH_FALLBACK` with reason: `legacy-html`, `enrichment-mismatch`, `network-error`

## Guardrail 2: Resource Deletion Safety
**Risk:** deleting files while HTML still contains relative local resource URLs causes broken images offline.

**Guardrail behavior**
- Before deleting resources, verify HTML contains **no** relative resource URLs (or verify it contains only absolute URLs).
- If unsafe, force refresh HTML to absolute URLs or abort deletion and log a warning.

**Suggested implementation points**
- `ContentPackageManager.deleteResources`
- `BatchArticleLoadWorker` (during image-toggle OFF flow)

**Telemetry (debug log)**
- `RESOURCE_DELETE_ABORTED` with reason: `relative-urls-present`
- `RESOURCE_DELETE_OK` with resource count and bytes freed

## Guardrail 3: DIRTY Content Handling
**Risk:** partial file move or refresh failure leaves content in DIRTY state without recovery.

**Guardrail behavior**
- Any failure in atomic swap or delete should mark state DIRTY and enqueue a retry with backoff.
- A DIRTY package should always be treated as **not ready** for offline rendering to avoid partial content display.

**Suggested implementation points**
- `ContentPackageManager.commitPackage`
- `BatchArticleLoadWorker`

## Notes
- These guardrails should be **lightweight** and avoid user‑visible UI changes.
- The goal is to prevent broken reader experiences rather than perfect recovery in every case.
