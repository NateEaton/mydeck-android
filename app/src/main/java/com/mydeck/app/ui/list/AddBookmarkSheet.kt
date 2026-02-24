package com.mydeck.app.ui.list

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import com.mydeck.app.R
import com.mydeck.app.ui.components.LabelAutocompleteTextField
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
    var newLabelInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
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
        // Header
        if (mode == SheetMode.SHARE_INTENT) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_brand_logo),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    text = stringResource(id = R.string.save_to_mydeck),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            Text(
                text = stringResource(id = R.string.add_bookmark),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Auto-save timer indicator
        if (mode == SheetMode.SHARE_INTENT && !autoSaveCancelled && urlError == null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { autoSaveProgress.value },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.share_auto_save_countdown, autoSaveSecondsRemaining),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
            newLabelInput = newLabelInput,
            isFavorite = isFavorite,
            existingLabels = existingLabels,
            onNewLabelChange = {
                newLabelInput = it
                cancelAutoSave()
            },
            onFocusLabel = cancelAutoSave,
            onAddLabel = {
                if (newLabelInput.isNotBlank()) {
                    val trimmedLabel = newLabelInput.trim()
                    if (trimmedLabel.isNotEmpty() && !labels.contains(trimmedLabel)) {
                        onLabelsChange(labels + trimmedLabel)
                    }
                    newLabelInput = ""
                    keyboardController?.hide()
                }
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
                    commitPendingLabels(newLabelInput, labels, onLabelsChange)
                    newLabelInput = ""
                    if (onAction != null) onAction.invoke(SaveAction.ARCHIVE) else onCreateBookmark()
                },
                enabled = isCreateEnabled
            ) {
                Text(stringResource(id = R.string.action_archive))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        commitPendingLabels(newLabelInput, labels, onLabelsChange)
                        newLabelInput = ""
                        if (onAction != null) onAction.invoke(SaveAction.VIEW) else onCreateBookmark()
                    },
                    enabled = isCreateEnabled
                ) {
                    Text(stringResource(id = R.string.action_view_bookmark))
                }
                Button(
                    onClick = {
                        commitPendingLabels(newLabelInput, labels, onLabelsChange)
                        newLabelInput = ""
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

private fun commitPendingLabels(
    newLabelInput: String,
    labels: List<String>,
    onLabelsChange: (List<String>) -> Unit
) {
    if (newLabelInput.isBlank()) return
    val trimmedLabel = newLabelInput.trim()
    if (trimmedLabel.isNotEmpty() && !labels.contains(trimmedLabel)) {
        onLabelsChange(labels + trimmedLabel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateBookmarkLabelsSection(
    labels: List<String>,
    newLabelInput: String,
    isFavorite: Boolean = false,
    existingLabels: List<String> = emptyList(),
    onNewLabelChange: (String) -> Unit,
    onFocusLabel: () -> Unit = {},
    onAddLabel: () -> Unit,
    onRemoveLabel: (String) -> Unit,
    onFavoriteToggle: (Boolean) -> Unit = {}
) {
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
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LabelAutocompleteTextField(
                value = newLabelInput,
                onValueChange = {
                    onNewLabelChange(it)
                    onFocusLabel()
                },
                onLabelSelected = { selected ->
                    onNewLabelChange(selected.trim())
                    onAddLabel()
                },
                existingLabels = existingLabels,
                currentLabels = labels,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onFavoriteToggle(!isFavorite) }) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(R.string.action_favorite),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
