# Getting Started

MyDeck is a mobile reader for [Readeck](https://readeck.org), a self-hosted read-later service. You save articles, videos, and web pages to your Readeck server and read them in MyDeck — including offline.

To use MyDeck you need access to a running Readeck server.

## Connecting to Your Server

When you first open MyDeck, you'll see the welcome screen. Enter the URL of your Readeck server's API endpoint in the **Readeck URL** field — for example, `https://readeck.example.com/api` — then tap **Connect**.

If the URL is invalid or the server cannot be reached, an error message will appear below the field. Double-check the address and make sure your device has network access to the server.

## Signing In

After tapping **Connect**, MyDeck uses a secure authorization flow to sign you in to your Readeck account. The screen will show two things:

- **A verification URL** — the address to visit in your browser
- **Your user code** — a short code to enter on that page

The easiest way to sign in is to tap **Open in Browser**. This opens the verification page in your browser with your code already included, so you only need to log in to Readeck and approve the connection.

If you prefer to do it manually, tap **Copy URL** to copy the address, open it in a browser yourself, and enter the code shown on screen when prompted.

Once you approve the connection, return to MyDeck. The app will detect the authorization automatically — you don't need to tap anything else.

> **Note:** The user code expires after a few minutes. If the countdown reaches zero before you finish, tap **Cancel** and start the connection process again.

## First Load

After signing in, MyDeck syncs your existing bookmarks from the server. Depending on how many bookmarks you have, this initial load may take a moment. Article content is downloaded in the background so your bookmarks are available to read offline.

## Signing Out or Switching Servers

To sign out or connect to a different server, go to **Settings → Account**. Signing out removes all locally stored bookmarks from the device.
