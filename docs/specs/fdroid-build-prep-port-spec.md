# F-Droid Build Prep — MyDeck Port Spec

**Status:** Implemented
**Date:** 2026-06-24
**Branch:** `chore/fdroid-build-prep` (off MyDeck `main`)
**Source:** Readeck for Android commit `038899d` on branch `chore/fdroid-build-prep`
**Related:**
- `docs/porting/mydeck-readeck-port.md` (cross-repo port harness)
- SOURCE spec: `docs/specs/fdroid-build-prep-spec.md` in `readeck-android`

## 1. Summary

Port of the F-Droid build-prep change from Readeck for Android. Two
behaviour-preserving build changes that make the app F-Droid-buildable and
reproducible:

1. Remove the unused `BUILD_TIME` buildConfigField (reproducibility blocker).
2. Replace the JitPack-sourced `fr.bipi.treessence` (Treessence) with a
   self-contained `FileLoggerTree` backed by `java.util.logging.FileHandler`,
   and drop the JitPack repository.

No features, UI, schema, or string changes.

## 2. The Three Guardrails

### 2.1 Did MyDeck read `BuildConfig.BUILD_TIME`?

**No.** `rg BUILD_TIME app/src/` returned empty. MyDeck's About screen did not
use this field (unlike an intermediate state that would have required also porting
SOURCE's `PackageManager.lastUpdateTime` refactor). `BUILD_TIME` was removed
cleanly.

### 2.2 Was Treessence the only JitPack dependency?

**Yes.** `rg -i 'jitpack|com\.github\.' settings.gradle.kts gradle/libs.versions.toml`
returned only the Treessence entry and the JitPack URL. The JitPack repository
block was removed from `settings.gradle.kts`.

### 2.3 How was `aboutlibraries.json` handled?

**Surgical removal on MyDeck's own file.** The AboutLibraries export task does not
rewrite the committed JSON, so the Treessence entry (object at lines 3220–3242 in
the pre-change file) was removed surgically using an exact-match edit. JSON was
re-validated with `python3 -c "import json; json.load(...)"` — valid, 239
libraries remain (matching SOURCE's post-change count).

SOURCE's `aboutlibraries.json` was never copied or referenced — MyDeck's own file
was edited throughout.

## 3. Files Changed and How

| File | Classification | How applied |
|------|---------------|-------------|
| `app/src/main/java/com/mydeck/app/util/FileLoggerTree.kt` | New, self-contained | Added verbatim from SOURCE (same `com.mydeck.app` package) |
| `app/src/main/java/com/mydeck/app/MyDeckApplication.kt` | Likely-identical | Diffed against SOURCE pre-change: byte-identical → SOURCE post-change applied verbatim |
| `app/src/main/java/com/mydeck/app/util/LoggerUtil.kt` | Likely-identical | Diffed against SOURCE pre-change: byte-identical → SOURCE post-change applied verbatim |
| `app/build.gradle.kts` | Divergent (flavors, applicationId, versions) | Edited by intent: removed `BUILD_TIME` line and `implementation(libs.treessence)` line |
| `settings.gradle.kts` | Divergent | Edited by intent: removed the `maven { url = uri("https://jitpack.io") }` block |
| `gradle/libs.versions.toml` | Divergent | Edited by intent: removed `treessence` version entry (line 40) and library entry (line 110) |
| `app/src/main/res/raw/aboutlibraries.json` | Per-repo | Surgical removal of Treessence object; JSON re-validated |

**`MyDeckApplication.kt` / `LoggerUtil.kt` note:** Both files were byte-identical
to SOURCE's pre-change versions, so the SOURCE post-change edits applied without
any adaptation. This was verified via `diff` before editing.

## 4. Verification

- **Full debug gate green:** `:app:assembleDebugAll :app:testDebugUnitTestAll
  :app:lintDebugAll` — BUILD SUCCESSFUL (281 tasks).
- **Clean signal:** `rg -i 'jitpack|treessence|fr\.bipi'` returns only
  `FileLoggerTree.kt`'s doc comment (expected) and historical `docs/archive/`
  entries (not code). `rg BUILD_TIME` returns nothing.
- **JSON valid:** 239 libraries confirmed post-removal.
- **On-device smoke test:** Not run (optional per port spec); format verification
  and log rotation can be confirmed via `scripts/install-phone.sh` if desired.

## 5. Out of Scope

The F-Droid submission itself (recipe, `fdroid build` reproducibility verification,
MR to fdroiddata) is gated on a MyDeck 1.0.0 release and is not part of this
change. This change only makes the build *ready*.
