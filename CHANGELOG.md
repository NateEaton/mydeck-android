# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

### Changed

### Fixed

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
