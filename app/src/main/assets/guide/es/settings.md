# Settings

Open the navigation drawer and tap **Settings** to access the app's settings. Settings are organized into the following sections.

## Account

The **Account** section shows the Readeck server your app is connected to.

- **Readeck URL** — the API address of your Readeck server. You can update this if your server moves.
- **Allow unencrypted connections** — permits connecting to servers over HTTP instead of HTTPS. Only enable this if your server does not support HTTPS.
- **Sign Out** — disconnects MyDeck from your Readeck account and removes all locally stored bookmarks from the device. You'll be returned to the welcome screen where you can connect to any server.

## Synchronization

MyDeck keeps your bookmark list in sync with the Readeck server. The **Synchronization** section controls how and when that happens.

- **Sync Bookmarks Now** — immediately checks for new, updated, and deleted bookmarks.
- **Background Synchronization** — sets how often the app syncs automatically in the background. Options range from manual-only to intervals between every hour and every 30 days.
- **Sync on App Open** — when enabled, MyDeck syncs with the server each time the app is opened.
- **Sync Notifications** — controls whether the app shows notifications for background sync results.

The **Sync Status** area shows the number of bookmarks with downloaded article content, the date and time of the last sync, and the next scheduled sync.

> **Note:** The pull-to-refresh gesture and the refresh icon on the bookmark list check for new and updated bookmarks immediately. Detecting bookmarks deleted on the server requires a full sync, which you can trigger with **Sync Bookmarks Now** or via background sync.

## User Interface

- **Theme** — choose between **System default**, **Light**, **Dark**, or **Sepia**. System default follows your device's light/dark mode setting.

## Logs

The **Logs** section lets you view the app's log files, which can be useful for troubleshooting. You can also share log files directly from this screen, and configure how long logs are retained before being automatically deleted.

## About

The **About** screen shows the current app version, build details, device information, and links to the MyDeck and Readeck project repositories. It also includes open source library attributions.
