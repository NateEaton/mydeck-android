# MyDeck: Material Design 3 Compliance Review & Remediation Plan

## 1. Executive Summary

**Material Compliance Score:** **7.5 / 10**

**Biggest Strengths:**
*   **Strong Foundation:** Correct usage of `MaterialTheme` and dynamic color schemes (`dynamicLightColorScheme`/`dynamicDarkColorScheme`).
*   **Navigation Structure:** The implementation of Modal Drawer and Bottom Sheet follows standard Android patterns correctly.
*   **Feature Density:** Successfully displays complex metadata (read time, word count) without breaking layouts on mobile.

**Biggest Design Risks:**
*   **Navigation Architecture:** The current navigation implementation (drawer inside screens vs. wrapping screens) will cause conflicts with the planned Tablet "Permanent Drawer".
*   **Static Transitions:** Navigation "cuts" instantly between screens, making the app feel like a web wrapper rather than a native Android app.
*   **Custom UI Re-implementations:** Settings and Lists use custom `Row`/`Column` layouts instead of standard M3 composables like `ListItem`, leading to subtle spacing and accessibility inconsistencies.
*   **Contrast & Accessibility:** The text overlay on Mosaic cards relies on a weak gradient, failing accessibility standards on bright images.

---

## 2. Sequencing Strategy

To minimize code churn, I recommend the following execution order.

1.  **Phase 1 Remediation (Foundations):** Implement these **before** starting the Tablet feature. The navigation refactoring here is a strict prerequisite for a clean Tablet implementation.
2.  **Feature: Filtered List:** Can be implemented immediately after Phase 1. It relies on the `ListItem` and `Surface` components established there.
3.  **Feature: Tablet Layout:** Implement this last. It requires the clean navigation hierarchy from Phase 1 and the modular list components from Phase 1/2.
4.  **Phase 2 Remediation (Polish):** Can be done iteratively alongside feature work.

---

## 3. Remediation Plan

### Phase 1: High-Impact UI Fixes (Prerequisites)
*These items fix UX issues and prepare the architecture for the Tablet feature.*

#### 1. Architect Content-Scoped Navigation (Critical for Tablet)
*   **Severity:** High
*   **Evidence:** `BookmarkListScreen.kt` currently hosts its own `ModalNavigationDrawer`.
*   **Problem:** If you animate the root `NavHost` now, the Drawer will slide away with the screen. For Tablet, the Drawer must exist *outside* the navigation content.
*   **Fix:** Lift the Drawer out of individual screens.
    *   **Structure:** `AppTheme -> ModalNavigationDrawer -> Scaffold -> NavHost`.
    *   **Tablet Future:** This allows you to easily swap `ModalNavigationDrawer` for `PermanentNavigationDrawer` based on screen width without touching the `NavHost` content.
*   **Effort:** Medium

#### 2. Implement Navigation Transitions
*   **Severity:** High (Visual Feel)
*   **Evidence:** `MainActivity.kt` (`MyDeckNavHost`).
*   **Problem:** Instant cuts between screens feel jarring and non-native.
*   **Fix:** Add standard M3 transitions to the `composable` definitions in `MainActivity.kt`.
*   **Snippet:**
    ```kotlin
    composable<BookmarkListRoute>(
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) }
    ) { ... }
    ```
*   **Effort:** Medium

#### 3. Adopt M3 `ListItem` for Settings & Lists
*   **Severity:** Medium
*   **Evidence:** `SettingsScreen.kt` uses custom `Row` layouts.
*   **Problem:** Manual layouts miss M3 specs for min-heights (56dp/72dp), padding (16dp), and text styles.
*   **Fix:** Replace custom rows with `androidx.compose.material3.ListItem`.
*   **Benefit:** This component is essential for the "Filter Summary Bar" in your Filtered List spec.
*   **Effort:** Low

#### 4. Fix Mosaic Card Text Contrast
*   **Severity:** High (Accessibility)
*   **Evidence:** `BookmarkCard.kt` (`BookmarkMosaicCard`) uses `Brush.verticalGradient`.
*   **Problem:** Text is illegible on bright images; gradient strength is inconsistent across screen sizes.
*   **Fix:** Replace the gradient box with a semi-transparent `Surface` at the bottom of the card.
    ```kotlin
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) { ... }
    }
    ```
*   **Effort:** Low

---

### Phase 2: Refinements & Polish

#### 1. Standardize Navigation Drawer Header
*   **Evidence:** `BookmarkListScreen.kt`.
*   **Recommendation:** The current header "MyDeck" + icons is too dense. Use `Text(style = MaterialTheme.typography.headlineSmall)` with 28dp top padding. Consider adding the App Icon or User Avatar here to anchor the menu visually.

#### 2. Scrollbar Compatibility
*   **Evidence:** `VerticalScrollbar.kt`
*   **Risk:** This custom implementation likely depends on `LazyListState`. The Tablet spec requires `LazyStaggeredGridState`.
*   **Fix:** Refactor `VerticalScrollbar` to accept `ScrollableState` or hide it on touch devices (M3 standard behavior is to fade scrollbars).

#### 3. Skeleton Loading
*   **Evidence:** `BookmarkCard.kt`.
*   **Recommendation:** While images load, use the `com.google.accompanist:accompanist-placeholder` library or a custom shimmering modifier on the *text* lines. This makes the app feel faster than waiting for the "Bubbles" placeholder.

---

## 4. Component Audit Table

| Component | Current Implementation | M3 Recommendation | Action |
| :--- | :--- | :--- | :--- |
| **Settings Item** | Custom `Row` | `ListItem` | **Replace** |
| **Mosaic Card** | Custom `Card` + Gradient | `Card` + `Surface` Scrim | **Refactor** for contrast |
| **Top Bar** | `TopAppBar` | `CenterAlignedTopAppBar` | **Update** for main screens |
| **Chips** | `SuggestionChip` | `FilterChip` / `InputChip` | **Update** in Filter UI |
| **Empty State** | Text centered | Icon (48dp) + Headline + Body | **Polish** |
| **Dialogs** | `AlertDialog` | `AlertDialog` | **Keep** (Good usage) |
| **Scrollbar** | Custom implementation | Standard `Modifier.scrollbar` | **Evaluate** for grid support |

---

## 5. Design Tokens (Dimens.kt)

Create `ui/theme/Dimens.kt` to centralize spacing. This is crucial for the Tablet layout where you might want to swap spacing values based on screen size.

```kotlin
object Dimens {
    val PaddingSmall = 4.dp
    val PaddingMedium = 8.dp
    val PaddingNormal = 16.dp
    val PaddingLarge = 24.dp
    
    val GridSpacing = 8.dp // For Tablet Staggered Grid
    
    val IconSizeSmall = 18.dp
    val IconSizeNormal = 24.dp
    
    val CornerRadiusCard = 12.dp
    val CornerRadiusSheet = 28.dp
}
```

---

## 6. Quick Wins Checklist

*   [ ] **Edge-to-Edge:** Ensure `StatusBarColor` in `Theme.kt` is `Color.Transparent`.
*   [ ] **Haptics:** Add `LocalHapticFeedback.current.performHapticFeedback(LongPress)` to bookmark actions (Archive/Delete).
*   [ ] **Inputs:** In `AddBookmarkSheet`, attach the progress bar to the top of the sheet container, not floating in the middle.
*   [ ] **Typography:** In `BookmarkDetailScreen`, only justify text if the user setting is enabled.
*   [ ] **Icons:** Ensure the "Menu" icon uses an `IconButton` with a valid `onClickLabel` for TalkBack.
*   [ ] **Spacing:** Increase spacing between Favicon and Title in `BookmarkCompactCard` (8dp -> 12dp).
*   [ ] **Settings:** Add `contentPadding` to the Settings LazyColumn so the last item isn't covered by the gesture bar.

---

## Appendix A: "Filtered List" Feature Recommendations

**Goal:** Functional parity with Readeck web, but Native Android feel.

**1. The "Inline Panel" Container**
*   **Visuals:** Do not use a plain white box. Use `Surface(color = MaterialTheme.colorScheme.surfaceContainer)` (or `surfaceVariant`). This subtle color shift visually separates the filter controls from the content list.
*   **Animation:** Use `AnimatedVisibility(enter = expandVertically() + fadeIn())` to reveal it.

**2. Form Inputs -> Chips**
Avoid dropdown menus or checkboxes. They feel like a web port.
*   **Status/Type:** Use `FilterChip`.
    *   *Example:* `FilterChip(selected = type == Article, onClick = { ... }, label = { Text("Article") })`
*   **Sort Order:** Use `SingleChoiceSegmentedButtonRow`. This is the modern M3 replacement for toggle groups and works perfectly for "Newest | Oldest".

**3. The Summary Bar**
*   **Component:** Use a `LazyRow` of `InputChip` elements.
*   **Interaction:** Allow users to tap the trailing icon ('X') on a chip to remove that specific filter directly from the summary bar.
*   **Hierarchy:** This bar should persist below the TopAppBar even when the full filter panel is closed.

---

## Appendix B: "Tablet Layout" Feature Recommendations

**Goal:** Effective layout on all sizes; align with Readeck Web structure.

**1. Layout Structure: Adaptive Scaffold**
*   **Library:** Use `androidx.compose.material3.adaptive`.
*   **Component:** `ListDetailPaneScaffold`.
*   **Why:** It handles the complex logic of "When do I show both panes?" and "How do I animate between them?" automatically. It is far superior to writing a custom `Row { List(); Detail() }` implementation.

**2. Navigation: Permanent vs. Rail**
*   **Landscape (Expanded):** Use `PermanentNavigationDrawer`. This aligns with the Readeck Web sidebar.
*   **Portrait (Medium):** Use `NavigationRail` (vertical bar of icons). This saves horizontal space for content while keeping navigation accessible thumb-side.
*   **Phone (Compact):** Keep the `ModalNavigationDrawer`.

**3. Reader View: The "Readable" Measure**
*   **Problem:** Text stretching 100% width on a tablet landscape screen is unreadable (lines are too long).
*   **Recommendation:** Wrap the `WebView` or Article content in a centered container with a max width.
    ```kotlin
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier.widthIn(max = 840.dp)) {
            // Reader Content
        }
    }
    ```
*   **Result:** This creates pleasant whitespace margins (gutters) on large screens, simulating a sheet of paper.

**4. Staggered Grid Typography**
*   **Risk:** In the mosaic layout, small cards might squash titles.
*   **Fix:** Ensure your `Text` composable has `overflow = TextOverflow.Ellipsis` and `minLines = 2` to prevent layout jumping when cards resize dynamically.