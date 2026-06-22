# Code Audit Remediation Plan

Targeted plan to resolve the findings in [CODE-AUDIT-REPORT.md](CODE-AUDIT-REPORT.md).
Scope is **cleanup/refactor only** — no behaviour changes, no new features.

## Goals / Non-goals

- **Goal:** Remove the category-2 duplication and the small S3 items the audit
  found, leaving observable behaviour identical.
- **Non-goal:** No restructuring beyond the named files, no new abstractions
  beyond the extracted composables/helper, no dependency changes.
- **No new user-facing strings or UI** are introduced, so the localization and
  user-guide requirements in `CLAUDE.md` do **not** apply to this work.

## Shared conventions (all work items)

- **Branch** from `main` after `git pull` (one branch per work item, or one
  `chore/audit-cleanup` branch with one commit per item — see Sequencing).
- **Verification gate** for every item before commit:
  ```
  ./gradlew :app:assembleDebugAll
  ./gradlew :app:testDebugUnitTestAll
  ./gradlew :app:lintDebugAll
  ```
  (or `./scripts/ci-verify.sh`). Pure-refactor items must show **no new lint
  baseline entries** and an unchanged test pass count.
- **Behavioural equivalence is the acceptance bar.** WI-1 has no unit-test
  coverage of layout, so it is gated on the `@Preview` (`BookmarkCardPreview`,
  BookmarkCard.kt:2538) rendering identically plus a manual smoke test
  (`./scripts/install-phone.sh`) of: list + grid + compact layouts, favorite/
  archive/open/delete actions, selection mode, and both long-press menus
  (body + image).

---

## WI-1 — Extract the shared card/shell action + context-menu block  ·  S2 · ~4–6 hrs

**Finding:** Responsive variants copy-paste a large identical action-row +
context-menu block.
**Files:** `app/src/main/java/com/mydeck/app/ui/list/BookmarkCard.kt`,
`app/src/main/java/com/mydeck/app/ui/shell/AppShell.kt`.

### Current state
The block at `BookmarkCard.kt:1372–1530` (action Row → card-index overlay → two
`LongPressContextMenuDialog`s) is duplicated across `BookmarkGridCardMobile­Portrait`,
`BookmarkGridCardNarrow`, `BookmarkGridCardWide`, `BookmarkCompactCardNarrow`,
`BookmarkCompactCardWide` (jscpd: 165-line exact clone `1370–1534` ↔ `1715–1879`
plus 60–100-line clones). Variants also differ only in layout *above* this block;
the block itself is identical apart from drifted parameter lists.

### Proposed change
Extract two private composables in `BookmarkCard.kt` and call them from each
variant. Hoist menu-visibility state to the caller (the layout-specific
long-press triggers set it, so it cannot move fully inside the menus composable).

```kotlin
@Composable
private fun BookmarkCardActionRow(
    bookmark: BookmarkListItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: (String) -> Unit,
    onClickFavorite: (String, Boolean) -> Unit,
    onClickArchive: (String, Boolean) -> Unit,
    onClickOpenUrl: (String) -> Unit,
    onClickDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
private fun BookmarkCardContextMenus(
    bookmark: BookmarkListItem,
    showBodyMenu: Boolean, onDismissBodyMenu: () -> Unit,
    showImageMenu: Boolean, onDismissImageMenu: () -> Unit,
    onClickCopyLink: (String) -> Unit,
    onClickCopyLinkText: (String) -> Unit,
    onClickDownloadLink: (String, String) -> Unit,
    onClickShareLink: (String, String) -> Unit,
    onClickOpenInBrowserFromMenu: (String) -> Unit,
    onClickCopyImage: (String) -> Unit,
    onClickDownloadImage: (String) -> Unit,
    onClickShareImage: (String) -> Unit,
)
```

Each variant keeps its own `var showBodyContextMenu` / `showImageContextMenu`
and its layout-specific long-press triggers, but replaces the inline action Row
and the two menu dialogs with calls to the two new composables. While here,
**align the variant parameter lists** (consistent `index` / `isInGrid` ordering)
to remove the drift the audit noted.

Then apply the same extraction to `AppShell.kt`: factor the block duplicated
across `CompactAppShell`/`MediumAppShell`/`ExpandedAppShell`
(`461–566` ↔ `649–754`, plus `~314–364`/`385–434` clones — inspect to confirm
it is the nav-destination list / drawer content) into one shared composable.
Treat the cards and the shell as two independent commits.

### Acceptance criteria
- No remaining jscpd clone >40 lines inside `BookmarkCard.kt` or `AppShell.kt`
  for the extracted block (re-run: `npx --yes jscpd app/src/main/java --format kotlin --min-lines 40`).
- `BookmarkCardPreview` renders identically; manual smoke test per the shared
  conventions passes.
- Net line reduction in both files.

**Risk:** Medium — large UI surface, no automated layout coverage. Mitigate by
extracting verbatim (no logic edits) and diffing the preview before/after.

---

## WI-2 — Delete the dead `CircularProgressIndicator`  ·  S3 · ~5 min

**Finding:** Unused custom composable that shadows Material3's same-named one.
**File/lines:** `BookmarkCard.kt:2501–2536`.

### Change
Delete the function. Confirm no new caller has appeared since the audit:
```
rg -n 'com\.mydeck\.app\.ui\.list\.CircularProgressIndicator' app/src
rg -n --pcre2 'CircularProgressIndicator\(\s*progress\s*=' app/src/main
```
Both must return nothing. Remove any now-unused imports the deletion orphans
(e.g. `Canvas`, `drawArc`, `Stroke`, `StrokeCap`) **only if** not used elsewhere
in the file.

### Acceptance criteria
- File compiles; verification gate green; no behaviour change anywhere.

**Risk:** Very low.

---

## WI-3 — Log the swallowed exceptions  ·  S3 · ~30 min

**Finding:** Four `catch` blocks discard exceptions silently (one with a
"…or show snackbar" comment describing undone work).
**Locations:**
- `ui/detail/BookmarkDetailScreen.kt:323`
- `ui/list/BookmarkListScreen.kt:324` and `:389`
- `domain/usecase/LoadContentPackageUseCase.kt:458`

### Change
Replace each empty/comment-only body with a `Timber` log at an appropriate level
(`Timber.w`/`Timber.e` with the throwable), keeping current control flow:
```kotlin
} catch (e: Exception) {
    Timber.w(e, "….")   // state what was being attempted
}
```
Decide per-site whether the failure should also surface to the user (the
`BookmarkDetailScreen` comment suggests a snackbar was intended); if surfacing is
out of scope for this cleanup, drop the misleading comment and keep the log.

**Do not touch:** `UserRepositoryImpl.kt:202` (best-effort credential cleanup in
an already-logged failure path) and the JavaScript `catch` blocks in
`WebViewSelectionScopeBridge.kt:36` / `WebViewAnnotationBridge.kt:412` (embedded
JS, not Kotlin).

### Acceptance criteria
- No silent `catch (…) {}` / comment-only catch remains at the four sites;
  `Timber` is the only added dependency (already in use). Verification gate green.

**Risk:** Low.

---

## WI-4 — Factor the repeated Flow/mapping scaffold in the repository  ·  S3 · ~1–2 hrs

**Finding:** Near-identical Flow-assembly + row→domain mapping across three query
methods.
**File/lines:** `domain/BookmarkRepositoryImpl.kt` — `observeBookmarkListItems`
(`~111–184`), `searchBookmarkListItems` (`~184–247`),
`observeFilteredBookmarkListItems` (`~247–458`); clones at `144–182`↔`298–336`
and `150–184`↔`213–247`.

### Change
Read the three methods, identify the exact shared segment (the mapping/Flow
scaffold), and extract one private helper (e.g.
`private fun Flow<List<…Entity>>.toListItems(): Flow<List<BookmarkListItem>>` or a
parameterised builder). Each method calls the helper; method-specific query
selection stays inline.

### Acceptance criteria
- The three methods share one helper; existing repository unit tests pass
  unchanged (no test edits expected). Re-run jscpd to confirm the inter-method
  clones are gone.

**Risk:** Low–medium — covered by existing tests; keep the public method
signatures and emitted values identical.

---

## WI-5 — (Optional) Cross-file dialog scaffolding  ·  S4 · defer

**Finding:** Small (~29–35-line) similar blocks across unrelated dialogs.
**Action:** No work now. Address opportunistically only when one of those dialogs
is next modified; the similarity is borderline-idiomatic Compose and not worth a
dedicated refactor.

---

## Sequencing

Land low-risk items first to keep the high-risk one isolated and easy to revert:

1. **WI-2** (delete dead fn) — trivial, unblocks a cleaner BookmarkCard diff.
2. **WI-3** (log catches) — independent, fast.
3. **WI-4** (repository helper) — independent, test-covered.
4. **WI-1** (card extraction, then shell extraction as a second commit) — largest
   and riskiest; do it last, on its own, with the manual smoke test.
5. **WI-5** — skip.

**Recommended PR shape:** one PR `chore/audit-cleanup` with WI-2/3/4 as separate
commits, and a **separate PR** for WI-1 (cards + shell) so the high-risk UI
extraction can be reviewed and reverted on its own.

**Total effort:** ~1 day (WI-1 dominates; WI-2/3/4 ≈ 2–3 hrs combined).

**Definition of done:** all four items merged, verification gate green on each,
jscpd re-run shows the targeted clones resolved, and a manual smoke test confirms
the card/shell UI and context menus behave exactly as before.

---

## Addendum — Port the cleanup back to MyDeck (`com.mydeck.app`)

Follows the harness in [docs/porting/mydeck-readeck-port.md](docs/porting/mydeck-readeck-port.md).
**Execute only after** the readeck-android cleanup is merged **and on-device tested** —
do not port unverified work.

- **Roles (§1):** SOURCE = Readeck for Android (this repo, where the cleanup lands);
  TARGET = MyDeck at `../MyDeck`.
- **Why this is an unusually low-risk port:** at audit time the six touched files are
  **byte-identical** across both repos (same line counts; dead `CircularProgressIndicator`
  at the same line 2502; same swallowed-catch sites at `BookmarkDetailScreen.kt:324`,
  `BookmarkListScreen.kt:325`/`:390`). The cleanup is **pure refactor** — no schema, no
  strings, no manifest, no branding.

### Divergence map (§3), filled for this port

| Axis | Applies to this diff? |
|---|---|
| Source package root / imports | **N/A** — both repos use `com.mydeck.app`; no package rewrite |
| `applicationId` | **N/A** — cleanup touches no build config |
| Build flavors / variants | MyDeck = `githubSnapshot`/`…Http`/`githubRelease`/`…Http`; Readeck = `snapshot`/`stable`. **N/A to the diff** — no flavor-specific code changes |
| Aggregate verify tasks | **Identical** in both: `:app:assembleDebugAll`, `:app:testDebugUnitTestAll`, `:app:lintDebugAll` — reuse verbatim |
| Schema / migrations (§5) | **N/A** — no DB change |
| Strings / locales (§6 re-localize) | **N/A** — no new strings |
| Branding strings/assets | **N/A** — untouched |
| Remotes | SOURCE Codeberg `readeck/readeck-android`; TARGET GitHub `NateEaton/mydeck-android` |

### Cherry-pick vs manual (§4)

Recent histories have diverged (readeck-android was squashed into #1–#3; MyDeck retains
#203–#206) on separate remotes, so SHA-ancestry cherry-pick is not clean. But because the
TARGET trees are byte-identical, **apply by patch**: in readeck-android produce the cleanup
diff (`git format-patch` of the cleanup commits, or `git diff <base>..<tip> -- <paths>`),
then `git apply` / `git am` in MyDeck. Expect clean application. Fall back to manual
re-apply only for any file that has drifted by port time.

### Procedure

1. **Re-confirm identity first:** `diff` each of the six files between the repos before
   applying. If MyDeck has drifted on a file in the interim, switch that file to manual
   re-apply.
2. Apply **WI-2/3/4** patches first (trivial, independent), then **WI-1**.
3. Mirror the commit/PR structure (WI-2/3/4 together; WI-1 cards+shell on its own).
4. **Verify in MyDeck** with the identical aggregate tasks; re-run jscpd on
   `MyDeck/app/src/main/java` to confirm the same clones resolved; on-device smoke test via
   `MyDeck/scripts/install-phone.sh` (card/grid/compact layouts + both context menus).
5. Per MyDeck's repo rules, **propose** the state-changing git/PR commands for the
   maintainer rather than running them.

### Gotchas that do NOT apply here (so the porter doesn't chase them)

Package/import rewrite (same root), migration renumber (no schema), surgical string inserts
(no strings), branding divergence (untouched), manifest merge (untouched).

---

## Addendum — Port provenance (MyDeck side)

> The two sections above were written **in the Readeck for Android repo**, looking
> forward (SOURCE = Readeck, TARGET = MyDeck). This section, added **in the MyDeck
> repo**, records the port as actually executed. Both files now live in MyDeck for
> the historical record.

**The short version:** this audit and its four-item cleanup originated in **Readeck
for Android** — the audit was run there, the fixes (WI-1…WI-4) were implemented,
verified, and on-device tested there, and the result was then **ported back into
MyDeck** (this repo). The port carried over only the code; no audit was
independently re-run against MyDeck.

**Why the port is valid for MyDeck despite originating elsewhere.** The two apps
are a fork pair that share the **`com.mydeck.app` source root** (Readeck's package
was renamed to it — see the report's verdict). At port time every one of the six
touched files was **byte-identical** between the two repos, so the audit's
findings, line numbers, and remediation apply to MyDeck **verbatim**. The cleanup
is a **pure behaviour-preserving refactor** — no schema, no strings, no manifest,
no dependency changes. The codebases' only **material** divergences are
**branding** (app name, launcher icons, store copy), remotes, and build-flavor
names — and this diff touches none of them. That is precisely why the divergence
map above is all-N/A and the port reduced to `git apply`.

**How it was carried over (executed, not planned).**

- **Applied 100% by patch.** Each cleanup commit's `format-patch` output applied
  cleanly onto MyDeck (`HEAD` byte-identical to Readeck's pre-cleanup base), in the
  order WI-2 → WI-3 → WI-4 → WI-1. Every resulting file was then re-confirmed
  byte-identical to Readeck's already-tested post-cleanup tree.
- **Commit structure mirrored.** WI-2 / WI-3 / WI-4 landed as three separate
  commits; WI-1 (card + shell extraction) as its own commit so it stays
  independently revertible.
- **Verified in MyDeck** with the identical aggregate gate
  (`:app:assembleDebugAll`, `:app:testDebugUnitTestAll`, `:app:lintDebugAll`) —
  green, 0 lint errors, full unit-test suite passing. jscpd re-run confirmed the
  165-line BookmarkCard and 106-line AppShell clones resolved (only the expected
  ≤55-line residuals remain). WI-1 has no automated layout coverage, so an
  on-device smoke test (card/grid/compact layouts + both context menus) backstops
  it.

**Reusing this in the other direction.** A future MyDeck→Readeck (or repeat
Readeck→MyDeck) port follows the same harness in
[docs/porting/mydeck-readeck-port.md](docs/porting/mydeck-readeck-port.md): confirm
byte-identity per file, apply by patch where identical, and transform only the
branding/remote/flavor axes — which, as here, a behaviour-preserving cleanup will
usually leave untouched.

