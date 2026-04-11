# Reading Progress, Read Anchor, and Read-State Model — Technical Specification

Status: Draft for implementation  
Date: 2026-03-16

## 1. Purpose

Define how MyDeck should persist and sync article reading position so that:

1. MyDeck keeps its current local/offline-first model
2. Scroll-derived progress is only written when leaving reading view or when the app backgrounds
3. Readeck can resume to the correct in-article position for progress values between 1 and 99
4. Completion behavior at 100% remains sticky, matching current MyDeck behavior and native Readeck behavior

This spec also explicitly decides whether MyDeck should introduce a persisted internal `is_read` field.

## 2. Confirmed Reference Behavior

The following behavior is now considered confirmed from live testing plus source review of `/Users/nathan/development/readeck`:

### 2.1 Reading progress semantics

1. For progress values between 1 and 99, the saved value is the current position, not the furthest historical position
2. When progress reaches 100, progress becomes sticky and later scrolling upward does not reduce it
3. Readeck uses `read_progress` for progress indicators and read/unread presentation
4. Readeck uses `read_anchor` only to restore position for progress values between 1 and 99
5. Readeck clears `read_anchor` when progress is 0 or 100

### 2.2 Persistence timing

Native Readeck updates the server during reading with debounce. MyDeck will not copy that timing model.

Accepted MyDeck-specific decision:

- MyDeck will continue to track reading state locally while reading
- MyDeck will only persist scroll-derived read position when the user exits reading view or when the app/screen backgrounds
- This is acceptable because MyDeck is single-user, supports offline reading, and already has an offline action queue

## 3. Goals

1. Preserve the current MyDeck behavior where the latest current position wins for 1 to 99
2. Preserve the current sticky-completion behavior at 100
3. Persist read position on all meaningful exit/background paths
4. Queue read-position updates offline and sync them later
5. Add `read_anchor` support so Readeck can reopen mid-article at the correct place after a MyDeck reading session
6. Improve MyDeck's own restore precision by using `read_anchor` when available, with percentage fallback
7. Keep the data model simple and avoid redundant read-state fields unless clearly required

## 4. Non-Goals

1. Sending read-position updates to the server continuously while the user scrolls
2. Reproducing Readeck's exact in-session debounce/write cadence
3. Full inbound cross-client anchor parity from every server sync response
4. Introducing a separate persisted `is_read` field unless required by the actual server data model
5. Tracking reading progress in Original mode WebView content

## 5. Product Decisions

### 5.1 Persistence model

MyDeck will use a local live-tracking plus exit-save model:

1. Progress continues to be tracked locally while reading
2. Anchor will also be tracked locally in memory while reading
3. Scroll-derived position will be persisted only on:
   - explicit back navigation from the detail screen
   - system back from the detail screen
   - `ON_STOP` while the reader is visible
   - `ViewModel.onCleared()` as a final fallback
4. Manual mark read/unread actions remain immediate writes because they are explicit user commands, not continuous tracking

### 5.2 Current-position rule

For article reading progress below 100:

- The latest current position replaces the previous saved position
- MyDeck must not use a furthest-point accumulator

### 5.3 Completion rule

When the user reaches the end of the article:

1. Save `read_progress = 100`
2. Treat the bookmark as read
3. Stop reducing progress from later scroll events
4. Clear `read_anchor`
5. Reopen at the end of the content

### 5.4 Read-state model decision

MyDeck will not add a persisted internal `is_read` field in V1.

Read state remains derived:

- `isRead == (readProgress == 100)`

Rationale:

1. Readeck's API and native reader behavior are driven by `read_progress` and `read_anchor`, not by a separately synchronized `is_read` field
2. MyDeck already treats completion as `readProgress == 100`
3. A persisted boolean would duplicate state, increase migration and sync complexity, and create new consistency failure modes
4. The desired sticky-completion behavior is already modeled by `readProgress == 100` plus the existing local completion lock

## 6. Functional Specification

### 6.1 Save triggers

#### 6.1.1 Explicit back

When the user leaves the bookmark detail screen through the app's back affordance:

1. Capture the latest local reader position snapshot
2. Persist it before navigation completes

#### 6.1.2 System back

MyDeck must add a normal detail-screen `BackHandler` so that Android system back uses the same save path as the top-bar back action.

This closes the current gap where some exits rely only on `ON_STOP`/`onCleared`.

#### 6.1.3 Backgrounding

When the app or screen enters `ON_STOP` while an article reader is active:

1. Persist the latest in-memory snapshot
2. Queue it offline if network/server sync is unavailable

This path is required even if the user never taps back.

#### 6.1.4 ViewModel fallback

`onCleared()` remains a last-resort fallback only.

It must not be the primary path for normal navigation or background handling.

### 6.2 Save eligibility

The current `progress > 0` guard is too coarse for the desired behavior and must be replaced.

MyDeck must support three distinct cases:

1. Fresh open, no meaningful movement:
   - Do not persist anything
2. User scrolled and current progress is between 1 and 99:
   - Persist progress and anchor
3. User scrolled back to the top and current progress is now 0:
   - Persist 0 and clear anchor

Required state change:

- Replace the simple `> 0` save guard with a session-aware dirty flag such as `hasReadPositionChangedThisSession`

This flag should become true when:

1. Scroll-derived progress changes meaningfully during the session
2. The in-memory anchor changes meaningfully during the session
3. The user explicitly marks read or unread

### 6.3 Progress and anchor rules

#### 6.3.1 Progress 0

- Restore to top
- Clear anchor
- Treat as unread

#### 6.3.2 Progress 1 to 99

- Restore by anchor when available
- Fall back to percentage restore if anchor is missing or invalid
- Treat as in progress

#### 6.3.3 Progress 100

- Restore to end
- Clear anchor
- Treat as read
- Keep progress locked at 100 unless the user explicitly marks unread

### 6.4 Manual Mark Read / Mark Unread

Manual read-state toggles remain explicit writes:

#### Mark read

1. Persist `read_progress = 100`
2. Persist `read_anchor = ""`
3. Set local completion lock

#### Mark unread

1. Persist `read_progress = 0`
2. Persist `read_anchor = ""`
3. Clear local completion lock

### 6.5 Article-only scope

The new anchor behavior applies only to article reader content.

Photos and videos keep the current behavior:

1. Auto-mark complete when appropriate
2. Do not store or restore a `read_anchor`

### 6.6 Original mode

Original mode remains outside scroll-based tracking scope in V1.

If the user switches from Reader mode to Original mode, MyDeck should preserve the last known reader snapshot from the current session and persist that on exit/background.

## 7. Data Model and Storage Changes

### 7.1 Domain model

Add a local `readAnchor` field to the domain bookmark model.

Recommended shape:

- `readAnchor: String`

Reason:

- The field is local and persisted
- Empty string is sufficient to represent "no anchor stored"

### 7.2 Room entity

Add `readAnchor` to `BookmarkEntity` as a local persisted field with default `""`.

Migration requirements:

1. Add the new column to the bookmarks table
2. Default existing rows to `""`
3. No index is required

### 7.3 DAO and mappers

Thread `readAnchor` through:

1. `BookmarkEntity`
2. `Bookmark`
3. `BookmarkMapper`
4. DAO upsert/update methods that currently rewrite bookmark metadata

### 7.4 Important sync decision: local-write-only anchor in V1

Readeck's bookmark list responses do not reliably provide `read_anchor`, and the field is omitted when empty.

Therefore V1 will treat `readAnchor` as:

- a local persisted field
- written by MyDeck when MyDeck saves reading position
- sent outbound in bookmark PATCH updates
- preserved across normal bookmark list syncs

V1 will not rely on regular `GET /bookmarks` sync responses to populate or clear local `readAnchor`.

This avoids accidental loss of a locally stored anchor every time metadata sync runs.

### 7.5 Remote merge rules

Because V1 treats anchor as local-write-only:

1. Regular bookmark sync must preserve existing local `readAnchor`
2. Local read-position updates may overwrite local `readAnchor`
3. Manual mark read/unread must clear local `readAnchor`
4. Remote metadata refreshes that do not explicitly carry a usable anchor must not clear local `readAnchor`

## 8. Offline Queue and Sync Changes

### 8.1 Payload model

Extend the existing progress payload to carry anchor data in a backward-compatible way.

Recommended change:

- Add optional `readAnchor: String? = null` to `ProgressPayload`

Reason:

1. Existing queued `UPDATE_PROGRESS` payloads remain decodable
2. No action-type migration is required
3. New payloads can carry both progress and anchor

### 8.2 Repository write API

Replace the repository's progress-only write with a read-position write.

Recommended API shape:

- `updateReadPosition(bookmarkId: String, progress: Int, readAnchor: String)`

If keeping `updateReadProgress(...)` for compatibility, it should delegate to the new method with `readAnchor = ""`.

### 8.3 Pending action semantics

For queued `UPDATE_PROGRESS` actions:

1. The latest queued snapshot wins
2. Progress and anchor must be updated together
3. Manual mark read/unread may still use their existing toggle action type

### 8.4 Sync request shape

When syncing a queued read-position update:

1. Send `read_progress`
2. Send `read_anchor` for progress values between 1 and 99 when available
3. Send `read_anchor = ""` when progress is 0 or 100

### 8.5 Local merge with pending actions

When bookmark metadata sync runs while a local queued progress update exists:

1. Preserve local `readProgress`
2. Preserve local `readAnchor`

This keeps offline read-position updates from being overwritten before they sync.

## 9. Reader Position Capture Design

### 9.1 New bridge

Add a new bridge object dedicated to reader position, for example:

- `WebViewReadPositionBridge`

Responsibilities:

1. Build a CSS selector for the currently visible leaf element in the article DOM
2. Resolve a stored selector back to a viewport position for restore

This should follow the same overall selector strategy used by native Readeck:

- leaf element
- relative to the tracked article container
- `tag:nth-child(n)>...` selector path

### 9.2 Local live anchor tracking

MyDeck should update the current in-memory anchor while the user reads, but should not persist it immediately.

Recommended behavior:

1. Keep the existing local progress tracking on scroll
2. Add a debounced local anchor refresh when scroll position changes and progress is between 1 and 99
3. Store the result in the ViewModel as the latest unsaved read anchor

Recommended debounce:

- about 250 to 500 ms after scroll settles

Rationale:

1. Avoid depending on a just-in-time JavaScript evaluation during `ON_STOP`
2. Keep server writes deferred until exit/background
3. Reuse the latest in-memory snapshot for all save paths

### 9.3 Snapshot structure

Introduce a dedicated local value object, for example:

```kotlin
data class ReaderPositionSnapshot(
    val progress: Int,
    val readAnchor: String,
)
```

This snapshot should be the unit used by:

1. local scroll tracking
2. exit/background save
3. offline queue writes
4. restore logic

## 10. Restore Behavior in MyDeck

### 10.1 Restore order

MyDeck should restore reader position in this order:

1. `progress == 0`:
   - no anchor lookup
   - restore to top
2. `progress == 100`:
   - no anchor lookup
   - restore to bottom
3. `progress in 1..99` and `readAnchor` present:
   - attempt anchor-based restore
4. If anchor restore fails:
   - fall back to percentage restore

### 10.2 UI timing

The existing "hide content until restore completes" behavior should be adapted so that anchor-based restore can complete before content is revealed.

This prevents a visible percentage-based jump followed by a second anchor correction scroll.

### 10.3 Fallback behavior

If the stored anchor cannot be resolved because:

1. content changed
2. selector no longer exists
3. the bridge fails

then MyDeck must:

1. fall back to percentage restore
2. keep the session functional
3. not clear the stored anchor immediately

The next successful save can replace it naturally.

## 11. Read-State Model: Rejected Alternative

### 11.1 Rejected option

Do not add a persisted `isRead: Boolean` field to local storage in V1.

### 11.2 Why it is rejected

It would require:

1. Room schema migration
2. Domain model expansion
3. DAO filter/query updates
4. Repository merge rules for conflicts between `isRead` and `readProgress`
5. Queue payload expansion or a second read-state action path
6. Additional invariants such as:
   - if `isRead == true`, must `readProgress` also be 100?
   - if `readProgress == 100`, can `isRead` ever be false?

This is redundant with the desired product behavior and would make sync correctness harder, not easier.

### 11.3 Approved alternative

Keep:

1. persisted `readProgress`
2. persisted `readAnchor`
3. derived `isRead` helper or UI property
4. local completion lock for in-session sticky behavior

## 12. Implementation Areas

The expected touch points are:

1. `app/src/main/java/com/mydeck/app/domain/model/Bookmark.kt`
2. `app/src/main/java/com/mydeck/app/io/db/model/BookmarkEntity.kt`
3. `app/src/main/java/com/mydeck/app/domain/mapper/BookmarkMapper.kt`
4. `app/src/main/java/com/mydeck/app/io/db/dao/BookmarkDao.kt`
5. `app/src/main/java/com/mydeck/app/io/db/model/ActionPayloads.kt`
6. `app/src/main/java/com/mydeck/app/domain/BookmarkRepository.kt`
7. `app/src/main/java/com/mydeck/app/domain/BookmarkRepositoryImpl.kt`
8. `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailViewModel.kt`
9. `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailScreen.kt`
10. `app/src/main/java/com/mydeck/app/ui/detail/components/BookmarkDetailWebViews.kt`
11. new file: `app/src/main/java/com/mydeck/app/ui/detail/WebViewReadPositionBridge.kt`
12. Room database version and migration files
13. tests covering ViewModel, repository, and bridge behavior

## 13. Testing Checklist

### 13.1 Core behavior

- Open article, scroll to about 75%, exit, reopen in MyDeck: restore near 75%
- Reopen same article, scroll back to about 25%, exit: saved indicator shows about 25%
- Reach 100%, exit, reopen: restore at end and show completed state
- Reach 100%, scroll back up, exit: still restore at end and remain completed

### 13.2 Anchor behavior

- Scroll to about 50% in MyDeck, exit, open in Readeck: Readeck shows about 50% and reopens near that location
- Save progress in MyDeck with missing/failed anchor capture: MyDeck still restores by percentage
- Save 0 or 100: anchor is cleared locally and in outbound payload

### 13.3 Offline behavior

- Read while offline, exit, confirm queued action exists with progress and anchor
- Sync later, confirm remote progress and anchor are applied
- Metadata sync while queued action exists must not erase local queued anchor/progress

### 13.4 Lifecycle behavior

- Toolbar back saves
- Android system back saves
- App switcher/home button triggers `ON_STOP` save
- App process death after backgrounding retains the latest saved snapshot

### 13.5 Manual read-state actions

- Mark read sets 100, clears anchor, locks progress
- Mark unread sets 0, clears anchor, unlocks progress

## 14. Risks and Open Questions

### 14.1 Exact selector parity

MyDeck should match Readeck's selector style closely, but byte-for-byte parity is not required if the resulting selector is stable and resolves correctly in both clients.

### 14.2 Inbound anchor sync from other clients

V1 does not guarantee exact inbound anchor parity from Readeck web sessions because normal bookmark list sync does not reliably provide `read_anchor`.

Accepted V1 behavior:

1. MyDeck publishes anchors outbound
2. MyDeck restores its own locally stored anchors
3. MyDeck still restores from percentage when no local anchor exists

### 14.3 Background anchor capture reliability

The spec recommends local in-memory anchor tracking during the session precisely to avoid relying on a fresh WebView DOM query during `ON_STOP`.

This should be treated as a requirement, not an optimization.

## 15. Summary

V1 should add local persisted `readAnchor`, keep `isRead` derived from `readProgress == 100`, track anchor locally during reading, and persist the combined snapshot only on exit/background or explicit read/unread actions.

This delivers:

1. current-position semantics below 100
2. sticky completion at 100
3. offline-safe queued read-position updates
4. correct reopen position in Readeck for mid-article progress
5. no redundant persisted `is_read` state
