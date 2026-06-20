# Implementer prompt — Offline Content Rework (hand to an Opus thread)

You are implementing the **Offline Content Model Rework + Sync Reliability & Visibility** on the MyDeck Android app. The branch `feat/offline-content-rework` already exists and is checked out (off latest `main`). Do all work there.

## Read first (do not skip)
1. `docs/specs/offline-content-rework-spec.md` — the authoritative plan. Follow it.
2. `debug/2026-06-14/offline-sync-diagnosis.md` — the evidence behind it (code refs, repro logs, live DB).
3. `docs/archive/unified-offline-content-spec.md` and `docs/archive/offline-download-icon-and-text-cache-retention-spec.md` — original design intent. Note the "demote-to-text" tier was deliberately **shelved**; do not reintroduce it.
4. `CLAUDE.md` — project rules (localization across 10 language files, user-guide updates, verification tasks, branching, GitHub restrictions).

## How to work
- **Follow the phase ordering strictly: Phase 0 → Phase 1 gate → Phase 2.** Do not start Phase 2 until the A2/A1 decision is locked.
- **Phase 0 is a measured decision gate, not a formality.** Build the A2 (`PACKAGE_DIRECT`) open path behind a dev flag, add the `OPENPERF` instrumentation, run the comparison matrix on the device, and decide A2-vs-A1 against the criteria in §5. The central risk is whether a package-on-open can paint HTML before images finish — resolve that in the spike. Present the `OPENPERF` results table and your recommendation, and get maintainer sign-off on the decision before Phase 1.
- **Delegate intelligently** (you are the orchestrator):
  - Keep for yourself: the content-model/guard refactor (W1), the A2 spike + interpreting the perf data + the go/no-go call, idempotent create (W3), and notification/foreground-service compliance (W7). These are subtle or design-bearing.
  - Delegate to **Sonnet** subagents: well-specced units — DAO/query fixes + live count (W6), Clear-All path (W4), sync-reliability enqueue/return-value fixes (W5), debug-export enhancement (W8), icon tweak (W9), and the unit/Robolectric tests for each.
  - Delegate to **Haiku** subagents: mechanical work — driving the Pixel 9 over adb to collect/aggregate `OPENPERF` logs (Phase 0), adding new strings across all 10 language files, running the gradle verification tasks, simple boilerplate/renames.
  - Give each subagent a self-contained brief (the relevant spec section + acceptance tests). Review their output before integrating.
- **The core invariant** (both A1 and A2): guards must key off **content completeness** (full package present), never bare `contentState == DOWNLOADED`. The "stuck text-only" and "stuck HTML-only" classes must be impossible by construction. See §4.
- **The product decisions in §9.1 are locked** — Fork B (A2 → unified pool; A1 → form-based, text caches survive, no provenance flag), open-always-builds-a-package (A2 only; A1 open-while-offline-off = text cache), Clear-All scope (packages + text caches, not the Coil thumbnail cache), and the deferred LRU bound. Implement to them; do not re-litigate. The only items still genuinely open are settled *inside* the Phase 0 spike with data: the A2 first-paint strategy and the final latency thresholds — resolve those, then get maintainer sign-off on the A2/A1 gate before Phase 1.

## Guardrails
- Any new/changed user-visible string → `values/strings.xml` **and** all 10 language files (English placeholder). Any user-visible change → update `app/src/main/assets/guide/en/settings.md` / `your-bookmarks.md`.
- Before each commit run: `./gradlew :app:assembleDebugAll`, `:app:testDebugUnitTestAll`, `:app:lintDebugAll`. Keep commits focused per workstream.
- Do not `git push`, open/merge PRs, or run other state-changing `gh`/git remote commands — propose the exact commands for the maintainer to run.
- Keep changes repo-agnostic where feasible; this will be ported to the **Readeck for Android** repo afterward. Flag any MyDeck-specific divergence (package name, applicationId, notification channel ids).
- Scope discipline: do **not** build the user-facing duplicate-URL warning (`docs/specs/duplicate-url-detection-and-review-spec.md`) — W3 is silent background idempotency only. Do not reintroduce demote-to-text.
- Test device: Pixel 9 over wireless adb; build/install via `./scripts/install-phone.sh` (snapshot variant). The app data + a representative ~200-bookmark test account are already set up.

## Definition of done
- A2/A1 decision recorded with the `OPENPERF` data in the spec.
- Content model reworked; all §4.2 guards completeness-based; §4.3 partial-package rule in place.
- W3–W9 implemented, each with the tests named in §6/§8.
- The deterministic test matrix (§8) passes, verified via the enhanced debug export.
- Verification tasks green; localization + user guide updated.
- A short port note for the Readeck for Android repo.
