# Settings

Open the navigation drawer and tap **Settings** to access the app's settings.

## Account

The **Account** option in the drawer shows your username. Tapping it opens the **Account** screen, which shows the Readeck server your app is connected to.

- **Readeck URL** — the API address of your Readeck server. To switch to a different server, update this field and tap **Login** to re-authenticate.
- **Sign Out** — disconnects MyDeck from your Readeck account and removes all locally stored bookmarks from the device. You'll be returned to the welcome screen.

> **Note:** Signing out removes all local bookmark data. Everything is stored on the server so the data will still be available the next time you sign into the same server.

## Server URL Security

The standard MyDeck app accepts HTTPS Readeck server URLs only; a separate HTTP-enabled APK exists for trusted private-network setups that cannot use HTTPS. See [Getting Started](./getting-started.md) for the full explanation, including what happens if an older install was already signed in over `http://`.

## Synchronization

The **Synchronization Settings** screen controls how MyDeck keeps your bookmarks and their content in sync with the Readeck server.

### Bookmark Sync

The top of the screen focuses on keeping your bookmark list current.

- **Sync frequency** — choose how often MyDeck checks for bookmark changes in the background; the time of the last sync and the next scheduled sync are shown below this control
- **Sync Bookmarks Now** — immediately checks for new, updated, and deleted bookmarks

Above the sync button, the **Bookmarks** counts show how many bookmarks are in your **My List**, **Archive**, and **Favorites** views.

### Offline Reading

Offline reading is optional and off by default. The **Enable offline reading** toggle turns it on; a status indicator below shows whether content is actively syncing, up to date, or waiting on a connection or battery constraint. For what offline reading does, how automatic and pinned content differ, and how download status appears on your cards, see the [Offline Reading](./offline-reading.md) guide. The controls below decide *how much* MyDeck keeps and *when* it downloads.

#### What to keep offline

Choose which bookmarks MyDeck keeps fully available offline:

- **Storage limit** — keep saved content until it reaches the selected total storage size
- **Most recent** — keep the most recently saved bookmarks fully available offline, up to the selected count
- **Last** — keep a selected duration's worth of bookmarks fully available offline, measured back from the current time
- **Maximum storage cap** — appears with **Most recent** and **Last** to set an upper limit on the *total* offline storage MyDeck uses. This cap covers **both** automatically managed content **and** content you've **pinned**. It is the only automatic setting that can remove pinned content: if the total ever exceeds the cap, MyDeck removes the oldest-downloaded content first — across both kinds — until usage is back under the cap. Setting the cap below what **Most recent**/**Last** would otherwise keep makes content download and trim repeatedly, so leave headroom above your chosen amount
- **Include Archive** — when off, automatic offline maintenance applies only to **My List**; when on, archived bookmarks remain eligible

For **Most recent**, the selected count is always based on the raw newest bookmarks in scope. If some of those bookmarks have no extractable article content, they are counted as skipped rather than replaced by older bookmarks.

When **Include Archive** is off, archiving a bookmark removes it from offline eligibility, so its content is removed during the next maintenance run.

#### Whether to download

- **Wi-Fi only** — prevents automatic offline downloads from using mobile data
- **Allow on battery saver** — when off, automatic offline downloads pause while battery saver is active

#### Storage

Shows how many bookmarks are fully available offline — split into **Automatic** and **Pinned** counts — how much storage offline content is using, and when offline content maintenance last ran.

- **Clear All Offline Content** — removes all locally stored offline content, including full offline packages and text cached for quick re-opening, reclaiming all offline storage; bookmarks themselves are not deleted, and content is re-downloaded according to your offline settings

If you turn offline reading off, MyDeck removes all stored offline content — both automatic and pinned — immediately, but keeps the lightweight text it cached when you opened bookmarks while browsing.

## User Interface

The **User Interface Settings** screen is organized into sections.

### Appearance

- **Theme** — choose **Light**, **Dark**, or **System** (follows your device's system setting)
- **When app is light** — choose **Paper** or **Sepia** for the light appearance used throughout the app and reader
- **When app is dark** — choose **Dark** or **Black** for the dark appearance used throughout the app and reader

### Bookmark List

- **Show source icons** — when on (the default), the Compact layout shows each site's favicon in the status rail. Turn it off to hide the favicons and give the card text the full width.
- **Show add-bookmark button** — when on (the default), the **+** button appears at the bottom-right of the bookmark list. Turn it off if you only ever save bookmarks with Android's share sheet.

#### Swipe actions

Controls the horizontal swipe gesture on bookmark cards.

- **Enable swipe actions** — master switch. When off, swiping cards does nothing and the navigation drawer's left-edge swipe gesture returns.
- **Swipe right action** — choose what a right swipe does: **Archive**, **Delete**, **Favorite**, or **None** (disables the right direction only).
- **Swipe left action** — choose what a left swipe does: **Archive**, **Delete**, **Favorite**, or **None** (disables the left direction only).

Setting a direction to **None** leaves the other direction active.

### Reading

- **Keep screen on while reading** — when enabled, the screen stays on while you have a bookmark open in reading view. Enabled by default.
- **Fullscreen while reading** — when enabled, article reading view hides the system bars and top bar after a short delay so the page can use the full screen. Swipe from the edge or tap near the top edge to reveal them temporarily.
- **Internal browser** — when on (the default), a bookmark's original web page opens in the in-app web viewer for an immersive experience. When off, the **View web page** action opens the page in your device's external browser instead, and a bookmark with no readable content shows its title and description with a *No content available* note and a button to open it externally. (This affects only the original-web-page view; links you tap inside an article are unaffected.)
- **Include native Readeck fonts** — off by default, so the reading view's font picker shows the app's own curated font set. Turn it on to also add the fonts from Readeck's native reading view. Licenses for every bundled font are listed under **About → Font licenses**.

### Sharing

- **Share links as** — choose whether bookmark sharing sends just the URL or a two-line block with the bookmark title above the URL

## Logs

The **Logs** screen shows the app's log output, which can be useful for troubleshooting. From this screen you can:

- **Filter** — tap the calendar icon to filter logs by date
- **Share** — send the current log to another app
- **Refresh** — reload the log display
- **Delete** — clear the log

---

The navigation drawer also has an **About** entry, which shows the current app version, build details, and links to the MyDeck and Readeck project pages. After an update, a **What's New** sheet highlights what changed in the new version; you can reopen it any time from **About → What's New**.
