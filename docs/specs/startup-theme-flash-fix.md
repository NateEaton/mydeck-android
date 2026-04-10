# Startup Theme Flash Fix — Technical Specification

**Status:** Implemented (2026-04-10)
**Affects:** All non-Paper/non-System-light startup scenarios (Sepia, Dark, Black)

---

## 1. Problem

When the app starts with the theme set to Sepia, Dark, or Black, the top app bar flashes the wrong colour for one frame immediately after the splash screen exits. The wrong colour matches the system light/dark setting (white in light mode, dark-grey in dark mode) rather than the user's chosen app theme.

The bug does **not** reproduce when the theme is Paper with System or Light mode, because the Paper colour scheme (`#F9F9F9`) is close enough to white that no flash is visible.

---

## 2. Root Cause

`MainViewModel.isReady` gates the splash screen on only `themeFlow`:

```kotlin
val isReady: StateFlow<Boolean> = settingsDataStore.themeFlow
    .map { true }
    .stateIn(started = SharingStarted.Eagerly, initialValue = false)
```

The `theme`, `lightAppearance`, and `darkAppearance` StateFlows all use `SharingStarted.WhileSubscribed(5000)` and carry default initial values (`Theme.SYSTEM`, `LightAppearance.PAPER`, `DarkAppearance.DARK`). They only begin collecting from DataStore once `collectAsState()` subscribes to them inside `setContent`.

The result: `isReady` becomes true as soon as `themeFlow` emits (very fast). The splash screen exits. `setContent` runs and subscribes to all three flows — but those flows haven't loaded their DataStore values yet, so they return their defaults. Compose renders the first frame using `Theme.SYSTEM + PAPER + DARK`, which resolves to the wrong appearance for users who have configured Sepia or Black. The correct appearance loads on the next DataStore emission (milliseconds later), but the damage is visible as a one-frame flash.

---

## 3. Fix

Two changes to `MainViewModel`:

1. **Change `isReady` to wait for all three flows** — splash screen is held until `themeFlow`, `lightAppearanceFlow`, and `darkAppearanceFlow` have all emitted at least once.

2. **Change `theme`, `lightAppearance`, `darkAppearance` to `SharingStarted.Eagerly`** — they begin collecting from DataStore immediately (before `setContent` subscribes), so their values are already loaded when `isReady` becomes true. When Compose renders its first frame, `collectAsState()` picks up the already-loaded values rather than the defaults.

```kotlin
val isReady: StateFlow<Boolean> = combine(
    settingsDataStore.themeFlow,
    settingsDataStore.lightAppearanceFlow,
    settingsDataStore.darkAppearanceFlow,
) { _, _, _ -> true }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

val theme = settingsDataStore.themeFlow.map { ... }
    .stateIn(started = SharingStarted.Eagerly, initialValue = Theme.SYSTEM)

val lightAppearance = settingsDataStore.lightAppearanceFlow
    .stateIn(started = SharingStarted.Eagerly, initialValue = LightAppearance.PAPER)

val darkAppearance = settingsDataStore.darkAppearanceFlow
    .stateIn(started = SharingStarted.Eagerly, initialValue = DarkAppearance.DARK)
```

The additional DataStore reads are negligible — all three keys are in the same Preferences DataStore file, which is read once on first access and cached in memory thereafter. The splash screen duration is not perceptibly affected.

---

## 4. Files Affected

| File | Change |
|------|--------|
| `MainViewModel.kt` | `isReady` waits for all three flows; `theme`/`lightAppearance`/`darkAppearance` use `Eagerly`. |
