# SQLite ORDER BY Error in GET /api/bookmarks/sync Endpoint

## Summary

The `GET /api/bookmarks/sync` endpoint returns HTTP 500 Internal Server Error when using SQLite as the database backend. The issue is caused by invalid SQL syntax in the ORDER BY clause of a UNION query.

## Environment

- **Database:** SQLite3
- **Error Message:** `1st ORDER BY term does not match any column in the result set`
- **Affected Endpoint:** `GET /api/bookmarks/sync?since={timestamp}`

## Reproduction Steps

1. Set up Readeck instance with SQLite database
2. Enable debug logging
3. Create at least one bookmark
4. Call `GET /api/bookmarks/sync?since=2026-01-30T06:43:35.942Z`
5. Observe HTTP 500 error with the message above in server logs

## Actual Server Error

```json
{
  "time": "2026-01-30T06:46:04.676679704Z",
  "level": "ERROR",
  "msg": "server error",
  "@id": "16fc039d/f3bb-00000016",
  "err": "1st ORDER BY term does not match any column in the result set"
}
```

## Root Cause

**File:** `internal/bookmarks/routes/api_sync.go`
**Function:** `bookmarkSyncList`

The issue is in how the ORDER BY clause is constructed for the UNION query:

```go
ds = ds.Union(
    db.Q().
        From(goqu.T(db.TableBookmarkRemoved).As("r")).
        Select(
            goqu.C("uid").Table("r"),
            goqu.C("deleted").Table("r").As("time"),
            goqu.V("delete").As("type"),
        ).
        Where(
            goqu.C("user_id").Table("r").Eq(auth.GetRequestUser(r).ID),
            exp.DateTime(goqu.C("deleted").Table("r")).
                Gte(exp.DateTime(since)),
        ),
)

// BUG: Wrapping the alias in exp.DateTime() is invalid in SQLite
ds = ds.Order(exp.DateTime(goqu.C("time")).Desc())
```

The generated SQL looks like:

```sql
SELECT uid, updated AS time, 'update' AS type FROM bookmarks WHERE ...
UNION
SELECT uid, deleted AS time, 'delete' AS type FROM bookmark_removed WHERE ...
ORDER BY DateTime(time) DESC  -- ❌ SQLite rejects this
```

**The problem:** SQLite does not allow function calls (like `DateTime()`) on column aliases in the ORDER BY clause of a UNION query. The column must be referenced directly.

**Why it works with PostgreSQL:** PostgreSQL is more permissive with function-wrapped aliases in ORDER BY clauses. This is why the bug wasn't caught in typical production environments.

## The Fix

**Change this line:**
```go
ds = ds.Order(exp.DateTime(goqu.C("time")).Desc())
```

**To:**
```go
ds = ds.Order(goqu.C("time").Desc())
```

**Rationale:** The `time` column is already a datetime value (aliased from either `updated` or `deleted` columns), so wrapping it in `exp.DateTime()` is unnecessary and causes SQLite to reject the query.

## Expected Behavior

After the fix, the endpoint should return a JSON array of sync status objects:

```json
[
  {
    "id": "WMNCbaQHTjGu59qRDbWh44",
    "status": "ok",
    "time": "2026-01-30T06:43:52Z"
  },
  {
    "id": "LSqK1cWbjB9crnUYzxT4Qe",
    "status": "deleted",
    "time": "2026-01-29T15:20:10Z"
  }
]
```

## Impact

This bug prevents SQLite-based Readeck instances from using the efficient delta sync endpoint. Clients must fall back to full syncs for deletion detection, which is less efficient and increases server load.

## Workaround for Clients

Until the server is fixed, clients can:
1. Use `GET /api/bookmarks?updated_since={timestamp}` for incremental bookmark updates (this works perfectly)
2. Periodically perform full syncs to detect deletions via comparison

## Additional Notes

- This issue affects any client using the sync endpoint (Android, iOS, web extensions, etc.)
- The bug was introduced when the sync endpoint was added (likely v0.20)
- The fix is a simple one-line change with no breaking changes to the API
