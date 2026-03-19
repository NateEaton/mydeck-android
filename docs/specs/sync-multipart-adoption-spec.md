# Spec: Multipart Sync Adoption and Offline Content Architecture (v0.12.0)

## Status

This is the canonical sync/content architecture spec for the planned `v0.12.0` cycle.

It supersedes the following documents in `docs/specs/` as the forward-looking plan for sync
and offline reader behavior:

- `sync-architecture-optimization-spec.md`
- `sync-content-api-omit-description-spec.md`
- `sync-error-media-follow-up-spec.md`

Those documents remain useful as historical records for the `v0.11.1` baseline, but new sync
and content work should follow this spec.

## Release Framing

### `v0.11.1`

Use `v0.11.1` to wrap up and ship the current stabilization branch, including:

- non-blocking app-open sync UX
- sync-cursor correctness
- metadata-only insert-path cleanup
- server error-filter parity work
- media-reader fallback fixes
- current-path `omitDescription` persistence and UI suppression behavior

### `v0.12.0`

Use `v0.12.0` to make multipart sync adoption the core enhancement:

- adopt the sync API multipart transport as the authoritative retrieval path
- deliver full offline reading packages for Articles and Pictures
- make `omit_description` come from sync JSON payloads instead of ad-hoc detail enrichment
- reduce reliance on endpoint-specific special cases in content loading

## Summary

MyDeck currently splits read-side retrieval across multiple transports:

- delta detection via `GET /bookmarks/sync?since=`
- incremental metadata reloads via paginated `GET /bookmarks?updated_since=`
- article content via `GET /bookmarks/{id}/article`
- `omit_description` enrichment via `GET /bookmarks/{id}` when needed

That design was acceptable for the `v0.11.1` stabilization release, but it leaves two strategic
problems unsolved:

1. offline reader packages for Articles and Pictures are incomplete because MyDeck does not store
   the resources required to render them faithfully offline
2. `omit_description` still depends on a side-request path that exists only because the list
   endpoint does not carry bookmark-detail fields

`v0.12.0` should consolidate normal read-side retrieval around the sync API:

- `GET /bookmarks/sync?since=` remains the delta detector
- `POST /bookmarks/sync` becomes the primary payload transport for both metadata and content

The app should continue to preserve the current pending-action write model and the non-blocking
sync UX shipped in `v0.11.1`.

## Goals

1. Make `POST /bookmarks/sync` the primary retrieval transport for updated bookmark data.
2. Replace normal use of:
   - `GET /bookmarks?updated_since=` for metadata reloads
   - `GET /bookmarks/{id}/article` for content sync
   - `GET /bookmarks/{id}` as a special `omit_description` enrichment step
3. Deliver full offline reader capability for:
   - **Article** bookmarks
   - **Picture** bookmarks
4. Keep **Video** bookmarks usable as metadata/embed-based bookmarks while explicitly not treating
   video bytes as offline-downloadable reader content.
5. Make `omit_description` flow from the sync JSON payload as the authoritative source of truth.
6. Preserve the current deferred-content model's user-facing behaviors:
   - on-demand content loading
   - automatic background content sync
   - date-range content sync
7. Preserve the current write path and conflict-handling rules for pending local actions.
8. Keep a practical rollback path while multipart ingestion is being proven.

## Non-Goals

- Offline video playback or download of remote video files
- A general-purpose offline browser for arbitrary third-party web apps or scripts
- Replacing the current local mutation queue/write path (`PATCH` / `DELETE` workers)
- Storing Markdown exports in this phase
- Solving every list-thumbnail caching problem in the same phase
- Reworking unrelated bookmark filtering or sort behavior beyond what this architecture touches

## `v0.11.1` Baseline Assumptions

This spec assumes the following behaviors remain intact from `v0.11.1` unless explicitly replaced
below:

- app-open sync stays non-blocking when cached bookmarks already exist
- sync cursors advance only after successful reload completion
- metadata-only writes do not overwrite downloaded content state unnecessarily
- pending local actions still merge ahead of persistence during read-side sync
- `omitDescription` remains a persisted nullable field across REST/domain/Room
- the detail UI still hides standalone article description only when:
  - `omitDescription == true`
  - bookmark type is article
  - article HTML is present
- the `With errors` filter continues matching the server-side error concept already aligned on the
  `v0.11.1` branch

## Current Problems to Solve in `v0.12.0`

### 1. Offline Articles are text-first, not package-complete

The current app stores article HTML but not the image and resource files that the HTML references.
That means:

- text remains available offline
- inline images may not render offline unless WebView cache happens to contain them already
- downloaded reader content does not match user expectations for full offline reading

### 2. Offline Pictures are not modeled as first-class reader packages

Picture bookmarks may have enough metadata to show a remote image while online, but the current
content model is still article-centric. There is no explicit offline package path that guarantees:

- primary image availability offline
- a stable local reader payload for photo detail mode
- the same content-sync semantics as article bookmarks

### 3. `omit_description` is strategically solved, but transport-wise still indirect

The current branch correctly persists and applies `omitDescription`, but it still has to reach for
`GET /bookmarks/{id}` because the metadata list transport does not include bookmark-detail fields.

That extra request is acceptable as a stopgap, but it should not remain the long-term architecture.

### 4. The read path is split across too many endpoint-specific branches

Today the app has separate logic and failure modes for:

- metadata pagination
- article HTML fetch
- detail-metadata enrichment
- resource resolution in WebView

Multipart adoption should reduce those branches and make the retrieval model more uniform.

## API Direction

## Delta Detection

Keep using:

- `GET /bookmarks/sync?since=`

for:

- updated bookmark IDs
- deleted bookmark IDs
- fast no-update detection

This endpoint remains the lightweight top-level trigger for read-side sync.

## Authoritative Payload Transport

Adopt:

- `POST /bookmarks/sync`

as the authoritative data transport for updated bookmark payloads.

From the current published API surface, the app can request:

- `with_json`
- `with_html`
- `with_markdown`
- `with_resources`
- `resource_prefix`

The response is `multipart/mixed`, with parts grouped by `Bookmark-Id` and identified by `Type`.

## Required Part Support in `v0.12.0`

The client must support at least:

- `Type: json`
- `Type: html`
- `Type: resource`

For resource parts, the client must also consume the headers needed to persist and serve them
locally, including at least:

- `Bookmark-Id`
- `Path`
- `Filename`
- `Content-Type`
- `Group` when present

## Request Profiles

### A. Metadata-only sync request

Use for reload of updated bookmarks discovered by delta detection:

```json
{
  "id": ["bookmark-id-1", "bookmark-id-2"],
  "with_json": true,
  "with_html": false,
  "with_resources": false
}
```

### B. Full offline content-package request

Use for content-eligible bookmarks that should be readable offline:

```json
{
  "id": ["bookmark-id-1", "bookmark-id-2"],
  "with_json": true,
  "with_html": true,
  "with_resources": true,
  "resource_prefix": "."
}
```

Notes:

- never call `POST /bookmarks/sync` with an empty `id` list
- `resource_prefix` should stay relative so stored HTML can resolve against a local per-bookmark
  base URL without fragile post-processing
- `with_markdown` remains `false` in this phase

## Target Retrieval Architecture

### 1. One sync-stream client

Add a shared sync-stream client responsible for:

1. calling `POST /bookmarks/sync`
2. parsing the `multipart/mixed` response stream safely
3. grouping parts by `Bookmark-Id`
4. producing per-bookmark package results that may contain:
   - JSON metadata
   - HTML content
   - zero or more resource files

This client becomes the shared retrieval foundation for:

- metadata reloads
- on-demand content sync
- automatic background content sync
- date-range content sync

### 2. One per-bookmark assembly model

Represent multipart results internally as a per-bookmark assembled payload, for example:

- bookmark JSON metadata
- optional HTML body
- optional resource list
- parse warnings / completeness flags

The app should not persist raw stream fragments directly as they arrive. It should first assemble a
per-bookmark result, validate that the minimum required parts are present, and then commit a single
bookmark update transaction.

### 3. Metadata sync migration

Replace incremental metadata reloads based on paginated `GET /bookmarks?updated_since=` with:

1. `GET /bookmarks/sync?since=` to obtain changed IDs and deletes
2. batched `POST /bookmarks/sync` with `with_json = true`

This makes the sync API the primary retrieval model for both metadata and content in `v0.12.0`.

The old paginated list endpoint should remain available only as a temporary rollback path during
initial stabilization, not as a co-equal long-term architecture.

### 4. Content sync migration

Replace normal content retrieval based on `GET /bookmarks/{id}/article` with multipart package
requests.

Use the same transport for:

- **on-demand content sync**
  - single bookmark ID
- **automatic background content sync**
  - batched eligible bookmark IDs
- **date-range content sync**
  - batched eligible bookmark IDs selected from the chosen date range

## Offline Package Semantics by Bookmark Type

### Article

Articles are full offline-package bookmarks.

A successful Article package should contain:

- `Type: json`
- `Type: html`
- any `Type: resource` parts referenced by the HTML and returned by the server

Result:

- article body renders offline
- inline images render offline
- `omit_description` is available from the JSON part

### Picture

Pictures are also full offline-package bookmarks, but their package rules differ slightly.

Required outcome:

- the primary image must be available offline
- the detail reader must render from a stable local payload even when the network is unavailable

If the multipart response includes an HTML part for the picture bookmark, store and use it.
If it does not, generate a minimal local reader HTML wrapper that references the downloaded local
image resource and preserves any useful description/caption text.

Result:

- picture bookmarks become true offline-readable content, not just remote image URLs
- picture detail behavior aligns with user expectations for a downloaded bookmark

### Video

Videos are not full offline-package bookmarks in this phase.

Rules:

- still ingest `Type: json` metadata through multipart sync
- do not treat remote video/embed bytes as offline content to be persisted
- do not promise offline playback
- keep the existing fallback behavior when no cached reader payload exists

Automatic/date-range content sync should therefore focus on Article and Picture bookmarks, not on
attempting to make video playback offline.

## Local Storage Model

## Keep the deferred-content model, but extend it into a package model

Do not collapse everything into the `bookmarks` table.

Instead:

- keep bookmark metadata in `bookmarks`
- keep the reader entry document in `article_content` for backward compatibility and minimal UI
  churn
- add explicit package/resource persistence for offline assets

### New persisted concepts

Add a package manifest model per bookmark, persisted in Room, containing at least:

- `bookmarkId`
- package kind (`ARTICLE`, `PICTURE`, `VIDEO`, or equivalent capability marker)
- whether HTML is present
- whether resources are present
- source bookmark `updated` timestamp or package revision marker
- last successful package refresh timestamp
- local base directory or package identifier

Add a resource index model, persisted in Room, containing at least:

- `bookmarkId`
- logical `Path` from the multipart part header
- MIME type
- group/category when present
- local relative file path
- byte size

Persist resource bytes on disk under app-private storage, for example beneath:

- `files/offline_content/<bookmarkId>/...`

The stored on-disk structure should preserve the server-provided logical paths so relative
references inside HTML continue to resolve predictably.

### Why keep `article_content`

Keeping `article_content` as the stored entry document avoids unnecessary reader churn:

- the current detail UI already knows how to render stored HTML through the existing template path
- migration from text-only article cache to package-backed cache becomes incremental
- picture bookmarks can use the same entry-document concept by storing a generated wrapper HTML
  when needed

## Reader Rendering Strategy

### Articles and Pictures must load with a local base URL

For offline-capable bookmarks, stop loading article HTML with `baseUrl = null`.

Instead:

- load the stored HTML entry document through the existing template path
- set `loadDataWithBaseURL()` to a stable per-bookmark local base URL
- serve package resources through `WebViewAssetLoader` or an equivalent local resource handler

The key invariant is:

- relative image/resource paths in stored HTML must resolve to local package files, not to the
  network

### Picture wrapper rendering

For pictures without a server HTML part, generate a wrapper document that can be rendered through
exactly the same reader pipeline as articles.

That wrapper should:

- reference the locally stored primary image
- preserve description/caption text when useful
- avoid introducing a separate rendering system for pictures if the existing WebView-based reader
  can already serve the need

## `omit_description` Strategy

## Authoritative source

In `v0.12.0`, `omit_description` should come from the multipart `Type: json` payload.

That makes the JSON sync payload the single authoritative source for:

- description
- `omit_description`
- other bookmark-detail fields included in the sync JSON variant

## Resulting architecture change

After multipart JSON adoption is stable:

- remove the special `GET /bookmarks/{id}` detail-enrichment request from the normal content path
- stop treating `omitDescription` as something that must be opportunistically backfilled only
  during article refresh
- let both metadata sync and content sync refresh it whenever the bookmark appears in a sync batch

## UI rule remains the same

Keep the same conservative UI behavior:

- hide the standalone description block only when:
  - `omitDescription == true`
  - bookmark type is article
  - article HTML is present
- keep description visible otherwise
- keep photo/video fallback descriptions visible when they are still the only useful summary text
- never delete or blank the raw description based on `omitDescription`

## Content State Semantics

The existing `contentState` values should be reinterpreted as **offline reader package state** for
bookmarks that support offline packages.

### Article / Picture

For Articles and Pictures:

- `NOT_ATTEMPTED` = no offline package requested yet
- `DOWNLOADED` = complete offline reader package is present locally
- `DIRTY` = package refresh is needed or last refresh failed transiently
- `PERMANENT_NO_CONTENT` = the server does not provide a usable offline-capable package for this
  bookmark

### Video

For Videos:

- do not use `DOWNLOADED` to imply offline playback capability
- avoid marking video bookmarks as content-downloaded simply because metadata or embed HTML exists
- keep fallback behavior explicit so the UI does not promise offline video playback

If the existing enum names become too misleading in implementation, introduce a follow-up rename or
capability flag rather than overloading article-centric assumptions further.

## Atomicity and Failure Handling

Multipart ingestion introduces new failure modes and must be designed defensively.

### Per-bookmark atomic replace

For a bookmark package refresh:

- parse and stage new parts first
- write resources into a temporary location
- validate required parts for that bookmark type
- swap the new package into place only after the assembled result is complete enough to use

Do not delete the old working package before the new one is fully committed.

### Safe handling requirements

The client must fail safely for at least:

- partial streams
- malformed MIME boundaries
- missing `Bookmark-Id` headers
- parts with unknown or unsupported `Type`
- json/html/resource mismatches for the same bookmark
- a resource part whose `Path` is missing or unusable

The correct default on failure is to preserve the last known good local package and mark the
bookmark/package as needing retry rather than leaving it half-updated.

## Migration Plan

### Database and file migration

`v0.12.0` requires schema work for package manifests and resource indexes.

Migration rules:

- keep existing `omitDescription` persistence intact
- keep existing `article_content` rows intact
- add new package/resource persistence alongside them

### Backfill behavior

Existing users may already have:

- bookmark metadata
- stored HTML in `article_content`
- no resource package on disk

After upgrade:

- existing cached HTML remains usable as a legacy text-first cache
- the first multipart content refresh for a bookmark upgrades it into a full local package
- no destructive migration should discard old readable content just because resources are not yet
  present

## Rollout Strategy

Implement `v0.12.0` in stages, but treat this document as one architectural destination.

### Stage 1: infrastructure and metadata

- land the multipart client and parser
- migrate metadata reloads to `POST /bookmarks/sync` with `with_json = true`
- keep the old paginated metadata path as a short-lived rollback switch while the new path is
  validated

### Stage 2: on-demand package sync

- adopt multipart package downloads for single-bookmark Article/Picture requests
- serve local resources in the reader
- verify that offline article inline images and picture detail now work reliably

### Stage 3: automatic and date-range package sync

- move `BatchArticleLoadWorker` and `DateRangeContentSyncWorker` onto the shared multipart package
  transport
- limit background content batches to Article/Picture bookmarks eligible for offline packages

### Stage 4: remove stopgap content/detail fetches

Once multipart sync is proven stable:

- stop using `GET /bookmarks/{id}/article` in normal content sync flows
- stop using ad-hoc `GET /bookmarks/{id}` detail enrichment for `omit_description`
- keep legacy code only as explicit fallback/rollback machinery if still justified

## Testing Requirements

Implementation should include at least:

### Parser and transport tests

- multipart parser grouping by `Bookmark-Id`
- mixed `json` / `html` / `resource` assembly
- malformed-stream failure behavior
- unknown part handling
- empty-batch guard coverage

### Persistence tests

- metadata-only multipart writes preserving local pending-action merge rules
- package-manifest persistence
- resource-index persistence
- upgrade from legacy HTML-only cache to full package cache
- per-bookmark atomic replace behavior preserving last known good package on failure

### Reader tests

- article reader rendering with local base URL and offline inline images
- picture reader rendering with generated wrapper HTML when server HTML is absent
- video fallback behavior remaining honest about online-only playback
- `omitDescription` UI suppression behavior driven from multipart JSON payloads

### Worker/use-case tests

- on-demand content sync using multipart transport
- automatic background content sync batching eligible Article/Picture bookmarks
- date-range content sync batching eligible Article/Picture bookmarks
- retry/failure state transitions for partial package failures

### Migration tests

- Room migration for package tables/indexes
- preservation of existing `article_content`
- preservation of existing `omitDescription`

## Interactive Validation

1. **Metadata sync uses multipart JSON transport.**
   - Trigger a normal bookmark sync with updated bookmarks on the server.
   - Expected result: updated bookmarks refresh correctly without using paginated
     `GET /bookmarks?updated_since=` as the normal path.

2. **Article offline package works fully.**
   - Download an article containing several inline images.
   - Disable network and reopen it.
   - Expected result: both text and inline images render offline.

3. **Picture offline package works fully.**
   - Download a picture bookmark.
   - Disable network and reopen it.
   - Expected result: the image and any useful description/caption render offline.

4. **Video remains honest about online dependency.**
   - Open a video bookmark offline.
   - Expected result: the app does not imply the video itself is downloaded or playable offline.

5. **`omit_description` comes from sync JSON.**
   - Sync a bookmark whose sync JSON includes `omit_description = true`.
   - Disable network and reopen it.
   - Expected result: description suppression behavior persists without requiring an extra detail
     fetch.

6. **Legacy cached content is preserved across upgrade.**
   - Upgrade an install that already contains stored `article_content` but no package resources.
   - Expected result: the bookmark remains readable immediately after upgrade, then gains full
     offline resource fidelity after the next multipart content refresh.

## Decision Summary

`v0.11.1` should ship the current stabilization work.

`v0.12.0` should adopt the sync API multipart transport as the primary retrieval architecture,
including metadata JSON, HTML entry documents, and local resource packages for offline-capable
Article and Picture bookmarks.

This solves both strategic issues at once:

- full offline reading for Articles and Pictures
- a durable, transport-native solution for `omit_description`
