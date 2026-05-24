# Spec: Release Default to HTTPS-Only, with Optional HTTP-Enabled Package

## Status

Proposal — not yet scheduled for implementation.

## Context

### Current state (v0.13.1)

`app/build.gradle.kts` defaults `ALLOW_INSECURE_HTTP_RELEASE` to `true` for release
builds. The published APK from `release.yml` therefore accepts `http://` Readeck URLs
at the URL-entry screen, with an inline warning that explains the risk but does not
block login. OAuth tokens — bearer credentials with
`bookmarks:read bookmarks:write profile:read` scope — flow over whatever scheme the
user typed.

The build infrastructure already supports an HTTPS-only release:

- `networkSecurityConfigRef()` selects `network_security_config.xml` (HTTPS-only,
  system CAs) when `allowHttp = false`.
- `ALLOW_INSECURE_HTTP_RELEASE` and `ALLOW_USER_CA_RELEASE` can be toggled via
  Gradle property or env var.

What is missing is (a) a safer default for the artifact distributed from the public
release page, and (b) a distinct, clearly labeled package for users who need HTTP.

### Why this matters

HTTP support was added specifically for a self-hosted user running Readeck behind
Tailscale. That use case is real, but it is not the majority case, and shipping
HTTP-enabled as the default means every user — including the ones connecting to
a public-facing HTTPS Readeck — gets a binary that would accept a cleartext URL if
they entered one.

A listing on readeck.org would imply some level of endorsement. The default
artifact for a listed client should be HTTPS-only.

### Why the inline warning is not sufficient on its own

The current warning informs but does not gate. A user who pastes an `http://` URL,
ignores the warning (or does not read it), and logs in has already sent their bearer
token in cleartext on whatever network the device is currently on. There is no
recoverable signal that this happened; the token is valid until revoked.

### Alternatives that exist for HTTP-only setups

Two upstream changes have reduced the need for client-side HTTP support since the
feature was added:

- **Tailscale Serve** can proxy a local HTTP service as a tailnet HTTPS URL with an
  auto-provisioned LetsEncrypt certificate
  (`tailscale serve --https=443 http://127.0.0.1:8000`). This addresses the original
  Tailscale-user motivation directly: the Readeck server stays HTTP on localhost,
  the device sees HTTPS over the tailnet, and OAuth tokens are encrypted in transit.
- **Readeck 0.22 Forwarded Authentication** enables SSO-style authentication via a
  reverse proxy. It does not encrypt traffic on its own, but pairs naturally with a
  proxy (including Tailscale Serve) that does.

Some setups will still require HTTP — users connecting directly to a tailnet IP on
HTTP, or installations without proxy access. The HTTP-enabled package serves those.

## Goals

- The default public release artifact accepts `https://` URLs only.
- The HTTP-enabled build remains available as a clearly labeled, separately
  installed package for users who need it.
- The standard and HTTP-enabled packages have separate application IDs, labels,
  filenames, workflow artifacts, and release-note sections so users cannot
  accidentally install one while trying to get the other.
- The in-app warning, when shown in the HTTP-enabled build, points users at the safer
  alternative (Tailscale Serve + HTTPS) so the HTTP path is the exception rather
  than the default.
- No silent breakage for existing users already logged in over HTTP. If their URL
  becomes unusable in the new build, the path forward is clear.

## Non-goals

- Removing HTTP support from the codebase entirely. The build flag stays.
- Changing the OAuth flow, token storage, or anything beyond network policy and
  packaging.
- Enforcing certificate pinning or otherwise restricting which HTTPS servers are
  trusted. The user-CA flag continues to exist for private-CA users.

## Design

### Default flip

In `app/build.gradle.kts`:

```kotlin
val allowInsecureHttpRelease = booleanBuildFlag(
    propertyName = "allowInsecureHttpRelease",
    envName = "ALLOW_INSECURE_HTTP_RELEASE",
    default = false  // was true
)
```

The standard release build now resolves to `network_security_config.xml`
(HTTPS-only, system CAs).

### Package identities and artifact names

Both release and snapshot workflows produce two clearly separate packages:

| Channel | Application ID | App label | APK filename | Network policy |
|---|---|---|---|---|
| Standard release | `com.mydeck.app` | `MyDeck` | `MyDeck-<version>.apk` | HTTPS only; system CAs |
| HTTP-enabled release | `com.mydeck.app.permissive` | `MyDeck HTTP` | `MyDeck-<version>-http-enabled.apk` | HTTP allowed; system + user CAs |
| Standard snapshot | `com.mydeck.app.snapshot` | `MyDeck Snapshot` | `MyDeck-<version>-snapshot.apk` | HTTPS only; system CAs |
| HTTP-enabled snapshot | `com.mydeck.app.snapshot.permissive` | `MyDeck HTTP Snapshot` | `MyDeck-<version>-snapshot-http-enabled.apk` | HTTP allowed; system + user CAs |

The HTTP-enabled package is built with `ALLOW_INSECURE_HTTP_RELEASE=true` and
`ALLOW_USER_CA_RELEASE=true`.

The separate `applicationId` is intentional. The standard package must never
update over the HTTP-enabled package, and the HTTP-enabled package must never
update over the standard package. Users can install both side by side, and each
package keeps its own encrypted credentials and local database.

### Workflow artifact separation

`.github/workflows/release.yml` uploads standard and HTTP-enabled APKs as
separate artifacts before creating the GitHub release:

- `app-release-standard-<version>` containing only `MyDeck-<version>.apk`
- `app-release-http-enabled-<version>` containing only
  `MyDeck-<version>-http-enabled.apk`

The GitHub Release body has separate sections:

1. **Recommended download** — standard HTTPS-only APK.
2. **Advanced: HTTP-enabled APK** — for trusted private-network setups that
   cannot use HTTPS.

`.github/workflows/snapshot.yml` follows the same separation for tester builds.
For each snapshot build type it uploads two artifacts, for example:

- `tester-snapshot-standard-debug`
- `tester-snapshot-http-enabled-debug`
- `tester-snapshot-standard-release`
- `tester-snapshot-http-enabled-release`

The automated `latest-snapshot` release publishes both APKs and checksums, with
the same recommended/advanced split in the release notes. The standard artifact
never contains an HTTP-enabled APK, and the HTTP-enabled artifact never contains
a standard APK.

### In-app warning copy (HTTP-enabled build)

When the HTTP-enabled build sees an `http://` URL at the URL-entry screen, the
inline warning is updated to:

1. Explain the cleartext risk in one sentence.
2. Link to documented alternatives:
   - Tailscale Serve (for Tailscale users)
   - Readeck's reverse-proxy + Forwarded Authentication docs (for Readeck 0.22+
     users behind any proxy)
3. Identify itself as the HTTP-enabled build, so the user understands they are in
   the opt-in distribution path.

The standard build never shows this warning because `http://` cannot be entered.

### Runtime network-policy identification

A `BuildConfig.IS_HTTP_ENABLED_BUILD` constant may still be useful for
artifact-specific copy, but the UI should not collapse the two security decisions
into one opaque label. `ALLOW_INSECURE_HTTP` and `ALLOW_USER_CA_CERTIFICATES` are
different risk knobs and should be surfaced independently.

The About screen shows the effective network policy as separate facts:

- Server URLs: `HTTPS only` or `HTTP allowed`
- HTTPS trust: `system certificate authorities` or `system + user certificate
  authorities`

The HTTP warning copy can still identify the installed APK as the HTTP-enabled
variant, but user-facing diagnostics and support screenshots should expose the
actual capabilities.

### Documentation updates

- `SECURITY.md`: replace the "default release allows HTTP with a warning" language
  with the new model (standard = HTTPS-only; HTTP-enabled = separately installed
  opt-in).
- `release.yml` and `snapshot.yml` comments: update to match the new default and
  the dual-package build.
- `README.md`: add a short "Which APK do I want?" section above the download link.
- The HTTP-enabled build's release-notes blurb should be templated so each
  release's notes carry it consistently.

## Migration

### Users already logged in over HTTP on the existing standard build

When they install the new standard build over the old one:

- The encrypted token remains on device. **The user does not need to log out or
  re-authenticate.** OAuth bearer tokens are not bound to URL scheme; the same
  token works against `https://server` as against `http://server`, provided the
  server is reachable on HTTPS.
- The next network request to the saved `http://` URL is blocked by the network
  security config (cleartext traffic not permitted), and the app sees a network
  failure on first sync.

What "hard stop" means here: instead of letting that network failure surface
through the normal error path (where it would look like a transient connectivity
problem and the user might retry repeatedly), the app detects the specific
condition "saved URL is `http://` on the standard HTTPS-only build" at startup
and routes to a dedicated screen explaining it.

The same guard also lives below the UI, close to the network boundary. Any code
path that can issue Readeck API calls — startup sync, WorkManager jobs, share/add
flows, settings refresh, and manual pull-to-refresh — must short-circuit before
OkHttp when the saved URL is `http://` and `ALLOW_INSECURE_HTTP == false`. This
prevents background workers from repeatedly logging opaque network-security
failures before the migration screen has a chance to explain the policy change.

That screen offers three paths in order of recommendation:

1. **Change the server URL to HTTPS** — inline edit, no re-auth needed if the
   server is reachable on HTTPS (Tailscale Serve, reverse proxy, native TLS).
   Links to the Tailscale Serve docs and Readeck reverse-proxy docs for users
   who don't already have HTTPS configured.
2. **Install the HTTP-enabled APK** — for users who genuinely cannot move to HTTPS
   (direct tailnet-IP setups, etc.). This is a separate package
   (`com.mydeck.app.permissive`), so it installs beside the standard app and
   requires OAuth device authorization again. The old standard app remains
   installed until the user removes it.
3. **Log out** — fallback only, presented last. Clears credentials and returns
   to the welcome screen. Required only if the user wants to reconfigure from
   scratch.

The screen is the endpoint until the user takes action — it does not silently
clear credentials, does not loop on retry, and does not let the user fall back
into a state where they think the app is just broken.

This screen ships in the same release as the default flip.

### Users on the HTTP-enabled build

No change. Their URL still works.

## Open questions

1. **Should the welcome/login screen in the standard build mention the HTTP-enabled
   variant exists?** Likely yes, in fine print, so users hitting the blocked-HTTP
   path do not conclude the app is broken.

## Code touch points

- `app/build.gradle.kts` — default flip; add a network-policy flavor dimension or
  equivalent variant wiring for standard vs. HTTP-enabled packages. The
  HTTP-enabled variants set an application ID suffix of `.permissive`, a distinct
  app label, and `IS_HTTP_ENABLED_BUILD=true`; keep independent
  `ALLOW_INSECURE_HTTP` and `ALLOW_USER_CA_CERTIFICATES` fields as the source of
  truth for runtime policy.
- `.github/workflows/release.yml` — build both standard and HTTP-enabled release
  packages; upload them as separate artifacts with unambiguous names; publish both
  APKs to the release with recommended/advanced release-note sections.
- `.github/workflows/snapshot.yml` — build both standard and HTTP-enabled snapshot
  packages for the selected debug/release snapshot mode; upload separate artifacts
  and publish both to `latest-snapshot` without mixing APK types in the same
  artifact.
- `SECURITY.md`, `README.md` — copy updates.
- `app/src/main/res/values*/strings.xml` — updated warning copy and new
  variant-specific strings (English placeholders in all locales per project
  convention).
- `app/src/main/java/com/mydeck/app/ui/welcome/WelcomeScreen.kt` and/or
  `AccountSettingsScreen.kt` — warning text and link rendering.
- New screen for "HTTP URL no longer supported in this build" — wired from
  `MainActivity` startup check on saved-URL scheme.
- Network-boundary guard shared by foreground API calls and background workers:
  if the saved server URL is HTTP and `ALLOW_INSECURE_HTTP == false`, return a
  typed "HTTP blocked in this build" result before constructing or executing the
  request.
- `app/src/main/java/com/mydeck/app/ui/about/AboutScreen.kt` — show effective
  HTTP and certificate-authority policy separately, not only a standard/HTTP-enabled
  variant badge.

## Out of scope

- Replacing the OAuth flow.
- Enforcing certificate pinning.
- Removing the `ALLOW_USER_CA_RELEASE` build flag (it remains independently
  toggleable for private-CA users).
- Telemetry on which variant users run. MyDeck has no telemetry and this spec
  introduces none.
