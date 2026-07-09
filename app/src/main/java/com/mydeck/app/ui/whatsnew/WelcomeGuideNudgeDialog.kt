package com.mydeck.app.ui.whatsnew

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mydeck.app.R

@Composable
fun WelcomeGuideNudgeDialog(
    onOpenGuide: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.whats_new_guide_nudge_title)) },
        text = { Text(stringResource(R.string.whats_new_guide_nudge_body)) },
        confirmButton = {
            TextButton(onClick = onOpenGuide) {
                Text(stringResource(R.string.whats_new_guide_nudge_open_guide))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.whats_new_guide_nudge_not_now))
            }
        }
    )
}
