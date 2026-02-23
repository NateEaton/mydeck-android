# Settings

Open the navigation drawer and tap **Settings** to access the app's settings.

## Account

The **Account** screen shows the Readeck server your app is connected to and your username.

- **Readeck URL** — the API address of your Readeck server. To switch to a different server, update this field and tap **Login** to re-authenticate.
- **Sign Out** — disconnects MyDeck from your Readeck account and removes all locally stored bookmarks from the device. You'll be returned to the welcome screen.

> **Warning:** Signing out removes all local bookmark data. Make sure your bookmarks are safely stored on the server before signing out.

## Synchronization

The **Synchronization Settings** screen controls how MyDeck keeps your bookmarks and their content in sync with the Readeck server.

MyDeck automatically syncs your bookmark list when you first sign in and each time the app is opened.

### Bookmark Sync

- **Sync frequency** — how often MyDeck syncs in the background. Tap to choose an interval (e.g., every hour).
- **Next synchronization** — shows when the next automatic sync is scheduled.
- **Sync Bookmarks Now** — immediately checks for new, updated, and deleted bookmarks.

### Content Sync

Controls when the full article content for bookmarks is downloaded.

**Content Sync Mode:**
- **Automatic** — content is downloaded automatically during each bookmark sync
- **Manual** — content is downloaded only when you open a bookmark (On demand), or you can trigger a batch download for bookmarks added within a date range (Date Range — choose from Past day, Past week, Past month, Past year, or Custom date range, then tap **Download**)

**Constraints:**
- **Only download on Wi-Fi** — prevents content from downloading over mobile data
- **Allow download on battery saver** — when off, content sync pauses when battery saver is active

If you try to sync content while a constraint is active (for example, downloading over mobile data when Wi-Fi-only is enabled), a warning dialog will give you the option to override the constraint.

### Sync Status

Shows a summary of what's been synced:

- **Bookmarks** — total count, My List, Archived, Favorites
- **Content** — how many bookmarks have downloaded content, how many are available to download, and any that failed

## User Interface

The **User Interface Settings** screen controls the app's appearance.

- **Theme** — choose **Light**, **Dark**, or **System** (follows your device's system setting)
- **Sepia reading theme** — when enabled, uses warm tinted colors throughout the app. This only applies when the app is in light mode (either Light is selected, or System is selected and your device is in light mode).

## Logs

The **Logs** screen shows the app's log output, which can be useful for troubleshooting. From this screen you can:

- **Filter** — tap the calendar icon to filter logs by date
- **Share** — send the current log to another app
- **Refresh** — reload the log display
- **Delete** — clear the log

---

The navigation drawer also has an **About** entry, which shows the current app version, build details, and links to the MyDeck and Readeck project pages.
