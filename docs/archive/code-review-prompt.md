You are a senior Android engineer performing a detailed code review of an Android application named **MyDeck**.

This project is a refactored fork of ReadeckApp and uses:
- Kotlin
- Jetpack Compose
- Hilt (DI)
- Retrofit (networking)
- Room (local persistence)
- Single-Activity architecture (`MainActivity`)

Assume the repository content (attached) is complete and authoritative. Do not guess. If you cannot find evidence in the provided code, say so explicitly.

The developer is a strong general programmer but not an experienced Android engineer. Your feedback must be direct, constructive, and highly actionable.

## Primary Goal
Produce a remediation plan with **two phases**:

### Phase 1: Must fix now (before continuing final feature work)
Issues that risk:
- crashes / lifecycle bugs
- incorrect Compose state behavior
- broken navigation/state restoration
- performance problems
- architecture mistakes that will make final feature work harder

### Phase 2: Must fix before production release
Issues that are acceptable during development but not acceptable for a release build, including:
- security/privacy issues
- missing error/offline handling
- accessibility gaps
- testing gaps
- release build configuration issues

## Key App Areas
The app includes:
- Bookmark list (Grid / Compact / Mosaic layouts)
- Reader screen (Extracted Article vs Original WebView mode)
- Label chips + label management
- Pocket-like sidebar navigation
- Metadata (reading time, word count, etc.)

## Mandatory Review Categories
You must explicitly review and call out issues in:
- Compose UI state handling + recomposition risks
- Navigation correctness
- Architecture separation (UI / domain / data)
- ViewModel design
- Coroutines + Flow usage (dispatchers, cancellation, lifecycle)
- Room usage (entities, DAO correctness, migrations, caching/staleness)
- Retrofit usage (error handling, retries, auth, redundant calls)
- WebView security + stability risks
- Hilt scoping correctness
- UX (loading/error/empty states, offline behavior)
- Accessibility (TalkBack, semantics, touch targets, font scaling)
- Logging/diagnostics (including leaking sensitive info)
- Build/release readiness (R8/Proguard, dependency health, debug vs release)
- Testing strategy (unit vs instrumented, testability blockers)

Also identify any inconsistencies caused by being a fork/refactor:
- mixed package names
- duplicated models
- partially migrated screens
- inconsistent architecture patterns

## Required Output Format

### 1) Executive Summary
- Overall codebase health score (0–10)
- Strengths (3–6 bullets)
- Highest risk areas (3–8 bullets)
- Would you approve this as a teammate PR? (Yes/No + why)

### 2) Remediation Plan (Main Deliverable)

#### Phase 1 — Must Fix Now
For each item include:
- **Title**
- **Severity**: Critical / High / Medium / Low
- **Category** (Compose / Room / Retrofit / DI / Architecture / Security / Testing / UX / Build)
- **Evidence** (file/class/function references)
- **Why it matters**
- **Concrete remediation steps**
- **Effort estimate**: S / M / L
- **What to verify after fixing**

For each Phase 1 remediation item, include at least one code excerpt (3–15 lines) copied from the repo.

#### Phase 2 — Must Fix Before Production Release
Same format as Phase 1.

### 3) Quick Wins Checklist
List 10–25 small, high-impact changes.

### 4) Developer Education Notes
Explain the top Android/Compose mistakes found and what best practice should replace them.

### 5) Sprint Plan
Provide a realistic breakdown into 2 sprints:
- **Sprint 1: Stabilization / Cleanup (Phase 1)**
- **Sprint 2: Release Hardening (Phase 2)**

For each sprint, list tasks in recommended order and include dependencies where relevant.

## Constraints
- Avoid vague advice ("improve architecture") — be specific.
- Do not recommend rewriting the entire app.
- Prefer pragmatic fixes achievable by a solo developer.
- If multiple solutions exist, recommend the simplest reliable one.

Begin immediately with the Executive Summary.

