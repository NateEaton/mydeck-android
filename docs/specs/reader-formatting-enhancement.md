# Reader Formatting Enhancement — Design Specification

## 1. Overview

Replace MyDeck's coarse font-size-only controls (buried in the overflow menu) with a
dedicated **Typography** icon in the reader toolbar that opens a Material Design 3
modal bottom sheet. All changes take effect immediately in the WebView. Settings
persist globally across articles.

The feature set matches and exceeds the native Readeck web interface (font family,
font size, line height) and adds text width, justification, and hyphenation controls.

---

## 2. Current State Analysis

### 2.1 Existing Implementation
| Aspect | Detail |
|---|---|
| **UI location** | `BookmarkDetailMenu` overflow menu — "Increase text size" / "Decrease text size" |
| **Mechanism** | `WebView.settings.textZoom` (integer percentage, default 100) |
| **Increment** | 25 % per tap (range 25–400) |
| **Storage** | `SettingsDataStore.zoomFactorFlow` → `SharedPreferences` key `zoom_factor` |
| **Templates** | Three static HTML files (`html_template_light/dark/sepia.html`) using Sakura.css with hard-coded `font-family`, `line-height: 1.618`, `max-width: 38em` |

### 2.2 Limitations
- 25 % steps are too coarse for comfortable reading adjustment
- No font family, line spacing, text width, justification, or hyphenation controls
- Controls require opening the overflow menu (3+ taps)

### 2.3 Readeck Web Feature Parity
Readeck's web reader (as of v0.16+) offers:
- **Font family** — ~10 bundled web fonts (Lora, Inter, Merriweather, IBM Plex Serif,
  Public Sans, Luciole, Atkinson Hyperlegible, JetBrains Mono, plus system default)
- **Font size** — slider
- **Line height** — slider
- **Theme** — white / beige / dark / black (MyDeck already has light/dark/sepia + system)
- **Settings persistence** — per-user, remembered across sessions

This spec meets all Readeck reader controls and adds: text width, justification,
and hyphenation.

---

## 3. Functional Requirements

### 3.1 Typography Panel Trigger
- Add a **TextFormat** (`Icons.Outlined.FormatSize`) icon button to the `TopAppBar`
  actions row, placed between the Info button and the overflow menu.
- Remove the "Increase text size" and "Decrease text size" `DropdownMenuItem`s from
  `BookmarkDetailMenu`.
- The icon is visible only when `contentMode == READER` and the bookmark type is
  `ARTICLE`. (Photos/videos don't benefit from typography controls.)

### 3.2 Panel Presentation (Material Design 3)
- **Component**: `ModalBottomSheet` (Compose Material 3).
- **Drag handle**: Provided automatically by M3 — do **not** add a custom header bar
  or close button. Per M3 guidelines, modal bottom sheets use the drag handle as the
  primary affordance and dismiss via swipe-down or scrim tap.
- **Scrim**: Default M3 scrim (semi-transparent overlay). Tap-to-dismiss enabled.
- **Skip partially expanded**: `true` — the sheet opens fully expanded since the
  content is compact enough to not need a half-expanded state.
- **Content padding**: 24 dp horizontal, 16 dp vertical (M3 standard content area
  padding for bottom sheets).
- **Bottom inset**: Respect navigation bar / gesture bar insets via
  `WindowInsets.navigationBars`.

### 3.3 Typography Controls

All controls apply **immediately** to the WebView via JavaScript injection. No
"Apply" or "OK" button.

#### 3.3.1 Font Size
| Property | Value |
|---|---|
| **Control** | M3 `Slider` with discrete steps |
| **Range** | 80 % – 200 % (of base `1.8rem` = ~16 px) |
| **Steps** | 13 discrete stops: 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200 |
| **Default** | 100 % |
| **Display** | Current percentage shown as `labelMedium` text to the right of the slider |

> **Rationale**: Using percentage-based sizing (applied via `WebView.settings.textZoom`)
> preserves the existing mechanism and avoids needing to override every CSS rule. This
> is simpler and more robust than injecting absolute `px` values. The finer 10 %
> increments (vs. current 25 %) address the user's request for finer control.

#### 3.3.2 Font Family
| Property | Value |
|---|---|
| **Control** | Horizontally scrollable row of `FilterChip`s (M3), one per font |
| **Default** | System Default |

**Font list** (6 fonts — practical for mobile, covers all categories):

| Display Name | CSS `font-family` value | Category | Bundled? |
|---|---|---|---|
| System Default | `-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, "Noto Sans", sans-serif` | Sans-serif | No (system) |
| Roboto Serif | `"Roboto Serif", Georgia, serif` | Serif | Yes (woff2 in assets) |
| Merriweather | `"Merriweather", Georgia, serif` | Serif | Yes (woff2 in assets) |
| Lora | `"Lora", Georgia, serif` | Serif | Yes (woff2 in assets) |
| Noto Sans | `"Noto Sans", Roboto, sans-serif` | Sans-serif | No (system on most Android) |
| JetBrains Mono | `"JetBrains Mono", monospace` | Monospace | Yes (woff2 in assets) |

> **Rationale**: Trimmed from 12 to 6 fonts. Avoids bundling fonts that aren't
> available on Android (Luciole, Public Sans, IBM Plex) without adding significant APK
> size. Covers serif, sans-serif, monospace. Accessibility-focused fonts (Atkinson
> Hyperlegible) can be added later as a follow-up if requested.

Each `FilterChip` shows the font name rendered in that font (using a small
`@font-face` preview or the system font as fallback). Selected chip uses M3 selected
state (filled tonal).

#### 3.3.3 Line Spacing
| Property | Value |
|---|---|
| **Control** | Row of 5 `SingleChoiceSegmentedButtonRow` options (M3 segmented button) |
| **Options** | Compact (1.2), Standard (1.4), Comfortable (1.6), Relaxed (1.8), Spacious (2.0) |
| **Default** | Comfortable (1.6) |

> **Note**: The current template uses `1.618` (golden ratio). Rounding to `1.6` for
> the default is imperceptible and keeps the option set clean.

#### 3.3.4 Text Width
| Property | Value |
|---|---|
| **Control** | `SingleChoiceSegmentedButtonRow` with 3 options (M3 segmented button) |
| **Options** | 1 = Wide (38em, modest margins — current default), 2 = Medium (50em, narrow margins), 3 = Full (100%, standard body padding only) |
| **Default** | Wide (1) |
| **Labels** | Icon-only: narrow-column / medium-column / full-width line icons |

> **Clarification on naming**: "Modest margins" = generous margins around text = narrower
> text column (38em). "Narrow margins" = less margin = wider text (50em). "Full width" =
> text fills the viewport minus standard padding.

CSS `max-width` values applied to `body`:
- Wide: `38em` (current)
- Medium: `50em`
- Full: `100%` (with existing `padding: 13px` preserved)

#### 3.3.5 Text Justification
| Property | Value |
|---|---|
| **Control** | `SingleChoiceSegmentedButtonRow` with 4 icon-only options |
| **Options** | Left, Center, Right, Justified |
| **Default** | Left |
| **Icons** | `FormatAlignLeft`, `FormatAlignCenter`, `FormatAlignRight`, `FormatAlignJustify` |

#### 3.3.6 Hyphenation
| Property | Value |
|---|---|
| **Control** | M3 `Switch` with label |
| **Default** | Off |
| **CSS** | `hyphens: auto` when on, `hyphens: manual` when off |
| **Dependency** | Requires the article's `lang` attribute to be set (already available from `bookmark.lang`). The `<html>` tag in templates must include `lang="${lang}"`. |

### 3.4 Panel Layout (top to bottom)

```
┌─────────────────────────────────────┐
│          ── drag handle ──          │  (M3 default)
│                                     │
│  Font                               │  labelLarge, onSurfaceVariant
│  [System Default] [Roboto Serif]    │  FilterChip row, horizontally
│  [Merriweather] [Lora] [Noto Sans]  │  scrollable
│  [JetBrains Mono]                   │
│                                     │
│  Size                          100% │  labelLarge + current value
│  ○━━━━━━━━━━━●━━━━━━━━━━━━━━━━━━━○  │  Slider
│                                     │
│  Line Spacing                       │  labelLarge
│  [Compact][Standard][Comfy][Rlx][Sp]│  SegmentedButtonRow
│                                     │
│  Width                              │  labelLarge
│  [ ≡ ][ ≡≡ ][ ≡≡≡ ]                │  SegmentedButtonRow (icons)
│                                     │
│  Alignment                          │  labelLarge
│  [ ◧ ][ ◫ ][ ◨ ][ ☰ ]             │  SegmentedButtonRow (icons)
│                                     │
│  Hyphenation                   [◉]  │  labelLarge + Switch
│                                     │
│  ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄  │  Divider
│  Reset to defaults                  │  TextButton, centered
│                                     │
│         (navigation bar inset)      │
└─────────────────────────────────────┘
```

- Each section label uses `MaterialTheme.typography.labelLarge` with
  `MaterialTheme.colorScheme.onSurfaceVariant`.
- Vertical spacing between sections: 20 dp.
- The sheet content is wrapped in a `Column` with `verticalScroll` in case the
  device has a very small screen.

---

## 4. Technical Design

### 4.1 Data Model

File: `app/src/main/java/com/mydeck/app/domain/model/TypographySettings.kt`

```kotlin
package com.mydeck.app.domain.model

data class TypographySettings(
    val fontSizePercent: Int = 100,               // 80–200
    val fontFamily: ReaderFontFamily = ReaderFontFamily.SYSTEM_DEFAULT,
    val lineSpacing: LineSpacing = LineSpacing.COMFORTABLE,
    val textWidth: TextWidth = TextWidth.WIDE,
    val textAlign: TextAlign = TextAlign.LEFT,
    val hyphenation: Boolean = false
)

enum class ReaderFontFamily(val displayName: String, val cssValue: String) {
    SYSTEM_DEFAULT("System Default",
        """-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, "Noto Sans", sans-serif"""),
    ROBOTO_SERIF("Roboto Serif", """"Roboto Serif", Georgia, serif"""),
    MERRIWEATHER("Merriweather", """"Merriweather", Georgia, serif"""),
    LORA("Lora", """"Lora", Georgia, serif"""),
    NOTO_SANS("Noto Sans", """"Noto Sans", Roboto, sans-serif"""),
    JETBRAINS_MONO("JetBrains Mono", """"JetBrains Mono", monospace""");

    /** Whether this font requires a woff2 file bundled in assets/fonts/ */
    val requiresBundledFont: Boolean
        get() = this != SYSTEM_DEFAULT && this != NOTO_SANS
}

enum class LineSpacing(val displayName: String, val cssValue: String) {
    COMPACT("Compact", "1.2"),
    STANDARD("Standard", "1.4"),
    COMFORTABLE("Comfortable", "1.6"),
    RELAXED("Relaxed", "1.8"),
    SPACIOUS("Spacious", "2.0")
}

enum class TextWidth(val displayName: String, val cssMaxWidth: String) {
    WIDE("Wide", "38em"),
    MEDIUM("Medium", "50em"),
    FULL("Full", "100%")
}

enum class TextAlign(val displayName: String, val cssValue: String) {
    LEFT("Left", "left"),
    CENTER("Center", "center"),
    RIGHT("Right", "right"),
    JUSTIFIED("Justified", "justify")
}
```

### 4.2 Settings Storage

Extend `SettingsDataStore` / `SettingsDataStoreImpl` with individual keys per field.
SharedPreferences does not support `Float` directly, so `lineSpacing` is stored as a
`String` enum name (same pattern used for `Theme`, `AutoSyncTimeframe`, etc.).

```kotlin
// In SettingsDataStore interface — add:
val typographySettingsFlow: StateFlow<TypographySettings>
suspend fun saveTypographySettings(settings: TypographySettings)

// In SettingsDataStoreImpl — new keys:
private val KEY_TYPO_FONT_SIZE   = intPreferencesKey("typography_font_size_percent")
private val KEY_TYPO_FONT_FAMILY = stringPreferencesKey("typography_font_family")
private val KEY_TYPO_LINE_SPACING = stringPreferencesKey("typography_line_spacing")
private val KEY_TYPO_TEXT_WIDTH  = stringPreferencesKey("typography_text_width")
private val KEY_TYPO_TEXT_ALIGN  = stringPreferencesKey("typography_text_align")
private val KEY_TYPO_HYPHENATION = booleanPreferencesKey("typography_hyphenation")
```

**Implementation pattern**: Follow the existing `preferenceFlow<T>` helper to create
a composite `typographySettingsFlow` that combines all six keys. Use the same
`SharedPreferences.OnSharedPreferenceChangeListener` approach already in place.

**Migration**: On first launch after upgrade, if `zoom_factor` != 100, map it to the
nearest `fontSizePercent` value and clear the old key. This preserves the user's
existing preference.

```kotlin
// Migration logic (run once in SettingsDataStoreImpl init or a Migrator)
suspend fun migrateZoomToTypography() {
    val oldZoom = getZoomFactor()
    if (oldZoom != 100) {
        val mapped = oldZoom.coerceIn(80, 200)
        val current = getTypographySettings()
        saveTypographySettings(current.copy(fontSizePercent = mapped))
        // Reset old key to default so migration doesn't re-run
        saveZoomFactor(100)
    }
}
```

### 4.3 Template & CSS Changes

#### 4.3.1 Strategy: Hybrid approach
Keep the existing static HTML templates for theme colors and base structure. Apply
typography settings via two mechanisms:

1. **`WebView.settings.textZoom`** — for font size (percentage). This is the simplest
   and most reliable way to scale all text in a WebView proportionally. Already in use.
2. **JavaScript injection** — for font family, line spacing, text width, alignment,
   and hyphenation. Applied after page load and on every settings change.

This avoids rewriting the template system and keeps the existing theme mechanism
intact.

#### 4.3.2 HTML Template Modifications
Add a `lang` attribute and a `mydeck-reader` class to enable CSS targeting:

```html
<!-- In all three templates, change: -->
<html>
<!-- To: -->
<html lang="%lang%">
```

And wrap the content div:

```html
<body>
    <div class="container mydeck-reader">
        %s
    </div>
</body>
```

The `%lang%` placeholder is populated from `bookmark.lang` at render time in
`Bookmark.getContent()`.

#### 4.3.3 Font Assets
Bundle woff2 font files in `app/src/main/assets/fonts/`:
```
assets/fonts/roboto-serif-regular.woff2
assets/fonts/merriweather-regular.woff2
assets/fonts/lora-regular.woff2
assets/fonts/jetbrains-mono-regular.woff2
```

Generate `@font-face` CSS blocks and inject them into the WebView. Example:

```css
@font-face {
    font-family: "Merriweather";
    src: url("file:///android_asset/fonts/merriweather-regular.woff2") format("woff2");
    font-weight: 400;
    font-display: swap;
}
```

#### 4.3.4 JavaScript Bridge

Add to `WebViewSearchBridge.kt` (or create a new `WebViewTypographyBridge.kt`):

```kotlin
object WebViewTypographyBridge {

    /**
     * Generates JavaScript that applies typography settings to the reader content.
     * Call via webView.evaluateJavascript(js, null).
     */
    fun applyTypography(settings: TypographySettings): String {
        val fontFaceDeclarations = buildFontFaceCss(settings.fontFamily)
        return """
            (function() {
                // Inject @font-face if needed
                var styleId = 'mydeck-typography-fonts';
                var existing = document.getElementById(styleId);
                if (!existing) {
                    var style = document.createElement('style');
                    style.id = styleId;
                    style.textContent = `${fontFaceDeclarations}`;
                    document.head.appendChild(style);
                }

                // Apply typography to body and reader container
                var body = document.body;
                body.style.fontFamily = '${settings.fontFamily.cssValue}';
                body.style.lineHeight = '${settings.lineSpacing.cssValue}';
                body.style.maxWidth = '${settings.textWidth.cssMaxWidth}';
                body.style.textAlign = '${settings.textAlign.cssValue}';
                body.style.hyphens = '${if (settings.hyphenation) "auto" else "manual"}';
                body.style.webkitHyphens = '${if (settings.hyphenation) "auto" else "manual"}';

                // Also apply to headings for font-family consistency
                var headings = document.querySelectorAll('h1,h2,h3,h4,h5,h6');
                headings.forEach(function(h) {
                    h.style.fontFamily = '${settings.fontFamily.cssValue}';
                });
            })();
        """.trimIndent()
    }

    private fun buildFontFaceCss(fontFamily: ReaderFontFamily): String {
        if (!fontFamily.requiresBundledFont) return ""
        val fileName = when (fontFamily) {
            ReaderFontFamily.ROBOTO_SERIF -> "roboto-serif-regular.woff2"
            ReaderFontFamily.MERRIWEATHER -> "merriweather-regular.woff2"
            ReaderFontFamily.LORA -> "lora-regular.woff2"
            ReaderFontFamily.JETBRAINS_MONO -> "jetbrains-mono-regular.woff2"
            else -> return ""
        }
        val familyName = fontFamily.cssValue.substringAfter('"').substringBefore('"')
        return """
            @font-face {
                font-family: "$familyName";
                src: url("file:///android_asset/fonts/$fileName") format("woff2");
                font-weight: 400;
                font-display: swap;
            }
        """.trimIndent()
    }
}
```

### 4.4 ViewModel Integration

Follow the existing pattern where `uiState` is a `combine()` of multiple flows.
Replace the current `zoomFactor: Flow<Int>` with `typographySettings`:

```kotlin
// In BookmarkDetailViewModel:

// Replace:
//   private val zoomFactor: Flow<Int> = settingsDataStore.zoomFactorFlow
// With:
private val typographySettings: Flow<TypographySettings> =
    settingsDataStore.typographySettingsFlow

// Update the combine() in uiState to use typographySettings instead of zoomFactor:
val uiState = combine(
    bookmarkRepository.observeBookmark(bookmarkId!!),
    updateState,
    template,
    typographySettings   // <-- changed
) { bookmark, updateState, template, typography ->
    // ... build UiState.Success with typography instead of zoomFactor
}

// In UiState.Success, replace:
//   val zoomFactor: Int
// With:
//   val typographySettings: TypographySettings

// Add:
fun onTypographySettingsChanged(settings: TypographySettings) {
    viewModelScope.launch {
        settingsDataStore.saveTypographySettings(settings)
    }
}

// Remove:
//   fun onClickChangeZoomFactor(value: Int)
```

### 4.5 Screen Integration

In `BookmarkDetailScreen.kt`:

```kotlin
// Add state for panel visibility
var showTypographyPanel by remember { mutableStateOf(false) }

// In TopAppBar actions, after the Info icon and before BookmarkDetailMenu:
if (uiState.bookmark.type == BookmarkDetailViewModel.Bookmark.Type.ARTICLE &&
    contentMode == ContentMode.READER) {
    IconButton(onClick = { showTypographyPanel = true }) {
        Icon(
            imageVector = Icons.Outlined.FormatSize,
            contentDescription = stringResource(R.string.action_typography_settings)
        )
    }
}

// After the Scaffold, render the panel:
if (showTypographyPanel) {
    TypographyPanel(
        currentSettings = uiState.typographySettings,
        onSettingsChanged = { settings ->
            viewModel.onTypographySettingsChanged(settings)
        },
        onDismiss = { showTypographyPanel = false }
    )
}
```

In `BookmarkDetailArticle`, update the WebView:

```kotlin
// Replace:  it.settings.textZoom = uiState.zoomFactor
// With:     it.settings.textZoom = uiState.typographySettings.fontSizePercent

// Add a LaunchedEffect to apply typography via JS whenever settings change:
LaunchedEffect(uiState.typographySettings) {
    webViewRef.value?.let { webView ->
        val js = WebViewTypographyBridge.applyTypography(uiState.typographySettings)
        webView.evaluateJavascript(js, null)
    }
}
```

### 4.6 Debouncing

The font size slider can fire rapidly. To avoid excessive JS evaluations:

```kotlin
// In BookmarkDetailArticle, debounce typography JS application:
LaunchedEffect(uiState.typographySettings) {
    delay(100) // 100ms debounce
    webViewRef.value?.let { webView ->
        val js = WebViewTypographyBridge.applyTypography(uiState.typographySettings)
        webView.evaluateJavascript(js, null)
    }
}
```

`textZoom` (font size) can be applied immediately without debounce since it's a
native WebView setting, not JS.

---

## 5. Implementation Plan

### Phase 1: Data Layer (Est. 2–3 hours)
1. Create `TypographySettings.kt` with all enums
2. Add keys and `typographySettingsFlow` to `SettingsDataStore` / `SettingsDataStoreImpl`
3. Add migration logic from `zoomFactor` to `fontSizePercent`
4. Unit test: settings round-trip, migration, defaults

### Phase 2: WebView Integration (Est. 2–3 hours)
1. Add `lang="%lang%"` to HTML templates; update `Bookmark.getContent()` to populate it
2. Bundle woff2 font files in `assets/fonts/`
3. Create `WebViewTypographyBridge` with `applyTypography()` and `buildFontFaceCss()`
4. Update `BookmarkDetailArticle` to use `typographySettings.fontSizePercent` for
   `textZoom` and inject JS for other properties
5. Unit test: JS generation for each setting combination

### Phase 3: ViewModel & Wiring (Est. 1–2 hours)
1. Replace `zoomFactor` with `typographySettings` in `BookmarkDetailViewModel`
2. Update `UiState.Success` data class
3. Remove `onClickChangeZoomFactor`; add `onTypographySettingsChanged`
4. Update all call sites in `BookmarkDetailScreen` (remove zoom callbacks, add
   typography callbacks)
5. Remove "Increase/Decrease text size" from `BookmarkDetailMenu`

### Phase 4: Typography Panel UI (Est. 3–4 hours)
1. Create `TypographyPanel.kt` composable with `ModalBottomSheet`
2. Implement `FontFamilyChipRow` (horizontally scrollable `FilterChip`s)
3. Implement `FontSizeSlider` (M3 `Slider` with discrete steps + value label)
4. Implement `LineSpacingSelector` (`SingleChoiceSegmentedButtonRow`)
5. Implement `TextWidthSelector` (`SingleChoiceSegmentedButtonRow`, icon-only)
6. Implement `TextAlignSelector` (`SingleChoiceSegmentedButtonRow`, icon-only)
7. Implement `HyphenationSwitch` (M3 `Switch` with label)
8. Implement "Reset to defaults" `TextButton`
9. Add typography icon to `TopAppBar` actions
10. Wire panel visibility state

### Phase 5: Polish & Testing (Est. 2–3 hours)
1. Verify all fonts render correctly in WebView across API levels 26+
2. Test with RTL content (Arabic, Hebrew) — justification and hyphenation
3. Test with very long articles — no performance regression
4. Test settings persistence across app restart
5. Test migration from old zoom factor
6. Accessibility: TalkBack navigation through the panel
7. Accessibility: minimum 48 dp touch targets on all controls
8. Preview composables for all panel states

**Total estimated effort: 10–15 hours**

---

## 6. Localization

### 6.1 String Resources

```xml
<!-- Reader typography panel -->
<string name="action_typography_settings">Text formatting</string>
<string name="typography_section_font">Font</string>
<string name="typography_section_size">Size</string>
<string name="typography_section_line_spacing">Line spacing</string>
<string name="typography_section_width">Width</string>
<string name="typography_section_alignment">Alignment</string>
<string name="typography_section_hyphenation">Hyphenation</string>
<string name="typography_reset">Reset to defaults</string>

<!-- Font family names (not translated — proper nouns) -->
<string name="font_system_default" translatable="false">System Default</string>
<string name="font_roboto_serif" translatable="false">Roboto Serif</string>
<string name="font_merriweather" translatable="false">Merriweather</string>
<string name="font_lora" translatable="false">Lora</string>
<string name="font_noto_sans" translatable="false">Noto Sans</string>
<string name="font_jetbrains_mono" translatable="false">JetBrains Mono</string>

<!-- Line spacing options -->
<string name="line_spacing_compact">Compact</string>
<string name="line_spacing_standard">Standard</string>
<string name="line_spacing_comfortable">Comfortable</string>
<string name="line_spacing_relaxed">Relaxed</string>
<string name="line_spacing_spacious">Spacious</string>

<!-- Text width options (icon-only buttons, used for content descriptions) -->
<string name="width_wide">Wide text column</string>
<string name="width_medium">Medium text column</string>
<string name="width_full">Full width text</string>

<!-- Text alignment (icon-only buttons, used for content descriptions) -->
<string name="align_left">Align left</string>
<string name="align_center">Align center</string>
<string name="align_right">Align right</string>
<string name="align_justified">Justify</string>

<!-- Hyphenation -->
<string name="hyphenation_label">Hyphenate words</string>
```

### 6.2 Key Localization Notes
- **Font names** are proper nouns and should **not** be translated (`translatable="false"`).
- **Section labels** ("Font", "Size", etc.) should be translated.
- **Icon-only buttons** must have `contentDescription` strings for accessibility.
- **RTL layouts**: Compose handles mirroring automatically for `Row`/`Column`. The
  segmented button rows and chip rows will mirror correctly. The alignment icons
  (left/right) should swap their visual meaning in RTL locales — use
  `Icons.AutoMirrored.Outlined.FormatAlignLeft` etc. where available.

---

## 7. Testing Strategy

### 7.1 Unit Tests
| Test | What to verify |
|---|---|
| `TypographySettings` defaults | All fields have expected default values |
| `ReaderFontFamily.cssValue` | Each enum produces valid CSS font-family string |
| `WebViewTypographyBridge.applyTypography()` | JS output contains correct CSS values for each setting |
| `WebViewTypographyBridge.buildFontFaceCss()` | Correct @font-face for bundled fonts, empty for system fonts |
| `SettingsDataStoreImpl` round-trip | Save → read produces identical `TypographySettings` |
| Migration from `zoomFactor` | Old zoom value maps to correct `fontSizePercent` |

### 7.2 Integration Tests
| Test | What to verify |
|---|---|
| WebView font rendering | Load article, apply each font family, verify no crash/blank |
| Settings persistence | Change settings, kill process, reopen — settings restored |
| Debounce behavior | Rapid slider changes don't cause ANR or visual glitches |

### 7.3 UI Tests (Compose)
| Test | What to verify |
|---|---|
| Panel opens/closes | Tap icon → sheet visible; swipe down → sheet dismissed |
| Font chip selection | Tap chip → selected state; only one selected at a time |
| Slider interaction | Drag slider → value label updates; WebView textZoom changes |
| Segmented button selection | Tap option → selected; previous deselected |
| Reset button | Tap → all controls return to defaults |
| Panel not shown for photos/videos | Typography icon hidden when type != ARTICLE |

### 7.4 Accessibility Tests
| Test | What to verify |
|---|---|
| TalkBack navigation | All controls reachable and announced correctly |
| Touch targets | All interactive elements ≥ 48 dp |
| Content descriptions | Icon-only buttons have meaningful descriptions |
| Font size scaling | Panel itself respects system font size preference |

### 7.5 Manual QA Checklist
- [ ] Each font renders correctly for English, CJK, Cyrillic, Arabic
- [ ] Hyphenation works for English and German (languages with good hyphenation dictionaries)
- [ ] Justified + hyphenation produces clean output without excessive word spacing
- [ ] Full-width mode looks correct on both phone and tablet
- [ ] Settings survive theme change (light → dark → sepia)
- [ ] No flash of unstyled content when opening an article with non-default settings
- [ ] Search-in-article still works correctly after typography changes

---

## 8. Performance Considerations

- **Font file sizes**: Each woff2 is typically 20–40 KB. Four bundled fonts add
  ~100–160 KB to APK size — negligible.
- **JS injection latency**: `evaluateJavascript` is async and fast (<5 ms for simple
  style changes). The 100 ms debounce prevents stacking.
- **`textZoom` changes**: Native WebView setting, applied synchronously — no
  performance concern.
- **No template regeneration**: Typography changes do NOT require re-rendering the
  HTML template. Only JS style injection and `textZoom` are used for live updates.
  Template is only re-rendered on theme change (existing behavior).

---

## 9. Backward Compatibility

- **Old `zoomFactor` preference**: Migrated to `fontSizePercent` on first launch
  (see §4.2). The `zoomFactor` key is reset to 100 after migration.
- **`zoomFactorFlow`**: Kept in `SettingsDataStore` interface temporarily (deprecated)
  to avoid breaking other potential consumers. Can be removed in a follow-up cleanup.
- **Overflow menu**: The "Increase/Decrease text size" items are removed. Users who
  relied on them will find the new Typography icon in the toolbar — more discoverable.

---

## 10. Future Enhancements

These are explicitly **out of scope** for this iteration but noted for future work:

1. **Bold/italic font variants** — bundle additional woff2 weights for each font
2. **Custom font upload** — allow users to load their own font files
3. **Per-article settings** — remember typography preferences per bookmark
4. **Reading profiles** — save/switch between named typography presets
5. **Paragraph spacing control** — separate from line spacing
6. **Letter spacing control** — for accessibility
7. **Theme integration** — different default typography per theme (e.g., serif for sepia)
8. **Atkinson Hyperlegible** — add as an accessibility-focused font option
