# Spec: Welcome Screen Quick-Access Buttons

## Background

The Welcome/server-connection screen (`WelcomeScreen.kt`) is the entry point
before a user has signed in. Today it offers no way to reach the About page,
Logs, or User Guide without first connecting — useful information when
diagnosing a connection problem (e.g. "is my server actually OAuth-capable?")
or when a user just wants to read the guide before connecting.

## Requirements

Add three round icon buttons to the bottom of the Welcome screen:

| Position | Icon | Destination |
|----------|------|-------------|
| Bottom left | Info | About page (variant, see below) |
| Bottom center | Question mark | User Guide |
| Bottom right | Logs/list icon | Logs page |

For all three, tapping the back button (top left, on the destination screen)
returns the user to the Welcome screen.

## Navigation

Confirmed via code inspection (MyDeck, package `com.mydeck.app`):

- All three destination routes (`AboutRoute`, `LogViewRoute`, `UserGuideRoute`)
  and `WelcomeRoute` already live in the same flat `NavHost` graph in
  `AppShell.kt`. There is no nesting or auth guard at the route level — the
  only auth gate in the file is the `startDestination` choice
  (`WelcomeRoute` vs `BookmarkListRoute()` based on whether a token exists).
  Navigating to `AboutRoute`/`LogViewRoute`/`UserGuideRoute` directly from
  `WelcomeScreen` requires no nav-graph restructuring — just wiring
  `navHostController.navigate(...)` calls from three new buttons.
- **`AppShell.kt` has two `NavHost` blocks** — the Compact layout's inline
  `NavHost` (`CompactAppShell`) and the shared `AppShellNavHost` used by the
  Medium layout (phone vs. tablet/foldable). Both already register the four
  routes above; `WelcomeScreen(navController)` receives the shared
  `NavHostController`, so the buttons work from either layout with no
  block-specific wiring.
- None of the three destination screens or their ViewModels have any
  dependency on an authenticated session:
  - `AboutViewModel` depends only on `SettingsDataStore`, `ConnectivityMonitor`,
    `ReadeckApi` — no token/session check.
  - `LogViewViewModel` depends only on `Context` and `SettingsDataStore`,
    reads local log files from disk — no network call.
  - `UserGuideIndexViewModel`/`UserGuideSectionViewModel` depend only on
    `MarkdownAssetLoader` reading bundled `assets/guide/en/*.md` — fully
    offline.
  - All three are already safe to open pre-login with **zero code changes**
    to the screens themselves.
- Back navigation: each destination screen's back button already calls
  `navHostController.popBackStack()` (confirmed in `AboutScreen`,
  `LogViewScreen`, `UserGuideIndexScreen`) — none hardcode a back target such
  as `BookmarkListRoute`. Entered from `WelcomeRoute`, "back" therefore pops
  naturally to the Welcome screen. **No changes to the destination screens are
  required.**

## About page variant — interpretation of "only the server info block"

The request was: *"the one difference than the normal About page is that
since there's no server connection at this point, only the server info
block."*

**Resolved interpretation:** this describes the natural state of the
existing About screen when opened with no server URL saved, not a request to
hide sections. Code inspection confirms `AboutScreenContent` already
handles the no-connection case gracefully and without code changes:

- `AboutViewModel` has no auth dependency. With no saved server URL,
  `serverUrl` is null and the server-info fetch fails or has no cache,
  setting `serverInfoError = true`.
- The server-info card already has fallback copy for this
  (`about_system_info_server_unavailable` / `about_system_info_server_error`
  strings) and renders an empty detail list rather than crashing.
- App version/description, Credits, Project Links, and License sections are
  static (no server dependency) and render normally regardless of connection
  state.

So **"only the server info block" differs** in the sense that it's the one
section whose content changes (shows an "unavailable"/"not connected" state
instead of live server details) — everything else on the page (app info,
credits, project links, license) is identical to the normal About page.

**Implementation implication:** no new `showOnlyServerInfo`-style parameter
or conditional section hiding is needed. The Welcome-screen entry point can
open the existing `AboutScreen` unmodified, with back navigation returning to
Welcome (per the Navigation section above).

**If this interpretation is wrong** (i.e. the actual intent was to hide
Credits/Project Links/License and show *only* the server-info card), that
would require a new boolean parameter threaded through `AboutScreen` →
`AboutScreenContent`, since those sections currently always render with no
existing gate. The server-info card is already cleanly isolated as its own
call to the reusable `CollapsibleInfoCard` composable, so extracting it alone
would be straightforward if needed — but the simpler, no-code-change reading
above should be confirmed with the user before implementation.

## Implementation notes

- New buttons: `FilledTonalIconButton` (round Material3 control) at the bottom
  of the Welcome column, pushed down with a weighted spacer, laid out in a
  `Row` with `Arrangement.SpaceEvenly`. Icons: `Icons.Outlined.Info` (About),
  `Icons.Outlined.HelpOutline` (User Guide), `Icons.AutoMirrored.Outlined.List`
  (Logs) — matching the icons the drawer/rail already use for About and Guide.
  Each needs a content description (accessibility) added as a new string
  resource — same localization requirement as any new string (English
  placeholder in all language files per `CLAUDE.md`).
- User guide documentation (`app/src/main/assets/guide/en/getting-started.md`)
  should mention the new quick-access buttons on the connection screen, per
  the project's documentation policy.
- Touches both `NavHost` blocks' entry point via the shared `NavHostController`
  and `WelcomeScreen.kt`.
- No backend/API changes; purely client-side navigation and layout.
