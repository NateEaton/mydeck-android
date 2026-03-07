package com.mydeck.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mydeck.app.R
import com.mydeck.app.domain.model.AnnotationColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationCreationSheet(
    pendingSelection: BookmarkDetailViewModel.PendingAnnotationSelection,
    onCreateAnnotation: (
        startSelector: String,
        startOffset: Int,
        endSelector: String,
        endOffset: Int,
        color: String,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedColor by remember { mutableStateOf(AnnotationColors.default) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.annotation_create_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))

            // Preview of selected text
            val preview = pendingSelection.text.take(100).let {
                if (pendingSelection.text.length > 100) "$it…" else it
            }
            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.shapes.small
                    )
                    .padding(12.dp),
            )
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.annotation_color_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnnotationColors.all.forEach { color ->
                    AnnotationColorSwatch(
                        colorHex = color,
                        isSelected = color == selectedColor,
                        onClick = { selectedColor = color },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onCreateAnnotation(
                            pendingSelection.startSelector,
                            pendingSelection.startOffset,
                            pendingSelection.endSelector,
                            pendingSelection.endOffset,
                            selectedColor,
                        )
                    },
                ) {
                    Text(stringResource(R.string.annotation_create_button))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun AnnotationColorSwatch(
    colorHex: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val color = remember(colorHex) {
        try { Color(android.graphics.Color.parseColor(colorHex)) } catch (_: Exception) { Color.Yellow }
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
    )
}
