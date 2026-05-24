# Spec: Release Default to HTTPS-Only, with Optional Permissive Variant

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
release page, and (b) a distinct, clearly labeled artifact for users who need HTTP.

### Why this matters

HTTP support was added specifically for a self-hosted user running Readeck behind
Tailscale. That use case is real, but it is not the majority case, and shipping
HTTP-permissive as the default means every user — including the ones connecting to
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
HTTP, or installations without proxy access. The permissive build serves those.

## Goals

- The default public release artifact accepts `https://` URLs only.
- The HTTP-permissive build remains available as a clearly labeled, separately
  downloaded artifact for users who need it.
- The in-app warning, when shown in the permissive build, points users at the safer
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

### Permissive build artifact

`release.yml` produces two APKs per release:

1. `MyDeck-<version>.apk` — standard, HTTPS-only.
2. `MyDeck-<version>-permissive.apk` — built with `ALLOW_INSECURE_HTTP_RELEASE=true`
   and `ALLOW_USER_CA_RELEASE=true`.

Both are signed with the same key. The release notes carry a section explaining
what the permissive variant is for and when not to use it.

Recommendation: the permissive variant uses the same `applicationId`
(`com.mydeck.app`) as the standard variant so users can replace one with the other
in place. The alternative — `applicationIdSuffix = ".permissive"` — allows
side-by-side installation but forces re-authentication when switching, and creates
two visible app entries for what is effectively the same product. Same-ID
in-place replacement is the better default; the trade-off is documented.

### In-app warning copy (permissive build)

When the permissive build sees an `http://` URL at the URL-entry screen, the
inline warning is updated to:

1. Explain the cleartext risk in one sentence.
2. Link to documented alternatives:
   - Tailscale Serve (for Tailscale users)
   - Readeck's reverse-proxy + Forwarded Authentication docs (for Readeck 0.22+
     users behind any proxy)
3. Identify itself as the permissive build, so the user understands they are in
   the opt-in distribution path.

The standard build never shows this warning because `http://` cannot be entered.

### Variant identification at runtime

A `BuildConfig.IS_PERMISSIVE_BUILD` constant (derived from the existing
`ALLOW_INSECURE_HTTP` and `ALLOW_USER_CA_CERTIFICATES` flags) drives:

- Variant-specific copy in the in-app warning.
- A small variant indicator on the About screen so users can confirm what they
  installed.

### Documentation updates

- `SECURITY.md`: replace the "default release allows HTTP with a warning" language
  with the new model (standard = HTTPS-only; permissive = separately downloaded
  opt-in).
- `release.yml` comments: update to match the new default and the dual-artifact
  build.
- `README.md`: add a short "Which APK do I want?" section above the download link.
- The permissive build's release-notes blurb should be templated so each release's
  notes carry it consistently.

## Migration

### Users already logged in over HTTP on the existing standard build

When they install the new standard build over the old one:

- The encrypted token remains on device.
- The next network request to their `http://` server URL is blocked by the network
  security config.
- The app sees a network failure on first sync.

This case must be handled cleanly. Proposed handling:

- On startup, if the saved URL has an `http://` scheme and the build is not
  permissive, route the user to a dedicated screen that explains the situation and
  offers two paths: download the permissive APK, or reconfigure the server for
  HTTPS (with a link to Tailscale Serve and Readeck-proxy docs).
- Do not silently clear credentials. Do not loop on retry. The screen is the
  endpoint until the user takes action.

This screen ships in the same release as the default flip.

### Users on the permissive build

No change. Their URL still works.

## Open questions

1. **Distribution channel for the permissive APK.** GitHub Releases is
   straightforward (a second artifact in the release). F-Droid would require a
   separate package or flavor. Recommendation: GitHub Releases only for the
   permissive variant; F-Droid carries only the standard build.
2. **Should the welcome/login screen in the standard build mention the permissive
   variant exists?** Likely yes, in fine print, so users hitting the blocked-HTTP
   path do not conclude the app is broken.
3. **Should existing HTTP users be offered an in-app one-click "switch to
   Tailscale Serve URL" guide?** Out of scope for v1; a future polish if demand
   warrants.

## Code touch points

- `app/build.gradle.kts` — default flip; optional `IS_PERMISSIVE_BUILD`
  buildConfigField.
- `.github/workflows/release.yml` — second build step + artifact upload + release
  notes templating.
- `SECURITY.md`, `README.md` — copy updates.
- `app/src/main/res/values*/strings.xml` — updated warning copy and new
  variant-specific strings (English placeholders in all locales per project
  convention).
- `app/src/main/java/com/mydeck/app/ui/welcome/WelcomeScreen.kt` and/or
  `AccountSettingsScreen.kt` — warning text and link rendering.
- New screen for "HTTP URL no longer supported in this build" — wired from
  `MainActivity` startup check on saved-URL scheme.
- `app/src/main/java/com/mydeck/app/ui/about/AboutScreen.kt` — variant indicator.

## Out of scope

- Replacing the OAuth flow.
- Enforcing certificate pinning.
- Removing the `ALLOW_USER_CA_RELEASE` build flag (it remains independently
  toggleable for private-CA users).
- Telemetry on which variant users run. MyDeck has no telemetry and this spec
  introduces none.
