# Port: F-Droid signingConfig fix + label-count pickers (from MyDeck v0.14.7)

**Status:** Ready to implement. Not yet applied.
**Direction:** SOURCE = MyDeck, TARGET = Readeck for Android (this repo).
**Methodology:** see [`mydeck-readeck-port.md`](./mydeck-readeck-port.md). This is the per-feature checklist (§6 there).
**SOURCE commits (on MyDeck `main`, shipped in v0.14.7):**
- `f5b8a24b` — fix: null-safe `signingConfig` lookups for F-Droid build compatibility (#231)
- `a143f1db` — fix: pass real label counts to Add Bookmark & Bookmark Details label pickers (#232)
- `a20eb25b` — fix: distinct OAuth callback scheme per non-production flavor (#230) — **see §0, do NOT port**

Target release: this port lands on Readeck `main`, then the user cuts **v1.0.0-rc7** separately (release process is out of scope for this port).

---

## 0. Scope: three v0.14.7 fixes make up the next RC — two port, one is already here

These three fixes shipped together in MyDeck v0.14.7 and together make up the content of Readeck's next release candidate (**v1.0.0-rc7**, cut separately by the maintainer after this port). Of the three, **only two need porting**; the third is already in this repo:

| Fix | MyDeck commit | Port? |
|---|---|---|
| signingConfig `findByName` (F-Droid) | `f5b8a24b` | **Yes** — apply to Readeck's 3 sites (§1) |
| Label-count pickers | `a143f1db` | **Yes** — cherry-pick / apply to 5 files (§2) |
| Per-flavor OAuth callback scheme | `a20eb25b` | **No — already in Readeck** (§0.1) |

### 0.1 Why #230 is already here
MyDeck's #230 was itself **ported *from* this repo** ("Ported from Readeck for Android" in its commit message). Readeck's `app/build.gradle.kts` already gives the `snapshot` flavor the distinct `readeck-snapshot` scheme, and `stable` keeps the default `readeck` scheme — the complete equivalent of MyDeck's change (Readeck has no `*Http` flavors, so there is nothing else to differentiate). **Verify-only:** confirm `manifestPlaceholders["oauthCallbackScheme"] = "readeck-snapshot"` and the matching `buildConfigField` are still present in the `snapshot` block; if so, #230 needs no work.

---

## 1. Fix #231 — null-safe signingConfig lookups (F-Droid build compatibility)

**Why it matters for Readeck:** F-Droid's build tooling strips the `signingConfigs { create("release") … }` block before building. `getByName("release")` then throws `SigningConfig with name 'release' not found` at configuration time. `findByName` returns `null` instead, degrading gracefully to debug-signing — exactly what already happens locally when `KEYSTORE` is unset. This is prerequisite work for Readeck's own eventual F-Droid submission and is behavior-preserving for all normal builds.

**Do NOT cherry-pick `f5b8a24b`** — it edits `app/build.gradle.kts`, whose flavor/signing block is a §2 branding-divergent file (MyDeck has 4 flavors, Readeck has 2). Apply the transform by hand to Readeck's **3 sites** (MyDeck had 5 because of its extra `*Http` flavors).

### Site A — `buildTypes { release { … } }` (Readeck build.gradle.kts ~line 76)
Replace:
```kotlin
            signingConfig = if (signingConfigs.getByName("release").storeFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
```
with:
```kotlin
            signingConfig = signingConfigs.findByName("release")?.takeIf { it.storeFile != null }
                ?: signingConfigs.findByName("debug")
```

### Sites B & C — the `snapshot` and `stable` flavor blocks (~lines 110 and 125)
In **each** flavor block, replace:
```kotlin
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
```
with:
```kotlin
            signingConfigs.findByName("release")?.takeIf { it.storeFile != null }?.let {
                signingConfig = it
            }
```

**Done check:** `grep -n 'getByName("release")' app/build.gradle.kts` returns nothing; `findByName("release")` appears 3 times.

---

## 2. Fix #232 — real label counts to Add Bookmark & Bookmark Details pickers

**What it fixes:** the label picker's sort-by-name/count and count chips only work when the caller passes a real `Map<String, Int>` (label → bookmark count). The Add Bookmark sheet (incl. the share-sheet "Save to Readeck" flow) and Bookmark Details' Edit Labels instead flattened counts to a `List<String>` and rebuilt the map with every count hardcoded to `0` — hiding count chips and making the sort toggle a no-op. This threads the real counts through end-to-end.

**Pure repo-agnostic UI logic — no branding.** All 5 files share Readeck's package `com.mydeck.app` and matching baselines, so **cherry-pick `a143f1db`** — it should apply clean:
```
git remote add mydeck /Users/nathan/development/MyDeck && git fetch mydeck
git cherry-pick a143f1db        # or: git cherry-pick -n, to fold into your own commit
git remote remove mydeck        # scratch remote, remove when done (methodology §3)
```
The commit also edits `CHANGELOG.md`; drop that hunk (§3 below re-adds it in Readeck's own `[Unreleased]`).

**If the cherry-pick conflicts** (minor line drift is possible), apply the same transform manually. In each of the 5 files, the change is mechanical:

- **`ui/list/AddBookmarkSheet.kt`** and **`ui/detail/BookmarkDetailsDialog.kt`** — rename the composable param `existingLabels: List<String> = emptyList()` → `existingLabelCounts: Map<String, Int> = emptyMap()` (both the public composable and its private `*LabelsSection`/`CreateBookmarkLabelsSection`), pass it through at the call site, and change the `labelOptions` builder:
  ```kotlin
  // before
  val labelOptions = remember(existingLabels, labels) {
      (existingLabels + labels).distinct().associateWith { 0 }
  }
  // after
  val labelOptions = remember(existingLabelCounts, labels) {
      existingLabelCounts + labels.associateWith { existingLabelCounts[it] ?: 0 }
  }
  ```
- **`ui/detail/BookmarkDetailScreen.kt`** — the `BookmarkDetailsDialog(...)` call: `existingLabels = labelsWithCounts.keys.toList()` → `existingLabelCounts = labelsWithCounts`.
- **`ui/list/BookmarkListScreen.kt`** — the `AddBookmarkBottomSheet(...)` call: `existingLabels = labelsWithCounts.value.keys.toList()` → `existingLabelCounts = labelsWithCounts.value`; and the private `AddBookmarkBottomSheet` wrapper's param + pass-through.
- **`ui/share/ShareActivity.kt`** — change the remembered state from `List<String>` to `Map<String, Int>`, assign `bookmarkRepository.observeAllLabelsWithCounts().first()` directly (drop the `.keys.toList()`), and thread `existingLabelCounts` through `ShareBookmarkContent`.

**Done check:** `grep -rn existingLabels app/src/main/java/com/mydeck/app/ui/{list,detail,share}` returns nothing (the only remaining `existingLabels` in the tree is `ui/components/LabelAutocompleteTextField.kt`, which is a different, unrelated param — leave it).

---

## 3. CHANGELOG (Readeck wording)

Add to the `[Unreleased]` `### Fixed` section of Readeck's `CHANGELOG.md` (adapt "MyDeck"→"Readeck" in prose; the label item names no brand):
```markdown
- **F-Droid build compatibility** — signing config lookups in the Gradle build now use a null-safe `findByName` instead of `getByName`, so the build no longer crashes when a build tool (like F-Droid's) strips the `release` signing config block. No change to normal build behavior.
- **Label sort by name/count** now works in the label picker shown from Add Bookmark (including the share-sheet "Save to Readeck" flow) and from the Bookmark Details page's Edit Labels — these previously showed no count chips and ignored the sort toggle because bookmark counts weren't passed through to the picker.
```

No new string resources, no data-model/Room change → methodology §4 is a no-op.

---

## 4. Verify

```
./gradlew :app:assembleDebugAll :app:testDebugUnitTestAll :app:lintDebugAll
```
All green. Optional device check via `scripts/install-phone.sh`: open Add Bookmark and Bookmark Details → Edit Labels, confirm labels show counts and the sort toggle reorders them. (No signing behavior to observe locally — #231 only manifests when a build tool strips the signing block.)

## 5. Branch / commit / release

- **Both fixes go on a single branch** off `main` (one push/PR cycle — do not split them into two branches). One or two commits on that branch is fine.
- **Commit this port doc** (`docs/porting/fdroid-signing-and-label-counts-port.md`) on the same branch — it is currently untracked in this repo and must not be left dangling. The MyDeck mirror already exists (placed untracked there by the maintainer, awaiting their next docs branch), so there is **no need to copy it back to MyDeck**.
- **Related specs:** scan MyDeck's `docs/` for any spec tied to these two fixes and mirror + commit it here if found. Expected result: none — both are reactive fixes with no dedicated feature spec (the earlier F-Droid *build-prep* spec is already mirrored as `docs/archive/fdroid-build-prep-spec.md`). Surface anything you do find rather than assuming.
- **Do not** run the release process — the maintainer cuts **v1.0.0-rc7** afterward (version bump, changelog move, any whatsnew/store metadata).
- Propose state-changing git/PR commands for the maintainer rather than running them.
