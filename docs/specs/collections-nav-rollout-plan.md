# Collections + Navigation Settings — Consolidated Rollout Plan

**Date:** 2026-06-27
**Coordinator thread:** this document is produced and maintained by the coordinator session.
**Target release:** MyDeck **v0.15.0** (headline: Collections). Port to Readeck for Android **v1.1.0** later.
**Source specs:**
- `docs/specs/collections-feature-design.md` (ships first)
- `docs/specs/navigation-settings-spec.md` (depends on Collections; ships second)
- `docs/porting/mydeck-readeck-port.md` (port harness; per-feature checklist built post-merge)

---

## 1. Purpose

A single, sequenced execution plan for rolling out Collections and then Navigation
Settings with engineering rigor and tight agent-usage efficiency. It defines:

- the **slice sequence** for each spec,
- the **six-phase cycle** every slice runs through,
- the **branch / PR model**,
- the **thread + sub-agent model** (how work is dispatched to keep the coordinator lean), and
- the **rebase protocol** for absorbing intervening hotfixes that may land on `main` first.

This plan is intentionally process-heavy because either feature may have to merge *on top of*
an unplanned hotfix before it ships.

---

## 2. Sequencing & dependencies

1. **Collections first.** `feat/collections` → PR → merge to `main`.
2. **Navigation Settings second.** `feat/navigation-settings`, branched from `main` **after**
   Collections merges. Nav hard-depends on Collections runtime API
   (`CollectionRepository.observeCollections()`, `BookmarkListViewModel.onSelectCollection(id)`,
   the `Collection` domain model). Do **not** start Nav coding against an unmerged Collections branch.

The hard ordering is non-negotiable and is stated in the Nav spec's header.

---

## 3. Branch & PR model

- **One feature branch per spec doc**, branched from freshly-pulled `main`:
  - `feat/collections`
  - `feat/navigation-settings` (cut only after Collections merges)
- The **coordinator creates each feature branch** before handing off the first slice prompt for it.
- Each **slice is committed** on the feature branch after its tests pass and the user signs off on
  the interactive check (focused, well-scoped commits).
- At branch end the coordinator + user **prepare the PR together**; the **user pushes and opens/merges
  the PR** (per repo rule: state-changing git/GitHub actions are proposed, not executed by agents).
- `gh` is for read-only inspection only, and always with `--repo NateEaton/mydeck-android`.

---

## 4. Thread & sub-agent model (efficiency)

**Coordinator (this thread):** stays lean — plans, cuts branches, hands off kickoff prompts, runs the
per-branch check-ins and PR prep. Does **not** do the bulk implementation reading/coding itself.

**Grouped threads (3 total).** Each thread is a fresh Claude Code session the user starts in this
working copy, on the branch the coordinator already created. Each thread runs **multiple slices** with a
commit + user check-in at every commit point:

| Thread | Branch | Slices |
|--------|--------|--------|
| **T1 — Collections backend** | `feat/collections` | C1 (data + repository + ViewModel) |
| **T2 — Collections UI** | `feat/collections` | C2 (drawer section), C3 (filter-sheet save + active banner) |
| **T3 — Navigation Settings** | `feat/navigation-settings` | N1 (data-driven refactor), N2 (model + offline + navItems), N3 (settings screen) |

**Sub-agent usage inside each thread (mandated in the kickoff prompt):** the thread's lead agent
delegates fan-out and mechanical work to **Sonnet or Haiku** sub-agents — e.g. scouting the codebase
(grep/ls before reads), propagating strings across the 10 locale files, writing repetitive unit tests,
and the light per-slice self-review. The lead agent reserves itself for design judgment, integration,
and the user check-ins. This keeps Opus tokens focused.

If a thread's context gets heavy (esp. T3), the coordinator may split it into a follow-on thread at a
commit boundary rather than letting one session bloat.

---

## 5. The standard per-slice cycle (six phases)

Every slice runs these phases in order. Phases 4 (interactive) and 6's depth scale to the slice.

1. **Design review.** Lead agent reads only the relevant spec section, then **scouts before reading**
   (grep/ls) to confirm current code shape. Surfaces any spec↔code mismatch to the user **before**
   coding (e.g. the Offline DAO must match the real list-source type — `@RawQuery`+`FilterFormState`
   vs `PagingSource`; confirm, don't assume). Drops inline **port-divergence flags** where package
   names, flavors, channel ids, branding, or DB version numbers will need transforming for the port.
2. **Coding.** Implement following existing patterns. New strings → `values/strings.xml` **and all 9
   locale files** with English placeholders. User-visible changes → update the English user-guide files.
3. **Automated batch tests.** Write/extend unit tests for the slice. Run the aggregate suite (§7).
4. **Interactive testing (user).** Only where there's a user-facing surface. The agent gives the user
   **concrete, numbered manual steps** and offers a device build via `./scripts/install-phone.sh`
   (Pixel 9). User runs on device and reports. Backend-only slices skip this.
5. **Verification cycle.** Re-run the three aggregate gradle tasks green (§7). Confirm **no DB migration
   version drift** (§8) and no lint/string-coverage regressions.
6. **Code review cycle (light, per-slice).** A Sonnet sub-agent reviews the slice diff against the spec
   section for correctness, reuse, and pattern-fit. Address findings. **Commit** after green + user
   sign-off.

**Per-branch deep review (hybrid model).** Once, at the end of each feature branch and before PR prep,
the **user runs `/code-review ultra`** (the coordinator cannot trigger it). This catches cross-slice
issues in one pass. Findings are triaged with the coordinator, fixed, re-verified, then PR prep.

---

## 6. Slice breakdown

### Branch 1 — `feat/collections`

Source: `docs/specs/collections-feature-design.md` (its §"Implementation Sequence" steps 1–14).

**Slice C1 — Data + repository + ViewModel (spec steps 1–9).** Backend only; no interactive surface.
- DTOs (`CollectionDto`, `CreateCollectionDto`/`UpdateCollectionDto`), four `ReadeckApi` methods.
- `Collection` domain model + `CollectionFilterAdapter` (bi-directional, with the field-mapping table —
  note the local-only `isLoaded`/`withLabels`/`withErrors` are dropped on persist, null on load).
- Room: `CollectionEntity`, `CollectionDao`, **DB v7→v8 + `MIGRATION_7_8`**, exported schema JSON,
  migration test.
- `CollectionRepository` interface + impl (paginate refresh, atomic cache replace; soft-delete via
  PATCH `is_deleted=true`). Hilt binding.
- `BookmarkListViewModel`: inject repo; add `selectedCollectionId`, `collections`, and the
  create/update/delete/select/clear/refresh functions.
- **Tests:** adapter round-trip, DAO queries, repo impl (success + error paths), VM collection logic.
- **Interactive:** none (covered by unit tests). Phase 4 skipped.

**Slice C2 — Drawer Collections section (spec step 10 + strings).**
- `AppDrawerContent` Collections section: header + `+` create, per-item `NavigationDrawerItem` with
  pin indicator, selected highlight, overflow menu (Edit / Duplicate / Delete + confirm dialog).
- Wire create/select/edit/duplicate/delete callbacks through `AppShell`.
- Strings for the section (10 locale files).
- **Interactive:** create a collection, select it (list filters), edit, duplicate, delete (+ confirm).

**Slice C3 — Filter-sheet save + active-collection banner (spec steps 11–12 + strings + docs).**
- `FilterBottomSheet`: "Save as Collection" outlined button (enabled only when
  `hasActiveFilters()`), name dialog.
- `BookmarkListScreen`: active-collection chip/banner below the FilterBar with edit + delete actions.
- Strings (10 files) + user-guide updates (`your-bookmarks.md`, and `reading.md`/`organising.md` only
  if behavior there changes).
- **Interactive:** apply a filter → Save as Collection → confirm it appears + selects; banner edit/delete.

> Note: spec lists "strings" and "tests" as terminal steps 13–14; this plan distributes them into each
> slice (strings/docs/tests land with the code that needs them), which is the lint- and review-safe order.

### Branch 2 — `feat/navigation-settings`

Source: `docs/specs/navigation-settings-spec.md` (its §"Implementation Sequence" steps 1–9).
Cut **after** Collections merges.

**Slice N1 — Data-driven drawer/rail refactor (spec step 1). Riskiest; lands first, no behavior change.**
- Introduce `NavItem`; convert `AppDrawerContent` + `AppNavigationRailContent` + `AppShell` to render
  from a `List<NavItem>` with a single `onSelect`, built from a hardcoded default that **reproduces
  today's drawer exactly**.
- **Interactive:** strict visual/behavioral **parity check** vs current build (drawer + wide-layout rail).
- Commit only after parity is confirmed on device.

**Slice N2 — Model + persistence + Offline view + navItems builder (spec steps 2–4).**
- `NavView` enum, `NavigationSettings` (+ `DEFAULT` reproducing today's drawer), `SettingsDataStore`
  JSON persistence with **tolerant deserialization** (unknown enums dropped, missing views default —
  port-safety).
- `DrawerPreset.OFFLINE`; offline DAO query (match the real list-source type — confirm in design review);
  `onSelectOfflineView()`; list-source branch.
- `navItems` builder in `BookmarkListViewModel` (group order, visibility filter, custom-view label =
  collection name, selection marking, deleted-collection → MY_LIST fallback).
- **Tests:** (de)serialization incl. dropping unknown/missing `NavView`; offline DAO (archived-with-
  package included, archive state ignored); navItems builder ordering/visibility/fallback.
- **Interactive:** light — enable offline view via a temporary hook or once N3 exists; primarily unit-tested.

**Slice N3 — Navigation settings screen (spec steps 5–8).**
- `NavigationSettingsRoute` + `NavigationSettingsScreen` + `NavigationSettingsViewModel` (UiSettings*
  pattern), `SettingsScreen` entry `ListItem`.
- Toggles with **min-one-standard-view** enforcement, within-group reorder, custom-view switch +
  `ExposedDropdownMenu` (+ empty-state "Add a collection…"), Offline switch.
- **Back-navigation** from "Add a collection…" → Collections → back to Nav settings (explicit
  `popUpTo`/return-route; keep explicit for the port).
- **Long-name handling** (ellipsis + full `contentDescription`) in dropdown + drawer/rail row.
- Strings (10 files) + user-guide (`settings.md` new Navigation section; `your-bookmarks.md` note).
- **Interactive:** toggle/reorder views, custom view selection + empty-state add-collection round-trip,
  offline view shows packaged bookmarks incl. archived, long-name ellipsis.

---

## 7. Verification commands (every slice, phase 5)

```
./gradlew :app:assembleDebugAll
./gradlew :app:testDebugUnitTestAll
./gradlew :app:lintDebugAll
```

Device build/install for interactive phases: `./scripts/install-phone.sh` (Pixel 9, wireless ADB).

---

## 8. Rebase / intervening-hotfix protocol

A hotfix may land on `main` before either branch merges. The protocol:

- **Highest-risk collision = the DB migration.** Collections bumps DB **v7→v8** with `MIGRATION_7_8`.
  If an intervening fix also bumps the DB version, renumber the Collections migration to the new
  `vN→vN+1`, regenerate the exported schema JSON for the new version, and re-run the migration test.
  The migration is isolated within C1 specifically to make this renumber cheap.
- When `main` moves, **rebase the feature branch on the new `main`** (coordinator proposes the commands;
  user runs pushes/force-pushes). Re-run §7 verification after any rebase.
- Nav's only schema-ish change is `DrawerPreset.OFFLINE` (an enum value, no migration) — low rebase risk.
- After any rebase, re-confirm string-coverage lint (intervening fixes may add strings to the locale set).

---

## 9. Localization & documentation obligations (every slice)

- New strings → `values/strings.xml` **and** all 9 locale folders (de, es, fr, gl, pl, pt, ru, uk,
  zh-rCN) with English placeholders. The lint check enforces full coverage.
- User-visible features → English user-guide files in `app/src/main/assets/guide/en/`
  (`your-bookmarks.md`, `settings.md`, `reading.md`, `organising.md` as applicable), updated in the
  same slice/commit as the code.

---

## 10. Porting (deferred artifacts; inline flags now)

Per the user's decision and methodology §8, the **port spec + port agent prompt are built after each
source PR merges**, finalized against shipped code. During coding, slice agents **must leave inline
port-divergence flags** (package root, flavor/variant task names, notification channel ids, branding
strings/assets, DB version numbers, locale set) so the post-merge checklist can be seeded from fresh,
accurate markers. The port itself runs **in the Readeck repo (TARGET)** following the §6 procedure and
§5 migration-renumber rule; do not hard-code the "MyDeck" brand anywhere that blocks the port.

---

## 11. Kickoff prompt template (coordinator → slice thread)

The coordinator delivers a concrete prompt per thread (not all at once), as raw markdown for the user to
paste into a fresh session. Each prompt includes: repo + branch (already created), the spec doc + exact
sections for the thread's slices, the six-phase cycle, the §7 verify commands, the sub-agent mandate
(Sonnet/Haiku for scouting/strings/repetitive tests/light review), the port-flag instruction, the
localization/docs obligations, and **explicit stop-and-check-in at every commit point** (do not plow
through multiple slices without the user's interactive sign-off).
