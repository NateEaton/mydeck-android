# Mini Spec: Video Fullscreen

## Goal

Provide a reliable fullscreen experience for embedded video bookmarks without dropping back to the reader during normal fullscreen use.

## Final Solution

- Keep embedded video fullscreen inside the bookmark detail screen by hosting the provider's custom fullscreen view in a dedicated Compose overlay.
- Hide the underlying reader WebView while the fullscreen custom view is active, then restore it when fullscreen exits.
- Use app-provided overlay controls instead of relying on device rotation behavior:
  - **Close** exits fullscreen
  - **Rotate** toggles the fullscreen player between portrait and landscape presentation
- Let the overlay rotate the fullscreen custom view in place rather than forcing device orientation changes.
- Keep the controls temporary: they appear on entry, fade after a short delay, and reappear when the user taps the screen.

## Exit Rules

- A UI-driven exit (Close button or Back) dismisses fullscreen and notifies the provider callback once.
- A provider-driven exit (`onHideCustomView`) clears the fullscreen host without re-triggering the callback path.
- The host restores reader visibility itself so fullscreen exit does not depend entirely on provider behavior.

## User-Facing Behavior

- Tapping the video's own fullscreen button opens fullscreen when the embed provider supports it.
- While fullscreen is open, the user can tap **Rotate** in the top-right corner to switch between portrait and landscape without leaving fullscreen.
- Pressing **Back** while rotated returns the fullscreen player to its default orientation first; pressing **Back** again exits fullscreen.

## Files

- `app/src/main/java/com/mydeck/app/ui/detail/BookmarkDetailScreen.kt`
- `app/src/main/java/com/mydeck/app/ui/detail/components/BookmarkDetailWebViews.kt`
- `app/src/main/assets/guide/en/reading.md`
