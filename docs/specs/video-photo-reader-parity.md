# Video and Photograph Reader Parity

**Status:** Implemented
**Date:** 2026-03-02

---

## Motivation

Article bookmarks have a richer reading experience than Video and Photograph bookmarks:
typography controls, Find in Page, and reader-mode content all work for articles but were absent or incomplete for the other two types. Additionally, all three types exposed a pencil icon next to the title for editing, adding visual noise that the tap target alone can serve.

This spec covers the changes needed to bring Video and Photograph reading views to parity with Article, fix the content pipeline for those types, and streamline title editing across all three.

---

## Changes

### 1. Typography and Find in Page for Video and Photograph

**Before:** The Typography (Aa) and Find in Page (🔍) icons in the top bar were only shown when the bookmark type was `ARTICLE` and content mode was `READER`.

**After:** Both icons are shown for `ARTICLE`, `VIDEO`, and `PHOTO` types when content mode is `READER`.

The Find in Page icon's content description is updated from the article-specific `action_search_in_article` string to the generic `action_find_in_page`.

New string resources added (English + placeholder in all 9 locale files):
- `action_find_in_page` — "Find in Page"
- `action_view_photograph` — "View Photograph"

---

### 2. "View Photograph" Content Mode Toggle

**Before:** The overflow menu offered a "View Original / View Video" toggle for `VIDEO` bookmarks but had no equivalent for `PHOTO` bookmarks.

**After:** A new branch handles `PHOTO` bookmarks with a "View Original / View Photograph" toggle using `Icons.Default.Image`.

| Type    | READER mode label    | ORIGINAL mode label |
|---------|----------------------|---------------------|
| ARTICLE | View Article         | View Original       |
| VIDEO   | View Video           | View Original       |
| PHOTO   | View Photograph      | View Original       |

---

### 3. Media-Above-Text Layout

**Before:** For `VIDEO` and `PHOTO` types, the generated HTML placed the article text before the media element (embed or image).

**After:** Media is rendered first, followed by any article text:

- `PHOTO`: `<img>` then text part
- `VIDEO`: embed `<div>` then text part

---

### 4. Article Content Pipeline for Video and Photograph

#### Background

The Readeck API returns `has_article: true` on Video and Photograph bookmarks when extracted text content is available. This content is served via `GET bookmarks/{id}/article` and is richer than the plain-text `description` field on the main bookmark response.

The reading view uses the following fallback chain to build the text portion of reader-mode content:

```
articleContent (HTML from /article endpoint)
  └── if null → description (plain text from bookmark metadata)
         └── if blank → empty string
```

#### Problem

`BookmarkDetailViewModel.initializeBookmark()` only called `fetchContentOnDemand()` for `ARTICLE` type bookmarks. For `VIDEO` and `PHOTO`, it called `refreshBookmarkFromApi()` (metadata only) but never triggered an article content fetch. As a result:

- If `BatchArticleLoadWorker` had already run, `articleContent` was populated and the richer HTML was shown.
- If the worker had not yet run (e.g. newly created bookmark, first open), `articleContent` was `null` and only the plain-text `description` was used.

The batch sync path (`BatchArticleLoadWorker` → `LoadArticleUseCase`) was correct — it queries all bookmarks with `hasArticle = 1` regardless of type — only the on-demand path was missing.

#### Fix

For `VIDEO` and `PHOTO` types, after `refreshBookmarkFromApi()`, check `contentState` and call `fetchContentOnDemand()` when content has not yet been downloaded:

```
DOWNLOADED          → no-op (content already available)
PERMANENT_NO_CONTENT → no-op (server confirmed no content)
any other state     → fetchContentOnDemand(id)
```

`LoadArticleUseCase` already handles the `hasArticle = false` case by returning `PermanentFailure` and updating `contentState`, so no guard is needed at the call site.

---

### 5. Title Editing UX

**Before:** A pencil (`Icons.Outlined.Edit`) `IconButton` appeared to the right of the title. Tapping either the pencil or the title entered edit mode. A checkmark (`Icons.Default.Check`) `IconButton` appeared to the right of the text field to confirm the save.

**After:**

- The pencil icon is removed. The title is the only tap target to enter edit mode.
- The title `Text` fills the full content width (`Modifier.fillMaxWidth()`).
- The edit `OutlinedTextField` is `singleLine = true` with `ImeAction.Done`, so pressing Enter on the keyboard saves the change and exits edit mode.
- The checkmark icon is removed.

This change applies to `BookmarkDetailHeader`, which is rendered for all three bookmark types in reader mode.

---

## Affected Files

| File | Change |
|------|--------|
| `BookmarkDetailTopBar.kt` | Show Typography + Find in Page icons for VIDEO and PHOTO |
| `BookmarkDetailMenu.kt` | Add View Photograph toggle for PHOTO type |
| `BookmarkDetailViewModel.kt` | Media-above-text layout; on-demand content fetch for VIDEO/PHOTO |
| `BookmarkDetailHeader.kt` | Remove pencil/checkmark icons; full-width title; Enter saves |
| `values/strings.xml` | Add `action_find_in_page`, `action_view_photograph` |
| `values-{locale}/strings.xml` | English placeholders in all 9 locale files |
| `assets/guide/en/reading.md` | Update Video View, Picture View, Typography sections |

---

## Out of Scope

- Translating the two new strings into non-English locales (placeholder values are English)
- Find in Page or Typography in `ORIGINAL` content mode (WebView-based; not applicable)
- Title editing outside of reader mode (list view, details dialog)
