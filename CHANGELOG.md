# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- **F-Droid build compatibility** — signing config lookups in the Gradle build now use a null-safe `findByName` instead of `getByName`, so the build no longer crashes when a build tool (like F-Droid's) strips the `release` signing config block. No change to normal build behavior.
- **Label sort by name/count** now works in the label picker shown from Add Bookmark (including the share-sheet "Save to MyDeck" flow) and from the Bookmark Details page's Edit Labels — these previously showed no count chips and ignored the sort toggle because bookmark counts weren't passed through to the picker.

## [0.14.6] - 2026-07-10

### Added

- **Reading fonts** — the reading view now offers a curated app font set (System Default, Literata, Cantarell, Cormorant Garamond, Recursive, Bitter, Gentium, Old Standard, JetBrains Mono). A new **Include native Readeck fonts** setting (off by default) adds the fonts from Readeck's native reading view (Lora, Public Sans, Merriweather, Inter, IBM Plex Serif, Luciole, Atkinson Hyperlegible).
- **Font picker** — the reading settings sheet shows the current font as a chip (in its own typeface); tapping it opens a dedicated **Select font** sheet with the full list, each font shown in its own typeface and applied live on tap.
- **Font licenses** page under **About**, listing the SIL Open Font License and Luciole (CC BY 4.0) attributions for every bundled font.
- **Wider reading-font coverage** — bundled reading fonts now render real **bold** and cover **Latin Extended** (e.g. Polish) and, where the font supports it, **Cyrillic** (Russian, Ukrainian), instead of basic Latin only. Gentium updated to SIL **Gentium 7.000**.
- OAuth Authorization Code + PKCE sign-in: tapping **Sign In** now opens the Readeck authorization page in your browser, completing sign-in without entering a code. PKCE ensures the flow is secure even with a custom URI scheme redirect.
- The app's icon is now sent as `logo_uri` during OAuth client registration, so the Readeck authorization page can display it in the Application information section.
- **Sign in with a code instead** fallback: users who cannot use the browser flow (e.g. on constrained devices) can still sign in via the existing OAuth Device Code flow by tapping the new secondary action on the sign-in screen.
- The sign-in flow now survives process death while the browser is open — if Android reclaims the app, returning from the browser resumes where it left off.
- **What's New**: after updating, a short sheet highlights what changed in the new version. First-time installs don't see it — instead they get a one-time, dismissible nudge to check out the User Guide. **About → What's New** lists every past release's notes, newest first.

### Changed

- **Label delete confirmation** now shows how many bookmarks the label will be removed from, so the scope of the action is clear before confirming.
- **Bulk bookmark delete** now requires an extra confirmation step when 25 or more bookmarks are selected, showing the count before proceeding to the usual undo snackbar flow.
- **Bottom sheets** now slide closed consistently — closing one with a button (e.g. Done, Save, Search) animates out the same way as swiping it down, instead of vanishing instantly. The label picker also drops its back arrow to match the other sheets.

### Fixed

- Signing in via the browser (Authorization Code) flow now shows your actual username on the Settings **Account** row, instead of a placeholder — the profile lookup right after sign-in could previously run before the new token was in place.

## [0.14.5] - 2026-07-03

### Added

- Collections: save a set of filter criteria (search, labels, type, date range, and more) as a named, reusable view. Create one from the new Collections screen (navigation drawer/rail) or save your current filter directly from a list via "Save as Collection". Opening a collection shows it as its own list view, and you can layer additional filters on top without changing the saved collection. Rename, edit, or delete collections at any time.
- Welcome screen quick-access buttons: reach **About**, the **User Guide**, and **Logs** before connecting to a server — handy when diagnosing a connection problem.
- The in-app User Guide now has a **search field** to look up a feature by name — tapping a result opens the page, jumps to the match, and highlights every occurrence of the term — and **Labels**, **Highlights**, **Collections**, and **Offline Reading** each have their own page in the guide's table of contents.

### Changed

- Clearer sign-in errors: connecting to a Readeck server older than 0.21 now explains the server needs updating, and pointing at something that isn't a Readeck server says so, instead of showing a generic error.
- User Guide reorganized to reduce duplication — offline-reading and download-status details now live on a single Offline Reading page, and labels and collections have their own pages.

## [0.14.4] - 2026-06-29

### Fixed

- Sign-in now succeeds against Readeck nightly server builds that no longer return `reader_settings` in the user profile response

## [0.14.3] - 2026-06-27

### Added

- User Interface Settings are now organized into clear sections: Appearance, Bookmark List, Reading, and Sharing.
- Internal browser toggle: choose whether a bookmark's original web page opens in the in-app viewer or your device's external browser.
- Label search options: match the start of a label or anywhere within it, and sort labels alphabetically or by most used.
- Show or hide the add-bookmark (+) button in the bookmark list.
- Show or hide website source icons in the Compact layout.
- "With errors" filter to find bookmarks whose content could not be extracted, plus card badges marking bookmarks with extraction errors or no readable content.

### Changed

- Label picker: in "Add" mode the on-screen Enter key now shows a checkmark and commits or creates the typed label; filter mode keeps the search action.
- Bookmarks with no readable content now show their title and description with a "No content available" note and a button to open the original page.

### Fixed

- The "With errors" filter now correctly returns bookmarks that failed extraction.

## [0.14.2] - 2026-06-19

### Added

- Offline pinning: pin articles to keep them available offline and protected from automatic cleanup. Pin or unpin from bookmark multi-select or the reader's overflow menu.
- System notification for offline content downloads, consistent with metadata sync.

### Changed

- Reworked offline content storage so downloads are reliable and complete (text and images), keyed off actual content completeness rather than a single overloaded state flag.
- Opening an article now caches it as managed content (subject to automatic cleanup); pin it to keep it offline.
- Settings shows current on-device offline storage, split into automatic and pinned content.

### Fixed

- Newly added articles now reliably download with their images instead of getting stuck as text-only.
- "Clear All Offline Content" now clears every form of downloaded content, not just managed packages.
- More reliable offline sync and trimming on flaky networks: a manual sync is no longer silently dropped, and partial or failed downloads are retried.
- No more duplicate bookmarks when saving over a dropped connection.

## [0.14.1] - 2026-06-08

### Changed

- Refined About screen copy, project repository labels, and license/source-code wording
- Updated English user guide action labels to match the current bookmark and reader controls

### Fixed

- Corrected About screen attribution for the Readeck server and original ReadeckApp project

## [0.14.0] - 2026-06-04

### Added

- Bookmark multi-select: long-press a card to enter selection mode, then batch-favorite, archive, delete, or apply labels to multiple bookmarks at once
- Top app bar overflow menu for additional actions in the bookmark list

### Changed

- Reader action footer is now a native Compose bar, replacing the in-WebView footer for smoother, more consistent interactions
- Standard release builds now enforce HTTPS-only server connections

## [0.13.2] - 2026-05-29

### Added

- Reading time and word count filters in the bookmark list, including Min/Max ranges and an option to include bookmarks with no estimate

### Fixed

- Reader no longer shows stale extracted content for bookmarks opened immediately after adding, before server-side extraction completes

## [0.13.1] - 2026-05-20

### Fixed

- Critical: bookmark metadata (title, site name, description) was being wiped when editing labels
- Sync progress indicator is now deterministic during initial bookmark load

## [0.13.0] - 2026-05-18

### Added

- Highlights: select and save text highlights while reading articles, with support for adding notes to each highlight
- Global highlights list in the navigation drawer: browse, search, filter, and sort all highlights across your bookmarks
- Swipe actions on bookmark cards for quick access to favorite, archive, and delete

### Changed

- Reader now uses native WebView scrolling for smoother, more reliable article navigation
- Picture bookmark reading view layout updated to match Readeck's presentation

### Fixed

- Foreground service crash on Android 14+ devices
- Reader top bar overlay behavior corrected
- Sync cancellation and resilience improvements

## [0.12.6] - 2026-05-05

### Fixed

- Resolved a critical OutOfMemoryError that caused crashes during offline content synchronization

### Changed

- Cleaned up obsolete code and reduced app footprint

## [0.12.0] - 2026-04-11

### Added

- In-page anchor link support in reader content: table-of-contents and fragment links now navigate correctly within articles
- Long-press context menu for in-page links to open or copy anchor targets
- Content download status icon on reading-view bookmark cards
- Reading progress icon in Compact list view

### Changed

- Sync architecture updated to multipart sync for improved reliability and consistency when refreshing bookmark metadata and content
- Sync Settings revised with automatic content sync for offline reading using volume, item-count, or date-range policies

### Fixed

- Reader text reflow regression: resolved cases where article text could disappear after layout/font reflow updates
- Server URL validation now allows http:// endpoints (in addition to https://) for self-hosted/local Readeck setups
- Offline status indicator now tracks network transitions more accurately and avoids incorrect offline icon states

## [0.11.1] - 2026-03-19

### Added

- Special thanks to Stefan (@Alanon202) in About screen and README for app functionality feedback and testing support

### Changed

- Background sync indicator removed for cleaner UI
- Filter chip behavior: dismissing synthetic chips restores preset defaults, literal chip removal remains unchanged

### Fixed

- Server error flag propagation: bookmarks with server errors now correctly appear in "With errors" filter after refresh/create operations
- Text autosizing in reader: fixed 8-12% text size increase when switching from Medium to Wide reader width
- Filter UI: synthetic filter chips now appear when preset constraints are broadened (e.g., "Is archived: N/A", "Is favorite: N/A", "Type: Any")
- Sync performance: reduced blocking spinner on app open by showing cached bookmarks immediately during background sync
- Delete operation: fixed race condition with rapid successive delete actions
- Layout stability: eliminated theme switching reflow and fixed layout shift in sync indicator
- Video controls: improved fullscreen discoverability and auto-rotation behavior
- Missing translations: added localized "Copy to clipboard" text for all languages

## [0.11.0] - 2026-03-14

### Added

- Image gallery lightbox in reading view: tap any article image to open a full-screen gallery with swipe navigation, pinch-to-zoom, double-tap to zoom, and a thumbnail strip
- Long-press context menus for images and links in reader view and bookmark list (copy, download, share, open in browser)
- Highlights and annotations: view, create, and edit Readeck highlights directly in the reading view
- "Keep screen on while reading" toggle in Settings → User Interface
- Reader appearance settings: curated themes, font size, line spacing, content width, and fullscreen mode
- Fullscreen reading mode: hides top bar while reading; swipe up to reveal controls
- Typography and Find in Page now available for Video and Picture bookmark types
- About screen shows app and server info in collapsible cards (version, build, server name, URL)
- 15-minute option added to auto-sync schedule

### Changed

- Favorite and Archive actions moved from top bar to overflow menu and inline buttons at end of article content
- Long-press context menus replaced with centered dialog popups showing a preview header
- "View original" renamed to "View web page" throughout
- Menu items and filter labels now use sentence case
- Bookmark deletion: card stays visible but greyed-out until snackbar is dismissed or undo is pressed
- Delta sync re-enabled for Readeck 0.22+; deleted bookmarks now detected immediately on pull-to-refresh
- Navigation drawer and settings screen typography refined for better visual hierarchy
- User guide gains a "Contents" button for one-tap navigation back to the table of contents

## [0.10.0] - 2026-02-26

### Added

- docs: Add fork differences, contributing guide, and issue templates

### Changed

- Refine sign-on and authorization screen branding layout
- Show base server URL in Account settings (hide /api)
- Improve filter UX with keyboard actions and preset reset behavior
- docs: Refactor README

## [0.9.2] - 2026-02-22

### Changed

- Mobile-portrait grid layout redesigned: fixed-height (168dp) cards with left thumbnail and right content split
- Grid card title upgraded to `titleMedium` typography across all grid variants

### Fixed

- Prevented TopBar jitter during overscroll bounce in reading view
- Fixed empty state flash on cold start with proper loading state indicator
- Fixed label chips not dismissing pending delete snackbars before navigation

## [0.9.1] - 2026-02-21

### Fixed

- Fixed read/unread icon and text state inconsistency in reading view overflow menu

## [0.9.0] - 2026-02-20

This is the initial MyDeck release, representing a comprehensive rebranding and feature expansion.

### Added

- OAuth Device Code Grant authentication: app provides a URL and one-time code; user authorizes via the Readeck web interface; app detects authorization automatically
- Pocket-style sidebar navigation with My List, Archive, and Favorites views
- Navigation drawer item count badges for all views (My List, Archive, Favorites, Articles, Videos, Pictures, Labels)
- Dedicated Labels view with searchable label list and per-label bookmark counts
- Three bookmark list layouts: Grid, Compact, and Mosaic
- Reading progress tracking: visual indicator on each bookmark card (unviewed, in-progress arc proportional to progress, completed checkmark); automatically marks a bookmark as Read when the end of content is reached; reopening a bookmark resumes at the furthest scroll position from the previous session
- Full label management: add, rename, delete labels; add/remove labels on individual bookmarks from cards or the details dialog
- Clickable label chips on bookmark cards for one-tap label filtering
- Bookmark Details screen (accessible from reading view overflow menu) with full metadata (thumbnail, site, author, type, language, word count, reading time, description) and label management
- Enhanced log viewer: clear-logs action, configurable log file granularity, multi-file zip export for sharing, and log retention policy
- Dual content view modes: Article (Readeck-extracted content) and Original (embedded webview), toggleable from the reading view header
- Full support for photo and video bookmark types with embedded media content
- Global full-text search across bookmark titles, site names, and labels
- Reading typography customization: font family (System Default, Noto Serif, Literata, Source Serif, Noto Sans, JetBrains Mono), size, line spacing, content width, justification, and hyphenation
- Find in Article: in-reading-view text search with highlighted current and secondary matches and previous/next navigation
- Inline title editing in reading view (tap pencil icon to edit, confirm with checkmark)
- About screen accessible from the navigation drawer, including open-source library attributions
- Unified bookmark addition: sharing a URL from another app opens a focused bottom sheet rather than the full application
- URL auto-captured from clipboard when opening the add bookmark dialog
- Add bookmark bottom sheet auto-submits after a 5-second countdown timer; timer pauses if the user interacts with the sheet
- Inline action icons on bookmark cards (Favorite, Archive, View Original, Delete) replacing the previous overflow menu
- Sorting by Added, Published, Title, Site Name, or Duration with ascending/descending toggle
- Offline action queue: state changes (favorite, archive, read, delete) made without connectivity are queued and applied when connection is restored
- Revised sync model: bookmark list sync (automatic) decoupled from content sync (configurable)
- Content sync modes: Automatic (synced with bookmark list), Manual on demand, or Manual by date range
- Content sync constraints: Wi-Fi-only and battery-saver-aware download toggles
- Sync status panel: bookmark counts (My List, Archived, Favorites) and content download status (downloaded, available, failed, no content)
- Tablet and landscape adaptive layouts: icon navigation rail for phone landscape and tablet portrait; persistent full drawer for tablet landscape
- Material Design 3 migration throughout the application
- Delete with Undo: bookmark deletions are reversible via a snackbar action until dismissed

### Changed

- Rebranded from ReadeckApp to MyDeck (application ID, name, icon, and assets)
- Reading view action buttons moved from the floating action button to the top bar
- Bookmark metadata (thumbnail, site name, author) moved from the top of article content to the Bookmark Details screen
- Theme system redesigned: app theme (Light, Dark, System Default) separated from Sepia, which is now a reading theme toggle that applies when the app is in light mode
- Account page shown automatically on first launch if not signed in

## [0.8.0] - 2025-11-11

### Added

- Added a new sepia theme. Closes #133. Contributed by @Janszczyrek
- Added the ability to scale fonts in details screen to increase or decrease font size. Closes #90. Contributed by @Janszczyrek

### Fixed

- Fixed missing support for Android devices with min sdk 24 (Android 7.0). Closes #127

## [0.7.0] - 2025-10-05

### Fixed

- Fixed a parsing error where `author` field could be null in the server response. Closes #123
- Fixed error extracting urls from share intents. Additional text is now treated as title. Closes #120
- Fixed a parsing error where `log` and `props` fields could be null in the server response. Closes #110

## [0.6.0] - 2025-06-16

### Added

- Added badges to navigation drawer items showing the count of unread, archived, favorite, article, video, and picture bookmarks.
- Added pull-to-refresh in bookmark list screen. Contributed by @sockenklaus
- Added translation for Chinese by Poesty Li
- Added translation for Spanish by Guillermo
- Added trust anchors for system and user certificates in `network_security_config.xml`. This allows the app to trust self-signed certificates. Exercise caution when adding user certificates. Malicious certificates can compromise your security. Only add certificates from sources you fully trust. Closes #105. Contributed by @ffminus
- Added the option to allow unencrypted connections (HTTP) for the Readeck server URL. This option is disabled by default and requires explicit user consent via a checkbox in the account settings.  This allows users to connect to servers that do not have HTTPS enabled, but it is strongly discouraged for security reasons. Closes #98.

### Changed

- The floating action button now adds new bookmarks instead of refreshing the list. Contributed by @sockenklaus
- The "Add Bookmark" action has been removed from the top action bar. Contributed by @sockenklaus

## [0.5.0] - 2025-05-30

### Added

- Added the ability to select the theme in the settings. The theme selection is now also considered when displaying content in the bookmark details. Dynamic changing of the dark mode when using the system theme is also supported. Closes #77
- Added the ability to open original url in browser. Closes #74. Contributed by @sockenklaus
- Added the ability to share links to bookmarks from list view and datail view. Closes #45. Contributed by @sockenklaus

### Changed

- Show placeholder images on image load failure in main list view. Closes #81
- Allow cleartext traffic for tor onion services. Closes #92

## [0.4.0] - 2025-05-21

### Added

- Implemented background synchronization of bookmarks. The app now automatically synchronizes with the Readeck server in the background to detect and remove bookmarks that have been deleted on the server. This ensures that the local bookmark list remains consistent with the server.
- Added translation for Spanish by @xmgz
- Added icons to navigation drawer by @sockenklaus

### Fixed

- Fix #64: Add library definitions to version control.
- Fix #66: Show bookmark detail view for all bookmark, even if no article content is available.

## [0.3.2] - 2025-04-28

### Fixed

- Fix #54: Persist article content in separate database table. Also improves performance.

### Changed

- Disabled baseline profile to allow reproducible builds for F-Droid

## [0.3.1] - 2025-04-15

### Added

- Added metadata for F-Droid builds

### Fixed

- Fix #53: Show bookmarks of type `photo` in detail view

### Changed

- Readeck now displays notifications when authentication fails. These notifications allow users to quickly navigate to the account screen to verify their credentials and log in again. This feature assists users in addressing token-related issues that may arise when upgrading to Readeck 1.8.0, as outlined in the breaking changes documentation (https://readeck.org/en/blog/202503-readeck-18/#breaking-changes).

## [0.3.0] - 2025-04-11

### Added

- Implemented the ability to delete bookmarks in list and detail screen. Closes #44
- Implemented the ability to change the read state of bookmarks in list and detail screen. Closes #47
- Implemented the ability to change the archive state of bookmarks in list and detail screen. Closes #43
- Implemented the ability to change the favorite state of bookmarks in list and detail screen. Closes #39
- Implemented the ability to view application logs within the settings screen and share them for troubleshooting purposes.

### Fixed

- Fix #34: Fix parsing error. Make field `read_progress` optional.
- Fix #40: Bookmark synchronization issues caused by incorrect timezone handling.

### Changed

- Now only bookmarks that are successfully loaded (`state = 0` in readeck api) are displayed. Bookmarks that are still loading or have encountered an error will not be displayed. 

## [0.2.0] - 2025-03-25

### Added

- Implemented the ability to receive shared URLs from other apps, automatically opening the create bookmark dialog and pre-populating the URL field. Closes #25

### Fixed

- Fix #23: fix error in release workflow
- Fix #27: Enforce HTTPS and allow cleartext traffic only for ts.net including subdomains.
- Fix #29, #30: Make login workflow more robust 
- Fix #18: automatically append /api to base URL if missing in login workflow

## [0.1.0] - 2025-03-19

### Added

- Initial release (as ReadeckApp, the upstream fork base).
- Implemented adding bookmarks.
- Implemented bookmark listing and detail screens.
- Implemented settings screen with account settings.
- Implemented authentication flow.
- Implemented data storage using Room database.
- Implemented dependency injection using Hilt.
- Implemented networking using Retrofit.
- Implemented MVVM architecture.

### Changed

- Initial implementation of the ReadeckApp.
