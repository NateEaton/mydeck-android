## User:

Before I start working on that, I've discovered a bug related to the Labels view functionality. I'm not looking for help fixing the bug but it raised a concern about whether the Labels selection/view solution in place follows the right paradigm, both in terms of standard UI design and also M3 standards. Here's a summary: 
- the main list view window generally shows a filtered set of bookmarks, with all unarchived (My List), archived (Archived) or favorites (Favorites). 
- if the user selects Labels from side menu, they got an alternative list via that shows available labels with counts. If the select one, they get a list view similar to the main one but with a subheading showing the selected label and a list of bookmarks filtered on the selected label. 
- they can end up on that same view if they are in the main list in Mosaic layout and click on a label chip 
- although the current solution works well and is understandable and includes ways to edit a label or delete it, it lacks a way to search the available labels (though the planned list filter may help address that)
- the bug is that if they are on the list view showing a selected label and its bookmarks and then pick the Labels option from side menu, they get the list of available labels but the subheading of the previously selected label is still shown. 

The list of available labels works much like it does in the native Readeck app but a) that's optimized for a large layout with the rail and sidebar menus and b) it's not Material Design. 

With your understanding of the repo, goals for M3 compliance and the filter/tablet features, is there an alternative UI solution you would suggest for label selection/filtering? Apart from the M3 goals, keep in mind this is a Readeck client and needs to be familiar to a Readeck user. 

No code, just a design proposal.

## Agent:

Based on the architecture of the app, the Material Design 3 guidelines, and the functionality of the native Readeck web interface, I recommend pivoting the mental model of Labels from **"A Navigation Destination"** to **"A Filter State."**

Currently, the app treats "Labels" as a sibling to "My List" or "Archive." This is the source of your state bug and feels slightly "web-like." In a modern Android content app, Labels are attributes used to slice the data, not separate rooms in the house.

Here is the design proposal to resolve this conflict and align with your upcoming features.

### The Proposal: Labels as a Modal Filter

Instead of navigating the user away from their content to a "Labels Screen," keep the user on the Bookmark List and bring the Labels to them via a **Modal Bottom Sheet**.

#### 1. Entry Points
*   **Navigation Drawer:** Keep "Labels" in the drawer. However, tapping it does not navigate to a new screen. Instead, it opens the **Labels Bottom Sheet**.
*   **Bookmark Card:** Tapping a Label Chip on a card immediately applies that filter (closes the sheet if open) and refreshes the current list.

#### 2. The Labels Bottom Sheet (Mobile) / Side Sheet (Tablet)
When "Labels" is tapped, a sheet slides up (mobile) or out (tablet):
*   **Header:** "Labels" with a search/filter bar at the top (M3 Search Bar) to quickly find a label in the list.
*   **List Content:** A `LazyColumn` of `ListItem`s.
    *   **Leading:** Optional Label Icon (hash or tag).
    *   **Headline:** Label Name.
    *   **Trailing:** The count badge (SurfaceVariant colored container).
*   **Management:** Long-pressing a row (or tapping a 3-dot menu on the row) triggers the "Edit/Delete" dialogs for that label. This keeps management contextual but secondary to selection.

#### 3. The Resulting View (The "Filtered" State)
When a label is selected from the sheet:
1.  The Sheet dismisses.
2.  The underlying Bookmark List refreshes.
3.  **Crucial Change:** Do not use a custom "Subheading" text element below the TopAppBar.
4.  Instead, rely on the **Filter Summary Bar** (from your upcoming Filter Feature spec).
    *   The selected label appears as a selected, dismissible `InputChip` (e.g., `[ x Label: Cooking ]`) immediately below the TopAppBar.
    *   The TopAppBar title remains "My List" (or "Archive"), providing context that you are looking at "My List *filtered by* Cooking".

### Alignment with Upcoming Features

This approach integrates perfectly with your roadmap:

*   **Filtered List Feature:** You are already planning a "Summary Bar" for active filters. By treating Labels as just another filter (exactly like "Read" or "Video"), you unify the UI logic. You don't need a special "Label View" screen; you just need the Bookmark List to support a `label` filter parameter.
*   **Tablet Form Factor:**
    *   On Tablet, a full-screen list of labels is visually sparse and wastes space.
    *   Using a **Modal Side Sheet** (standard M3 component) for the Label List allows the user to browse labels on the right while seeing their bookmarks on the left. It feels like a productivity tool rather than a mobile list.

### Why this is better than the current implementation

1.  **Eliminates the Navigation Bug:** Since you aren't navigating to a new "Screen," you don't have to manage a complex back stack or state restoration when switching between "Labels" and "Bookmarks." The user is always on `BookmarkListScreen`, just with different query parameters.
2.  **M3 Compliance:** It utilizes `ModalBottomSheet` and `InputChip`, which are the canonical M3 ways to handle selection and filtering.
3.  **Visual Hierarchy:** It prevents the "double header" look (TopAppBar + Subheader) which often causes scrolling/elevation visual glitches.
4.  **Readeck Familiarity:** It preserves the "List of Labels with counts" that Readeck users expect, but presents it in an ephemeral layer that doesn't lose the context of the user's content.

### Summary of Changes (Design Level)

| Feature | Current | Proposed |
| :--- | :--- | :--- |
| **Drawer Action** | Navigates to `LabelsScreen` | Opens `LabelsBottomSheet` |
| **Label List UI** | Full screen list | Searchable list inside Bottom/Side Sheet |
| **Filtered View** | `LabelsScreen` with sub-header | `BookmarkListScreen` with Filter Chip |
| **Label Management** | Icons on the list row | Context menu (long press) in Sheet |

This creates a highly "native" feel—similar to how Gmail handles label changing or how Google Photos handles folder selection—while solving your architectural state issue.
