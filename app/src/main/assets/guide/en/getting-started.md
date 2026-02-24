# Getting Started

MyDeck is a mobile reader for [Readeck](https://readeck.org), a self-hosted read-later service. You save web pages (articles, videos, and pictures) to your Readeck server and read them in MyDeck — including offline.

To use MyDeck you need access to a running Readeck server.

## Connecting to Your Server

When you first open MyDeck, you'll see the welcome screen with a **Readeck URL** field pre-filled with `https://`. Enter the address of your Readeck server — for example, `https://readeck.example.com` — and tap **Connect**. 

If the URL is invalid or the server cannot be reached, an error message will appear below the field. Double-check the address and make sure your device has network access to the server.

## Signing In

MyDeck uses the OAuth device authorization flow to sign you in securely without ever handling your password. After tapping **Connect**, the screen shows two things:

1. **A verification URL** — the address to visit in your browser (for example, `https://readeck.example.com/device`)
2. **Your user code** — a short one-time code to enter on that page

The easiest way to sign in is to tap **Open in Browser**, which opens the Readeck authorization page directly. Log in to Readeck if prompted, review the permissions requested, and tap **Authorize**.

If you prefer to do it manually, tap **Copy URL** to copy the verification address and open it yourself, then tap **Copy Code** to copy the code and enter it when prompted.

Once you tap Authorize, MyDeck detects the approval automatically. Once you see the Readeck page with the "Return to MyDeck" button, you can close Readeck and return to MyDeck.

> **Note:** The user code expires after 5 minutes. If the countdown reaches zero before you finish, go back and start the connection process again.

## First Load

After signing in, MyDeck syncs your bookmarks from the server. Depending on how many you have, this initial sync may take a moment. Article content is downloaded in the background as you read or according to your sync settings, so your bookmarks are available offline.

## Signing Out or Switching Servers

To sign out or connect to a different server, open the navigation drawer and tap **Settings**, then tap **Account**.

- To **switch servers**, update the **Readeck URL** field and tap **Login** to re-authenticate.
- To **sign out**, tap **Sign Out**. This removes all locally stored bookmarks from the device and returns you to the welcome screen.
