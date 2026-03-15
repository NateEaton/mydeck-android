# Reading

Tap any bookmark card to open it. MyDeck displays the bookmark in a view suited to its type.

## Reading View

For bookmarks with text content, MyDeck shows the extracted text in a clean, distraction-free reading layout. The bookmark title appears at the top, and if the bookmark has a description it is shown directly underneath in italic text. Title changes are managed from **⋮ → Details** rather than inline in the reader header.

Tap any image in the content to open it in the **Image Lightbox** (see below). Long-press links or images to access **Long-press Context Menus** (see below).

## Bookmark Types

MyDeck supports three types of bookmarks, each with unique features:

### Articles
Articles display extracted text content in a clean, distraction-free reading layout. If the bookmark already has highlights from Readeck, those highlights appear inline in their saved colors while you read. Articles support full typography controls and text search, and may include images that can be opened in the **Image Lightbox** and links that can be long-pressed to access **Long-press Context Menus**.

### Videos  
For video bookmarks (YouTube, Vimeo, etc.), MyDeck shows an embedded video player at the top, followed by any text content (description or transcript). Videos stream from their original servers and require an internet connection. Use the player's fullscreen control to expand the video when the provider supports it. While fullscreen video is open, temporary overlay controls appear for **Close** and **Rotate**; touch the screen to bring them back after they fade. Tap **Rotate** in the top-right corner to switch the fullscreen player between portrait and landscape without leaving fullscreen mode. If the video includes text content, you can long-press links to access **Long-press Context Menus**.

### Pictures
For picture bookmarks, MyDeck shows the stored image at the top at full content width, followed by any text content. Tap the image to open it in the **Image Lightbox** where you can zoom and pan. Pictures may also include text content with links that support **Long-press Context Menus**.

### View Formats

MyDeck supports two view formats for bookmarks: **extracted content view** (Article, Video, and Picture) and **web view**. The extracted content view displays content extracted by Readeck in a clean, distraction-free reading layout, while **web view** displays the bookmark in its original format in an in-app web viewer. The extracted content view is available offline (once the content is downloaded — see **Settings → Synchronization → Content Sync**), while **web view** requires an internet connection.

- **From Article, Video, or Picture view:** tap **⋮** → **View web page**
- **From web view:** tap **⋮** → **View [bookmark type]** to return to the extracted content view

> **No article content:** If Readeck was unable to extract article content when the bookmark was saved, MyDeck automatically opens the bookmark in **web view** instead of showing an empty reading view. In that case, the overflow menu hides the **View [bookmark type]** action because extracted content is unavailable.


### Top Bar

The top bar provides quick access to essential navigation and reading actions:

- **← Back** — return to the bookmark list
- **TT Typography** — open the reading settings sheet
- **🔍 Find in page** — search for text within the content
- **⋮ More** — overflow menu with additional actions

As you scroll down to read, the top bar hides to give you more screen space. It reappears when you scroll back toward the top.

If **Fullscreen while reading** is enabled in **Settings → User Interface**, the reader hides the system bars and top bar after a short delay. Swipe from the edge or tap near the top edge to reveal them temporarily.

The screen stays on while you read, so you won't lose your place to the device's screen timeout. This behaviour can be turned off in **Settings → User Interface**.

### Typography Settings

Tap **TT** in the top bar to open the reading settings sheet.

- **Text** tab — contains the controls for Font, Font size, and Spacing
- **Layout** tab — contains the controls for Width, Justify text, and Hyphenate words
- **Theme** tab — contains the controls for Paper, Sepia, Dark, and Black reading themes
- **Font** — scroll the font row to choose from: System Default, Noto Serif, Literata, Source Serif, Noto Sans, or JetBrains Mono
- **Font size** — tap **−** or **+** to adjust in 5% steps (from 80% to 170%, shown as a percentage of the default)
- **Spacing** — tap **−** or **+** to adjust in 5% steps (from 80% to 125%, shown as a percentage of the default)
- **Width** — choose the Narrow, Medium, or Wide layout button (75%, 85%, or 95% of the reader width)
- **Justify text** — toggle on or off
- **Hyphenate words** — toggle on or off
- **Reading theme** — choose Paper, Sepia, Dark, or Black; if you pick a light theme while reading in dark mode, or a dark theme while reading in light mode, MyDeck switches the app theme to match and remembers your last light and dark reading themes separately
- **Reset theme** — use the footer action to return the app theme to **System** and use the saved light or dark reading theme that matches the current system theme
- **Reset text** / **Reset layout** — use the footer action to reset only the controls in the current tab

These settings are saved automatically and apply to every Article, Video, and Picture bookmark you read.

### Finding Text

Tap **🔍** to open the in-content search bar. Type to search; matching text in the content is highlighted. If there are multiple matches, the current one is highlighted in amber and others in yellow. Use the **↑** and **↓** arrows to move between matches. The counter shows your current position (e.g., "2/5").

### Highlights

Article bookmarks support Readeck highlights directly in the reading view. To create a highlight, select text in the article and tap **Highlight** in the Android text selection menu, then choose a color. If your selection overlaps one or more existing highlights, **Save** changes the color of those highlights and **Delete** removes them immediately. To edit or delete an existing highlight directly, tap the highlighted text in the article. If an existing highlight has a note, the highlight sheet shows that note in a read-only field, and tapping it explains that note editing is not supported yet. You can also open **⋮ → Highlights** to browse saved highlights and jump to any one in the article. When you reopen a downloaded article while online, MyDeck checks whether the saved highlight set changed on the server and refreshes the article if needed.

## Overflow Menu

Tap **⋮** in the top bar for additional actions:

- **Highlights** — for article bookmarks in reading view, open a list of saved highlights and tap one to jump to it in the article
- **Add to favorites / Remove favorite** — toggle the favorite status
- **Archive / Unarchive** — move the bookmark to the archive (or back to My List)
- **Mark as read / Mark as unread** — toggle whether the bookmark has been read; this is reflected back in the bookmark list
- **View web page / View [bookmark type]** — switch between the reading view and the web page in an in-app viewer (see above)
- **Share link** — open the Android share sheet to share the bookmark using the format selected in **Settings → User Interface**
- **Details** — open the Bookmark Details screen (see below)
- **Delete** — delete the bookmark; you are returned to the list and a **"Bookmark deleted"** bar appears with an **Undo** option

The menu includes visual dividers to separate related actions and destructive actions.

## Links in Content

Tapping a link within bookmark content (articles, pictures with descriptions, or videos with text) opens the associated web page in your device's default browser.

In the **Sepia** appearance, article links also use a subtle underline so they are easier to distinguish from body text while reading.

## Long-press Context Menus

Long-pressing on links or images within bookmark content opens a context menu dialog with quick actions. The dialog header shows the URL or image source — tap the header to expand truncated text, tap again to collapse.

**Link long-press:**
- Copy link address
- Copy link text
- Download link
- Share link
- Open in browser

**Image long-press:**
- Copy image
- Download image
- Share image

## Image Lightbox

Tap any image in an article or picture bookmark to open it in a fullscreen lightbox viewer. In the lightbox, you can:

- **Pinch to zoom** — zoom in and out on the image
- **Double-tap** — toggle between 1× and 2× zoom
- **Pan** — drag to move around a zoomed image
- **Tap** — show or hide the top bar and thumbnail strip
- **Swipe left/right** — navigate between images (when the content contains multiple images)
- **Close (×)** or **Back** — close the lightbox and return to reading

When the content contains multiple images, a thumbnail strip appears at the bottom of the lightbox. Tap any thumbnail to jump directly to that image.

If the current image was originally a hyperlink (linking to another page or site), an **external link icon** (↗) appears in the top-right corner of the lightbox. Tap it to open the linked URL in the browser.

## Bookmark Details

**Details** (opened from the overflow menu) shows the bookmark's saved metadata:

- Thumbnail
- Title row with a pencil icon — tap the title or pencil to open the full-screen metadata editor
- Site name, date added, date published, author, site root URL, reading time, and word count
- **Labels** — add or remove labels for this bookmark; see [Organizing](./organizing.md) for the full guide to working with labels
- **Refresh content** — re-download the extracted reader content for this bookmark
- Debug info in debug builds

The metadata editor includes title, description, site name, authors, published date, language, and text direction. It opens full screen with an **X** at the top and **Save** at the bottom right. Closing the editor returns you to Details, the published date field uses the Android date picker, and the keyboard **Next** action moves between the standard fields while **Authors** keeps normal multi-line entry.
