# Spec: Sync API Content Sync and `omit_description`

## Superseded Status

This document is superseded as the forward-looking content-sync and `omit_description` plan by:

- `docs/specs/sync-multipart-adoption-spec.md`

Keep this file as a historical record of the `v0.11.1` stopgap implementation that persisted
`omitDescription` on top of the pre-multipart content path.

## Summary

This follow-up spec tracks the remaining content-sync work that was split out of
`docs/specs/sync-architecture-optimization-spec.md`.

It now serves two purposes:

1. document the shipped `omit_description` handling that was implemented on top of the
   current content-download path
2. track the still-deferred adoption of `POST /bookmarks/sync` as a future shared content
   transport

It does **not** include incremental metadata migration from
`GET /bookmarks?updated_since=` to the sync API. That remains a separate, lower-priority
follow-up.

## Goals

1. Persist `omitDescription` locally using the current shared content-download path.
2. Use `omitDescription` to suppress duplicate description text in the detail UI when
   article HTML already contains the same summary.
3. Keep future `POST /bookmarks/sync` content-transport work documented separately from the
   shipped `omitDescription` behavior.

## Current State

Today the app still fetches article HTML with `GET /bookmarks/{id}/article`.

That transport is used by all current content sync modes:

- on-demand content load from the detail screen
- automatic background content sync via `BatchArticleLoadWorker`
- date-range content sync via `DateRangeContentSyncWorker`

The incremental metadata flow still cannot persist `omit_description` directly because the
list endpoint returns bookmark summaries, not bookmark detail.

The shipped direction on this branch is:

- persist a nullable `omitDescription` field across REST DTO, domain model, and Room
- fetch `omit_description` from `GET /bookmarks/{id}` only from the shared content refresh
  path when article HTML is being stored and the local flag is still unknown
- reuse the same `LoadArticleUseCase.execute()` path for:
  - on-demand content loading
  - automatic background content sync
  - date-range content sync
- clear cached `omitDescription` when description metadata changes without a content refresh

This gives all three content modes the same behavior without forcing multipart sync-API
adoption first.

## Implemented `omit_description` Assessment

### Why the Current Path Was Chosen

`omit_description` is present on bookmark detail (`GET /bookmarks/{id}`) but not on the
bookmark-summary list endpoint used for incremental metadata sync.

Because all content modes already converge on `LoadArticleUseCase.execute()`, enriching that
shared path was the lowest-risk way to make the flag available everywhere it matters.

That approach keeps the extra request scoped to bookmarks whose content is actually being
downloaded or refreshed instead of expanding every metadata sync into a bookmark-detail pass.

### Efficiency and Correctness Rules

- fetch bookmark detail only after article HTML download succeeds
- skip the detail request when `omitDescription` is already known locally
- treat `omitDescription` as cached metadata tied to the current description/content pairing
- preserve the cached value across metadata-only updates only when description text is
  unchanged
- clear the cached value when description text changes without content refresh

### UI Rule

- hide the standalone italic description block only when:
  - `omitDescription == true`
  - bookmark type is article
  - article HTML is present
- otherwise keep the description visible

That rule avoids suppressing the only summary text available for photo and video fallbacks.

## Future Sync-API Transport Direction

Use the sync endpoints as follows once multipart content transport is worth adopting:

- `GET /bookmarks/sync?since=` remains the delta detector for updated and deleted IDs
- `POST /bookmarks/sync` becomes the shared content-sync transport

From the current API surface:

- request body uses `id`, not `ids`
- request options include:
  - `with_json`
  - `with_html`
  - `with_markdown`
  - `with_resources`
  - `resource_prefix`
- the response is `multipart/mixed`
- each part is identified by headers including `Bookmark-Id` and `Type`
- `Type: json` is the bookmark-information payload variant that includes
  `omit_description`

## Proposed Request Shape

For future content sync, use:

```json
{
  "id": ["bookmark-id-1", "bookmark-id-2"],
  "with_json": true,
  "with_html": true,
  "with_resources": false
}
```

Notes:

- `with_resources` stays `false` initially to preserve the current local storage model
- never call `POST /bookmarks/sync` with an empty `id` list
- keep batch sizes modest for automatic and date-range sync while multipart handling is
  new

## Content Sync Transport Plan

### Shared Client

Add a sync-stream client that:

1. calls `POST /bookmarks/sync`
2. parses the `multipart/mixed` response
3. groups parts by `Bookmark-Id`
4. supports at least:
   - `Type: json`
   - `Type: html`
5. produces a combined per-bookmark result containing:
   - bookmark JSON metadata
   - article HTML when present

### Transport Unification

Use that client for all content sync modes:

- **on-demand content sync**
  - call `POST /bookmarks/sync` with a single bookmark ID
- **automatic background content sync**
  - call `POST /bookmarks/sync` with batched IDs
- **date-range content sync**
  - call `POST /bookmarks/sync` with selected ID batches

This keeps one transport for all content modes and ensures that all of them receive the
same JSON and HTML payload shape.

## `omitDescription` Persistence

The current implementation persists `omitDescription` as a nullable field across:

- REST DTO
- domain model
- Room entity

The current implementation populates it from `GET /bookmarks/{id}` during article refresh.

If multipart sync-API content transport is adopted later, the same field can instead be
populated from `Type: json` parts returned by `POST /bookmarks/sync`.

## UI Rule for `omitDescription`

Use `omitDescription` to prevent duplicated text in the detail UI:

- if `omitDescription == true`, bookmark type is article, and article HTML is present, hide
  the standalone description block in the detail header
- otherwise keep the current description behavior
- for photo and video bookmarks, keep using description as a fallback when no article HTML
  exists
- `omitDescription` is a presentation hint only; it must not delete or blank stored
  description data

## Rejected Alternative: Heuristic Description Deduplication

A purely local heuristic based on matching the description against the beginning of the
article content is intentionally not the primary plan.

Reasons:

- summaries are often normalized differently between metadata and article HTML
- markup, whitespace, entities, punctuation, or truncation can make semantic duplicates
  fail simple prefix checks
- more aggressive fuzzy matching increases false positives and can hide useful summaries
- once hidden incorrectly, the user loses a meaningful piece of context with no reliable
  recovery signal

If a heuristic fallback is ever added, it should be treated as a conservative temporary
workaround rather than a substitute for a real `omit_description` signal from the API.

## Constraints

- **Deferred content model remains intact.** Article content stays in the separate local
  content table.
- **Pending-action merge logic remains intact.** This work should not change metadata
  conflict handling during bookmark sync.
- **Schema migration is required.** Persisting `omitDescription` requires a Room schema
  migration even before future sync-API transport work.
- **Resource persistence remains out of scope.** Binary sync resources are not stored in
  this phase.

## Risks

- current-path detail enrichment adds an extra bookmark-detail request when the cached value
  is missing
- parser and multipart transport adoption, if added later, introduce a new failure surface:
  - partial streams
  - malformed parts
  - missing `Bookmark-Id` headers
  - mismatched or missing json/html pairs
- `omitDescription` must not suppress the only available summary text for non-article
  bookmarks

## Testing

- current-path tests covering:
  - on-demand/shared content download fetching `omit_description`
  - skipping bookmark-detail re-fetch when `omitDescription` is already cached
  - metadata-only invalidation when description changes
  - UI suppression only for article bookmarks with downloaded HTML
- future sync-API tests, if multipart adoption happens later:
  - parser tests for `multipart/mixed` grouping by `Bookmark-Id`
  - parser tests for malformed or incomplete streams failing safely
  - content-sync tests covering on-demand, automatic, and date-range batched sync

## Interactive Validation

1. Validate on-demand content sync on the current shared content path.
   - In Sync Settings, choose **Manual** and keep **On demand** selected.
   - Pick a bookmark whose article has not been downloaded yet and open it.
   - Expected result: the article loads normally, then remains available offline when you
     reopen it with network disabled.
   - If you inspect traffic or logs, you should see `GET /bookmarks/{id}/article`, and when
     `omitDescription` is still unknown for an article bookmark with a description, a
     follow-up `GET /bookmarks/{id}` for detail metadata.
2. Validate automatic content sync.
   - In Sync Settings, choose **Automatic**.
   - Add or update several bookmarks on the server, then trigger bookmark sync in the app.
   - After sync finishes, disable network and open multiple newly synced bookmarks.
   - Expected result: their content is already available offline, and bookmarks that needed
     `omitDescription` now preserve the same reader-header behavior offline.
3. Validate date-range content sync.
   - In Sync Settings, choose **Manual**, then switch the sub-option to **Date Range**.
   - Pick **Past week** or a custom range and tap **Download**.
   - After the job completes, disable network and open bookmarks inside and outside the
     selected range.
   - Expected result: bookmarks in range have offline content; bookmarks outside the range
     are unchanged.
4. Validate `omit_description` behavior in the detail UI.
   - Use a bookmark whose detail payload includes `omit_description = true` and whose
     article HTML already contains the same summary text.
   - Open the bookmark detail screen.
   - Expected result: the standalone description block in the header is hidden, while the
     article body still renders normally.
5. Validate fallback cases for `omit_description`.
   - Open a bookmark with `omit_description = false`.
   - Open a photo or video bookmark where article HTML is absent.
   - Expected result: descriptions still appear in the cases where they are the only
     useful summary text.
6. Validate persistence of the new field.
   - Open a bookmark after sync has fetched both detail metadata and article HTML for it.
   - Force-close the app, disable network, and reopen the same bookmark.
   - Expected result: the same description-suppression behavior remains, proving
     `omitDescription` was stored locally and not applied transiently.
