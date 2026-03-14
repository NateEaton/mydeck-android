# Reader Appearance Backgrounds Specification

**Date:** 2026-03-13
**Status:** Draft

## Overview

This specification defines the next focused slice of reader appearance work: background styling, surface treatment, and the relationship between app-wide appearance and reader appearance.

It intentionally narrows the broader [reader-appearance-and-typography-controls-spec.md](./reader-appearance-and-typography-controls-spec.md) into one implementation-ready decision set:

1. replace the current `sepiaEnabled` toggle model with explicit curated appearance families
2. keep one appearance family applied across both the Compose UI and the reader WebView
3. establish distinct `Paper`, `Sepia`, `Dark`, and `Black` appearances
4. ensure that system bars, app chrome, and reader surfaces all stay within the same tonal family
5. add an optional fullscreen reading mode controlled from `Settings > User Interface`
6. defer Material You accent experimentation until after the core appearance model is stable

## Problem Statement

The current app appearance model mixes multiple visual systems:

- app-wide light and dark themes still rely on Material 3 dynamic color on supported Android versions
- sepia is already a curated app-wide appearance, but only when light mode is active
- the reader itself uses fixed HTML templates selected independently from the Compose palette

This creates two problems:

- the reader background and the rest of the app can feel visually inconsistent
- wallpaper-driven colors make `Paper` and `Dark` unpredictable, which is a poor fit for a reading-focused experience
- even curated appearances can still feel mismatched when the system bars, top app bars, dialogs, and WebView background resolve to different colorations inside the same appearance family

For a reading app, stable and deliberate background, text, and surface relationships are more important than matching the system wallpaper.

## Goals

- Support four named curated appearances:
  - `Paper`
  - `Sepia`
  - `Dark`
  - `Black`
- Keep `Light`, `Dark`, and `System` as the top-level theme mode.
- Apply the selected appearance family to both the app shell and the reader.
- Make `Dark` and `Black` meaningfully different in contrast and feel.
- Replace wallpaper-driven background behavior with curated palette behavior.
- Keep each appearance tonally coherent across status bar, navigation bar, app chrome, dialogs, and reader content.
- Support an optional fullscreen reading mode.
- Keep the user model simple and understandable.

## Non-Goals

- Independent app-theme and reader-theme controls
- Material You / wallpaper-derived colors in this slice
- User-selectable accent colors
- Typography control changes
- Pagination or reader navigation changes
- A per-screen fullscreen chooser outside of settings

## Product Decisions

### 1. One appearance across app and reader

MyDeck should continue to behave more like Instapaper than Play Books or Kindle for this slice:

- the selected appearance applies to the full Compose UI
- the reader template uses the matching appearance family
- there is no separate reading-theme chooser

This keeps the mental model simple and avoids the feeling that the user is managing two unrelated visual systems.

### 2. Theme mode stays, appearance families expand

The existing theme mode control remains:

- `Light`
- `Dark`
- `System`

Appearance families are selected separately for light and dark contexts:

- when app is light:
  - `Paper`
  - `Sepia`
- when app is dark:
  - `Dark`
  - `Black`

Behavior:

- `Light` mode uses the selected light appearance immediately
- `Dark` mode uses the selected dark appearance immediately
- `System` uses the selected light appearance when the system is light and the selected dark appearance when the system is dark

### 3. Curated palettes replace dynamic color

Dynamic color should be removed from the main appearance path for this feature.

Reasoning:

- curated reading backgrounds matter more than wallpaper-matching accents
- the current app does not have a deeply tuned Material You experience worth preserving
- dynamic color makes the light and dark appearance families unpredictable across devices

This slice should ship with four curated Compose color schemes and four matching reader appearances.

### 4. Accent colors are secondary

Accent colors matter less than surface and text harmony in a reading app.

For this slice:

- each curated appearance may define its own accent values
- those accents should be selected to fit the appearance family
- wallpaper-derived accent behavior is deferred

If Material You accents are explored later, they should be a separate advanced option such as `Use system accents`, not a fifth appearance family.

### 5. Appearance families must remain tonally coherent across layers

Each appearance family should read as one visual environment, not a stack of loosely related colors.

This means:

- the status bar and navigation bar should visually belong to the selected appearance family when visible
- top app bars, drawers, sheets, dialogs, and list surfaces should stay within the same tonal family as the screen background
- the reader header and reader WebView background should not drift into a noticeably different coloration from the surrounding app chrome

Subtle elevation differences are acceptable. Cross-family drift is not. For example, a warm sepia reader should not sit under cooler gray-beige bars or dialogs.

### 6. Fullscreen reading is optional and global

MyDeck should add a `Fullscreen while reading` setting under `Settings > User Interface`.

This is a simple on/off preference:

- off: preserve the current non-fullscreen reader layout
- on: use an immersive reader presentation when viewing article content

The setting is global rather than configured per bookmark.

## Appearance Definitions

### Paper

`Paper` is the default neutral light reading appearance.

Characteristics:

- soft paper-like light background rather than stark white
- dark neutral text with comfortable contrast
- calm light surfaces across lists, dialogs, sheets, and cards
- neutral or subtly cool accent treatment

### Sepia

`Sepia` is the warm reading appearance and applies across the full app, not only the article pane.

Characteristics:

- warm tan background
- dark brown body text
- warm brown accents
- softer, book-like feel than `Paper`

### Dark

`Dark` is the lower-contrast dark appearance.

Characteristics:

- charcoal or very dark gray background
- soft off-white text rather than bright white
- subdued surfaces that reduce visual glare
- clearly lower contrast than `Black`

### Black

`Black` is the higher-contrast dark appearance.

Characteristics:

- true black main background
- brighter white text for sharper contrast
- reader pane remains fully black
- Compose surfaces such as cards, sheets, and dialogs are slightly lifted off black for legibility

The difference between `Dark` and `Black` is primarily contrast sharpness, not brand identity.

## Functional Requirements

### 1. Settings UI

`Settings > User Interface` must replace the current sepia toggle with explicit appearance groups.

Required controls:

- theme mode selector:
  - `Light`
  - `Dark`
  - `System`
- light appearance selector:
  - `Paper`
  - `Sepia`
- dark appearance selector:
  - `Dark`
  - `Black`

The appearance selectors should use large, visually previewable pills so the user can understand the background tone at a glance.

`Settings > User Interface` must also include a separate `Fullscreen while reading` toggle.

Suggested description:

- hides the top bar and system bars while reading for a more immersive layout

### 2. App-wide application

The selected appearance family must apply to:

- app background
- status bar and navigation bar treatment when those bars are visible
- top bars and navigation chrome
- cards, sheets, dialogs, and menus
- bookmark list surfaces
- settings screens
- reader header and reader WebView

There should be no state where the reader is using one appearance family while the rest of the app is using another.

### 3. Tonal consistency rules

Within a single appearance family:

- background, top app bar, dialog/sheet surfaces, and reader background should stay within one tonal family
- elevation changes should read as intentional steps within that family, not as a shift to a different hue family
- the system bars should either match the adjacent app surface or blend closely enough that they do not read as a separate coloration

This requirement applies to `Paper`, `Sepia`, `Dark`, and `Black`.

### 4. Reader template mapping

The reader must use appearance-matched HTML templates:

- `Paper` -> `html_template_light.html`
- `Sepia` -> `html_template_sepia.html`
- `Dark` -> `html_template_dark.html`
- `Black` -> `html_template_black.html`

`Black` requires a new template file.

### 5. Fullscreen reading behavior

When `Fullscreen while reading` is enabled:

- article reading screens should enter immersive mode
- the top app bar should be hidden while actively reading
- system bars should be hidden while actively reading
- standard Android gestures should still allow the user to temporarily reveal system bars
- reader controls must remain reachable without forcing the user to disable the setting

This fullscreen behavior applies to reading screens only. It does not affect the rest of the app.

Default:

- `off`

### 6. Dark vs Black contrast behavior

`Dark` and `Black` must be visually distinct in actual use, not only by name.

Minimum intent:

- `Dark` keeps a charcoal reader background and softer text
- `Black` uses pure black or near-pure black with brighter text
- the bookmark list and other app screens should preserve that same contrast distinction

### 7. Dynamic color removal

The Compose theme should no longer depend on:

- `dynamicLightColorScheme(...)`
- `dynamicDarkColorScheme(...)`

The reader should not indirectly inherit wallpaper-driven colors through any appearance path.

## Technical Design

### 1. Settings model

Replace the current sepia toggle with explicit appearance enums.

Suggested model:

```kotlin
enum class LightAppearance {
    PAPER,
    SEPIA
}

enum class DarkAppearance {
    DARK,
    BLACK
}
```

`SettingsDataStore` should expose:

- `lightAppearanceFlow`
- `darkAppearanceFlow`
- `saveLightAppearance(...)`
- `saveDarkAppearance(...)`
- `fullscreenWhileReadingFlow`
- `saveFullscreenWhileReading(...)`

### 2. Preference migration

Existing values migrate as follows:

- existing `theme` values remain unchanged
- existing `sepiaEnabled = true`:
  - `lightAppearance = SEPIA`
  - `darkAppearance = DARK`
- existing `sepiaEnabled = false`:
  - `lightAppearance = PAPER`
  - `darkAppearance = DARK`

The old `sepiaEnabled` preference may be retained only long enough to complete migration safely.

The new fullscreen preference defaults to `false` for existing users.

### 3. Effective appearance resolution

The app should compute one effective appearance from:

- theme mode
- system dark/light state
- selected light appearance
- selected dark appearance

Rules:

- theme mode `LIGHT` -> use selected `LightAppearance`
- theme mode `DARK` -> use selected `DarkAppearance`
- theme mode `SYSTEM`
  - system light -> use selected `LightAppearance`
  - system dark -> use selected `DarkAppearance`

### 4. Compose palette model

Implement four curated Compose color schemes:

- `PaperColorScheme`
- `SepiaColorScheme`
- `DarkColorScheme`
- `BlackColorScheme`

Important note:

- `Paper` and `Dark` must be fully curated, not thin wrappers around default Material 3 colors
- `Black` must be curated separately from `Dark`, not derived only by flipping one background token
- each appearance should define system-bar-compatible background tones rather than letting those bars fall back to unrelated defaults

### 5. Reader palette alignment

The reader WebView template selection must be driven from the same effective appearance used by Compose.

This means:

- no more `Theme + sepiaEnabled` branching
- no reader-only interpretation of appearance
- no mismatch between app shell and article body appearance

### 6. System bar treatment

When fullscreen reading is disabled, system bars should be appearance-aware:

- light appearances use light system-bar icon treatment with appearance-matched light backgrounds
- dark appearances use dark-system treatment with appearance-matched dark backgrounds
- `Black` should preserve its higher-contrast identity even when system bars are visible

When fullscreen reading is enabled on reader screens, the app may use immersive handling instead of persistent system-bar coloring.

### 7. Fullscreen reader handling

Fullscreen reading should be implemented as a global UI preference rather than a reader-sheet control.

Suggested behavior:

- hide reader-specific top chrome while the article is in focus
- use immersive system-bar handling on reader screens
- keep non-reader screens unchanged
- preserve access to navigation and actions through transient reveal behavior

## Suggested File Areas

Likely areas of change when implementation begins:

- `app/src/main/java/com/mydeck/app/io/prefs/SettingsDataStore.kt`
- `app/src/main/java/com/mydeck/app/io/prefs/SettingsDataStoreImpl.kt`
- `app/src/main/java/com/mydeck/app/domain/model/Theme.kt`
- `app/src/main/java/com/mydeck/app/ui/theme/Theme.kt`
- `app/src/main/java/com/mydeck/app/ui/settings/UiSettingsScreen.kt`
- `app/src/main/java/com/mydeck/app/ui/settings/UiSettingsViewModel.kt`
- `app/src/main/java/com/mydeck/app/MainActivity.kt`
- `app/src/main/java/com/mydeck/app/MainViewModel.kt`
- `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailViewModel.kt`
- `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailScreen.kt`
- `app/src/main/assets/html_template_light.html`
- `app/src/main/assets/html_template_sepia.html`
- `app/src/main/assets/html_template_dark.html`
- `app/src/main/assets/html_template_black.html`
- `app/src/main/res/values/strings.xml`
- all translated `strings.xml` files
- `app/src/main/assets/guide/en/settings.md`
- `app/src/main/assets/guide/en/reading.md`

## Acceptance Criteria

- `Settings > User Interface` exposes light and dark appearance choices instead of a sepia toggle.
- The app continues to support `Light`, `Dark`, and `System` theme modes.
- The selected appearance family applies consistently across app chrome and reader content.
- The status bar, navigation bar, app chrome, dialogs, and reader background stay within the same appearance family without obvious color mismatches.
- `Paper`, `Sepia`, `Dark`, and `Black` are each visually distinct.
- `Dark` and `Black` are distinguishable primarily by contrast level.
- The app no longer uses wallpaper-derived dynamic color in its main appearance path.
- `Sepia` still feels warm and book-like across the full app.
- `Black` uses true-black reading backgrounds while keeping Compose surfaces legible.
- `Settings > User Interface` includes a `Fullscreen while reading` toggle.
- When fullscreen reading is enabled, reader screens hide their top chrome and system bars while preserving reachable controls.

## Follow-Up Candidates

- Optional `Use system accents` behavior as a separate advanced spec
- Fine-tuning link/accent colors within each curated appearance family
- Revisiting split app-theme versus reader-theme behavior only if strong user feedback demands it
