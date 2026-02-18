# User Guide Implementation Plan

## Current State Assessment

### What Exists (feature/user-guide branch)
- **Route & Navigation**: `UserGuideRoute` in `Routes.kt`, wired into `NavHost` in `MainActivity.kt`
- **Drawer Entry**: "User Guide" item between Settings and About in the navigation drawer (`BookmarkListScreen.kt`), using `HelpOutline` icon
- **3 Kotlin files** in `ui/userguide/`:
  - `MarkdownAssetLoader.kt` — reads markdown from `assets/guide/en/`, hardcoded section list, image loader stub
  - `UserGuideViewModel.kt` — loads sections, manages selected section + content state
  - `UserGuideScreen.kt` — Markwon-based rendering via `AndroidView(TextView)`
- **Assets**: Markdown files + webp images duplicated in both `assets/docs/en/` and `assets/guide/en/` (identical content)
- **Dependencies**: Markwon 4.6.2 (core, strikethrough, tables, tasklist, image, syntax-highlight, image-glide) added as inline dependencies in `build.gradle.kts`
- **Localization**: `user_guide` string added to all locale `strings.xml` files
- **Build**: Compiles successfully

### What's Wrong / Incomplete
1. **Layout is desktop-oriented**: Uses `NavigationRail` + side-by-side content split — inappropriate for a phone. On a phone this renders as a narrow rail with truncated titles alongside cramped content.
2. **No single-page scrollable view**: The design shows a TOC index → detail two-screen pattern, but the current implementation tries to show both simultaneously. The native Readeck help is a **single scrollable page per section**, not a split view.
3. **No toc.json**: The proposal calls for a JSON manifest, but sections are hardcoded in `MarkdownAssetLoader.kt`. This is actually fine for now — simpler and sufficient.
4. **Duplicate asset directories**: `docs/en/` and `guide/en/` contain identical files. One should be removed.
5. **Debug artifacts**: `println("DEBUG: ...")` statements throughout ViewModel and Screen.
6. **Image loading not working**: Markwon's `GlideImagesPlugin` is configured but the image paths in markdown are relative (`./img/bookmark-view.webp`), and there's no custom scheme handler to resolve `file:///android_asset/guide/en/img/...`. Images will fail to load silently.
7. **No M3 typography mapping**: Markwon renders with default Android `TextView` styling, not Material 3 theme typography/colors.
8. **Frontmatter not stripped**: `index.md` contains YAML frontmatter (`---` blocks) that will render as visible text.
9. **Markwon dependencies not in version catalog**: Added as inline strings in `build.gradle.kts` rather than through `libs.versions.toml`.
10. **No inter-section navigation**: Markdown links like `[Bookmark View](./bookmark.md)` won't navigate to the corresponding section.

---

## Target UX (Short-Term Goal)

Present the Readeck user guide content **as-is** in a readable, scrollable format that looks native to the app — functionally similar to the Readeck web help page:

- **Screen 1 — Table of Contents**: A simple `LazyColumn` list of section titles. Tapping navigates to the detail screen.
- **Screen 2 — Section Detail**: Top app bar with section title + back button. Scrollable rendered markdown content with images. M3 typography and colors.

This is a **phone-first** layout. No split-pane / NavigationRail.

---

## Implementation Phases

### Phase 1: Fix the Foundation (Cleanup & Restructure)
**Model class**: Any coding model (Haiku-class or above). Straightforward refactoring.
**Estimated effort**: 1-2 hours

#### 1a. Remove duplicate assets
- Delete `assets/docs/en/` entirely (keep only `assets/guide/en/`)
- Canonical path: `assets/guide/en/`

#### 1b. Clean up debug artifacts
- Remove all `println("DEBUG: ...")` from `UserGuideViewModel.kt` and `UserGuideScreen.kt`
- Remove the `"=== Loaded: $fileName ==="` debug prefix in `MarkdownAssetLoader.loadMarkdown()`

#### 1c. Strip YAML frontmatter
- In `MarkdownAssetLoader.loadMarkdown()`, strip content between opening and closing `---` lines before returning
- Simple regex: `content.replace(Regex("^---[\\s\\S]*?---\\n*"), "")`

#### 1d. Move Markwon versions to version catalog
- Add markwon version + library entries to `gradle/libs.versions.toml`
- Replace inline dependency strings in `build.gradle.kts` with catalog references

#### 1e. Add toc.json (optional, low priority)
- The hardcoded list in `MarkdownAssetLoader` is acceptable for now
- Can add `toc.json` later when localization work begins

**Verification**: Build compiles. Existing screen still loads content (even if layout is wrong).

---

### Phase 2: Redesign to Two-Screen Navigation
**Model class**: Sonnet-class recommended. Requires understanding Compose navigation patterns, passing arguments between screens, and M3 component usage.
**Estimated effort**: 2-3 hours

#### 2a. Create two distinct composables

**`UserGuideIndexScreen`** (replaces current `UserGuideScreen`):
```
Scaffold(
  topBar = TopAppBar(title = "User Guide", nav = back arrow)
) {
  LazyColumn {
    // Welcome text from index.md (rendered markdown, no TOC links)
    item { MarkdownContent(welcomeText) }
    
    // Section list
    items(sections) { section ->
      ListItem(
        headlineContent = section.title,
        leadingContent = section.icon,  // optional
        trailingContent = chevron,
        onClick = navigate to detail
      )
    }
  }
}
```

**`UserGuideSectionScreen`** (new):
```
Scaffold(
  topBar = TopAppBar(title = sectionTitle, nav = back arrow)
) {
  // Scrollable markdown content
  VerticalScrollableMarkdownContent(content)
}
```

#### 2b. Add navigation route with argument
- Add `UserGuideSectionRoute(fileName: String, title: String)` to `Routes.kt`
- Wire into `NavHost` in `MainActivity.kt`
- `UserGuideIndexScreen` navigates to `UserGuideSectionRoute` on item click

#### 2c. Split ViewModel or use per-screen ViewModels
- `UserGuideIndexViewModel` — loads section list from `MarkdownAssetLoader`
- `UserGuideSectionViewModel` — loads single section content by fileName
- Or keep one ViewModel and use SavedStateHandle for the section route arg

**Verification**: Can navigate from drawer → index → section → back. Content displays in each section.

---

### Phase 3: Markdown Rendering Quality
**Model class**: Sonnet-class recommended. Requires Markwon API knowledge, custom span factories, Coil/asset integration.
**Estimated effort**: 3-4 hours

#### 3a. Fix image loading from assets
The core issue: markdown references images as `./img/bookmark-view.webp` but Markwon needs to resolve these to `file:///android_asset/guide/en/img/bookmark-view.webp`.

**Approach — Custom Markwon ImagePlugin with Coil**:
- The app already uses Coil (not Glide) for image loading elsewhere
- Remove `image-glide` dependency, keep `image` dependency
- Create a custom `MarkwonImagePlugin` or `SchemeHandler` that:
  1. Intercepts relative image paths
  2. Prepends `file:///android_asset/guide/en/` to resolve them
  3. Loads via Coil or direct `AssetManager` access

**Alternative — Simpler approach**:
- Pre-process markdown content to replace relative paths with absolute asset URIs before passing to Markwon
- e.g., `content.replace("(./img/", "(file:///android_asset/guide/en/img/")`
- Then use Markwon's built-in image loading which handles `file://` URIs

#### 3b. Apply M3 typography and colors
Configure Markwon with a custom theme that maps to Material 3:

```kotlin
val markwonTheme = MarkwonTheme.builderWithDefaults(context)
    .headingBreakHeight(0)
    .headingTextSizeMultipliers(floatArrayOf(1.6f, 1.35f, 1.17f, 1.0f, 0.87f, 0.75f))
    .linkColor(colorScheme.primary)
    .build()
```

For deeper M3 integration, use Markwon's `MarkwonSpansFactory` to apply custom spans:
- H1 → `headlineMedium` typeface + size
- H2 → `titleLarge`
- Body → `bodyLarge`
- Code → `bodyMedium` with `surfaceVariant` background
- Links → `primary` color

The `AndroidView(TextView)` approach is correct for Markwon. Apply theme colors:
```kotlin
textView.setTextColor(colorScheme.onSurface.toArgb())
textView.setLinkTextColor(colorScheme.primary.toArgb())
```

#### 3c. Handle inter-section links
Markdown contains links like `[Bookmark View](./bookmark.md)` and `[Labels](./labels.md)`.

**Approach**: Set a custom `MovementMethod` or `LinkResolver` on the TextView that:
1. Intercepts clicks on links matching `*.md`
2. Maps the filename to a navigation action → `UserGuideSectionRoute`
3. Passes other URLs (like `readeck-instance://...`) to a no-op or strips them

#### 3d. Handle/strip Readeck-specific links
The markdown contains `readeck-instance://` URLs (e.g., `readeck-instance://bookmarks`). These are meaningless in the mobile app.
- Option A: Strip them (replace with plain text)
- Option B: Pre-process markdown to remove these link wrappers, keeping only the display text

**Verification**: Images render. Typography matches app theme. Section-to-section links navigate correctly. Dark mode works.

---

### Phase 4: Polish & Edge Cases
**Model class**: Haiku-class sufficient for most items. Sonnet for scroll state preservation.
**Estimated effort**: 1-2 hours

#### 4a. Scroll state
- Remember scroll position when navigating back from a section to the index
- Standard Compose `rememberLazyListState()` handles this via nav back stack

#### 4b. Loading states
- Show shimmer or progress indicator while markdown content loads (already partially implemented)
- Content loads from assets so it's near-instant; a simple fade-in transition is sufficient

#### 4c. Error handling
- Graceful fallback if a markdown file is missing
- Already partially implemented in `MarkdownAssetLoader`

#### 4d. Section icons (optional)
- Add leading icons to the TOC list items per the proposal (Bookmark icon, Glasses icon, etc.)
- Low priority, purely cosmetic

#### 4e. Remove Glide dependency
- The app uses Coil everywhere else; Markwon's `image-glide` plugin pulls in Glide unnecessarily
- Either switch to a Coil-based image handler or use direct asset loading
- This reduces APK size and avoids dependency conflicts

**Verification**: Full user flow works smoothly. No crashes on edge cases. Dark/light mode correct.

---

### Phase 5: Content Adaptation (Future — Manual/Human Work)
**Not model work** — this is editorial/content work for the human developer.

- Replace Readeck web screenshots with MyDeck mobile screenshots
- Edit markdown text: "Click" → "Tap", "Sidebar" → "Navigation Drawer", etc.
- Remove sections not applicable to MyDeck (e.g., OPDS Koreader setup if not supported)
- Add MyDeck-specific sections (e.g., "Connecting to your Readeck server")
- Update `readeck-instance://` links to either deep-link into the app or remove them

### Phase 6: Localization Foundation (Future)
**Model class**: Haiku-class. Mechanical file/folder operations.

- Create `assets/guide/fr/`, `assets/guide/de/`, etc. folder structure
- Add locale detection logic to `MarkdownAssetLoader` (check `Locale.getDefault().language`, fallback to `en`)
- Optionally add `toc.json` per locale at this point to allow locale-specific section ordering/titles

---

## Dependency Decisions

| Current | Recommendation | Rationale |
|---------|---------------|-----------|
| Markwon 4.6.2 (core + 6 plugins) | **Keep Markwon** | Mature, well-suited for AndroidView+TextView rendering. The Compose-native alternatives (compose-markdown, multiplatform-markdown-renderer) are less mature and harder to style precisely. |
| Markwon image-glide | **Remove**, use asset-direct loading or Coil | App already uses Coil; adding Glide is redundant bloat |
| No toc.json | **Keep hardcoded list for now** | Simpler. Add JSON manifest when localization work begins |
| Dual asset dirs (docs/en + guide/en) | **Remove docs/en** | Eliminate duplication |

## File Structure (Target)

```
app/src/main/
├── assets/
│   └── guide/
│       └── en/
│           ├── index.md
│           ├── bookmark.md
│           ├── bookmark-list.md
│           ├── labels.md
│           ├── collections.md
│           ├── opds.md
│           ├── user-profile.md
│           └── img/
│               ├── bookmark-view.webp
│               ├── bookmark-list.webp
│               └── ... (other images)
├── java/com/mydeck/app/ui/userguide/
│   ├── MarkdownAssetLoader.kt      (data layer - reads assets)
│   ├── UserGuideIndexScreen.kt     (TOC screen)
│   ├── UserGuideSectionScreen.kt   (detail/reader screen)
│   ├── UserGuideViewModel.kt       (shared or split VM)
│   └── MarkdownRenderer.kt         (Markwon config + M3 theming)
```

## Risk Assessment

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| Markwon image loading from assets fails | Medium | Pre-process markdown to use absolute `file:///android_asset/` URIs as fallback |
| M3 typography mapping looks off | Low | Iterative — tune multipliers and colors visually |
| Inter-section `.md` links hard to intercept | Low | Custom `LinkResolver` is well-documented in Markwon |
| `readeck-instance://` links cause crashes | Medium | Pre-process markdown to strip or neutralize before rendering |
| Markwon + Compose interop issues | Low | `AndroidView` wrapping `TextView` is the standard pattern and already working |

## Handoff Notes

- **Phases 1-2** can be done in a single session and are prerequisite for everything else
- **Phase 3** is the most technically challenging phase and benefits from a model that understands Markwon's plugin API
- **Phase 4** is incremental polish that can be done piecemeal
- **Phases 5-6** are future work and not blocking the short-term goal
- The short-term goal ("open the in-app user guide with the same content as native Readeck") is achieved at the end of **Phase 3**
