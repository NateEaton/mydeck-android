# Claude Code Guidelines for MyDeck Android

This document provides guidelines for AI-assisted development on the MyDeck Android project.

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

## Other Guidelines

- Follow existing code style and patterns
- Run lint checks before committing
- Keep commits focused and well-documented
