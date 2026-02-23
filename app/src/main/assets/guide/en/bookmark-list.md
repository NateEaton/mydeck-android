# Bookmarks

Bookmarks are the web pages, articles, and videos you've saved to your Readeck server. MyDeck syncs them to your device so you can read them anytime, even offline.

## Bookmark Types

Readeck recognizes three types of web content:

### Article

A page from which the text content was extracted and stored. MyDeck renders it as a clean, readable article.

### Picture

A page identified as an image (for example, a link to Unsplash). MyDeck displays the stored image.

### Video

A page identified as a video (for example, YouTube or Vimeo). MyDeck renders a video player. Note that videos stream from their original servers and require an internet connection.

## Adding Bookmarks

### From within MyDeck

Tap the **+** button at the bottom right of the bookmark list to open the **Add Bookmark** sheet. Enter the URL of the page you want to save, and optionally a title, then tap **Create**.

![New Bookmark form](./img/bookmark-new.webp)

After a moment the bookmark will appear in your list. Readeck processes it on the server and MyDeck downloads the content in the background.

### From another app

You can save any link directly to MyDeck using Android's share sheet. In a browser or another app, tap **Share** and choose **MyDeck** from the list. The link is sent to your Readeck server immediately.

## Navigating Your Bookmarks

Tap the **menu icon** (☰) in the top-left corner to open the navigation drawer. The drawer lets you filter your list to a specific subset of your bookmarks:

- **My List** — unread bookmarks (not archived)
- **Archive** — bookmarks you've moved to the archive
- **Favorites** — bookmarks you've marked as a favorite
- **Articles** — article-type bookmarks only
- **Videos** — video-type bookmarks only
- **Pictures** — picture-type bookmarks only
- **[Labels](./labels.md)** — browse bookmarks by label

![Bookmark list sidebar](./img/bookmark-sidebar.webp)

## Searching

Tap the **search icon** in the top bar to search across your bookmarks. The search runs against titles, article text, authors, site names, and labels.

## Filter Bookmarks {#filters}

For more specific filtering, tap the **filter icon** to open the filter sheet.

![Bookmark list filters](./img/bookmark-filters.webp)

You can combine the following filters:

- **Search** — search across text, title, authors, site name, and labels
- **Title** — search in the title only
- **Author** — search in the author list only
- **Site** — search in the site title and domain name
- **Label** — filter by one or more labels
- **Is Favorite**, **Is Archived**, **Type** — restrict to bookmarks matching these properties
- **From date**, **To date** — restrict to bookmarks saved within a date range

Tap **Apply** to run the filtered search.

### Search query syntax

Text search fields support the following operators:

- `startled cat` — finds bookmarks containing both **startled** and **cat**
- `"startled cat"` — finds the exact phrase **startled cat**
- `cat*` — finds words starting with **cat** (cat, catnip, caterpillar, etc.)
- `-startled cat` — finds **cat** but excludes **startled**

## Bookmark Cards

Each item in the list is a bookmark card.

![Bookmark List](./img/bookmark-list.webp)
Grid view

A card shows the bookmark's **title**, **site name**, estimated **reading time**, and any **labels** assigned to it. Tap the card to open the bookmark.

Each card has four quick-action buttons:

- **Favorite** (❤️) — toggle the favorite status
- **Archive** — move the bookmark to the archive (or remove it from there)
- **Open in Browser** (🌐) — open the original page in your default browser
- **Delete** (🗑️) — mark the bookmark for deletion; a brief undo option appears immediately after

## List Layout

You can switch between a **grid view** and a **compact list** using the layout toggle in the top bar. The compact view shows less imagery and fits more bookmarks on screen at once.

![Bookmark Compact List](./img/bookmark-list-compact.webp)
Compact list view

## Refreshing

Pull down on the list to sync with your Readeck server, or tap the **refresh icon** in the top bar. This checks for new and updated bookmarks. Deleted bookmarks are reconciled during background sync (see [Settings](./settings.md) for sync options).
