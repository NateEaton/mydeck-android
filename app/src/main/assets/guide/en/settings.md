# Settings

Open the navigation drawer and tap **Settings** to access the app's settings.

## Account

The **Account** option in the drawer shows your username. Tapping it opens the **Account** screen, which shows the Readeck server your app is connected to.

- **Readeck URL** — the API address of your Readeck server. To switch to a different server, update this field and tap **Login** to re-authenticate.
- **Sign Out** — disconnects MyDeck from your Readeck account and removes all locally stored bookmarks from the device. You'll be returned to the welcome screen.

> **Note:** Signing out removes all local bookmark data. Everything is stored on the server so the data will still be available the next time you sign into the same server.

## Synchronization

The **Synchronization Settings** screen controls how MyDeck keeps your bookmarks and their content in sync with the Readeck server.

### Bookmark Sync

The top of the screen focuses on keeping your bookmark list current.

- **Sync frequency** — choose how often MyDeck checks for bookmark changes in the background; the time of the last sync and the next scheduled sync are shown below this control
- **Sync Bookmarks Now** — immediately checks for new, updated, and deleted bookmarks

Above the sync button, the **Bookmarks** counts show how many bookmarks are in your **My List**, **Archive**, and **Favorites** views.

### Offline Reading

Offline reading is optional and off by default. When enabled, MyDeck automatically downloads and keeps eligible bookmarks available so you can read them without internet access. A status indicator below the toggle shows whether content is actively syncing, up to date, or waiting on a connection or battery constraint.

#### What to keep offline

Choose which bookmarks MyDeck keeps fully available offline:

- **Storage limit** — keep saved content until it reaches the selected total storage size
- **Most recent** — keep the most recently saved bookmarks fully available offline, up to the selected count
- **Added within last** — keep bookmarks added within the selected rolling time window fully available offline
- **Maximum storage cap** — appears with **Most recent** and **Added within last** to set an upper storage limit for those options
- **Include Archive** — when off, automatic offline maintenance applies only to **My List**; when on, archived bookmarks remain eligible

When **Include Archive** is off, archiving a bookmark removes it from offline eligibility, so its content is removed during the next maintenance run.

#### Whether to download

- **Wi-Fi only** — prevents automatic offline downloads from using mobile data
- **Allow on battery saver** — when off, automatic offline downloads pause while battery saver is active

#### Storage

Shows how many bookmarks are fully available offline, how much storage offline content is using, and when offline content maintenance last ran.

- **Clear All Offline Content** — removes all locally stored offline content without deleting the bookmarks themselves

If you turn offline reading off, MyDeck removes all automatically downloaded content immediately but keeps text that was loaded on demand when you opened bookmarks while browsing.

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
