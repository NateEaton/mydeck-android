# Keep Screen On While Reading

**Status:** Implemented
**Date:** 2026-03-02

---

## Motivation

Android devices apply a screen timeout that turns the display off after a period of inactivity. While useful in general, this interrupts long reading sessions — the user loses their place and must unlock the device to continue. Reading apps conventionally suppress the screen timeout while a document is open.

---

## User-Facing Behaviour

| Situation | Behaviour |
|-----------|-----------|
| User opens a bookmark in reading view, setting is **on** | Screen stays on indefinitely (system timeout suppressed) |
| User closes / navigates away from reading view | Normal system screen timeout resumes |
| User opens a bookmark in reading view, setting is **off** | Normal system screen timeout applies |
| User changes the setting while reading view is open | Takes effect immediately (no restart required) |

The feature applies to all reading view types: Article, Video, and Picture.

---

## Settings

A toggle is added to **Settings → User Interface**:

- **Label:** Keep screen on while reading
- **Description:** Prevents the screen from turning off while you read
- **Default:** On

---

## Implementation Summary

### Preference storage
A new boolean preference `keep_screen_on_reading` is stored via `SettingsDataStore` (encrypted SharedPreferences), defaulting to `true`.

### Reading view
`BookmarkDetailScreen.kt` uses a `DisposableEffect` keyed on the `keepScreenOnWhileReading` state to set `view.keepScreenOn = true/false` on the current Android `View`. When the composable is disposed (user navigates away), `view.keepScreenOn` is reset to `false`, restoring the system default.

### Settings UI
`UiSettingsScreen.kt` receives a new `Switch` `ListItem` following the existing sepia-toggle pattern. The toggle is wired to `UiSettingsViewModel`, which persists changes via `SettingsDataStore`.

---

## Out of Scope

- Separate behaviour per bookmark type (all types treated equally)
- Auto-dimming or brightness control
- Lock-screen / ambient display interactions
