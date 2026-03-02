Notes for low-hanging fruit:

### Reader: Text Search Bug

The very first time you search for a word the positional result counter stays at 0. So let’s say the word "cat" is present in a text 7 times. As I type in c-a-t letter by letter the total results switch around and end up at 7 by the time I type in t. However, at that point the counter will say 0/7 despite the first result already being highlighted. Only by switching between the results with the arrows will the counter reset properly, at which point the first result will actually show as 1/7.

### Reader: Title Font

Currently, the title font does not change when the font selected in the Typography settings is changed. It should.

### Navigation: Drawer Width

The drawer width appears to currently be about 95% of the screen width. I think it should be 75% of the width.

### Navigation: Drawer Format

Currently, the drawer has all fonts in a somewhat subdued emphasis. See the Settings drawer for comparison. That makes sense for the items that are views like My List, Archive, etc. since the currently selected one has a pill shape with background. However, for the items that are actions (Lables, Settings, User Guide, About) the format should be more like the Settings drawer. Also, the current "view" option should not only have the pill/background but also the same font style as the action items.

### Delete Bookmark

Currently, when you delete a bookmark the card is is removed from the list and the snackbar at bottom of the screen shows "Bookmark deleted" and the Undo buttom. If the user clicks anything other than the Undo button the bookmark is deleted at the server. If they click the Undo button the bookmark is added back to the list and the snackbar closed. To more closely match the behavior of Readeck itself, the bookmark card should remain in the list but be greyed out. If the user clicks anywhere but the Undo button the bookmark is deleted at the server. If they click the Undo button the bookmark card appearence is reset to normal and the snackbar closed.

### Reader: Colors

Currently, the background of the reader appears to not be the standard Material Design 3 color, whether in light or dark mode. It should.

Also, the reader text color doesn't appear (according to user feedback) to match the currently active theme and in particular links don't appear to have an accept color from the current theme. 

### Reader and Add Bookmark: New Label

With focus in the Labels field and on-screen keyboard displayed, once the user types a label and hits Enter key (checkmark showing representing I think Accept), the new label is added and a chip displayed above the field. However, the keyboard closes and the field loses focus. That is fine if the user just wants to enter one label and either close the Bookmark Detail dialog or use one of the buttons in the Add Bookmark sheet, but if they want to add another label they have to click on the Labels field again. A better (?) UX would be for the enter key to add the bookmark, leave focus in the Labels field and leave the keyboard visible. As that is the last field on the form (in the case of the Add Bookmark sheet) that they would type into, having to close the on-screen keyboard to use one of the bottom sheet buttons is fine if they wanted to add more than one label. If they frequently add just one label though, that may be tedious and the current functionality may be preferred. I see two possible solutions that I want to discuss:
- Have an icon to right of the Labels field that when clicked adds the currently typed label and returns focus to the Labels field. The Enter key would still work as it does so if they just hit Enter then the label is added and the keyboard closes. (Although on the Add Bookmark bottom sheet this might conflict with the Favorite button - I don't recall if we put it to right of Labels field on the FAB-initiated sheet, the intent-initiated sheet or both, or if it is along bottom between Archive and View buttons)
- Have a configuration setting (probably would warrant a new Settings option for Application Config or something along those lines, though I could see it going under User Interface) that allows the user to select between the current behavior and one in which the enter key adds the label and leaves focus in the field and the keyboard visible.




