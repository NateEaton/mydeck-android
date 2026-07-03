# Spec: Server Compatibility Error Messaging

## Background

When a user enters a Readeck server URL and taps Connect, MyDeck already calls
`GET /api/info` before starting the OAuth device flow. The response includes a
`features` list; the app already checks for `"oauth"` in that list (see
`UserRepositoryImpl.initiateLogin`). However, the current error messages in that
path are generic and developer-facing, not user-friendly. This spec tightens the
three failure modes into clear, actionable messages.

## Server version reference

| Version | Significance |
|---------|-------------|
| **0.20** | First version to include `GET /api/info` |
| **0.21** | First version to support OAuth — minimum required to use this app |
| **0.22** | OAuth mandatory; username/password authentication removed |
| **0.22.3** | Current stable release |

Since `/api/info` (0.20) predates OAuth support (0.21), both failure modes below
are reachable in practice:

- Servers in the **0.20.x** range have `/api/info` but lack `"oauth"` in
  `features` → failure mode 2 (clean, JSON-parseable rejection).
- Servers **older than 0.20** have no `/api/info` route at all → failure mode 3
  (HTML response, deserialization exception).

## The `/api/info` contract

`GET /api/info` is an unauthenticated public endpoint. Its response includes:

```json
{
  "version": {
    "canonical": "0.23.1-175-g154ad5c1",
    "release":   "0.23.1",
    "build":     "175-g154ad5c1"
  },
  "features": ["email", "oauth"]
}
```

`features` explicitly advertises whether the server supports OAuth. No version
parsing is required — the presence or absence of `"oauth"` in the list is the
authoritative signal.

## Failure modes and proposed messages

### 1. `/api/info` call fails (network error, non-200, or empty body)

**Current message:** "Failed to read server capabilities. Please verify the
server URL and try again."

**Proposed message:** No change needed — this is already clear and actionable.

---

### 2. `/api/info` succeeds but `features` does not contain `"oauth"`

This applies to servers in the **0.20.x** range: they have `/api/info` but
predate OAuth support (0.21).

**Current message:** "This server does not support OAuth authentication."

**Proposed message:**

> This Readeck server (version **X.Y.Z**) does not support the authentication
> method required by this app. Please update your server to version **0.21 or
> later**.

The server's `version.release` is available from the `info` object at the point
the feature check runs, so it can be interpolated directly into the message.

---

### 3. `/api/info` returns HTML instead of JSON

This is the confirmed error for the user who triggered this spec. It occurs when
the server is so old it has no `/api/info` route at all, or the URL points to
something that isn't a Readeck API endpoint. Retrofit attempts to deserialize the
HTML response as `ServerInfoDto`, throws a `JsonDecodingException`, which falls
into the outer `catch` block and surfaces as:

> An unexpected error occurred: Unexpected JSON token at offset 0: Expected
> start of the object '{', but had '<' instead …

This applies to servers **older than 0.20** (no `/api/info` route exists yet).

**Proposed behaviour:** In the outer `catch` of `initiateLogin`, detect this by
checking for `SerializationException` (or its subtypes
`JsonDecodingException`/`MissingFieldException`) and return:

> Could not connect to a Readeck server at this address. If your server is
> running a version older than **0.21**, it is not supported by this app.
> Please check the URL and ensure your server is reachable and up to date.

---

## Implementation notes

- All three messages map to `LoginResult.Error` with a user-readable `message`
  string (same type used today).
- The error display in `AccountSettingsViewModel` already surfaces
  `LoginResult.Error.errorMessage` — no UI changes needed.
- For failure mode 2, the server's `version.release` is available from the
  `info` object before the feature check is made, so it can be interpolated
  into the message.
- Messages 2 and 3 are localized string resources resolved in
  `UserRepositoryImpl`. Because the repository is a domain-layer class with no
  `Context`, inject `@ApplicationContext Context` (the same pattern already used
  by `OAuthDeviceAuthorizationUseCase`) so the resource can be resolved to a
  string before it is placed into `LoginResult.Error`.
- String resources need entries in all language files (English placeholder for
  all, per the localization policy in `CLAUDE.md`). Package: `com.mydeck.app`.
