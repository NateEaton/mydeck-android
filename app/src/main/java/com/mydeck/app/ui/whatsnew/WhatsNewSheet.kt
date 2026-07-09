package com.mydeck.app.ui.whatsnew

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.text.method.LinkMovementMethod
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import com.mydeck.app.R
import com.mydeck.app.ui.components.dismissSheet
import com.mydeck.app.ui.userguide.applyMarkwonColors
import com.mydeck.app.ui.userguide.rememberMarkwon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(
    version: String,
    content: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val markwon = rememberMarkwon(onSectionNavigate = {})

    ModalBottomSheet(
        onDismissRequest = { scope.dismissSheet(sheetState) { onDismiss() } },
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = stringResource(R.string.whats_new_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = version,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AndroidView(
                factory = { ctx ->
                    TextView(ctx).apply {
                        setPadding(0, 32, 0, 0)
                        setTextIsSelectable(true)
                        movementMethod = LinkMovementMethod.getInstance()
                    }
                },
                update = { textView ->
                    applyMarkwonColors(textView, colorScheme)
                    if (textView.tag != content) {
                        markwon.setMarkdown(textView, content)
                        textView.tag = content
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            TextButton(
                onClick = { scope.dismissSheet(sheetState) { onDismiss() } },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.whats_new_got_it),
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}
