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

Per card, introduce a transient UI state:

```
SwipeState:
  - Idle
  - Dragging(offsetX)
  - Revealed(direction)
  - Committed(direction)
```

This state:

* Lives **only in the View layer (Compose or ViewHolder)**
* Must not leak into domain/data layer
* Is owned by the bookmark card composable / ViewHolder, not by the list,
  scaffold, drawer, rail, or shell

Gesture recognizers must be installed only on bookmark card roots. Do not put
the recognizer on `LazyColumn`, `LazyVerticalGrid`, the screen scaffold, or any
navigation container. This prevents tablet navigation areas from participating
in bookmark-card swipe tracking.

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

Define anchors (per card width):

```
REVEAL_THRESHOLD = 0.25
COMMIT_THRESHOLD = 0.65
```

Behavior:

| Position        | Result               |
| --------------- | -------------------- |
| < reveal        | snap closed          |
| reveal → commit | snap open (Revealed) |
| > commit        | trigger action       |

---

## **4. Action Dispatch (Key Integration Point)**

When swipe commits:

```
onSwipeCommit(direction):
    val action = resolveConfiguredAction(direction)
    invokeExistingActionHandler(action, itemId)
```

Examples:

* Archive → same method as archive icon
* Delete → same method as delete icon (already shows snackbar)

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

### **6.1 Background Layer**

* Fixed behind card
* Changes with direction:

  * Right → archive color
  * Left → delete color

### **6.2 Icon Behavior**

* Appears after ~10% drag
* Scales from 0.8 → 1.0 as threshold approaches
* Locks into place at reveal threshold

---

### **6.3 Motion**

#### Dragging

* Card follows finger (1:1 horizontal translation)

#### Release

* Velocity-aware:

  * High velocity → bias toward commit
* Snap rules:

  * < reveal → animate to 0
  * mid → animate to reveal anchor
  * > commit → animate offscreen

---

## **7. Hybrid Reveal + Commit Behavior**

### **Revealed State**

* Card offset fixed at reveal position
* Action button visible
* Tap button → triggers same handler as swipe commit

### **Commit State**

* Trigger action immediately
* Animate card offscreen
* Let adapter/list update handle removal

---

## **8. Undo Handling**

No special handling required:

* Delete → existing snackbar
* Archive → existing behavior (if any)

Important:

* Do not delay action for animation—trigger immediately on commit

---

## **9. Settings Integration**

Expose:

```
SwipeConfig:
  leftAction: ActionType
  rightAction: ActionType
  enabled: Boolean
```

Optional:

* requireFullSwipe: Boolean

Resolution:

```
resolveConfiguredAction(direction):
    return config.leftAction or config.rightAction
```

---

## **10. RecyclerView vs Compose Notes**

### If RecyclerView (likely given repo lineage)

Avoid vanilla `ItemTouchHelper`:

* It assumes immediate commit
* Hard to support reveal state

Instead:

* Custom `ItemTouchHelper.Callback` OR
* GestureDetector + manual translation

---

### If Compose (or migrating)

Use:

* `anchoredDraggable` (best fit)
* Anchors:

  ```
  0f → Idle
  revealPx → Revealed
  fullWidthPx → Commit
  ```

---

## **11. Edge Cases (Don’t Skip These)**

### **A. Rapid successive swipes**

* Ensure adapter updates don’t break animation state
* Use stable IDs

---

### **B. Partial swipe + scroll interruption**

* Cancel swipe cleanly
* Snap to nearest anchor

---

### **C. Multi-touch**

* Ignore secondary pointers

---

### **D. Dataset mutation mid-gesture**

* If item disappears → cancel animation gracefully
