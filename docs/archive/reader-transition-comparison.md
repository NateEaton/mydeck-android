# Reader Open Transition — A/B Comparison

## Purpose

Compare two reader-open transition styles and gather user feedback to decide
which one ships. Both branches share the same baseline reader logic (scroll
restore, content gating, collapsing top bar) — only the navigation animation
differs.

---

## Option A — Slide + Fade (baseline)

**Branch:** `codex/compare-reader-baseline-20260305`

**Behaviour:** When a bookmark card is tapped the reader screen slides in from
the right while fading in (300 ms). Pressing back reverses: the reader slides
out to the right while the list slides back in from the left.

**Characteristics:**
- Standard Material horizontal-slide pattern used for forward/back navigation.
- Strong directional cue — clearly communicates "deeper" navigation.
- Consistent with how Settings, About, and other secondary screens transition.
- The list screen is briefly visible sliding underneath during the crossover.

---

## Option B — Scale + Fade (zoom)

**Branch:** `claude/compare-reader-transition-20260305`

**Behaviour:** When a bookmark card is tapped the reader screen scales up from
92 % to 100 % while fading in (300 ms). The list screen underneath uses the
default slide-out. Pressing back reverses: the reader scales down + fades out.

**Characteristics:**
- Gives a subtle "zoom into content" feel — the reader appears to expand
  toward the user from the centre of the screen.
- Visually distinct from regular screen-to-screen navigation, reinforcing
  that opening a bookmark is a mode change (list → immersive reading).
- No layout-level coupling — the animation is purely a NavHost transition
  override; it does not interfere with scroll state, nested-scroll
  connections, or content measurement.
- Reader functionality (scroll restore, collapsing top bar, progress
  tracking) is identical to Option A.

---

## What to evaluate during testing

| Criterion | Notes |
|---|---|
| **Feel** | Does the transition feel smooth and intentional? |
| **Speed** | Does 300 ms feel right, or should it be faster/slower? |
| **Directionality** | Is it clear you're going "into" a bookmark and "back out"? |
| **Consistency** | Does the reader transition feel natural relative to other screen transitions (Settings, About, etc.)? |
| **Distraction** | Does the animation draw attention to itself or stay out of the way? |

---

## Technical notes

- The only file changed between Option A and Option B is `AppShell.kt`.
- The transition is applied per-route on the `BookmarkDetailRoute` composable
  in all three layout tiers (Compact, Medium, Expanded).
- All other routes retain the default slide + fade transition.
- No `SharedTransitionLayout`, `sharedBounds`, or `sharedElement` APIs are
  used — the animation is a simple `scaleIn`/`scaleOut` + `fadeIn`/`fadeOut`
  override on the Navigation composable entry, which keeps it completely
  decoupled from the reader's scroll and layout systems.
