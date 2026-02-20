# Feature Proposal: Highlights Support

## Overview
Add support for text highlights in bookmark articles, with cross-device sync via Readeck server.

## Current Status
- **Readeck Server**: Full highlights support exists (v0.17+)
- **Readeck iOS App**: Implemented with 4 colors and cross-device sync (v1.2.0+)
- **MyDeck Android**: Not yet implemented
- **Planned**: Readeck 0.22 will bring full annotations support (highlights + notes)

## Implementation Complexity
**Medium** - Estimated 12-17 hours (1.5-2 days)

## Technical Architecture

### Backend (Already Exists)
Readeck server has complete highlights infrastructure:
- Database storage for highlights (separate from immutable bookmark content)
- RESTful API endpoints for CRUD operations
- Selector + offset positioning system (similar to W3C Web Annotation spec)
- Cross-device sync capability

### Data Model
Based on Readeck's `annotate` package implementation:

```kotlin
data class Highlight(
    val id: String,
    val bookmarkId: String,
    val startSelector: String,  // CSS selector for start element
    val endSelector: String,    // CSS selector for end element
    val startOffset: Int,        // Character offset within start element
    val endOffset: Int,          // Character offset within end element
    val color: HighlightColor,   // yellow, red, blue, green
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

enum class HighlightColor {
    YELLOW, RED, BLUE, GREEN
}
```

### API Endpoints
Based on iOS app and server implementation:

```
POST   /api/bookmarks/:id/highlights     # Create highlight
GET    /api/bookmarks/:id/highlights     # Get highlights for bookmark
PUT    /api/highlights/:id               # Update highlight (change color)
DELETE /api/highlights/:id               # Delete highlight
```

## Implementation Plan

### Phase 1: Data Layer (2-3 hours)
- [ ] Create domain model for Highlight
- [ ] Add database table and DAO for local storage
- [ ] Implement network API service for highlight endpoints
- [ ] Create repository for highlight CRUD operations
- [ ] Add sync logic to fetch highlights with bookmarks

### Phase 2: WebView Integration (4-6 hours)
- [ ] Implement text selection detection in article WebView
- [ ] Calculate CSS selectors and text offsets for user selections
- [ ] Create JavaScript bridge for:
  - Applying highlight styles when article loads
  - Detecting user text selection
  - Handling highlight click events
  - Removing/modifying existing highlights
- [ ] Test highlight persistence across theme changes and zoom levels

### Phase 3: UI Components (2-3 hours)
- [ ] Color picker dialog (4 color options)
- [ ] Highlight action menu (edit color, delete)
- [ ] Highlights list in bookmark detail page
- [ ] Click-to-jump functionality (scroll to highlight in article)
- [ ] Visual feedback for highlight operations

### Phase 4: Sync & Persistence (2 hours)
- [ ] Fetch highlights when loading bookmark
- [ ] Push new/modified highlights to server
- [ ] Handle offline mode (queue operations)
- [ ] Conflict resolution for multi-device edits

### Phase 5: Testing & Polish (2-3 hours)
- [ ] Test overlapping highlights
- [ ] Test highlights across content updates
- [ ] Test cross-device sync
- [ ] Performance testing with many highlights
- [ ] Edge case handling (deleted content, modified articles)

## Technical Challenges

### 1. Text Selection & Position Tracking
- **Challenge**: Accurately capturing and restoring text positions in HTML
- **Solution**: Use Readeck's proven selector + offset approach
- **Reference**: Readeck annotate package, iOS app implementation

### 2. WebView-Compose Integration
- **Challenge**: Coordinating native Android UI with WebView content
- **Solution**: Use JavaScript bridge pattern with message passing
- **Reference**: Existing search-in-article feature uses similar approach

### 3. Performance with Many Highlights
- **Challenge**: Large number of highlights could slow rendering
- **Solution**:
  - Lazy loading of highlight data
  - Efficient DOM manipulation
  - Virtualization if needed

## References

### Code References
- [Readeck Annotate Package](https://pkg.go.dev/codeberg.org/readeck/readeck/pkg/annotate)
- [Readeck iOS App](https://github.com/ilyas-hallak/readeck-ios)
- [Readeck Browser Extension](https://codeberg.org/readeck/browser-extension)

### Related Integrations
- **Readel (Emacs)**: Retrieves annotations and displays in Org-mode
- **Obsidian Importer**: Imports bookmarks with annotations
- **KoReader Plugin**: Syncs annotations back to Readeck

## Next Steps

1. **API Discovery**: Test highlight endpoints using curl/Postman to understand exact request/response format
2. **iOS App Study**: Review iOS implementation for best practices
3. **Proof of Concept**: Build minimal highlight creation to validate approach
4. **Iterative Development**: Follow phased plan above

## Notes

- Highlights are **mutable** (unlike bookmark content which is immutable)
- Server stores highlights separately from ZIP-archived content
- Position tracking must handle dynamic content (responsive layouts, font size changes)
- Color scheme should follow Material Design guidelines while supporting 4 Readeck colors
- Consider accessibility: ensure highlights work with screen readers

---

**Status**: Proposal stage
**Priority**: Medium (nice-to-have feature enhancement)
**Dependencies**: None (server support already exists)
