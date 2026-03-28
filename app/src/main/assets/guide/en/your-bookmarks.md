# Your Bookmarks

Bookmarks are the web pages (articles, videos and pictures) you've saved to your Readeck server. MyDeck syncs them to your device so you can read them anytime, even offline.

## Navigating Your Bookmarks

Tap the **menu icon** (☰) in the top-left corner to open the navigation drawer. The drawer lets you browse a specific subset of your bookmarks:

- **My List** — bookmarks you haven't archived yet (your default reading queue)
- **Archive** — bookmarks you've moved to the archive
- **Favorites** — bookmarks you've marked as a favorite (these can be in My List or the Archive)
- **Articles** — article-type bookmarks only
- **Videos** — video-type bookmarks only
- **Pictures** — picture-type bookmarks only
- **Labels** — browse bookmarks by label; tap a label to see all bookmarks with that label

Each item shows a count of how many bookmarks it contains.
Tap outside the drawer or swipe it closed to dismiss it without changing sections.

## Bookmark Cards

Each item in the list is a bookmark card. Every card shows the bookmark's **title**, **site name**, estimated **reading time**, and any **labels** assigned to it. If a bookmark has more labels than fit on one line, you can scroll the label row horizontally.

Tap anywhere on a card (except the action buttons) to open the bookmark.

### Grid Layout

The default layout. Each card has a **thumbnail** on the left and the title, source, and other details on the right. A **reading progress indicator** appears in the top-right corner of the thumbnail, described in the Reading Progress section below.

### Compact Layout

A denser view that uses the site's **favicon** instead of a full thumbnail. A fixed status rail on the left shows the favicon at the top, **reading progress** in the middle, and **download status** near the bottom (see below). Useful when you want to scan a long list quickly.

### Mosaic Layout

Each card is a large tile with the thumbnail filling the entire card and the title overlaid at the bottom. Labels are not shown in mosaic layout.

### Switching Layouts

Tap the **layout icon** in the top bar to choose between Grid, Compact, and Mosaic. The icon updates to reflect your current choice.

## Reading Progress

All three layouts show the bookmark's reading status:

- **No indicator** — not yet opened
- **Partial circle** — in progress; the arc angle represents how far through the article you've read
- **Checkmark (✓)** — read (completed)

In Grid and Mosaic layouts, the reading status indicator appears in the top-right corner of the thumbnail. In Compact layout, it appears in the middle slot of the left status rail.

Progress is tracked based on how far you scroll in the article — it reflects the farthest point you've reached, not your position when you close the bookmark.

A **video icon** or **picture icon** appears in the top-left corner for video and picture bookmarks in Grid and Mosaic layouts.

## Download Status

All three layouts show whether a bookmark's content has been downloaded for offline reading:

- **No indicator** — content not yet downloaded
- **Cloud outline** — text downloaded (without images)
- **Cloud with checkmark** — fully downloaded (text and images)

In Grid layout, the download icon appears near the lower-left edge of the thumbnail. In Mosaic layout, it appears in the lower-left area of the thumbnail above the title overlay. In Compact layout, it appears in the lower slot of the left status rail.

## Card Actions

Each card has four action buttons:

- **❤️ Favorite** — toggle the favorite status; filled when set
- **Archive** — move the bookmark to the archive (or back to My List if already archived); the card is removed from the current view when toggled
- **🌐 View web page** — open the web page in an in-app viewer
- **🗑️ Delete** — delete the bookmark; the card is greyed out and a **"Deleting bookmark \"Title...\""** bar appears at the bottom with an **Undo** option and a short title snippet so you can tell which queued delete you are restoring. Tap **Undo** to restore the bookmark, or tap anywhere else (including the greyed-out card itself) to confirm deletion

See [Organizing](./organizing.md) for more on how favorites, archive, labels, and deletion work across your whole collection.

## Long-press Context Menu

Long-pressing on a bookmark card opens a context menu dialog. The dialog header shows the bookmark URL or image source — tap the header to expand truncated text, tap again to collapse.

**Body long-press** (anywhere except the thumbnail) opens a link menu:
- Copy link address
- Copy link text
- Download link
- Share link
- Open in browser
- Remove downloaded content (only shown when the bookmark has downloaded content)

**Thumbnail long-press** opens an image menu:
- Copy image
- Download image
- Share image

## Adding Bookmarks

### From within MyDeck

Tap the **+** button at the bottom-right of the list to open the **Add Link** sheet. Enter the URL of the page you want to save, and optionally a title, labels, and mark it as a favorite (❤️), then tap **Add**.

For labels, use the **Add labels** / **Edit labels** row in the Labels section. This opens a picker where you can select multiple labels (and create a new label from search text). When you pick a label from search results, the search clears, and your selected labels stay pinned below the search field while you continue choosing more. Tap **Done** to apply picker changes, or tap **Back** to cancel picker changes.

- **Add** — saves the bookmark and closes the sheet
- **View** — saves the bookmark and immediately opens it to read
- **Archive** — saves the bookmark directly to the archive

### From another app

You can save any link to MyDeck using Android's share sheet. In a browser or another app, tap **Share** and choose **Save to MyDeck**. A sheet appears with the URL (and title, if the app shared one) pre-filled. You can add or edit the title, pick labels, mark it as a favorite (❤️), and choose **Add**, **View**, or **Archive**.

When opened via the share sheet, a 5-second countdown starts and the bookmark is added automatically when it reaches zero. Tap anywhere on the sheet to stop the countdown and take your time.

## Searching and Filtering

Tap the **filter icon** (≡) in the top bar to open the filter sheet. The sheet opens showing the top half of the options. Scroll down within the sheet to see all options.

Active filters are shown as chips below the top bar. Tap the **×** on a chip to clear that filter.

When no bookmarks match the current filters, the list shows a "No bookmarks match your filters" message.

**Text fields:**
- **Search** — full-text search across title, article text, author, site name, and labels
- **Title** — search the title only
- **Author** — search the author field only
- **Site** — search the site name and domain
- **Label** — opens a label picker to filter to bookmarks with a specific label

**Date range:**
- **From Date / To Date** — filter by when the bookmark was added

**Type** (choose one or more):
- Article, Video, Picture

**Progress** (choose one or more):
- Unviewed, In progress, Completed

**Toggle filters** (N/A / Yes / No):
- Is favorite, Is archived, Is downloaded, With labels, With errors

**With errors** follows the error status reported by your Readeck server. It matches bookmarks that Readeck marked as errored or bookmarks where Readeck stored extraction or processing errors. It does not simply mean that MyDeck has not downloaded local reader content for the bookmark.

When your current filters no longer match the default scope of the list you started from, the top bar title changes to **Filtered List**. A chip bar appears below the title showing every active deviation from the starting view. Each chip has an ✕ to dismiss that specific filter.

If you broaden the starting view's core constraint — for example, setting *Is archived* to N/A while in My List, or deselecting all types while in Articles — a synthetic chip appears (e.g. "Is archived: N/A" or "Type: Any"). Dismissing that chip restores the starting view's constraint for that dimension while keeping any other filters intact.

To return to a standard view you can:
- dismiss all filter chips one by one,
- tap **Reset** in the filter sheet to restore the full starting-view defaults, or
- select a predefined view (My List, Archive, etc.) from the navigation drawer.

Tap **Search** to apply, or **Reset** to clear all filters.

## Sorting

Tap the **sort icon** (↕) in the top bar to change the sort order. Options are:

- **Added** — date the bookmark was saved to Readeck
- **Published** — date the original article was published
- **Title** — alphabetical by title
- **Site Name** — alphabetical by site
- **Duration** — by estimated reading time

An arrow on the selected option shows the current direction. Tap the same option again to toggle between ascending and descending.

## Refreshing

Pull down on the list to sync with your Readeck server. This checks for new and updated bookmarks. To also check for bookmarks deleted on the server, use **Sync Bookmarks Now** in [Settings](./settings.md).

## Tablet and Landscape Layout

MyDeck adapts its navigation to the available screen space:

- **Phone portrait** — the navigation drawer slides in from the left when you tap ☰.
- **Phone landscape / Tablet portrait** — the drawer is replaced by a persistent **navigation rail** on the left edge showing icons for each section. Tap ☰ to open the full drawer if needed.
- **Tablet landscape** — the full navigation drawer is always visible alongside the bookmark list.

The bookmark list also adapts in Grid layout: on wider screens cards are arranged in multiple columns with the thumbnail above the title rather than to the left of it.
