# Reader Line Spacing and Width Controls Specification

**Date:** 2026-03-12
**Status:** Draft

## Overview

This specification defines the next focused slice of reader typography work:

1. replace the current two-state line spacing control with stepped percentage adjustments
2. replace the current two-state width control with three synchronized width stops

This follows the earlier work on body-text baseline, heading scale, and zoom refinement. The goal here is not to expand the settings surface dramatically, but to make two existing controls feel more intentional and more in line with mature reading apps.

## Problem Statement

The current reader exposes:

- line spacing as `Tight` or `Loose`
- width as `Wide` or `Narrow`

Those controls are too coarse in different ways.

For line spacing, the current two-state toggle jumps between `1.7` and `2.2`, which is a large change. There is no way to make a smaller adjustment around the default reading rhythm.

For width, the current two-state toggle is both limited and inconsistent:

- the WebView uses one width mapping
- the Compose header and surrounding reader layout use another
- there is no middle stop

Compared with apps such as Readeck, Kindle, and Instapaper, a three-stop width model appears to be the more common pattern:

- narrow
- medium
- wide

On phones in portrait, the difference between stops is naturally smaller. On larger devices, the narrow mode should create a more clearly column-like reading width.

## Goals

- Make line spacing easier to fine-tune around the default.
- Keep line spacing changes compact and percentage-based.
- Introduce a three-stop width model with a clear middle option.
- Ensure width changes apply consistently to the bookmark header and the article content.
- Keep the control surface simple enough to use quickly while reading.

## Non-Goals

- Additional heading controls
- Arbitrary freeform width values
- More than three width stops in this slice
- A redesign of the overall reader settings sheet
- Device-specific width rules in the first implementation

## Product Decisions

### Line spacing

Line spacing becomes a percentage-based stepped control:

- default: `100%`
- minimum: `80%`
- maximum: `125%`
- increment: `5%`

Baseline:

- `100%` equals the current default line height of `1.7`

Legacy migration:

- old `TIGHT` maps to `100%`
- old `LOOSE` maps to `125%`

### Width

Width becomes a three-stop control presented as segmented pills:

- `W`
- `M`
- `N`

These map to:

- `W` / Wide: `95%`
- `M` / Medium: `88%`
- `N` / Narrow: `82.5%`

Behavior:

- `M` is the default for new users
- existing saved `Wide` and `Narrow` selections are preserved
- the same stop must be applied to both the bookmark header/title container and the article content container
- article content must not be narrowed a second time inside the WebView after the outer reading column width has been chosen

Accessibility:

- the visible pill labels may stay short (`W`, `M`, `N`)
- accessibility labels should still expose full meanings (`Wide`, `Medium`, `Narrow`)

## Functional Requirements

### 1. Line spacing control

The reader settings sheet must replace the existing `Tight / Loose` segmented control with a percentage-based stepped control.

Control format:

- `[-] 100% [+]`

Behavior:

- decreasing and increasing both move in `5%` increments
- the current percentage is always visible
- the control is disabled at the min and max bounds

### 2. Line spacing rendering

The chosen line spacing percentage must be converted into a CSS line-height using the current baseline of `1.7`.

Examples:

- `100%` -> `1.7`
- `80%` -> `1.36`
- `125%` -> `2.125`

### 3. Width control

The reader settings sheet must replace the current `Wide / Narrow` control with a three-stop segmented control.

Visible labels:

- `W`
- `M`
- `N`

Semantic meanings:

- `Wide`
- `Medium`
- `Narrow`

### 4. Width rendering

The selected width stop must drive both:

- the Compose width used by the bookmark header and surrounding reader container
- the effective article content width inside the WebView

The two layers must stay synchronized so the header and body feel like one reading column.

The bookmark title and description must visually align with the article body text on larger screens. In particular, the header must not appear wider than the body content in tablet portrait, tablet landscape, or phone landscape layouts.

### 5. Defaults and migration

New defaults:

- line spacing: `100%`
- width: `Medium`

Saved preferences:

- old line spacing values must be migrated sensibly into the new stepped model
- old width values must continue to resolve correctly

## Design Principles

- Favor small, predictable adjustments over dramatic jumps.
- Keep width selection simpler than font-size selection.
- Use a middle width as the default reading experience.
- Let larger devices benefit more visibly from width choices without special-case UI.

## Acceptance Criteria

- Line spacing can be adjusted in `5%` steps from `80%` to `125%`.
- The current line spacing percentage is always visible in the sheet.
- Width offers exactly three stops: `Wide`, `Medium`, and `Narrow`.
- The bookmark header/title width and article content width move together for each width stop.
- `Medium` feels like a real midpoint between the current narrow and wide behaviors.
- `Narrow` is noticeably column-like on larger form factors while remaining usable on phones.
- The bookmark title and article body align to the same visible reading column on larger screens.
- Existing saved `Tight`, `Loose`, `Wide`, and `Narrow` preferences migrate without breaking the reader.

## Review Notes

The first implementation should be reviewed specifically on a larger form factor device or emulator to judge whether `82.5%` is narrow enough compared with other reading apps. If it still feels too wide, the next iteration should adjust the width stops rather than adding more stops immediately.
