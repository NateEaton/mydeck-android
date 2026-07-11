# OAuth callback scheme — distinct per non-production flavor (mini-spec)

**Status:** Implemented (branch `fix/oauth-snapshot-callback-scheme`). Ported from Readeck
for Android (`docs/specs/oauth-snapshot-callback-scheme-spec.md`).

---

## Problem

With more than one build installed side by side (e.g. a **snapshot** plus the **production**
install), completing browser-based OAuth sign-in (Authorization Code + PKCE) pops an Android
**"Open with" app-chooser** on the redirect back from the browser tab, and can route to the
wrong app — instead of returning straight to the app that started the flow.

Only visible once two builds coexist, so it wasn't caught until snapshot + release were both
installed with the new OAuth flow.

## Root cause

MyDeck's flavors have distinct `applicationId`s so they coexist as separate apps, but they all
inherited the **same** OAuth callback scheme+host from `defaultConfig` (`mydeck://oauth-callback`).
Each therefore registered an identical `VIEW`/`BROWSABLE` intent-filter for that URI, so the
browser redirect had multiple claimants. Custom schemes carry no verification (unlike verified
App Links), so Android cannot auto-pick.

The scheme+host are single-sourced in `app/build.gradle.kts` `defaultConfig` and flow, drift-free,
to all three consumers: the manifest intent-filter (`${oauthCallbackScheme}` placeholder),
`MainActivity`'s matcher (`BuildConfig.OAUTH_CALLBACK_SCHEME/HOST`), and the registered
`redirect_uri` (`OAuthAuthorizationCodeUseCase`, built from the same `BuildConfig` values).

## Change

Give every **non-production** flavor a distinct scheme by overriding the placeholder +
`buildConfigField` in its flavor block. `githubRelease` (the production install) keeps the bare
`mydeck` scheme so existing users are unaffected. Host stays `oauth-callback` throughout.

| Flavor | applicationId | scheme |
|---|---|---|
| `githubRelease` | `com.mydeck.app` | `mydeck` (unchanged) |
| `githubReleaseHttp` | `com.mydeck.app.permissive` | `mydeck-permissive` |
| `githubSnapshot` | `com.mydeck.app.snapshot` | `mydeck-snapshot` |
| `githubSnapshotHttp` | `com.mydeck.app.snapshot.permissive` | `mydeck-snapshot-permissive` |

No two coexisting `applicationId`s share a scheme, so each redirect resolves to exactly one app.

## Why it's safe (no server change)

The `redirect_uri` is registered **dynamically per sign-in** — the app registers an ephemeral
OAuth client with `redirect_uris = [that URI]`, and Readeck honors whatever it is given (the
server creates a fresh client per registration and only ever redirects to the request's own
validated `redirect_uri`; confirmed against the server source). A per-flavor redirect URI is
simply registered as-is. Pure client build-config change.

## Verification

- Build + install a non-production build alongside the production install; run browser OAuth
  sign-in on it and confirm it returns directly to that app with **no** app-chooser and **no**
  wrong-app.
- `githubRelease` sign-in is unchanged.
- `:app:testDebugUnitTestAll` + `:app:lintDebugAll` + `:app:assembleDebugAll` green.

### GOTCHA — this change requires a CLEAN build (`./gradlew clean`)

`OAuthAuthorizationCodeUseCase.REDIRECT_URI` is `"${BuildConfig.OAUTH_CALLBACK_SCHEME}://…"`,
which the Kotlin compiler **constant-folds**. When the scheme changes, an *incremental* build
regenerates the manifest and the `BuildConfig` field but may **not** recompile the use case,
leaving the OLD scheme folded into `REDIRECT_URI`. The result is a build that **listens** on the
new scheme but **sends** the old `redirect_uri` — the redirect then routes to the production app,
and a clean *reinstall* does not help because the wrong scheme is baked into the APK. This is
exactly how the first Readeck snapshot of this fix behaved (diagnosed by dex inspection).

- Always build these variants for this change with `./gradlew clean` first (or wipe `app/build`).
  A CI/fresh-checkout build is inherently clean and unaffected.
- Verifying the merged manifest + `BuildConfig` field is **not sufficient** — check the *compiled*
  `REDIRECT_URI`:
  `unzip -p <apk> 'classes*.dex' | strings | grep -E 'mydeck[a-z-]*://oauth-callback'`
  must show only the flavor's own scheme, never the bare `mydeck://`.
- `githubRelease` is immune (its scheme never changes).
