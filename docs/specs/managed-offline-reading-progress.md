# Managed Offline Reading Progress

This document tracks implementation progress for the managed offline reading redesign defined in [managed-offline-reading-spec.md](/Users/nathan/development/MyDeck/docs/specs/managed-offline-reading-spec.md).

## Product Direction

- Default experience is network-first, similar to native Readeck.
- Offline reading is optional and off by default.
- When offline reading is enabled, MyDeck automatically keeps text content available offline for the selected scope.
- Image downloads are optional and managed as an enhancement to offline reading, not the default behavior.
- Content scope is policy-based, not managed per bookmark.
- Bookmark-card offline badges are no longer the focus; offline state belongs primarily in settings and bookmark details.

## Completed

- Added the new managed-offline design spec in [managed-offline-reading-spec.md](/Users/nathan/development/MyDeck/docs/specs/managed-offline-reading-spec.md).
- Reworked Sync Settings away from accordions and away from manual/date-range content sync controls.
- Added the new offline-reading model in settings storage:
  - offline reading enabled/disabled
  - offline scope: `My List` or `My List + Archived`
- Made disabling offline reading purge managed offline content immediately.
- Made archiving purge offline content automatically when scope is `My List`.
- Added initial support for changing the global image-download preference and reconciling already-managed content.
- Expanded offline-content deletion so legacy cached article HTML is cleared along with multipart packages.
- Updated the English settings guide for the new general direction.
- Aligned constraints and status behavior so Wi-Fi and battery-saver rules apply only to image downloads, not text-only offline maintenance.
- Made sync status reactive so content-related status changes when connectivity/work state changes.
- Improved settings language and layout:
  - Replaced technical "Scope" wording with user-facing "Available offline".
  - Kept the clear-content action available without giving storage its own oversized section.
- Constraints hide/show correctly based on the "Download images" toggle.

- Implement strategy for automatically pruning image resources when storage exceeds the preset limit. (Completed: uses `BatchArticleLoadWorker` texts-only overwrite strategy sorting by `created` timestamp to preserve HTML references while deleting local images).

## Confirmed Issues From Testing

- Turning off offline reading clears content immediately. This is working as intended.
- Turning offline reading on appears to work.
- Storage presentation should likely be folded into overall status, with the clear action moved below the stats.

## Remaining Work

### Reader and Content Behavior

- Remove offline-content state/status badges from bookmark cards in list. (Completed)
- Integrate the offline status explicitly into the bookmark details experience. (Completed)
- Move the reading progress badge on the line with the site name in Compact layout. (Completed)
- Improve bookmark open performance, especially repeat opens where cached/local content should feel instant (short-circuit loading chrome). (Next)
- Revisit the balance between multipart content and the legacy article endpoint so the architecture stays simple while still supporting:
  - network-first reading
  - text-only offline content
  - full text-plus-images offline content

### Storage Management

- Add image-only storage budgeting/eviction behavior.
- Ensure storage pressure removes images before text content.
- Decide whether storage controls remain simple presets or are fully automatic in v1.

### Future Enhancements

- Consider archived-favorites offline support after the basic scope model is stable.
- Add richer sync diagnostics only if needed after the simplified status model settles.

## Verification Checklist

After each implementation slice, run serial verification:

- `./gradlew :app:assembleDebugAll`
- `./gradlew :app:testDebugUnitTestAll`
- `./gradlew :app:lintDebugAll`

## Handoff Notes

- The current branch already contains substantial uncommitted work for the first managed-offline slice.
- Current verification status for the in-progress slice:
  - `:app:assembleDebugAll` passed
  - `:app:testDebugUnitTestAll` could not complete cleanly because the machine ran out of disk space
  - `:app:lintDebugAll` also failed because the machine ran out of disk space
- Current disk state at verification time was approximately `63 MiB` free on `/System/Volumes/Data`.
- If another model picks up from here, it should review both this progress document and the current worktree diff before continuing.
