# URL Parameter Sanitization — Technical Specification

Status: **Tier 1 implemented but shelved — validation pending**
Date: 2026-05-21
Last Updated: 2026-05-25

## Implementation Status

Tier 1 was fully implemented per §6 of this spec. The implementation is held on the local branch `feat/url-sanitizer-tier1` (not pushed) and includes:

- `UrlSanitizer` utility with 43 hardcoded tracking parameters (utm_*, fbclid, gclid, ref, share, si, t, spm, vero_*, hsCtaTracking, mkt_tok, and the rest of the §6.2 list)
- 15 unit tests covering the §6.4 acceptance criteria and the §9.3 edge cases (malformed URLs, fragments, repeated params, percent-encoding)
- `sanitizedUrl` and `removedParams` added to `CreateBookmarkUiState`
- Advisory composable below the URL field in `AddBookmarkSheet`, wired to both the FAB entry point and `ShareActivity`
- Reactive trigger so sanitization runs when the dialog opens with a clipboard-prefilled URL, not only on user edit
- Strings added to all 10 language files; user guide updated

### Why it is shelved

The motivating problem (§1 — Substack share URLs hitting auth/preview walls because of `r=…`, `triedRedirect=true`, and `utm_*` parameters) no longer reproduces in testing as of 2026-05-25. Sanitizing the URL produces the canonical form correctly, but extraction now fails on those canonical URLs as well — the failure mode has moved upstream of anything URL sanitization can fix. Without a reproducible case showing that sanitization improves extraction success, shipping the advisory would add UI surface without demonstrable value.

### What would revive this

Any of the following:

- A reproducible host where sanitization measurably improves extraction success rates
- A second host class (beyond Substack) exhibiting the original symptom
- A user request for tracking-parameter stripping as a privacy feature in its own right, independent of extraction reliability

To revive, check out the `feat/url-sanitizer-tier1` branch and re-validate against current Readeck behavior. Tier 2 (host-specific rules) and Tier 3 (user-defined rules) remain unimplemented and would build on this foundation if revived.

### Next review

Revisit this status when one of the revival conditions above is met, or proactively when triaging future v0.x release planning. At that point: either re-validate and merge, or delete the branch and archive this spec to `docs/archive/`.

## 1. Purpose

Define a feature for detecting and optionally rewriting URLs at bookmark-creation time so that tracking parameters, share identifiers, and other host-specific noise do not cause Readeck extraction failures.

Background: certain hosts (notably Substack) react to share-link parameters by redirecting through interstitials that land Readeck on auth/preview walls. The same URL stripped to its canonical form extracts cleanly. Today MyDeck has no way to surface this to the user, and Readeck custom-extraction scripts cannot help because they run after fetch.

This spec is scoped to the client-side fix. An upstream proposal to Readeck is described in the addendum (§10).

## 2. Definitions

### 2.1 Tracking parameter

A query-string parameter whose presence is incidental to identifying the document. Examples: `utm_source`, `utm_medium`, `utm_campaign`, `fbclid`, `gclid`, `mc_cid`, `igshid`, `ref`, `s`.

### 2.2 Host-problematic parameter

A parameter that, while not a generic tracker, is known to cause extraction failure or content divergence on a specific host. Example: `r=…` and `triedRedirect=true` on `substack.com`.

### 2.3 Sanitization

The act of producing a normalized URL by stripping or filtering query parameters according to a rule. Path, scheme, fragment, and host are preserved unless an explicit rule says otherwise.

### 2.4 Rule

A unit of sanitization logic. A rule has:

- A host matcher (exact, suffix, or `*` for any host)
- An action: `strip-params`, `keep-only-params`, or (Tier 3) `regex-replace`
- A parameter list or regex pattern

## 3. Goals

1. Detect when a URL contains parameters that are likely to cause extraction problems
2. Surface the detection to the user before submission, not silently
3. Let the user accept the sanitized URL with a single tap
4. Preserve user agency — never auto-rewrite without consent
5. Work in both bookmark-add entry paths (in-app FAB, share intent)
6. Ship value incrementally: a minimal Tier 1 must be useful on its own

## 4. Non-Goals

1. Server-side URL rewriting (covered by the Readeck enhancement in §10)
2. Recovering content from extraction failures after the fact
3. Detecting paywalls or auth walls
4. Full URL canonicalization (case folding, sorting params, removing default ports, etc.)
5. Cross-device sync of user-defined rules (Tier 3 stores locally only in V1)

## 5. UX Specification

### 5.1 Common pattern across all tiers

When the URL field changes (either via paste, share intent, or typing), the URL is run through the sanitizer pipeline. If sanitization would produce a different URL, the add sheet displays a non-error advisory row directly below the URL field:

```
[icon]  3 tracking parameters detected         [ Clean ]
```

- The advisory is visually distinct from `urlError` (informational, not error styling)
- The `[ Clean ]` action replaces the URL field value with the sanitized URL
- If the user dismisses or ignores it, the original URL is submitted unchanged
- The advisory disappears once the URL is clean or the field is empty

### 5.2 Entry point parity

Behavior is identical in:

- In-app FAB add sheet (`AddBookmarkSheet` invoked from `BookmarkListScreen`)
- Share-intent add sheet (`AddBookmarkSheet` invoked from `ShareActivity`)

Auto-save countdown in share-intent mode behaves as today: editing the URL or tapping `[ Clean ]` cancels the countdown, consistent with existing `cancelAutoSave()` semantics.

## 6. Tier 1 — Built-in Tracking Parameter Detection (V1)

### 6.1 Scope

A single `UrlSanitizer` utility with a hardcoded list of well-known generic tracking parameters. No per-host logic. No user configuration.

### 6.2 Built-in tracker list (initial)

```
utm_source, utm_medium, utm_campaign, utm_term, utm_content, utm_id,
fbclid, gclid, dclid, msclkid, yclid,
mc_cid, mc_eid,
igshid, igsh,
_ga, _gl,
ref, ref_src, referrer,
s, share, share_id, shared,
si, t,           // YouTube share tracker, timestamp tracker
spm,             // Alibaba tracker
vero_conv, vero_id,
hsCtaTracking, hsenc, hsmi,
mkt_tok,
```

The list lives in `UrlSanitizer.kt` as a `Set<String>` constant. Additions are code changes, not config.

### 6.3 Sanitization algorithm

```
1. Parse the input as a URI. If parsing fails, return the input unchanged.
2. Collect query parameters preserving order.
3. Drop any parameter whose name (case-insensitive) is in the built-in tracker set.
4. If no parameters were dropped, return the input unchanged.
5. Rebuild the URL with the surviving parameters, preserving original encoding
   and parameter order.
6. Preserve fragment and trailing slash semantics exactly.
```

### 6.4 Acceptance criteria

- Pasting `https://example.com/x?utm_source=foo&id=42` shows the advisory; tapping `[ Clean ]` produces `https://example.com/x?id=42`
- Pasting a clean URL shows no advisory
- Pasting a URL with only tracker params produces `https://example.com/x` (no trailing `?`)
- Advisory does not appear during URL field focus loss alone — only on actual textual change
- Both add-sheet entry points behave identically

### 6.5 Implementation touchpoints

- New: `app/src/main/java/com/mydeck/app/util/UrlSanitizer.kt`
- New: advisory composable in `AddBookmarkSheet.kt` placed below the URL `OutlinedTextField` (around line 191)
- Modified: `BookmarkListViewModel.updateCreateBookmarkUrl` to compute the sanitized URL alongside `urlError` and expose it as new state
- Modified: `ShareActivity.kt` URL state block (around line 261) to do the same
- Modified: `CreateBookmarkUiState` (and the share-intent equivalent) to add `sanitizedUrl: String?` and `removedParams: List<String>`
- New strings (English plus placeholders in all language files per §Localization in `CLAUDE.md`):
  - `url_sanitize_advisory` — "%1$d tracking parameter(s) detected"
  - `url_sanitize_action` — "Clean"

### 6.6 Estimated effort

~1 day, including tests and localization placeholders.

## 7. Tier 2 — Curated Host-Specific Rules (V2)

### 7.1 Scope

Extend the sanitizer with a curated rule set keyed by host. Ships as an asset bundled in the APK; updateable via app release without code changes to the sanitizer engine itself.

### 7.2 Rule file format

Location: `app/src/main/assets/url-rules/builtin.yaml`

```yaml
version: 1
rules:
  - host: substack.com
    match: suffix      # matches substack.com and *.substack.com
    strip-params: [r, triedRedirect, utm_source, utm_medium, utm_campaign]
  - host: medium.com
    match: suffix
    strip-params: [source]
  - host: youtube.com
    match: suffix
    keep-only-params: [v, t, list]
  - host: youtu.be
    match: exact
    keep-only-params: [t]
```

### 7.3 Matching semantics

- `match: exact` — full host equality, case-insensitive
- `match: suffix` — the URL host equals the rule host or ends with `.{rule-host}`
- `match: glob` — reserved for future use; not implemented in V2

### 7.4 Sanitization pipeline (Tier 2)

```
1. Apply Tier 1 generic tracker stripping.
2. Find all rules whose host matcher matches the URL host.
3. For each match in declaration order:
   - If strip-params: drop the listed parameters.
   - If keep-only-params: drop every parameter not in the list.
4. Return the result.
```

If both Tier 1 and Tier 2 remove parameters, the advisory reports a single combined count.

### 7.5 Acceptance criteria

- A Substack share URL with `?utm_source=share&utm_medium=android&r=1pr2d5&triedRedirect=true` sanitizes to the bare article URL
- A YouTube URL `https://youtube.com/watch?v=abc&si=xyz&pp=tracking` sanitizes to `https://youtube.com/watch?v=abc`
- Updating `builtin.yaml` and releasing a new APK is sufficient to ship new host rules

### 7.6 Implementation touchpoints

- New: YAML parsing dependency (or hand-rolled minimal parser — the schema is tiny)
- New: `UrlSanitizer` reads `builtin.yaml` lazily on first use and caches in memory
- Test fixture: a parallel YAML in `app/src/test/resources/url-rules/` with synthetic hosts for unit tests
- No new UI strings beyond Tier 1

### 7.7 Estimated effort

~2-3 days.

## 8. Tier 3 — User-Defined Custom Rules (V3)

### 8.1 Scope

A settings screen that lets users add, edit, enable/disable, and delete their own rules. User rules layer on top of Tier 1 and Tier 2 — they cannot remove built-in behavior, only extend it.

### 8.2 UX

New settings entry: **Settings → Bookmarking → URL rules**

Screen contents:

- Toggle: "Detect tracking parameters" (master switch, default on, controls Tier 1 + Tier 2 + Tier 3)
- List of user rules, each row: host, action summary, enabled toggle, edit/delete
- FAB / "+" button to add a new rule
- "Import / Export" overflow action — JSON, file picker

Add/edit rule form:

- Host (text field, with `exact / suffix` selector)
- Action (dropdown: `Strip parameters` / `Keep only these parameters` / `Replace pattern`)
- Parameter list (chip input) for the first two actions
- Pattern + replacement (two text fields) for `Replace pattern` — advanced/regex mode
- Save / Cancel

### 8.3 Storage

Room entity `UrlRule` with fields:

```kotlin
data class UrlRule(
    val id: Long,
    val host: String,
    val matchKind: MatchKind,        // EXACT, SUFFIX
    val action: RuleAction,          // STRIP, KEEP_ONLY, REGEX_REPLACE
    val params: List<String>?,       // for STRIP, KEEP_ONLY
    val pattern: String?,            // for REGEX_REPLACE
    val replacement: String?,        // for REGEX_REPLACE
    val enabled: Boolean,
    val createdAt: Instant,
)
```

### 8.4 Pipeline (Tier 3)

```
1. Apply Tier 1.
2. Apply Tier 2 (matching built-in rules in order).
3. Apply Tier 3 user rules in order of creation, skipping disabled ones.
```

### 8.5 Regex safety

- Compile patterns at rule-save time; reject syntactically invalid patterns
- Apply a per-rule timeout (e.g., 50 ms) when running on a URL to guard against catastrophic backtracking
- If a rule throws or times out, log and skip it, do not block the add flow

### 8.6 Import / Export

JSON shape:

```json
{
  "version": 1,
  "rules": [
    {
      "host": "substack.com",
      "match": "suffix",
      "action": "strip-params",
      "params": ["r", "triedRedirect"],
      "enabled": true
    }
  ]
}
```

Import merges by appending; duplicates (same host + action + params) are skipped.

### 8.7 Acceptance criteria

- A user can create a rule for `example.com` stripping `tk`, and pasting `https://example.com/x?tk=1` shows the advisory
- Disabling the rule restores the original behavior immediately, without restarting the app
- Exporting then re-importing produces an equivalent rule set
- An invalid regex is rejected at save time with a clear error message

### 8.8 Implementation touchpoints

- New: Room entity, DAO, migration
- New: `UrlRulesViewModel`, `UrlRulesScreen`, add/edit form composables
- Modified: `UrlSanitizer` to consume rules from the repository
- New strings (all language files): rule list title, add/edit labels, action types, import/export, validation errors
- User guide update: new section in `settings.md` covering URL rules

### 8.9 Estimated effort

~1 week, dominated by the rules editor UI and tests.

## 9. Cross-Tier Concerns

### 9.1 Performance

The sanitizer runs on every URL field change. Pipeline must complete in < 5 ms for typical inputs; Tier 3 regex rules have their own 50 ms per-rule budget. Built-in rules are precompiled at load time.

### 9.2 Telemetry / observability

Out of scope for V1-V3. If added later, log only counts of detections and accepts, never URLs.

### 9.3 Testing strategy

- Unit tests for `UrlSanitizer` covering Tier 1 trackers, Tier 2 host matches (exact, suffix), Tier 3 actions including regex
- Edge cases: malformed URLs, URLs with fragments, URLs with repeated parameter names, percent-encoded values, IDN hosts
- Snapshot tests for the advisory row composable

### 9.4 Rollout order

Ship Tier 1 first as a self-contained release. Tier 2 follows once at least three host rules are validated against real failures. Tier 3 only ships if Tier 2 misses justify it.

### 9.5 Documentation

- Tier 1: update `your-bookmarks.md` ("Add bookmark") with a paragraph describing the advisory row
- Tier 2: extend the same section with the host-rule note
- Tier 3: new subsection in `settings.md`

## 10. Addendum — Readeck Enhancement Proposal

### 10.1 Motivation

URL sanitization in MyDeck helps MyDeck users, but Readeck has many clients: the web UI, the official mobile app, browser extensions, and other third-party apps. Each would otherwise need to reinvent this logic, and rule sets would drift.

The right long-term home for URL normalization is Readeck itself, executed server-side **before fetch**. This is distinct from Readeck's existing site-config and custom extraction script features, both of which run after the page has been fetched and therefore cannot help when the original URL redirects to an auth wall.

### 10.2 Proposed feature: pre-fetch URL normalization

A new Readeck capability that:

1. Maintains a built-in list of generic tracking parameters, stripped by default
2. Supports per-host rules (strip / keep-only / regex-replace) loaded from a config file
3. On extraction failure with the original URL, automatically retries once with the normalized URL and records both attempts in the extraction log
4. Returns the effective URL in API responses so clients can display "saved as <canonical URL>"
5. Per-instance admin toggle to disable normalization entirely (for users who care about preserving referrer/affiliate params)

### 10.3 Proposed configuration format

YAML, parallel to Readeck's existing site-config conventions:

```yaml
version: 1
defaults:
  strip-tracking: true     # enables the built-in tracker list
rules:
  - host: substack.com
    match: suffix
    strip-params: [r, triedRedirect]
  - host: medium.com
    match: suffix
    strip-params: [source]
```

The format is intentionally identical to MyDeck's Tier 2 format (§7.2) to make the curated MyDeck rule set a directly portable starter set for the upstream feature.

### 10.4 API surface

- `POST /api/bookmarks` accepts the user-supplied URL as today
- Response includes:
  - `url`: the originally submitted URL
  - `normalized_url`: the URL after normalization (may equal `url`)
  - `effective_url`: the URL that successfully extracted (may equal either of the above)
- Extraction log includes a `normalization` event when a rule fired

### 10.5 Relationship to MyDeck client-side feature

Client-side sanitization is still valuable even if Readeck ships server-side normalization:

- User confirmation happens before the request, surfacing the rewrite visibly
- Works against older Readeck instances and other backends that may never get this feature
- Provides immediate feedback (no round-trip) for the advisory UI

If Readeck accepts the proposal, MyDeck's client logic becomes a redundant safety net plus a UI affordance — it does not become dead code.

### 10.6 Concrete repro for the proposal

Include in the upstream issue:

- The failing Substack URL pattern (`?utm_source=share&utm_medium=android&r=...&triedRedirect=true`)
- A captured extraction log showing the auth-wall result
- The same URL stripped, showing successful extraction
- The MyDeck Tier 1 + Tier 2 rule files as a proposed starting point

### 10.7 Suggested filing path

1. Open a discussion on the Readeck repository describing the problem, before filing an issue, to gauge maintainer appetite
2. If positive, file a feature request issue with the repro from §10.6 and the proposed config format from §10.3
3. Offer to contribute the Tier 1 tracker list and curated host rules
4. Do not block MyDeck client work on the upstream timeline

## 11. Open Questions

1. Should Tier 1 ever auto-clean without prompting for known-pure-tracker cases (e.g., a URL whose *only* params are `utm_*`)? Current spec says no — always require confirmation. Worth revisiting after user feedback.
2. Should the advisory show the specific parameter names being removed, or only a count? Count-only is simpler; names are more transparent but noisier in the UI.
3. For Tier 3 regex rules, should we offer a "test against URL" affordance in the editor? Probably yes, but deferred to V3.1.
4. Should sanitization apply to edit flows (e.g., when a user edits a bookmark's URL after creation)? V1 says no — add-only. Revisit if user demand appears.
