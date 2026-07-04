# Getting Started

MyDeck is a mobile reader for Readeck, a self-hosted read-later service. You save web pages (articles, videos, and pictures) to your Readeck server and read them in MyDeck — including offline.

To use MyDeck you need access to a running Readeck server.

## Connecting to Your Server

When you first open MyDeck, you'll see the welcome screen with a **Readeck URL** field pre-filled with `https://`. Enter the address of your Readeck server — for example, `https://readeck.example.com` — and tap **Connect**.

The standard MyDeck app accepts `https://` URLs only. If you enter an `http://` address, it is rejected — your OAuth token would otherwise travel over the network in cleartext. For self-hosted setups on a trusted private network, the recommended path is to put HTTPS in front of Readeck — for example, [Tailscale Serve](https://tailscale.com/docs/features/tailscale-serve) presents an HTTPS tailnet URL while proxying to a local HTTP service, or a reverse proxy can terminate TLS.

A separate **HTTP-enabled APK** is published for setups that genuinely cannot use HTTPS (for example, connecting directly to a tailnet IP over HTTP). It installs alongside the standard app as its own package and shows an insecure-connection warning when you enter an `http://` URL.

MyDeck requires a Readeck server running **version 0.21 or later** (the first release to support the sign-in method MyDeck uses). If you enter the address of an older server, MyDeck tells you the server is too old and needs updating rather than showing a cryptic error. If the address doesn't point to a Readeck server at all — or the server can't be reached — you'll see a message explaining that instead. Double-check the address and make sure your device has network access to the server.

At the bottom of the welcome screen are three quick-access buttons — **About**, **User Guide**, and **Logs** — so you can read the guide, check app details, or inspect logs before connecting. This is handy when diagnosing a connection problem. Tap the back arrow on any of those screens to return to the welcome screen.

## Signing In

MyDeck uses OAuth to sign you in securely without ever handling your password.

### Browser-based sign-in (default)

After entering your server address and tapping **Sign In**, MyDeck opens the Readeck authorization page in your browser. Log in to Readeck if you are not already logged in, review the permissions, and tap **Authorize**. You are returned to MyDeck automatically and the sign-in is complete.

If you close the browser before completing the authorization, tap **Cancel** in MyDeck and try again.

### Sign in with a code instead

If you cannot use the browser flow — for example, on a TV or e-reader, or if your device browser cannot reach the server — tap **Sign in with a code instead** on the sign-in screen. The screen shows:

1. **A verification URL** — the address to visit in any browser (for example, `https://readeck.example.com/device`)
2. **Your user code** — a short one-time code to enter on that page

Tap **Open in Browser** to open the Readeck page directly, or tap **Copy URL** and **Copy Code** to use them manually. Once you authorize in the browser, MyDeck detects the approval automatically.

> **Note:** The user code expires after 5 minutes. If the countdown reaches zero, go back and start the process again.

## First Load

After signing in, MyDeck syncs your bookmarks from the server. Depending on how many you have, this initial sync may take a moment. Article content is downloaded in the background as you read or according to your sync settings, so your bookmarks are available offline.

## Signing Out or Switching Servers

To sign out or connect to a different server, open the navigation drawer and tap **Settings**, then tap **Account**.

- To **switch servers**, update the **Readeck URL** field and tap **Sign In** to re-authenticate.
- To **sign out**, tap **Sign Out**. This removes all locally stored bookmarks from the device and returns you to the welcome screen.
