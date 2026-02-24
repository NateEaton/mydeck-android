Let me review the Readeck source markdown files and the current navigation/build setup to complete my assessment.

Let me fetch the Readeck source markdown files and review the current app navigation/build setup.

Let me continue reviewing the remaining source files and the navigation/build setup.

Now let me review the navigation setup, build config, and drawer implementation.

Let me check the navigation routes, drawer setup, and how the user guide is wired in.

Now let me look at the Readeck native user guide UI for reference, and check the [docs/en](cci:9://file:///Volumes/projects/mydeck-android/app/src/main/assets/docs/en:0:0-0:0) assets (which seem to be a duplicate of [guide/en](cci:9://file:///Volumes/projects/mydeck-android/app/src/main/assets/guide/en:0:0-0:0)).

Let me check the native Readeck help UI to understand the target presentation style.

Let me now look at the Readeck native help UI to understand the target presentation, and check for any existing `toc.json`.

Let me look at the Readeck native help page to understand the target presentation style.

Let me look at the Readeck in-app help UI specifically to understand the target presentation.

Good — I now have a comprehensive picture of the current state. Let me now check the existing [docs/en](cci:9://file:///Volumes/projects/mydeck-android/app/src/main/assets/docs/en:0:0-0:0) vs [guide/en](cci:9://file:///Volumes/projects/mydeck-android/app/src/main/assets/guide/en:0:0-0:0) duplication and the Readeck in-app help rendering approach, then draft the implementation document.

Now I have a complete picture. Let me update my plan and draft the implementation document.

Now I have all the context needed. Let me draft the implementation document.

The implementation plan is now at `@/Volumes/projects/mydeck-android/_notes/IMPL_UserGuide.md:1-219`. Here's a summary of my findings and the plan:

---

## Assessment Summary

### What's Working
The foundation is solid — route, navigation drawer entry, asset files, Markwon dependencies, and basic ViewModel/Screen structure all exist and the build compiles.

### Key Problems (10 issues identified)

1. **Wrong layout paradigm** — `NavigationRail` side-by-side split is a desktop pattern; on a phone it's unusable. Needs to be a standard two-screen TOC → Detail flow.
2. **Images don't load** — Relative paths (`./img/foo.webp`) aren't resolved to `file:///android_asset/` URIs.
3. **No M3 theming** — Markwon renders with default `TextView` styling, not your Material 3 typography/colors.
4. **YAML frontmatter visible** — @index.md has `---` frontmatter that renders as text.
5. **Duplicate assets** — [docs/en/](cci:9://file:///Volumes/projects/mydeck-android/app/src/main/assets/docs/en:0:0-0:0) and [guide/en/](cci:9://file:///Volumes/projects/mydeck-android/app/src/main/assets/guide/en:0:0-0:0) are identical; one should go.
6. **Debug println statements** throughout ViewModel and Screen.
7. **`readeck-instance://` links** will cause issues if tapped.
8. **Inter-section `.md` links** don't navigate.
9. **Glide pulled in unnecessarily** — app uses Coil everywhere else.
10. **Markwon deps not in version catalog**.

### Plan: 6 Phases

| Phase | What | Model Class | Time |
|-------|------|-------------|------|
| **1** | Cleanup (remove dupes, debug code, strip frontmatter, catalog deps) | Haiku-class | 1-2h |
| **2** | Redesign to two-screen TOC→Detail navigation | Sonnet-class | 2-3h |
| **3** | Rendering quality (images, M3 typography, inter-section links, strip `readeck-instance://`) | Sonnet-class | 3-4h |
| **4** | Polish (scroll state, transitions, error handling, remove Glide) | Haiku-class | 1-2h |
| **5** | Content adaptation (human editorial work — screenshots, terminology) | Human | Ongoing |
| **6** | Localization foundation (locale folders, detection logic) | Haiku-class | 1h |

**Your short-term goal** (same content as native Readeck, readable presentation) **is achieved at the end of Phase 3.** Phases 1-3 are ~6-9 hours total.

The recommendation is to **keep Markwon** (mature, well-suited for `AndroidView`+`TextView`) and **drop Glide** in favor of direct asset loading or Coil. The hardcoded section list is fine for now — `toc.json` can wait for localization work.


Here are the prompts, organized for efficiency:

---

## Generic Prompt (Phases 1, 4, 6)

Use this template for the straightforward phases — just swap the phase reference:

Implement **Phase [1]: [Cleanup]** of the User Guide feature on the `feature/user-guide` branch.

Read @_notes/IMPL_UserGuide.md for the full implementation plan, current state assessment, and target file structure. Execute all sub-steps listed under Phase [N]. Follow existing code patterns and conventions in the project.

**Constraints:**
- Do not modify files outside the `ui/userguide/` package, `build.gradle.kts`, or `gradle/libs.versions.toml` unless the plan explicitly calls for it.
- Do not add comments or documentation beyond what already exists.
- Verify the build compiles after your changes.
- Commit message prefix: `User guide: Phase [1] — [Cleanup]`

---

## Phase 2: Dedicated Prompt (Navigation Redesign)

This phase has the most structural complexity — new files, new routes, NavHost changes:

Implement **Phase 2: Redesign to Two-Screen Navigation** of the User Guide feature on the `feature/user-guide` branch.

Read @_notes/IMPL_UserGuide.md for the full plan. Key requirements:

1. **Replace** the current single @UserGuideScreen.kt (which uses a `NavigationRail` split layout) with two screens:
   - `UserGuideIndexScreen.kt` — Table of Contents. Uses `Scaffold` + `LazyColumn` with M3 `ListItem` composables for each section. Tapping a section navigates to the detail screen.
   - `UserGuideSectionScreen.kt` — Section detail. `Scaffold` with `TopAppBar` (section title + back button) and scrollable Markwon-rendered markdown content via `AndroidView(TextView)`.

2. **Add** `UserGuideSectionRoute(fileName: String, title: String)` to @Routes.kt (use `@Serializable data class`, matching the existing pattern).

3. **Wire** the new route into `NavHost` in @MainActivity.kt — the composable should extract `fileName` and `title` from the route and pass them to `UserGuideSectionScreen`.

4. **Split or refactor** @UserGuideViewModel.kt to support both screens. The index screen needs the section list; the section screen needs to load content by fileName. Use `SavedStateHandle` or separate ViewModels — follow whichever pattern is simpler.

5. The existing `UserGuideRoute` in @Routes.kt should remain and map to the new `UserGuideIndexScreen`.

**Reference files for conventions:**
- @Routes.kt — route definition pattern
- @MainActivity.kt — `NavHost` wiring pattern
- @BookmarkListScreen.kt — drawer navigation pattern (do not modify this file)
- @MarkdownAssetLoader.kt — data layer (modify only if needed)

**Do not** change the drawer entry in @BookmarkListScreen.kt. It already navigates to `UserGuideRoute` which is correct.

Verify the full navigation flow works: Drawer → Index → Section → Back → Index → Back to previous screen.

---

## Phase 3: Dedicated Prompt (Rendering Quality)

This is the most technically demanding phase:

Implement **Phase 3: Markdown Rendering Quality** of the User Guide feature on the `feature/user-guide` branch.

Read @_notes/IMPL_UserGuide.md for the full plan. This phase has 4 sub-tasks:

**3a. Fix image loading from assets.**
Markdown files reference images as `./img/filename.webp`. These must resolve to `file:///android_asset/guide/en/img/filename.webp` for Markwon to load them. The simplest approach: pre-process the markdown string in [MarkdownAssetLoader.loadMarkdown()](cci:1://file:///Volumes/projects/mydeck-android/app/src/main/java/com/mydeck/app/ui/userguide/MarkdownAssetLoader.kt:36:4-48:5) to replace relative image paths with absolute `file:///android_asset/guide/en/` URIs before returning. Then ensure Markwon's `ImagesPlugin` can handle `file://` URIs. Remove the `image-glide` dependency (the app uses Coil, not Glide) — use Markwon's built-in file scheme support or a custom `SchemeHandler` using `AssetManager` directly.

**3b. Apply Material 3 typography and colors.**
Create a `MarkdownRenderer.kt` (or equivalent) in `ui/userguide/` that configures Markwon with the app's M3 theme. The `AndroidView(TextView)` pattern is correct. At minimum:
- `textView.setTextColor(colorScheme.onSurface)` and `setLinkTextColor(colorScheme.primary)`
- Configure `MarkwonTheme` heading size multipliers to approximate M3 type scale
- Ensure it works in both light and dark mode (re-apply colors in the `update` block of `AndroidView`)

**3c. Handle inter-section markdown links.**
Links like `[Bookmark View](./bookmark.md)` should navigate to the corresponding `UserGuideSectionRoute`. Set a custom click handler (e.g., Markwon's `LinkResolverDef` override or a custom `MovementMethod`) that intercepts `.md` link clicks and triggers navigation. You'll need to accept a navigation callback lambda in the composable.

**3d. Strip or neutralize `readeck-instance://` URLs.**
The markdown contains links like `[Bookmark List](readeck-instance://bookmarks)`. Pre-process the markdown to convert these to plain text (keep the display text, remove the link wrapper). Regex: replace `\[([^\]]+)\]\(readeck-instance://[^)]+\)` with `$1`.

**Reference:** The Markwon version is 4.6.2. The existing Markwon setup is in @UserGuideScreen.kt (or `UserGuideSectionScreen.kt` if Phase 2 is complete). Update @build.gradle.kts to remove the `image-glide` dependency.

Verify: images render in section pages, typography looks native, `.md` links navigate between sections, `readeck-instance://` links don't crash, dark mode works.

---

These three prompts cover all six phases (the generic one handles 1, 4, and 6). Phases 5 is your editorial work so no prompt needed. You can run them sequentially — each assumes the prior phase is complete.