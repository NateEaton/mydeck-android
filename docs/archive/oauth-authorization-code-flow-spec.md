# OAuth Authorization Code Flow — Technical Spec

Status: Implemented (shipped in PR #221, `feat/oauth-auth-code`)
Related archives: `docs/archive/TECH_SPEC_OAuth_Device_Code_Authentication.md`, `docs/archive/IMPLEMENTATION_CODE_OAuth_Device_Code.md`
Authoritative server contract: `docs/openapi-spec.json`

## 1. Goal

Add OAuth 2.0 **Authorization Code + PKCE** as the primary, browser-based sign-in path,
while keeping the existing **Device Code** flow in the tree as a user-selectable fallback
("sign in with a code instead") for browserless / input-constrained devices (e-readers, etc.).

The token we ultimately obtain and everything downstream of it is unchanged — this feature
only replaces *how the access token is acquired* in the common case.

## 2. Server support is already present

The bundled `docs/openapi-spec.json` confirms the Readeck server already implements the full
authorization-code + PKCE flow. **This feature is not blocked on CIDM or any server change.**
CIDM (Olivier's interest) is a separate future concern.

Confirmed contract details (source of truth — supersedes the independent assessment where they differ):

- **Authorization endpoint lives at the server ROOT: `GET /authorize`** — *not* under `/api`.
  The app stores the server URL with a `/api` suffix, so the browser URL is built as
  `storedUrl.removeSuffix("/api") + "/authorize"`. (Same `removeSuffix("/api")` pattern already
  used in `AccountSettingsViewModel`.)
- **Authorize query params:** `client_id`, `redirect_uri`, `scope`, `code_challenge`,
  `code_challenge_method=S256`, `state`.
- **Redirect result (success):** `code`, `state`. **(error):** `error` (`invalid_request` or
  `access_denied`), `error_description`, `state`.
- **Custom schemes are explicitly allowed as redirect URIs.** Spec: redirect URIs must be
  "any https URI, http loopback, *or any other app link scheme (ie `my-app.org:/callback`)*."
  So `mydeck://oauth-callback` is sanctioned.
- **`redirect_uris` is REQUIRED at client registration when `grant_types` contains
  `authorization_code`**, and the `redirect_uri` sent to `/authorize` must match a registered one
  exactly.
- **Token endpoint stays `POST /api/oauth/token`.** Request body is a `oneOf`. The
  `authorization_code` branch requires exactly `grant_type` + `code` + `code_verifier` and takes
  **no `client_id`** (the device-code branch does take `client_id`). `code` maxLength 2048,
  `code_verifier` maxLength 256. Response DTO unchanged (`access_token`, `token_type`, `scope`).
- **PKCE:** S256 only ("plain" rejected). Verifier is a random string; challenge is
  base64url(SHA-256(verifier)) **with URL encoding, without padding**. Server's own example uses a
  64-char alphanumeric verifier.
- Clients are ephemeral (valid 10 minutes) — register a fresh client for each flow, exactly as the
  device-code path already does.

## 3. Redirect mechanism (decision recorded)

- **MyDeck: always custom scheme** — `mydeck://oauth-callback`. We do not control users'
  self-hosted Readeck domains, so verified https App Links are not an option for MyDeck.
  PKCE + `state` make the custom scheme acceptable per RFC 8252 (an interceptor would get the code
  but not the verifier).
- **Readeck Android (port): pluggable.** Eventually the Readeck platform may host
  `/.well-known/assetlinks.json`, enabling verified https App Links against the Readeck domain.
  Design the redirect handling so the redirect URI + intent-filter are driven by `BuildConfig`
  / `manifestPlaceholders`, not hardcoded, so the port can swap custom scheme → App Links without
  touching flow logic.

**Scheme form:** a single literal `mydeck://oauth-callback` is simplest. Because client
registration is ephemeral and per-device, each installed build registers only *its own*
`redirect_uri`, so a per-flavor scheme (via `manifestPlaceholders`) is also viable and avoids two
side-by-side installs (e.g. snapshot + release) both claiming the same scheme and triggering
Android's disambiguation dialog. Default to the single literal unless side-by-side installs prove
annoying in practice.

## 4. Architecture / changes

### 4.1 Data layer (`io/rest/model`, `io/rest/ReadeckApi`)

- **`OAuthClientRegistrationRequestDto`** — add `redirect_uris: List<String>` and include
  `"authorization_code"` in `grant_types` (alongside device_code) when registering for the
  auth-code flow.
- **New `OAuthAuthCodeTokenRequestDto`** — `grant_type` (`"authorization_code"`), `code`,
  `code_verifier`. Do **not** reshape the existing `OAuthTokenRequestDto` to nullable fields —
  a dedicated DTO keeps the device-code path clean and avoids interaction with the global
  `Json(explicitNulls=false)` config.
- **`ReadeckApi`** — add a second token method, e.g.
  `suspend fun requestTokenWithAuthCode(@Body body: OAuthAuthCodeTokenRequestDto): Response<OAuthTokenResponseDto>`
  (Retrofit can't overload on `@Body` type alone; use a distinct method name). Response type is the
  unchanged `OAuthTokenResponseDto`.
- **`OAuthTokenResponseDto` / `OAuthErrorDto`** — unchanged.

### 4.2 PKCE + URL utilities (new)

- `PkceUtil` (or add to `util/`): `generateCodeVerifier()` using `SecureRandom` (43–128 char
  unreserved charset; 64-char alphanumeric is fine), `codeChallenge(verifier)` =
  base64url-no-pad SHA-256.
- `generateState()` — random CSRF token.
- Authorize-URL builder: `buildAuthorizeUrl(serverUrlWithApi, clientId, redirectUri, scope, challenge, state)`
  → strips `/api`, appends `/authorize`, URL-encodes params.

### 4.3 Redirect plumbing (`AndroidManifest.xml`, `MainActivity`)

- Add an `<intent-filter>` (BROWSABLE/DEFAULT + `<data android:scheme="..." android:host="..."/>`)
  driven by a manifest placeholder for the scheme/host — mirror the existing `ShareActivity`
  filter pattern. Attach it to `MainActivity` (or a thin routing activity).
- Set `MainActivity` `launchMode="singleTop"`; handle the redirect in `onNewIntent`.
- Parse `code` / `state` / `error` / `error_description` and emit to the VM via an event channel
  (SharedFlow / Channel), so the redirect result reaches the in-progress login.

### 4.4 Use case (new: `OAuthAuthorizationCodeUseCase`)

Mirror the structure of `OAuthDeviceAuthorizationUseCase`:

- `initiateAuthorization(serverUrl): Result` — register client (with `redirect_uris` +
  `authorization_code`), generate verifier/challenge/state, build authorize URL, return the URL to
  launch plus the values to persist.
- `exchangeCode(code, verifier): TokenResult` — `POST /api/oauth/token` via
  `requestTokenWithAuthCode`, map success → access token, map `invalid_grant` / other errors.
- Reuse the existing `parseOAuthError`, `isHttpBlockedByBuildPolicy`, and error taxonomy.

### 4.5 Process-death persistence

Android may kill the app while the Custom Tab is foregrounded. Persist `code_verifier`, `state`,
`client_id`, and the target `serverUrl` in **`SavedStateHandle`** (not plain in-memory VM state, which
the device-code path relied on). On redirect, restore, validate `state` matches, then exchange.

### 4.6 ViewModel / UI (`AccountSettingsViewModel`, login UI)

- Replace the `startLogin()` → `startPolling()` while-loop with a one-shot flow:
  initiate → launch Custom Tab (reuse `openUrlInCustomTab` in `util/Utils.kt`) → await redirect
  event → validate `state` → `exchangeCode` → `completeLogin(url, token)` (unchanged).
- New UI states: browser launched / awaiting redirect / user denied or cancelled in browser /
  redirect never arrived (timeout).
- **Fallback affordance:** add a visible secondary action on the login screen — "Sign in with a code
  instead" — that routes to the existing `DeviceAuthorizationDialog` / device-code path (kept
  in-tree, not deleted).

### 4.7 Unchanged (confirmed)

`SettingsDataStore` token storage, `AuthInterceptor`, `completeLogin`, logout / `POST /oauth/revoke`
— all grant-type agnostic.

## 5. Flow (happy path)

1. User enters instance URL, taps Sign in.
2. App registers ephemeral client (authorization_code + device_code grants, redirect_uris=[our URI]).
3. App generates verifier/challenge/state, persists them (SavedStateHandle), builds `/authorize` URL.
4. App opens the authorize URL in a Custom Tab.
5. User authenticates + approves in the browser; server redirects to `mydeck://oauth-callback?code=...&state=...`.
6. `MainActivity.onNewIntent` parses params → VM validates `state` → `POST /api/oauth/token`
   (grant_type=authorization_code, code, code_verifier).
7. Server returns `access_token` → `completeLogin(url, token)` → profile fetch → done.

Error branches: user denies (`access_denied`), `state` mismatch (abort, show error), redirect never
arrives (timeout → allow retry or fall back to code entry), network / HTTP-blocked-by-build-policy
(reuse existing handling).

## 6. Compliance with project requirements

- **Strings:** any new UI strings added to `values/strings.xml` AND all 9 language files as English
  placeholders (see root `CLAUDE.md`).
- **User guide:** update `app/src/main/assets/guide/en/getting-started.md` to describe the new
  browser-based sign-in, and mention the "sign in with a code instead" fallback.
- **Changelog:** update `## [Unreleased]` in `CHANGELOG.md`.
- **Verification:** `./gradlew :app:assembleDebugAll :app:testDebugUnitTestAll :app:lintDebugAll`,
  plus device verification via `./scripts/install-phone.sh`.

## 7. Phased plan

- **Phase 0 — Live-server verification (de-risk first).** Manually register a throwaway client with
  `authorization_code` + a redirect_uri, open `/authorize` in a browser against the real instance,
  confirm redirect + token exchange. Validates the root-vs-`/api` path and scheme handling before
  building UI.
- **Phase 1 — Data/API.** Registration DTO change; `OAuthAuthCodeTokenRequestDto` + Retrofit method;
  PKCE util; authorize-URL builder. Unit tests.
- **Phase 2 — Redirect plumbing.** Manifest intent-filter (placeholder-driven); MainActivity
  singleTop + onNewIntent; event channel.
- **Phase 3 — Use case.** `OAuthAuthorizationCodeUseCase`; SavedStateHandle persistence;
  state/error validation. Unit tests (hand-written fake repo — MockK can't mock suspend funs
  returning Kotlin `Result`).
- **Phase 4 — VM/UI.** One-shot login flow; new states; "sign in with a code instead" fallback entry.
- **Phase 5 — Strings (10 files) / guide / changelog.**
- **Phase 6 — Verify** (assembleDebugAll / testDebugUnitTestAll / lintDebugAll) + device test.

## 8. Port to Readeck Android (rc6 / v1.0.0)

Self-contained; ports via `docs/porting/mydeck-readeck-port.md`. Deltas: redirect scheme/host,
`client_uri` / `client_name` / `software_id`, string keys. Server contract is identical, so flow
logic ports 1:1. Keep the redirect URI + intent-filter `BuildConfig`/placeholder-driven so Readeck
can later switch custom scheme → server-hosted verified App Links without touching flow logic.

## 9. Open items / to verify in Phase 0

- Exact behavior of `/authorize` when the user is not yet logged into the web session (login then
  consent) inside a Custom Tab, and that it redirects cleanly to the custom scheme.
- Whether any deployed server versions we support lack the authorize endpoint (gate on the existing
  server OAuth-capability check if needed).
- Final decision on single-literal vs per-flavor scheme.
