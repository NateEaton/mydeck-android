# Bookmarks

Bookmarks are where you save the web content you like.

## Create a new Bookmark

Found a web page you like? Great! Copy its link into the text field named **new bookmark** on the [Bookmark List](readeck-instance://bookmarks).

![New Bookmark form](./img/bookmark-new.webp)

After a few seconds, your bookmark will be ready. You can then open it to read or watch its content, add labels, highlight text or export an ebook. For more information, please read the [Bookmark View](./bookmark.md) section.

## Bookmark type

Readeck recognizes 3 different types of web content:

### Article

An article is a page from which the text content was extracted. It renders as a readable version of its content.

### Picture

A picture is a page that was recognized as a picture container (ie. a link to Unsplash). It renders the stored picture.

### Video

A video is a page that was identified as a video container (ie. a link to Youtube or Vimeo). It renders a video player. Please note that videos are played from their respective remote servers.


## Bookmark List

The [bookmark list](readeck-instance://bookmarks) is where you'll find all your saved bookmarks.

### Navigation

On the sidebar, you'll find a search field and links that will take you to filtered bookmark lists.

![Bookmark list sidebar](./img/bookmark-sidebar.webp)

- **Search** \
  Enter any search term (title, content, website...)
- **All** \
  All your bookmarks.
- **Unread** \
  The bookmarks that are not in the archive.
- **Archive** \
  The bookmarks you marked as archived.
- **Favorites** \
  The bookmarks you marked as favorite.


Once you start saving pages, you'll see the following additional links:

- **Articles** \
  Your article bookmarks
- **Videos** \
  Your video bookmarks
- **Pictures** \
  Your picture bookmarks

Finally, you'll see 3 more sections that take you to bookmark related pages:

- **[Labels](./labels.md)** \
  All your bookmark labels
- **Highlights** \
  All the highlights created on your bookmarks
- **[Collections](./collections.md)** \
  The list of all your collections

### Bookmark Cards

Each item on a list is called a Bookmark Card.

![Bookmark List](./img/bookmark-list.webp)
Grid Bookmark List

A card shows:

- the **title** on which you can click to watch or read the bookmark,
- the **site name**,
- the estimated **reading time**,
- the **label list**,
- **action buttons**

The action buttons perform the following:

- **Favorite** \
  This toggles the favorite status of the bookmark.
- **Archive** \
  This moves the bookmark to the archives (or removes it from there).
- **Delete** \
  This marks the bookmark for deletion (it can be canceled during a few seconds).

### Compact List

If you find the bookmark grid view too busy, you can switch to a more compact list with less images. Click on the button next to the title to switch from the grid view to the compact view.

![Bookmark Compact List](./img/bookmark-list-compact.webp)
Compact Bookmark List

## Filter Bookmarks {#filters}

On the bookmark list, you can filter your results based on one or several criteria. Click on the button "Filter list" next to the page title to open the filtering form.

![Bookmark list filters](./img/bookmark-filters.webp)
The filter form

Enter any criteria and click on **Search**.

### Available filters

You can combine the following filters:

- **Search**\
  Search in the bookmark's text, its title, its authors, its site name and domain and the labels.
- **Title**\
  Search in the title only.
- **Author**\
  Search in the author list only.
- **Site**\
  Search in the site title and the site domain name.
- **Label**\
  Search for specific labels.
- **Is Favorite**, **Is Archived**, **Type**\
  This filters let you restrict your search to any of these criteria.
- **From date**, **To date**\
  This last filters let you restrict from when and to when the bookmark was saved. For example, this lets you retrieve the bookmark list saved during the past 4 weeks but not after the last week.

### Search query

The **Search**, **Title**, **Author**, **Site** and **Label** fields understand search criteria the same way:

- `startled cat` will find the content with the words **startled** and **cat**
- `"startled cat"` will find the content with the exact words **startled cat** together.
- `cat*` will find the content with the words starting with **cat** (cat, catnip and caterpillar would be a match).
- `-startled cat` will find the content with the word **cat** but NOT the word **startled**.


After you performed a search, you can save it into a new [collection](./collections.md) to make it permanent.

## Export and Import Bookmarks

![Menu](./img/bookmark-list-menu.webp)
Bookmark list menu

### Export bookmarks

The menu button next to the filters button let you download an EPUB file of the current list of bookmarks. It exports one e-book containing all the articles organized in chapters.

### Import bookmarks

In the same menu, you'll find a [Import bookmarks](readeck-instance://bookmarks/import) link. It will take you to an import wizard that lets you import your existing bookmarks from various sources.
