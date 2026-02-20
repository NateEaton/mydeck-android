## Final Opus Prompt (Paste-Ready)

You are performing a final senior-level engineering review of an Android application after a major foundational refactor.

This refactor was implemented across multiple branches using guidance from several AI-assisted code reviews and planning/tracking documents. The purpose of this review is to assess what was changed, how correct/complete the implementation is, and what work remains to reach a high-quality baseline for continued development and eventual release.

---

# Baseline / Comparison Target

Compare the current state of the code (HEAD) against the pre-refactor baseline commit:

**BASELINE_COMMIT = `83ac193`**

Use git history and diffs to establish ground truth:

* `git diff BASELINE_COMMIT..HEAD`
* `git log --oneline BASELINE_COMMIT..HEAD`

Start by identifying the major structural and architectural changes introduced since the baseline.

---

# Input Documents

## A) Original Code Review Documents (the “requirements”)

These are the original review documents that motivated the refactor work:

* `docs/architectural-code-review-2026-02-10.md`
* `docs/ai-studio-code-review.md`

## B) Planning / Tracking / Progress Notes

These documents reflect what the various models planned and tracked during implementation:

* `_notes/PRE_RELEASE_CLEANUP.md` (had some previously identified items but was updated in midst of the various passes through the code in the cleanup work)
* `/Users/nathan/.gemini/antigravity/brain/fb742342-d074-4154-854f-7cdefc58f9c9/implementation_plan.md.resolved`
* `/Users/nathan/.gemini/antigravity/brain/fb742342-d074-4154-854f-7cdefc58f9c9/p1_foundational_cleanup.md.resolved`
* `/Users/nathan/.gemini/antigravity/brain/fb742342-d074-4154-854f-7cdefc58f9c9/p3_detail_screen_modularization.md.resolved`
* `/Users/nathan/.gemini/antigravity/brain/fb742342-d074-4154-854f-7cdefc58f9c9/walkthrough.md.resolved`
* `/Users/nathan/.gemini/antigravity/brain/fb742342-d074-4154-854f-7cdefc58f9c9/p2_data_layer_integrity.md.resolved`
* `/Users/nathan/.gemini/antigravity/brain/fb742342-d074-4154-854f-7cdefc58f9c9/task.md.resolved`

During the cleanup, I identified that Readeck does support commas in labels so to address that and the code review concerns regarding CSV processes, this was added to the work. These documents cover that additional activity. 

* `/Users/nathan/.gemini/antigravity/brain/fb742342-d074-4154-854f-7cdefc58f9c9/label_fix_plan.md.resolved`
* `/Users/nathan/.gemini/antigravity/brain/fb742342-d074-4154-854f-7cdefc58f9c9/label_fix_walkthrough.md.resolved`

## C) Canonical Review Priorities / Output Format

Use this document as the canonical definition of review priorities, required review categories, and expected output format:

* `docs/code-review-prompt.md`

Do **not** restate this document. Treat it as the authoritative specification for how the review should be structured and what areas must be covered.

## D) Project Rules / AI Collaboration Constraints

This repo includes `CLAUDE.md`. Treat it as a binding set of project rules and conventions.

In particular:
- Ensure localization practices are followed (no hardcoded UI strings, correct use of string resources, etc.).
- Flag any changes that violate the standards described in `CLAUDE.md`.

---

# Required Review Approach

## Step 1 — Establish Ground Truth

Start by reviewing the diff and commit history to understand what changed between BASELINE_COMMIT and HEAD.

Summarize the major refactor themes (package changes, architecture changes, persistence/networking changes, DI changes, ViewModel/state patterns, etc.).

If any major regressions are visible at this stage, flag them early.

## Step 2 — Understand the Intended Work

Read the original review documents and the planning/tracking documents.

Extract the key goals and recommendations that were expected to be implemented.

## Step 3 — Validate Completeness Against the Original Reviews

For each significant recommendation from the original code reviews:

* Determine whether it was implemented
* Assess whether it was implemented correctly
* Identify partial implementations or unfinished refactors
* Identify new issues introduced as side effects

You must support conclusions with evidence (file/class/function references).

If you disagree with any recommendation from the original review docs, say so explicitly and explain why.

## Step 4 — Independent Review of Current Codebase

After validating against the original review recommendations, perform your own independent review of the current codebase using senior Android engineering standards.

Your goal is to identify issues that could:

* cause crashes or lifecycle bugs
* cause incorrect Compose state behavior
* cause navigation/state restoration issues
* cause performance issues or excessive recomposition
* cause threading/coroutines bugs
* cause persistence correctness issues (Room/migrations/staleness)
* cause network error handling gaps (Retrofit)
* cause security/privacy risks (especially WebView)
* cause maintainability/testability problems
* cause localization/i18n regressions or inconsistent string usage

This review must cover both:
- application/source code
- test code (unit tests and instrumentation tests), including correctness, maintainability, and coverage gaps

---

# Constraints / Focus

* A full Material Design 3 visual/UX polish pass will happen later.
* Therefore, do not focus on styling or purely aesthetic UI concerns unless they indicate deeper architectural/state/accessibility problems.
* Avoid generic advice. Recommendations must be specific and grounded in evidence from the actual codebase.
* Prefer pragmatic improvements appropriate for a solo developer.
* Do not recommend rewriting the entire app.
 Do not ignore test quality: test architecture and test reliability are in scope for this review.

---

# Required Deliverables

Create **two separate review documents**.

## Document 1 — Refactor Completeness & Quality Assessment

This document should evaluate the work performed against the original review documents and planning notes.

Include:

* Executive summary of overall refactor success
* Major improvements achieved
* Remaining gaps or incomplete work
* Regressions / new risks introduced
* A recommendation compliance matrix (implemented / partial / missing / incorrect)
* Top remaining issues to address next

## Document 2 — New Comprehensive Code Review & Remediation Plan

This document should be your independent code review and plan for what should be done next.

Use the structure and category expectations defined in `code-review-prompt.md`.

Each issue must include:

* severity (Critical / High / Medium / Low)
* category (Architecture / Compose / Navigation / Coroutines / Room / Retrofit / DI / WebView / Security / Testing / Build / UX / Accessibility)
* evidence (file/class/function)
* why it matters
* concrete remediation steps
* effort estimate (S/M/L)

End with a prioritized execution roadmap (Phase 1 / Phase 2) and a suggested sprint breakdown.

---

# Additional Notes

If you find multiple possible solutions, recommend the simplest reliable approach and briefly explain tradeoffs.

Be opinionated but fair: if something is over-engineered, inconsistent, or unnecessarily complex, call it out directly.

---

Begin immediately.

