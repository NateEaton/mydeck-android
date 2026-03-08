# Design Spec: Server Info Section in About Screen

**Status:** Design / Pre-implementation
**Branch:** `claude/design-server-info-section-Nfsq8`
**Date:** 2026-03-07 (amended)

---

## Overview

Enhance the About screen's "System Info" section to display both app and server information under separate subheadings. Each subheading will show a brief summary line, with an expandable card revealing full diagnostic details and a copy-to-clipboard action — mirroring the pattern used on the Readeck web UI's "About Readeck" page.

Server info is **cached to persistent storage on first sign-in** and shown immediately on the About screen. It is **refreshed from the network only when the About screen is opened and a network connection is available**, keeping the experience fast and offline-friendly.

---

## Current State

The System Info section renders four flat lines of text:
- App version + build code
- App build timestamp
- Android OS version
- Device manufacturer + model

The `GET /api/info` endpoint is **already integrated** (called during login to verify OAuth support) but its response is discarded after that check. It returns:
- `version.canonical` — Full version string (e.g., `"0.22.1"`)
- `version.release` — Major.minor.patch component
- `version.build` — Commit info on nightly builds; empty on stable releases
- `features` — List of enabled features (e.g., `["oauth", "email"]`)

---

## Proposed Layout

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
System Info
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  App                                  ▼
  Version 1.2.3 (build 456)

  ┌──────────────────────────────────┐
  │ Version:   1.2.3 (456)           │
  │ Built:     2026-03-06 13:08:25   │
  │ Android:   15 (API 35)           │
  │ Device:    Google Pixel 9        │
  │                                  │
  │ 📋 copy to clipboard             │
  └──────────────────────────────────┘

  Server                               ▼
  Version 0.22.1

  ┌──────────────────────────────────┐
  │ Version:   0.22.1                │
  │ Release:   0.22.1                │
  │ Build:     (stable release)      │
  │ Features:  oauth, email          │
  │                                  │
  │ 📋 copy to clipboard             │
  └──────────────────────────────────┘

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**Collapse/expand behaviour:**
- Both cards start **collapsed** (detail hidden, only summary line visible).
- Tapping the subheading row (or a chevron icon) toggles expansion with a standard Material 3 `AnimatedVisibility`.
- The two cards are independent — both, either, or neither may be open at once.

**Server card display states:**
1. **Cached data available** — Show canonical version as summary immediately (no loading spinner).
2. **No cache yet** — Show a `CircularProgressIndicator` in the summary area while the initial fetch runs.
3. **Refreshing in background** — Cached data is shown; the refresh is silent (no spinner). If the refresh succeeds, the UI updates; if it fails, the cached data remains.
4. **Cache empty and fetch failed** — Show a short error string (e.g., "Could not load server information").
5. **No network, no cache** — Show "No server information available" (not an error state; expected offline condition).

**Copy to clipboard:**
- Appears inside the expanded card, below the detail rows.
- Copies the formatted multi-line text block to the system clipboard.
- Shows a transient "Copied!" `Snackbar` confirmation (or a brief icon state change).

---

## Caching Strategy

### Storage mechanism

Use `SettingsDataStore` (backed by encrypted `SharedPreferences`) — the existing pattern for all persistent app metadata. Four new keys store the server info components:

```
KEY_SERVER_INFO_CANONICAL   → String   (e.g., "0.22.1")
KEY_SERVER_INFO_RELEASE     → String   (e.g., "0.22.1")
KEY_SERVER_INFO_BUILD       → String   (e.g., "" or "175-g154ad5c1")
KEY_SERVER_INFO_FEATURES    → String   (comma-separated, e.g., "oauth,email")
```

No new database tables or data classes beyond a small helper model are needed.

### Write points

| Event | Action |
|---|---|
| **Sign-in (`initiateLogin`)** | After a successful `GET /api/info` (already happens), save the response to `SettingsDataStore` before returning. This is the "cache on initial sign-in" write. |
| **About screen opened, network available** | `AboutViewModel` fetches fresh info and overwrites the cache on success. Failures are swallowed; cached data stays. |
| **Logout (`clearCredentials`)** | Clear the four server info keys alongside all other credentials so stale info from a previous server doesn't appear for a new user. |

### Read point

`AboutViewModel.init` reads the cache immediately and populates `UiState` before launching any network call. The UI always renders the fastest available data first.

### `SettingsDataStore` interface additions

```kotlin
// New domain model (tiny, no serialization library needed)
data class CachedServerInfo(
    val canonical: String,
    val release: String,
    val build: String,       // empty string on stable releases
    val features: List<String>
)

// New interface methods
suspend fun saveServerInfo(info: CachedServerInfo)
suspend fun getServerInfo(): CachedServerInfo?   // null if never cached
suspend fun clearServerInfo()                     // called from clearCredentials()
```

`clearCredentials()` in `SettingsDataStoreImpl` gains one additional line calling `clearServerInfo()`.

---

## Architecture Changes

### 1. `UserRepositoryImpl.kt` — cache on sign-in

In `initiateLogin()`, after the existing successful `getInfo()` check (line 57), save the result before proceeding:

```kotlin
val info = if (infoResponse.isSuccessful) infoResponse.body() else null
// ... existing null/OAuth checks ...

// NEW: cache server info for later display
settingsDataStore.saveServerInfo(
    CachedServerInfo(
        canonical = info.version.canonical,
        release   = info.version.release,
        build     = info.version.build,
        features  = info.features
    )
)
```

No other login-flow changes. No new error paths.

### 2. `AboutViewModel.kt` — read cache, conditionally refresh

Inject `SettingsDataStore`, `ConnectivityMonitor`, and `ReadeckApi` (all already provided by the existing Hilt graph):

```kotlin
data class UiState(
    val serverInfo: CachedServerInfo? = null,  // null = never cached
    val serverInfoLoading: Boolean = false,     // true only on initial fetch (no cache)
    val serverInfoError: Boolean = false        // true only when cache empty AND fetch failed
)
```

On `init`:
```
1. Load cache synchronously → emit UiState with cached data (loading=false)
2. If cache is null → set loading=true in UiState
3. If ConnectivityMonitor.isNetworkAvailable():
      launch coroutine → GET /api/info
        → on success: saveServerInfo(), update UiState (loading=false, error=false)
        → on failure with cache: swallow, keep existing UiState
        → on failure without cache: set error=true, loading=false
4. If no network AND no cache: emit UiState(serverInfo=null, loading=false, error=false)
   (UI shows "No server information available" — not an error)
```

### 3. `AboutScreen.kt` — UI composable

Replace the current flat System Info block with two collapsible subsection composables.

Extract a new **private** composable `CollapsibleInfoCard`:
```
CollapsibleInfoCard(
    subheading: String,        // "App" or "Server"
    summary: String,           // version line always visible
    detailLines: List<String>, // shown when expanded
    isLoading: Boolean = false,
    isError: Boolean = false
)
```

Internal state: `var expanded by remember { mutableStateOf(false) }`
Uses `AnimatedVisibility` for the detail block.
Uses `LocalClipboardManager` for copy action.

**Update `AboutScreenContent` signature** to accept `uiState: AboutViewModel.UiState` (passed down from `AboutScreen`).

### 4. String Resources

New strings (add to `values/strings.xml` **and** all 9 language files with English placeholder):

| Resource name | English value |
|---|---|
| `about_system_info_app_subtitle` | `App` |
| `about_system_info_server_subtitle` | `Server` |
| `about_system_info_details_show` | `Show details` |
| `about_system_info_details_hide` | `Hide details` |
| `about_system_info_copy` | `Copy to clipboard` |
| `about_system_info_copied` | `Copied!` |
| `about_system_info_server_version` | `Version: %s` |
| `about_system_info_server_release` | `Release: %s` |
| `about_system_info_server_build` | `Build: %s` |
| `about_system_info_server_build_stable` | `(stable release)` |
| `about_system_info_server_features` | `Features: %s` |
| `about_system_info_server_loading` | `Loading…` |
| `about_system_info_server_error` | `Could not load server information` |
| `about_system_info_server_unavailable` | `No server information available` |

**Existing strings retained as-is:**
- `about_system_info_version`, `about_system_info_build_time`, `about_system_info_android`, `about_system_info_device` — still used inside the App detail block.

---

## Detailed Clipboard Text Format

### App block
```
Version:  1.2.3 (456)
Built:    2026-03-06 13:08:25
Android:  15 (API 35)
Device:   Google Pixel 9
```

### Server block
```
Version:  0.22.1
Release:  0.22.1
Build:    (stable release)
Features: oauth, email
```

---

## Files to Change

| File | Change type |
|---|---|
| `io/prefs/SettingsDataStore.kt` | Modify — add `saveServerInfo`, `getServerInfo`, `clearServerInfo` to interface |
| `io/prefs/SettingsDataStoreImpl.kt` | Modify — implement new methods; add 4 new keys; call `clearServerInfo()` inside `clearCredentials()` |
| `domain/UserRepositoryImpl.kt` | Modify — save server info to cache after successful `getInfo()` in `initiateLogin()` |
| `domain/model/CachedServerInfo.kt` | **New** — 6-line data class |
| `ui/about/AboutViewModel.kt` | Modify — inject `SettingsDataStore` + `ConnectivityMonitor` + `ReadeckApi`; add `UiState`; implement cache-first + conditional refresh logic |
| `ui/about/AboutScreen.kt` | Modify — replace System Info block, add `CollapsibleInfoCard`, update signature |
| `res/values/strings.xml` | Modify — add 14 new strings |
| `res/values-de-rDE/strings.xml` | Modify — add 14 English placeholders |
| `res/values-es-rES/strings.xml` | Modify — add 14 English placeholders |
| `res/values-fr/strings.xml` | Modify — add 14 English placeholders |
| `res/values-gl-rES/strings.xml` | Modify — add 14 English placeholders |
| `res/values-pl/strings.xml` | Modify — add 14 English placeholders |
| `res/values-pt-rPT/strings.xml` | Modify — add 14 English placeholders |
| `res/values-ru/strings.xml` | Modify — add 14 English placeholders |
| `res/values-uk/strings.xml` | Modify — add 14 English placeholders |
| `res/values-zh-rCN/strings.xml` | Modify — add 14 English placeholders |

One new file (`CachedServerInfo.kt`). No new API endpoints, Hilt modules, or Room entities.

---

## What Is Not In Scope

- No "refresh" button — refresh is automatic and silent on About screen open.
- No staleness timer — the refresh happens on every About screen open when online (server info is tiny; the call is negligible).
- No changes to other screens beyond login flow.
- No user guide update required (diagnostic/technical info section, not a user-workflow feature).

---

## Effort Estimate

| Area | Lines changed (approx.) | Notes |
|---|---|---|
| `SettingsDataStore.kt` + `Impl` | ~40 | 3 new methods, 4 new keys, one `clearServerInfo()` call |
| `CachedServerInfo.kt` | ~8 | New data class |
| `UserRepositoryImpl.kt` | ~10 | Save cache after existing `getInfo()` call |
| `AboutViewModel.kt` | ~50 | 3 injections, UiState, cache-first + refresh logic |
| `AboutScreen.kt` | ~90 | `CollapsibleInfoCard` composable, updated section |
| `strings.xml` × 10 files | ~140 | 14 strings × 10 locale files |
| **Total** | **~340** | |

**Recommended model: Haiku.**
The caching addition is still pattern-following: `SettingsDataStore` already stores timestamps and preferences exactly this way, `ConnectivityMonitor.isNetworkAvailable()` is a ready-made synchronous check, and the cache-first / background-refresh pattern is standard Android. All wiring points are precisely specified above. Haiku can execute this reliably given the file-by-file instructions in this spec.

Upgrade to Sonnet only if unexpected complexity arises — e.g., if the encrypted SharedPreferences migration for new keys causes issues, or if a more sophisticated staleness policy (time-based TTL, per-account isolation) is later desired.
