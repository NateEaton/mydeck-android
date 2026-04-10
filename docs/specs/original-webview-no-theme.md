# Original Web View — No App Theme Applied (Known Limitation)

**Status:** Documented, not fixed (2026-04-10)

---

## 1. Behaviour

When a bookmark is opened in "View Original" mode (`BookmarkDetailOriginalWebView`), the web page does not follow the app's chosen theme (Sepia, Dark, Black, Paper). It also does not reliably follow the system light/dark setting.

---

## 2. Why

"View Original" loads the bookmark's source URL directly in a plain WebView — it is a lightweight in-app browser, not a Readeck-extracted reader. The page appearance is entirely controlled by the remote site's CSS. The app has no opportunity to inject its own styles.

There is a partial mitigation available: `WebSettingsCompat.setForceDark()` (deprecated in API 33, superseded by the `prefers-color-scheme` media query approach) can instruct the WebView to apply an automatic algorithmic dark mode inversion when the system is in dark mode. However:

- It does not apply app-specific themes (Sepia is a warm light theme with no equivalent web API).
- The algorithmic inversion produces poor results on many sites (inverted images, broken colour contrast).
- Sites that already implement `prefers-color-scheme` handle dark mode themselves; forcing it double-inverts them.
- The API is deprecated and its replacement (`WebSettings.setAlgorithmicDarkeningAllowed`) behaves differently across API levels.

---

## 3. Decision

Leave "View Original" as a raw browser experience. The user has explicitly chosen to leave the reader and view the original page; it is reasonable for that page to look like itself.

**If revisited:** The least-risky improvement would be to enable `setAlgorithmicDarkeningAllowed(true)` when the system is in dark mode, accepting that some sites will look worse but most will look better. This would apply only to system dark mode — not to app-level Sepia/Dark/Black — and should be treated as a separate feature decision.

---

## 4. Files Relevant

| File | Notes |
|------|-------|
| `ui/detail/components/BookmarkDetailWebViews.kt` | `BookmarkDetailOriginalWebView` composable — the plain WebView for original mode. |
