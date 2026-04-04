# Branch Summary: feature/sync-multipart-v012

## Purpose
Document the work completed in this branch, the design decisions behind it, and the remaining follow-ups. This serves as a historical record before squash/merge.

---

## Summary of Major Changes

### 1) Multipart sync as the dominant content path
- Multipart `POST /bookmarks/sync` is now the primary path for metadata and offline content.
- Content packages are stored per bookmark, with HTML + resources managed via `ContentPackageManager`.
- Batch content fetch uses multipart batching (up to 10 IDs) with worker orchestration.

### 2) Managed offline reading redesign
- Offline reading is a **policy**, not per-bookmark micromanagement.
- Offline reading is **off by default**; when enabled, text is automatically stored locally.
- Image downloads are controlled by a global toggle.
- Offline scope is policy-based (`My List` vs `My List + Archived`).

### 3) Storage budgeting and eviction
- Users can set an **image storage limit** (100MB → 1GB or unlimited).
- When the limit is exceeded, oldest image-heavy packages are pruned via text-only overwrite.
- Eviction preserves HTML while clearing local images to stay within budget.

### 4) Reader performance and network-first UX
- Reader open/re-open performance optimized to minimize loading chrome when cached HTML exists.
- Network-first behavior remains intact for users who keep offline reading disabled.

### 5) Annotation rendering and refresh
- Multipart HTML may omit annotation attributes (immutable extraction output). The client enriches only when attributes are missing.
- Legacy `/bookmark/{id}/article` remains the preferred refresh path for fully attributed annotation markup.
- Annotation refresh bypasses freshness checks when needed to capture server-side changes.

---

## Key Design Decisions (and Rationale)

1. **Multipart as the canonical storage format**
   - Two HTML modes (absolute URLs vs relative local URLs) are supported in multipart without dual persistence.
   - Simplifies long-term architecture once multipart stability is proven.

2. **Offline reading defaults to off**
   - Keeps network-first behavior as the baseline experience.
   - Offline reading becomes an opt-in background policy instead of a manual workflow.

3. **Image downloads are optional and bounded**
   - Text HTML is cheap; image storage is the true cost.
   - Storage budgets apply to images only, not text content.

4. **Resource deletion requires HTML refresh first**
   - Prevents broken image references after images are removed.
   - Ensures HTML always matches the available resource mode.

5. **Annotation enrichment is conditional**
   - Multipart HTML may arrive without annotation attributes; enrichment is applied only when missing to preserve color/note context.
   - Legacy article HTML is used when possible to reduce mismatch risk.

---

## Notable Risks & Mitigations

- **Annotation mismatch risk** (multipart HTML vs annotations API)
  - Mitigation: legacy article HTML preferred; enrichment requires tests + guardrails.

- **Resource deletion ordering risk** (broken images if HTML not refreshed)
  - Mitigation: guardrails to block deletion unless HTML uses absolute URLs.

- **Atomic swap failures (DIRTY packages)**
  - Mitigation: enforce retry, avoid rendering DIRTY content.

---

## Verification Performed
- Local verification was run serially; failures encountered were due to disk space limits.
- Remaining verification should be re-run on a system with sufficient disk space.

---

## Follow-Up Work (Post-merge)

### Documentation updates
- In-app guide: offline reading default-off, image backfill, detail status strings, storage budget.

### Tests to add
- Annotation enrichment + refresh logic.
- Resource deletion order and text-only overwrite.
- DIRTY recovery behavior.

### Guardrails to implement
- Skip HTML overwrite if annotation enrichment mismatch is too high.
- Block resource deletion when HTML still uses relative local URLs.
- Enforce DIRTY retry/backoff on failure.

---

## Historical Notes
- This branch supersedes the granular per-bookmark offline model with a policy-driven approach.
- Managed offline reading spec open questions were resolved in this branch (multipart dominance + simplified sync status).
- Storage budgeting/eviction is now implemented and enforced during batch content sync.
