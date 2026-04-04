# Spec: Sync Error Parity and Media Reader Follow-Up

## Superseded Status

This document is superseded as the forward-looking sync/content plan by:

- `docs/specs/sync-multipart-adoption-spec.md`

Keep this file as a historical record of the `v0.11.1` follow-up fixes that landed before the
planned multipart architecture work in `v0.12.0`.

## Summary

This spec captures the concrete follow-up work identified during review of the `debug/sync2`
artifacts.

The artifact set showed that the current branch's sync pipeline is mostly behaving as
intended:

- app-open and pull-to-refresh metadata sync both run normally
- delta cursors advance only after successful metadata reloads
- automatic content sync is enqueued correctly
- bookmarks that Readeck processed without extractable article HTML are being persisted as
  local `PERMANENT_NO_CONTENT`

The remaining issues are not primarily broken sync scheduling. They are a combination of:

1. **server/client error-filter mismatch**
2. **media-reader fallback gaps when no cached reader payload exists**
3. **article-centric content-state semantics being stretched to cover media bookmarks**

This follow-up keeps the overall sync architecture intact while fixing the highest-value
behavior gaps.

## Findings from `debug/sync2`

### 1. Most problematic bookmarks are upstream no-content cases

The representative debug exports showed bookmarks that Readeck had already processed but for
which extracted article HTML was unavailable.

Common patterns included:

- article bookmarks with `loaded=true` and `has_article=false`
- video bookmarks with an `embed` payload but no article HTML
- picture bookmarks with image metadata but no article HTML
- local MyDeck state persisted as `PERMANENT_NO_CONTENT`

These cases are consistent with upstream failures such as:

- 404 / page removed
- broken source URLs
- permission or anti-bot restrictions
- generic extraction failures

In other words, the pipeline is mostly surfacing the *post-failure state* rather than a raw
HTTP error line inside MyDeck itself.

### 2. `With errors` does not currently mean the same thing as Readeck's `with_error`

Readeck's server-side `with_error` filter matches bookmarks where either:

- `state = ERROR`
- or the bookmark `errors[]` array is non-empty

MyDeck currently approximates that concept as:

- `state = ERROR`
- or local `contentState IN (DIRTY, PERMANENT_NO_CONTENT)`

That is useful for local troubleshooting, but it is not Readeck parity and it causes MyDeck to
miss bookmarks that the server considers errored because they carry extraction errors while
still being in `state = LOADED`.

### 3. Media bookmarks can remain stuck in reader-mode loading when no cached payload exists

The detail screen already auto-switches back to web/original mode when content fetch fails and
no reader payload exists. However, article bookmarks and media bookmarks were not using exactly
the same failure-state initialization.

For video and picture bookmarks, `PERMANENT_NO_CONTENT` could still leave the detail screen in
reader-mode loading because `ContentLoadState` was not explicitly set to `Failed` during
initialization.

## Goals

1. Make MyDeck's `With errors` filter match Readeck's server-side `with_error` meaning.
2. Preserve the server bookmark `errors` list locally for filtering, diagnostics, and debug
   export visibility.
3. Ensure video and picture bookmarks with no cached reader payload fall back cleanly instead of
   leaving the reader on a spinner.
4. Keep the current sync architecture and deferred-content model intact.

## Non-Goals

- Replacing the existing metadata sync transport
- Implementing multipart `POST /bookmarks/sync`
- Downloading full offline media assets
- Redesigning sync statistics into a generalized reader-payload model in this phase

## Proposed Changes

### A. Persist server bookmark errors locally

Add bookmark `errors` to:

- `BookmarkDto`
- `Bookmark`
- `BookmarkEntity`
- the Room migration path
- bookmark mapping code

This allows MyDeck to preserve the server's error classification rather than attempting to infer
it from local content-state fields.

### B. Update `With errors` filtering to match Readeck

Change the local filter implementation from:

- `state = ERROR OR contentState IN (DIRTY, PERMANENT_NO_CONTENT)`

To:

- `state = ERROR OR persistedServerErrors is not empty`

This aligns the filter chip and sheet behavior with Readeck's own `with_error` semantics.

Local content failures remain visible elsewhere via debug info and content-state-specific UI, but
no longer redefine the meaning of the `With errors` filter.

### C. Initialize media no-content state as a real failure

During bookmark detail initialization for video and picture bookmarks:

- if `contentState == PERMANENT_NO_CONTENT`, set `ContentLoadState.Failed`
- keep `canRetry = false`
- use the stored `contentFailureReason` when present

This ensures the existing reader fallback logic can switch to web/original mode instead of
leaving the user on a loading overlay.

## Product Behavior After This Change

### Filtering

- `With errors = Yes` shows bookmarks that Readeck reports as errored
- `With errors = No` excludes bookmarks that Readeck reports as errored
- local no-content bookmarks are no longer automatically treated as server-error bookmarks
  unless Readeck itself reported errors for them

### Reading behavior

- if an article, video, or picture bookmark has usable reader content, reader mode behaves as it
  does today
- if a media bookmark has no cached reader payload and is already known to have no content,
  reader mode does not remain on a spinner; the screen falls back cleanly

## Migration

A Room migration is required to add the persisted `errors` column.

Backfill behavior:

- existing rows default to an empty error list
- future metadata refreshes populate the column from Readeck responses

## Risks

- Existing users who relied on `With errors` as a proxy for local content problems will see a
  narrower result set after parity is restored.
- Old bookmarks remain without server error detail until they are refreshed from metadata sync.
- The content-state model is still article-centric; this change fixes the most visible media UX
  issue without fully redefining sync statistics.

## Validation

Implementation should include:

- repository/mapping coverage for persisted bookmark errors
- filter coverage for the new `With errors` semantics
- detail-screen/viewmodel coverage for media `PERMANENT_NO_CONTENT` initialization

## Follow-Up Work

Potential later work, not included in this spec:

1. Add a separate local filter for content-sync failures (`DIRTY` / `PERMANENT_NO_CONTENT`)
2. Revisit sync statistics so media reader preparation can be counted independently from article
   HTML downloads
3. Explore metadata/embed preparation for video and picture bookmarks during automatic and
   date-range sync without overloading the meaning of `contentDownloaded`
