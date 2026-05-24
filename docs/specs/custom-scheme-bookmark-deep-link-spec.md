# Custom Scheme Bookmark Deep Link Spec

## Goal

Allow external tools, such as a Telegram highlight bot, to open a Readeck bookmark directly in MyDeck's reading view using the Readeck bookmark ID.

Example source URL:

```text
https://read.eatonfamily.net/bookmarks/PJmgMtUozPd5JF9zqPra7w
```

Bot-generated MyDeck URL:

```text
mydeck://bookmark/PJmgMtUozPd5JF9zqPra7w
```

## Terminology

This is a **custom scheme deep link**.

It is not an Android **Verified Link** or **App Link**. Android App Links are verified `http` or `https` links where the website proves ownership by serving `/.well-known/assetlinks.json`. A `mydeck://...` URL is instead a private app URI scheme claimed by MyDeck through an Android intent filter.

In short:

- `mydeck://bookmark/{id}`: custom scheme deep link.
- `https://read.eatonfamily.net/bookmarks/{id}` opening directly in MyDeck: Android App Link / Verified Link.

## Supported URL Format

Initial supported format:

```text
mydeck://bookmark/{bookmarkId}
```

Example:

```text
mydeck://bookmark/PJmgMtUozPd5JF9zqPra7w
```

Optional future formats:

```text
mydeck://bookmark/{bookmarkId}?annotation={annotationId}
mydeck://bookmark/{bookmarkId}?original=true
```

## Android Entry Point

Add an `ACTION_VIEW` intent filter to `MainActivity`:

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />

    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />

    <data
        android:scheme="mydeck"
        android:host="bookmark" />
</intent-filter>
```

This makes Android offer MyDeck when a user taps `mydeck://bookmark/...`.

## Intent Handling

Extend `MainActivity` intent handling to support custom scheme URLs.

Rules:

- Only handle `Intent.ACTION_VIEW`.
- Only handle `mydeck://bookmark/{id}`.
- Extract the bookmark ID from the first path segment.
- Ignore malformed URLs safely.
- Reuse the existing navigation path:

```kotlin
navController.navigate(BookmarkDetailRoute(bookmarkId))
```

The app already supports opening the detail view by ID through `BookmarkDetailRoute`.

## Bookmark Lookup Behavior

Initial behavior:

- If the bookmark exists locally, open the existing reading/detail screen.
- If the bookmark does not exist locally, show the existing detail error state.

Preferred follow-up behavior:

- If not found locally, trigger a lightweight sync or direct `GET /api/bookmarks/{id}` fetch.
- If fetch succeeds, cache the bookmark and open it.
- If fetch fails, show a user-facing "Bookmark not found or not synced" message.

## Validation

Bookmark ID validation should be conservative:

- Trim whitespace.
- Reject empty IDs.
- Accept Readeck-style opaque IDs such as `PJmgMtUozPd5JF9zqPra7w`.
- Do not assume UUID format.
- Avoid interpreting arbitrary nested paths.

Valid:

```text
mydeck://bookmark/PJmgMtUozPd5JF9zqPra7w
```

Invalid:

```text
mydeck://bookmark/
mydeck://bookmark
mydeck://other/PJmgMtUozPd5JF9zqPra7w
```

## Security and Safety

This deep link should not authenticate the user or expose private content by itself.

It only asks MyDeck to open a bookmark ID using the user's already-configured Readeck account and local database. If the user is not logged in or the bookmark is unavailable, MyDeck should not leak anything.

## User-Facing Documentation

Add a short note to the English guide, likely under `getting-started.md` or `your-bookmarks.md`:

> MyDeck can open bookmark deep links in the format `mydeck://bookmark/{bookmarkId}`. External tools can use this format to open a synced Readeck bookmark directly in the reader.

## Tests

Suggested unit tests:

- Parses `mydeck://bookmark/PJmgMtUozPd5JF9zqPra7w`.
- Rejects missing bookmark ID.
- Rejects wrong scheme.
- Rejects wrong host.
- Preserves opaque IDs without UUID assumptions.

Suggested manual checks:

- Tap a `mydeck://bookmark/{id}` link from Telegram or another app.
- Confirm MyDeck opens to the reader.
- Confirm cold start and already-running app both work.
- Confirm an unknown ID fails gracefully.
