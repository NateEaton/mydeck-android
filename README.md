# MyDeck

[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

## Overview

MyDeck is an Android application for managing and reading your saved articles and bookmarks with a Pocket-like user experience. It is forked from [ReadeckApp](https://github.com/jensomato/ReadeckApp) and is currently being refactored to provide a more streamlined and intuitive interface.

This app is a companion to [Readeck](https://readeck.org/en/), a self-hosted read-it-later service. To use this app, you'll need a Readeck account and server instance.

## Acknowledgements

MyDeck is based on [ReadeckApp](https://github.com/jensomato/ReadeckApp) by jensomato, which is licensed under the GNU General Public License v3.0.

### Major Changes

As required by the GNU GPL v3, here are the major modifications made to the original ReadeckApp:

**UI & Navigation:**
* Rebranding as MyDeck
* Pocket-like sidebar navigation: My List, Archive, Favorites views (replacing All/Unread)
* Revise header to show view name
* Add About menu option / dialog
* Add dedicated Labels view to browse and filter by labels
* Multiple bookmark list layouts: Grid, Compact, and Mosaic views
* List sorting functionality
* Shift reading view menu options from FAB to header

**Bookmark Management:**
* Full label management system: add, edit, and remove labels on individual bookmarks
* Inline action icons on bookmark cards (replacing 3-dot overflow menu)
* Reading time displayed on bookmark cards
* Bookmark details dialog showing metadata (type, language, word count, reading time, authors, description)
* Interactive labels section in details dialog with add/remove capabilities
* Label filtering: click any label to filter bookmarks by that label
* Clickable label chips on bookmark cards for quick filtering
* Auto-populate URL field when adding bookmarks using clipboard contents
* Account page automatically displays if not signed in

**Content & Reading:**
* Full support for Article, Photo, and Video bookmark types
* Dual content view modes: Article (Readeck-extracted content) and Original (embedded web view)
* Content view toggle in reading view header
* Embedded content support for photos and videos via iframe embeds
* Improved reading view for photo and video content with fallback descriptions

**Search & Discovery:**
* Global full-text search across bookmark titles, site names, and labels
* Search clears automatically when returning to list view

**Planned Features (In Development):**
* Revised sync model: decoupled bookmark metadata sync from content sync with configurable content policies (Automatic, Manual, Date Range)
* Enhanced sync status reporting and offline indicators
* Wi-Fi-only and battery-saver aware content downloads

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE). Some of the used libraries are released under different licenses.
