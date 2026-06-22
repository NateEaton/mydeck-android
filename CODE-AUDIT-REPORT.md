# Code Audit Report — Readeck for Android

> **Provenance / why this lives in the MyDeck repo.** This audit was run against
> the **Readeck for Android** codebase; the resulting cleanup (see
> [CODE-AUDIT-REMEDIATION.md](CODE-AUDIT-REMEDIATION.md)) was implemented and
> verified there, then **ported back to MyDeck** by patch. The two apps share the
> `com.mydeck.app` source root and were byte-identical across every file this
> report cites, so the findings apply verbatim to MyDeck — the only material
> cross-repo differences are branding (app name, icons, store copy), which this
> cleanup does not touch. The file paths and line numbers below are the
> audit-time Readeck values and matched MyDeck at port time. See the
> "Port provenance (MyDeck side)" addendum in the remediation plan for the
> executed port.

_Audit per the CODE-AUDIT.md spec (in the Readeck for Android repo). Full repo
sweep (no prior audit tag).
Scope: `app/src/main` Kotlin (228 files, ~43.5K LOC). Date: 2026-06-21._

---

## 1. Verdict

**MINOR** — Adopt; schedule a short cleanup pass (**est. ~1 day**).

The codebase is well-structured and largely free of machine-generated cruft: no
commented-out code, no copied/unattributed snippets, idiomatic Hilt/Room/Compose
boundaries, and a clean package rename (`de.readeckapp` → `com.mydeck.app`) with
zero dangling references. The one issue worth prioritising is **concentrated
copy-paste duplication** in the two largest, most-churned UI files
(`BookmarkCard.kt`, `AppShell.kt`): responsive layout variants replicate a large
identical action-row/context-menu block rather than sharing one composable, so
each card-action change must be made in ~5 places. Everything else is a low-risk
batchable cleanup. No adoption blockers.

---

## 2. Health Table

| # | Category | Status | Count |
|---|---|---|---|
| 1 | Dead / orphan code | Minor | 1 |
| 2 | Redundant implementation | **Issues** | 3 |
| 3 | Poorly developed algorithms | Clean | 0 |
| 4 | Inappropriate quoting / attribution | Clean | 0 |
| 5 | Hallucinated / vestigial scaffolding | Clean | 0 |
| 6 | Comment slop | Clean | 0 |
| 7 | Inconsistent conventions | Clean | 0 |
| 8 | Phantom robustness | Minor | 1 |

**Tooling note:** Duplication (category 2) was screened across the **whole repo
with `jscpd`** (4.93% duplicated lines, 81 clones) — the duplication gate is
tool-satisfied. Per the approved scope (jscpd only), **no Kotlin-aware
dead-code/lint tool (detekt) was run**; category-1 negative claims were verified
case-by-case with repo-wide `rg` (no imports, no call sites), so dead-code
findings are *verified for the flagged item but not exhaustive*. Categories
3/4/6/7 were checked with `rg` + manual review.

---

## 3. Findings (sorted by severity)

### [S2] Redundant implementation — `app/src/main/java/com/mydeck/app/ui/list/BookmarkCard.kt` and `…/ui/shell/AppShell.kt`

Responsive UI variants copy-paste large identical blocks instead of extracting a
shared composable. In `BookmarkCard.kt` the grid variants
(`BookmarkGridCardMobilePortrait`, `…Narrow`, `…Wide`) and compact variants
(`BookmarkCompactCardNarrow`, `…Wide`) repeat the same action-row + overflow
context-menu block — jscpd reports a **165-line exact clone** (`1370–1534` ↔
`1715–1879`) plus several 60–100-line clones. The same pattern recurs in
`AppShell.kt` across `CompactAppShell`/`MediumAppShell`/`ExpandedAppShell`
(106-line clone `461–566` ↔ `649–754`, plus two ~50-line clones).

```kotlin
// BookmarkCard.kt:1372 (identical at :1717 and other variants)
                // Action buttons
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    if (!isSelectionMode) {
                    Row(horizontalArrangement = Arrangement.Start) {
                        IconButton(onClick = { onClickFavorite(bookmark.id, !bookmark.isMarked) }, …) { … }
                        IconButton(onClick = { onClickArchive(bookmark.id, !bookmark.isArchived) }, …) { … }
```

**Why:** A large block duplicated across ~5 card variants (and 3 shell variants)
in the most-churned file means every action/menu change must be made in parallel
N times — actively impedes future change (category 2). _(The variants' parameter
lists have also drifted — e.g. `index` ordering, presence of `isInGrid` — a
symptom of the same copy-paste.)_

### [S3] Redundant implementation — `app/src/main/java/com/mydeck/app/domain/BookmarkRepositoryImpl.kt:144–182, 213–247, 298–336`

The Flow-building + row-to-domain mapping logic is repeated near-verbatim across
three query methods (`observeBookmarkListItems`, `searchBookmarkListItems`,
`observeFilteredBookmarkListItems`); jscpd flags 35–39-line clones between them.

```kotlin
// BookmarkRepositoryImpl.kt:144 (≈ identical at :213 and :298)
            .map { entities -> entities.map { it.toListItem() } }
            // …repeated Flow assembly / mapping scaffold across the 3 methods
```

**Why:** The same mapping/Flow scaffold reimplemented three times should be one
parameterised helper (category 2); low risk, batch with the cleanup pass.

### [S3] Dead / orphan code — `app/src/main/java/com/mydeck/app/ui/list/BookmarkCard.kt:2501–2536`

A custom top-level `CircularProgressIndicator(progress: Int, …)` is defined but
never called — repo-wide `rg` finds no import of
`com.mydeck.app.ui.list.CircularProgressIndicator` and no call site; every
consumer imports `androidx.compose.material3.CircularProgressIndicator` instead.

```kotlin
@Composable
fun CircularProgressIndicator(progress: Int, modifier: Modifier = Modifier) {
    val progressColor = Color.White
    Canvas(modifier = modifier) { /* draws a determinate arc */ }
}
```

**Why:** Defined-but-never-referenced symbol (category 1) that also shadows
Material3's identically-named composable for anyone working in this package;
safe to delete.

### [S3] Phantom robustness — `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailScreen.kt:323` (×4)

Exceptions are caught and silently discarded with no logging. Representative:

```kotlin
        } catch (e: Exception) {
            // Handle error silently or show snackbar
        }
```

Also at `BookmarkListScreen.kt:324` and `:389` (`// silent fail`) and
`LoadContentPackageUseCase.kt:458` (swallows an insert failure, no log).

**Why:** Swallowed exceptions hide failures (category 8); the
`BookmarkDetailScreen` comment also describes intended-but-undone work
("…or show snackbar"). At minimum log via Timber. _(Excluded as legitimate:
`UserRepositoryImpl.kt:202` best-effort credential cleanup in a logged failure
path; `WebViewSelectionScopeBridge.kt:36` and `WebViewAnnotationBridge.kt:412`
are **embedded JavaScript** `catch` blocks, not Kotlin swallows.)_

### [S4] Redundant implementation — cross-file dialog scaffolding (note only)

Small (~29–35-line) similar blocks across unrelated dialogs/screens
(`AutoSyncTimeframeDialog.kt:62` ↔ `ThemeDialog.kt:63`;
`UserGuideIndexScreen.kt:68` ↔ `UserGuideSectionScreen.kt:79`;
`BookmarkDetailsDialog.kt:491` ↔ `AddBookmarkSheet.kt:343`).

**Why:** Borderline-idiomatic Compose dialog scaffolding (category 2); noted as a
pattern, not worth enumerating or refactoring individually.

---

## 4. Incidental (non-slop)

None observed within scope.

---

## 5. Remediation Order

1. **Extract the shared card action-row + context-menu** into one composable used
   by all `BookmarkCard.kt` variants; apply the same to `AppShell.kt`'s shell
   variants (the S2 item — the only one worth prioritising).
2. Delete the unused `CircularProgressIndicator` (BookmarkCard.kt:2501–2536).
3. Add Timber logging to the four swallowed `catch` blocks.
4. Factor the repeated Flow/mapping scaffold in `BookmarkRepositoryImpl` into a
   helper.
5. (Optional) Revisit the cross-file dialog scaffolding only if those dialogs are
   touched again.
