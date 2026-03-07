package com.mydeck.app.ui.list

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import com.mydeck.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val AUTO_SAVE_SECONDS = 5

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddBookmarkSheet(
    url: String,
    title: String,
    urlError: Int?,
    isCreateEnabled: Boolean,
    labels: List<String>,
    isFavorite: Boolean = false,
    existingLabels: List<String> = emptyList(),
    onUrlChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onLabelsChange: (List<String>) -> Unit,
    onFavoriteToggle: ((Boolean) -> Unit)? = null,
    onCreateBookmark: () -> Unit,
    mode: SheetMode = SheetMode.IN_APP,
    onAction: ((SaveAction) -> Unit)? = null,
    onInteraction: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()

    val autoSaveProgress = remember { Animatable(1f) }
    var autoSaveSecondsRemaining by remember { mutableIntStateOf(AUTO_SAVE_SECONDS) }
    var autoSaveCancelled by remember { mutableStateOf(false) }

    // Auto-save timer for share intent mode: smooth progress bar + integer countdown text
    if (mode == SheetMode.SHARE_INTENT && !autoSaveCancelled && urlError == null) {
        LaunchedEffect(Unit) {
            launch {
                autoSaveProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = AUTO_SAVE_SECONDS * 1000,
                        easing = LinearEasing
                    )
                )
            }
            while (autoSaveSecondsRemaining > 0) {
                delay(1000L)
                autoSaveSecondsRemaining--
            }
            onAction?.invoke(SaveAction.ADD)
        }
    }

    val cancelAutoSave = {
        if (!autoSaveCancelled) {
            autoSaveCancelled = true
            onInteraction?.invoke()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .pointerInput(autoSaveCancelled) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        cancelAutoSave()
                    }
                }
            }
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with integrated countdown timer
        if (mode == SheetMode.SHARE_INTENT) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_brand_logo),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp)
                )
                Text(
                    text = stringResource(id = R.string.save_to_mydeck),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                // Circular countdown indicator - always occupies space to prevent layout shift
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (mode == SheetMode.SHARE_INTENT && !autoSaveCancelled && urlError == null) {
                        // Background track
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            strokeWidth = 3.dp
                        )
                        // Progress indicator
                        CircularProgressIndicator(
                            progress = { autoSaveProgress.value },
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        // Countdown number
                        Text(
                            text = autoSaveSecondsRemaining.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        } else {
            Text(
                text = stringResource(id = R.string.add_bookmark),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        OutlinedTextField(
            value = url,
            onValueChange = {
                onUrlChange(it)
                cancelAutoSave()
            },
            isError = urlError != null,
            label = { Text(stringResource(id = R.string.url)) },
            supportingText = {
                urlError?.let { Text(text = stringResource(it)) }
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = title,
            onValueChange = {
                onTitleChange(it)
                cancelAutoSave()
            },
            label = { Text(stringResource(id = R.string.title)) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) cancelAutoSave() }
        )

        CreateBookmarkLabelsSection(
            labels = labels,
            isFavorite = isFavorite,
            existingLabels = existingLabels,
            onOpenLabelPicker = cancelAutoSave,
            onSetLabels = { updatedLabels ->
                onLabelsChange(updatedLabels)
                cancelAutoSave()
            },
            onRemoveLabel = { label ->
                onLabelsChange(labels.filter { it != label })
                cancelAutoSave()
            },
            onFavoriteToggle = { onFavoriteToggle?.invoke(it) }
        )

        Spacer(modifier = Modifier.size(4.dp))

        // Action buttons — same layout for both modes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    if (onAction != null) onAction.invoke(SaveAction.ARCHIVE) else onCreateBookmark()
                },
                enabled = isCreateEnabled
            ) {
                Text(stringResource(id = R.string.action_archive))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (onAction != null) onAction.invoke(SaveAction.VIEW) else onCreateBookmark()
                    },
                    enabled = isCreateEnabled
                ) {
                    Text(stringResource(id = R.string.action_view_bookmark))
                }
                Button(
                    onClick = {
                        if (onAction != null) onAction.invoke(SaveAction.ADD) else onCreateBookmark()
                    },
                    enabled = isCreateEnabled
                ) {
                    Text(stringResource(id = R.string.add))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateBookmarkLabelsSection(
    labels: List<String>,
    isFavorite: Boolean = false,
    existingLabels: List<String> = emptyList(),
    onOpenLabelPicker: () -> Unit = {},
    onSetLabels: (List<String>) -> Unit,
    onRemoveLabel: (String) -> Unit,
    onFavoriteToggle: (Boolean) -> Unit = {}
) {
    var showLabelPicker by remember { mutableStateOf(false) }
    val labelOptions = remember(existingLabels, labels) {
        (existingLabels + labels).distinct().associateWith { 0 }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.detail_labels),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (labels.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                labels.forEach { label ->
                    LabelChip(
                        label = label,
                        onRemove = { onRemoveLabel(label) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        onOpenLabelPicker()
                        showLabelPicker = true
                    },
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = if (labels.isEmpty()) {
                            stringResource(R.string.add_labels)
                        } else {
                            stringResource(R.string.edit_labels)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (labels.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.labels_selected_count, labels.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            IconButton(onClick = { onFavoriteToggle(!isFavorite) }) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(R.string.action_favorite),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showLabelPicker) {
            LabelPickerBottomSheet(
                labels = labelOptions,
                mode = LabelPickerMode.MultiSelect(
                    initialSelection = labels.toSet(),
                    onDone = { selectedLabels ->
                        val updatedLabels =
                            labels.filter { selectedLabels.contains(it) } +
                                selectedLabels.filterNot { labels.contains(it) }.sorted()
                        onSetLabels(updatedLabels)
                    }
                ),
                onDismiss = { showLabelPicker = false }
            )
        }
    }
}

@Composable
private fun LabelChip(
    label: String,
    onRemove: () -> Unit = {}
) {
    Card(
        modifier = Modifier.padding(4.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f, fill = false)
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.remove_label),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
