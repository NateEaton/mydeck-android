Here is the consolidated technical documentation focusing specifically on the **Add Bookmark** and **Synchronization** algorithms, incorporating the findings regarding API errors and response handling.

***

# Readeck Native Client: Data Ingestion & Sync Strategy

## Context: User Prompts Summary
The following design decisions were derived from an analysis of the Readeck web client behavior and API troubleshooting:

1.  **Adding Bookmarks:** *Does the web UI use the sync endpoint for adding? How should the native app handle the server response to populate the local database?*
2.  **Sync Algorithm:** *Refining the sync logic based on the specific `GET` (diff) and `POST` (content) endpoints.*
3.  **Error Troubleshooting:** *Investigating `500 Internal Server Errors` when calling sync endpoints (specifically regarding timestamp formatting and payload structure).*

---

## 1. "Add Bookmark" Algorithm
**Objective:** Save a URL to the server and make it available offline immediately, handling potential delays in server-side content parsing.

**Endpoint:** `POST /api/bookmarks`

### Implementation Steps

1.  **Optimistic UI:** 
    *   Immediately show a placeholder card in the list with the URL as the title and a "Saving..." indicator.

2.  **Network Request:**
    *   Send Payload: `{ "url": "https://example.com/article" }`

3.  **Response Handling:**
    *   The API returns the created `Bookmark` JSON object.
    *   **Critical Check:** Verify if the `content` (or `article_html`) field is populated. Readeck attempts to parse immediately, but complex sites may timeout the parser while still creating the database entry.

4.  **Local Database Upsert:**
    *   **Scenario A: Content is Present (Success):**
        *   Save the complete object (Metadata + HTML Content) to the local database.
        *   *Asset Task:* Extract `thumbnail_url`. Enqueue a background job to download the image to local disk and update the local DB record with the `file://` path.
    *   **Scenario B: Content is Empty/Pending:**
        *   Save the **Metadata Only** (ID, Title, URL) to the local database so the user sees the item in their list.
        *   *Recovery Task:* Schedule a background job (e.g., WorkManager) to call `GET /api/bookmarks/{id}` after 5–10 seconds. This second call will retrieve the parsed content once the server finishes processing.

5.  **UI Feedback:**
    *   Replace the "Saving..." placeholder with the actual data from the local database.

---

## 2. Synchronization Engine (Offline-First)
**Objective:** Efficiently keep the local database in sync using a "Diff then Fetch" strategy to minimize bandwidth.

**Trigger:** App Start / Pull-to-Refresh / Periodic Background Work.

### Phase 1: The Diff Check (Get Changes)
**Endpoint:** `GET /api/bookmarks/sync?since={timestamp}`

*   **Logic:** Retrieve a lightweight list of items that have changed since the last sync.
*   **The "500 Error" Fix (Timestamp Formatting):**
    *   The Readeck server (Go/SQLite) is strict about timestamp formats. Sending milliseconds (e.g., `.123Z`) often causes a generic `500 Internal Server Error`.
    *   **Requirement:** Format the timestamp to UTC seconds only.
    *   *Correct:* `2023-10-27T10:00:00Z`
    *   *Incorrect:* `2023-10-27T10:00:00.000Z`

### Phase 2: Reconciliation
The server returns a list of status objects: `[{ "id": 1, "status": "updated" }, { "id": 2, "status": "deleted" }]`.

1.  **Process Deletions:**
    *   Filter for `status == "deleted"`.
    *   Delete these IDs from the local database immediately.

2.  **Process Updates:**
    *   Filter for `status == "updated"`.
    *   *Conflict Optimization:* Compare server `dt` vs. local `updated_at`. If the local record is newer (e.g., user just modified it offline), you may choose to skip this ID to preserve local changes, or implement a "Last Write Wins" strategy.
    *   Collect the remaining IDs into a list: `[10, 15, 20]`.

### Phase 3: Bulk Content Download
**Endpoint:** `POST /api/bookmarks/sync`

*   **The "500 Error" Fix (Empty Payload):**
    *   **Never** call this endpoint with an empty list. It may cause a server-side SQL syntax error (`IN ()`).
    *   *Check:* If the list of IDs from Phase 2 is empty, stop here.

*   **Payload Structure:**
    *   Must be wrapped in an object: `{ "ids": [10, 15, 20] }`.

*   **Stream Handling:**
    *   This endpoint returns a **Data Stream** (NDJSON or concatenated JSON), not a standard JSON array.
    *   **Android Client:** Do not use a standard `List<Bookmark>` return type in Retrofit. Use `ResponseBody` and parse the stream line-by-line or chunk-by-chunk to avoid timeouts or parsing errors on large payloads.

### Phase 4: Finalize
1.  **Upsert:** Insert/Update the fetched bookmarks into the local database.
2.  **Assets:** Download new thumbnails for the updated bookmarks.
3.  **State:** Update the local `last_sync_timestamp` to the current server time.