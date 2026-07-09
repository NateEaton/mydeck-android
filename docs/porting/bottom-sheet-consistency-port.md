# Port checklist — Bottom-sheet dismissal + affordance consistency

**Source:** Readeck for Android `enhancement/bottom-sheet-consistency` (commit `0000e13` — "feat: consistent bottom-sheet dismissal + affordances")
**Target:** MyDeck `main`
**Methodology:** `docs/porting/mydeck-readeck-port.md`

> **Direction note:** this change originated in **Readeck for Android**, so here SOURCE = Readeck, TARGET = MyDeck (the reverse of most earlier port docs). The source package is identical (`com.mydeck.app`), so files still copy verbatim.

## Branding deltas applied (§2)

**None.** The change is pure Compose UI/logic — no `applicationId`/flavor, no `app_name`, no OAuth constants, no brand-prefixed URLs or schemes, and no app name in the CHANGELOG wording. This is a copy-verbatim port with zero §2 edits.

## Files changed

### New files (verbatim)
- `app/src/main/java/com/mydeck/app/ui/components/BottomSheetDismiss.kt` — shared `CoroutineScope.dismissSheet(sheetState) { … }` helper: runs `sheetState.hide()` then invokes the close in `invokeOnCompletion`, so button/programmatic closes animate out like a swipe/scrim dismiss instead of vanishing instantly.

### Modified files (merged — baselines are byte-identical per §0.2, so the source version applies cleanly)
- `app/src/main/java/com/mydeck/app/ui/detail/ReaderSettingsBottomSheet.kt` — `SelectFontSheet`: added `rememberCoroutineScope`; route the top-right **Done** through `dismissSheet`. (Adds imports `rememberCoroutineScope`, `com.mydeck.app.ui.components.dismissSheet`.)
- `app/src/main/java/com/mydeck/app/ui/collections/CollectionEditorSheet.kt` — route **Cancel / Save / Delete** through `dismissSheet` (all close synchronously in the caller). (Adds the same two imports.)
- `app/src/main/java/com/mydeck/app/ui/components/FilterBottomSheet.kt` — route **Search** (incl. the keyboard IME action, via the `applyFilter` lambda) and **Reset** through `dismissSheet`. Same-package, so only `rememberCoroutineScope` import is added.
- `app/src/main/java/com/mydeck/app/ui/list/LabelsBottomSheet.kt` — (1) removed the `TopAppBar` back-arrow `navigationIcon` (affordance convention: no back arrow in a modal sheet) and dropped the now-unused `ArrowBack` import; (2) moved the `sheetState` declaration up (ahead of `commitSearch` / the item click handlers) so every dismiss path can reach it; (3) route the **Done** action, single-select label taps (item `onClick` and the search-commit `exactLabel` path) through `dismissSheet`. (Adds `com.mydeck.app.ui.components.dismissSheet` import.)
- `CHANGELOG.md` — `[Unreleased]` → `### Changed` bullet. Wording is brand-neutral; copy as-is (or reword to taste — MyDeck isn't named).

### Sheets deliberately NOT changed (audit result — port the same decision)
- **Add bookmark** and **Annotation edit** close via an async loading→success state transition (not an instant flag-flip), and already have no back arrow.
- **Annotations list** and **Reader settings (main sheet)** close only via drag handle / scrim, which already animates.
- **ShareActivity**'s inline sheet dismisses via `finish()` (activity teardown), no back arrow.

## §4 data-model check
No Room schema changes — §4 is a no-op.

## Pre-port sanity (§0.2)
Before copying, confirm MyDeck's current versions of the four modified sheet files match Readeck's pre-`0000e13` baseline (they should be byte-identical). If MyDeck has independently diverged in any of them (e.g. its own sheet edits), merge the `dismissSheet` routing by hand rather than overwriting, and surface the divergence.

## Verification
Run after porting:
```
./gradlew :app:assembleDebugAll :app:testDebugUnitTestAll :app:lintDebugAll
./scripts/install-phone.sh
```
On-device: open each changed sheet and confirm it **slides** closed (not blinks) when dismissed by its button — Select font (Done), Filter (Search / Reset), Save/Edit Collection (Save / Cancel / Delete), Label picker (pick a label / Done). Confirm the label picker no longer shows a back arrow.
