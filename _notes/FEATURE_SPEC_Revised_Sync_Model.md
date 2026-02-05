# MyDeck Android – Revised Sync Model

**Design & Implementation Specification**

## 1. Goals & Non-Goals

### 1.1 Goals

* Align sync behavior with **Pocket’s UX model** while respecting **Readeck’s immutable-content philosophy**
* Decouple **bookmark metadata sync** from **content sync**
* Minimize unnecessary background work, bandwidth, and storage
* Improve user understanding of:

  * online/offline state
  * sync status
  * why some bookmarks do not (and will never) have content
* Enable intentional offline preparation via **date-range content download**

### 1.2 Non-Goals

* No use of Readeck’s `/api/sync` endpoint
* No automatic re-fetch or refresh of already-downloaded content
* No “global Sync Now” button
* No notification spam (notifications are optional and limited to long-running date-range sync completion, if implemented later)

---

## 2. Conceptual Model (Key Paradigm Shift)

### 2.1 Core Principle

**Bookmarks are live and cheap.
Content is immutable and expensive.**

### 2.2 High-Level Separation

| Concern           | Behavior                                          |
| ----------------- | ------------------------------------------------- |
| Bookmark metadata | Always synced automatically                       |
| Content           | Synced based on explicit user policy or on-demand |
| Sync triggers     | Contextual (app open, pull-to-refresh, read view) |
| Retry behavior    | Explicit and visible                              |

This replaces the current “everything syncs together” approach.

---

## 3. Revised Sync State Machine

### 3.1 Bookmark State Machine

```
┌──────────────┐
│ Not Present  │
└──────┬───────┘
       │ metadata sync
       ▼
┌──────────────┐
│ Present      │
│ (metadata)   │
└──────┬───────┘
       │ deleted on server
       ▼
┌──────────────┐
│ Deleted      │
└──────────────┘
```

* Bookmark metadata is:

  * fetched on login
  * fetched on app open
  * fetched on pull-to-refresh
* Bookmark sync is **non-configurable**, except for periodic frequency while app is open.

---

### 3.2 Content State Machine (Per Bookmark)

```
┌─────────────────────┐
│ No Content Attempted│
└──────────┬──────────┘
           │ fetch content
           ▼
┌─────────────────────┐
│ Content Downloaded  │◄────────────┐
└─────────────────────┘             │
                                     │ (no re-fetch)
┌─────────────────────┐             │
│ Fetch Failed (Dirty)│─────────────┘
└──────────┬──────────┘
           │ retry
           ▼
┌─────────────────────┐
│ Permanent No Content│
│ (media / invalid /  │
│ extraction failure) │
└─────────────────────┘
```

#### Notes:

* **Dirty** means:

  * transient failure
  * eligible for retry
* **Permanent No Content** includes:

  * video-only bookmarks
  * image-only bookmarks
  * unreachable or invalid URLs
  * Readeck extraction failure with error flag
* Once content is successfully downloaded, it is **never fetched again**

---

## 4. Sync Triggers & Behavior

### 4.1 Bookmark Sync (Metadata)

**Triggers**

* Initial login
* App open
* Pull-to-refresh on list view
* Periodic while app is open (default: 1 hour)

**Behavior**

* Fetch bookmark IDs + metadata
* Insert new bookmarks
* Remove deleted bookmarks
* Update metadata fields only
* No content fetch performed unless explicitly allowed by content policy

---

### 4.2 Content Sync

Content sync behavior is controlled by **Content Sync Policy** (see Section 5).

#### Trigger Matrix

| Trigger               | Automatic        | Manual           | Date Range       |
| --------------------- | ---------------- | ---------------- | ---------------- |
| Bookmark sync         | Fetch content    | No               | No               |
| Open reading view     | Fetch if missing | Fetch if missing | Fetch if missing |
| Pull-to-refresh       | Fetch content    | No               | No               |
| Date-range “Download” | N/A              | N/A              | Fetch            |

---

### 4.3 Reading View On-Demand Fetch

When a bookmark is opened:

1. If content exists locally → render article view
2. If content does not exist:

   * Attempt content fetch
   * Show spinner / loading indicator
3. If fetch succeeds → render article view
4. If fetch fails:

   * Mark bookmark **dirty**
   * Show retry control in reading view
5. If server reports no content available:

   * Switch automatically to **Original (Web) view**
   * Reading view supports two modes:

     * **Article** (Readeck content)
     * **Original** (embedded WebView)

---

## 5. Settings | Sync – Revised UX & Behavior

### 5.1 Structure

**Settings → Sync**

#### Section 1: Bookmark Sync

* Description: “Bookmarks are always kept in sync.”
* Option:

  * Sync frequency while app is open

    * Default: once per hour
    * Existing interval options reused
* No manual option
* No disable option

---

#### Section 2: Content Sync

**Options (radio-style selection):**

1. **Automatic**

   * Content is fetched during bookmark sync
   * Requires background sync permission
2. **Manual**

   * No batch content sync
   * Content fetched only when opening a bookmark
3. **Date Range**

   * Two date pickers:

     * Added from
     * Added to
   * Grouped **Download** button:

     * Label: “Download”
     * Scope: content for bookmarks added in selected range
   * On-demand only

---

#### Section 3: Constraints

* Only download content on Wi-Fi
* Allow content download when battery saver is enabled (toggle)

---

## 6. Permissions & Background Work

### 6.1 When to Prompt

Prompt for background sync permission only when:

* User selects **Automatic** content sync
* User taps **Download** in Date Range sync

### 6.2 Rationale Displayed to User

* Explain:

  * why background work is needed
  * that content downloads may continue while app is closed
* Prompt only once per scenario

---

## 7. Offline / Online Indicator

### 7.1 Location

* Navigation drawer header (next to app name)

### 7.2 Behavior

* Icon-only indicator
* Tooltip on long press:

  * “Offline – unable to reach server”
* Shown when:

  * no network connectivity
  * or server unreachable

No text labels. No list-header clutter.

---

## 8. Sync Status & Reporting

### 8.1 Visual Treatment

* Sync status block visually separated from other settings

  * border or card-style container

### 8.2 Metrics Displayed

**Bookmarks**

* Total bookmarks
* Unread
* Archived
* Favorites

**Content**

* Content downloaded
* Content available to download
* Content download failed (dirty)
* Bookmarks without article content (video/photo)
* Invalid or unreachable URLs
* Extraction errors reported by server

Counts are derived from existing bookmark fields and Readeck flags.

---

## 9. Failure Handling & Retry Semantics

### 9.1 Dirty State

A bookmark is marked **dirty** when:

* content fetch fails
* network error occurs
* server returns transient error

Dirty bookmarks:

* are eligible for retry on next content sync
* can be retried manually from reading view

### 9.2 Reading View Retry

* Visible retry control when content fetch fails
* Retry attempts respect:

  * Wi-Fi setting
  * battery saver setting
  * offline state

---

## 10. Current Codebase – Relevant Architecture Notes (No Code)

### 10.1 Existing Components

* `FullSyncWorker`

  * Currently handles both bookmark and content sync
* `LoadArticleUseCase`

  * Fetches article content
  * Inserts content once (immutable behavior)
* `BatchArticleLoadWorker` / `LoadArticleWorker`

  * Background article loading
* `BookmarkDao.observeAllBookmarkCounts`

  * Provides live bookmark metrics
* `WorkManager`

  * Already used for periodic and one-shot background tasks

---

### 10.2 Required Structural Changes

**Worker Responsibilities**

* Split responsibilities conceptually:

  * Bookmark sync worker (metadata only)
  * Content sync workers (policy-driven)

**Policy Layer**

* Introduce explicit content sync policy evaluation:

  * Automatic
  * Manual
  * Date range

**State Tracking**

* Track:

  * content present
  * content dirty
  * permanent no-content states

**UI Coordination**

* Reading view must be able to:

  * trigger one-shot content fetch
  * observe fetch result
  * expose retry affordance

---

## 11. Migration & Compatibility Considerations

* Existing users with downloaded content:

  * Content remains valid
  * No re-fetch required
* Existing sync interval settings:

  * Reused for bookmark sync
* Existing manual sync button:

  * Removed
  * Pull-to-refresh becomes primary manual trigger

---

## 12. Summary

This design:

* Reduces unnecessary background work
* Improves perceived performance
* Matches Pocket’s successful UX patterns
* Respects Readeck’s architectural philosophy
* Clarifies sync state for users and developers alike

It is a **refactor**, not a rewrite, and leverages existing infrastructure while significantly improving clarity, efficiency, and user trust.

---

## 13. Phase 1 Implementation Plan (Bookmark Metadata Sync Isolation)

### 13.1 Goals

* Ship **metadata-only** bookmark sync as a standalone worker.
* Ensure **no content fetch** is triggered during bookmark sync.
* Preserve current trigger points and intervals for metadata sync.

### 13.2 Scope of Change

* Introduce a dedicated **BookmarkSyncWorker** (metadata only).
* Update existing **FullSyncWorker** usages:

  * Metadata sync only, or
  * Replace call sites with BookmarkSyncWorker.
* Keep existing interval configuration (app-open periodic sync).
* Ensure pull-to-refresh and app-open sync perform metadata-only updates.

### 13.3 Implementation Steps

1. **Create BookmarkSyncWorker**

   * Keep API surface minimal: metadata fetch, insert/update/delete only.
   * Reuse existing sync interval configuration.
2. **Retire FullSyncWorker in metadata contexts**

   * Replace call sites that only need list updates (app open, pull-to-refresh).
   * If FullSyncWorker remains for legacy reasons, guard it to run metadata-only.
3. **Guard content fetch paths**

   * Ensure bookmark sync cannot enqueue LoadArticleWorker or batch loaders.
   * Verify no implicit content fetch in sync use cases.
4. **Wire triggers**

   * Login sync -> BookmarkSyncWorker
   * App open periodic sync -> BookmarkSyncWorker
   * Pull-to-refresh -> BookmarkSyncWorker

### 13.4 Expected Behavior

* On login, app open, pull-to-refresh:

  * Fetch bookmark IDs + metadata
  * Insert new bookmarks
  * Remove deleted bookmarks
  * Update metadata fields only
* No content fetch as part of metadata sync, regardless of settings.

### 13.5 Out of Scope (Phase 1)

* Content policy evaluation (Automatic / Manual / Date Range)
* Content dirty/permanent-no-content tracking
* Reading view on-demand fetch changes
* Settings UI changes
* Sync status reporting changes

### 13.6 Acceptance Checklist

* Metadata sync completes without triggering any content download.
* Existing periodic sync interval continues to schedule metadata-only work.
* Pull-to-refresh and app-open sync still update bookmark lists.

---

## 14. Phase 2 Implementation Plan (Content State Tracking)

### 14.1 Goals

* Introduce explicit content states: downloaded, dirty (retryable), permanent no-content.
* Ensure content immutability: no re-fetch once downloaded.
* Enable reliable retry flows based on dirty state.

### 14.2 Scope of Change

* Extend bookmark storage to track content state and failure reason.
* Map server responses to permanent no-content categories.
* Update content download flows to set dirty on transient failures.

### 14.3 Implementation Steps

1. **Define content state model**

   * Add fields for content presence, dirty flag, and permanent-no-content reason.
   * Map to existing Readeck flags where possible.
2. **Update content fetch pipeline**

   * On success: mark content downloaded (immutable).
   * On transient failure: mark dirty.
   * On permanent failure: mark permanent no-content with reason.
3. **Adjust retry logic**

   * Allow retries only for dirty content.
   * Block retries for permanent no-content.
4. **Backfill for existing data**

   * Default existing downloaded content to downloaded state.
   * Leave unknown/no content as unattempted.

### 14.4 Out of Scope (Phase 2)

* Content sync policy evaluation
* Settings UI changes
* Reading view UX changes
* Sync status reporting

### 14.5 Acceptance Checklist

* Content download outcomes correctly populate state fields.
* Dirty content is retryable; permanent no-content is not.
* Downloaded content is never re-fetched.

---

## 15. Phase 3 Implementation Plan (Content Sync Policy Layer)

### 15.1 Goals

* Centralize policy evaluation for Automatic, Manual, and Date Range modes.
* Apply Wi-Fi and battery constraints consistently.
* Prepare for permission prompts in later phases.

### 15.2 Scope of Change

* Add a policy evaluator (single source of truth).
* Route batch content downloads through policy checks.
* Store selected policy and date range parameters.

### 15.3 Implementation Steps

1. **Create policy evaluator**

   * Inputs: user settings, connectivity, battery saver, policy mode.
   * Outputs: allowed/blocked with reason.
2. **Wire policy into batch content flows**

   * Gate any background or bulk fetch through evaluator.
3. **Add date range parameters**

   * Persist selected date range for download requests.
4. **Add policy-specific logging**

   * Record why content fetch is skipped (for later UI surfacing).

### 15.4 Out of Scope (Phase 3)

* Reading view UX changes
* Settings UI implementation
* Sync status reporting
* Permission prompts

### 15.5 Acceptance Checklist

* All content batch fetches are blocked/allowed by policy.
* Wi-Fi and battery constraints are enforced consistently.
* Date range parameters are stored for later download execution.

---

## 16. Phase 4 Implementation Plan (Reading View Fetch + Retry)

### 16.1 Goals

* On-demand content fetch when opening a bookmark.
* Show retry affordance when fetch fails.
* Switch to Original view when content is permanently unavailable.

### 16.2 Scope of Change

* Update reading view load flow to consult content state.
* Add retry trigger that respects policy constraints.
* Provide fallback to WebView on permanent no-content.

### 16.3 Implementation Steps

1. **Gate reading view load**

   * If content present: render article view.
   * If no content: attempt fetch (subject to constraints).
2. **Handle fetch failures**

   * Mark dirty and expose retry UI.
   * Record failure reason.
3. **Handle permanent no-content**

   * Switch to Original view automatically.
   * Disable retry for permanent failures.

### 16.4 Out of Scope (Phase 4)

* Settings UI changes
* Sync status reporting
* Background date range download flow

### 16.5 Acceptance Checklist

* Opening a bookmark fetches missing content once.
* Failed fetches show retry with constraints respected.
* Permanent no-content switches to Original view.

---

## 17. Phase 5 Implementation Plan (Settings UX + Sync Status)

### 17.1 Goals

* Implement the revised Settings → Sync UI.
* Provide visible sync status metrics for bookmarks and content.

### 17.2 Scope of Change

* Add sections for Bookmark Sync, Content Sync policy, and Constraints.
* Add date range picker and Download button.
* Add sync status block with counts.

### 17.3 Implementation Steps

1. **Settings UI wiring**

   * Bookmark sync interval selection.
   * Content sync policy radio group.
2. **Date range UI**

   * Persist date inputs and allow Download action.
3. **Constraints toggles**

   * Wi-Fi only and battery saver settings.
4. **Sync status block**

   * Hook to live metrics (bookmark counts, content states).

### 17.4 Out of Scope (Phase 5)

* Permission prompt flows
* Offline indicator

### 17.5 Acceptance Checklist

* Settings UI reflects saved policy and constraint values.
* Download action is present for Date Range mode.
* Sync status metrics render and update.

---

## 18. Phase 6 Implementation Plan (Permissions + Offline Indicator)

### 18.1 Goals

* Prompt for background permission only when required.
* Surface offline/online indicator in navigation drawer.

### 18.2 Scope of Change

* Add permission prompt flow for Automatic and Date Range download.
* Add offline indicator with long-press tooltip.

### 18.3 Implementation Steps

1. **Permission gating**

   * Prompt on Automatic selection and Date Range download.
   * Ensure prompt only once per scenario.
2. **Offline indicator UI**

   * Icon-only in drawer header.
   * Tooltip text for offline/unreachable state.

### 18.4 Out of Scope (Phase 6)

* Migration cleanup tasks

### 18.5 Acceptance Checklist

* Permission prompts appear only in specified scenarios.
* Offline indicator updates based on connectivity and server reachability.

---

## 19. Phase 7 Implementation Plan (Migration + Cleanup)

### 19.1 Goals

* Remove legacy sync assumptions.
* Ensure compatibility for existing users and settings.

### 19.2 Scope of Change

* Remove manual sync button (if still present).
* Migrate any stored settings to new policy model.
* Validate no re-fetch of existing content.

### 19.3 Implementation Steps

1. **Cleanup legacy UI**

   * Remove or hide manual Sync Now button.
2. **Settings migration**

   * Map existing intervals to bookmark sync frequency.
   * Set content policy default for existing users.
3. **Content migration validation**

   * Confirm existing downloaded content remains intact.

### 19.4 Acceptance Checklist

* Manual Sync Now is removed or hidden.
* Existing settings map correctly to new model.
* No re-fetch of previously downloaded content occurs.
