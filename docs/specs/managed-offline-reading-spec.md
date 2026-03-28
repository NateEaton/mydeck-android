# Spec: Managed Offline Reading & Sync Settings Redesign (DRAFT)

## Status

Draft.

This spec defines the post-`v0.12.0` direction for sync and offline reading after
revisiting the granular-offline approach. It supersedes the bookmark-card offline
status direction described in `docs/specs/granular-offline-content-followup-spec.md`
without replacing the multipart/offline-content foundation that branch introduced.

## Origin

The current sync/offline implementation successfully added multipart content fetching,
text-only article storage, image download control, and offline-state indicators. After
shipping that first pass, two things became clear:

1. The UX is still too management-heavy for the desired "set and forget" experience.
2. The most important user value is not per-bookmark offline control, but:
   - a simple default network-first experience
   - optional offline reading that works automatically
   - fast bookmark open and re-open behavior

The revised direction is to treat offline reading as an opt-in background maintenance
policy rather than a manual download workflow.

## Summary

MyDeck should behave like this:

1. Out of the box, the app is network-first, similar to native Readeck behavior.
2. Bookmark metadata sync is always available and remains the primary sync concept.
3. Offline reading is optional and off by default.
4. When offline reading is enabled, eligible bookmarks automatically get text content
   downloaded in the background.
5. Image download is controlled by a single global preference.
6. Offline scope is policy-based, not per-bookmark micromanagement.
7. Storage management is automatic and applies primarily to downloaded images, not text.
8. Bookmark cards no longer carry offline/download state badges.
9. Offline content state is shown in the bookmark details dialog instead.

## Goals

1. Make sync and offline behavior feel automatic and low-maintenance.
2. Keep the default experience simple for users who do not care about offline reading.
3. Preserve the ability to read cached text offline when offline reading is enabled.
4. Let users choose whether article images are stored locally.
5. Backfill images automatically for already-cached text content when image download is turned on.
6. Improve bookmark open and re-open speed, especially when local text is already available.
7. Reduce UI clutter by removing list-level offline state indicators and one-off content download actions.

## Non-Goals

- Per-bookmark offline content management as a primary workflow
- Manual date-range content sync
- Multi-select offline content operations
- Bookmark-card offline state badges
- A large in-your-face sync progress UI
- Fine-grained storage policies for text content

## Design Principles

### 1. Offline reading is a maintenance policy, not a user task

Users should decide whether MyDeck maintains offline reading automatically. They should
not have to think in terms of one-time download jobs, date ranges, or individual bookmark
download state unless they are troubleshooting.

### 2. Network-first is the default; offline-first is opt-in

The initial experience should remain simple and familiar:

- bookmark metadata sync is on
- articles open over the network when needed
- no offline management UI dominates the settings screen

### 3. Text is cheap; images are the real storage concern

Text HTML for a large library is tiny compared with image assets. The system should
optimize storage management around image resources, not around article text.

### 4. UI should expose policy, not internal machinery

The settings screen should let users express intent:

- keep bookmarks in sync
- keep bookmarks available offline
- include images or not
- control scope
- control image storage budget

The screen should not expose underlying worker orchestration concepts.

## Core Behavior

### Bookmark Sync

Bookmark metadata sync remains a first-class concept and appears first in the settings UI.

- Users can choose sync frequency.
- Users can run bookmark sync manually.
- Basic sync status can remain visible near the bottom of the screen.

### Offline Reading

Add a top-level toggle:

- `Enable offline reading`

When off:

- background offline maintenance is disabled
- managed offline content is purged immediately
- advanced offline settings are hidden or greatly reduced
- the user still gets normal network-first reading behavior

When on:

- eligible bookmarks automatically get text content stored locally
- image download behavior is controlled by a separate global preference
- scope and storage policies become visible

Important: disabling offline reading is not just a policy pause. It is an explicit
opt-out of managed offline storage, so managed offline content should be removed
immediately rather than aging off later.

### Offline Scope

The offline scope should be one clear control.

Preferred options:

1. `My List`
2. `My List + Archived`

Rationale:

- `My List` already means all non-archived bookmarks in MyDeck's terminology.
- encoding this as a single scope choice is easier to understand than combining
  scope with one or more exception toggles
- archived favorites can be revisited later as a future enhancement if needed

Behavior:

- `My List`: archive removal makes the bookmark ineligible and its managed offline content
  is purged
- `My List + Archived`: both My List and Archive remain eligible

### Download Images

Add a global toggle:

- `Download images`

Behavior:

- off: eligible article text is stored locally, but HTML keeps absolute image URLs so
  images lazy-load from the network when online
- on: eligible bookmarks use full multipart content packages with local image resources

When the user turns image download on:

- already-cached eligible text-only articles should automatically backfill image resources
- no extra user action should be required
- this applies across the eligible offline scope, not just to newly synced bookmarks

When the user turns image download off:

- eligible bookmarks should transition back to text-only packages
- HTML must be refreshed to restore absolute image URLs before local resources are removed
- the transition must work cleanly for already-cached bookmarks without leaving stale
  relative image URLs behind

### Storage Limit

Add a single storage-limit control for downloaded images only.

Examples:

- `100 MB`
- `250 MB`
- `500 MB`
- `1 GB`
- `Unlimited`

Behavior:

- the cap applies to image resources, not to cached text HTML
- when pressure exceeds the cap, image resources are pruned automatically
- text HTML should be preserved whenever possible

This preserves the core value of offline reading while keeping image storage bounded.

### Bookmark Details Dialog

Offline state moves from bookmark cards to the bookmark details dialog.

The dialog should show explicit status text rather than relying on cloud iconography alone.

Examples:

- `Offline content: Not kept`
- `Offline content: Text available`
- `Offline content: Text and images available`

This is a better fit for a managed system:

- no list clutter
- no need to decode subtle badge differences while scanning bookmarks
- status remains available when the user actually wants details

## Sync Settings UI

### Layout

Do not use collapsible sections.

Recommended structure:

1. `Bookmark Sync`
   - Sync frequency
   - Sync now
2. `Offline Reading`
   - Enable offline reading
   - If enabled:
     - Offline scope
     - Download images
     - Image storage limit
     - Wi-Fi only
     - Allow downloads on battery saver
3. `Storage Management`
   - Offline content size
   - Clear all offline content
4. `Sync Status`
   - When offline reading is off: basic bookmark sync info only
   - When offline reading is on: expanded bookmark/content sync counts and timestamps

When offline reading is off, the screen should shrink substantially. The user should not
have to scroll through disabled storage and content controls they are not using.

### Sync Status Detail

When offline reading is off, show only basic sync status such as:

- last bookmark sync time
- optionally next scheduled bookmark sync

When offline reading is on, show broader sync/offline status such as:

- total bookmarks
- My List count
- archived count
- favorites count
- offline content available
- offline content stored
- last bookmark sync time
- last offline-content maintenance time

Do not surface low-value diagnostic counters like:

- download failed
- no article content

### Controls to Remove

Remove these concepts from the main settings flow:

- content sync mode: automatic vs manual
- one-time date-range content download
- content download section headings built around manual jobs
- bookmark-card offline indicator rationale

## Content Fetching Strategy

### Multipart remains the canonical managed-offline format

For offline-managed content, multipart is the canonical storage path.

Use:

- text-only multipart (`fetchTextOnly`) when images are not being stored locally
- full multipart packages (`fetchContentPackages`) when images are stored locally

This gives the app two important HTML modes:

1. absolute image URLs for lazy/network-loaded images
2. relative local URLs for stored resources

### Multipart should become the dominant content path

Multipart should be the preferred long-term path if it can support:

- network-first reading behavior when offline reading is disabled
- managed local text storage when offline reading is enabled
- clean transitions between lazy/network-loaded images and locally stored images
- strong reader open and re-open performance

This is the cleaner architecture because multipart already supports the two HTML modes
MyDeck needs:

1. absolute image URLs for lazy/network-loaded images
2. relative local URLs for stored resources

### Legacy article endpoint remains available during transition

The legacy `/bookmark/{id}/article` endpoint may remain in use for network-first reading
when offline reading is disabled or as a fallback path during transition.

However, it should not be treated as the long-term authoritative offline storage format
if multipart can deliver equivalent UX and performance.

Why:

- multipart already supports the two HTML modes MyDeck needs
- dual persisted HTML sources increase reconciliation complexity
- reader-open performance is easier to reason about if one local package format is canonical

The key validation points for making multipart dominant are:

- toggling image download on backfills resources for already-cached text content
- toggling image download off restores absolute URLs cleanly for already-cached content
- reader performance remains strong in both network-first and local-first scenarios

## Reader Loading Behavior

Smooth bookmark opening is a major goal of this redesign.

Desired behavior:

1. If a local content package exists, the reader should use it immediately.
2. Re-opening a cached bookmark should avoid visible loading chrome whenever possible.
3. If no local package exists and offline reading is off, the app may use the network-first
   article path.
4. If no local package exists and offline reading is on, the app may still render from the
   network initially, but it should enqueue or maintain the correct local package in the
   background according to current policy.

This means reader performance work is explicitly part of this direction, not a side effect.

## Automatic Cleanup Rules

### Archive Changes

When a bookmark becomes out-of-scope because of archiving:

- managed offline content is purged automatically

### Image Pruning

When image storage exceeds the configured cap:

- prune image resources first
- preserve text HTML where possible
- do not require the user to manually clear or re-download content to stay within budget

## Data and Settings Implications

### Existing settings to keep

- bookmark sync frequency
- download images
- Wi-Fi only
- allow on battery saver

### Existing settings to deprecate or remove

- content sync mode
- date-range params as a user-facing feature
- include archived content as a simple boolean if replaced by a richer scope enum
- clear-on-archive as a user-facing toggle if archive cleanup becomes intrinsic to scope

### New settings

- `offlineReadingEnabled: Boolean`
- `offlineScope: Enum`
- `offlineImageStorageLimit: Enum or Long`

## Migration Strategy

1. Existing users keep current bookmark sync frequency.
2. Offline reading defaults to off for fresh installs.
3. Existing installs with prior content-sync features should migrate to a sensible default:
   likely offline reading on if they previously had automatic content sync enabled, with
   scope mapped to `My List` unless prior settings imply otherwise.
4. Existing text-only packages remain valid.
5. Turning image download on after migration triggers background backfill for eligible
   text-only packages.
6. Disabling offline reading purges managed offline content immediately.

## Implementation Phases

### Phase 1: Settings redesign

- Replace the current content-sync/date-range UI with the new bookmark-sync + offline-reading layout
- Remove accordions
- Add offline reading toggle and new scope control

### Phase 2: Policy-driven offline maintenance

- Introduce offline scope enum behavior
- Make archive cleanup follow scope automatically
- Purge managed offline content immediately when offline reading is disabled
- Remove obsolete content-sync mode behavior from the user flow

### Phase 3: Image storage management

- Add image-only storage cap
- Add automatic image pruning
- Implement automatic image backfill when image download is enabled

### Phase 4: Reader load performance

- Optimize cached bookmark open/re-open behavior
- Ensure local HTML short-circuits unnecessary loading indicators

### Phase 5: UI cleanup

- Remove bookmark-card offline badges
- Move offline content status to bookmark details
- Revisit remaining detail-level controls so the product reflects managed offline policy

## Acceptance Criteria

- A fresh user sees bookmark sync first and does not see a manual content-download workflow.
- With offline reading off, the settings screen is short and network-first in tone.
- With offline reading on and images off, eligible bookmarks get text cached with absolute
  image URLs.
- Turning image download on automatically backfills images for already-cached eligible articles.
- Turning image download off converts eligible cached articles back to text-only packages
  with working absolute image URLs.
- Archiving an out-of-scope bookmark removes its managed offline content automatically.
- Disabling offline reading removes managed offline content immediately.
- Bookmark cards do not display offline status badges.
- Bookmark details show explicit offline content state.
- Re-opening a cached bookmark is noticeably faster and less visually noisy than the current implementation.

## Open Questions

1. Can multipart fully replace legacy article persistence while preserving the desired
   network-first feel and reader-open performance?
2. How much sync-status detail belongs in the main screen versus a future diagnostics surface
   once the simplified status set has shipped?
