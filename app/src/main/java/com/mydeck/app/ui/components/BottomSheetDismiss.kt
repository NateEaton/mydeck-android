package com.mydeck.app.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Animate a [ModalBottomSheet][androidx.compose.material3.ModalBottomSheet] closed, then run
 * [onClosed] once it has finished sliding out.
 *
 * Swipe/scrim dismissal already animates — the sheet slides out *before* `onDismissRequest`
 * fires. Buttons and programmatic closes that flip the caller's show-flag directly (e.g. a
 * Done/Save button that immediately sets `show = false`) skip that motion and make the sheet
 * vanish instantly. Route those through this helper so every close shares the same MD3
 * slide-out. [onClosed] is where the show-flag is actually flipped (typically the sheet's
 * `onDismiss`, or a commit callback that dismisses synchronously).
 */
@OptIn(ExperimentalMaterial3Api::class)
fun CoroutineScope.dismissSheet(sheetState: SheetState, onClosed: () -> Unit) {
    launch { sheetState.hide() }.invokeOnCompletion {
        if (!sheetState.isVisible) onClosed()
    }
}
