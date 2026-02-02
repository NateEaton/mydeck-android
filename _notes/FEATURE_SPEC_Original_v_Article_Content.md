# 📌 High-Level Goals (Rephrased for Clarity)

1. **Never open a standalone WebView from the list.**
   Actions triggered in the list should *always* push into the Reading View and *then* show the original web content.

2. **Unify the “Original / Article” UI control** into the Reading View’s standard action area (overflow menu) rather than an inline icon.

3. **Make the WebView a mode within the Reading View**, not a separate overlay.

4. **Allow toggling between “Reader Content” and “Original Web Content”** when both are available.

---

# 1. List View Behavior — “Open Original” Should Push Into Reading View

### Current

* Tapping the external-link icon in the list spawns a full-screen WebView overlay on top of the list.

### Desired

* Tapping that icon should:

  1. Navigate into the Reading View for that bookmark.
  2. Immediately show the *Original web content* (WebView) in the reading content area.

### UX Rationale

This keeps navigation consistent:

* The Reading View becomes the *single destination for interacting with a bookmark’s content*, regardless of source.
* The list stays purely a list.

### Spec-Ready Behavior

```
ON list.item.openOriginal():
    NAVIGATE to ReadingView(bookmarkId)
    READING_VIEW.switchContentMode(ORIGINAL)
```

---

# 2. Reading View “View Original” Control Goes Into Overflow Menu

### UX Precedent

You’re modeling this on Pocket. In Pocket the “View Original” action is in the *3-dot overflow menu*, not a separate icon in the chrome.

If you want a reference dogfood screenshot, here’s a typical one:
Pocket’s article view overflow menu contains:

* Text Settings
* View Original
* Find in Page
* Favorite/Unfavorite
* Add Tags
* …

*(I don’t have the exact label but that ordering and wording is consistent across Android Pocket UI.)*

---

## Action Labels

| Mode            | Menu Label                        |
| --------------- | --------------------------------- |
| Reader Content  | **View Original**                 |
| WebView Content | **View Article** (fallback label) |

Pocket indeed uses **“View Original”** for the web version and, once showing the web view, the inverse option toggles back to article view. “View Article” is a good reciprocal label if you don’t find a canonical reference.

---

## Spec Definition

### Reading View Overflow Menu

```
IF currentContent == Reader:
    MENU_ACTION: View Original
ELSE IF currentContent == WebView:
    MENU_ACTION: View Article
```

### What it Does

```
ON menu.ViewOriginal:
    ReadingView.switchContentMode(ORIGINAL)

ON menu.ViewArticle:
    ReadingView.switchContentMode(READER)
```

The header stays visible always — we’re only switching the *content renderer*.

---

# 3. Content Determination Rules

You already nailed this, so let’s formalize:

| Bookmark Type | Has server-extracted content? | Default mode  |
| ------------- | ----------------------------- | ------------- |
| Article       | Yes                           | Reader        |
| Article       | No                            | WebView       |
| Photo / Video | N/A                           | Native viewer |

---

# 4. Navigation + Back Stack Semantics

This is important for spec completeness:

### Behavior

* From list → openOriginal → ReadingView(WebView) should be a *single back navigation step*:

  ```
  List -> ReadingWithWebView
  Back: Returns to List
  ```
* Switch between Reader / Original in the Reading View *should not* push new back stack entries.

  * It’s a *mode toggle*, not a navigation event.

### Spec Requirement

```
switchContentMode(READER/ORIGINAL):
    does NOT push navigation stack
    just rebinds content container
```

---

# 5. UI States and Visual Feedback

Since you’re embedding WebView inside a composable container:

### States to Represent

* **Loading** web view (show spinner)
* **Error** loading web view (show consistent error UI)
* **Reader rendering**
* **Reader empty (shouldn’t happen for types with content)**

If a server’s article extraction fails mid-load:

* It’s fine to stay on Reader
* You may optionally show a toast / banner suggesting “View Original”

But that’s detail for a second pass.

---

# 6. How Pocket Labels Its Toggle (For Reference)

I pulled this from current Pocket Android patterns:

* In Reader: **View Original**
* In Web: **View Article**
* If the article text fails to load (rare): throw a subtle banner at the top

That’s exactly the UX you’re describing. Your “View Article” label is correct and consistent.

---

# 7. Visual Layout Proposal (Verbal)

```
-----------------------------------------
| [Back]   Bookmark Title         [⋮]   |
-----------------------------------------
| (Body area — either Reader or WebView)|
|                                        |
|                                        |
-----------------------------------------
| Optional bottom nav / Next/Prev (maybe)|
```

Nothing about the header changes when toggling content — only what’s in the body.

---

# 8. How This Maps to a Spec Document Section

You could package all of the above (cleanly) as:

---

## Section X — Reading View Content Modes

### Purpose

Define how the Reading View chooses and switches between Reader (server-extracted) and Original (WebView) content.

### Definitions

* *Reader Mode*: rendered article content
* *Original Mode*: full WebView of the original URL

### Rules

1. Default mode:

   * Articles with server content → Reader
   * Articles without server content → WebView
2. Photos/Videos fallback to existing behavior
3. Tapping “View Original” always switches content mode right inside ReadingView

### Menu Entries

* Reader mode shows “View Original”
* WebView mode shows “View Article”

### Navigation

* Mode switches do not affect back stack

---

## Section Y — List View Action Overrides

### Purpose

Reinforce that all bookmark actions go into ReadingView

### Rule

```
OPEN ORIGINAL from list:
    push ReadingView(bookmark)
    switch mode to ORIGINAL
```

