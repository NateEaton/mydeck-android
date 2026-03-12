# Security Hardening Workstream Summary

## Goal

This workstream hardened MyDeck's release posture for Android install security scanning while preserving a practical path for self-hosted Readeck users who may need less strict networking in custom builds.

The main objectives were:

- secure official release builds by default
- reduce risk from local backup/restore of sensitive state
- reduce risk from the in-app original-page WebView
- remove obsolete pre-OAuth authentication code
- document the user-visible behavior changes

## Initial Findings

The initial security review identified four primary issues:

1. **Release networking was too permissive**
   - The app-wide network security config allowed cleartext HTTP.
   - The app also trusted user-installed CAs globally.
   - This is a common Android security scan finding because it weakens transport security for login, sync, and page loading.

2. **Backup posture was too broad**
   - `allowBackup` was enabled.
   - The backup/data extraction rule files were effectively placeholders.
   - Sensitive local state included bearer token storage, server URL, cached sync state, local bookmark metadata, and article content.

3. **Original-page WebView used JavaScript by default**
   - Reader/extracted HTML is sanitized via Readeck extraction.
   - The higher-risk surface was the "original page" mode, which loads arbitrary bookmark URLs into a WebView.
   - JavaScript was enabled there even though MyDeck itself was not depending on JS for its own UI controls.

4. **Legacy pre-OAuth authentication code was still present**
   - The live auth flow is OAuth device flow.
   - Older username/password-era API models and storage structure were still present in the codebase.

## Hardening Changes Implemented

### 1. Secure-by-default release networking

Official release builds now default to:

- HTTPS only
- system CAs only

Debug builds remain permissive for development.

Custom release builds can opt into weaker networking via build-time flags:

- `allowInsecureHttpRelease` / `ALLOW_INSECURE_HTTP_RELEASE`
- `allowUserCaRelease` / `ALLOW_USER_CA_RELEASE`

This preserves a secure official distribution while still allowing self-hosters to build an APK that supports plain HTTP or private/user-installed CAs when necessary.

### 2. Backup reduced to non-sensitive UI preferences only

Backup and device-transfer rules were tightened to allow only non-sensitive UI preferences.

Sensitive state is no longer included in backup:

- auth/session info
- encrypted preferences
- server metadata
- local bookmark/content database
- sync timestamps and related internal state

To support this cleanly, non-sensitive UI settings were split into a dedicated `user_preferences.xml` store, while auth/session/server state remains in encrypted preferences.

### 3. Original-mode WebView hardened

JavaScript and DOM storage were disabled for original-page mode in the in-app WebView.

This keeps the WebView available for simple page viewing while reducing the attack surface for arbitrary third-party pages loaded in original mode.

The user guide was updated to advise users to open the page in their external browser if a JS-heavy page does not behave correctly in web view mode.

### 4. Obsolete pre-OAuth code removed

The following legacy pieces were removed:

- deprecated old auth API models
- deprecated authentication endpoint declaration
- password-based storage remnants from the earlier auth model

Authentication state is now aligned with the live OAuth flow:

- server URL
- username/profile metadata
- bearer token

### 5. Post-login initialization hardened

Follow-up testing uncovered a brittle post-login path:

- login success immediately navigated to the bookmark list
- the app marked initial sync complete before the first sync actually succeeded
- a transient failure could leave the user on an empty list even though the account had bookmarks

This was hardened as follows:

- initial sync completion is now recorded only after successful sync
- post-login navigation now waits for the specific initial sync worker to finish successfully
- transient load failures (`IOException`) now return `Result.retry()` in the bookmark load worker
- initial-sync UI state is now distinguished from a truly empty account

This does not fully solve stale-token startup bootstrap yet, but it does remove the "successful login followed by empty list after failed first sync" path.

### 6. Release workflow Node runtime warning addressed

GitHub Actions reported that some JavaScript actions in the release workflow were still on the deprecated Node 20 runtime path.

To address that warning for the release workflow, `release.yml` was updated to opt JavaScript actions into Node 24:

- `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: 'true'`

This is a CI/runtime maintenance issue and is separate from the Android app itself.

## User-Visible Functional Changes

- Official release builds require `https://` server URLs by default.
- Self-hosters who need HTTP or user-installed CA trust must build a custom APK with the appropriate build flags enabled.
- Web view mode for original pages may render fewer JS-heavy sites correctly.
- When web view mode does not behave well, users should open the page in their external browser.
- Device/app backup now preserves UI preferences only, not session/auth/content state.

## Verification Performed

The hardening work was verified with the relevant project Gradle tasks, including:

- `:app:assembleGithubReleaseDebug`
- `:app:testGithubReleaseDebugUnitTest`
- `:app:lintGithubReleaseDebug`
- `:app:assembleGithubReleaseRelease`

The default unflavored `:app:testDebugUnitTest` and `:app:lintDebug` names are ambiguous in this flavored project, so explicit flavor tasks were used where needed.

## Addendum: Remaining Stale-Token Startup Issue

### What remains

One session-bootstrap issue still remains conceptually separate from the hardening work above:

- app startup still treats the presence of a stored token as enough to route directly to the bookmark list
- the bookmark list can auto-sync on startup before the app has validated that the token is still accepted by the server
- if the token is stale, revoked, or otherwise invalid, the app can still hit a startup `401`

This is distinct from the post-login initial-sync issue described earlier. The post-login empty-list path has been hardened. The stale-token bootstrap path has not yet been fully redesigned.

### Recommended resolution

The recommended fix is to introduce an explicit session bootstrap step at app launch.

Instead of:

- `token exists -> route to list -> sync`

the app should use:

- `no token/url -> welcome`
- `token/url present -> validate session`
- `valid session -> list + sync`
- `401 invalid session -> clear credentials -> welcome`
- `offline/unreachable -> optionally allow cached offline reading without running authenticated sync`

### Why this is the right follow-up

This would:

- eliminate the stale-token startup `401` path
- avoid treating cached credentials as automatically valid
- preserve the option to support offline reading intentionally rather than accidentally
- centralize session invalidation instead of relying on notification-only handling

### Recommended implementation outline

1. Add a startup auth/bootstrap state at the app shell level rather than routing based only on token presence.
2. Validate stored credentials with a lightweight authenticated request such as `/profile`.
3. Clear credentials and return to welcome immediately on `401`.
4. Gate startup sync on validated auth state, not merely stored URL/token presence.
5. Keep offline behavior explicit: if desired, allow cached reading without sync when network validation is unavailable.

## Summary

This workstream materially improved release security posture by tightening networking defaults, reducing backup exposure, hardening original-mode WebView behavior, removing obsolete auth code, and improving post-login initialization behavior.

The main unresolved follow-up is session bootstrap validation for stale stored tokens at cold start. The recommended next step is an explicit validated-auth startup flow rather than token-presence-based routing.
