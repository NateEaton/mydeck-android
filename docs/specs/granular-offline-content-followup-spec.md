# Spec: Granular Offline Content Follow-up (DRAFT)

## Status

**DRAFT** on `feature/sync-multipart-v012` (2026-03-27).

This spec captures follow-up work based on tester feedback after the sync/offline content
refactor and the move to side-by-side installable snapshot builds.

It is intended as the follow-up companion to
`docs/specs/granular-offline-content-management-spec.md`, which documents the original
offline-content feature set that has already shipped.

## Origin

The latest tester feedback highlighted five themes:

1. Offline/download status badges feel visually inconsistent across list layouts.
2. The Sync Settings screen feels off in its information hierarchy and naming.
3. Manual/content sync progress is easy to miss because the current feedback is subtle.
4. Turning on image downloads does not transparently enrich already-downloaded text content.
5. Reopening already-downloaded reader content feels slower and more visibly "loading" than
   older releases, even when images are still lazy-loaded.

The app ID / side-by-side install concern has already been addressed separately and is not part
of this follow-up spec.

## Summary

This spec proposes a staged follow-up focused on polish and correctness rather than another large
sync architecture change.

The core outcomes are:

1. Make download/offline badges visually stable and easier to scan.
2. Reframe "content downloads" as "content sync" and improve Sync Settings information
   hierarchy without elevating bookmark sync to the top of the screen.
3. Make the global "download images" setting transparently backfill existing downloaded
   articles in the background.
4. Remove the visible loading flash when reopening cached reader content, while keeping the
   determinate progress bar for true on-demand fetches.
5. Document progress-feedback options for later owner review without committing to an
   implementation yet.

## Goals

1. Keep bookmark-card status indicators consistent across layouts.
2. Make Sync Settings feel more coherent without adding more user decisions.
3. Ensure changing the global image-download setting "just works" for existing content.
4. Restore the fast-feeling reopen experience for cached articles.
5. Preserve the current sync model and avoid adding new user-facing controls unless they are
   clearly necessary.

## Non-Goals

- A full redesign of the Sync Settings screen visual language
- New per-bookmark sync controls beyond the existing detail-page image toggle
- A rewrite of the multipart/offline content architecture
- Deciding and implementing a system-notification progress UI in this first pass
- Changes to the release/tester build workflows

## Proposed Implementation Order

1. Bookmark list indicator placement and size polish
2. Cached reader reopen performance fix
3. Transparent image backfill when the global image-download setting is enabled
4. Sync Settings information-architecture cleanup
5. Progress feedback decision and implementation

Rationale:

- Item 1 is low-risk and highly visible.
- Items 2 and 3 address functional friction and should be treated as high priority.
- Item 4 improves clarity but does not block core workflows.
- Item 5 is intentionally deferred until after manual review of the UX direction.

## Workstream 1: Bookmark List Indicator Placement and Size

### Problem

The current offline/download indicator is visually tied to the reading-progress indicator in
grid and mosaic layouts, so it shifts depending on whether a bookmark has reading progress.
The progress badge and cloud badge also use different sizes across layouts. This creates a
"floating" feel and weakens scanability.

### Proposed Solution

Introduce fixed placement rules and shared sizing tokens for status indicators.

#### Shared sizing rules

- Use a common thumbnail status badge diameter for grid and mosaic overlays.
- Use the same outer size for progress and offline badges in those layouts.
- Use a fixed compact-layout status rail with reserved slots even when one indicator is absent.

Suggested baseline tokens:

- `thumbnailStatusBadgeSize = 28.dp`
- `thumbnailStatusIconSize = 18.dp`
- `compactStatusSlotSize = 20.dp`
- `compactStatusRailWidth = 24.dp`

Exact numbers can be tuned during implementation, but the important part is that grid and
mosaic use the same badge geometry and compact uses a fixed rail rather than ad hoc spacing.

#### Grid layout

- Keep reading progress in the top-right corner of the thumbnail.
- Move the offline/download badge to the lower-left corner of the thumbnail.
- Anchor it to the thumbnail edge, not to the progress badge.

This keeps the offline state visible while avoiding position changes caused by the presence or
absence of reading progress.

#### Mosaic layout

- Use the same offline/download badge placement as grid: lower-left corner of the thumbnail.
- Keep the reading-progress badge in the top-right corner.
- Keep the media-type icon in the top-left corner.

Using the same corner rules for grid and mosaic reduces cognitive overhead.

#### Compact layout

- Keep the download/offline indicator in the left rail, aligned to the lower slot so it visually
  lines up with the action-row zone.
- Keep the favicon in the top slot.
- Place reading status in the middle slot, between favicon and download status.
- Reserve the middle slot even when there is no reading progress so the rail does not collapse.

This addresses the tight vertical space in short-title/no-label rows while keeping the offline
state easy to find.

### Acceptance Criteria

- The offline/download badge does not move in grid or mosaic when a bookmark has no reading
  progress.
- Grid and mosaic progress/offline badges are the same size.
- Compact layout maintains consistent left-rail spacing regardless of title length or labels.

## Workstream 2: Sync Settings Information Architecture

### Problem

The current screen mixes "content downloads," storage, bookmark sync, and status in a way that
does not feel intuitive. At the same time, bookmark sync should remain a secondary concern rather
than becoming the first section in the screen.

### Proposed Solution

Reframe the screen around **Content Sync** as the primary user-controlled feature, while grouping
status and storage together and keeping bookmark sync lower in the screen.

### Proposed Section Order

#### 1. Content Sync

Rename the current "Content Downloads" section to **Content Sync**.

This section should contain:

- Content sync mode selector
- On-demand/date-range controls when applicable
- Download images toggle
- Include archived content toggle
- Clear content on archive toggle
- Download constraints

This keeps the decision-making controls together in one place.

#### 2. Status & Storage

Combine status and storage into a single secondary section.

This section should contain:

- Bookmark/content counts
- Last bookmark sync timestamp
- Last content sync timestamp
- Offline storage usage
- Clear all offline content action

Storage usage belongs here because it is informational state, not a sync rule.

#### 3. Bookmark Sync

Keep bookmark sync low on the screen.

This section should contain:

- Sync frequency
- Next scheduled run
- Sync now button

This preserves the principle that bookmark sync is important but not the primary decision area.

### Additional IA Notes

- Keep "clear content on archive" in the primary Content Sync section so it does not get buried.
- Continue to surface any content-sync constraint status near the Content Sync controls.
- Avoid introducing new top-level sections unless implementation uncovers a clear need.

### Acceptance Criteria

- The screen language consistently refers to "Content Sync" rather than "Content Downloads."
- Storage usage appears alongside sync status information.
- "Clear content on archive" remains easy to discover in the main content-sync controls.
- Bookmark sync remains below the primary content-sync and status/storage sections.

## Workstream 3: Transparent Image Backfill for Existing Downloads

### Problem

When a user turns on image downloads after previously downloading text-only article content,
already-downloaded articles are not updated with images. The current behavior feels like a no-op
unless the user clears the local database or manually refreshes content.

### Proposed Solution

When the global image-download setting changes from off to on, automatically enqueue a background
backfill job that updates existing downloaded article packages that currently have no resources.

This should require no new controls and no manual re-download flow.

### Backfill Scope

Eligible bookmarks should be:

- Article bookmarks only
- Not locally deleted
- Already downloaded or dirty
- Backed by a content package with `hasResources = false`

Picture bookmarks are unaffected because images are already intrinsic to their content model.
Video bookmarks remain unchanged.

### Execution Model

- Enqueue a unique one-time worker when the setting flips from off to on.
- Process eligible bookmark IDs in batches using the existing multipart content-package fetch.
- Respect the current content-sync constraints by default.
- If the device is offline or constrained, let the worker wait rather than failing loudly.

### User Experience

- No new buttons, dialogs, or required follow-up actions.
- The setting change should simply begin reconciling existing content in the background.
- If the user opens an eligible article before the background backfill reaches it, the existing
  per-article image toggle and on-demand flows still work normally.

An optional passive status message or snackbar may be added later if testing shows users need a
hint, but the default design goal is "just works" rather than introducing more ceremony.

### Acceptance Criteria

- Turning on image downloads causes previously downloaded text-only articles to gain images over
  time without clearing storage or re-downloading everything manually.
- No new user-facing controls are required to trigger the backfill.
- Existing per-article image toggle behavior still works.

## Workstream 4: Cached Reader Reopen Performance

### Problem

The current reader experience makes reopening cached content feel slower than it should. In the
relevant scenario:

- `v0.11.1`: first open shows a brief spinner, later reopens feel effectively instant
- current code with global image download disabled: first open shows the determinate progress bar
  as expected, but subsequent opens still show a brief non-determinate loading state

That second loading state is especially noticeable because the content is already local and should
feel as fast or faster than before.

### Proposed Solution

Separate **network fetch progress** from **local reader render readiness** more strictly.

#### Desired behavior

- If reader content is not yet cached locally, keep the current determinate top progress bar for
  the on-demand fetch.
- If reader content is already cached locally when the screen opens, do not show the full-screen
  loading overlay again.
- Cached content should render immediately, with any remaining WebView/layout preparation
  happening without a blocking visual mask.

### Likely Root Cause Area

The current detail-screen logic still gates visible reader content on a "content ready" path even
when cached HTML already exists. That makes reopen behavior look like a fresh load even when no
network fetch is happening.

### Proposed Implementation Direction

- Treat "content already available at screen entry" as a first-class fast path.
- Only show the global reader loading overlay when content must actually be fetched or when the
  first local render truly has no usable content to display.
- Preserve the determinate progress bar for actual content-package fetches.
- Validate behavior on a release-optimized snapshot build, not just debug builds.

### Acceptance Criteria

- Reopening a bookmark with already-cached article content does not show a blocking loading
  overlay.
- First-open on-demand fetches still show determinate progress feedback.
- Cached reopen behavior feels at least as fast as `v0.11.1` in the text + lazy-image scenario.

## Workstream 5: Progress Feedback for Manual/Content Sync

### Status

**Documented only for now.** The owner wants to review the options before implementation.

### Problem

The current date-range/manual content sync flow only exposes subtle in-screen feedback. If the
relevant section is collapsed or off-screen, it can feel like nothing happened after tapping the
download button.

### Recommended Options

#### Option A: In-screen determinate progress

- Show a visible progress row in Sync Settings while manual/date-range content sync is active.
- Include counts such as `processed / total` when that data is available.
- Auto-expand the relevant section while work is running.

Pros:

- Immediate and contextual when the user stays in the screen

Cons:

- Easy to miss if the app goes to background

#### Option B: System notification with progress bar

- Promote long-running manual/date-range content sync work to a foreground worker.
- Show a system notification with determinate or coarse progress.

Pros:

- Survives backgrounding and makes work visible outside the settings screen

Cons:

- More invasive; needs notification design and lifecycle decisions

#### Option C: Hybrid approach

- Use in-screen progress while the user is on Sync Settings.
- Also use a foreground notification when long-running work continues in the background.

This is the recommended direction if the later review concludes that stronger feedback is needed.

### Decision Deferred

No code changes for this workstream should be made until the owner reviews the interaction model.

## Verification Notes

When implementation begins, validation should include:

1. Grid, mosaic, and compact layout screenshots for indicator placement comparisons
2. A setting-toggle scenario where text-only content is already downloaded and image backfill
   runs automatically
3. A reopen-performance comparison between:
   - `v0.11.1`
   - current `main`
   - the updated branch on a release-optimized snapshot build
4. Sync Settings usability review focused on section order and scanability

## Open Questions

1. Whether the Status & Storage section should be expanded by default or simply positioned above
   Bookmark Sync while remaining collapsible
2. Whether passive messaging is needed when image backfill begins after the global setting change
3. Which progress-feedback option best fits the app's background-work philosophy
