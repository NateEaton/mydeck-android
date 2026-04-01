# Readeck API Documentation Gaps

This document captures undocumented or incorrectly documented API behaviors discovered
during the development of MyDeck Android v0.12.0 (multipart sync adoption branch).
It is intended as a basis for a documentation issue submission to the Readeck maintainers.

Each finding includes: what was expected from documentation, what the actual behavior is,
and how it was discovered.

---

## Issue 1: `POST /bookmarks/sync` — Request and Response Format Largely Undocumented

### Scope
The `POST /bookmarks/sync` endpoint is the primary bulk content retrieval path for the API,
but its request schema and response format are absent from the OpenAPI spec.

### Request body fields

The following request body fields are not documented:

| Field | Type | Observed Behavior |
|---|---|---|
| `id` | `string[]` | Required. List of bookmark IDs to retrieve. **When `id` is omitted entirely, the server appears to return data for all bookmarks** — this behavior is dangerous for clients and is not documented. |
| `with_json` | `boolean` | When `true`, the server includes a `type: json` multipart part per bookmark containing the full bookmark metadata DTO. When `false` or omitted, no JSON parts are returned. |
| `with_html` | `boolean` | When `true`, the server includes a `type: html` multipart part per bookmark containing the article HTML. |
| `with_resources` | `boolean` | When `true`, the server includes `type: resource` multipart parts for inline images and other assets. |
| `resource_prefix` | `string` | When set (e.g., `"."`), the server prepends this value to resource file paths in the HTML's `src` attributes, producing relative URLs. When omitted, resource URLs in HTML are absolute. This behavior is entirely undocumented. |

### Response format

The response is `multipart/mixed` with a `Content-Type` header of the form:
```
Content-Type: multipart/mixed; boundary=<boundary-string>
```

The multipart structure is not documented anywhere. From inspection:

- Parts are grouped by bookmark. Each part has a `Bookmark-Id` header identifying which
  bookmark it belongs to.
- Each part has a `Type` header identifying its content: `json`, `html`, or `resource`.
- Resource parts have additional headers: `Path` (relative path for the file), `Filename`,
  `Group` (e.g., `image`), `Content-Type`, and `Content-Length`.
- Parts for different bookmarks may be interleaved in the response.
- The order of parts within a bookmark group is not guaranteed.

**Suggested documentation additions:**
- Full request body schema in OpenAPI
- Multipart response format spec, including all part headers and their semantics
- Explicit documentation that omitting `id` returns all bookmarks (or that `id` is required)
- Documentation of `resource_prefix` behavior and the effect on HTML `src` attributes

---

## Issue 2: `<rd-annotation>` Attribute Behavior Differs Between Endpoints

### Observation

The `GET /bookmarks/{id}/article` endpoint returns article HTML where `<rd-annotation>`
elements contain full attributes:

```html
<rd-annotation
  id="annotation-{uuid}"
  data-annotation-id-value="{uuid}"
  data-annotation-color="yellow"
  title="optional note text"
  data-annotation-note="true">
  highlighted text
</rd-annotation>
```

The `POST /bookmarks/sync` endpoint returns the same article HTML but with **all
`<rd-annotation>` attributes stripped**:

```html
<rd-annotation>highlighted text</rd-annotation>
```

This behavioral difference is not documented. The `<rd-annotation>` element itself is not
documented anywhere in the API reference.

### Impact on clients

A client that stores HTML from the sync endpoint cannot display annotation colors, enable
tap-to-edit, or scroll to a specific annotation without either:
- Making a separate `GET /bookmarks/{id}/annotations` call per bookmark and matching
  annotations to HTML positions by text content (fragile — text match can fail for
  annotations with identical or near-identical text), or
- Falling back to the legacy `GET /bookmarks/{id}/article` endpoint to get attributed HTML.

Both workarounds were required in MyDeck v0.12.0.

### Suggested documentation additions

- Document the `<rd-annotation>` element and its attributes
- Explicitly note that `POST /bookmarks/sync` returns bare `<rd-annotation>` tags (and
  whether this is intentional or a known limitation — see improvement proposals)
- Document the `GET /bookmarks/{id}/annotations` response fields (`color`, `note`,
  `start_selector`, `start_offset`, `end_selector`, `end_offset`, `text`)

---

## Issue 3: `embed_domain` Field Not in OpenAPI Spec

### Observation

Both `GET /bookmarks/{id}` and `POST /bookmarks/sync` (JSON part) return a field
`"embed_domain"` in the bookmark object — for example:

```json
{
  "id": "...",
  "embed_domain": "youtube.com",
  ...
}
```

This field is completely absent from the OpenAPI spec. It was initially treated as
`embed_hostname` based on inference, which caused it to always deserialize as `null`
until the correct field name was confirmed via direct `curl` inspection.

### Suggested documentation addition

- Add `embed_domain` to the bookmark object schema in the OpenAPI spec
- Clarify which bookmark types can have a non-null `embed_domain` (video/embed bookmarks)
- Clarify whether `embed_domain` is the same as or different from any `embed_hostname` that
  may appear in other contexts

---

## Issue 4: `omit_description` Field — Inconsistent Presence Across Endpoints

### Observation

The `omit_description` field controls whether the bookmark's description text should be
suppressed in the reading view (because it duplicates the article's opening paragraph).

Field presence by endpoint:

| Endpoint | Returns `omit_description`? |
|---|---|
| `GET /bookmarks` (list) | **No** — field is absent |
| `GET /bookmarks/{id}` (single) | **Yes** |
| `POST /bookmarks/sync` (JSON part) | **Yes** |

The fact that the list endpoint omits this field is not documented. A client that relies
on the list endpoint for metadata will never receive this value and must make additional
requests to obtain it.

### Impact on clients

Clients must either:
- Make a per-bookmark `GET /bookmarks/{id}` request to retrieve `omit_description`
  (creates N+1 requests), or
- Obtain it through a multipart sync content package fetch (which may not have happened
  yet for a given bookmark)

In MyDeck v0.12.0, this required a dedicated field-preservation strategy where the
value is retained from whatever source last provided it and never overwritten by
list-endpoint syncs that don't carry the field.

### Suggested documentation additions

- Explicitly document which fields are present in the list endpoint response vs. the
  single-bookmark endpoint
- If `omit_description` exclusion from the list is intentional, document why
- Consider adding it to the list endpoint response (see improvement proposals)

---

## Issue 5: `GET /bookmarks` List Endpoint Missing Several Fields Present in Sync JSON

### Observation

The `POST /bookmarks/sync` JSON part returns a richer bookmark object than
`GET /bookmarks`. Fields confirmed missing from the list endpoint:

| Field | In list endpoint? | In sync JSON? | In single-bookmark GET? |
|---|---|---|---|
| `omit_description` | No | Yes | Yes |
| `embed_domain` | No | Yes | Yes |
| `embed` (iframe HTML) | No | Yes | Yes |

The list endpoint is documented but the field coverage differences between representations
are not documented anywhere.

### Suggested documentation addition

- Add a field-coverage comparison table to the API reference showing which fields are
  available in each endpoint's response

---

## Issue 6: `GET /bookmarks/sync` vs `POST /bookmarks/sync` — Shared Path, Different Semantics

### Observation

Two endpoints share the `/bookmarks/sync` path:

- `GET /bookmarks/sync?since=<timestamp>` — returns a list of IDs for bookmarks changed
  since the given timestamp (delta sync signal)
- `POST /bookmarks/sync` (JSON body with `id` array) — returns multipart content for
  specific bookmark IDs

These two operations are semantically complementary (use GET to discover which IDs changed,
then POST to retrieve their content), but their relationship and intended combined usage is
not documented. A client developer reading either endpoint in isolation would not understand
they are designed to be used together.

### Suggested documentation addition

- Add a "Sync workflow" or "Offline reading" section that explains the recommended pattern:
  1. `GET /bookmarks/sync?since=` to discover changed IDs
  2. `POST /bookmarks/sync` with those IDs to retrieve updated content packages
- Cross-reference the two endpoints from each other's documentation

---

## Summary of Suggested Documentation Changes

1. Add full request/response schema for `POST /bookmarks/sync` to OpenAPI spec
2. Document `<rd-annotation>` element and attribute format
3. Document the attribute-stripping behavior of sync endpoint HTML vs. article endpoint HTML
4. Add `embed_domain` to the bookmark schema
5. Document `omit_description` field and note which endpoints include/exclude it
6. Add a field-coverage table comparing list, single, and sync JSON representations
7. Add a sync workflow guide explaining the GET+POST sync pattern
8. Explicitly document the "omit `id` returns all" behavior of POST sync, or enforce `id` as required
