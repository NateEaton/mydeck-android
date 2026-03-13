# Reader Appearance and Typography Controls Specification

**Date:** 2026-03-07
**Status:** Draft

## Overview

This specification defines the next iteration of MyDeck's reading appearance and typography controls.

The work has two linked goals:

1. Replace the current global sepia toggle with explicit light and dark reading appearance choices in `Settings > User Interface`.
2. Expand the reader typography sheet from a small set of coarse controls into a more flexible, but still compact, control surface for text, headings, and title styling.

The design intent is:

- keep reading customization immediately visible while the user reads
- avoid a taller default bottom sheet than the current one
- preserve a simple mental model
- keep appearance settings global and app-wide
- avoid the complexity of ReadYou-style deep per-element customization

## Goals

- Support four named global appearances:
  - `Paper`
  - `Sepia`
  - `Dark`
  - `Black`
- Keep appearance selection in `Settings > User Interface`, not in the reader sheet.
- Make `Sepia` apply to the entire application, not only the reading pane.
- Introduce a distinct `Black` appearance with a fully black reading pane and app background.
- Replace the current line spacing and width toggles with incremental plus/minus controls.
- Add title and heading tuning without splitting article headings into separate heading/subheading groups.
- Keep live updates while the sheet is open.
- Keep the reader sheet compact by using collapsible sections.

## Non-Goals

- User-configurable accent colors
- Material You / wallpaper-derived accent toggles
- Pagination mode
- Separate controls for `h1/h2` versus `h3-h6`
- Separate font-family controls for title or headings
- Additional typography controls such as letter spacing, paragraph indentation, or custom link styling options
- Changes to the existing font list

## Current State

### Global appearance

The app currently stores:

- `Theme` as `LIGHT`, `DARK`, or `SYSTEM`
- `sepiaEnabled` as an independent boolean used only when the effective theme is light

This produces three practical reader appearances today:

- light
- sepia
- dark

The sepia choice is exposed in `Settings > User Interface` as a toggle, while the reader chooses its HTML template separately from the Compose app theme.

### Reader typography

The current typography model stores:

- font family
- font size percent
- line spacing as a two-value enum
- text width as a two-value enum
- justify text
- hyphenation

The current bottom sheet exposes:

- font family chips
- font size `- / +`
- line spacing `Tight / Loose`
- width `Wide / Narrow`
- justify toggle
- hyphenation toggle
- reset

### Known limitations

- The appearance model is incomplete. There is no explicit `Paper` or `Black` choice.
- Sepia is conceptually a reading appearance, but technically implemented as a light-mode modifier.
- The dark reader pane is charcoal, not fully black.
- Width is controlled in both Compose layout and WebView CSS.
- Font size currently relies on `WebView.textZoom`, which scales body text and article headings together.
- The current typography sheet has no title or heading controls.
- Sepia links do not stand out enough from body text.

## Product Decisions Captured in This Spec

### Appearance decisions

- Reading background theme remains in `Settings > User Interface`.
- Appearance settings affect the entire app, not only the reader pane.
- Theme mode remains `Light`, `Dark`, or `System`.
- Light and dark appearances are configured separately.
- Accent color customization is deferred.

### Typography decisions

- Article headings are controlled as one group covering `h1` through `h6`.
- The bookmark title is controlled separately from article headings.
- Size controls use percentage labels.
- Width uses incremental plus/minus controls instead of a two-state toggle.
- Collapsible sections are used to keep the sheet compact.
- The underlying article content remains the primary live preview.

### Link styling decisions

- Light, dark, and black link styling remain effectively unchanged.
- Sepia links gain a subtle underline to improve distinction without adding a user setting.

## Functional Requirements

### 1. Global Appearance Settings

`Settings > User Interface` must replace the current sepia toggle with explicit appearance groups.

#### 1.1 Theme mode

Keep the existing theme mode control:

- `Light`
- `Dark`
- `System`

#### 1.2 Light appearance group

Add a labeled pill group with large selectable pills that visibly preview the background tone:

- `When app is light`
  - `Paper`
  - `Sepia`

Behavior:

- If theme mode is `Light`, the selected light appearance is applied immediately.
- If theme mode is `System`, the selected light appearance is used whenever the system is in light mode.

#### 1.3 Dark appearance group

Add a labeled pill group with large selectable pills that visibly preview the background tone:

- `When app is dark`
  - `Dark`
  - `Black`

Behavior:

- If theme mode is `Dark`, the selected dark appearance is applied immediately.
- If theme mode is `System`, the selected dark appearance is used whenever the system is in dark mode.

#### 1.4 Appearance definitions

`Paper`
- Uses the current light reading background as the baseline
- Applies across app surfaces, not only the reader template

`Sepia`
- Uses the current sepia reading background as the baseline
- Applies across app surfaces, not only the reader template
- Adds a subtle underline to article links

`Dark`
- Uses the current non-black dark reading appearance as the baseline
- Reading pane remains charcoal/dark gray rather than fully black

`Black`
- Uses a fully black main background and fully black reading pane
- Raised surfaces such as cards, sheets, dialogs, and controls remain slightly lifted off black for legibility

### 2. Reader Sheet Information Architecture

The reader typography sheet remains a modal bottom sheet.

#### 2.1 Initial height

The default opened height should remain close to the current sheet height so the user can continue to see content changes in real time.

#### 2.2 Section structure

The sheet is reorganized into these sections:

- `Text`
- `Headings`
- `Title`
- `Advanced`
- `Reset`

Default expansion state:

- `Text`: expanded
- `Headings`: collapsed
- `Title`: collapsed
- `Advanced`: collapsed

The sheet remains vertically scrollable if the user expands multiple sections.

#### 2.3 Live updates

All changes apply immediately while the sheet is open.

There is no separate preview block in the initial implementation. The currently visible article remains the live preview surface.

### 3. Text Section

The `Text` section contains the frequently used body-text controls.

#### 3.1 Font family

Keep the existing font-family control conceptually unchanged:

- horizontally scrollable chips
- same font list as current implementation
- selected font applies to body text, article headings, and the bookmark title

#### 3.2 Body font size

Replace the current filled icon buttons with outlined pills.

Control format:

- `[-] 100% [+]`

Behavior:

- default: `100%`
- range: `85%` to `170%`
- increment: `5%`

#### 3.3 Line spacing

Replace the `Tight / Loose` segmented control with plus/minus controls.

Control format:

- `[-] 100% [+]`

Behavior:

- `100%` equals the current default article line spacing
- default line-height baseline: current reader default, equivalent to `1.7`
- range: `85%` to `130%`
- increment: `5%`

This range intentionally reaches approximately the current `Loose` upper bound without making line spacing unreasonably large.

#### 3.4 Width

Replace the `Wide / Narrow` segmented control with plus/minus controls.

Control format:

- `[-] 85% [+]`

Behavior:

- displayed value is the actual content width as a percentage of the available reader width
- default: `85%`
- range: `75%` to `90%`
- increment: `5%`

Width mapping:

- `90%` matches the current `Wide` layout
- `85%` becomes the new default
- `75%` is slightly narrower than the current `Narrow` layout

This control applies to the article container and the header/title width together.

### 4. Headings Section

The `Headings` section controls article headings as one group.

Scope:

- applies to all article `h1` through `h6`

Controls:

- size
- weight
- case

#### 4.1 Heading size

Control format:

- `[-] 100% [+]`

Behavior:

- default: `100%`
- range: `85%` to `130%`
- increment: `5%`

The heading size value is a multiplier applied across all heading levels, not a replacement of each heading tag's relative hierarchy.

#### 4.2 Heading weight

Two-state pill or segmented control:

- `Normal`
- `Bold`

Default:

- `Bold`

#### 4.3 Heading case

Two-state pill or segmented control:

- `Original`
- `Uppercase`

Default:

- `Original`

### 5. Title Section

The `Title` section controls the bookmark title shown above the article body.

Controls:

- size
- weight
- case

#### 5.1 Title size

Control format:

- `[-] 100% [+]`

Behavior:

- default: `100%`
- range: `85%` to `130%`
- increment: `5%`

This multiplier is applied to the existing title style rather than replacing the title with an arbitrary freeform text size.

#### 5.2 Title weight

Two-state pill or segmented control:

- `Normal`
- `Bold`

Default:

- `Normal`

#### 5.3 Title case

Two-state pill or segmented control:

- `Original`
- `Uppercase`

Default:

- `Original`

Title case transformation applies only to the displayed title. Inline title editing continues to use the stored original text.

### 6. Advanced Section

The `Advanced` section keeps the lower-frequency controls.

Controls:

- `Justify text`
- `Hyphenate words`

Behavior:

- semantics remain unchanged from the current implementation
- only the placement changes

### 7. Reset

The reader sheet reset control restores typography settings only:

- typography settings to defaults

Appearance reset, if needed later, belongs in `Settings > User Interface`, not in the reader sheet.

## Technical Design

### 1. Settings Model Changes

#### 1.1 Replace sepia boolean with explicit appearance enums

Add two new stored preferences:

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

`SettingsDataStore` gains:

- `lightAppearanceFlow`
- `darkAppearanceFlow`
- `saveLightAppearance(...)`
- `saveDarkAppearance(...)`

`sepiaEnabled` is deprecated and retained only long enough to support migration.

#### 1.2 Typography settings model

Replace enum-based line spacing and width with numeric settings, and add title/heading controls.

Proposed model:

```kotlin
data class TypographySettings(
    val fontFamily: ReaderFontFamily = ReaderFontFamily.SYSTEM_DEFAULT,
    val bodyFontSizePercent: Int = 100,
    val lineSpacingPercent: Int = 100,
    val contentWidthPercent: Int = 85,
    val headingsSizePercent: Int = 100,
    val headingsBold: Boolean = true,
    val headingsUppercase: Boolean = false,
    val titleSizePercent: Int = 100,
    val titleBold: Boolean = false,
    val titleUppercase: Boolean = false,
    val justified: Boolean = false,
    val hyphenation: Boolean = false
)
```

Recommended constants:

```kotlin
const val MIN_BODY_FONT_SIZE = 80
const val MAX_BODY_FONT_SIZE = 200
const val BODY_FONT_SIZE_STEP = 10

const val MIN_LINE_SPACING = 85
const val MAX_LINE_SPACING = 130
const val LINE_SPACING_STEP = 5

const val MIN_CONTENT_WIDTH = 75
const val MAX_CONTENT_WIDTH = 90
const val CONTENT_WIDTH_STEP = 5

const val MIN_HEADING_SIZE = 85
const val MAX_HEADING_SIZE = 130
const val HEADING_SIZE_STEP = 5

const val MIN_TITLE_SIZE = 85
const val MAX_TITLE_SIZE = 130
const val TITLE_SIZE_STEP = 5
```

### 2. Preference Migration

Migration behavior:

- Existing `theme` values remain unchanged.
- Existing `sepiaEnabled=true` migrates to:
  - `lightAppearance = SEPIA`
  - `darkAppearance = DARK`
- Existing `sepiaEnabled=false` migrates to:
  - `lightAppearance = PAPER`
  - `darkAppearance = DARK`
- Existing typography values migrate as follows:
  - `fontSizePercent` -> `bodyFontSizePercent`
  - `lineSpacing = TIGHT` -> `lineSpacingPercent = 100`
  - `lineSpacing = LOOSE` -> `lineSpacingPercent = 130`
  - `textWidth = WIDE` -> `contentWidthPercent = 90`
  - `textWidth = NARROW` -> `contentWidthPercent = 80`
  - `headingsSizePercent = 100`
  - `headingsBold = true`
  - `headingsUppercase = false`
  - `titleSizePercent = 100`
  - `titleBold = false`
  - `titleUppercase = false`

### 3. Global Theme Application

#### 3.1 Effective appearance resolution

Compute effective appearance from:

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

#### 3.2 Compose theme model

Replace the current theme-plus-sepia branching with explicit curated app color schemes:

- `PaperColorScheme`
- `SepiaColorScheme`
- `DarkColorScheme`
- `BlackColorScheme`

This spec does not add an accent customization layer.

To keep appearance choices stable and understandable, the app should no longer depend on `dynamicLightColorScheme(...)` or `dynamicDarkColorScheme(...)` for these four named appearances.

Accent-color exploration is deferred to a later spec.

### 4. Reader Template Selection

Reader HTML template selection must use the same effective appearance as the Compose theme.

Required templates:

- `html_template_light.html` for `Paper`
- `html_template_sepia.html` for `Sepia`
- `html_template_dark.html` for `Dark`
- `html_template_black.html` for `Black`

This keeps the reader pane visually aligned with the rest of the application.

### 5. Reader Link Styling

Sepia template changes:

- keep the current link hue family
- add a subtle underline
- use a small underline offset and normal underline thickness

Suggested CSS behavior in `html_template_sepia.html`:

```css
a {
  text-decoration: underline;
  text-underline-offset: 0.12em;
  text-decoration-thickness: 1px;
}
```

No equivalent underline change is required for paper, dark, or black in this iteration.

### 6. Reader Sheet Implementation

The bottom sheet remains `ReaderSettingsBottomSheet`, but its internals change substantially.

Recommended structure:

- static `Text` section content
- reusable `CollapsibleSettingsSection` composable for `Headings`, `Title`, and `Advanced`
- shared `StepPillControl` composable for percent-based `- / value / +` controls

The plus/minus controls should be outlined pills, not filled icon buttons.

### 7. Width Source of Truth

Width must no longer be applied independently in both Compose layout and WebView CSS.

Recommended approach:

- Compose layout becomes the only source of truth for overall content width
- the reader header and article container both use the same `contentWidthPercent`
- WebView body width is set to fill its container

Implementation consequence:

- remove enum-based width handling from `BookmarkDetailScreen`
- remove width-specific `maxWidth` behavior from `WebViewTypographyBridge`
- keep inner body padding, but do not apply a second narrower max-width inside the already-sized container

### 8. Typography Rendering Strategy

#### 8.1 Body text

Body text sizing should move away from `WebView.textZoom` as the primary sizing mechanism.

Reason:

- `textZoom` scales article headings together with body text
- this prevents an independent heading-size multiplier

Recommended approach:

- keep `WebView.textZoom = 100`
- inject a dedicated typography stylesheet or style block with CSS custom properties
- drive body font size, heading size, line spacing, and text alignment from CSS variables

#### 8.2 Headings

Heading sizes should be applied as explicit multipliers across `h1` through `h6`.

Important constraint:

- source HTML heading levels are inconsistent across websites
- the app cannot normalize article authoring quality

Therefore the implementation should:

- preserve the relative difference between `h1`-`h6`
- apply one heading-size multiplier across all six heading tags
- apply heading weight and uppercase consistently across all six heading tags

#### 8.3 Title

Title styling remains in Compose and is not part of the WebView.

Implementation points:

- apply the selected reader font family to the title as today
- apply `titleSizePercent` as a multiplier on the existing `headlineSmall` title style
- apply `titleBold` and `titleUppercase` in display mode only
- keep inline editing bound to the original underlying title text

### 9. Suggested File Changes

Expected areas of change:

- `app/src/main/java/com/mydeck/app/domain/model/TypographySettings.kt`
- `app/src/main/java/com/mydeck/app/io/prefs/SettingsDataStore.kt`
- `app/src/main/java/com/mydeck/app/io/prefs/SettingsDataStoreImpl.kt`
- `app/src/main/java/com/mydeck/app/domain/model/Theme.kt`
- `app/src/main/java/com/mydeck/app/ui/theme/Theme.kt`
- `app/src/main/java/com/mydeck/app/ui/settings/UiSettingsScreen.kt`
- `app/src/main/java/com/mydeck/app/ui/settings/UiSettingsViewModel.kt`
- `app/src/main/java/com/mydeck/app/ui/detail/ReaderSettingsBottomSheet.kt`
- `app/src/main/java/com/mydeck/app/ui/detail/WebViewTypographyBridge.kt`
- `app/src/main/java/com/mydeck/app/ui/detail/components/BookmarkDetailWebViews.kt`
- `app/src/main/java/com/mydeck/app/ui/detail/components/BookmarkDetailHeader.kt`
- `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailViewModel.kt`
- `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailScreen.kt`
- `app/src/main/assets/html_template_light.html`
- `app/src/main/assets/html_template_sepia.html`
- `app/src/main/assets/html_template_dark.html`
- `app/src/main/assets/html_template_black.html`
- `app/src/main/res/values/strings.xml`
- all translated `strings.xml` files
- `app/src/main/assets/guide/en/reading.md`
- `app/src/main/assets/guide/en/settings.md`

### 10. Validation and Testing

#### 10.1 Functional validation

Verify:

- light appearance changes update the full app in light mode
- dark appearance changes update the full app in dark mode
- system theme chooses the correct appearance automatically
- reader title, headings, and body update live while the sheet is open
- width updates keep title and article body aligned
- sepia links are visibly more distinct than before
- reset returns settings to the documented defaults

#### 10.2 Regression validation

Verify:

- existing saved typography settings migrate without crashes
- bookmarks of type article, video, and picture still render correctly in reader mode
- title editing still works
- justify and hyphenation still work
- the bottom sheet remains usable on smaller phones

#### 10.3 Build checks

When implementation begins, run:

- `./gradlew :app:assembleDebug`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:lintDebug`

## Acceptance Criteria

- `Settings > User Interface` exposes separate light and dark appearance choices instead of a sepia toggle.
- The light and dark appearance controls use large selectable pills with visible background previews.
- Selecting `Sepia` affects the full app, not only the reader pane.
- Selecting `Black` produces a visually distinct appearance from `Dark`.
- The reader sheet starts at approximately the same height as today.
- The reader sheet contains `Text`, `Headings`, `Title`, `Advanced`, and `Reset`.
- Text, heading, and title size controls use percent-based plus/minus controls.
- Width uses a percent-based incremental control instead of `Wide / Narrow`.
- Article headings are controlled as a single `h1`-`h6` group.
- Sepia links have a subtle underline.
- The accent-color topic remains out of scope.
- Pagination remains out of scope.

## Risks and Follow-Up Notes

### 1. Heading inconsistency across articles

Some articles misuse heading levels. This spec accepts that limitation and intentionally avoids trying to infer semantic intent beyond the source HTML.

### 2. CSS refactor complexity

Independent body and heading sizing is not a UI-only change. It likely requires shifting more typography logic into injected CSS rather than relying on `WebView.textZoom`.

### 3. Appearance palette follow-up

If a future accent-color spec is approved, it should build on these curated appearance families rather than reopening the basic appearance model.

### 4. Pagination follow-up

Pagination remains a separate research effort because the current reader architecture is scroll-based:

- read progress is scroll-based
- the extracted-content reader relies on a vertical Compose scroll container
- WebView pagination would require different navigation, search behavior, and progress persistence

This should be specified separately if revisited.
