# Design Spec: Server Info Section in About Screen

**Status:** Design / Pre-implementation
**Branch:** `claude/design-server-info-section-Nfsq8`
**Date:** 2026-03-07

---

## Overview

Enhance the About screen's "System Info" section to display both app and server information under separate subheadings. Each subheading will show a brief summary line, with an expandable card revealing full diagnostic details and a copy-to-clipboard action — mirroring the pattern used on the Readeck web UI's "About Readeck" page.

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

**Loading/error states for the Server card:**
- While fetching: show a `CircularProgressIndicator` where the summary version would appear.
- On success: show the canonical version as the summary line.
- On error: show a short error string instead of the summary, card still expandable to show the error detail (e.g., "Could not load server information").

**Copy to clipboard:**
- Appears inside the expanded card, below the detail rows.
- Copies the formatted multi-line text block to the system clipboard.
- Shows a transient "Copied!" `Snackbar` confirmation (or a brief icon state change).

---

## Architecture Changes

### 1. `AboutViewModel.kt`

Add constructor injection of `ReadeckApi` and expose a `UiState`:

```kotlin
data class UiState(
    val serverInfo: ServerInfoDto? = null,
    val serverInfoLoading: Boolean = true,
    val serverInfoError: Boolean = false
)
```

On `init`, launch a coroutine to call `readeckApi.getInfo()` and update state.
`ReadeckApi` is already provided by the Hilt `NetworkModule` — no new DI wiring needed.

### 2. `AboutScreen.kt`

**Replace** the current flat System Info block with two collapsible subsection composables.

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

### 3. String Resources

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
| `ui/about/AboutViewModel.kt` | Modify — add `ReadeckApi` injection, `UiState`, init fetch |
| `ui/about/AboutScreen.kt` | Modify — replace System Info block, add `CollapsibleInfoCard`, update signature |
| `res/values/strings.xml` | Modify — add 13 new strings |
| `res/values-de-rDE/strings.xml` | Modify — add 13 English placeholders |
| `res/values-es-rES/strings.xml` | Modify — add 13 English placeholders |
| `res/values-fr/strings.xml` | Modify — add 13 English placeholders |
| `res/values-gl-rES/strings.xml` | Modify — add 13 English placeholders |
| `res/values-pl/strings.xml` | Modify — add 13 English placeholders |
| `res/values-pt-rPT/strings.xml` | Modify — add 13 English placeholders |
| `res/values-ru/strings.xml` | Modify — add 13 English placeholders |
| `res/values-uk/strings.xml` | Modify — add 13 English placeholders |
| `res/values-zh-rCN/strings.xml` | Modify — add 13 English placeholders |

No new files required. No new API endpoints, repositories, or DI modules needed.

---

## What Is Not In Scope

- No caching of server info between sessions (re-fetched each time About is opened).
- No "refresh" button — the fetch happens once on screen open.
- No changes to other screens.
- No user guide update required (diagnostic/technical info section, not a user-workflow feature).

---

## Effort Estimate

| Area | Lines changed (approx.) | Notes |
|---|---|---|
| `AboutViewModel.kt` | ~30 | DI injection, coroutine, UiState |
| `AboutScreen.kt` | ~90 | CollapsibleInfoCard composable, updated section |
| `strings.xml` × 10 files | ~130 | 13 strings × 10 locale files |
| **Total** | **~250** | |

**Recommended model: Haiku.**
This is a well-scoped, pattern-following task. Every building block already exists in the codebase — the API call, the ViewModel pattern, the Compose composable structure, the string resource pattern, and the Hilt injection setup. No novel architectural decisions are required. Haiku can execute this reliably given the clear file-by-file instructions in this spec.

Sonnet would be appropriate only if the implementation encounters unexpected complexity (e.g., threading issues with the API call, or the server info needing to be persisted/cached across sessions in a future iteration).
