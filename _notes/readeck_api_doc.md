Based on the Readeck source code analysis and available documentation for versions 0.20+, here is the reconstructed API documentation. 

Since there is no public hosted documentation, this markdown is derived from the Go source definitions (specifically `internal/bookmarks/routes` and `pkg/openapi`) and the behavior described in recent release notes (Sync API).

***

# Readeck API Documentation

This documentation covers the core endpoints required to build a native client (Android/iOS) for Readeck.

**Base URL**: `https://<your-instance>/api`  
**Authentication**: Bearer Token  
**Content-Type**: `application/json`

## Authentication

All requests must include the `Authorization` header. You can generate a token from your user profile in the web UI or use the `POST /auth/token` endpoint (if available and configured).

```http
Authorization: Bearer <your_api_token>
```

---

## 1. Synchronization (Client Sync)
*Added in v0.20. Use this for keeping a local offline database in sync.*

### Check for Updates
Returns a lightweight list of IDs that have changed since a specific time. Use this to avoid downloading everything.

**Endpoint:** `GET /bookmarks/sync`

**Query Parameters:**
*   `since` (optional): timestamp (Unix epoch or RFC3339). If omitted, returns all.

**Response:**
```json
[
  {
    "id": 42,
    "status": "updated",  // "updated" or "deleted"
    "dt": "2023-10-27T10:00:00Z"
  },
  {
    "id": 15,
    "status": "deleted",
    "dt": "2023-10-26T09:30:00Z"
  }
]
```

### Download Content (Stream)
Fetches the **full data** (including HTML content and image resources) for a specific list of bookmarks. This is a streamed response designed for bulk syncing.

**Endpoint:** `POST /bookmarks/sync`

**Request Body:**
```json
{
  "ids": [42, 45, 108]
}
```

**Response:**
A JSON stream (NDJSON or a list) containing the full `Bookmark` objects (see [Models](#models)).

---

## 2. Bookmarks (CRUD)

### Create Bookmark (Save URL)
Saves a new URL. The server immediately begins the "Readability" extraction process.

**Endpoint:** `POST /bookmarks`

**Request Body:**
```json
{
  "url": "https://example.com/article-to-read",
  "labels": ["news", "tech"], // Optional
  "is_favorite": false         // Optional
}
```

**Response:**
Returns the created `Bookmark` object.
*Note: Depending on server load and article size, the `content` (HTML) field might be empty initially if the extraction task is still running in the background. Your client should handle this "Processing" state.*

### List Bookmarks
Returns a paginated list of bookmarks. This is typically used for the initial view before syncing content.

**Endpoint:** `GET /bookmarks`

**Query Parameters:**
*   `page`: (int) Page number (default 1).
*   `limit`: (int) Items per page (default 20).
*   `state`: (string) Filter by state: `unread` (default), `archived`, `all`.
*   `q`: (string) Search query.

### Get Single Bookmark
Retrieves the full details of a bookmark, **including the extracted HTML content**.

**Endpoint:** `GET /bookmarks/{id}`

### Update Bookmark (Progress & State)
Use this to update reading progress or archive an article.

**Endpoint:** `PATCH /bookmarks/{id}`

**Request Body:**
```json
{
  "is_read": false,
  "reading_progress": 0.45,  // Float: 0.0 to 1.0
  "is_favorite": true,
  "title": "New Custom Title",
  "labels": ["new-label"]
}
```

### Delete Bookmark
**Endpoint:** `DELETE /bookmarks/{id}`

---

## Models

### Bookmark Object
The core data structure representing a saved article.

```json
{
  "id": 123,
  "url": "https://example.com/original-article",
  "title": "The Title of the Article",
  "excerpt": "A short summary or first paragraph...",
  "author": "John Doe",
  "source": "example.com",
  
  // State
  "is_read": false,
  "is_favorite": false,
  "reading_progress": 0.0,
  
  // Timestamps
  "created_at": "2023-10-27T10:00:00Z",
  "updated_at": "2023-10-27T10:05:00Z",
  "published_at": "2023-10-26T12:00:00Z",
  
  // Content (Usually only present in GET /bookmarks/{id} or Sync POST)
  "content": "<article>The full simplified HTML content...</article>",
  "text_content": "The plain text version...",
  
  // Assets
  "thumbnail_url": "/api/bookmarks/123/image", 
  
  // Organization
  "labels": [
    { "id": 1, "name": "tech", "slug": "tech" }
  ]
}
```

## Client Implementation Strategy

Based on your Android/Offline-first requirement, use this algorithm:

1.  **On Startup / Refresh:**
    *   Call `GET /bookmarks/sync?since={last_sync_timestamp}`.
    *   Identify IDs marked as `updated` that you don't have locally or that have changed.
    *   Identify IDs marked as `deleted` and remove them from your local SQLite DB.
2.  **Fetch Content:**
    *   Take the list of `updated` IDs.
    *   Call `POST /bookmarks/sync` with these IDs.
    *   Upsert the returned objects (metadata + content) into your local DB.
3.  **On "Add Bookmark":**
    *   Call `POST /bookmarks` with the URL.
    *   Save the returned metadata to your DB immediately.
    *   If `content` is missing (async processing), schedule a background worker to fetch `GET /bookmarks/{id}` after a few seconds to get the full HTML.