# Addendum: Multipart Adoption Code Review Outcomes and Status (Mar 2026)

This addendum documents findings from a detailed review of the `feature/sync-multipart-v012` branch and the resulting implementation status.

## Key findings addressed

- Parser buffered resource bodies in memory and used text-line parsing for binary data when `Content-Length` was absent, risking OOM and corruption.
- Atomic commit deleted the old package before ensuring the new one was in place, risking loss of last-known-good content.
- Freshness check compared mismatched timestamp formats (LocalDateTime vs Instant), breaking skip/restore behavior.
- Background/date-range workers fetched content per-ID instead of batching up to 10 as required by the transport spec.
- Metadata sync did not proactively mark existing downloaded packages DIRTY when the server had newer content.
- Server multipart HTML strips `<rd-annotation>` attributes; client enrichment is required for offline highlights.

## Changes implemented

- Streaming parser
  - Streams resource parts directly to disk (no full-body buffering).
  - Binary-safe boundary detection when `Content-Length` is absent (no `readUtf8Line()` for binary).
  - Added tests for large content-length, no-length binary, and truncated streams.

- Atomic package commit
  - Stage → backup old final → promote staging → then persist manifest/resources and cached annotations → remove backup.
  - Preserves prior package on failure; marks `contentState = DIRTY` with reason.

- Timestamp normalization
  - Stores `sourceUpdated` using the JSON `updated` Instant when present.
  - Freshness checks parse stored values leniently and compare as Instants.

- Worker batching
  - New `executeBatch(ids)` (≤10) in content package use case.
  - `BatchArticleLoadWorker` and `DateRangeContentSyncWorker` now use multipart batching; transient per-ID failures fall back to legacy.

- Freshness marking after metadata sync
  - New `FreshnessMarkerUseCase` marks existing `DOWNLOADED` packages `DIRTY` when `bookmark.updated > content_package.sourceUpdated`.
  - Invoked after successful delta metadata reload in `FullSyncWorker`.

## Decisions and clarifications

- Annotation enrichment is required today.
  - Empirical verification shows multipart HTML from the server strips annotation attributes; the client enriches using the annotations API to restore `id`, `data-annotation-id-value`, `data-annotation-color`, and optional `title`.
  - Re-evaluate when server-side behavior changes; until then, enrichment remains part of commit flow.

## Remaining follow-ups (pre-Stage 4)

- Consider invoking freshness marking after legacy paginated metadata reloads as well (when delta→multipart path is bypassed).
- Add more malformed-stream tests (bad boundary, interleaved multi-bookmark parts) and end-to-end persistence tests (including rollback paths).
- Proceed to Stage 4 (remove legacy article/detail/paginated paths) once soak testing completes.

## Status summary

- Stage 1–3a are now substantially implemented with the fixes above; background and date-range content sync use multipart batching and the reader reliably serves offline packages via WebViewAssetLoader.
- Annotation offline support is in place with necessary enrichment and caching.
- Stage 4 removal of legacy paths is the next milestone.
