package com.mydeck.app.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mydeck.app.R
import com.mydeck.app.ui.components.VerticalScrollbar

sealed interface LabelPickerMode {
    data class SingleSelect(
        val selectedLabel: String?,
        val onLabelSelected: (String) -> Unit,
        val onRenameLabel: ((String, String) -> Unit)? = null,
        val onDeleteLabel: ((String) -> Unit)? = null,
    ) : LabelPickerMode

    data class MultiSelect(
        val initialSelection: Set<String>,
        val onDone: (Set<String>) -> Unit,
    ) : LabelPickerMode
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LabelPickerBottomSheet(
    labels: Map<String, Int>,
    mode: LabelPickerMode,
    onDismiss: () -> Unit,
) {
    var availableLabels by remember(labels) { mutableStateOf(labels) }
    var searchQuery by remember { mutableStateOf("") }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    var contextMenuLabel by remember { mutableStateOf<String?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var tempSelection by remember(mode) {
        mutableStateOf(
            when (mode) {
                is LabelPickerMode.MultiSelect -> mode.initialSelection.toList()
                is LabelPickerMode.SingleSelect -> emptyList()
            }
        )
    }

    val filteredLabels = remember(availableLabels, searchQuery) {
        if (searchQuery.isBlank()) {
            availableLabels.entries.toList()
        } else {
            availableLabels.entries
                .filter { it.key.contains(searchQuery, ignoreCase = true) }
                .toList()
        }
    }

    val hasExactLabelMatch = remember(availableLabels, searchQuery) {
        availableLabels.keys.any { it == searchQuery.trim() }
    }
    val trimmedSearchQuery = searchQuery.trim()
    val showCreateAction =
        mode is LabelPickerMode.MultiSelect &&
            trimmedSearchQuery.isNotEmpty() &&
            !hasExactLabelMatch
    val filteredLabelKeys = remember(filteredLabels) {
        filteredLabels.mapTo(LinkedHashSet()) { it.key }
    }
    val selectedFilteredLabels = remember(filteredLabelKeys, tempSelection, availableLabels, mode) {
        if (mode is LabelPickerMode.MultiSelect) {
            tempSelection
                .asSequence()
                .filter { filteredLabelKeys.contains(it) }
                .map { it to (availableLabels[it] ?: 0) }
                .toList()
        } else {
            emptyList()
        }
    }
    val unselectedFilteredLabels = remember(filteredLabels, tempSelection, mode) {
        if (mode is LabelPickerMode.MultiSelect) {
            filteredLabels
                .filterNot { tempSelection.contains(it.key) }
                .map { it.key to it.value }
        } else {
            filteredLabels.map { it.key to it.value }
        }
    }

    val title = when (mode) {
        is LabelPickerMode.SingleSelect -> stringResource(R.string.select_label)
        is LabelPickerMode.MultiSelect -> stringResource(R.string.select_labels)
    }

    fun commitSearch() {
        val query = searchQuery.trim()
        if (query.isEmpty()) return

        val exactLabel = availableLabels.keys.firstOrNull { it == query }
        val resolvedLabel = exactLabel ?: query
        if (exactLabel == null && !availableLabels.containsKey(resolvedLabel)) {
            availableLabels = availableLabels + (resolvedLabel to 0)
        }

        when (mode) {
            is LabelPickerMode.MultiSelect -> {
                if (!tempSelection.contains(resolvedLabel)) {
                    tempSelection = tempSelection + resolvedLabel
                }
                searchQuery = ""
            }

            is LabelPickerMode.SingleSelect -> {
                searchQuery = ""
                mode.onLabelSelected(resolvedLabel)
                onDismiss()
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = screenHeight)
        ) {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (mode is LabelPickerMode.MultiSelect) {
                        TextButton(
                            onClick = {
                                mode.onDone(tempSelection.toSet())
                                onDismiss()
                            }
                        ) {
                            Text(stringResource(R.string.label_picker_done))
                        }
                    }
                }
            )

            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.labels_search_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { commitSearch() },
                    onDone = { commitSearch() }
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.clear_search)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(8.dp))

            when {
                availableLabels.isEmpty() && !showCreateAction -> {
                    Text(
                        text = stringResource(R.string.labels_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }
                filteredLabels.isEmpty() && !showCreateAction -> {
                    Text(
                        text = stringResource(R.string.labels_search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }
                else -> {
                    val lazyListState = rememberLazyListState()
                    Box(modifier = Modifier.fillMaxWidth()) {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (showCreateAction) {
                                item(key = "create_label") {
                                    ListItem(
                                        headlineContent = {
                                            Text(
                                                text = stringResource(
                                                    R.string.create_label_action,
                                                    trimmedSearchQuery
                                                )
                                            )
                                        },
                                        leadingContent = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.Label,
                                                contentDescription = null
                                            )
                                        },
                                        modifier = Modifier.combinedClickable(
                                            role = Role.Button,
                                            onClick = {
                                                if (!availableLabels.containsKey(trimmedSearchQuery)) {
                                                    availableLabels = availableLabels + (trimmedSearchQuery to 0)
                                                }
                                                if (!tempSelection.contains(trimmedSearchQuery)) {
                                                    tempSelection = tempSelection + trimmedSearchQuery
                                                }
                                                searchQuery = ""
                                            }
                                        )
                                    )
                                }
                            }

                            fun androidx.compose.foundation.lazy.LazyListScope.renderLabelItems(
                                entries: List<Pair<String, Int>>
                            ) {
                                items(
                                    items = entries,
                                    key = { it.first }
                                ) { (label, count) ->
                                    val isSelected = when (mode) {
                                        is LabelPickerMode.SingleSelect -> mode.selectedLabel == label
                                        is LabelPickerMode.MultiSelect -> tempSelection.contains(label)
                                    }

                                    val supportsManagement =
                                        mode is LabelPickerMode.SingleSelect &&
                                            mode.onRenameLabel != null &&
                                            mode.onDeleteLabel != null

                                    Box {
                                        ListItem(
                                            colors = ListItemDefaults.colors(
                                                containerColor = if (
                                                    mode is LabelPickerMode.SingleSelect && isSelected
                                                ) {
                                                    MaterialTheme.colorScheme.secondaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surface
                                                }
                                            ),
                                            headlineContent = { Text(label) },
                                            leadingContent = {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Outlined.Label,
                                                    contentDescription = null
                                                )
                                            },
                                            trailingContent = {
                                                when {
                                                    mode is LabelPickerMode.MultiSelect && isSelected -> {
                                                        Icon(
                                                            imageVector = Icons.Filled.Check,
                                                            contentDescription = null
                                                        )
                                                    }

                                                    count > 0 -> {
                                                        Badge(
                                                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                                                        ) {
                                                            Text(
                                                                text = count.toString(),
                                                                style = MaterialTheme.typography.labelMedium,
                                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                                            )
                                                        }
                                                    }

                                                    else -> Unit
                                                }
                                            },
                                            modifier = Modifier
                                                .semantics { selected = isSelected }
                                                .combinedClickable(
                                                    role = Role.Button,
                                                    onClick = {
                                                        when (mode) {
                                                            is LabelPickerMode.SingleSelect -> {
                                                                mode.onLabelSelected(label)
                                                                onDismiss()
                                                            }

                                                            is LabelPickerMode.MultiSelect -> {
                                                                tempSelection =
                                                                    if (tempSelection.contains(label)) {
                                                                        tempSelection - label
                                                                    } else {
                                                                        tempSelection + label
                                                                    }
                                                            }
                                                        }
                                                    },
                                                    onLongClick = if (supportsManagement) {
                                                        { contextMenuLabel = label }
                                                    } else {
                                                        null
                                                    }
                                                )
                                        )
                                        if (supportsManagement) {
                                            DropdownMenu(
                                                expanded = contextMenuLabel == label,
                                                onDismissRequest = { contextMenuLabel = null }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.edit_label)) },
                                                    onClick = {
                                                        renameText = label
                                                        showRenameDialog = true
                                                        contextMenuLabel = null
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.delete_label)) },
                                                    onClick = {
                                                        renameText = label
                                                        showDeleteDialog = true
                                                        contextMenuLabel = null
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (mode is LabelPickerMode.MultiSelect) {
                                renderLabelItems(selectedFilteredLabels)
                                if (selectedFilteredLabels.isNotEmpty() && unselectedFilteredLabels.isNotEmpty()) {
                                    item(key = "selected_unselected_separator") {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                                renderLabelItems(unselectedFilteredLabels)
                            } else {
                                renderLabelItems(unselectedFilteredLabels)
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                        VerticalScrollbar(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight(),
                            lazyListState = lazyListState
                        )
                    }
                }
            }
        }
    }

    if (mode is LabelPickerMode.SingleSelect && showRenameDialog) {
        val labelToRename = renameText
        var newName by remember(labelToRename) { mutableStateOf(labelToRename) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.rename_label)) },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank() && newName != labelToRename) {
                            mode.onRenameLabel?.invoke(labelToRename, newName)
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text(stringResource(R.string.rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (mode is LabelPickerMode.SingleSelect && showDeleteDialog) {
        val labelToDelete = renameText
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_label)) },
            text = { Text(stringResource(R.string.delete_label_confirm_message, labelToDelete)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        mode.onDeleteLabel?.invoke(labelToDelete)
                        showDeleteDialog = false
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
