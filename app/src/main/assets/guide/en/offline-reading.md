# Offline Reading

Offline reading lets you read your bookmarks without an internet connection. It is optional and off by default. When you turn it on, MyDeck downloads and keeps eligible bookmarks available so their extracted content is ready even when you're offline.

This page explains how offline content works and how it shows up on your bookmarks. The controls that decide *how much* to keep and *when* to download live in **Settings → Synchronization → Offline Reading** (see [Settings](./settings.md)).

## Automatic and Pinned Content

MyDeck keeps two kinds of offline content:

- **Automatic** — content MyDeck manages for you within your offline limits. This includes any bookmark you simply open while offline reading is on. Automatic content is trimmed as newer bookmarks arrive or limits are reached.
- **Pinned** — content you deliberately keep. Pinned bookmarks are protected: routine offline maintenance won't remove them. Only unpinning, **Clear All Offline Content**, turning offline reading off, or exceeding the maximum storage cap can remove pinned content.

### Pinning a Bookmark

- **While reading** — tap **⋮ → Pin offline** (it reads **Unpin offline** once pinned). This option appears only while offline reading is enabled.
- **Several at once** — tap **⋮ → Select bookmarks** in a list, choose the cards you want, then open the **⋮ overflow** and tap **Pin offline**. MyDeck downloads anything not already stored, under your sync settings (Wi‑Fi only, battery saver), and you stay in selection mode so you can keep working. A snackbar summarizes the result, such as "8 pinned offline" or "8 pinned offline · 2 have no offline content" when some selected items (for example, link-only bookmarks or videos with no article) have nothing to store.

Unpinning hands a bookmark back to automatic management — it doesn't delete the content, it's just no longer protected.

## Download Status on Cards

All three card layouts show whether a bookmark's content has been downloaded for offline reading:

- **No indicator** — content not yet downloaded
- **Outlined download icon** — text has been cached on demand for reading
- **Filled download icon** — the bookmark is fully available offline, downloaded automatically by your offline settings
- **Filled download icon in a highlight colour** — the bookmark is pinned: fully available offline because you kept it yourself, and protected from routine offline maintenance

In **Grid** and **Compact** layouts, this icon appears on the site-info row, next to the site name and reading time. In **Mosaic** layout, it appears on the title row near the top-right area of the card.

The same slot also surfaces two status badges, which take priority over the download icon:

- **Error icon** — your Readeck server reported a problem with this bookmark (for example, it couldn't extract the content). These are the same bookmarks matched by the **With errors** filter; open the bookmark to see the details.
- **Circle-slash icon** — there is no readable content to download; opening the card takes you to the original page.

## Turning Offline Reading Off

If you turn offline reading off, MyDeck removes all stored offline content — both automatic and pinned — immediately, but keeps the lightweight text it cached when you opened bookmarks while browsing. See [Settings](./settings.md) for the storage limits, Wi‑Fi/battery rules, storage stats, and **Clear All Offline Content**.
