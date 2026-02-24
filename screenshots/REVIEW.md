# Screenshot Review — MyDeck Android (2026-02-22)

Each entry describes what I see in the screenshot. Add your feedback/corrections below
each `FEEDBACK:` line. Leave blank if the description is correct.

FEEDBACK:
- overall note on the screenshots vs. the code: treat the screenshots and this feedback as gospel for the working draft of the documentation since the code for this branch is far behind that of the current `main` branch. 
- if I mention that a specific feature is not implemented, not working or will change from what is shown in the screenshot, the documentation should reflect what you see in the screenshot - when/if the code referenced is changed, I'll update the documentation to match the code
- if I do refer you to the code, it is for the `main` branch, not this branch

---

## Authentication Flow

### 205507 — Welcome / Sign-in screen
App launch. Shows the MyDeck logo, tagline "MyDeck is a read-later app for Readeck",
a single **Readeck URL** text field (pre-filled with `https://read.eatonfamily.net/api`),
and a **Connect** button. No password field — authentication is OAuth only.

FEEDBACK:
- when app initially opened, the URL field is prefilled with `https://`
- the value in screenshot is my home server showing what it would look like just before I press connect
- the `/api` suffix is appended automatically

---

### 205623 — Device Authorization screen
After tapping Connect, the two-step device auth screen appears:
1. "Visit this URL: https://read.eatonfamily.net/device" with **Copy URL** and
   **Open in Browser** buttons.
2. "Enter this code: TQQHPLXW" with a **Copy Code** button.

A spinner shows "Waiting for authorization... Expires in: 4:53" (countdown timer).

FEEDBACK:
- the countdown starts at 5 minutes

---

### 205716 — Browser: Readeck authorization page
User has tapped "Open in Browser". The Readeck web UI shows:
- "Signed in as: MyDeckTest"
- "Authorization required" with a list of permissions (Bookmarks R/W, Profile Read)
- **Authorize** and **Deny** buttons

FEEDBACK:
- the user presses authorize to continue

---

### 205804 — Browser: Authorization granted
After tapping Authorize, the browser shows: "Connect a device — Authorization granted.
Connecting device..." with a spinner and a "Reload this page" link. The app polls in
the background and navigates automatically once it detects authorization.

FEEDBACK:
- I have an outstanding issue I am researching where in some cases Readeck page doesn't move from the spinner/reload on to the one where the button shows "Return to Readeck" but that's what is supposed to happen from this page

---

## Bookmark List

### 205830 — My List — Mosaic layout
My List in Mosaic layout. Top bar: **☰** (drawer), "My List", and three action icons:
sort (↕), layout (⊞), filter (≡). Cards are large with thumbnail, title text overlaid
at the bottom, and four action buttons: ❤️ Favorite, Archive (box), 🌐 View Original,
🗑️ Delete. A **+** FAB is visible bottom-right.

FEEDBACK:
- this is the grid layout, not mosaic. it has thumbnails on the left and title text on the right
- below the title is a row with the site name and estimated reading time
- below that is a row of label chips (if any)
- if the labels do not all fit on the row, the chips can be scrolled horizontally
- below that is a row of action buttons (favorite, archive, view original, delete)
- the favorite and archive buttons are toggle buttons (heart and box) and are filled when set
- if the archive button is set or unset, the card is removed from the list (i.e., moved from My List to Archived or vice versa)
- the favorite button being set or unset doesn't change the card's visibility in the list
- if a label chip is selected, the list is filtered to show only bookmarks with that label (see other screenshots for examples)
- if the user clicks anywhere on the card except the action buttons, the app navigates to the reading view for that bookmark
- if the user long-clicks on the card, a browser-like pop-up menu appears for actions related to the link and, if the long-click was on the thumbnail, related to the image (see other screenshots for examples)
- if the bookmark is a video, the top right of the thumbnail shows a video icon
- if the bookmark is a picture, the top right of the thumbnail shows a picture icon
- for articles not yet read, the top right of the thumbnail has no icon
- for articles that are in-progress, the top right of the thumbnail shows a partial circle with the arc angle representing the percentage of the article read
- for articles that are read (completed), the top right shows a checkmark icon
- these visual elements and behaviors also apply to all list views (archive, search, label-filtered, etc)
- they also apply to compact and mosaic layouts (with exceptions noted below)

---

### 205847 — Sort menu
Sort dropdown open. Options: **Added** (selected, with ↓ arrow), Published, Title,
Site Name, Duration. Selecting a sort option that is already selected presumably
toggles ascending/descending (arrow indicates current direction).

FEEDBACK:
- yes, selecting currently selected option toggles ascending/descending
---

### 205904 — Layout picker menu
Layout dropdown open. Options: **Grid** (selected), Compact, Mosaic.

FEEDBACK:
- icon in top bar changes to show current layout
---

### 205911 — Compact layout
My List in Compact layout. Cards have a small thumbnail on the left, title + source
domain + reading time on the right, label chips below title, and four action buttons
(❤️, Archive, 🌐, 🗑️) in a row at the bottom.

FEEDBACK:
- unlike the grid layout, the compact layout does not show the progress indicator
- the thumbnail is the site favicon

---

### 205920 — Mosaic layout (full card detail visible)
Mosaic layout showing large cards. Each card has the full-bleed thumbnail, title
overlaid on the image, and action buttons below. A ✓ checkmark badge on the thumbnail
indicates a bookmark has been read (progress = completed).

FEEDBACK:
- unlike the other layouts, the mosaic layout does not show the labels

---

### 205935 — Filter bottom sheet (top half)
Filter bottom sheet overlays the list. Fields visible: **Search** (full-width), then
a 2-column grid of: Title, Author, Site, Label, From Date, To Date. Below that:
**Type** section with chip buttons: Article, Video, Picture. Progress section is
partially visible at the bottom edge.

FEEDBACK:
- the filter sheet initially opens at the top half

---

### 210047 — Filter bottom sheet (bottom half, scrolled down)
Continuation of filter sheet. **Progress** section: Unviewed, In progress, Completed
(chip buttons). Then toggle rows (N/A / Yes / No segmented controls):
- Is Favorite: N/A ✓
- Is Archived: No ✓
- Is Loaded: N/A ✓
- With Labels: N/A ✓
- With Errors: N/A ✓

**Reset** and **Search** buttons at the bottom.

FEEDBACK:
- several of the filter options are not implemented but I have a todo to fix them so leave documented as if implemented (exception to instructions at top of this document)

---

### 210402 — Search results
A search for "home assistant" has been applied. The top bar shows a chip **"Search:
home assistant ×"** (tap × to clear). The list below shows only matching bookmarks
in Compact layout. The FAB remains visible.

FEEDBACK:
- if the search result is empty, the page shows the search chip and a message "No bookmarks match your filters"

---

### 210432 — Label-filtered view
Viewing bookmarks filtered by the "tech" label. Top bar: ☰, label icon (⊳), "tech",
and **⋮** overflow menu (not a hamburger). List is in Compact layout showing only
tech-tagged bookmarks. FAB visible.

FEEDBACK:
- the sort and layout icons on top bar work as expected

---

## Adding Bookmarks

### 210707 — Android share sheet → Save to MyDeck
Sharing a URL from Chrome. The Android share sheet shows the **Save to MyDeck** option
with the app icon. Tapping it opens a bottom sheet with:
- **URL** field (pre-filled with the shared URL)
- **Title (Optional)** text field
- **Labels** — "Add label" field with a ❤️ icon
- Three buttons: **Archive**, **View**, **Add**

FEEDBACK:
- ignore the bar below the bottom sheet; that is from Android Studio emulator
- if an app shares in the form "text whether title or description", "URL" then both the URL and title are pre-filled
- the labels field when typed in shows an auto-complete list of matching existing lables below the field; I have a todo to improve the experience since it can be hidden behind the on-screen keyboard but leave described as if implemented
- the key point of this one is that unlike ReadeckApp if a user shares from another app, this dialog is displayed instead of the full app being opened
- when the intent-initiated add bookmark bottom sheet is first displayed, there is a timer and progress bar across the top that counts down 5 seconds after which the bookmark is added and the sheet closed
- if the user clicks anywhere on the sheet, the timer stops so they can take their time to update the URL, title, labels or favorite status and then use one of the buttons to add the bookmark
- clicking the "Add" button adds the bookmark and closes the sheet
- clicking the "View" button adds the bookmark and opens the app in reading view for it
- clicking the "Archive" button adds the bookmark and archives it then closes the sheet

---

### 210905 — Add Link bottom sheet (via FAB)
Tapping the **+** FAB opens a similar "Add Link" bottom sheet. Same fields as the
share sheet: URL, Title (Optional), Labels ("Add label" + ❤️), Archive / View / Add.
This sheet is titled "Add Link" rather than "Save to MyDeck".

FEEDBACK:
- as described

---

## Reading View (Article)

### 210937 — Reading view — article top
Reading view for an article. Top bar (5 buttons + overflow): **← Back**, ❤️ Favorite,
Archive, **TT** (Typography), 🔍 (Find in Article), **⋮** overflow. Article shows the
title ("How to Install Home Assistant on a Mini PC") with a **✏️ pencil** icon for
inline title editing. Article body and images render inline.

FEEDBACK:
- as described

---

### 210949 — Reading view — title editing
Tapping the pencil activates inline title editing. The title becomes an outlined text
field labeled "Edit title" with the current title pre-filled. A **✓** checkmark button
confirms the edit. The system keyboard appears.

FEEDBACK:
- as described

---

### 211009 — Typography bottom sheet (fonts 1–3 visible)
Tapping **TT** opens the Typography bottom sheet. Font row (horizontally scrollable):
**System Default** (selected), Noto Serif, Literata (more to the right). Other
controls below:
- **Font size**: − 100% +
- **Spacing**: Tight ✓ / Loose
- **Width**: Wide ✓ / Narrow
- **Justify text**: toggle (off)
- **Hyphenate words**: toggle (off)
- **Reset to defaults**: with reset icon

FEEDBACK:
- as described

---

### 211027 — Typography bottom sheet (fonts 4–6 visible, scrolled right)
Font row scrolled right shows the remaining fonts: (Source) Serif (partial),
**Noto Sans**, **JetBrains Mono**. All other controls are the same.
Total fonts: System Default, Noto Serif, Literata, Source Serif, Noto Sans,
JetBrains Mono (6 total).

FEEDBACK:
- as described

---

### 211047 — Find in Article
Tapping 🔍 opens the in-article search bar. The top bar is replaced with a search
input ("platform"), a **1/1** match counter, and **↑ ↓** navigation buttons.
Matching text in the article is highlighted in amber.

FEEDBACK:
- the "current match" is highlighted in amber, other matches are highlighted in yellow
- as described

---

### 211100 — Reading view — overflow menu (article)
Tapping **⋮** in the reading view shows the overflow menu:
- 🌐 View Original
- ↗ Open in Browser
- ↗ Share Link
- ✓ Unread (toggles read state)
- ℹ Bookmark Details
- 🗑 Delete

FEEDBACK:
- as described

---

### 211115 — View Original (in-app webview) — overflow menu
When "View Original" is open (in-app webview), the overflow menu shows different
first item: **View Article** (returns to reader view). The rest are the same:
Open in Browser, Share Link, Unread, Bookmark Details, Delete. The top bar in this
view has ← Back, ❤️, Archive, ⋮ only (no TT or 🔍).

FEEDBACK:
- as described

---

### 211134 — Share Link
Tapping "Share Link" opens the Android system share sheet showing the article's
original URL. Standard share targets include Quick Share, Gmail, Drive, Chrome, and
MyDeck ("Save to My...").

FEEDBACK:
- as described

---

### 211153 — Bookmark Details screen
Full-screen "Bookmark Details" (navigated to from overflow menu). Shows:
- Hero thumbnail
- Author (with avatar), Added date, Published date, Reading time, Author name, Word count
- Description text
- **Labels** section: existing label chips (with **×** to remove) + "Add label" text field
- **Debug Info** section with copy/share icons and raw debug text

FEEDBACK:
- the labels auto-complete also works here
- debug info only shows for debug builds, not production release builds

---

### 211213 — Reading view — article body scrolled
Article body mid-scroll. Top bar has scrolled away (auto-hide). Inline headings,
body text, and images render as expected.

FEEDBACK:
- as described but also if the user scrolls back towards the top of the article,
the top bar reappears

---

### 211241 — Reading view — article with image and list
Article section showing a numbered list and an inline screenshot image. Images
are rendered at full content width.

FEEDBACK:
- the point here is that on reaching the end of the article, the topbar reappears

---

### 211300 — My List after returning from reading
Back on My List after reading. The bookmark now shows a ✓ badge on its thumbnail,
indicating read/completed status.

FEEDBACK:
- as described
- also, note that a change to the bookmark reading status from the overflow menu in the reading view will be reflected back in the list view (i.e., if they completed it but set it to unread in the reading view, it will be unread in the list view)
- also, the reading progress is set based on the farthest scroll position in the reading view, not the position when they exit back to the list view (i.e., if they got to the end of the article but then scrolled back up to the top, the reading progress would still be 100%)

---

### 211418 — Reading view — end of article
End of the article. Shows "Final Thoughts" section, a "Watch on YouTube" section
with embedded image + link, and a "Featured Tech" section with affiliate links.
Links are tappable (rendered in blue).

FEEDBACK:
- as described

---

### 211431 — My List — read bookmark marked
Same as 211300 — confirming the ✓ badge persists on the card after reading.

FEEDBACK:
- as described

---

### 211505 — Delete snackbar
After deleting a bookmark, a **"Bookmark deleted"** snackbar appears at the bottom
of the screen with an **UNDO** button. The bookmark is immediately removed from the
list.

FEEDBACK:
- if the user clicks on the UNDO button, the bookmark is restored to the list
- the snackbar remains visible indefinitely and the actual delete operation does not occur (and snackbar disappear) until the user clicks anywhere except the Undo button

---

## Navigation Drawer

### 211520 — Navigation drawer
Drawer open from My List. Header: **"MyDeck"** title. Items with counts:
- ✓ **My List** (selected, highlighted) — 15
- Archive — 6
- Favorites — 4
- *(divider)*
- Articles — 18
- Videos — 2
- Pictures — 1
- *(divider)*
- Labels — 10
- *(divider)*
- Settings (gear icon)
- About (info icon)

No "All" item. Count badges are circular chips.

FEEDBACK:
- this is part of the Pocket-like interface. In Readeck, I personally find it inconsistent that the Unread filter shows "not archived" bookmarks - there can actually be a bookmark that is read in the Unread list and a bookmark that is unread in the Archive list. It's also a bit weird that the Unread filter (which does make sense as the default view) is the second item in Readeck. I get that All makes sense as the top item from a hierarchy perspective, but the UX of having the default be the second item shown is a bit odd for me. The My List vs. Archive approach in MyDeck (patterned on Pocket) is a bit different from Readeck, but I think it makes sense.
- the Favorites filter is different than My List vs. Archive - those two are mutually exclusive but it is not. However, they are all grouped together in Readeck so that's why they are grouped together here.

---

### 211533 — Archive view
Navigated to Archive from the drawer. Top bar: ☰, "Archive", sort/layout/filter icons.
Same card layout as My List (Compact shown). Archive button on cards appears filled/
active (indicating these are archived).

FEEDBACK:
- as described

---

### 211541 — Favorites view
Navigated to Favorites. Top bar: ☰, "Favorites". ❤️ is filled on all cards. Same
layout as other list views.

FEEDBACK:
- as described

---

### 211550 — Articles view
Navigated to Articles (type filter). Same list layout, filtered to article-type
bookmarks only.

FEEDBACK:
- as described

---

### 211606 — Videos view
Videos view showing 2 video bookmarks (Vimeo, YouTube). Layout icons in top bar are
the same.

FEEDBACK:
- as described

---

### 211613 — Videos view (duplicate)
Identical to 211606.

FEEDBACK:
- as described

---

## Reading View (Video)

### 211629 — Reading view — video bookmark
Opening a video bookmark ("Budget Paradise" from Vimeo). Top bar has **← Back**, ❤️,
Archive, **⋮** only — **no TT or 🔍** (those are article-only). Shows title with ✏️
edit pencil, description text, and an embedded Vimeo player with play button and
duration (13:54).

FEEDBACK:
- as described

---

## Reading View (Picture)

### 211645 — Pictures view
Pictures view showing 1 picture bookmark (Flickr photo).

FEEDBACK:
- this is the list view with Pictures filter selected from drawer

---

### 211649 — Pictures view (duplicate)
Identical to 211645.

FEEDBACK:
- as described

---

### 211704 — Reading view — picture bookmark
Opening a picture bookmark ("Lunar Eclipse over Mount Hood" from Flickr). Top bar:
← Back, ❤️, Archive, ⋮ only (no TT or 🔍). Shows title with ✏️, description text,
and the full-width image.

FEEDBACK:
- as described

---

## Labels

### 211746 — Labels bottom sheet (top portion)
Tapping "Labels" in the drawer opens a bottom sheet titled **"Select Label"** with
a "Search labels" field. Labels listed with count badges (partially visible):
animals (1), astronomy (1), bands (1), music (1), nature (1), science (1).

FEEDBACK:
- as described

---

### 211757 — Labels bottom sheet (full list)
Full labels list (scrolled to see all): animals (1), astronomy (1), bands (1),
music (1), nature (1), science (1), shutterbug (2), tech (4), think (1), travel (1).
10 labels total. Each has a label icon (⊳).

FEEDBACK:
- a long-press on a label opens popup menu for Edit label and Delete label; they each display the modal dialogs shown elsewhere in this document

---

### 211803 — Label-filtered view with overflow menu
After tapping "shutterbug", the list shows 2 bookmarks tagged "shutterbug". Top bar:
☰, ⊳, "shutterbug", ⋮. Tapping ⋮ shows: **Rename label**, **Delete label**.

FEEDBACK:
- as described

---

## Settings

### 211814 — Settings screen
Settings top-level. Top bar: **← Settings**. Four sections:
- **Account** — subtitle: "MyDeckTest" (the username)
- **Synchronization** — subtitle: "Synchronization Settings"
- **User Interface** — subtitle: "Appearance"
- **Logs** — subtitle: "View and Send Logfiles"

FEEDBACK:
- as described

---

### 211821 — Account screen
Account settings. Shows:
- **Readeck URL** field: `https://read.eatonfamily.net/api`
- **Login** button (to re-authenticate)
- *(divider)*
- Warning: "Signing out will clear all local data including saved bookmarks."
- **Sign Out** button (red text)

No "Allow unencrypted connections" toggle visible.

FEEDBACK:
- the allow unencrypted connections option is no longer valid in the app since the change to OAuth
- signing out returns the user to the welcome screen
- if the user just changes the server url and clicks login, then as long as the url is valid the app will display the authentication screen prompting them to go to the Readeck devices URL to authenticate

---

### 211829 — Synchronization Settings (top)
**Bookmark Sync** section:
- Description: "Your bookmark list is automatically kept up to date..."
- **Sync frequency**: "Every hour" (tappable link to change)
- "Next synchronization on Feb 22, 2026 10:18:27 PM"
- "Check for deleted bookmarks and sync all changes immediately"
- **Sync Bookmarks Now** button

**Content Sync** section:
- Content Sync Mode label (section header)
- ○ Automatic — "Content is downloaded during bookmark sync"
- ● Manual — "Content is downloaded only when you open a bookmark"
  - ● On demand (sub-option, selected)
  - ○ Date Range — "Download content for bookmarks added in a date range"
- **Constraints** label:
  - Only download on Wi-Fi: toggle ON
  - Allow download on battery saver: toggle OFF

FEEDBACK:
- although the app automatically syncs all bookmarks (not content) with the initial login and each time the app is opened, the point of the manual sync button is to retrieve from the server information on any bookmarks that were deleted via another client
- I didn't include a screenshot for it but if the user tries to sync content when a constraint is active (download only on wifi but they are trying to sync content when not on wifi, etc.) then a warning dialog will be displayed that gives them the option to override the constraint

---

### 211835 — Synchronization Settings (bottom / Sync Status)
Scrolled down to show **Sync Status** section:
- **Bookmarks**: Total 21, My List 15, Archived 6, Favorites 4
- **Content**: Downloaded 1, Available to download 16, Failed (retryable) 0,
  No article content 0

FEEDBACK:
- as described

---

### 211853 — Synchronization Settings — Date Range picker
When "Date Range" content sync mode is selected, a date range dropdown appears:
**Past day**, **Past week**, **Past month** (selected), **Past year**,
**Custom date range**. A "Past month ▼" button and a **Download** button appear.

FEEDBACK:
- as described

---

### 211914 — User Interface Settings (Sepia off)
**User Interface Settings** screen. **Theme** section: Light / Dark / **System** ✓
(3-way selector). Below: **Sepia reading theme** toggle (OFF) with description
"Use warm tinted colors for reading in light mode".

FEEDBACK:
- as described

---

### 211923 — User Interface Settings (Sepia on)
Same screen with Sepia toggle turned ON. The entire app UI shifts to a warm cream/
tan background. The "System" selector pill takes on a brown tint.

FEEDBACK:
- with Sepia enabled, the changes only apply when the app is in light mode (either System is selected and the system is in light mode, or Light is selected)

---

### 211935 — Logs screen
**Logs** screen. Top bar: ← Logs, calendar icon, share icon, refresh icon, delete icon.
Content shows raw debug log lines in monospace (timestamps, class names, SQL queries,
OAuth flow events, etc.).

FEEDBACK:
- as described

---

## In-Article Link / Image Context Menus

### 212001 — Reading view — long-press on a link
Long-pressing a hyperlink in the article shows a context menu:
- Copy Link Address
- Copy Link Text
- Share Link
- Open in Browser

FEEDBACK:
- right now,  the link in this case is the original content URL, not the Readeck server content URL; that is planned for a future update

---

### 212008 — Reading view — long-press on an image
Long-pressing an image shows a context menu:
- Copy Link Address
- Copy Link Text
- Share Link
- Copy Image
- Download Image
- Share Image
- Open in Browser

FEEDBACK:
- as described

---

## Label Rename / Delete

### 212040 — Label view — ⋮ overflow menu open
In the "science" label view, ⋮ overflow is open showing: **Rename label**,
**Delete label**.

FEEDBACK:
- as described

---

### 212047 — Rename label dialog
Tapping "Rename label" opens a modal dialog: **"Rename label"** title, text field
pre-filled with "science", **Cancel** and **Rename** buttons.

FEEDBACK:
- as described

---

### 212059 — Delete label confirmation dialog
Tapping "Delete label" opens a confirmation dialog: **"Delete label"** title,
body text "Are you sure you want to delete the label 'science'? It will be removed
from all bookmarks.", **Cancel** and **Delete** buttons.

FEEDBACK:
- as described

---

## Tablet / Large Screen Views

### 212133 — Tablet landscape — grid view
Landscape tablet (appears to be ~10" form factor). Navigation appears as a compact
**icon-only rail** on the left edge (no labels). Main content shows My List in a
3-column grid. Top bar has sort/layout/filter icons, FAB bottom-right.

FEEDBACK:
- this is not a tablet... this is a phone in landscape orientation
- in this orientation, the navigation drawer is replaced by the icon rail on the left edge
- also, the layout of the grid view is different in this orientation (matches the tablet format)
- the other views (compact, mosaic) work and look the same as mobile portrait orientation

---

### 212146 — Tablet portrait — grid view (4 columns)
Portrait tablet. Navigation compact icon rail on the left. My List in a **4-column
grid**. Cards show same content as phone (thumbnail, title, source, time, labels,
action buttons).

FEEDBACK:
- this **is** a tablet
- it uses the rail in place of the navigation drawer in portrait orientation
- as noted above, the format of cards in grid view are different than mobile portrait orientation, with the thumbnail above instead of to the left of the title

---

### 212201 — Tablet landscape — compact list view (wide rail)
Landscape tablet showing the **Compact** list layout. The left navigation rail
appears slightly wider and shows icon + label text (My List, Archive, Favorites,
Articles, Videos, Pictures, Labels, Settings, About). The list occupies the
remaining right content area.

FEEDBACK:
- no, this is portrait orientation on tablet
- the rail is **not** wider than on grid view

### 212212 — Tablet portrait — grid/mosaic view
Portrait tablet, My List in what appears to be **Mosaic** layout with 4 columns.
Cards show just the thumbnail and title (no action buttons visible — they may appear
on hover/tap).

FEEDBACK:
- this is mosaic view on tablet
- the action icons **do** appear in this screenshot
- on Readeck there is nothing but the image for mosaic view and the title and icons show on hover but as hover isn't an option on Android and tap opens the bookmark in reading view, the title and icons are always visible

---

### 212242 — Tablet — reading view
Tablet reading view for an article. Full-width article content. Top bar: ← Back, ❤️,
Archive, TT, 🔍, ⋮. Content is wider than phone, but otherwise the same layout.

FEEDBACK:
- as described

---

### 212309 — Tablet — drawer open + grid view
Tablet with the full navigation drawer open as a persistent panel on the left. Shows
"MyDeck" title and the full item list (My List 15, Archive 6, Favorites 4, Articles 18,
Videos 2, Pictures 1, Labels 10, Settings, About). Main content (My List, 4-column
grid) is visible to the right of the drawer simultaneously.

FEEDBACK:
- tablet in landscape orientation
- the drawer for this form factor and orientation is persistent (no rail)
- clicking on any navigation item in the drawer replaces the right content area with the corresponding view
---

## Summary of Key Observations (for guide updates)

1. **Account screen**: "Allow unencrypted connections" option has been removed. OAuth
   device auth requires HTTPS, so the option is no longer applicable. ✅ CONFIRMED

2. **Sync settings**: The current two-system approach (Bookmark Sync + Content Sync
   with separate controls) is a temporary workaround for a large-bookmark-set issue.
   Once Readeck 0.22 ships (with the needed fix), sync will revert to the api/sync
   endpoint and many of these controls may go away. Document current behavior as-is
   for now. ✅ CONFIRMED

3. **Video/Picture reading views**: Top bar currently shows ← Back, ❤️, Archive, ⋮
   only. Whether TT and 🔍 should appear for video/picture bookmarks (which can also
   have text content) is under review. **Do not update the guide on this point yet.**

4. **Article ↔ Original switching**: Document clearly that both directions work.
   Special case: if Readeck could not extract article content at save time, MyDeck
   **automatically opens in View Original** instead of showing a "no content" message.
   This means the overflow menu first item will be "View Article" rather than
   "View Original" in that scenario. ✅ CONFIRMED

5. **Tablet/adaptive layout**: Document as a subsection within the Bookmark List
   section. Behavior:
   - **Phone portrait**: drawer slides in from the left (standard behavior)
   - **Phone landscape / Tablet portrait**: persistent navigation **rail** (icon-only)
     replaces the drawer
   - **Tablet landscape**: persistent full **drawer** alongside the list view
   - **Tablet reading view**: same layout as mobile (no changes)
   ✅ CONFIRMED

6. **Bookmark Details — Debug Info section**: Only present in debug/dev builds.
   Production builds will not show it. **Do not document.** ✅ CONFIRMED

7. **Long-press context menus** in reader: Links show 4 options; Images show 7 options.
   (Need to decide whether to document these.)

8. **Rename label**: Opens a modal dialog (not inline editing).

FEEDBACK:
- note that the popup dialog for renaming a label or deleting a label is a popup instead of inline editing or a snackbar with undo is because these changes affect all bookmarks in the label, not just the one being edited. That broader impact on the data makes having a more "in your face" confirmation of the change more appropriate.

9. **Sepia theme**: Visible toggle in UI Settings — "Use warm tinted colors for reading
   in light mode". Warm cream background applies app-wide when enabled.

10. **Labels bottom sheet title**: "Select Label" (not just "Labels").

FEEDBACK:
- note the "Search labels" field in the bottom sheet for labels; useful when you have a lot of labels and want to find a specific one

11. **Add Link via share sheet**: Sheet titled "Save to MyDeck"; via FAB it's "Add Link".
    Both have URL, Title (Optional), Labels, Archive / View / Add buttons.
