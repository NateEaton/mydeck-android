# **Swipe Gesture Implementation Spec (Architecture-Aligned)**

## **1. Design Principle (Critical)**

Swipe must:

> **Trigger existing action pathways only**

No new ViewModel methods, no alternate mutation logic.

Equivalent mapping:

* Swipe → invokes same handler as action icon click
* UI reacts via existing observers / flows / adapters

---

## **2. Interaction State Model (UI Layer Only)**

Per card the only durable state is the drag offset; the gesture model is
commit-only with elastic snap-back, so there is no `Revealed` or
`Committed` state to persist:

```
SwipeState:
  - Idle              (offset == 0)
  - Dragging(offsetX) (anywhere in between)
```

Commit fires on `onDragStopped` if `|offset| >= 0.5 * cardWidth` at the
moment of release. Haptic feedback fires once mid-drag, on the first
threshold crossing within a gesture, as a commit-ready cue. A boolean
guard (`hapticConsumed`) prevents repeated haptic on offset jitter near
the threshold; it resets on settle at Idle. After commit, the drag state
animates back to Idle. If the user crosses threshold and then drags back
under it before release, no commit fires — this back-off cancellation is
an intentional escape hatch.

This state:

* Lives **only in the View layer (Compose)**
* Must not leak into domain/data layer
* Is owned by the bookmark card composable, not by the list, scaffold,
  drawer, rail, or shell

Gesture recognizers must be installed only on bookmark card roots. Do not put
the recognizer on `LazyColumn`, `LazyVerticalGrid`, the screen scaffold, or any
navigation container. This prevents tablet navigation areas from participating
in bookmark-card swipe tracking.

### Layout gating

Swipe is only attached in single-column rendering: phone portrait in any
layout, or Compact layout on any device. In multi-column grid/mosaic
layouts (tablet or mobile landscape) the wrap site passes
`config.copy(enabled = false)` so the container is a pure pass-through.

---

## **3. Gesture Resolution Logic**

### **3.1 Input Arbitration**

On pointer input:

```
if down started inside system gesture inset:
    do not track card swipe
elif abs(dx) > touchSlop AND abs(dx) >= abs(dy) * 1.5:
    enter SwipeState.Dragging
    cancel long press
    consume horizontal deltas
elif abs(dy) > touchSlop:
    pass to scroll
else:
    continue waiting (long press eligible)
```

Rules:

* Track passively until the gesture resolves; do not consume pointer movement
  before swipe intent is locked.
* Vertical scroll has precedence for ambiguous diagonals.
* Once scroll wins, card swipe must not start later in the same pointer
  sequence.
* Once swipe wins, keep the swipe locked until release/cancel.
* Use platform touch slop, not a hardcoded pixel value.

Long press:

* Only fires if still in Idle within slop radius

---

### **3.2 System Back and Drawer Policy**

Compact `ModalNavigationDrawer` swipe-open gestures conflict directly with
rightward card swipes. When swipe actions are enabled, set compact drawer
gestures to disabled on bookmark-list screens and rely on the existing
hamburger button to open the drawer.

System back must keep priority at the screen edge:

* Do not start card swipe tracking when the initial pointer is inside the
  system gesture inset. At minimum, reserve the left inset for the
  left-to-right back gesture; reserving both horizontal system gesture insets is
  preferable for Android gesture-navigation consistency.
* Do not call gesture exclusion APIs for bookmark-card swipe actions.
* If the pointer starts in the reserved edge area, let the event stream pass to
  the platform and parent containers.

Compose implementation note: use `WindowInsets.systemGestures` (or the current
project equivalent) to derive the reserved edge width in pixels for the current
density/layout direction. Add only a small safety margin if needed; do not
reserve the whole card padding area.

---

### **3.3 Threshold Model**

Single commit threshold, per card width:

```
COMMIT_THRESHOLD = 0.5
```

Behavior:

| Event                                             | Result                                       |
| ------------------------------------------------- | -------------------------------------------- |
| First time `|offset| >= 0.5*W` during a gesture   | fire haptic once (commit-ready cue)          |
| Release with `|offset| < 0.5*W`                   | snap back to 0; no commit                    |
| Release with `|offset| >= 0.5*W`                  | commit fires; snap back to 0; list updates   |

Implementation note: there is only one `AnchoredDraggableState` anchor
(`Idle: 0f`). The haptic cue is dispatched from a `snapshotFlow { state.offset }`
collector with a once-per-gesture `hapticConsumed` guard. Commit dispatch
lives in the `onDragStopped` callback (or equivalent), which checks the
final offset against the threshold. The user-facing benefit: dragging
past the threshold and then back off before release cancels the commit.

---

## **4. Action Dispatch (Key Integration Point)**

When swipe commits:

```
onSwipeCommit(direction):
    val action = resolveConfiguredAction(direction)
    invokeExistingActionHandler(action, itemId)
```

The "existing action handler" is the **screen-level callback** already
passed into each card composable, not a direct ViewModel call:

* `Archive`  → `onClickArchive(bookmark.id, !bookmark.isArchived)`
* `Delete`   → `onClickDelete(bookmark.id)` (this is the lambda in
  `BookmarkListScreen` that calls `stageDeleteWithSnackbar` — calling
  the ViewModel's `onDeleteBookmark` directly would bypass the
  snackbar + undo pipeline)
* `Favorite` → `onClickFavorite(bookmark.id, !bookmark.isMarked)`
  (the `BookmarkListItem` field is `isMarked`, not `isFavorite`)

The archive / favorite handlers take the **desired new state** as the
second argument, not a toggle — read the bookmark's current flag and
pass the negation.

No branching by list type here.

---

## **5. View-Specific Behavior (Handled Indirectly)**

You correctly identified this already—formalizing it:

### **Important Rule**

> Swipe does not decide list removal. Filtering does.

So:

| View                     | Archive Effect   | Removal Behavior |
| ------------------------ | ---------------- | ---------------- |
| Main                     | archived = true  | removed          |
| Archive                  | archived = false | removed          |
| Favorites                | toggle archived  | stays            |
| Articles/Videos/Pictures | toggle archived  | stays            |

Implementation:

* Swipe calls `toggleArchive()` (same as icon)
* List updates automatically via existing filtering logic

---

## **6. Visual Layer Spec**

### **6.1 Background Layer (Gmail-style growing surface)**

* A `Box` matching the card's bounds (use `Modifier.matchParentSize()` on
  the background layer inside the same parent that holds the draggable
  card).
* Inside it, a rounded colored surface aligned to the side the card is
  being pulled **away from** (i.e. swipe-left exposes a surface aligned
  to the right edge), with `width = abs(offset)` and the same corner
  radius and height as the card.
* Surface color reflects the resolved action for that direction:

  * Archive  → `secondaryContainer`
  * Delete   → `errorContainer`
  * Favorite → `tertiaryContainer`

### **6.2 Icon Behavior**

* The action icon is centered inside the growing colored surface.
* Alpha and scale ramp against the **commit threshold** (not a reveal
  threshold, which no longer exists):

  * `progress = (abs(offset) / commitPx).coerceIn(0f, 1f)`
  * `alpha = progress`
  * `scale = lerp(0.8f, 1.0f, progress)`
* Icon is fully formed at the commit point.

---

### **6.3 Motion**

#### Dragging

* Card follows finger (1:1 horizontal translation).

#### Release

* If the threshold was crossed during the drag, commit has already fired;
  the snap-to-Idle is just visual cleanup.
* Otherwise: animate to `Idle` (single anchor). Default fling behavior
  on `AnchoredDraggableState` handles this.
* Velocity-aware commit is optional polish — not required for v1.

---

## **7. Undo Handling**

* Delete → routes through the screen-level `onClickDelete` lambda which
  calls `stageDeleteWithSnackbar` → ViewModel `onDeleteBookmark` stages
  the pending deletion; the screen owns the snackbar; on Undo the
  screen calls `onCancelDeleteBookmark`, on timeout / dismissal it
  must call `onConfirmDeleteBookmark`.
* Archive → no undo in this feature.
* Favorite → toggle, no undo.

Important:

* Do not delay the commit handler for animation — trigger immediately
  on threshold crossing.
* Successive deletes must dismiss the prior snackbar in a way that
  confirms (not cancels) the prior pending deletion. The current
  screen-level dismiss-then-stage pattern handles this for icon
  presses; the same pattern must apply to swipe.

---

## **9. Settings Integration**

Persist in `SettingsDataStore` with `swipe_`-prefixed keys; expose:

```
SwipeConfig:
  enabled: Boolean
  leftAction: SwipeAction   // ARCHIVE | DELETE | FAVORITE | NONE
  rightAction: SwipeAction

Default = SwipeConfig(enabled = true,
                      leftAction = SwipeAction.DELETE,
                      rightAction = SwipeAction.ARCHIVE)
```

Resolution:

```
resolveConfiguredAction(direction):
    return config.leftAction or config.rightAction
```

If a direction resolves to `NONE`, that side of the gesture is inactive
(no commit, no background reveal, no semantics action).

---

## **10. Compose Implementation Notes**

Project is 100% Compose; the implementation uses:

* `Modifier.anchoredDraggable(Orientation.Horizontal)` with a single
  anchor at `Idle: 0f`. Free drag in both directions; fling settles
  back to `Idle`.
* `snapshotFlow { state.offset }.collect { ... }` for mid-drag commit
  detection.
* `commitConsumed` guard reset by the collector when `|offset| < 1f`
  (not by `state.settledValue`, since pre- and post-commit settled
  values are both `Idle` and the key would never change).
* `BoxWithConstraints` to compute `commitPx = 0.5f * maxWidth.toPx()`.
* The composable wraps the existing card content as the outermost
  modifier on the wrap site; the inner `combinedClickable` on the
  card body and thumbnail remains untouched.

---

## **11. Edge Cases (Don’t Skip These)**

### **A. Rapid successive swipes**

* Ensure adapter updates don’t break animation state
* Use stable IDs

---

### **B. Partial swipe + scroll interruption**

* Cancel swipe cleanly
* Snap to `Idle` (the only anchor)

---

### **C. Multi-touch**

* Ignore secondary pointers

---

### **D. Dataset mutation mid-gesture**

* If item disappears → cancel animation gracefully
