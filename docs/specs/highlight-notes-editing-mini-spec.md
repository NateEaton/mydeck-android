# Highlight Notes Editing Mini-Spec

## Problem

MyDeck can display Readeck highlight notes, but cannot create, edit, or clear them.
The reader highlight edit sheet shows existing note text as a disabled read-only
field, and tapping it shows `highlight_note_not_supported`. The original Highlights
nav-drawer spec explicitly left note editing and note creation out of scope.

## Current State

- `docs/openapi-spec.json` documents `PATCH /bookmarks/{id}/annotations/{annotation_id}`
  as a highlight update endpoint, but its `annotationUpdate` schema only lists the
  required `color` field. It does not document `note`.
- `docs/readeck-api/api-documentation-gaps.md` confirms the read-side documentation gap:
  `GET /bookmarks/{id}/article` returns `<rd-annotation>` elements with
  `title="optional note text"` and `data-annotation-note="true"`, and
  `GET /bookmarks/{id}/annotations` has undocumented response fields including
  `color` and `note`.
- `docs/readeck-api/api-improvement-proposals.md` discusses annotation attributes in
  synced HTML, but does not document note creation or update request fields.
- `docs/readeck-api/readeck-api-dump.py` can inspect `GET /bookmarks/{id}/annotations`,
  but it has no helper for creating or patching annotations.
- Runtime app models already include notes:
  - `AnnotationDto.note`
  - `AnnotationSummaryDto.note`
  - domain `Annotation.note`
  - `CachedAnnotationEntity.note`
  - `HighlightSummary.note`
- Article HTML enrichment already writes note metadata as `title="..."` and
  `data-annotation-note="true"` on `<rd-annotation>`.
- HTML parsing already reads `title` back into cached annotations.
- Existing mutation flows update the WebView immediately for color changes, refresh
  cached annotations from the API, and keep cached reader HTML consistent across full
  offline packages and Room text caches.
- The edit sheet is currently available only for article bookmarks in reader mode.
  Video highlight editing is intentionally blocked today.

## Feasibility

Feasibility is **high** for editing existing highlight notes. Live API testing on
2026-05-09 confirmed that Readeck accepts `note` on
`PATCH /bookmarks/{id}/annotations/{annotation_id}` despite both local API
documentation sources being write-side incomplete.

Implementation is straightforward because the app already persists and renders note
data. The main work is UI state, DTO shape, mutation methods, immediate
WebView/cache updates, and documentation/localization.

Creating a new highlight with an initial note still needs confirmation. If POST does
not accept `note`, the app can create the highlight first and immediately PATCH the
returned annotation with the requested note.

## API Confirmation Phase

Confirmed against a real Readeck instance on 2026-05-09:

1. `PATCH /bookmarks/{id}/annotations/{annotation_id}` accepts:
   ```json
   { "color": "yellow", "note": "Some note" }
   ```
2. Clearing a note works with an empty string:
   ```json
   { "color": "yellow", "note": "" }
   ```
3. PATCH returns a JSON body containing `updated` and `annotations`, matching the
   OpenAPI response shape even though the local Retrofit method currently uses
   `Response<Unit>`.

Still to confirm:

1. `POST /bookmarks/{id}/annotations` optionally accepts `note`, or note creation
   must be implemented as create highlight first, then patch note.
2. Whether clearing with `note: null` is accepted. This is optional; empty-string
   clearing is enough for the app.

Implementation can keep `Response<Unit>` if it refreshes from
`getAnnotations(bookmarkId)` after success, but a response DTO could reduce one
network call later.

## Proposed UX

- Replace the disabled read-only note field in `AnnotationEditSheet` with an editable
  multi-line note field.
- Show the note field for both existing highlights and new highlight creation.
- Save applies both color and note.
- Empty note text clears the note.
- For overlapping/multi-highlight selections:
  - If all selected highlights share the same note, prefill it.
  - If notes differ, show an empty field and a short helper text such as
    "Saving replaces notes on selected highlights."
  - Saving applies the entered note to all selected highlights.
- Keep video highlight note editing out of scope unless the existing article-only edit
  gate is intentionally expanded in a separate phase.

## Implementation Plan

### Phase 1: API Verification

- Confirm server support for `note` create/update/clear behavior.
- Update this spec with the confirmed request shape.

Agent class: **Explorer / research agent**. This is bounded, read-heavy work that
benefits from checking the API contract carefully before code changes.

### Phase 2: DTO and ViewModel State

- Extend `UpdateAnnotationDto` with a `note` field once the request shape is known.
- If POST supports note, extend `CreateAnnotationDto`; otherwise create first and
  patch note only when non-empty.
- Change `AnnotationEditState.noteText` from nullable read-only display data to an
  editable `String`.
- Add `onAnnotationEditNoteChanged(note: String)`.
- Change `saveAnnotationEdit()` to call a combined update method such as
  `updateAnnotations(annotationIds, color, note)`.

Agent class: **Mid-tier coding worker**. This is ordinary Kotlin state/API work with
low architectural risk after the API contract is settled.

### Phase 3: Cache and WebView Updates

- Add a note update event to `AnnotationRefreshEvent`, or replace `ColorUpdate` with
  a combined annotation attribute update event.
- On save, update in-page `<rd-annotation>` elements:
  - Set or remove `title`.
  - Set or remove `data-annotation-note`.
  - Keep color behavior unchanged.
- Update cached reader HTML for both full-package and Room text-cache modes.
  Prefer a structured helper that updates `data-annotation-color`, `title`, and
  `data-annotation-note` together for the matching annotation IDs.
- Refresh `cached_annotation` from `getAnnotations(bookmarkId)` after successful
  server mutation so the Highlights list, bottom sheet, and drawer counts stay
  reactive.

Agent class: **Senior/frontier coding agent**. This phase touches the fragile
WebView/HTML/cache boundary that recently caused stale color behavior, so it needs
careful reasoning and focused regression checks.

### Phase 4: UI and Strings

- Make the note field editable in `AnnotationEditSheet`.
- Remove the `onNoteClicked` toast path and the `highlight_note_not_supported` UX.
- Add helper/placeholder strings if needed, with English placeholders in every
  locale-specific `strings.xml` file.
- Consider a character limit only if the API confirms one.
- Keep the layout compact: highlight preview, note text field, color row, Save,
  Delete.

Agent class: **Mid-tier coding worker with Compose familiarity**. The UI is localized
but not structurally complex.

### Phase 5: Documentation and Verification

- Update `app/src/main/assets/guide/en/reading.md` to describe adding, editing, and
  clearing highlight notes.
- Run verification serially:
  - `./gradlew :app:assembleDebugAll`
  - `./gradlew :app:testDebugUnitTestAll`
  - `./gradlew :app:lintDebugAll`
- Manual checks:
  - Create a new highlight with a note.
  - Edit only the note.
  - Edit color and note together.
  - Clear a note.
  - Edit overlapping/multi-selected highlights.
  - Navigate from Highlights list to a highlight, edit note, back out, and confirm
    Highlights list and reader reloads both show the updated note.
  - Reopen the same article from the bookmark list and confirm cached HTML did not
    revert note metadata.

Agent class: **Mid-tier verification worker** for scripted checks; **senior/frontier**
for any regression involving WebView reloads, annotation tapping, or cache mismatch.

## Complexity Assessment

Overall complexity: **medium** if the API supports notes, **high/blocked** if it does
not.

The easy parts are DTOs, state, localization, and the Compose text field. The risky
part is not the note field itself; it is keeping four views of the same annotation in
sync: server annotation JSON, `cached_annotation`, cached reader HTML, and live WebView
DOM. The recent Highlights reactivity and cached-reader-HTML fixes make this much more
tractable, but Phase 3 should still be treated as the critical path.

## Out of Scope

- Local-only notes.
- Rich text or Markdown notes.
- Searching/filtering by note text.
- Editing notes directly from the global Highlights list.
- Expanding video highlight editing unless separately specified.
- Photo highlight support.
