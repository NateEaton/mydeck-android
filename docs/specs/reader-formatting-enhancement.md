# Reader Formatting Enhancement Design Specification

## Overview

This specification details the enhancement of MyDeck's reading experience by replacing the current basic font size controls with a comprehensive typography panel that matches and exceeds the functionality available in the native Readeck interface.

## Current State Analysis

### Existing Implementation
- **Location**: Font size controls are in the overflow menu (BookmarkDetailMenu)
- **Controls**: Text increase/decrease buttons with 25% increments
- **Range**: 25% to 400% zoom factor
- **Storage**: SettingsDataStore with encrypted SharedPreferences
- **Application**: Applied via CSS zoom in HTML templates
- **Templates**: Separate light/dark/sepia HTML templates with Sakura.css

### Limitations
- Coarse 25% increments (too large for fine-tuning)
- No font family selection
- No line spacing control
- No text width/layout options
- No text justification options
- No hyphenation control
- Buried in overflow menu

## Readeck Feature Analysis

Based on research of Readeck's codebase and documentation:

### Current Readeck Features
- **Font Selection**: 8 fonts including serif, sans-serif, and monospace options
  - Lora (serif)
  - Public Sans (sans-serif) 
  - Merriweather (serif)
  - Inter (sans-serif)
  - IBM Plex Serif (serif)
  - Luciole (accessibility-focused)
  - Atkinson Hyperlegible (accessibility-focused)
  - JetBrains Mono (monospace)

- **Typography Controls**: Font size, line height
- **Theme Modes**: White, beige, dark, black (OLED)
- **Settings Persistence**: Remembers user preferences

### Enhancement Requests (Issue #69)
- Dark mode variants (white/beige/dark/black OLED)
- Monospaced font option
- Additional typography controls

## Functional Requirements

### 1. Typography Panel Interface
- **Trigger**: New format icon in reading view header (replacing overflow menu font controls)
- **Presentation**: Slide-up panel from bottom of screen
- **Material Design 3**: Follow latest Material Design guidelines
- **Immediate Effect**: All changes apply instantly without requiring confirmation

### 2. Typography Controls

#### Font Size
- **Range**: 12px to 24px (equivalent to 75% to 150% of current base)
- **Increments**: 1px steps for fine control
- **Default**: 16px (current base size)
- **Preview**: Live preview as slider moves

#### Font Family
- **Sans-serif Options**:
  - System Default (current)
  - Inter
  - Public Sans
  - Roboto (Android standard)

- **Serif Options**:
  - Georgia (web-safe fallback)
  - Merriweather
  - Lora
  - IBM Plex Serif

- **Monospace Options**:
  - JetBrains Mono
  - IBM Plex Mono
  - System monospace

- **Accessibility Options**:
  - Atkinson Hyperlegible
  - Luciole

#### Line Spacing
- **Range**: 1.2 to 2.0
- **Increments**: 0.1 steps
- **Default**: 1.618 (current golden ratio)
- **Presets**: 
  - Compact: 1.2
  - Standard: 1.4
  - Comfortable: 1.618 (current)
  - Relaxed: 1.8
  - Spacious: 2.0

#### Text Width
- **Level 1**: Modest margins (current 38em max-width)
- **Level 2**: Narrow margins (28em max-width)
- **Level 3**: Full width with standard padding (90vw max-width)

#### Text Justification
- **Options**: Left, Right, Center, Justified
- **Default**: Left (current)
- **Note**: Justification should include hyphenation consideration

#### Hyphenation
- **Options**: On/Off
- **Default**: Off
- **Behavior**: CSS hyphens property with language support

### 3. Panel Layout

#### Material Design 3 Bottom Sheet
- **Handle**: Drag handle at top
- **Header**: "Typography Settings" with close button
- **Sections**: Grouped by control type
- **Scrollable**: Content scrolls if needed
- **Backdrops**: Semi-transparent backdrop with tap-to-dismiss

#### Control Organization
1. **Font Settings**
   - Font family selector (dropdown/grid)
   - Font size slider with value display

2. **Spacing Settings**
   - Line spacing slider with presets
   - Text width selector (segmented buttons)

3. **Text Alignment**
   - Justification selector (segmented buttons)
   - Hyphenation toggle

4. **Reset Section**
   - Reset to defaults button
   - Apply to all articles toggle (future enhancement)

## Technical Design

### 1. Data Model

#### TypographySettings
```kotlin
data class TypographySettings(
    val fontSize: Int = 16, // px
    val fontFamily: FontFamily = FontFamily.SYSTEM_DEFAULT,
    val lineHeight: Float = 1.618f,
    val textWidth: TextWidth = TextWidth.MODEST,
    val textJustification: TextJustification = TextJustification.LEFT,
    val hyphenation: Boolean = false
)

enum class FontFamily {
    SYSTEM_DEFAULT,
    INTER, PUBLIC_SANS, ROBOTO,
    GEORGIA, MERRIWEATHER, LORA, IBM_PLEX_SERIF,
    JETBRAINS_MONO, IBM_PLEX_MONO, SYSTEM_MONO,
    ATKINSON_HYPERLEGIBLE, LUCIOLE
}

enum class TextWidth {
    MODEST, NARROW, FULL
}

enum class TextJustification {
    LEFT, RIGHT, CENTER, JUSTIFIED
}
```

### 2. Settings Storage

#### SettingsDataStore Extensions
```kotlin
interface SettingsDataStore {
    // Existing methods...
    
    // Typography settings
    val typographySettingsFlow: StateFlow<TypographySettings>
    suspend fun saveTypographySettings(settings: TypographySettings)
    suspend fun getTypographySettings(): TypographySettings
}
```

#### SharedPreferences Keys
```kotlin
private val KEY_FONT_SIZE = intPreferencesKey("typography_font_size")
private val KEY_FONT_FAMILY = stringPreferencesKey("typography_font_family")
private val KEY_LINE_HEIGHT = floatPreferencesKey("typography_line_height")
private val KEY_TEXT_WIDTH = stringPreferencesKey("typography_text_width")
private val KEY_TEXT_JUSTIFICATION = stringPreferencesKey("typography_text_justification")
private val KEY_HYPHENATION = booleanPreferencesKey("typography_hyphenation")
```

### 3. Template System Enhancement

#### Dynamic CSS Generation
Instead of static HTML templates, generate CSS dynamically:

```kotlin
class TypographyCssGenerator {
    fun generateCss(settings: TypographySettings, isDark: Boolean): String {
        return """
            .mydeck-reader {
                font-size: ${settings.fontSize}px;
                font-family: ${getFontFamilyCss(settings.fontFamily)};
                line-height: ${settings.lineHeight};
                max-width: ${getTextWidthCss(settings.textWidth)};
                text-align: ${settings.textJustification.value.toLowerCase()};
                hyphens: ${if (settings.hyphenation) "auto" else "manual"};
            }
        """
    }
    
    private fun getFontFamilyCss(fontFamily: FontFamily): String {
        return when (fontFamily) {
            FontFamily.SYSTEM_DEFAULT -> "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
            FontFamily.INTER -> "'Inter', sans-serif"
            // ... other font mappings
        }
    }
    
    private fun getTextWidthCss(textWidth: TextWidth): String {
        return when (textWidth) {
            TextWidth.MODEST -> "38em"
            TextWidth.NARROW -> "28em" 
            TextWidth.FULL -> "90vw"
        }
    }
}
```

#### Font Loading
```kotlin
class FontLoader {
    private val loadedFonts = mutableSetOf<FontFamily>()
    
    suspend fun loadFont(fontFamily: FontFamily, context: Context) {
        if (fontFamily in loadedFonts) return
        
        when (fontFamily) {
            FontFamily.INTER -> loadWebFont("Inter", context)
            FontFamily.MERRIWEATHER -> loadWebFont("Merriweather", context)
            // ... other fonts
            else -> {} // System fonts don't need loading
        }
        
        loadedFonts.add(fontFamily)
    }
    
    private suspend fun loadWebFont(fontName: String, context: Context) {
        // Load from assets or remote CDN
        // Use TypefaceCompat for modern Android
    }
}
```

### 4. UI Components

#### TypographyPanel Composable
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypographyPanel(
    isVisible: Boolean,
    currentSettings: TypographySettings,
    onSettingsChanged: (TypographySettings) -> Unit,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Typography Settings",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Font Family Section
                TypographySection(title = "Font") {
                    FontFamilySelector(
                        selected = currentSettings.fontFamily,
                        onSelected = { family -> 
                            onSettingsChanged(currentSettings.copy(fontFamily = family))
                        }
                    )
                }
                
                // Font Size Section  
                TypographySection(title = "Size") {
                    FontSizeSlider(
                        value = currentSettings.fontSize,
                        onValueChange = { size ->
                            onSettingsChanged(currentSettings.copy(fontSize = size))
                        }
                    )
                }
                
                // Line Spacing Section
                TypographySection(title = "Spacing") {
                    LineSpacingSlider(
                        value = currentSettings.lineHeight,
                        onValueChange = { spacing ->
                            onSettingsChanged(currentSettings.copy(lineHeight = spacing))
                        }
                    )
                }
                
                // Text Width Section
                TypographySection(title = "Width") {
                    TextWidthSelector(
                        selected = currentSettings.textWidth,
                        onSelected = { width ->
                            onSettingsChanged(currentSettings.copy(textWidth = width))
                        }
                    )
                }
                
                // Text Alignment Section
                TypographySection(title = "Alignment") {
                    TextAlignmentSelector(
                        justification = currentSettings.textJustification,
                        onJustificationChanged = { justification ->
                            onSettingsChanged(currentSettings.copy(textJustification = justification))
                        }
                    )
                }
                
                // Hyphenation Section
                TypographySection(title = "Hyphenation") {
                    HyphenationToggle(
                        enabled = currentSettings.hyphenation,
                        onToggle = { enabled ->
                            onSettingsChanged(currentSettings.copy(hyphenation = enabled))
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Reset Button
                OutlinedButton(
                    onClick = { 
                        onSettingsChanged(TypographySettings())
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset to Defaults")
                }
            }
        }
    }
}
```

#### Header Icon Replacement
```kotlin
// In BookmarkDetailScreen.kt TopAppBar actions
actions = {
    // ... existing actions (favorite, archive, info)
    
    // New typography button
    IconButton(onClick = { showTypographyPanel = true }) {
        Icon(
            imageVector = Icons.Default.TextFormat,
            contentDescription = stringResource(R.string.action_typography_settings)
        )
    }
    
    // Updated menu without font size controls
    BookmarkDetailMenu(
        // ... existing parameters
        // Remove: onClickIncreaseZoomFactor, onClickDecreaseZoomFactor
    )
}
```

### 5. WebView Integration

#### JavaScript Bridge for Live Updates
```kotlin
class TypographyJavaScriptBridge {
    fun updateTypography(settings: TypographySettings): String {
        return """
            (function() {
                const reader = document.querySelector('.mydeck-reader') || document.body;
                reader.style.fontSize = '${settings.fontSize}px';
                reader.style.fontFamily = '${getFontFamilyString(settings.fontFamily)}';
                reader.style.lineHeight = '${settings.lineHeight}';
                reader.style.maxWidth = '${getTextWidthString(settings.textWidth)}';
                reader.style.textAlign = '${settings.textJustification.value.toLowerCase()}';
                reader.style.hyphens = '${if (settings.hyphenation) "auto" else "manual"}';
            })();
        """
    }
}
```

### 6. ViewModel Integration

#### BookmarkDetailViewModel Updates
```kotlin
class BookmarkDetailViewModel {
    // Existing properties...
    
    private val _typographySettings = MutableStateFlow(TypographySettings())
    val typographySettings: StateFlow<TypographySettings> = _typographySettings.asStateFlow()
    
    init {
        // Load typography settings
        viewModelScope.launch {
            _typographySettings.value = settingsDataStore.getTypographySettings()
        }
    }
    
    fun updateTypographySettings(settings: TypographySettings) {
        viewModelScope.launch {
            settingsDataStore.saveTypographySettings(settings)
            _typographySettings.value = settings
        }
    }
}
```

## Implementation Plan

### Phase 1: Foundation (Week 1)
1. **Data Model & Storage**
   - Create TypographySettings data class
   - Extend SettingsDataStore interface and implementation
   - Add SharedPreferences keys and migration logic

2. **CSS Generation System**
   - Create TypographyCssGenerator
   - Implement dynamic CSS generation
   - Update template loading to support dynamic CSS

### Phase 2: UI Components (Week 2)  
1. **Typography Panel**
   - Create TypographyPanel bottom sheet
   - Implement individual control components
   - Add Material Design 3 styling

2. **Header Integration**
   - Add format icon to reading view header
   - Remove font size controls from overflow menu
   - Wire up panel visibility state

### Phase 3: Font Loading & WebView (Week 3)
1. **Font Management**
   - Implement FontLoader for web fonts
   - Add font assets to project
   - Handle font loading states

2. **WebView Integration**
   - Create JavaScript bridge
   - Implement live typography updates
   - Handle WebView JavaScript interface

### Phase 4: Polish & Testing (Week 4)
1. **Edge Cases**
   - Handle font loading failures
   - Validate settings ranges
   - Add error recovery

2. **Testing**
   - Unit tests for CSS generation
   - UI tests for panel interactions
   - Integration tests with WebView

## Localization Considerations

### String Resources
```xml
<!-- Typography settings -->
<string name="action_typography_settings">Typography Settings</string>
<string name="typography_font">Font</string>
<string name="typography_size">Size</string>
<string name="typography_spacing">Spacing</string>
<string name="typography_width">Width</string>
<string name="typography_alignment">Alignment</string>
<string name="typography_hyphenation">Hyphenation</string>
<string name="typography_reset">Reset to Defaults</string>

<!-- Font family names -->
<string name="font_system_default">System Default</string>
<string name="font_inter">Inter</string>
<string name="font_merriweather">Merriweather</string>
<!-- ... other font names -->

<!-- Text width options -->
<string name="width_modest">Modest Margins</string>
<string name="width_narrow">Narrow Margins</string>
<string name="width_full">Full Width</string>

<!-- Text alignment options -->
<string name="align_left">Left</string>
<string name="align_right">Right</string>
<string name="align_center">Center</string>
<string name="align_justified">Justified</string>
```

### Right-to-Left Support
- Ensure text justification works with RTL languages
- Test font rendering with RTL text
- Consider bidi text direction in layout

### Font Accessibility
- Ensure accessibility-focused fonts are properly loaded
- Test with system accessibility settings
- Provide fallbacks for font loading failures

## Testing Strategy

### Unit Tests
1. **TypographySettings**: Test data class and defaults
2. **SettingsDataStore**: Test persistence and retrieval
3. **CssGenerator**: Test CSS output for all settings combinations
4. **FontLoader**: Test font loading and caching

### Integration Tests  
1. **WebView Updates**: Test JavaScript bridge functionality
2. **Settings Persistence**: Test settings survive app restart
3. **Font Loading**: Test fonts load correctly in WebView

### UI Tests
1. **Panel Interaction**: Test opening/closing panel
2. **Control Manipulation**: Test all controls update settings
3. **Live Preview**: Test changes apply immediately
4. **Reset Functionality**: Test reset to defaults

### Accessibility Tests
1. **Screen Reader**: Test panel navigation with TalkBack
2. **Touch Targets**: Verify minimum touch target sizes
3. **Contrast**: Test panel meets contrast requirements

## Performance Considerations

### Font Loading
- Lazy load fonts on demand
- Cache loaded fonts in memory
- Provide loading indicators

### CSS Generation
- Cache generated CSS strings
- Minimize regeneration frequency
- Use efficient string building

### WebView Performance
- Debounce rapid setting changes
- Batch CSS updates when possible
- Monitor memory usage with custom fonts

## Future Enhancements

### Advanced Features
1. **Custom Fonts**: Allow users to upload custom font files
2. **Theme Integration**: Sync typography with app themes
3. **Article-Specific Settings**: Remember settings per article
4. **Reading Profiles**: Save multiple typography profiles

### Platform Integration
1. **System Font Sync**: Sync with system font size settings
2. **Accessibility Integration**: Respect system accessibility preferences
3. **Export Settings**: Allow importing/exporting typography settings

## Success Metrics

### User Experience
- Reduced taps to change typography (from 3+ to 1)
- Increased typography customization usage
- Positive user feedback on reading experience

### Technical
- Zero performance regression in article loading
- Successful font loading in 95% of cases
- Settings persistence reliability >99%

### Accessibility
- Improved readability for users with visual impairments
- Better support for various reading preferences
- Compliance with accessibility guidelines

## Conclusion

This enhancement transforms MyDeck's reading experience from basic font sizing to a comprehensive typography system that matches and exceeds Readeck's capabilities. The implementation follows Material Design 3 principles, maintains performance, and provides immediate visual feedback for all settings changes.

The modular design allows for incremental implementation and future enhancements while maintaining backward compatibility with existing functionality.
