# Settings

Open the navigation drawer and tap **Settings** to access the app's settings.

## Account

The **Account** option in the drawer shows your username. Tapping it opens the **Account** screen, which shows the Readeck server your app is connected to.

- **Readeck URL** — the API address of your Readeck server. To switch to a different server, update this field and tap **Login** to re-authenticate.
- **Sign Out** — disconnects MyDeck from your Readeck account and removes all locally stored bookmarks from the device. You'll be returned to the welcome screen.

> **Note:** Signing out removes all local bookmark data. Everything is stored on the server so the data will still be available the next time you sign into the same server.

## Synchronization

The **Synchronization Settings** screen controls how MyDeck keeps your bookmarks and their content in sync with the Readeck server.

### Content Downloads

The top of the screen controls when article content is downloaded. Choose between two modes using the segmented button:

- **On demand** (default) — content loads when you open a bookmark. This uses the least resources and works well when you have a network connection.
- **Automatic** — content is downloaded in the background during each bookmark sync, so bookmarks are ready for offline reading at any time.

#### Download for offline reading

When **On demand** mode is selected, a download section appears that lets you batch-download content for offline reading — useful before a flight or any time you'll be without a connection. Choose a time range from the dropdown:

- **All time** — downloads content for all bookmarks
- **Past day / Past week / Past month / Past year** — downloads content for bookmarks added within that period
- **Custom date range** — pick specific from/to dates

Tap **Start Download** to begin. The download runs in the background — you can leave the settings screen and check progress in the **Status** section.

#### Download options

- **Download images** — when enabled, images are downloaded alongside article text for offline viewing. When off, images load from the network when you read.
- **Include archived content** — when enabled, content is also downloaded for archived bookmarks; when disabled, only bookmarks in My List are downloaded.

#### Download constraints

Expand this section to control download conditions:

- **Only download on Wi-Fi** — prevents content from downloading over mobile data
- **Allow download on battery saver** — when off, content downloads pause when battery saver is active

If you try to download content while a constraint is active, a dialog will give you the option to temporarily override the constraint.

### Storage

Expand this section to manage downloaded content storage.

- **Offline content** — total disk space used by downloaded bookmark content
- **Clear content when archiving** — when enabled, downloaded content is automatically removed when you archive a bookmark, freeing storage space
- **Clear All Offline Content** — removes all locally stored content; bookmark metadata remains synced

### Bookmark Sync

Expand this section to control how MyDeck keeps your bookmark list in sync with changes from other devices.

- **Sync frequency** — how often MyDeck syncs in the background. Tap to choose an interval (e.g., every hour).
- **Sync Now** — immediately checks for new, updated, and deleted bookmarks

### Status

Expand this section to see a summary of what's been synced:

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
