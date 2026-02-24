# Fork Differences: ReadeckApp → MyDeck

This document summarizes the differences between [ReadeckApp](https://github.com/jensomato/ReadeckApp) at its 0.8.0 release and MyDeck at its 0.9.0 release. It is a point-in-time snapshot and is not intended to be kept continuously updated. My sincere thanks to Jens for creating ReadeckApp and making it open source, and the contributors to ReadeckApp.

## GPL Compliance

MyDeck is derived from ReadeckApp, which is licensed under the GNU General Public License v3.0. As required by the GPL, the source code for MyDeck is available at this repository. The changes described below constitute the primary modifications made to the original codebase.

The original ReadeckApp repository and its authors are credited in [README.md](../README.md) and in the in-app About screen.

---

## What Changed from ReadeckApp 0.8.0 to MyDeck 0.9.0

### Authentication

| ReadeckApp 0.8.0 | MyDeck 0.9.0 |
|---|---|
| Token-based login (username + password) | OAuth Device Code Grant flow |
| Credentials entered directly in the app | Authentication delegated to the Readeck web interface |
| HTTP allowed without explicit warning | HTTP support removed (OAuth requires HTTPS) |

MyDeck uses the OAuth Device Code Grant standard: when signing in, the app provides a URL and one-time code. The user visits the URL in a browser, authorizes the app on the Readeck website, and the app detects authorization automatically.

---

### Navigation

| ReadeckApp 0.8.0 | MyDeck 0.9.0 |
|---|---|
| All / Unread top-level views | My List / Archive / Favorites views (Pocket-style) |
| No dedicated Labels view | Labels view with searchable label list and count badges |
| Phone-only layout | Adaptive: drawer on phone, icon rail on landscape/tablet portrait, persistent drawer on tablet landscape |

---

### Bookmark List

| ReadeckApp 0.8.0 | MyDeck 0.9.0 |
|---|---|
| Single list layout | Three layouts: Grid, Compact, and Mosaic |
| 3-dot overflow menu per card | Inline action icons: Favorite, Archive, View Original, Delete |
| No reading progress on cards | Progress indicator: unviewed (none), in-progress (arc), completed (checkmark) |
| Labels displayed as text on cards | Tappable label chips on cards; tap a chip to filter the list |
| Sort by Added only | Sort by Added, Published, Title, Site Name, or Duration; toggle ascending/descending |

---

### Labels

ReadeckApp 0.8.0 had no label management in the Android client — labels visible in the list were read-only. MyDeck adds:

- Add labels to any bookmark from the card or the details dialog
- Remove labels from individual bookmarks
- Rename a label (applies to all bookmarks with that label)
- Delete a label (removes it from all bookmarks)
- Filter the list by label from the Labels view or by tapping a chip on a card
- Searchable label picker in the Labels bottom sheet

---

### Reading View

| ReadeckApp 0.8.0 | MyDeck 0.9.0 |
|---|---|
| Article content only | Article mode + Original (embedded webview) toggle |
| Photo and video bookmarks show description with external link | Embedded media renders inline in a dedicated reading view |
| Reading view actions in floating action button | Reading view actions in top bar |
| Typography: font size adjustment (increase/decrease) | Full typography panel: font family, size, line spacing, content width, justification, hyphenation |
| No in-article search | Find in Article with highlighted match navigation |
| No inline title editing | Tap pencil icon to edit bookmark title inline |

---

### Adding Bookmarks

| ReadeckApp 0.8.0 | MyDeck 0.9.0 |
|---|---|
| Share from another app → opens full app | Share from another app → lightweight bottom sheet |
| Manual URL entry only | URL auto-captured from clipboard when opening the add dialog |
| No auto-submit | Bottom sheet auto-submits after 5-second countdown (cancels if user interacts) |
| No label selection when adding | Labels field with autocomplete from existing labels |

---

### Sync

| ReadeckApp 0.8.0 | MyDeck 0.9.0 |
|---|---|
| Single sync model | Split: bookmark list sync (always) + content sync (configurable) |
| No content sync modes | Content sync: Automatic, Manual (on demand or date range) |
| Download constraints present but not user-configurable | Wi-Fi-only and battery-saver constraint toggles in settings |
| Basic sync status | Sync status panel: bookmark counts + content download status |

---

### Other Changes

- **Rebranding:** Application ID, name, and assets changed from ReadeckApp to MyDeck.
- **Theming:** ReadeckApp offered Light, Dark, Sepia, and System Default as a single theme selector. MyDeck separates these into an app theme (Light, Dark, System Default) and a Sepia reading theme toggle that applies whenever the app is in light mode.
- **About screen:** A dedicated About screen accessible from the navigation drawer, including open-source library attributions.
- **Bookmark details dialog:** Full metadata (type, word count, reading time, language, authors, description) accessible from the reading view overflow menu.
- **Delete with Undo:** Deleting a bookmark shows a snackbar with an Undo action; the deletion is not committed until the snackbar is dismissed.
- **Material Design 3:** Full migration to Material 3 components and theming throughout the app.
- **Offline action queue:** State changes (favorite, archive, read, delete) made without network are queued and applied when connectivity is restored.
