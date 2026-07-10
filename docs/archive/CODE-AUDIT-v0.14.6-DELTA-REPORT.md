# Code Audit — v0.14.6 Delta Report

> **Cross-repo applicability.** This delta audit was executed this cycle against
> the sibling **Readeck for Android** working tree, but it covers **both** repos.
> Per the porting methodology in [docs/porting/](porting/) (see
> [mydeck-readeck-port.md](porting/mydeck-readeck-port.md)), the five feature PRs
> in scope were landed in both trees commit-for-commit and behaviour-identical,
> differing only in branding (app name, launcher icons, store copy,
> `applicationId`, OAuth client constants) and remotes. Every source file this
> report cites is byte-identical across the two trees except those enumerated
> branding constants, so the findings, categories, and verdict apply to **MyDeck
> verbatim**. This file is the MyDeck-authoritative record; the Readeck copy is
> `docs/archive/CODE-AUDIT-RC6-DELTA-REPORT.md`.

_Delta audit per the CODE-AUDIT.md spec (8-category slop/health sweep). Scope:
code changed since the previous release — five feature PRs: #221 (OAuth
Authorization Code + PKCE), #222 (defensive delete confirmations + OAuth
`logo_uri`), #223 (reading fonts), #224 (bottom-sheet dismissal consistency),
#226 (What's New page + release history). Method: repo-wide `rg` for cross-file
redundancy (no `jscpd`/`pmd cpd` installed for this pass — see the
duplication-gate note), plus per-diff manual review against the 8 categories.
Date: 2026-07-09._

See baseline context:
[docs/archive/CODE-AUDIT-REPORT.md](CODE-AUDIT-REPORT.md),
[docs/archive/CODE-AUDIT-REMEDIATION.md](CODE-AUDIT-REMEDIATION.md),
[docs/archive/CODE-AUDIT-v0.14.3-DELTA-REPORT.md](CODE-AUDIT-v0.14.3-DELTA-REPORT.md).

---

## 1. Verdict

**MINOR — cleared for v0.14.6.** One real, low-risk finding, remediated in this
branch. A second finding raised by the initial pass proved, on direct code
inspection, to be a false positive with a documented technical reason — recorded
below so a future auditor doesn't re-flag it.

- **[S2] OAuth client-identity constants duplicated across two use cases —
  REMEDIATED** in `chore/v0.14.6-prep-cleanup`. See §2.
- **[S3] `dismissSheet()` "not applied everywhere" — VERIFIED NOT A GAP.** See
  §2. No code change.
- Two S4 notes (a repeated version-resolve-then-load sequence in
  `AboutViewModel`/`WhatsNewViewModel`; a "Spike" comment in
  `ReaderSettingsBottomSheet.kt`) are low-priority, tracked, non-blocking
  follow-ups — not addressed here.

**Duplication-gate note (§5.1 of CODE-AUDIT.md):** no copy-paste-detection tool
was installed for this pass. Cross-file redundancy (category 2) was screened
manually via repo-wide `rg` for the new abstractions each PR introduced (OAuth
constants/use-case bodies, `dismissSheet`/`ModalBottomSheet` call sites,
`WhatsNewAssetLoader` consumers, font/typography symbols). Not the exhaustive
whole-repo coverage a CPD tool would give; category 2 is recorded as
screened-but-not-tool-verified.

---

## 2. Findings

### [S2] Redundant implementation — `OAuthAuthorizationCodeUseCase.kt` vs `OAuthDeviceAuthorizationUseCase.kt` — ✅ FIXED in this branch

```kotlin
// Both classes declared, verbatim (MyDeck branding values shown):
private const val CLIENT_URI = "https://github.com/NateEaton/mydeck-android"
private const val SOFTWARE_ID = "com.mydeck.app"
private val CLIENT_NAME get() = "MyDeck Android — ${Build.MANUFACTURER} ${Build.MODEL}"
private const val REQUIRED_SCOPES = "bookmarks:read bookmarks:write profile:read"
private fun parseOAuthError(errorBody: String?): String { /* identical body */ }
```

`OAuthAuthorizationCodeUseCase` (added in #221) duplicated
`OAuthDeviceAuthorizationUseCase`'s pre-existing client-identity constants and
error-parsing logic verbatim. The two classes register **independent** OAuth
clients, so this was copy-pasted identity/logic with no shared source of truth —
the two login flows' client identity could silently drift if one were edited
without the other.

**Resolution:** extracted `CLIENT_URI`, `SOFTWARE_ID`, `CLIENT_NAME`,
`REQUIRED_SCOPES`, and `parseOAuthError` into a new `OAuthClientConstants` object
(`app/src/main/java/com/mydeck/app/domain/usecase/OAuthClientConstants.kt`, with
MyDeck branding values); both use cases now reference it. `LOGO_URI` (branding,
auth-code flow only) and `GRANT_TYPE_DEVICE_CODE` (a protocol literal) were left
in place — out of scope for this pass. Verified: `:app:assembleDebugAll`,
`:app:testDebugUnitTestAll`, `:app:lintDebugAll` all green across all flavors
after the refactor (pure extraction, no behaviour change).

### [S3] "Inconsistent bottom-sheet dismissal" — initially flagged, VERIFIED NOT A GAP

PR #224's own commit message states the sheets left unchanged were audited: Add
bookmark and Annotation edit close via an async loading→success transition (not
an instant flag-flip); Annotations list and Reader settings close via
handle/scrim only. Direct inspection confirms the rationale still holds:

- **`AnnotationEditSheet`** (Save/Delete) and **`AddBookmarkBottomSheet`** close
  via an async `ViewModel` state transition (`isSaving = true` → network call →
  state set to `null`), not a synchronous button-tap flag-flip. `dismissSheet()`
  only wraps a composable-owned `sheetState` around a synchronous click;
  retrofitting it here would require restructuring state ownership — larger than
  this pass's scope.
- **`AnnotationsBottomSheet`** has no button-driven dismiss path (only
  scrim/swipe/back, which `ModalBottomSheet` already animates). Nothing to route.
- **`ShareActivity`** dismisses via `finish()` on the whole Activity, a
  different lifecycle event than a Composable's `sheetState.hide()`.

**No code change.** Recorded so a future delta audit doesn't re-raise the same
false positive without first checking PR #224's documented exclusions.

### [S4] Redundant implementation — `AboutViewModel` vs `WhatsNewViewModel` (not fixed, low priority)

Both resolve the current version via
`WhatsNewAssetLoader.normalizeVersion(AppVersion.versionName(context))` then call
`loader.loadNotesForVersion(...)` — the same two-call sequence repeated in two
ViewModels instead of behind one shared helper. Small, low-risk; not addressed.

### [S4] Comment slop — `ReaderSettingsBottomSheet.kt` `getFontDisplayName` (not fixed, low priority)

A "Spike" comment describes an unbuilt "real feature" (per-font string
resources) rather than justifying the shipped approach. Reads as an honest scope
note; font family names are proper nouns not typically localized. Left as-is.

---

## 3. Incidental

None with sufficient evidence to report as a real bug, separate from the slop
findings above.

> Note: a separate user-facing bug — the browser (auth-code) sign-in showing a
> placeholder username on the Settings Account row — was found during smoke
> testing (not this slop sweep) and fixed in the same
> `chore/v0.14.6-prep-cleanup` branch (`TokenManager` async-cache read-after-write
> race). It is out of scope for the CODE-AUDIT categories but noted here for
> release-record completeness.

---

## 4. Verification

- `OAuthClientConstants` extraction: both use cases compile and reference the
  shared object; `:app:assembleDebugAll`, `:app:testDebugUnitTestAll`,
  `:app:lintDebugAll` all green across every build flavor.
- The four sheets in the S3 finding were read directly (not just grepped) to
  confirm each one's dismissal mechanism before concluding no fix was needed.
- Font asset wiring (families × subsets × weights) for #223 cross-checked against
  the bundled `.woff2` files — no orphaned/missing assets. New locale
  `strings.xml` placeholders consistent across all 9 non-English locale files.

**Definition of done for v0.14.6:** met. The one real finding is fixed; the one
false positive is documented; the two S4 notes are tracked, non-blocking
follow-ups.
