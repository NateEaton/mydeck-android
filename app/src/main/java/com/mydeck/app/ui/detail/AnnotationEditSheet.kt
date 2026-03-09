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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mydeck.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationEditSheet(
    state: BookmarkDetailViewModel.AnnotationEditState,
    onColorSelected: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirmation by remember(state.annotationIds) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(
                    if (state.hasExistingAnnotations) {
                        R.string.highlight_edit
                    } else {
                        R.string.highlight_create
                    }
                ),
                style = MaterialTheme.typography.titleLarge
            )

            if (state.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = state.text.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                colorOptions().forEach { option ->
                    AnnotationColorOption(
                        colorName = option.colorName,
                        label = stringResource(option.labelRes),
                        selected = state.color == option.colorName,
                        onClick = { onColorSelected(option.colorName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.save))
            }

            if (state.hasExistingAnnotations) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirmation = true },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.highlight_delete))
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    }
                ) {
                    Text(text = stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
            text = {
                Text(text = stringResource(R.string.highlight_delete_confirm))
            }
        )
    }
}

@Composable
private fun AnnotationColorOption(
    colorName: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .then(
                    if (colorName == "none") {
                        Modifier.border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                    } else {
                        Modifier.background(annotationColorForName(colorName))
                    }
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = if (colorName == "none") {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        Color.Black.copy(alpha = 0.78f)
                    }
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

private fun annotationColorForName(colorName: String): Color {
    return when (colorName) {
        "red" -> Color(0xFFEF5350)
        "blue" -> Color(0xFF42A5F5)
        "green" -> Color(0xFF66BB6A)
        "none" -> Color.Transparent
        else -> Color(0xFFFFEB3B)
    }
}

private fun colorOptions(): List<ColorOption> {
    return listOf(
        ColorOption("yellow", R.string.highlight_color_yellow),
        ColorOption("red", R.string.highlight_color_red),
        ColorOption("blue", R.string.highlight_color_blue),
        ColorOption("green", R.string.highlight_color_green),
        ColorOption("none", R.string.highlight_color_none)
    )
}

private data class ColorOption(
    val colorName: String,
    val labelRes: Int
)
