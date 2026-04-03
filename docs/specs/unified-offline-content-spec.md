# Spec: Unified Offline Content Architecture

## Status

Draft. Supersedes `managed-offline-reading-spec.md` and `granular-offline-content-management-spec.md`
as the forward-looking design for offline content in MyDeck.

The multipart transport infrastructure from `feature/sync-multipart-v012` is retained as-is.
This spec redefines what is built on top of it.

---

## Origin

`managed-offline-reading-spec.md` established a policy-driven offline reading model and
identified the two-tier content design (text-only with lazy images / full with local images)
as the foundation. After working through implementation implications, it became clear that
the two-tier design creates more complexity than it resolves:

- A fragile three-step sequence (HTML refresh → URL rewriting → image deletion) is required
  to downgrade a full package to text-only.
- A backfill worker path is required to upgrade text-only packages to full packages.
- `ContentPackageManager.deleteResources()` has a safety abort condition that can stall
  storage pruning.
- Hysteresis watermark logic in `BatchArticleLoadWorker` tracks image bytes separately from
  text bytes, with a restore path back to text-only state.
- The reader and annotation refresh paths must branch on whether resources are present.

This spec collapses the two tiers. A bookmark either has a full offline content package
(text + images) or it does not. There is no managed text-only offline state.

---

## Design Principles

### 1. Offline content is binary: full package or nothing

In offline reading mode, managed content always includes both text and images. There is no
"text-only offline" tier. A bookmark is either fully downloaded or not downloaded.

### 2. On-demand text caching is a performance optimization, not offline content

When a user opens a bookmark that has no local package, the reader fetches HTML from the
network and stores it locally. This text cache exists for fast re-open performance and is
not "offline content" in the managed sense. It is not subject to offline storage policies.

### 3. Network-first is the default; offline reading is opt-in

The default experience is network-first: bookmark metadata syncs automatically, articles
open over the network, and the reader caches text locally for performance. Offline reading
is an optional background maintenance policy.

### 4. Storage management operates at the bookmark level, not the resource level

When a management policy requires pruning, whole offline packages are evicted (oldest first),
not individual image files from otherwise-retained packages. This keeps the content state
machine simple and makes storage behavior predictable.

---

## Content States

The existing `BookmarkEntity.ContentState` enum is retained without schema change:

```
NOT_ATTEMPTED      — no package, no cache
DOWNLOADED         — content is present locally
DIRTY              — content is present but needs refresh (e.g. annotations changed)
PERMANENT_NO_CONTENT — server has confirmed no extractable content for this bookmark
```

The distinction between "text-cached on demand" and "full offline package" is expressed
through the existing `ContentPackageEntity.hasResources` field, which already flows to
`BookmarkListItemEntity.hasResources` for list display:

| ContentState  | hasResources | Meaning                                               |
|---------------|--------------|-------------------------------------------------------|
| NOT_ATTEMPTED | null         | No local content                                      |
| DOWNLOADED    | false        | Text cached on-demand; images lazy-load from network  |
| DOWNLOADED    | true         | Full offline package (text + images); managed offline |
| DIRTY         | false        | Stale text cache; needs refresh                       |
| DIRTY         | true         | Stale full package; needs refresh                     |
| PERMANENT...  | null         | No content available from server                      |

`DOWNLOADED + hasResources=false` replaces what would otherwise be a new `TEXT_CACHED` enum
value. Since the alternative architecture eliminates the managed text-only offline state,
this combination has a single, unambiguous meaning going forward: the text was cached
on-demand when the user opened the article. No DB schema migration is required.

---

## Modes of Operation

### Default Mode (offline reading disabled)

- No background content downloading.
- Opening a bookmark fetches HTML from the network. Text is stored locally
  (`DOWNLOADED, hasResources=false`) for fast re-open. Images lazy-load from the network.
- Text cache is retained indefinitely until the user explicitly removes it (via "Remove
  downloaded content" in the bookmark detail overflow menu) or archives the bookmark
  with auto-clear enabled. It is not auto-pruned by any background process. LRU or
  age-based eviction is deferred as a potential future enhancement.
- `BookmarkListItemEntity.hasResources = false` → outline download icon on bookmark cards.

### Offline Reading Mode (enabled via settings)

- Background worker (`BatchArticleLoadWorker`) proactively downloads full packages
  (text + images) for eligible bookmarks, newest-first by date added, subject to the
  active management policy.
- WiFi-only and battery-saver constraints apply to the background worker, not to
  on-demand article opens.
- When the user opens a bookmark not yet downloaded (beyond current policy range or not
  yet reached by the worker), the reader fetches text from the network immediately
  (fast open) and queues a full package download (text + images) as a priority task.
  The bookmark transitions: `NOT_ATTEMPTED → DOWNLOADED(hasResources=false) →
  DOWNLOADED(hasResources=true)` as first the text is cached, then the full package
  arrives. This means any bookmark the user actively opens while online and offline
  reading is enabled will naturally acquire a full offline package regardless of whether
  it is within the current policy range.
- `BookmarkListItemEntity.hasResources = true` → filled download icon on bookmark cards.
- Disabling offline reading purges all managed offline content (`hasResources=true`
  packages) immediately. On-demand text caches (`hasResources=false`) are retained
  per the default-mode rules above.

---

## Bookmark Card Icons

| State                              | Icon                        |
|------------------------------------|-----------------------------|
| No local content (NOT_ATTEMPTED)   | None                        |
| Text cached on-demand (DOWNLOADED, hasResources=false) | Outline download icon |
| Full offline package (DOWNLOADED, hasResources=true)   | Filled download icon  |
| DIRTY (either sub-state)           | Same icon as DOWNLOADED sub-state; no separate dirty indicator on cards |

This extends the icon system from `granular-offline-content-management-spec.md` with one
semantic shift: the outline icon's meaning changes from "managed text-only offline" to
"on-demand text cache."

---

## Offline Management Policies

All three policies share the same download mechanism: fetch full packages newest-first.
They differ only in how eligibility and the pruning threshold are defined.

### Policy A: Storage Limit

- Download newest-first until total offline storage (text + images combined) reaches
  the configured cap.
- When new bookmarks arrive and the cap would be exceeded: drop offline content for the
  oldest eligible bookmark(s) until under the cap.
- Cap options: 100 MB, 250 MB, 500 MB, 1 GB, 2 GB, Unlimited.
- No secondary storage cap applies to this policy; the cap is the only limit.

### Policy B: Newest N Bookmarks

- Maintain full offline packages for the N most recently added eligible bookmarks.
- When the count would exceed N (new bookmark added, or eligibility changes): drop
  offline content for the oldest bookmark above the limit.
- N options: 10, 25, 50, 100, 250, 500, All eligible.
- A secondary maximum storage cap may optionally be configured as a safeguard:
  "Never exceed X GB regardless of count." Defaults to Unlimited.

### Policy C: Added Within Last N Days / Weeks / Months

- Maintain full offline packages for bookmarks added within a rolling time window.
- Content for bookmarks older than the window is pruned during the next maintenance run.
- Window options: 1 week, 2 weeks, 1 month, 3 months, 6 months, 1 year.
- A secondary maximum storage cap may optionally be configured as a safeguard.
  Defaults to Unlimited.

### Secondary Storage Cap

The secondary cap applies only to Policies B and C. It acts as a hard ceiling: pruning
runs to satisfy the cap before policy-based maintenance runs. Storage-Limit (Policy A)
already defines the space budget directly; adding a secondary cap to it would be
redundant and confusing, so it is not offered for Policy A.

### Policy Selector

The three policies are presented as a single radio-button selector. One policy is always
active when offline reading is enabled.

---

## One-Time Manual Sync by Date Range

In addition to the ongoing management policies, a one-time manual batch download action
is available. The user specifies a from-date and to-date, and the app downloads full
packages for all eligible bookmarks added within that range, subject to the current
WiFi and battery constraints.

This is a manual action, not an ongoing management policy. Content downloaded via a
one-time sync is subject to the same eviction rules as content downloaded by the active
management policy (it may be pruned if the policy threshold is later exceeded). The
action appears in the settings screen as a button, separate from the policy selector.

This restores the date-range download capability that existed pre-branch while keeping
it distinct from the ongoing rolling-window policy.

---

## Offline Scope and Archive Handling

### Scope

Two scope options control which bookmarks are eligible for offline management:

- **My List**: Only non-archived bookmarks. When a bookmark is archived, it becomes
  ineligible; its managed offline content (`hasResources=true` package) is purged if
  `clearContentOnArchive` is enabled.
- **My List + Archived**: Both non-archived and archived bookmarks are eligible.

### Auto-Clear on Archive

A separate toggle: `clearContentOnArchive`. When enabled, archiving any bookmark removes
its full offline package (and its on-demand text cache) and resets `contentState` to
`NOT_ATTEMPTED`. Un-archiving does not trigger a re-download; the bookmark re-enters the
normal download queue at its natural newest-first position.

---

## Bookmark Type Handling

### Article Bookmarks

Standard handling: full package = HTML text + inline image resources. Both text and images
are stored and served from the local package.

### Picture Bookmarks

Full package = primary image + any associated text/caption HTML. Pictures always download
their primary image; this is unchanged. The management policies apply in the same way as
articles. Icon logic is the same.

### Video Bookmarks

Full package = HTML content (which may include a transcript, captions, or text extracted
via a custom content script) plus any inline images in that text. The video itself is not
stored (embed-based; the player requires network access). A user opening a video bookmark
while offline with a downloaded package can read any text content, but cannot play the
video. The icon and state machine are the same as other bookmark types. The download action
does not attempt to fetch or cache video media files.

---

## Per-Article Image Toggle: Removed

The per-article cycling icon in the Bookmark Detail dialog (toggle between "images stored
locally" and "images lazy-loaded") is removed. It is not needed under this architecture.

Users who want images for a specific article that was not downloaded by the active policy
can simply open the article while online when offline reading is enabled — a full package
(text + images) will be queued immediately on open, as described in the Offline Reading
Mode section above.

Users who want to remove offline content for a specific article can use the "Remove
downloaded content" action in the bookmark detail overflow menu. This resets the bookmark
to `NOT_ATTEMPTED`, and the full package will be re-downloaded by the next maintenance run
if the bookmark is still within policy range.

Retaining the toggle would require keeping:
- `ContentPackageManager.deleteResources()` and its safety abort on relative URL detection.
- The HTML refresh + URL rewriting sequence to restore absolute image URLs before deletion.
- The text-only vs. full-resource branch in `refreshHtmlForAnnotations`.
- A second sub-state within `DOWNLOADED` content that the batch worker, reader, and
  annotation pipeline must all handle separately.

The simplification gained by removal outweighs the marginal per-article control lost.

---

## Annotation Implications

The annotation pipeline is unchanged in structure. The simplification is in the
`refreshHtmlForAnnotations` path:

- Full offline packages always have resources. The URL rewriting path in `refreshHtmlForAnnotations`
  always uses relative URLs for offline packages. The branch that checked `hasResources`
  to decide whether to rewrite URLs is no longer needed.
- For on-demand text-cached bookmarks (`hasResources=false`), annotation refresh uses the
  legacy article endpoint path (`LoadArticleUseCase`) as today.
- The annotation snapshot caching logic (`BookmarkDetailViewModel`) is unchanged.
- When the background worker downloads a full package for a bookmark that has local
  annotation modifications (e.g., the user created a highlight while reading the text-cached
  version), the worker must detect the `DIRTY` state and re-apply annotation enrichment
  rather than overwriting with a clean server package. This is the same safeguard that
  exists in the current branch.

---

## What Is Removed from the Current Branch

The following are explicitly removed as part of implementing this architecture:

- `fetchTextOnly()` worker path in `BatchArticleLoadWorker` (the method may remain in
  `MultipartSyncClient` for annotation refresh use only).
- `ContentPackageManager.deleteResources()` and its associated safety check.
- HTML URL rewriting for the full → text-only downgrade path.
- Hysteresis watermark image pruning in `BatchArticleLoadWorker`.
- Adaptive batch sizing based on text-vs-image mode.
- Per-article image toggle UI in `BookmarkDetailDialog` and its backing ViewModel logic.
- `downloadImagesWithArticles` preference key.
- Per-bookmark "Remove downloaded content" action: the UI element was removed from the
  branch but the removal was incomplete. The following dead code must be fully cleaned up:
  `BookmarkDetailViewModel.onRemoveDownloadedContent()`,
  `BookmarkListViewModel.onRemoveDownloadedContent()`, the `onRemoveDownloadedContent`
  callback parameter and its propagation through the full composable chain
  (`BookmarkDetailScreen`, `BookmarkDetailTopBar`, `BookmarkDetailMenu`, `BookmarkCard`,
  `BookmarkCards`, `BookmarkListScreen`, `BookmarkDetailsDialog`), and any associated
  string resources. `ContentPackageManager.deletePackage()` itself is retained — it is
  still called by the policy pruning worker and by archive auto-clear.
- Bookmark-card offline state badges that distinguish text-only from full packages in a
  managed-offline context (replaced by the outline/filled icon distinction described above).

---

## Settings Screen UI Requirements

### Material You Compliance

The sync settings screen must be updated to be fully Material You compliant as part of
this implementation. The existing **Bookmark Sync** section already uses correct Material
You styling and serves as the reference. All new and existing sections below it must be
brought to the same standard: consistent typography scale, proper use of
`ListItem`/`SwitchPreference`/`DropdownMenuBox` Material 3 components, correct surface
tiers and container colors, and appropriate use of `contentColor`/`containerColor` tokens.
No custom-styled controls that deviate from the Material 3 component set should remain
after this work.

### User Guide

The `app/src/main/assets/guide/en/settings.md` user guide must be updated to reflect all
changes to sync and offline reading settings introduced by this spec. The update should
cover: the offline reading toggle and what enabling/disabling it does, the three management
policies and how to choose between them, the one-time date-range download action, the
archive scope options, and the "Remove downloaded content" action in the bookmark detail
menu. Changes to bookmark card icons (outline vs. filled) should be documented in
`your-bookmarks.md`.

---

## Settings Layout

```
Offline Reading
  [✓] Enable offline reading

  Keep offline:
  ( ) Newest N bookmarks          [100 ▼]
  ( ) Added within last            [3 months ▼]
  (●) Storage limit               [1 GB ▼]

  Maximum storage cap             [Unlimited ▼]
  (only shown for Newest N and Date Range policies)

  Include archived bookmarks                  [OFF]
  Clear offline content when archiving        [OFF]
  Wi-Fi only                                  [OFF]
  Allow on battery saver                      [ON]

  [  Download content for date range...  ]
  (opens date picker; one-time manual action)
```

---

## Settings Keys

### Remove
- `downloadImagesWithArticles: Boolean`
- `contentSyncMode: Enum` (automatic/manual/date-range as a scheduling concept)
- `dateRangeParams` as a user-facing scheduling concept (retained internally for one-time sync)

### Keep (rename where appropriate)
- `syncFrequency`
- `wifiOnly: Boolean`
- `allowOnBatterySaver: Boolean`
- `includeArchivedInOfflineScope: Boolean` (was `includeArchivedContentInSync`)
- `clearContentOnArchive: Boolean`

### Add
- `offlineReadingEnabled: Boolean`
- `offlinePolicy: Enum { STORAGE_LIMIT, NEWEST_N, DATE_RANGE }`
- `offlinePolicyStorageLimit: Long` (bytes)
- `offlinePolicyNewestN: Int`
- `offlinePolicyDateRangeWindow: Duration`
- `offlineMaxStorageCap: Long` (secondary cap; Policies B and C only; default: Long.MAX_VALUE)

---

## Bookmark Detail Dialog Offline Status

Replace any icon-only indicator with explicit status text:

- `Offline content: Not kept` — NOT_ATTEMPTED, offline reading off
- `Offline content: Text cached` — DOWNLOADED, hasResources=false
- `Offline content: Available` — DOWNLOADED, hasResources=true
- `Offline content: Refresh pending` — DIRTY (either sub-state)

No per-bookmark content removal action is exposed in the detail dialog or its overflow
menu. Content lifecycle is managed entirely by the system (policy-based background worker,
archive auto-clear, global clear in settings). The status display above is purely
informational.

---

## Sync Status Display

When offline reading is off:
- Last bookmark sync time
- Next scheduled sync

When offline reading is on (additional fields):
- Total bookmarks / My List count / Archived count
- Bookmarks with full offline content available
- Offline storage used
- Last offline content maintenance time

Low-value diagnostic counters (download failed, no article content) are not surfaced
in the main settings screen.

---

## Migration

1. Fresh installs: offline reading defaults to off; no managed offline content.
2. Existing installs with automatic content sync enabled: migrate to offline reading on,
   policy set to Storage Limit at the user's current or a sensible default cap.
3. Existing `DOWNLOADED + hasResources=true` packages remain valid; no re-download needed.
4. Existing `DOWNLOADED + hasResources=false` packages remain as on-demand text caches;
   they are not purged unless the user explicitly removes them or archives the bookmark.
5. The `downloadImagesWithArticles` preference is removed; its previous value does not
   need migration (the new architecture always downloads images in offline mode).

---

## Future Considerations

### LRU / age-based eviction of on-demand text caches

In the default (offline reading off) mode, the on-demand text cache grows without bound.
For users with large libraries who open many articles, this could accumulate significant
storage over time. Future options:

- Evict on-demand caches older than N days.
- Keep at most N on-demand caches (LRU).
- Let the user configure a separate cache size limit.

These are deferred; "remove downloaded content" and "clear all offline content" in settings
cover the explicit management case in the near term.

### Collections-based offline scope

When Readeck collections are implemented in MyDeck, a fourth management policy becomes
viable: the user designates a collection (defined by a set of filter criteria) as the
offline scope. The app maintains full offline packages for all bookmarks matching that
collection. This would be an ongoing active management policy with very fine-grained
control. The architecture described in this spec accommodates this straightforwardly:
the eligibility predicate in `BatchArticleLoadWorker` simply becomes a collection query
rather than a date-range or count check. Implementation is deferred until the collections
feature exists.

### Custom date/time range as an ongoing rolling policy

The one-time manual date-range sync described above uses an explicit from/to date pair.
A rolling variant (e.g., "bookmarks added between 6 months ago and 3 months ago") is
technically feasible but creates a window that may grow stale without user attention.
Policy C (rolling time window from today) is the cleaner ongoing variant; the custom
from/to range is most useful as a one-time backfill tool.

---

## Open Questions

None unresolved at time of writing.
