# Settings

Open the navigation drawer and tap **Settings** to access the app's settings.

## Account

The **Account** option in the drawer shows your username. Tapping it opens the **Account** screen, which shows the Readeck server your app is connected to.

- **Readeck URL** — the API address of your Readeck server. To switch to a different server, update this field and tap **Login** to re-authenticate.
- **Sign Out** — disconnects MyDeck from your Readeck account and removes all locally stored bookmarks from the device. You'll be returned to the welcome screen.

> **Note:** Signing out removes all local bookmark data. Everything is stored on the server so the data will still be available the next time you sign into the same server.

## Synchronization

The **Synchronization Settings** screen controls how MyDeck keeps your bookmarks and their content in sync with the Readeck server.

MyDeck automatically syncs your bookmark list when you first sign in and each time the app is opened. The periodic sync is for detecting if bookmarks were deleted on the server so they can be removed from the app.

### Schedule

- **Sync frequency** — how often MyDeck syncs in the background. Tap to choose an interval (e.g., every hour).
- **Next synchronization** — shows when the next automatic sync is scheduled.
- **Content sync mode** — controls when the full article content for bookmarks is downloaded:
  - **Automatic** — content is downloaded automatically during each bookmark sync
  - **Manual (on demand)** — content is downloaded only when you open a bookmark; this is the default when the app is installed
  - **Manual (date range)** — you can trigger a batch download for bookmarks added within a date range (choose from Past day, Past week, Past month, Past year, or Custom date range, then tap **Download**)
- **Sync Bookmarks Now** — immediately checks for new, updated, and deleted bookmarks

### Content

Controls what content is downloaded and under which conditions.

- **Download images** — when enabled, images are downloaded alongside article text for offline viewing. When off, articles are stored as text only and images load from the network when you read. Picture bookmarks always download their image regardless of this setting. You can override this setting for individual articles using the image toggle in **Details** (see [Reading](./reading.md)).
- **Include archived bookmarks** — when enabled, content is also downloaded for archived bookmarks; when disabled, only bookmarks in My List are synced
- **Only download on Wi-Fi** — prevents content from downloading over mobile data
- **Allow download on battery saver** — when off, content sync pauses when battery saver is active

If constraints are currently blocking content downloads (e.g., waiting for Wi-Fi), a status line appears below the constraint toggles showing what's blocking the download.

If you try to sync content while a constraint is active, a warning dialog will give you the option to temporarily override the constraint.

### Storage

Shows how much space downloaded content is using and provides cleanup options.

- **Content storage** — total disk space used by downloaded bookmark content
- **Auto-clear content on archive** — when enabled, downloaded content is automatically removed when you archive a bookmark, freeing storage space
- **Clear all downloaded content** — removes all locally stored content; bookmark metadata remains synced

### Sync Status

Shows a summary of what's been synced:

- **Bookmarks** — total count, My List, Archived, Favorites
- **Content** — how many bookmarks have downloaded content, how many are available to download, and any that failed

## User Interface

The **User Interface Settings** screen controls the app's appearance.

- **Theme** — choose **Light**, **Dark**, or **System** (follows your device's system setting)
- **When app is light** — choose **Paper** or **Sepia** for the light appearance used throughout the app and reader
- **When app is dark** — choose **Dark** or **Black** for the dark appearance used throughout the app and reader
- **Share links as** — choose whether bookmark sharing sends just the URL or a two-line block with the bookmark title above the URL
- **Fullscreen while reading** — when enabled, article reading view hides the system bars and top bar after a short delay so the page can use the full screen. Swipe from the edge or tap near the top edge to reveal them temporarily.
- **Keep screen on while reading** — when enabled, the screen stays on while you have a bookmark open in reading view. Enabled by default.

## Logs

The **Logs** screen shows the app's log output, which can be useful for troubleshooting. From this screen you can:

- **Filter** — tap the calendar icon to filter logs by date
- **Share** — send the current log to another app
- **Refresh** — reload the log display
- **Delete** — clear the log

---

The navigation drawer also has an **About** entry, which shows the current app version, build details, and links to the MyDeck and Readeck project pages.
