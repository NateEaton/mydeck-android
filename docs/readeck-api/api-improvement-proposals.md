# Readeck API Improvement Proposals

This document captures compromises made in MyDeck Android v0.12.0 due to limitations in
the current Readeck API, and proposes specific changes that would improve the API's support
for mobile offline reading clients.

Each proposal includes: the current limitation, the workaround required in v0.12.0, the
proposed change, and the expected benefit.

---

## Proposal 1: Include Annotation Attributes in Multipart Sync HTML

### Current limitation

The `POST /bookmarks/sync` endpoint returns article HTML where all `<rd-annotation>` elements
have their attributes stripped:

```html
<!-- What sync returns -->
<rd-annotation>highlighted text</rd-annotation>

<!-- What GET /bookmarks/{id}/article returns -->
<rd-annotation
  id="annotation-{uuid}"
  data-annotation-id-value="{uuid}"
  data-annotation-color="yellow">
  highlighted text
</rd-annotation>
```

### Workaround required in v0.12.0

Two separate workaround paths were required:

1. **Client-side enrichment:** After receiving sync HTML, MyDeck fetches
   `GET /bookmarks/{id}/annotations` and attempts to match bare `<rd-annotation>` tags
   to annotation objects by comparing the tag's inner text against the annotation's `text`
   field. This is fragile — it fails when annotation text contains nested HTML tags, when
   two annotations highlight identical text, or when text encoding differences prevent an
   exact match.

2. **Legacy endpoint fallback:** For annotation refresh (after a user adds/edits/deletes an
   annotation), MyDeck preferentially calls `GET /bookmarks/{id}/article` instead of the
   sync endpoint, because it returns fully attributed HTML. This means retaining a legacy
   endpoint that the rest of the architecture has moved away from.

### Proposed change

Include annotation attributes in `<rd-annotation>` elements in the multipart sync HTML
response, matching the behavior of `GET /bookmarks/{id}/article`.

Alternatively, if the attribute-stripping is intentional (e.g., the sync HTML reflects
the server's stored extraction HTML which predates the annotation), consider adding a
`with_annotation_attributes` request parameter that controls whether enrichment is applied
before returning the HTML part.

### Expected benefit

- Eliminates the fragile text-matching enrichment step entirely
- Allows clients to retire the legacy article endpoint for annotation refresh
- Makes offline annotation display reliable for all annotation types

---

## Proposal 2: Add `since` Filter to `POST /bookmarks/sync`

### Current limitation

`POST /bookmarks/sync` requires clients to provide a specific list of bookmark IDs. There is
no way to ask the server for content packages of "everything updated since timestamp T."

The current workflow for a delta content sync is:
1. `GET /bookmarks/sync?since=T` → receive list of changed IDs
2. `POST /bookmarks/sync` with those IDs → receive content packages

This two-step flow has a correctness gap: between step 1 and step 2, additional bookmarks
may be updated. A client that wants a consistent snapshot must either accept this gap or
implement its own retry logic.

### Proposed change

Add an optional `since` parameter to the `POST /bookmarks/sync` request body:

```json
{
  "since": "2026-01-01T00:00:00Z",
  "with_json": true,
  "with_html": true,
  "with_resources": false
}
```

When `since` is provided and `id` is omitted, the server returns content packages for all
bookmarks updated since the given timestamp. When both `id` and `since` are provided, the
server returns packages for the intersection (IDs in the provided list that have been
updated since `since`).

### Expected benefit

- Reduces a two-step delta sync to a single request for clients that want HTML/content
  packages for all recently-changed bookmarks
- Eliminates the race window between ID discovery and content fetch
- Particularly valuable for mobile clients that want to keep offline content fresh in
  background sync without tracking which IDs to fetch

---

## Proposal 3: Include `omit_description` and `embed_domain` in List Endpoint

### Current limitation

`GET /bookmarks` omits `omit_description` and `embed_domain` from its response. These
fields are only available via `GET /bookmarks/{id}` (single) or `POST /bookmarks/sync`
(JSON part).

For `omit_description`: a client that syncs bookmark lists without downloading content
packages will never know whether to suppress the description in its reader view. This
required MyDeck to implement a field-preservation strategy where the value is never
overwritten by list syncs, and to fall back to `GET /bookmarks/{id}` when the value is
unknown.

For `embed_domain`: video bookmarks need this field to display the correct embed source
domain in the UI. Without it in the list endpoint, the domain is unavailable until either
a content package is downloaded or a single-bookmark GET is performed.

### Proposed change

Add `omit_description` and `embed_domain` to the `GET /bookmarks` response object, matching
the fields returned by `GET /bookmarks/{id}`.

### Expected benefit

- Single list sync is sufficient to populate all UI-visible fields
- Eliminates per-bookmark detail fetches for video bookmarks opened before any sync
- Simplifies client data model — no field-origin tracking needed

---

## Proposal 4: Bookmark-Level Error Detail in Multipart Response

### Current limitation

When `POST /bookmarks/sync` cannot produce content for a specific bookmark (e.g., the
bookmark has server-side errors, or has no article content), the server simply omits that
bookmark's parts from the multipart response. The client receives no indication of whether
the bookmark was intentionally excluded or whether an error occurred.

This forced MyDeck to treat any missing bookmark as a transient failure (DIRTY state,
retry later) rather than as a permanent signal (PERMANENT_NO_CONTENT, don't retry).

### Proposed change

Include an error part for bookmarks that could not be served:

```
--boundary
Bookmark-Id: {uuid}
Type: error
Content-Type: application/json

{"reason": "no_article_content", "permanent": true}
--boundary
```

Or alternatively, include a minimal `type: json` part for all requested bookmarks,
with a `has_article: false` field, so the client can distinguish "no content available
(permanent)" from "content fetch failed (transient)."

### Expected benefit

- Clients can immediately mark ineligible bookmarks as `PERMANENT_NO_CONTENT` rather
  than retrying indefinitely
- Reduces unnecessary retry traffic for bookmarks that will never have content
- Gives clients accurate information for UI display ("this bookmark has no article content")

---

## Proposal 5: Explicit Maximum Batch Size Documentation

### Current limitation

The `POST /bookmarks/sync` endpoint accepts an `id` array but its maximum supported size
is undocumented. MyDeck uses a batch size of 10 IDs per request based on empirical testing
during development. It's unclear whether:
- There is a server-enforced maximum
- The server degrades gracefully (returns partial results vs. errors) above some threshold
- The optimal batch size varies by `with_resources` mode (resource-heavy requests likely
  have lower practical limits than metadata-only requests)

### Proposed change

Document the maximum (or recommended) batch size in the API reference. If the server
enforces a maximum, document the error response when exceeded. If no hard limit exists,
document any performance or memory guidance for large batches.

### Expected benefit

- Clients can tune batch sizes with confidence
- Eliminates the need for empirical discovery of limits

---

## Summary

| Proposal | Priority | Complexity (server-side) | Client impact |
|---|---|---|---|
| 1: Annotation attributes in sync HTML | High | Medium — apply existing article enrichment to sync path | Eliminates fragile text-matching, retires legacy endpoint for annotations |
| 2: `since` filter on POST sync | Medium | Medium — filter logic already exists on GET sync | Simplifies delta sync to single request, closes race window |
| 3: `omit_description` + `embed_domain` in list | Medium | Low — add fields to existing serialization | Eliminates per-bookmark detail fetches |
| 4: Error parts in multipart response | Medium | Low — add new part type | Enables accurate permanent-failure detection |
| 5: Document max batch size | Low | None — documentation only | Removes guesswork from client batch tuning |
