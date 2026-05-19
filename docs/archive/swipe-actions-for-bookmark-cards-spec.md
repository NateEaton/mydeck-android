# **Swipe Actions for Bookmark Cards — Functional Spec**

## **1. Purpose**

Add horizontal swipe gestures to bookmark cards to enable fast access to common actions (archive / delete), while preserving all existing interactions (tap, long press, action icons).

Swipe is an **optional accelerator**, not a replacement for existing UI controls.

---

## **2. Scope**

Applies to card-based lists in these views:

* Main list (“My List”)
* Archive
* Favorites
* Articles
* Videos
* Pictures

Swipe tracking is scoped to the bookmark card surface only. It must not be
attached to the list container, scaffold, navigation drawer, navigation rail,
or permanent drawer area. On tablet layouts, gestures that begin over the rail
or permanent drawer are navigation interactions, not bookmark-card swipe
interactions.

### Layout gating

Swipe applies only when the layout renders as a single column:

* Phone portrait, in any layout
* Compact layout on any device (Compact uses a single-column list even on
  tablets)

Multi-column grid and mosaic layouts (tablet, mobile landscape) do **not**
enable swipe. In those layouts, neighbouring cards occupy the horizontal
space that swipe would otherwise reveal, and no major app applies row-swipe
gestures to a card grid. Users in multi-column layouts use icon buttons or
multi-select for batch actions.

---

## **3. Core Interaction Model**

### **3.1 Gesture Detection**

* On pointer down over a bookmark card, begin passive tracking only.
* Do not consume pointer changes until swipe intent is locked.
* Determine gesture intent after touch slop is crossed:

  * If horizontal movement clearly dominates → **swipe**
  * If vertical movement reaches slop first or dominates → **scroll**
  * If movement remains ambiguous → keep waiting
* Long press triggers only if:

  * Movement remains within touch slop
  * Time threshold is reached
* Any horizontal movement beyond touch slop **cancels long press**
* Once a swipe is locked, keep it locked until release/cancel.
* Once scroll is locked, do not start a card swipe for that pointer sequence.

Suggested arbitration:

* Lock swipe only when `abs(dx) > touchSlop` and `abs(dx) >= 1.5 * abs(dy)`.
* Lock scroll when `abs(dy) > touchSlop` before swipe locks, or when vertical
  movement is not clearly subordinate to horizontal movement.
* Consume horizontal drag deltas only after swipe lock.

Additionally, the container applies a **vertical-intent fence**: once
`abs(dy)` crosses a smaller pre-slop threshold AND `abs(dy) >= 1.5 *
abs(dx)`, swipe is locked out for the rest of that pointer's life — even
if the user later moves cleanly sideways within the same touch. This
prevents an accidental commit when a scroll gesture changes direction
mid-touch. See the architecture spec for details. The symmetric direction
(horizontal-commit locks out late vertical motion) is already provided by
Compose's default slop-arbitration once swipe wins the pointer.

### **3.2 System and Navigation Gestures**

Compact layout currently opens the navigation drawer with a left-to-right
swipe on bookmark-list screens. This must be disabled when card swipe actions
are introduced. Users will open the drawer with the hamburger button only.

Android system back gestures must remain available. A left-to-right swipe that
starts from the screen edge/system gesture inset must not be captured by a
bookmark card. Do not apply gesture exclusion to the system back edge for this
feature.

---

### **3.3 Swipe Behavior**

Each card supports:

* **Swipe right** → configurable action (default: Archive)
* **Swipe left** → configurable action (default: Delete)

#### Commit-only (elastic snap-back)

Swipe is a single-stage commit gesture. There is no reveal-and-tap mode.

* Card translates with the finger (1:1).
* Visual feedback (colored action surface + icon) grows from the trailing
  edge as the card moves.
* **Threshold cue:** when the dragged edge of the card first reaches the
  mid-point of the card's width (i.e. `|offset| >= 0.5 * cardWidth`),
  haptic feedback fires once to confirm commit-ready state. The icon is
  fully formed at this point.
* **Commit on release past threshold:** if the finger is released while
  `|offset| >= 0.5 * cardWidth`, the action fires. The card then snaps
  back to its origin while the list state handles any consequent removal
  or re-render.
* **Back-off cancellation:** if the user crosses threshold, then drags
  back to under threshold and releases, the action does **not** fire.
  Gives the user an escape hatch from a near-miss commit.
* **Released below threshold:** card snaps back; nothing happens.

Rationale: a reveal-then-tap mode duplicates what the existing action icons
already do (tap to act) and creates a window in which tapping the card body
opens the reader instead of confirming the action. Commit-only removes that
ambiguity.

---

## **4. Actions**

### **4.1 Action Mapping**

User-configurable per direction:

* Archive (toggle)
* Delete
* Favorite (optional future)
* None (disable direction)

Default:

* Swipe right → Archive
* Swipe left → Delete

---

### **4.2 Action Execution**

Swipe actions must invoke the **same logic paths** as existing action icons:

* Archive → identical to archive icon
* Delete → identical to delete icon (including snackbar + undo)

No separate business logic for swipe.

---

## **5. View-Specific Behavior**

Swipe always performs a **state change**, not a direct list mutation.

### **5.1 Main List**

* Archive:

  * Sets archived = true
  * Item removed from list (due to filter)
* Delete:

  * Deletes item
  * Snackbar with Undo

---

### **5.2 Archive View**

* Archive swipe:

  * Toggles archived → false (unarchive)
  * Item removed from list

---

### **5.3 Favorites / Articles / Videos / Pictures**

* Archive swipe:

  * Toggles archived state (set or unset)
  * **Item remains in list**
* Delete:

  * Deletes item
  * Snackbar with Undo

Note:

* No special-case removal logic required; rely on existing filtering behavior.

---

## **6. Visual & Motion Design**

### **6.1 Swipe Feedback**

The action surface is drawn behind the card and mimics the Gmail-style
interaction:

* A rounded colored surface grows from the trailing edge of the card,
  matching the card's height and corner radius.
* Width of the colored surface tracks the absolute drag offset (so it
  appears to fill the gap the card vacates).
* Surface color reflects the action mapped to the swipe direction:

  * Archive → secondary container
  * Delete → error container
  * Favorite → tertiary container
* Action icon is centered horizontally within the growing surface and
  vertically centered against the card. It fades in and scales from
  ~0.8 → 1.0 as the offset approaches the commit threshold.
* Haptic feedback (`HapticFeedbackType.LongPress`) fires at the commit
  threshold. This is required, not optional, to match existing destructive
  action affordances.

---

### **6.2 Motion**

* Smooth horizontal drag tied to finger (1:1 translation).
* Haptic fires once on the first threshold crossing within a gesture (mid-drag).
* Release behavior:

  * `|offset| < 0.5 * cardWidth` on release → snap back to 0; no commit.
  * `|offset| >= 0.5 * cardWidth` on release → commit fires; card snaps
    back to 0 and the list state handles any consequent removal.
* Velocity-aware commit may be added as polish if the elastic snap-back
  feels sluggish on fast flicks.

---

## **7. Undo Behavior**

* Delete must trigger the existing pending-deletion snackbar with Undo
  (this is already implemented for icon-button deletes; swipe must invoke
  the same screen-level handler, not the ViewModel directly, so the
  snackbar pipeline is preserved).
* Archive has no undo in this feature. Adding archive undo is a separate
  workstream.
* Favorite is a toggle and does not require undo.
* Multiple rapid deletes must not strand prior pending deletions. Each new
  delete dismisses the prior snackbar, which must confirm (not cancel)
  the prior pending deletion.

---

## **8. Coexistence with Existing Interactions**

Must not interfere with:

* Tap → open reader
* Long press (card body) → open card context menu
* Long press (thumbnail) → open thumbnail context menu
* Action icons → unchanged
* Hamburger button → open navigation drawer
* Left-edge system gesture → system back
* Multi-select mode → swipe is disabled while multi-select is active.
  Entering multi-select must close any in-flight swipe state.

Gesture priority:

1. System back edge gesture
2. Vertical scroll
3. Horizontal card swipe
4. Long press (only if no movement)

Navigation drawer policy:

* Disable `ModalNavigationDrawer` swipe gestures on bookmark-list screens.
* Keep explicit drawer open/close actions through existing buttons and drawer
  item taps.
* Do not add a replacement drawer-edge gesture.

---

## **9. Settings**

Add user-configurable options:

* Enable/disable swipe gestures (master kill switch)
* Swipe left action (Archive / Delete / Favorite / None)
* Swipe right action (Archive / Delete / Favorite / None)

Per-direction "None" disables a single direction independently of the
master toggle.

---

## **10. Non-Goals**

* No removal of existing action icons
* No change to underlying data model
* No new actions beyond existing ones

---

## **11. Success Criteria**

* Swipe feels responsive and predictable
* No accidental triggering of long press during swipe
* No accidental triggering of swipe during vertical scroll
* No accidental triggering of vertical scroll during a locked card swipe
* Navigation drawer does not open from a left-to-right card swipe
* Left-edge Android system back gesture still works
* Swipe tracking occurs only over bookmark cards, including on tablet layouts
* Swipe is not active in multi-column grid/mosaic layouts
* Actions behave identically to icon-triggered actions
* Successive deletes confirm prior pending deletions; no orphaned snackbars
* Users can ignore swipe entirely and still use the app normally

---

## **12. Documentation & Localization**

If implementation adds settings labels, action labels, accessibility labels, or
snackbar text, add English placeholder values to every language `strings.xml`
file, following the project localization rule.

Because this is user-visible behavior, update the English user guide under
`app/src/main/assets/guide/en/` when the feature is implemented.
