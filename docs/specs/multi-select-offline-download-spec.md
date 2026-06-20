# Spec: Offline pinning (Pin / Unpin)

**Status:** Revised 2026-06-18 — **supersedes** the earlier "multi-select *Available offline*"
framing (Stefan/maintainer feedback). Filename retained for continuity; the feature now spans
per-article **and** multi-select pinning. Branch: `feat/multi-select-offline`.
**Applies to:** MyDeck (`com.mydeck.app`) and, by port, **Readeck for Android** — shared
offline/list/reader code. Keep changes repo-agnostic.
**Depends on:**
- `offline-content-rework-spec.md` **W2** (Automatic vs Manual provenance) — pinned items are **MANUAL**.
- The existing **multi-select** feature and the **priority-download** path.
**Distinct from:** `duplicate-url-detection-and-review-spec.md` (separate work).

---

## 1. Summary

Reframe the offline-keep feature as **pinning**, with two retention tiers:

- **Managed** (`AUTOMATIC`): policy downloads **plus** articles cached on open. MyDeck manages
  this pool within the rolling window + absolute storage cap. **Prunable.**
- **Pinned** (`MANUAL`): articles the user **explicitly pins**. Protected from the policy prune;
  removed only by **unpin**, Clear All, disable-offline teardown, or the absolute cap.

Pinning is a deliberate, **reversible toggle** (favorite / mark-read style), available **per-article**
(reader overflow) and in **multi-select** (overflow toggle). This replaces the ambiguous, one-way
"Available offline" action and gives the user a real inverse (unpin) instead of only the nuclear
Clear All.

## 2. Model change — opening no longer pins

Previously, promote-on-open committed `MANUAL` (protected forever), conflating *incidental* opening
with *deliberate* keeping. Now **promote-on-open commits `AUTOMATIC`** (managed/prunable):

- Opening with offline storage on = **convenience caching** (joins the managed pool; prunable).
- Keeping = an **explicit pin**.
- Consequence: an opened-but-unpinned article may be reclaimed later by the window/cap; re-opening
  re-caches; pinning keeps it indefinitely.
- The offline-storage **hard line** is unchanged: with offline storage **off**, opening still only
  text-caches (a performance feature) and commits **nothing**.

## 3. Provenance & retention (W2)

- **Managed** (`AUTOMATIC`): subject to the policy prune window + absolute cap.
- **Pinned** (`MANUAL`): never policy-pruned; evicted only by **unpin**, Clear All, disable, or the
  absolute cap (LRU across both pools — the one mechanism that may evict pinned content).
- **Pin** an existing `AUTOMATIC` package → **flip in place to `MANUAL`** (no re-fetch).
- **Unpin** a `MANUAL` package → **demote to `AUTOMATIC`** — *non-destructive*: the content stays on
  disk and simply becomes prunable again (mirrors mark-unread). This dissolves the "clear just these"
  problem without a new destructive per-item API; an accidentally-pinned article is just unpinned and
  reclaimed normally.

## 4. Triggers / UX

### 4.1 Per-article — reader overflow (⋮)

- The reader overflow shows **one contextual item**: **Pin offline** when the article isn't pinned,
  **Unpin offline** when it is.
- **Pin:** download if not yet a committed package + set `MANUAL`; or flip an existing package to
  `MANUAL`. **Unpin:** demote `MANUAL` → `AUTOMATIC`.
- Hidden entirely when offline storage is disabled; shown but **greyed out** when the bookmark isn't
  pinnable — a no-content article, or an embed-only video with no transcript (§6). Driven by a
  reactive `pinEligible` flow that matches the Pin action's eligibility gate (so `hasContent`, which
  is true for a viewable embed, is *not* used).

### 4.2 Multi-select — overflow toggle

- The selection overflow shows a **contextual** entry: **Pin offline** when any *pinnable* selected
  item isn't pinned; **Unpin offline** when all *pinnable* selected items are already pinned (parallels
  the Archive/Favorite bar slots; driven by `MultiSelectTargets.selectedAllPinned`).
- The toggle decision **ignores ineligible (no-content) items** (2026-06-19): a no-content bookmark can
  never be `PINNED`, so counting it would permanently stick the label on "Pin offline." `selectedAllPinned`
  is therefore computed over the *pinnable* subset, backed by a new `BookmarkListItem.offlineEligible`
  flag (mapper-computed from `hasArticle`/type/`contentState`).
- **Stays in selection mode** after the action (favorite/archive parity) — does **not** exit.
- Hidden when offline storage is disabled (§6).

## 5. Per-item behavior & eligibility

**Eligibility** (unchanged, same gate as `LoadContentPackageUseCase`): `hasArticle = 1 OR type = 'photo'`
(includes videos carrying a transcript/article). **Exclude** `PERMANENT_NO_CONTENT` and embed-only
videos with no article.

**Pin**, per eligible item — keyed off **committed-package presence + source** (not image presence):
- No committed package → **enqueue** a priority download as `MANUAL`.
- Committed package, `AUTOMATIC` → **flip to `MANUAL`** (no re-fetch). *Works for image-less committed
  packages too (§9).*
- Committed package, `MANUAL` → **no-op**.
- Ineligible → **skip**, count for the snackbar.

**Unpin**, per item:
- Committed package, `MANUAL` → **demote to `AUTOMATIC`**.
- Managed / not-downloaded / ineligible → **no-op**, count.

## 6. Visibility — offline-storage toggle (REVISED 2026-06-18)

When offline storage is **disabled**, **all** pin/unpin entries (per-article **and** multi-select) are
**hidden** — not shown at all. This **supersedes** the prior "greyed out and inactive" decision
(2026-06-15). Rationale (maintainer 2026-06-18): hide rather than grey — an inactive reader-overflow
item is clutter, and pinning is meaningless without offline storage. No prompt-to-enable; the hard
line between offline-on and offline-off stands.

Within offline-on (2026-06-19): the **reader** item is shown but **greyed out** when the bookmark
isn't pinnable (no downloadable content — a no-content article or an embed-only video), signalling
"not applicable" rather than vanishing. **Multi-select** instead keeps its entry active and simply
excludes ineligible items from the toggle decision (§4.2) and skips them in the action (§5).

## 7. Feedback

- **Snackbar** summarizing the action: e.g. **"12 pinned offline"** / **"12 unpinned"**, with a skip
  count when some were ineligible — **"9 pinned offline · 3 have no offline content"**. The two counts
  sum to the selection (multi-select) or to 1 (per-article).
- **No Undo** (decided 2026-06-18): Pin/Unpin is its own inverse and multi-select **stays in
  selection mode**, so re-selecting and toggling back *is* the undo; the snackbar is informational
  only. This also sidesteps cancelling still-pending pin downloads. *May be revisited* — pinning can
  trigger background downloads (not a pure state flip), so a future Undo/cancel affordance is open.
- **Icon:** pinned items show a **pin** (📌, `Icons.Filled.PushPin`); managed full package = filled
  download icon; managed text-only = outline download icon; not downloaded = no icon.
- Multi-select **stays in selection mode**; the reader overflow flips its label Pin ↔ Unpin.

## 8. Labels / naming

- Action labels: **"Pin offline"** / **"Unpin offline"**.
- Settings + guide: **"Pinned"** replaces "Manual." Concept blurb: *Pinned articles stay on your
  device until you unpin them; everything else MyDeck manages automatically within your storage limit.*

## 9. Classification & the image-less package (revisits §3.1)

The pin action keys off **committed-package presence** (a `content_package` row), so a genuinely
image-less article (`hasResources = 0`, but a complete committed package) **pins correctly**
(flips `AUTOMATIC` → `MANUAL`). The pin **icon** likewise shows for any `MANUAL` committed package
regardless of images, so a pinned image-less article renders the pin badge (not the outline icon).

Unchanged / accepted as-is: a **managed** (`AUTOMATIC`) image-less committed package still renders the
text-only/outline icon until pinned. Fully aligning the managed full-vs-text distinction off package
presence (rather than `hasResources = 1`) — including the W2/W6 Settings counts — remains a future
cleanup, out of scope here.

### 9.1 Reactivity (2026-06-19)

Pin/unpin must reflect **live** on both surfaces, since a flip is a `UPDATE content_package SET source`
that does not touch the `bookmarks` table:

- **List icons:** both bookmark-list `@RawQuery`s now declare `observedEntities = [BookmarkEntity,
  ContentPackageEntity]`, so a source flip (pin/unpin from either surface) re-emits the list and the
  pin/download icon updates immediately. (Previously only `BookmarkEntity` was observed, so a flip
  silently went unreflected until some unrelated `bookmarks` write forced a re-query.)
- **Reader label/state:** `isPinned` and `pinEligible` are reactive flows (`ContentPackageDao.observePackageSource`
  / `BookmarkRepository.observeBookmark`), so the overflow label and enabled state always match the
  persisted state on open and update live — no manual refresh.

## 10. Out of scope / Future

- **"Pinned" filter chip** — deferred; the filter sheet is already crowded. "Is downloaded" covers the
  near-term need.
- **Nav-drawer "Available Offline" preset view** (for travelers) — promising future enhancement;
  tracked here, not built this round.
- **Immediate per-item "Remove offline content" / space reclaim** — unpin demotes (non-destructive);
  explicit per-item delete stays out of scope (Clear All + policy/cap reclaim space).
- **Override sync constraints** (download-now on cellular) — out of scope; pin respects
  `getContentSyncConstraints()` (wifi-only / battery-saver).
- **User-facing duplicate-URL warning** — separate spec.

## 11. Tests

- Pin enqueues only eligible **non-pinned** items as `MANUAL`; ineligible (`PERMANENT_NO_CONTENT`,
  embed-only video) skipped and surfaced in the snackbar count.
- Pin **flips** a committed `AUTOMATIC` package (incl. image-less) → `MANUAL`, **no re-fetch**.
- Pin **no-ops** an already-`MANUAL` item.
- Unpin **demotes** `MANUAL` → `AUTOMATIC` (content retained); no-op on managed / not-downloaded.
- promote-on-open commits **`AUTOMATIC`** (not `MANUAL`).
- Multi-select contextual label via `selectedAllPinned`; **stays in selection mode**.
- Pin/unpin entries **hidden** when offline storage disabled (per-article + multi-select).
- Pinned survives a subsequent **policy prune**; an unpinned (demoted) item becomes prunable.
- Respects **wifi-only / battery-saver** constraints.

## 12. Localization / docs

- New/renamed strings → `values/strings.xml` + all 10 language files (English placeholder).
- Guide: `your-bookmarks.md` (multi-select Pin/Unpin), `reading.md` (reader overflow Pin/Unpin);
  `settings.md` — "Pinned" relabel + concept blurb.

## 13. Port note (Readeck for Android)

Repo-agnostic — reuses the offline download pipeline, the multi-select UI, and the reader overflow.
No package-name / applicationId / channel-id divergence.
