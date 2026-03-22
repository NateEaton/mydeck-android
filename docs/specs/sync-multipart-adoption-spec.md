# Spec: Multipart Sync Adoption and Offline Content Architecture (v0.12.0) — v2

## Status

This is the canonical sync/content architecture spec for the `v0.12.0` release cycle.

It supersedes the following documents in `docs/specs/` as the forward-looking plan for sync and
offline reader behavior:

- `sync-architecture-optimization-spec.md`
- `sync-content-api-omit-description-spec.md`
- `sync-error-media-follow-up-spec.md`
- v1 of this document

Those documents remain useful as historical records for the `v0.11.1` baseline, but new sync and
content work should follow this spec.

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
- deliver full offline reading packages for Articles and Pictures (text + images)
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
   - **Article** bookmarks (text + inline images)
   - **Picture** bookmarks (primary image + caption)
4. Keep **Video** bookmarks usable as metadata/embed-based bookmarks while explicitly not treating
   video bytes as offline-downloadable reader content.
5. Make `omit_description` flow from the sync JSON payload as the authoritative source of truth.
6. Preserve the current deferred-content model's user-facing behaviors:
   - on-demand content loading
   - automatic background content sync
   - date-range content sync
7. Preserve the current write path and conflict-handling rules for pending local actions.

## Non-Goals

- Offline video playback or download of remote video files
- A general-purpose offline browser for arbitrary third-party web apps or scripts
- Replacing the current local mutation queue/write path (`PATCH` / `DELETE` workers)
- Storing Markdown exports in this phase
- Solving every list-thumbnail caching problem in the same phase
- Reworking unrelated bookmark filtering or sort behavior beyond what this architecture touches
- Per-bookmark "remove downloaded content" actions (future enhancement)
- User toggle for text-only vs. text+images download (future enhancement if needed)

## `v0.11.1` Baseline Assumptions

This spec assumes the following behaviors remain intact from `v0.11.1` unless explicitly replaced
below:

- app-open sync stays non-blocking when cached bookmarks already exist
- sync cursors advance only after successful reload completion
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

- metadata pagination (`LoadBookmarksUseCase` → `GET /bookmarks?updated_since=`)
- article HTML fetch (`LoadArticleUseCase` → `GET /bookmarks/{id}/article`)
- detail-metadata enrichment (`resolveOmitDescription` → `GET /bookmarks/{id}`)
- resource resolution in WebView (currently not intercepted — relies on network)

Multipart adoption should reduce those branches and make the retrieval model more uniform.

## API Direction

### Delta Detection

Keep using:

- `GET /bookmarks/sync?since=`

for:

- updated bookmark IDs
- deleted bookmark IDs
- fast no-update detection

This endpoint remains the lightweight top-level trigger for read-side sync.

### Authoritative Payload Transport

Adopt:

- `POST /bookmarks/sync`

as the authoritative data transport for updated bookmark payloads.

From the current published API surface (Readeck v0.22.1), the app can request:

- `with_json`
- `with_html`
- `with_markdown`
- `with_resources`
- `resource_prefix`

The response is `multipart/mixed`, with parts grouped by `Bookmark-Id` and identified by `Type`.

### Required Part Support in `v0.12.0`

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
- `Content-Length`
- `Group` (`icon`, `image`, `thumbnail`, `embedded`)

### Request Profiles

#### A. Metadata-only sync request

Use for reload of updated bookmarks discovered by delta detection:

```json
{
  "id": ["bookmark-id-1", "bookmark-id-2"],
  "with_json": true,
  "with_html": false,
  "with_resources": false
}
```

#### B. Full offline content-package request

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

- never call `POST /bookmarks/sync` with an empty `id` list (the API returns all bookmarks when
  `id` is omitted — enforce this guard client-side)
- `resource_prefix` should stay relative (`.`) so stored HTML can resolve against a local
  per-bookmark base URL without fragile post-processing
- `with_markdown` remains `false` in this phase

### Batch Size Limits

To keep memory usage and response sizes manageable on mobile:

- **Metadata-only requests**: batch up to 50 IDs per request (JSON-only responses are small)
- **Content-package requests**: batch up to 10 IDs per request (responses include HTML and image
  binary data that can be several MB per bookmark)

These limits should be constants that are easy to tune after real-world observation.

## Multipart Client Architecture

### The core technical challenge

Retrofit cannot deserialize `multipart/mixed` responses natively. The `POST /bookmarks/sync`
endpoint returns a binary stream with MIME boundaries, not structured JSON. This requires a
purpose-built streaming parser.

### Retrofit interface

Add a new method to `ReadeckApi` that returns the raw response body:

```kotlin
@POST("bookmarks/sync")
suspend fun syncBookmarks(
    @Body request: SyncRequest
): Response<ResponseBody>
```

Where `SyncRequest` is a serializable data class matching the request profiles above.

The raw `ResponseBody` is consumed through OkHttp's Okio `BufferedSource` for streaming parsing.

### Streaming multipart parser

Build a dedicated `MultipartSyncParser` that:

1. Reads the `Content-Type` response header to extract the MIME boundary string
2. Streams through the response body using the Okio `BufferedSource`
3. For each part between boundaries:
   a. Parses part headers (`Bookmark-Id`, `Type`, `Path`, `Group`, `Content-Type`,
      `Content-Length`, `Filename`)
   b. Routes the part body based on `Type`:
      - `json` → deserialize into `BookmarkDto` using the existing kotlinx.serialization model
      - `html` → read as UTF-8 string
      - `resource` → stream directly to a temporary file on disk (do not buffer in memory)
      - unknown types → skip and log a warning
4. Groups parsed parts by `Bookmark-Id` into per-bookmark assembled results

### Memory safety

The parser must stream resource parts directly to disk rather than buffering them in memory. For a
batch of 10 bookmarks, resource data could total 10+ MB. The parser should hold at most one part's
headers plus a small read buffer in memory at any time.

### Per-bookmark assembly model

After parsing, each bookmark's results are represented as:

```
BookmarkSyncPackage:
  - bookmarkId: String
  - json: BookmarkDto?
  - html: String?
  - resources: List<ResourcePart>
  - parseWarnings: List<String>

ResourcePart:
  - path: String          (from Path header)
  - filename: String      (from Filename header)
  - mimeType: String      (from Content-Type header)
  - group: String?        (from Group header: icon/image/thumbnail/embedded)
  - tempFile: File        (staged temp file on disk)
  - byteSize: Long
```

### Error handling in the parser

The parser must handle gracefully:

- Partial/truncated streams (connection dropped mid-response)
- Malformed MIME boundaries
- Missing `Bookmark-Id` headers on a part (skip the part)
- Parts with unknown or unsupported `Type` values (skip and log)
- Resource parts with missing or unusable `Path` headers (skip and log)
- JSON deserialization failures for individual bookmarks (skip that bookmark, continue parsing)

On any per-part failure, the parser should continue processing remaining parts. Only a stream-level
failure (broken connection, invalid boundary) should abort the entire parse.

## Target Retrieval Architecture

### 1. One sync-stream client

Add a shared `MultipartSyncClient` responsible for:

1. Calling `POST /bookmarks/sync` with the appropriate request profile
2. Invoking the streaming parser
3. Producing a list of `BookmarkSyncPackage` results

This client becomes the shared retrieval foundation for:

- metadata reloads
- on-demand content sync
- automatic background content sync
- date-range content sync

### 2. Metadata sync migration

Replace incremental metadata reloads based on paginated `GET /bookmarks?updated_since=` with:

1. `GET /bookmarks/sync?since=` to obtain changed IDs and deletes
2. Batched `POST /bookmarks/sync` with `with_json = true` (metadata-only profile)

This replaces `LoadBookmarksUseCase`'s current paginated fetch loop. The delta detection response
already provides the exact set of changed IDs, so pagination headers are no longer needed.

The old paginated list endpoint should remain available only as a temporary fallback during initial
stabilization.

### 3. Content sync migration

Replace content retrieval based on `GET /bookmarks/{id}/article` with multipart package requests
(content-package profile).

Use the same transport for:

- **On-demand content sync** — single bookmark ID, triggered from detail view
- **Automatic background content sync** — batched eligible bookmark IDs after metadata sync
- **Date-range content sync** — batched eligible bookmark IDs selected from the chosen date range

This replaces `LoadArticleUseCase`'s current per-bookmark `getArticle()` call and its
`resolveOmitDescription()` side-request, since both JSON metadata (including `omit_description`)
and HTML content arrive in the same multipart response.

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

### Clean-break migration

Because both current users perform fresh installs across releases, `v0.12.0` treats local content
storage as a clean break:

- No backward-compatible migration from `v0.11.1` content tables
- No dual-path code for "legacy HTML-only cache" alongside "full package cache"
- The Room schema migration should be destructive for content tables — drop and recreate
- Bookmark metadata migration should remain non-destructive (metadata is cheap to re-sync but
  preserving it avoids an unnecessary full reload)
- After upgrade, the app auto-triggers a metadata sync followed by content sync to repopulate

### Schema changes

#### Drop `article_content` table

The existing `ArticleContentEntity` table (bookmarkId → content string) is replaced by the package
model. The entry HTML document moves into the package storage.

#### New: `content_package` table

Per-bookmark package manifest, persisted in Room:

| Column | Type | Description |
|--------|------|-------------|
| `bookmarkId` | TEXT PK | FK to bookmarks |
| `packageKind` | TEXT | `ARTICLE`, `PICTURE`, or `VIDEO` |
| `hasHtml` | INTEGER | whether HTML entry document is present |
| `hasResources` | INTEGER | whether resource files are present |
| `sourceUpdated` | TEXT | bookmark `updated` timestamp at time of package download |
| `lastRefreshed` | INTEGER | epoch millis of last successful package refresh |
| `localBasePath` | TEXT | relative path under app-private storage |

#### New: `content_resource` table

Per-resource index, persisted in Room:

| Column | Type | Description |
|--------|------|-------------|
| `id` | INTEGER PK | auto-increment |
| `bookmarkId` | TEXT | FK to bookmarks |
| `path` | TEXT | logical path from multipart `Path` header |
| `mimeType` | TEXT | from `Content-Type` header |
| `group` | TEXT | `icon`, `image`, `thumbnail`, `embedded`, or null |
| `localRelativePath` | TEXT | relative file path under package directory |
| `byteSize` | INTEGER | file size in bytes |

#### File storage layout

Persist resource bytes on disk under app-private storage:

```
files/offline_content/<bookmarkId>/index.html
files/offline_content/<bookmarkId>/image.jpeg
files/offline_content/<bookmarkId>/Wj66qLatSeikPc31FwvqyS.jpg
files/offline_content/<bookmarkId>/...
```

The stored on-disk structure preserves the server-provided logical paths so relative references
inside HTML resolve predictably against a per-bookmark base URL.

### Content state semantics

The existing `contentState` values are reinterpreted as **offline reader package state**:

#### Article / Picture

- `NOT_ATTEMPTED` = no offline package requested yet
- `DOWNLOADED` = complete offline reader package is present locally (HTML + resources)
- `DIRTY` = package refresh is needed or last refresh failed transiently
- `PERMANENT_NO_CONTENT` = the server does not provide a usable offline-capable package

#### Video

- Do not use `DOWNLOADED` to imply offline playback capability
- Avoid marking video bookmarks as content-downloaded simply because metadata or embed HTML exists
- Keep fallback behavior explicit so the UI does not promise offline video playback

## Reader Rendering Strategy

### Articles and Pictures must load with a local base URL

For offline-capable bookmarks with a downloaded package, stop loading article HTML with
`baseUrl = null` (the current behavior in `BookmarkDetailWebViews.kt`).

Instead:

- Load the stored HTML entry document through the existing template path
- Set `loadDataWithBaseURL()` to a stable per-bookmark local base URL
- Serve package resources through `WebViewAssetLoader` with a custom `PathHandler` that maps
  resource requests to files under `files/offline_content/<bookmarkId>/`

The key invariant is:

- Relative image/resource paths in stored HTML (e.g., `image.jpeg`,
  `Wj66qLatSeikPc31FwvqyS.jpg`) must resolve to local package files, not to the network

### WebViewAssetLoader integration

Use `WebViewAssetLoader.Builder()` with a `PathHandler` registered for a synthetic per-bookmark
domain, for example:

- Base URL: `https://offline.mydeck.local/<bookmarkId>/`
- Path handler serves files from `files/offline_content/<bookmarkId>/`

The `shouldInterceptRequest` override in the WebViewClient intercepts resource loads and serves
them from local storage. For bookmarks without a downloaded package, fall through to default
network loading (preserving current behavior).

### Picture wrapper rendering

For pictures without a server HTML part, generate a wrapper document that can be rendered through
exactly the same reader pipeline as articles.

That wrapper should:

- Reference the locally stored primary image using its relative path
- Preserve description/caption text when useful
- Use the same template/theming system as article content
- Avoid introducing a separate rendering system for pictures

### Fallback for non-package bookmarks

When a bookmark does not have a local package (e.g., content not yet downloaded, or video type):

- Continue using the current rendering behavior (network-loaded content, `baseUrl = null` for
  articles, embed HTML for videos)
- The transition should be transparent — the reader checks for a local package and falls back
  gracefully

## `omit_description` Strategy

### Authoritative source

In `v0.12.0`, `omit_description` comes from the multipart `Type: json` payload.

That makes the JSON sync payload the single authoritative source for:

- description
- `omit_description`
- other bookmark-detail fields included in the sync JSON variant

### Resulting architecture change

After multipart JSON adoption is stable:

- Remove the special `GET /bookmarks/{id}` detail-enrichment request
  (`resolveOmitDescription()` in `LoadArticleUseCase`)
- Stop treating `omitDescription` as something that must be opportunistically backfilled only
  during article refresh
- Let both metadata sync and content sync refresh it whenever the bookmark appears in a sync batch

### UI rule remains the same

Keep the same conservative UI behavior:

- Hide the standalone description block only when:
  - `omitDescription == true`
  - bookmark type is article
  - article HTML is present
- Keep description visible otherwise
- Keep photo/video fallback descriptions visible when they are still the only useful summary text
- Never delete or blank the raw description based on `omitDescription`

## Network Efficiency

### Skip redundant content re-downloads

The primary mechanism for avoiding redundant downloads:

1. Delta detection (`GET /bookmarks/sync?since=`) returns only bookmarks whose `updated` timestamp
   has advanced since the last sync cursor
2. Before requesting a content package for a bookmark, compare the server's `updated` timestamp
   (from the delta detection response or the metadata sync JSON) against the `sourceUpdated` field
   in the local `content_package` manifest
3. If `sourceUpdated` matches or is newer than the server's `updated`, skip the content request
   for that bookmark

This avoids re-downloading multi-megabyte resource packages for bookmarks that haven't changed.

### Annotation-driven refresh

The current `refreshCachedArticleIfAnnotationsChanged` flow in `LoadArticleUseCase` re-fetches
article HTML when annotations change. In `v0.12.0`, this should request a content package (HTML +
resources) through the multipart transport rather than calling `GET /bookmarks/{id}/article`
directly.

Since annotation changes don't necessarily advance the bookmark's `updated` timestamp, this flow
should bypass the `sourceUpdated` freshness check and always request the package when annotation
state indicates a refresh is needed.

### Offline annotation data

The Readeck server embeds annotation rendering data directly into article HTML as `<rd-annotation>`
custom elements with attributes including `data-annotation-id-value`, `data-annotation-color`,
`title` (note text), and `id="annotation-{id}"`. This HTML is the **authoritative source** for
annotation color and note data — the `GET /bookmarks/{id}/annotations` REST endpoint returns only
`id`, `text`, selectors, offsets, and `created` but **does not include color or note**.

To support offline highlight listing, scroll-to-annotation, and tap-to-edit, annotation metadata
must be extracted from the HTML at content-package commit time and persisted locally.

#### Local annotation cache schema

A new `cached_annotation` table stores annotation metadata extracted from downloaded HTML:

| Column       | Type    | Description                                    |
|--------------|---------|------------------------------------------------|
| `id`         | TEXT PK | Annotation ID (from `data-annotation-id-value`)|
| `bookmarkId` | TEXT FK | Foreign key to `bookmarks`                     |
| `text`       | TEXT    | Highlighted text content                       |
| `color`      | TEXT    | Color (from `data-annotation-color`)           |
| `note`       | TEXT?   | Note text (from `title` attribute)             |
| `created`    | TEXT    | Creation timestamp (empty if not available)    |

Foreign key cascades on bookmark delete. Indexed on `bookmarkId`.

#### HTML annotation extraction

During `ContentPackageManager.commitPackage()`, after the HTML part is available:

1. Parse all `<rd-annotation>` elements from the HTML string
2. For each element, extract `data-annotation-id-value`, `data-annotation-color`, `title`,
   and inner text content
3. Persist as `CachedAnnotationEntity` rows in the same Room transaction that commits the
   content package manifest and resources

This is a pure local operation — no network call needed. Every content download path
(on-demand, batch, date-range) automatically populates the annotation cache.

#### Offline-first annotation loading

The annotations bottom sheet (`fetchAnnotations`) currently makes a live
`GET /bookmarks/{id}/annotations` call. This must become offline-first:

1. Load from `CachedAnnotationDao.getAnnotationsForBookmark(bookmarkId)` immediately
2. If online, also call the REST endpoint and merge: API provides `id`, `text`, `created`;
   local cache provides `color` and `note` (authoritative from HTML)
3. If offline, use the local cache directly — it has all fields needed for display

#### Legacy annotation sync elimination

For bookmarks with a content package directory, the legacy `syncAnnotationsIfNeeded` /
`refreshCachedArticleIfAnnotationsChanged` path must be skipped entirely. Content-package
HTML already contains server-baked annotations with correct colors. Running the legacy sync
causes a visible color flash (annotations momentarily default to yellow during re-render).

For content-package bookmarks, annotation-driven refresh should:

1. Compare the current annotation snapshot against the cached snapshot
2. If changed, re-download the content package via `MultipartSyncClient.fetchContentPackages()`
3. Re-extract annotation metadata from the new HTML and update `cached_annotation` rows

#### Annotation CRUD while offline (future)

Offline annotation create/update/delete is deferred to a future stage. It requires new
`PendingActionEntity.ActionType` values and optimistic local state updates. For now,
annotation mutations require network connectivity (existing behavior).

## Storage Management

### Visibility

Add a "Storage used" display in Sync Settings showing total size of downloaded offline content.
Computed by summing file sizes under `files/offline_content/`.

### Cleanup

Provide a "Clear all offline content" action in Sync Settings that:

1. Deletes all files under `files/offline_content/`
2. Clears all rows from `content_package` and `content_resource` tables
3. Resets `contentState` to `NOT_ATTEMPTED` for all bookmarks
4. Shows a confirmation dialog before proceeding

### Natural limits

The existing user controls already limit accumulation:

- On-demand download: user explicitly chooses which bookmarks to download
- Automatic sync: only downloads content for bookmarks that appear in normal sync batches
- Date-range sync: user selects a specific date window

For reference, ~1800 content-bearing bookmarks with full image resources totals approximately
1.6 GB, which is modest for modern mobile devices (128–256 GB typical storage).

## Atomicity and Failure Handling

Multipart ingestion introduces new failure modes and must be designed defensively.

### Per-bookmark atomic replace

For a bookmark package refresh:

1. Parse and stage new parts into a temporary directory
   (`files/offline_content/<bookmarkId>_staging/`)
2. Write resources to the staging directory as they stream in
3. Validate required parts for that bookmark type:
   - Article: JSON required, HTML required, resources optional but expected
   - Picture: JSON required, at least one `image`-group resource required
4. In a single Room transaction:
   a. Update bookmark metadata from JSON
   b. Upsert `content_package` manifest
   c. Replace `content_resource` rows
   d. Update `contentState` to `DOWNLOADED`
5. Atomically move the staging directory to the final location (rename, not copy)
6. Delete the old directory only after the new one is in place

Do not delete the old working package before the new one is fully committed.

### Failure recovery

On any failure during package ingestion for a specific bookmark:

- Delete the staging directory for that bookmark
- Preserve the last known good local package (if any)
- Mark `contentState` as `DIRTY` with a failure reason
- Continue processing remaining bookmarks in the batch

On stream-level failure (connection dropped, invalid boundary):

- Any fully-assembled bookmarks processed before the failure should be committed
- Remaining bookmarks should be marked `DIRTY` for retry

## Rollout Strategy

Implement `v0.12.0` in stages, treating this document as one architectural destination.

### Stage 1: Multipart client and metadata migration

- Land the `MultipartSyncParser` and `MultipartSyncClient`
- Add the `SyncRequest` model and `syncBookmarks()` Retrofit method
- Add Room schema migration (version 11): drop `article_content`, add `content_package` and
  `content_resource` tables
- Migrate metadata reloads to `POST /bookmarks/sync` with `with_json = true`
- Keep the old paginated metadata path as a short-lived fallback switch while the new path is
  validated
- Unit test the parser thoroughly against real and synthetic multipart responses

### Stage 2: On-demand content packages

- Adopt multipart package downloads for single-bookmark Article/Picture requests
- Implement per-bookmark file storage and staging
- Implement `WebViewAssetLoader` integration for local resource serving
- Update `loadDataWithBaseURL()` to use per-bookmark local base URLs
- Implement picture wrapper HTML generation
- Verify that offline article inline images and picture detail now work reliably

### Stage 3: Automatic and date-range content packages

- Move `BatchArticleLoadWorker` and `DateRangeContentSyncWorker` onto the shared multipart
  package transport
- Update DAO queries to include Picture bookmarks in content-eligible queries (currently
  `hasArticle = 1` only)
- Limit background content batches to Article/Picture bookmarks eligible for offline packages
- Implement `sourceUpdated` freshness check to skip redundant re-downloads
- Add storage usage display and "Clear all offline content" to Sync Settings

### Stage 3a: Offline annotation support

- Register `CachedAnnotationEntity` in Room `@Database` entities and add schema migration
  (version 12)
- Create `CachedAnnotationDao` with queries for get, replace, and observe by bookmark ID
- Build `AnnotationHtmlParser` to extract annotation metadata from `<rd-annotation>` elements
  in article HTML
- Call the parser and persist results during `ContentPackageManager.commitPackage()` in the
  same Room transaction
- Make `BookmarkDetailViewModel.fetchAnnotations()` offline-first: load from local cache,
  optionally merge with API when online
- Skip legacy `syncAnnotationsIfNeeded` for content-package bookmarks to eliminate the
  annotation color flash
- Update annotation-driven refresh to use multipart content package re-download instead of
  legacy `GET /bookmarks/{id}/article`
- Verify scroll-to-annotation and tap-to-edit work correctly with offline content packages

### Stage 4: Remove legacy content/detail fetches and fix bootstrap sync

Completed scope:

- Removed `resolveOmitDescription()` and its `GET /bookmarks/{id}` detail-enrichment request
- Removed the old paginated `GET /bookmarks?updated_since=` metadata reload path from
  `LoadBookmarksUseCase`
- Fixed initial/full-sync bootstrap regression via Option D (see field note below)
- `LoadArticleUseCase` retained as resilience fallback in on-demand content fetch only
  (see Retained Legacy Endpoints section)
- Video bookmarks included in content sync pipeline (metadata + text content, not video bytes)
- Embed preservation across sync paths (list endpoint nulls don't overwrite multipart values)
- Reader progress bar: deterministic bar for on-demand fetch, indeterminate for already-downloaded
  content during WebView render

## Testing Requirements

### Parser and transport tests

- Multipart boundary extraction from Content-Type header
- Parser grouping by `Bookmark-Id` across interleaved parts
- Mixed `json` / `html` / `resource` assembly into `BookmarkSyncPackage`
- Streaming resource parts to disk without memory buffering
- Malformed-stream failure behavior (truncated stream, bad boundary)
- Unknown `Type` values are skipped gracefully
- Missing `Bookmark-Id` on a part is skipped gracefully
- Empty `id` list guard prevents calling `POST /bookmarks/sync`
- Batch size chunking for both metadata-only and content-package profiles

### Persistence tests

- Metadata-only multipart writes preserving local pending-action merge rules
- Package manifest (`content_package`) persistence and update
- Resource index (`content_resource`) persistence and update
- Per-bookmark atomic replace: staging → commit → old directory cleanup
- Per-bookmark atomic replace: failure preserves last known good package
- `contentState` transitions: `NOT_ATTEMPTED` → `DOWNLOADED`, failure → `DIRTY`

### Reader tests

- Article reader rendering with local base URL and offline inline images via `WebViewAssetLoader`
- Picture reader rendering with generated wrapper HTML when server HTML is absent
- Picture reader rendering with server-provided HTML when present
- Video fallback behavior remaining honest about online-only playback
- `omitDescription` UI suppression behavior driven from multipart JSON payloads
- Fallback to network rendering when no local package exists

### Annotation tests

- `AnnotationHtmlParser` extracts annotation ID, color, note, and text from `<rd-annotation>`
  elements
- Parser handles missing attributes gracefully (missing color defaults to `yellow`, missing
  note is null)
- Parser handles HTML with no annotations (returns empty list)
- Parser handles nested/overlapping annotations
- `CachedAnnotationDao` round-trip: replace and retrieve annotations for a bookmark
- `ContentPackageManager.commitPackage()` persists extracted annotations in the same transaction
- `fetchAnnotations` returns cached annotations when offline
- `fetchAnnotations` merges API data with cached color/note when online
- Legacy annotation sync is skipped for content-package bookmarks
- No annotation color flash on initial article load from content package

### Worker/use-case tests

- On-demand content sync using multipart transport (single bookmark)
- Automatic background content sync batching eligible Article/Picture bookmarks
- Date-range content sync batching eligible Article/Picture bookmarks
- `sourceUpdated` freshness check skipping redundant content downloads
- Retry/failure state transitions for partial package failures
- Annotation-driven content refresh through multipart transport
- Annotation cache populated during content package commit
- Annotation cache cleared when content package is cleared

### Storage management tests

- Storage usage calculation
- "Clear all offline content" cleans files, tables, and resets content state
- Confirmation dialog before clearing

### Migration tests

- Room migration from version 10 → 11 (drop `article_content`, add new tables)
- Bookmark metadata preserved across migration
- Content state reset to `NOT_ATTEMPTED` after migration

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

6. **Content re-download is skipped for unchanged bookmarks.**
   - Download content for a bookmark. Trigger another sync where the bookmark has not changed.
   - Expected result: no content package is re-requested (observable via network logging).

7. **Storage management works.**
   - Download content for several bookmarks. Navigate to Sync Settings.
   - Expected result: storage usage is displayed. "Clear all offline content" removes all
     downloaded content and resets content state.

8. **Annotation refresh triggers package re-download.**
   - Add or modify an annotation on a downloaded article.
   - Expected result: article HTML is refreshed via multipart transport, not via legacy
     `GET /bookmarks/{id}/article`.

9. **Highlights work fully offline.**
   - Download an article that has annotations. Disable network and reopen it.
   - Open the highlights bottom sheet from the overflow menu.
   - Expected result: all highlights appear with correct colors and text.

10. **Highlight tap-to-edit works offline.**
    - With network disabled, tap a highlight in the article text.
    - Expected result: the annotation edit sheet opens with correct color and note.

11. **Highlight scroll-from-sheet works offline.**
    - With network disabled, open highlights sheet, tap an annotation.
    - Expected result: the sheet closes and the reader scrolls to the highlight position.

12. **No annotation color flash on content-package articles.**
    - Open a content-package article with annotations while online.
    - Expected result: highlights render immediately with correct colors; no flash to yellow.

## Implementation Status

### Stage 1: Multipart client and metadata migration — Complete

- `MultipartSyncParser` and `MultipartSyncClient` landed and tested
- `SyncRequest` model and `syncBookmarks()` Retrofit method in place
- Room schema migration (version 11): dropped `article_content`, added `content_package` and
  `content_resource` tables
- Metadata reloads migrated to `POST /bookmarks/sync` with `with_json = true`
- Old paginated metadata path (`GET /bookmarks?updated_since=`) removed from normal sync

### Stage 2: On-demand content packages — Complete

- Multipart package downloads for single-bookmark Article/Picture requests
- Per-bookmark file storage with staging directory and atomic replace
- `WebViewAssetLoader` integration for local resource serving
- `loadDataWithBaseURL()` uses per-bookmark local base URLs
- Picture wrapper HTML generation for bookmarks without server HTML
- Offline article inline images and picture detail verified working

### Stage 3: Automatic and date-range content packages — Complete

- `BatchArticleLoadWorker` and `DateRangeContentSyncWorker` use multipart package transport
- DAO queries include Picture and Video bookmarks in content-eligible queries
- `sourceUpdated` freshness check skips redundant re-downloads
- Storage usage display and “Clear all offline content” in Sync Settings

### Stage 3a: Offline annotation support — Complete

- `CachedAnnotationEntity` in Room with schema migration (version 12)
- `CachedAnnotationDao` with get, replace, and observe queries
- `AnnotationHtmlParser` extracts metadata from `<rd-annotation>` elements
- Annotations persisted during `ContentPackageManager.commitPackage()` in same transaction
- `fetchAnnotations` is offline-first: local cache → optional API merge
- Legacy `syncAnnotationsIfNeeded` skipped for content-package bookmarks
- Annotation-driven refresh uses multipart content package re-download

### Stage 4: Remove legacy content/detail fetches — Complete

- Removed `resolveOmitDescription()` and its `GET /bookmarks/{id}` detail-enrichment request
- Removed old paginated `GET /bookmarks?updated_since=` metadata reload path
- `LoadArticleUseCase` retained as resilience fallback only (see Retained Legacy Endpoints below)
- Video bookmarks now participate in content sync (metadata via multipart JSON, embed preserved)

## Retained Legacy Endpoints

Three `GET`-based endpoint usages remain in the codebase. Each was evaluated for removal and
determined to be necessary because the multipart sync endpoint cannot fulfill their function.

### 1. `LoadArticleUseCase` as on-demand content fallback

**Endpoint:** `GET /bookmarks/{id}/article`

**Usage:** `BookmarkDetailViewModel.fetchContentOnDemand()` falls back to `LoadArticleUseCase`
when `LoadContentPackageUseCase` returns a permanent or transient failure.

**Why it stays:** This is a deliberate resilience fallback, not a leftover. If the multipart sync
endpoint fails (server error, network issue, Readeck version mismatch), the legacy article
endpoint gives the user a second chance to load content. Removing it would create a single point
of failure on the sync endpoint. For a reader app where content availability is the core value
proposition, this tradeoff is correct.

### 2. Single-bookmark polling during `createBookmark`

**Endpoint:** `GET /bookmarks/{id}`

**Usage:** `BookmarkRepositoryImpl.createBookmark()` fetches the newly created bookmark and, if
the server is still parsing it (`state == LOADING`), polls until `state` transitions to `LOADED`.

**Why it stays:** This is a creation-time polling loop for a bookmark the server has not finished
processing. The sync endpoint requires you to provide specific IDs to retrieve data you already
know about — but here the point is to discover when the server has finished producing the data.
The sync endpoint would return the same incomplete state. Switching transports would add
complexity for zero benefit.

### 3. Server error flag refresh

**Endpoint:** `GET /bookmarks?has_errors=true`

**Usage:** `LoadBookmarksUseCase.refreshServerErrorFlags()` pages through all bookmarks with
server-side errors to populate the local error flag set.

**Why it stays:** The sync endpoint requires specific bookmark IDs in the request body. There is
no way to ask it “give me all bookmarks with server errors” — that is a query/filter operation,
not a sync operation. The `has_errors=true` parameter is a server-side filter with no multipart
equivalent. This is the only way to discover which bookmarks have errors.

## Field Note: Initial Sync Regression (Stage 4)

**Context (2026-03-21):** After removing the legacy paginated metadata reload, initial app open
showed **zero bookmarks**. Log file: `debug/multipart/mydeck-logs-2026-03-21-214800.txt`.

**Root cause:** Stage 4 removed the paginated metadata transport, but all bootstrap/full-sync
paths relied on it. When `updatedIds == null`, `LoadBookmarksUseCase` returned success without
fetching, which was correct for “no updates” but incorrect for first sync. Every sync path that
seeds the DB was broken; only incremental delta updates (which require bookmarks to already exist)
worked.

**Resolution: Option D — Persist metadata from the full-sync paging response.**

Options A/B/C all involved double-fetching: the full-sync paging loop already downloads complete
`BookmarkDto` objects for deletion detection but discarded everything except IDs. All three
options would have re-fetched the same data via multipart POST.

Option D persists metadata in the same loop that collects IDs. Concrete changes:

- `performFullSync()` maps `BookmarkDto` → domain → `insertBookmarks()` during the existing
  paging loop (alongside ID extraction for deletion detection). Zero additional network requests.
- `FullSyncWorker` skips `loadBookmarksUseCase.execute()` when full sync already persisted
  metadata. Still runs `enqueueContentSyncIfNeeded()` and `refreshServerErrorFlags()`.
- `LoadBookmarksWorker` INITIAL trigger calls `performFullSync()` directly instead of
  `loadBookmarksUseCase.execute()`, giving users bookmarks immediately upon first login.
- `LoadBookmarksUseCase.execute()` no longer accepts `updatedIds == null` as a silent no-op.

This does not violate the spec's intent: the spec targets replacing `GET /bookmarks?updated_since=`
(the incremental metadata reload). `performFullSync` uses `GET /bookmarks` (no `updated_since`)
for enumeration and deletion detection — a fundamentally different operation with no multipart
equivalent, since `POST /bookmarks/sync` requires you to already know which IDs to request.
Multipart POST remains the primary transport for normal delta metadata updates.

**Note on `embed`:** The list endpoint does not include `embed` or `embed_domain` in its response
(confirmed via API testing), but the multipart sync JSON does. `insertBookmarks` preserves
existing non-null `embed`/`embedHostname` values when incoming data has nulls, so embed data is
populated on the first multipart interaction (delta sync or content package fetch) and never
overwritten by the list endpoint's null values. A `refreshBookmarkMetadata()` fallback via
`GET /bookmarks/{id}` covers the gap when a video bookmark is opened before any multipart path
has touched it.

## Field Note: Video Content Package Inclusion (Stage 4)

**Context (2026-03-21):** Video bookmarks were excluded from automatic/manual/date-range content
sync DAO queries and from `LoadContentPackageUseCase`. This meant video bookmarks with
`has_article = true` (i.e., server-extracted transcript/text content) could never receive content
packages.

**Resolution:** Removed the `type != 'video'` exclusion from DAO content-eligible queries and
`LoadContentPackageUseCase`. Video bookmarks now receive metadata via multipart JSON (which
includes `embed` and `embed_domain`), and those with article content can receive content packages
like any other type. The spec's non-goal of “offline video playback” remains respected — this
downloads text/transcript content, not video bytes. The embed iframe remains online-only, with an
offline placeholder shown when the network is unavailable.

## Decision Summary

`v0.12.0` adopts the sync API multipart transport as the primary retrieval architecture, including
metadata JSON, HTML entry documents, and local resource packages for offline-capable Article and
Picture bookmarks.

This is a clean-break release for content storage — no backward-compatible migration from
`v0.11.1` content tables.

This solves both strategic issues at once:

- full offline reading for Articles and Pictures (text + images)
- a durable, transport-native solution for `omit_description`
