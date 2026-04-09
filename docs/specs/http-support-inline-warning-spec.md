# HTTP Server URL Support — Inline Warning

## Context

MyDeck previously required an `https://` server URL in release builds. A user requested
HTTP support for a self-hosted Readeck instance accessed over Tailscale, which provides
WireGuard-based network-layer encryption — making HTTP traffic over Tailscale functionally
comparable to HTTPS in terms of confidentiality.

The infrastructure for HTTP support already existed in the codebase (network security configs,
`ALLOW_INSECURE_HTTP` build flag, `isValidUrl(allowHttp)` logic) but was gated off in release
builds by a build flag that defaulted to `false`.

## Design Decisions

### Two-layer approach

**Build gate** and **inline UI warning** serve different purposes and are kept both:

- **Build gate** (`allowInsecureHttpRelease` / `ALLOW_INSECURE_HTTP_RELEASE`): a
  distribution-policy control. Operators forking MyDeck for environments that mandate HTTPS
  can set this to `false` to prohibit HTTP entirely at the binary level — no UI required.
- **Inline warning**: user-awareness in the distributed APK. When the gate is open and a
  user enters an `http://` URL, a non-blocking advisory appears below the URL field explaining
  the tradeoff.

### Default change

The `allowInsecureHttpRelease` default is flipped from `false` to `true`. The standard GitHub
release APK now permits HTTP connections. The build gate is documented for operators who need
to enforce HTTPS in custom builds.

### Warning placement

An inline warning in the `supportingText` slot below the URL field is preferred over:
- A checkbox (ReadeckApp's approach): adds persistent state, inverted interaction model,
  clutters the auth screen for the majority who use HTTPS.
- A modal dialog: interrupts the connect flow; annoying on repeat visits.

The inline warning appears immediately as the user types an `http://` URL, is non-blocking,
and disappears when the URL is changed to `https://`. It is visible in both the welcome screen
and the Account settings screen.

## Changes

### 1. `app/build.gradle.kts`

Add a `default: Boolean = false` parameter to `booleanBuildFlag()` and pass `default = true`
for `allowInsecureHttpRelease`. This flips the release default while preserving opt-out via
`allowInsecureHttpRelease=false` (Gradle property) or `ALLOW_INSECURE_HTTP_RELEASE=false`
(env var).

### 2. `AccountSettingsViewModel.kt`

Add `urlWarning: Int?` to `AccountSettingsUiState`. In `validateUrl()`, set `urlWarning` to
`R.string.account_settings_url_http_warning` when:
- `BuildConfig.ALLOW_INSECURE_HTTP` is `true`, and
- the URL is valid, and
- the URL scheme is `http://`

Clear `urlWarning` in all other cases. The warning does not affect `loginEnabled`.

### 3. `WelcomeScreen.kt` and `AccountSettingsScreen.kt`

Extend the `supportingText` lambda on the URL `OutlinedTextField` to show either:
- the existing `urlError` (field in error state), or
- the new `urlWarning` (field valid but HTTP detected, normal supporting-text style)

`isError` remains tied to `urlError` only.

### 4. String resources

Add `account_settings_url_http_warning` to `values/strings.xml` and as an English placeholder
to all nine language files.

### 5. Documentation

- **`README.md`** — update Building section: HTTP is now allowed in standard release builds;
  the build flag can be used to prohibit it in custom builds.
- **`SECURITY.md`** — update Network Security section: document the new default, the two-layer
  approach (build gate + inline warning), and the opt-out path.
- **`getting-started.md`** — update Connecting to Your Server: note that `http://` URLs are
  supported with an in-app warning, call out Tailscale as the primary use case.
