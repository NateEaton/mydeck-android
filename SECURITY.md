# Security

This document describes the security posture of MyDeck and the design decisions that affect how the app handles sensitive data, network communication, and untrusted content.

## Reporting Vulnerabilities

If you discover a security vulnerability, please report it privately by opening a [GitHub Security Advisory](https://github.com/NateEaton/mydeck-android/security/advisories/new) rather than a public issue.

## Authentication

MyDeck authenticates with the Readeck server using the **OAuth 2.0 Device Authorization Grant** flow. The app never handles or stores the user's Readeck password directly. OAuth tokens are stored in `EncryptedSharedPreferences` using AES-256 (SIV for keys, GCM for values), backed by the Android Keystore.

## Network Security

### Default configuration

The default release build enforces HTTPS-only communication:

- `cleartextTrafficPermitted="false"` in the network security config
- Only system-trusted certificate authorities are accepted
- OAuth requires HTTPS by design

### Self-hosted overrides

For users running Readeck on a local network or behind a private CA, two opt-in build flags are available. These are **disabled by default** and require a custom build:

- `ALLOW_INSECURE_HTTP_RELEASE` — permits cleartext HTTP connections (for `http://` Readeck instances without TLS)
- `ALLOW_USER_CA_RELEASE` — trusts user-installed certificate authorities (for HTTPS servers signed by a private CA)

These flags are documented in `.github/workflows/release.yml` and are never enabled in the standard GitHub release builds.

## Credential Storage

- OAuth tokens are stored in `EncryptedSharedPreferences` (AES-256-GCM), not in plain `SharedPreferences` or the local database.
- User-facing preferences (theme, typography, sync settings) are stored in unencrypted `SharedPreferences` since they contain no sensitive data.
- The Room database stores bookmark metadata and content. It does not contain authentication credentials.

## WebView Security

MyDeck uses two distinct WebView contexts with different security profiles:

### Reader WebView (extracted content)

- Loads **app-owned HTML templates** from local assets, populated with server-extracted article content
- JavaScript is enabled to support in-article search, highlight/annotation interaction, and image lightbox functionality
- Two `@JavascriptInterface` bridges are registered for image tap handling and annotation interaction
- Content is sandboxed within the app's asset domain; no external navigation occurs from this WebView

### Original Web Page WebView

- Loads the **original source URL** when the user selects "View web page" from the overflow menu
- JavaScript and DOM storage are enabled so that modern websites render correctly
- **No `@JavascriptInterface` bridges** are registered on this WebView — it has no privileged access to app internals
- No cookies are shared between this WebView and the app's authenticated API communication
- File access is disabled by default (Android WebView default)
- This WebView is functionally equivalent to opening the URL in a browser, but kept in-app for reading continuity

### Design rationale

The original web page WebView enables JavaScript because most modern websites require it to render. Disabling JavaScript makes video sites (YouTube, Vimeo) and many article sites non-functional, with no visible indication of why the page is broken. Since this WebView has no privileged bridges, no access to app credentials, and no cookie sharing with the authenticated API layer, the risk profile is comparable to opening the same URL in any browser — which the user can already do from the bookmark details screen.

## Build Security

- Release builds use R8 code shrinking and obfuscation
- `dependenciesInfo` is excluded from the APK (`includeInApk = false`) and AAB (`includeInBundle = false`)
- GitHub Actions release workflows use pinned action versions

## Data Handling

- Bookmark content is synced from the user's own Readeck server and stored locally in Room
- No analytics, telemetry, or crash reporting services are included
- No data is sent to third-party services; all communication is between the app and the user's Readeck server
