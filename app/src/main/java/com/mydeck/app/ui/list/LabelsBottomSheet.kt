package com.mydeck.app.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mydeck.app.R
import com.mydeck.app.ui.components.VerticalScrollbar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LabelsBottomSheet(
    labels: Map<String, Int>,
    selectedLabel: String?,
    onLabelSelected: (String) -> Unit,
    onRenameLabel: (String, String) -> Unit,
    onDeleteLabel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    var searchQuery by remember { mutableStateOf("") }
    var contextMenuLabel by remember { mutableStateOf<String?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val filteredLabels = remember(labels, searchQuery) {
        if (searchQuery.isBlank()) {
            labels.entries.sortedBy { it.key }
        } else {
            labels.entries
                .filter { it.key.contains(searchQuery, ignoreCase = true) }
                .sortedBy { it.key }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.select_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.labels_search_placeholder)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))

            when {
                labels.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.labels_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }
                filteredLabels.isEmpty() -> {
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
                            items(
                                items = filteredLabels,
                                key = { it.key }
                            ) { (label, count) ->
                                Box {
                                    ListItem(
                                        headlineContent = { Text(label) },
                                        leadingContent = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.Label,
                                                contentDescription = null
                                            )
                                        },
                                        trailingContent = {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = count.toString(),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        },
                                        modifier = Modifier.combinedClickable(
                                            onClick = {
                                                onLabelSelected(label)
                                                onDismiss()
                                            },
                                            onLongClick = {
                                                contextMenuLabel = label
                                            }
                                        )
                                    )
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

    if (showRenameDialog) {
        val labelToRename = renameText
        var newName by remember(labelToRename) { mutableStateOf(labelToRename) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.rename_label)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank() && newName != labelToRename) {
                            onRenameLabel(labelToRename, newName)
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

    if (showDeleteDialog) {
        val labelToDelete = renameText
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_label)) },
            text = { Text(stringResource(R.string.delete_label_confirm_message, labelToDelete)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteLabel(labelToDelete)
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
