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

- **Sync frequency** — choose how often MyDeck checks for bookmark changes in the background
- **Sync Bookmarks Now** — immediately checks for new, updated, and deleted bookmarks

### Offline Reading

Offline reading is optional and off by default. When enabled, MyDeck automatically keeps eligible bookmarks available for offline reading in the background.

- **Enable offline reading** — turns on automatic offline content maintenance
- **Storage limit** — keep full offline packages until they reach the selected storage target
- **Newest N bookmarks** — keep the newest saved bookmarks fully available offline, up to the selected count
- **Added within last** — keep bookmarks that were added within the selected rolling time window fully available offline
- **Maximum storage cap** — appears for **Newest N bookmarks** and **Added within last**; it limits how much space those policies can use
- **Include archived bookmarks** — when off, automatic offline maintenance applies only to **My List**
- **Clear offline content when archiving** — removes managed offline packages when you archive a bookmark
- **Wi-Fi only** — prevents automatic offline downloads from using mobile data
- **Allow on battery saver** — when off, automatic offline downloads pause while battery saver is active
- **Download content for date range…** — this button is reserved for one-time offline downloads for a specific date range

If you turn offline reading off again, MyDeck removes managed offline packages immediately but keeps any on-demand text caches you created by opening bookmarks.

### Sync Status

The **Sync Status** section always shows when bookmark sync last ran and when the next scheduled bookmark sync is due. When offline reading is enabled, it also shows bookmark totals, how many bookmarks are fully available offline, how much offline storage is in use, and when offline content maintenance last ran.

- **Clear All Offline Content** — removes locally stored offline content without deleting the bookmarks themselves

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
