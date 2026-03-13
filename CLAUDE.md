# Claude Code Guidelines for MyDeck Android

This document provides guidelines for AI-assisted development on the MyDeck Android project.

## Usage Efficiency

**Do not run tasks in parallel.** Always execute steps sequentially, one at a time. Usage efficiency is more important than speed — do not use background agents, parallel tool calls, or concurrent subagents to accelerate work.

## Localization Requirements

**Important:** This project maintains translations for multiple languages. Any code changes that add new string resources must be accompanied by English placeholder strings in all language files.

### When Adding New Strings

1. Add the new string to `app/src/main/res/values/strings.xml` with the English text
2. Add the **same string** with English text as a placeholder to all language-specific files:
   - `app/src/main/res/values-de-rDE/strings.xml` (German)
   - `app/src/main/res/values-es-rES/strings.xml` (Spanish)
   - `app/src/main/res/values-fr/strings.xml` (French)
   - `app/src/main/res/values-gl-rES/strings.xml` (Galician)
   - `app/src/main/res/values-pl/strings.xml` (Polish)
   - `app/src/main/res/values-pt-rPT/strings.xml` (Portuguese)
   - `app/src/main/res/values-ru/strings.xml` (Russian)
   - `app/src/main/res/values-uk/strings.xml` (Ukrainian)
   - `app/src/main/res/values-zh-rCN/strings.xml` (Simplified Chinese)

### Example

If adding a new string:
```xml
<!-- In values/strings.xml -->
<string name="my_new_feature">My new feature text</string>

<!-- In values-fr/strings.xml and all other language files -->
<string name="my_new_feature">My new feature text</string>
```

The English text serves as a placeholder until professional translators provide translations for each language.

### Why This Matters

- **Lint validation:** The build system validates that all strings exist in all language files
- **User experience:** Users in other locales won't see missing string errors
- **Translation ready:** Professional translators can easily identify strings that need translation

---

## Documentation Requirements

**Important:** All user-visible features must be documented in the user guide.

### When Adding or Modifying User-Facing Features

1. Update the relevant user guide file(s) in `app/src/main/assets/guide/en/`:
   - `getting-started.md` — initial setup and authentication
   - `your-bookmarks.md` — bookmark list, cards, layouts, actions, filtering, sorting
   - `reading.md` — article/video/picture view, typography, search, lightbox
   - `organising.md` — favorites, archive, labels, deletion
   - `settings.md` — app settings and preferences

2. Keep documentation:
   - **Clear and concise** — describe what the feature does and how to use it
   - **Action-oriented** — focus on what users can do, not implementation details
   - **Consistent** — match the tone and style of existing documentation
   - **Up-to-date** — update docs in the same commit/PR as the feature change

3. For multi-language support, update only the English (`en`) files. Translations are handled separately.

### Examples of User-Visible Changes Requiring Documentation

- New UI elements (buttons, menus, dialogs)
- Changed interaction patterns (tap vs long-press, swipe gestures)
- New or modified features (filters, sorting, reading modes)
- Behavior changes that affect user workflow (deletion UX, navigation flow)

---

## Other Guidelines

- Follow existing code style and patterns
- Run the aggregate debug verification tasks before committing:
  - `./gradlew :app:assembleDebugAll`
  - `./gradlew :app:testDebugUnitTestAll`
  - `./gradlew :app:lintDebugAll`
- Keep commits focused and well-documented
