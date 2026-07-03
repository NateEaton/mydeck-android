# Code Audit — v0.14.3 Delta Report

> **Cross-repo applicability.** This delta audit was run against **MyDeck**
> (`com.mydeck.app`), but it is intended to cover **both** MyDeck and **Readeck
> for Android**. Per the porting methodology in
> [docs/porting/](porting/) (see
> [mydeck-readeck-port.md](porting/mydeck-readeck-port.md)), every change made to
> this codebase since the original audit was mirrored into the sibling repo —
> commit-for-commit, behaviour-identical, differing only in branding (app name,
> launcher icons, store copy), remotes, and build-flavor names. The
> per-feature port records are at
> [docs/porting/rc3-enhancements-port.md](porting/rc3-enhancements-port.md),
> [docs/porting/with-errors-sync-port.md](porting/with-errors-sync-port.md), and
> the fdroid build-prep spec. The two trees remain byte-identical across every
> file this report cites, so the findings, line numbers, and verdict apply to
> both repos **verbatim**. No separate audit of the Readeck repo is required.

_Delta audit per the CODE-AUDIT.md spec (8-category slop/health sweep). Scope:
**only code changed since the original code-audit cleanup** (commit `81e00db9`,
"Cleanup/code audit port" #207). Method: `jscpd` whole-repo duplication gate,
`rg` + per-diff manual review against the 8 categories. Date: 2026-06-27._

See the originating audit and its remediation for baseline context:
[docs/archive/CODE-AUDIT-REPORT.md](archive/CODE-AUDIT-REPORT.md),
[docs/archive/CODE-AUDIT-REMEDIATION.md](archive/CODE-AUDIT-REMEDIATION.md).

---

## 1. Verdict

**CLEAN** — Adopt as-is for the v0.14.3 release. No cleanup pass required to finalize.

The original audit's cleanup **held**: the duplication it removed has not been
reintroduced, and the most-churned file from that audit (`BookmarkCard.kt`,
modified again in both #210 and #211) correctly reuses the extracted shared
composables. Whole-repo duplication dropped from the audit-time **4.93% / 81
clones** to **0.94% / 8 clones**, with every residual at ≤58 lines and every one
of them **pre-existing** rather than introduced by the new work. Net-new code
(logging, settings, the "with errors" sync fix) is healthy. Two trivial,
optional nits surfaced — a pair of unused interface getters and a latent
thread-safety note in the new file logger — neither of which blocks the release.

**Remediation status (`chore/v0.14.3-cleanup` branch, ported from readeck-android `65df83b`):**
the dead getters are now **removed**; the `SimpleDateFormat` item is **deferred**
as a tracked, non-blocking follow-up. See §3 and §6.

### Scope of changes audited

| Commit | Title | Code-bearing? | Audited |
|---|---|---|---|
| `9fa850d1` (#208) | nav-settings spec | No — docs only | Excluded |
| `6296ccc6` (#209) | fdroid-build-prep | Yes — logging, build | ✅ |
| `748b798f` (#210) | rc3-enhancements | Yes — large feature set | ✅ |
| `aa57a407` (#211) | "with errors" filter fix | Yes — repo/DAO/card | ✅ |

---

## 2. Health Table (delta only)

| # | Category | Status | Count |
|---|---|---|---|
| 1 | Dead / orphan code | Minor | 1 |
| 2 | Redundant implementation | Clean (cleanup held) | 0 new |
| 3 | Poorly developed algorithms | Clean | 0 |
| 4 | Inappropriate quoting / attribution | Clean | 0 |
| 5 | Hallucinated / vestigial scaffolding | Clean | 0 new |
| 6 | Comment slop | Clean | 0 new |
| 7 | Inconsistent conventions | Clean | 0 |
| 8 | Phantom robustness | Minor (latent) | 1 |

**Duplication gate (jscpd, `--min-lines 40`):** 8 clones / 394 duplicated lines
(**0.94%**), down from the audit baseline of 4.93% / 81 clones.
`BookmarkRepositoryImpl.kt` now shows **0 clones** — the WI-4 helper factoring
held. All 8 residual clones are ≤58 lines and pre-date these three commits (see
§4). No new Kotlin-aware dead-code tool (detekt) was run, per the original
scope; category-1 negatives were verified case-by-case with `rg` (no call
sites).

---

## 3. Findings (sorted by severity)

### [S4] Dead / orphan code — `io/prefs/SettingsDataStore.kt`, `SettingsDataStoreImpl.kt` — ✅ FIXED in `chore/v0.14.3-cleanup`

Two one-shot suspend getters added in #210 are declared on the interface and
implemented, but have **no call sites** anywhere in `app/src` — `rg` finds only
the declaration and the `override`. Consumers
(`LabelSearchSettingsViewModel`) collect the flow-based equivalents
(`labelSearchMatchingFlow`, `labelSearchSortFlow`) instead.

```kotlin
suspend fun getLabelSearchMatching(): LabelSearchMatching   // never called
suspend fun getLabelSearchSort(): LabelSearchSort           // never called
```

**Why:** Defined-but-never-referenced interface surface (category 1). Harmless
but dead; safe to delete both the declarations and their `override`
implementations. Low urgency.

**Resolution:** both interface declarations and their `override` implementations
were deleted on the `chore/v0.14.3-cleanup` branch (ported from readeck-android `65df83b`).
No call sites, tests, or mocks referenced them (re-verified with `rg` across
`app/src`).

### [S4] Phantom robustness (latent) — `util/FileLoggerTree.kt:107`

The new file logger holds a single `SimpleDateFormat` instance on its
`LineFormatter`. `SimpleDateFormat` is not thread-safe, and Timber may call
`log()` from any thread.

```kotlin
private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss:SSS", Locale.US)
```

**Why:** In practice the format call happens under JUL's
`StreamHandler.publish()` lock, so this is **latent rather than currently
broken** — but if the handler config ever allowed concurrent calls it would
silently produce corrupt timestamps (category 8). Note only; `Locale.US` is the
correct choice for a log file. If touched, switch to a thread-local or
`DateTimeFormatter`.

---

## 4. Incidental (pre-existing, NOT regressions)

These were surfaced by the re-run but **predate** #209–#211 and are recorded so a
future porter does not mistake them for new slop:

- **`BookmarkCard.kt:956–1011` ↔ `2122–2175` (56-line clone).**
  `BookmarkGridCardMobilePortrait` inlines its own action row instead of calling
  the extracted `BookmarkCardActionRow`. This carryover pre-dates the original
  audit; #210 kept the two copies **in sync** when adding the new
  `onClickEditLabels` button, so it neither introduced nor worsened the clone.
- **`AppShell.kt:314–364 ↔ 522–572` (51 lines) and `385–434 ↔ 594–643`
  (50 lines).** Detail-route transition block and nav-event handler duplicated
  across the phone vs. tablet/rail NavHosts. Unchanged since the audit baseline.
- **`BookmarkListScreen.kt:1499–1556 ↔ 1620–1677` (58 lines).** `when
  (layoutMode)` dispatch duplicated across two layout branches. Pre-existing.
- **`BookmarkRepositoryImpl.kt:~1215` "parse JSON here until we refactor the
  DAO" comment.** Tech-debt note that predates all three commits.

---

## 5. Regression checks (all green)

- Dead `CircularProgressIndicator` (deleted by the original WI-2) **did not
  return** — `rg 'fun CircularProgressIndicator'` finds nothing.
- No new silent / comment-only `catch` blocks in the changed files. The new
  `refreshServerErrorFlags()` (#211) correctly re-throws `CancellationException`
  and logs the rest via `Timber.w`, matching the surrounding convention.
- #210 actively **removed** dead code (the orphaned `OfflineStateIndicator` /
  `CompactOfflineStateIndicator` composables) — a net cleanliness gain.
- New strings added in #210/#211 are present across all 10 locale files per the
  project's localization rule (not an audit category, noted for completeness).

---

## 6. Remediation Order (optional — none required for v0.14.3)

1. ✅ **Done** (`chore/v0.14.3-cleanup`) — deleted the two unused `getLabelSearch*` getters
   and their `override`s.
2. (Deferred) Revisit `FileLoggerTree`'s `SimpleDateFormat` only if the
   logging path is next touched — tracked, non-blocking follow-up.
3. (Defer) The pre-existing `BookmarkGridCardMobilePortrait` inline action row
   could fold into `BookmarkCardActionRow`, but only if that card is reworked —
   it is not new debt.

**Definition of done for v0.14.3:** nothing. The branch is clean to finalize; the
two findings above are tracked as low-priority follow-ups, not release blockers.
