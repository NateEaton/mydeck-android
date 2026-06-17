# Spec: Multi-select "Available offline" bulk download

**Status:** Draft. Branch: `feat/offline-content-rework` (or a follow-on).
**Applies to:** MyDeck (`com.mydeck.app`) and, by port, **Readeck for Android** — shared offline/list code. Keep changes repo-agnostic.
**Depends on:**
- `offline-content-rework-spec.md` **W2** (Automatic vs Manual provenance) — the downloaded items are **MANUAL**.
- The existing **multi-select** feature (bookmark-list selection mode + overflow actions).
**Distinct from:** `duplicate-url-detection-and-review-spec.md` (not part of this work).

---

## 1. Summary

Add a bulk action to the bookmark-list multi-select overflow menu — **"Available offline"** — that downloads full offline content packages for the selected bookmarks. Downloaded items are **MANUAL** provenance (W2): they survive the policy prune and are removed only by Clear All, disable-offline (full teardown), or the absolute storage cap. This lets a user hand-pick a set to read offline in one gesture, instead of opening each one.

## 2. Trigger / UX

- In multi-select mode, the overflow (⋮) menu gains **"Available offline"** (string parallels the existing `bookmark_card_available_offline`).
- On tap, for each selected bookmark, enqueue a full content-package download **iff** it is offline-eligible and not already a full package.
- **Eligibility** (same as the offline policy): `hasArticle = 1 OR type = 'photo'`, including videos that carry a transcript/article (these download like articles — confirmed in field testing). **Exclude** `PERMANENT_NO_CONTENT` and embed-only videos with no article.
- **Already-downloaded** items: **no re-download** (no-op on the fetch). See §3 for the provenance flip on already-downloaded *Automatic* items.
- **No "download now vs later" prompt** (maintainer lean): the action just enqueues; work runs in the background via the existing priority/batch download workers, under the user's content-sync constraints.

## 3. Provenance (W2) — Manual pool

Multi-select "Available offline" always lands items in the **MANUAL** pool (W2 two-value model: `AUTOMATIC` vs `MANUAL`). Manual = never auto-pruned by the policy window; removed only by Clear All, disable-offline teardown, or the absolute storage cap. This is the **same** pool as promote-on-open (opening while offline reading is on) — there is no separate "Selected" tier.

- Newly downloaded items → **`MANUAL`**.
- Already-downloaded **`MANUAL`** item in the selection → full no-op.
- Already-downloaded **`AUTOMATIC`** item in the selection → **no re-fetch, but flip to `MANUAL`** (decided 2026-06-15: multi-select flips, so the user's explicit pick survives prune). *(Per W2, merely **opening** an AUTOMATIC item does **not** flip it — only this explicit action does.)*

### 3.1 Known limitation — image-less committed packages (2026-06-17)

The flip above triggers off the list item's `offlineState`, which classifies a package as
**full** only when it has image resources (`content_package.hasResources = 1`). An article that
genuinely has **no images** commits a complete offline package with `hasResources = 0`, which
`deriveOfflineState` renders as `DOWNLOADED_TEXT_ONLY` (the outline icon), not `DOWNLOADED_FULL`.

Consequences for this action on such an item:
- It is classified as "not yet a full package," so the action **enqueues** a download instead of flipping.
- That download hits the completeness guard in `LoadContentPackageUseCase.execute`
  (`contentState == DOWNLOADED && hasPackage → AlreadyDownloaded`), which does **not** rewrite
  `source` — so the item **stays `AUTOMATIC`** and is not protected from prune.

Decision (maintainer, 2026-06-17): **leave as-is.** This is a pre-existing classification choice
("full = has images") that predates this feature; no-image articles are rare (1 of 37 downloaded
items in field testing) and tiny. Documented here so the behavior is intentional, not a regression.
A future fix would key `deriveOfflineState` (and, for consistency, the W2/W6 Settings counts) off
**package-row presence** (`hasResources != null`) rather than image presence.

## 4. Offline-reading master toggle — RESOLVED (2026-06-15)

When offline reading is **disabled**, the "Available offline" overflow option is **greyed out and inactive**. Rationale (maintainer): the text caching that happens when a bookmark is opened with offline disabled is a **performance/efficiency** feature, *not* an alternative to offline storage (though it indirectly provides that benefit) — keep a **hard line** between offline enabled and disabled. No prompt-to-enable; the option simply isn't actionable until offline reading is on.

## 5. Feedback

- Snackbar summarizing the action, e.g. **"Downloading 12 bookmarks for offline"**; when some are skipped, **"12 queued · 3 have no offline content"**.
- Cards gain the MANUAL-colored "available offline" icon (W2) as packages land. **No modal/blocking progress** — downloads are background.
- Multi-select mode exits after the action, consistent with other bulk actions. [confirm against existing multi-select bulk-action behavior]

## 6. Constraints / reuse

- Reuse the priority-download enqueue path (`enqueuePriorityPackageDownload` / `BatchArticleLoadWorker.enqueuePriorityDownload`) per bookmark, or a small bulk variant, committing `source = MANUAL`.
- Respect `getContentSyncConstraints()` (wifi-only, battery saver). The pull-to-refresh-style "override constraints" path is **out of scope** (default: respect constraints).

## 7. Out of scope

- Per-item "remove offline content" (still global Clear All only, per the unified spec).
- A separate "Selected" provenance tier (folded into MANUAL — §3).
- User-facing duplicate-URL warning (separate spec).

## 8. Tests

- Action enqueues only eligible, non-full-package items as MANUAL; ineligible (`PERMANENT_NO_CONTENT`, embed-only video) skipped and surfaced in the snackbar count.
- Already-downloaded MANUAL → full no-op; already-downloaded AUTOMATIC → no re-fetch, flipped to MANUAL *(if W2(a) = yes)*.
- Downloaded items survive a subsequent policy prune (MANUAL).
- Respects wifi-only / battery-saver constraints.

## 9. Localization / docs

- New strings → `values/strings.xml` + all 10 language files (English placeholder).
- User guide: `your-bookmarks.md` (multi-select actions) gains the "Available offline" bulk action; the count/behavior is covered by W2 in `settings.md`.

## 10. Port note (Readeck for Android)

Repo-agnostic — reuses the offline download pipeline and the multi-select UI. No package-name / applicationId / channel-id divergence.
