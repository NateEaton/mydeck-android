# Reader / Details Management Tweaks

## Summary

This mini-spec adjusts the bookmark reading experience so reader-only tools stay in the reader surface, while bookmark metadata and content-management actions move into a dedicated edit flow launched from the existing Details surface.

The goal is to make the reading UI feel closer to Readeck's separation of reading vs. management, without introducing a new top-bar icon or changing the current Details dialog architecture.

## Scope

This change includes:

- showing the bookmark description in italics under the reader title
- removing inline title editing from the reader header
- keeping **Highlights** in the reader overflow menu
- moving **Refresh content** out of the reader overflow menu
- showing the bookmark title inside **Details** as a metadata row with an edit affordance
- adding a full-screen metadata editor launched from the title row in **Details**
- placing **Refresh content** on **Details** below the labels action

This change does not include:

- moving labels out of the current Details layout
- converting Details into a bottom sheet or tablet side panel
- pull-to-refresh for bookmark content

## UX

### Reader header

- The reader header continues to show the bookmark title.
- If the bookmark has a description, it appears directly below the title in italic styling.
- The header title is no longer editable inline.

### Reader overflow

- **Highlights** stays in the overflow menu for article bookmarks in reader mode.
- **Refresh content** is removed from the overflow menu.

### Details

- Details remains a metadata view.
- The thumbnail stays at the top.
- The title appears directly under the thumbnail as a tappable row with a pencil icon.
- Details shows site name, added date, published date, author, site root URL, reading time, word count, labels, and debug info.
- Description is not repeated in Details because it already appears under the reader title.

### Metadata editor

- Tapping the title row in Details opens a full-screen metadata editor.
- The editor contains title, description, site name, authors, published date, language, and text direction.
- The editor uses a close **X** in the top app bar.
- Closing the editor returns to Details.
- The published date field uses the platform date picker.
- The footer keeps **Save** on the right.

## Technical notes

- Content refresh should reuse the existing forced article refresh flow.
- Metadata editing should use the current bookmark `PATCH /bookmarks/{id}` route, which now supports title, description, site name, authors, published date, language, and text direction.
- Local-first behavior should be preserved by storing metadata edits in the local bookmark row and queuing them for pending-action sync.

## Documentation

- Update the English reading guide to describe:
  - the italic description under the title
  - metadata editing from the title row in Details instead of inline in the header
  - **Refresh content** living in Details rather than the overflow menu
