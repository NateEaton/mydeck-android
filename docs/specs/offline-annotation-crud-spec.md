# Spec: Offline Annotation CRUD

## Status

Proposal — not yet scheduled for implementation.

## Context

### Current state (v0.12.0)

Annotation **reading** works offline: the `AnnotationHtmlEnricher` patches bare `<rd-annotation>`
tags from the Readeck multipart sync endpoint with full attributes (`id`, `data-annotation-color`,
`data-annotation-id-value`) fetched from the annotations REST API at content-package commit time.
The `AnnotationHtmlParser` then extracts these into `cached_annotation` rows for offline listing,
scroll-to-annotation, and tap-to-edit display.

Annotation **mutations** (create, update color, delete) require network connectivity. When offline,
the save/delete action shows a snackbar: "Highlights can't be changed while offline."

### Server-side limitation discovered during v0.12.0

The Readeck `POST /bookmarks/sync` multipart endpoint returns `<rd-annotation>` elements with
**all attributes stripped** — bare `<rd-annotation>text</rd-annotation>` tags. Only the legacy
`GET /bookmarks/{id}/article` endpoint returns fully attributed annotation markup. The
`GET /bookmarks/{id}/annotations` REST endpoint **does** return `color` and `note` fields (contrary
to the original sync-multipart-adoption-spec which stated it did not).

This was worked around in v0.12.0 via `AnnotationHtmlEnricher`, which fetches annotation metadata
from the REST API during content-package commit and patches the bare tags before writing to disk.

### Why offline annotation CRUD is non-trivial

Unlike bookmark metadata mutations (favorite, archive, labels) which only require a DB column
change and a queued API call, annotation mutations involve **three coupled state stores**:

1. **`cached_annotation` table** — drives the highlights bottom sheet listing
2. **On-disk `index.html`** — drives the WebView rendering (highlight visibility and color)
3. **Server state** — the authoritative source, synced via the pending action queue

For bookmark metadata, the Room database is the single local source of truth and the UI reactively
observes it. For annotations, the WebView renders from a separate HTML file on disk, so optimistic
local updates must modify both the database AND the HTML file atomically.

## Design Considerations

### Create is the hardest operation

Creating an annotation offline requires:

1. **Generating a temporary local ID** — the server normally assigns the annotation ID. The client
   would need a locally-generated placeholder (e.g., `local-<UUID>`) that gets replaced when the
   server responds.

2. **Manipulating the HTML DOM from Kotlin** — the selection data provides XPath selectors and text
   offsets (`start_selector`, `start_offset`, `end_selector`, `end_offset`). Wrapping the
   corresponding text range in an `<rd-annotation>` element requires parsing the HTML, navigating
   the XPath, counting character offsets, and inserting the wrapper tags — all without a browser DOM
   engine. This is error-prone and fragile.

3. **Reconciling on sync** — when connectivity returns and the server processes the create, the
   server returns its own annotation ID and re-renders the article HTML. The locally-inserted
   annotation (with temp ID) must be replaced by the server's version. If the article content
   changed between the offline create and the sync, the selectors/offsets may no longer be valid.

### Update (color change) is straightforward

Updating an annotation's color requires:

1. Updating `cached_annotation.color` in Room
2. Replacing `data-annotation-color="old"` with `data-annotation-color="new"` in `index.html`
   (a targeted string replacement keyed by the annotation's known ID)
3. Queuing a `PATCH /bookmarks/{id}/annotations/{annotationId}` pending action

The HTML modification is safe because the annotation element's `id` attribute provides a unique
anchor for the replacement. No XPath navigation or offset counting is needed.

### Delete is moderate

Deleting an annotation requires:

1. Removing the row from `cached_annotation`
2. Unwrapping the `<rd-annotation id="annotation-{id}" ...>inner content</rd-annotation>` element
   in `index.html`, replacing it with just `inner content`
3. Queuing a `DELETE /bookmarks/{id}/annotations/{annotationId}` pending action

The HTML modification is a targeted regex replacement keyed by the annotation ID. Slightly more
complex than color update but still reliable because the ID-based selector is unambiguous.

## Proposed Implementation

### Phase 1: Update and Delete (recommended first)

Add two new `ActionType` values:

```kotlin
enum class ActionType {
    // ... existing values ...
    UPDATE_ANNOTATION_COLOR,
    DELETE_ANNOTATION
}
```

#### Payloads

```kotlin
@Serializable
data class UpdateAnnotationPayload(
    val annotationId: String,
    val color: String
)

@Serializable
data class DeleteAnnotationPayload(
    val annotationId: String
)
```

#### Optimistic local updates

For **update color**:
- Update `cached_annotation` row: set `color = newColor`
- Read `index.html`, regex-replace the `data-annotation-color` attribute on the element with
  matching `data-annotation-id-value`, write back
- Reload WebView content from the updated file
- Queue pending action

For **delete**:
- Delete `cached_annotation` row
- Read `index.html`, regex-unwrap the `<rd-annotation>` element with matching ID (replace
  `<rd-annotation id="annotation-{id}" ...>content</rd-annotation>` with `content`), write back
- Reload WebView content from the updated file
- Queue pending action

#### Sync processing

In `processPendingAction`:

```kotlin
ActionType.UPDATE_ANNOTATION_COLOR -> {
    val payload = json.decodeFromString<UpdateAnnotationPayload>(action.payload!!)
    val response = readeckApi.updateAnnotation(
        action.bookmarkId, payload.annotationId, UpdateAnnotationDto(payload.color)
    )
    handleSyncApiResponse(action.bookmarkId, response)
    // Re-download content package to get server-rendered HTML
    loadContentPackageUseCase.executeForceRefresh(action.bookmarkId)
}

ActionType.DELETE_ANNOTATION -> {
    val payload = json.decodeFromString<DeleteAnnotationPayload>(action.payload!!)
    val response = readeckApi.deleteAnnotation(action.bookmarkId, payload.annotationId)
    handleSyncApiResponse(action.bookmarkId, response)
    loadContentPackageUseCase.executeForceRefresh(action.bookmarkId)
}
```

#### Content package refresh after sync

After the pending action syncs, `executeForceRefresh` re-downloads the content package with
server-rendered HTML (which now reflects the mutation). This ensures the local HTML converges
with server state even if the local regex manipulation had minor formatting differences.

### Phase 2: Create (deferred)

Offline annotation creation is deferred because it requires:

1. A robust Kotlin-side HTML DOM manipulation library (or a custom XPath+offset text wrapper)
2. Temporary local ID generation and reconciliation logic
3. Handling of stale selectors/offsets if the article is re-processed server-side

A possible future approach:
- Use the WebView itself to perform the DOM manipulation via JavaScript (inject the annotation
  element using the same selectors/offsets the server uses), then extract the modified HTML back
  to Kotlin via `evaluateJavascript`
- This leverages the browser's DOM engine instead of reimplementing XPath navigation in Kotlin
- The extracted HTML is written to `index.html` and the temp annotation is added to
  `cached_annotation` with a `local-` prefixed ID
- On sync, the create API call returns the server-assigned ID; the local state is updated and
  a content package refresh replaces the locally-modified HTML with the server version

## Testing

### Phase 1 validation

- Update color offline: change color, verify WebView re-renders with new color, verify bottom
  sheet shows updated color, go online, verify server receives the update
- Delete offline: delete highlight, verify it disappears from WebView and bottom sheet, go
  online, verify server receives the delete
- Conflict: update color offline, then sync — verify content package refresh brings server HTML
- Queue ordering: multiple offline mutations on different annotations, verify all sync correctly
- Error handling: server rejects mutation (e.g., annotation already deleted) — verify graceful
  handling without data loss

## Open Questions

1. **Should the annotation edit sheet stay open after an offline update/delete?** Currently it
   dismisses on success. For offline operations the local update is instant, so dismissing
   immediately seems correct.

2. **Should there be a visual indicator for pending annotation mutations?** Bookmark metadata
   doesn't show pending state in the UI. Annotations could follow the same pattern (no indicator).

3. **Is Phase 2 (offline create) worth the complexity?** If users primarily create annotations
   while online and only need offline reading/viewing, Phase 1 may be sufficient indefinitely.
