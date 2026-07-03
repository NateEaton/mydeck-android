# Collections — per-feature port checklist (MyDeck → Readeck)

**Status:** Draft, untracked. Seeds a porting thread run **in the `../readeck-android` repo** (TARGET). Not needed until after the Collections PR closes.
**Direction:** SOURCE = MyDeck, TARGET = Readeck for Android (reverse of the earlier offline-content port).
**Read alongside:**
- `docs/porting/mydeck-readeck-port.md` — the direction-agnostic harness (roles, divergence map, migration reconciliation, procedure). Follow it.
- `docs/specs/collections-feature-design.md` — the UI/UX design + `FilterFormState` ↔ API field-mapping table.
- `docs/specs/collections-nav-rollout-plan.md` **§11** — the confirmed spec-drift deltas (authoritative; don't re-derive).

The harness relies on **git deltas applied byte-for-byte except at divergence points**. This doc supplies the Collections-specific delta inventory, the divergence points, and the "confirm this exists in Readeck first" list that the harness can't know.

---

## 1. Source changeset

The Collections feature is the entire delta from merge-base `cd88c37c` to the branch tip. Two equivalent ways to read it:
- **`main` squash-merge commit `81a78850`** — the whole feature as one commit (cleanest single diff for the port).
- **`feat/collections` branch** (14 commits, intact locally) — if you want the change grouped by slice.

`main` and `feat/collections` are content-identical (empty diff), so either works. `git diff cd88c37c 81a78850` is the canonical source diff.

**Scope:** port **Collections only**. The Navigation Settings work (N1–N3) is a *separate, not-yet-built* branch — explicitly out of scope here.

**Already present in Readeck:** the `reader_settings` nullable emergency fix (#214) that `feat/collections` was rebased onto. Do not port it.

---

## 2. File inventory

### New files — port ~verbatim (package rewrite only)
```
domain/CollectionRepository.kt
domain/CollectionRepositoryImpl.kt
domain/model/Collection.kt
domain/model/CollectionFilterAdapter.kt
domain/model/CollectionSortOption.kt
io/db/dao/CollectionDao.kt
io/db/model/CollectionEntity.kt
io/rest/model/CollectionDto.kt
io/rest/model/CreateCollectionDto.kt
ui/collections/CollectionsScreen.kt
ui/collections/CollectionEditorSheet.kt
test/domain/CollectionRepositoryImplTest.kt
test/domain/model/CollectionFilterAdapterTest.kt
test/io/db/CollectionDaoTest.kt
app/schemas/<DbClass>/18.json      # DO NOT copy — regenerate for Readeck's version (see §3)
```

### Modified shared files — apply the diff carefully (integration points; confirm Readeck parity §4)
```
AppModule.kt                       # Hilt @Binds CollectionRepository
io/db/DatabaseModule.kt            # register the migration
io/db/MyDeckDatabase.kt            # DB version bump + entity/DAO + MIGRATION_17_18  (class name + version differ)
io/rest/ReadeckApi.kt              # 5 endpoints (GET list, GET {id}, POST, PATCH, DELETE)
ui/components/FilterBottomSheet.kt # FilterControls: includeLocalOnlyFilters → localOnlyFiltersEditable + "Unavailable" controls
ui/components/FilterBar.kt         # layered-filter chip baseline = active collection's own filter
ui/list/BookmarkListScreen.kt      # active-collection app bar, Save-as/Edit/Delete overflow, sort menu, editor sheet host
ui/list/BookmarkListViewModel.kt   # collection state + create/update/delete/select/clear/refresh/sort
ui/navigation/Routes.kt            # CollectionsRoute
ui/shell/AppShell.kt               # collection count plumbing, Collections route, active-collection wiring
ui/shell/AppDrawerContent.kt       # Collections drawer entry + count Badge
ui/shell/AppNavigationRailContent.kt # Collections rail entry
worker/FullSyncWorker.kt           # best-effort refreshCollections() in the fan-out (periodic + Sync Now)
worker/LoadBookmarksWorker.kt      # best-effort refreshCollections() in the fan-out (app open + pull-to-refresh)
test/io/db/MyDeckDatabaseMigrationTest.kt  # migrate17To18CreatesCollectionsTable (renumber)
test/ui/list/BookmarkListViewModelTest.kt  # collection VM slices
res/values/strings.xml + 9 locale files    # new collection_* and filter_unavailable* strings
```

---

## 3. Migration reconciliation (highest risk — harness §5)

MyDeck added Collections as **DB v17 → v18**, `MyDeckDatabase.MIGRATION_17_18`, exported `app/schemas/<DbClass>/18.json`, test `migrate17To18CreatesCollectionsTable`.

In Readeck:
1. **Confirm Readeck's current DB version `N`** (it will differ). Implement `MIGRATION_N_(N+1)` with the same `CREATE TABLE collections (...)` body.
2. **Regenerate Readeck's own `(N+1).json`** — never copy MyDeck's `18.json`.
3. Register the migration in Readeck's database module; bump its `@Database(version=...)`.
4. Port the migration test, renumbered.
5. **Keep the columns identical**, including `has_errors` and `has_labels`. The bundled `openapi-spec.json` is **incomplete** and does not list those two — but they ARE server-supported (verified live). Do not drop them on that basis.
6. The migration SQL **omits `DEFAULT` clauses** so it matches Room's generated schema exactly (Room validates defaults). Carry that.

The `// PORT:` flags in `MyDeckDatabase.kt` (lines ~27 and ~322) mark the version + renumber points.

---

## 4. Confirm these exist / match in Readeck BEFORE porting

Collections is deeply integrated. If any of these diverges in Readeck, adapt rather than apply verbatim:

- **Filter system** — `FilterFormState`, `FilterControls`, `FilterBottomSheet`, `FilterBar`, `rememberFilterEditorState`. Collections reuses all of it (the editor sheet = FilterControls + name field; chips use a collection-vs-preset baseline). This is the biggest parity dependency.
- **`DrawerPreset` enum** — used as the filter baseline for chips and as the "no collection active" discriminator. Readeck may name its default view differently ("Deck"/"Unread" vs "My List"); the logic is preset-agnostic but the enum members must map.
- **Shell** — `AppShell`, `AppDrawerContent`, `AppNavigationRailContent`, and the **drawer count-`Badge` pattern** (Collections badge counts collections, mirroring how bookmark/highlight badges work).
- **Sync workers** — `LoadBookmarksWorker` and `FullSyncWorker`, and specifically their **fan-out pattern**: the Collections refresh is placed **alongside the Highlights backstop refresh**. If Readeck lacks Highlights or structures its workers differently, place the best-effort `refreshCollections()` at the equivalent post-metadata-sync point in each.
- **`ReadeckApi`** Retrofit interface + the global `Json` (kotlinx.serialization, `explicitNulls=false`). Note two API subtleties Collections depends on (design spec + rollout §11): POST returns `201 + Location` header (id parsed from it, then `getCollectionById` to hydrate); PATCH returns a **partial** summary (no id/href/created) so update is `Response<Unit>` + re-fetch; clearing a criterion needs **explicit nulls**, so the update body is a hand-built `JsonObject` via `toUpdateCollectionJson` (scoped — does not touch the global `Json`).
- **Room `Converters`** — `CollectionEntity` stores list fields as JSON arrays via the existing converters.

---

## 5. Testing gotcha (carry it, don't rediscover)

**MockK cannot mock a `suspend` function returning Kotlin's `Result` value class** (ClassCastException at call time). `CollectionRepository`'s suspend methods return `Result`, so the VM tests use a hand-written `FakeCollectionRepository`, not `mockk()`. Do the same in Readeck. (Worker tests may `mockk` the repo *only* because they explicitly stub `refreshCollections()` returning `Result.success(Unit)` — the crash is on the unstubbed default path.)

---

## 6. Branding surface (minimal)

The only MyDeck-specific tokens in the Collections code are the **package root** (`com.mydeck.app.*`) and the **`MyDeckDatabase` class name**. There are **no user-facing "MyDeck" brand strings** in the feature (confirmed) — the new strings are generic ("Collections", "New Collection", "Save as Collection", "Unavailable", etc.). Port them into Readeck's string set with English placeholders; real translations follow separately.

`// PORT:` flags in source: `CollectionEditorSheet.kt` (branding note), `MyDeckDatabase.kt` ×2 (version + migration renumber).

---

## 7. Verify (use Readeck's own task names — harness §3)

MyDeck uses `:app:assembleDebugAll`, `:app:testDebugUnitTestAll`, `:app:lintDebugAll`. **Confirm Readeck's equivalents** (the "All" aggregate tasks may be MyDeck-only) and run them green. Then device-verify: create (FAB + Save-as), select (active view + chips for layered filters), edit/rename, delete + Undo, sort, drawer count badge, cold-start badge population, and the "Unavailable" Length/Downloaded treatment in the editor.

Add the new strings to Readeck's locale set surgically (harness §6 warns against whole-file `strings.xml` restores).

---

## 8. What the design specs already give you (don't duplicate — reference)

- **collections-feature-design.md** — screen-based UI model, the `FilterFormState` ↔ API field-mapping table, component behavior, implementation sequence.
- **collections-nav-rollout-plan.md §11** — the confirmed deltas: DB v17→v18, kotlinx.serialization (not Gson), `has_errors`/`has_labels` server-supported & included, `Downloaded`(`isLoaded`) + `Length` device-local & excluded (shown "Unavailable"), Location-header create id, partial-PATCH re-fetch, real DELETE endpoint, `toUpdateCollectionJson` scoped nulls, collections sorted newest-first.
