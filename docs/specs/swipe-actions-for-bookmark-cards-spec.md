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

#### Two-stage interaction:

**A. Partial Swipe (Reveal State)**

* At ~20–30% width:

  * Action background + icon revealed
* On release:

  * Card snaps open (action visible)
  * User may tap revealed action button to confirm

**B. Full Swipe (Commit State)**

* At ~60–70% width:

  * Action triggers immediately
  * Card animates out
  * No additional tap required

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

* Background color reflects action:

  * Archive → neutral / secondary container
  * Delete → error container
* Action icon appears and scales with swipe progress
* Optional haptic feedback at commit threshold

---

### **6.2 Motion**

* Smooth horizontal drag tied to finger
* Release behavior:

  * < reveal threshold → snap closed
  * between thresholds → snap open (revealed)
  * > commit threshold → animate offscreen + execute action

---

## **7. Undo Behavior**

* Delete must trigger existing snackbar with Undo
* Archive may optionally support Undo (if already implemented)
* Multiple rapid actions should not break undo flow

---

## **8. Coexistence with Existing Interactions**

Must not interfere with:

* Tap → open reader
* Long press (card) → share
* Long press (thumbnail) → share image
* Action icons → unchanged
* Hamburger button → open navigation drawer
* Left-edge system gesture → system back

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

* Swipe left action
* Swipe right action
* Optional:

  * Enable/disable swipe gestures
  * Require full swipe to trigger (vs reveal-only mode)

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
* Actions behave identically to icon-triggered actions
* Users can ignore swipe entirely and still use the app normally

---

## **12. Documentation & Localization**

If implementation adds settings labels, action labels, accessibility labels, or
snackbar text, add English placeholder values to every language `strings.xml`
file, following the project localization rule.

Because this is user-visible behavior, update the English user guide under
`app/src/main/assets/guide/en/` when the feature is implemented.
