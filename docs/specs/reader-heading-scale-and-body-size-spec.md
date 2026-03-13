# Reader Heading Scale and Body Size Specification

**Date:** 2026-03-12
**Status:** Draft

## Overview

This specification defines a smaller first slice of the broader reader appearance work:

1. tune the built-in heading scale and heading spacing used by the article WebView
2. evaluate and, if needed, adjust the default body-text size used at `100%`

The goal is to improve article readability without introducing new heading-specific user controls.

This spec is intentionally narrower than [reader-appearance-and-typography-controls-spec.md](/Users/nathan/development/MyDeck/docs/specs/reader-appearance-and-typography-controls-spec.md). It isolates one concrete problem that can be implemented, tested, and evaluated before deciding whether deeper typography controls are still necessary.

## Problem Statement

The current reader often renders in-article headings too large relative to body text.

In the debug samples under `debug/reader-enhancements/CSS/`, MyDeck headings consistently appear more oversized than the same articles in Instapaper. The difference is especially visible in:

- the Futura article section headings such as `Deep sleep as a natural shield`
- the TODAY article section headings such as `Cardiologist Tip of the Day`
- the Medium sample's extracted callout heading `Get Benlong's stories in your inbox`

The current MyDeck behavior is driven mainly by app-side template styling, not by a user-facing reader setting:

- body text defaults to `1.8rem` in the HTML template
- `h2` defaults to `2em`
- heading margins are also fairly large
- user font size still uses one global `WebView.textZoom`, so body text and headings scale together

This produces two related problems:

1. the visual relationship between body text and headings is often too dramatic
2. the default body text size itself may not be the right baseline for comfortable long-form reading

Because `textZoom` preserves the ratio between body text and headings, increasing or decreasing zoom does not solve the underlying proportion problem.

## Context

### Rendering pipeline today

- Extracted article HTML is inserted into a static app-owned HTML template.
- The template defines the base body font size, heading sizes, and heading spacing.
- Runtime typography controls modify font family, width, line height, justification, hyphenation, and one global zoom percentage.
- There is no existing heading-size preference in the data model.

### Important nuance from the debug samples

The exported article HTML in the sampled JSON files does not show inline `font-size` styling on the problematic headings. In other words, the oversized appearance is not mainly coming from server-provided inline CSS.

However, some source content does contain structures that become awkward when rendered with a strong heading scale. The Medium sample is a good example: a promotional block is represented as an `h2`, so even a semantically valid heading can feel too loud if the app's default heading styling is too aggressive.

This means the first fix should target MyDeck's built-in heading treatment. It also means some "odd heading" cases may remain content-driven even after the scale is improved.

## Goals

- Reduce the size gap between body text and article headings.
- Reduce heading spacing when it contributes to an exaggerated break in reading rhythm.
- Revisit the default body-text size used at `100%`.
- Keep the current zoom-factor model for user font sizing.
- Make the default reader appearance feel closer to established long-form reading apps without cloning any one app exactly.
- Improve the `100%` baseline so fewer users feel an immediate need to adjust text size.

## Non-Goals

- New user-facing controls for headings or subheadings
- Separate controls for `h1/h2` versus `h3-h6`
- Separate title controls in this first slice
- A redesign of the reader bottom sheet
- Font-family changes
- Full article-content cleanup or heuristic removal of non-content callouts
- Broad normalization or removal of extracted formatting that is already working well

## Current State

The current template defaults create a strong heading hierarchy:

- body text: `1.8rem`
- `h1`: `2.35em`
- `h2`: `2em`
- `h3`: `1.75em`
- heading top margin: `3rem`
- heading bottom margin: `1.5rem`

On smaller screens, the template also reduces body size through media queries, which can make body copy feel smaller while headings still remain comparatively dominant.

## Product Direction

For this slice, MyDeck should continue using one global zoom factor for reader text sizing. Instead of adding heading controls, the app should improve the built-in default typography ratios and refine the zoom control so it is easier to fine-tune around the new baseline.

The implementation should focus on two decisions:

1. what the new default body-text baseline should be
2. what heading size and spacing scale best complements that baseline

These decisions should be made together, not independently.

The body-text baseline should be adjusted in the HTML templates, not by changing the stored default zoom percentage.

The zoom control remains percentage-based, but its range and increment behavior should be tuned to better fit the improved default baseline:

- default remains `100%`
- increment changes from `10%` to `5%`
- range changes from `80–200%` to `85–170%`

Bookmark title sizing is not a primary target of this slice, but it should still be checked as part of the overall visual hierarchy. The intended hierarchy remains:

- title as the largest text treatment
- article headings beneath the title in descending order
- body text beneath headings

If the heading and body-text changes preserve that hierarchy cleanly, no title-specific adjustment is needed in this slice.

## Functional Requirements

### 1. Keep the existing zoom model

- The reader continues to expose one body-text zoom control.
- The zoom control continues to scale article text globally.
- No new heading-specific controls are added in this feature.

### 1.1 Refine zoom granularity and range

The zoom control should remain percentage-based, but the values should be adjusted to fit the new default reading baseline more naturally.

- default: `100%`
- minimum: `85%`
- maximum: `170%`
- increment: `5%`

The goal is to make adjustments feel less jumpy near the default size while removing an overly large upper extreme that is unlikely to be useful for most readers.

### 2. Tune built-in heading scale

MyDeck must reduce the default visual dominance of article headings in the WebView templates.

This includes:

- reducing the default heading-size ratio relative to body text
- reviewing `h1`, `h2`, and `h3` together so the hierarchy remains clear
- reducing heading margins if needed to create a steadier reading rhythm

The exact numeric values are an implementation decision to be validated visually, but the outcome should be visibly closer to a comfortable long-form reading layout than the current default.

### 3. Revisit the default body-text baseline

MyDeck must evaluate whether the current `100%` body-text baseline is too small for comfortable reading.

The new default should be selected using both of these reference frames:

- Material Design 3 body-text expectations for readable mobile content
- the visual baseline commonly used by mature reading apps

This does not require matching another app exactly. It does require choosing a deliberate baseline instead of inheriting the current one unchanged.

This baseline change should be implemented by adjusting the template's base body-text size while preserving `100%` as the default zoom value.

### 4. Preserve proportional behavior under zoom

After the new default body size and heading scale are chosen:

- zoom changes must still feel natural
- headings must remain distinct but not oversized at common zoom levels
- the `100%` view must feel like a sensible default rather than a compromise

### 5. Apply consistently across reading appearances

The updated typography defaults must be reflected consistently in:

- light reader template
- sepia reader template
- dark reader template

### 6. Preserve existing beneficial formatting

This feature should preserve extracted formatting that already contributes positively to readability, including cases such as:

- italics
- blockquotes or other indented content
- semantic emphasis and similar inline formatting

The goal is to improve default typography ratios, not to flatten articles into a simpler but less expressive presentation.

## Design Principles

- Favor better defaults over more controls.
- Optimize for reading comfort, not maximum visual hierarchy.
- Make headings easy to scan without letting them overpower the page.
- Treat body-size baseline and heading scale as one typography system.
- Prefer one well-tuned default experience over a larger settings surface.

## Acceptance Criteria

- In the sampled reader-enhancement articles, headings no longer feel oversized compared with body text at the default `100%` setting.
- The visual gap between body text and `h2` headings is clearly smaller than it is today.
- Heading spacing feels intentional and does not create unnecessarily abrupt breaks.
- The default body-text size feels comfortable for long-form reading on a phone-sized screen without immediate zoom adjustment.
- The zoom control adjusts in `5%` increments.
- The zoom range is limited to `85–170%`.
- The new default is achieved while keeping `100%` as the default zoom value.
- The bookmark title still reads as the largest text element without requiring a new title-specific control.
- The updated defaults behave consistently across light, sepia, and dark templates.
- Existing helpful formatting such as italics and indented or block content continues to render appropriately.
- No new user-visible heading controls are introduced.

## Follow-Up Candidates

- If some extracted promo blocks still feel too prominent after the template change, content normalization can be handled in a separate follow-up spec.
