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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.mydeck.app.domain.model.LabelSearchMatching
import com.mydeck.app.domain.model.LabelSearchSort
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mydeck.app.R
import com.mydeck.app.ui.components.VerticalScrollbar
import kotlinx.coroutines.launch

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
    // Hoisted so the title-tap affordance can scroll the results list (see the scrollable branch).
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
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

    val labelSearchSettings: LabelSearchSettingsViewModel = hiltViewModel()
    val labelSearchMatching by labelSearchSettings.matching.collectAsState()
    val labelSearchSort by labelSearchSettings.sort.collectAsState()

    val filteredLabels = remember(availableLabels, searchQuery, labelSearchMatching, labelSearchSort) {
        val query = searchQuery.trim()
        val matched = if (query.isBlank()) {
            availableLabels.entries.toList()
        } else {
            availableLabels.entries.filter { entry ->
                when (labelSearchMatching) {
                    LabelSearchMatching.STARTS_WITH -> entry.key.startsWith(query, ignoreCase = true)
                    LabelSearchMatching.CONTAINS -> entry.key.contains(query, ignoreCase = true)
                }
            }
        }
        when (labelSearchSort) {
            LabelSearchSort.ALPHABETICAL ->
                matched.sortedWith(
                    compareBy<Map.Entry<String, Int>>(
                        { it.key.lowercase() },
                        { it.key }
                    )
                )
            // By frequency: surface prefix matches first (moot for STARTS_WITH or a blank query),
            // then most-used (count desc), then alphabetical as a stable tiebreak.
            LabelSearchSort.BY_FREQUENCY ->
                matched.sortedWith(
                    compareByDescending<Map.Entry<String, Int>> {
                        query.isNotBlank() && it.key.startsWith(query, ignoreCase = true)
                    }
                        .thenByDescending { it.value }
                        .thenBy { it.key.lowercase() }
                )
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
    val selectedLabels = remember(tempSelection, availableLabels, mode) {
        if (mode is LabelPickerMode.MultiSelect) {
            tempSelection
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

        when (mode) {
            is LabelPickerMode.MultiSelect -> {
                val resolvedLabel = exactLabel ?: query
                if (exactLabel == null && !availableLabels.containsKey(resolvedLabel)) {
                    availableLabels = availableLabels + (resolvedLabel to 0)
                }
                if (!tempSelection.contains(resolvedLabel)) {
                    tempSelection = tempSelection + resolvedLabel
                }
                searchQuery = ""
            }

            is LabelPickerMode.SingleSelect -> {
                // Single-select mode is selection-only: only existing labels can be applied.
                if (exactLabel != null) {
                    searchQuery = ""
                    mode.onLabelSelected(exactLabel)
                    onDismiss()
                }
            }
        }
    }

    fun toggleMultiSelectLabel(label: String) {
        tempSelection =
            if (tempSelection.contains(label)) {
                tempSelection - label
            } else {
                tempSelection + label
            }
        searchQuery = ""
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val showSearchEmptyState = filteredLabels.isEmpty() && !showCreateAction
    val showScrollableResults = showCreateAction || unselectedFilteredLabels.isNotEmpty()

    @Composable
    fun LabelPickerItem(
        label: String,
        count: Int,
        isSelected: Boolean,
    ) {
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
                                    toggleMultiSelectLabel(label)
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
                title = {
                    Text(
                        text = title,
                        modifier = Modifier.clickable {
                            coroutineScope.launch { lazyListState.animateScrollToItem(0) }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    // Label-search ranking toggles (persisted globally). Placed before Done so they
                    // shift left to make room for it on the multi-select sheet. Each shows the current
                    // mode as a monospace glyph and flips on tap; long-press reveals a tooltip.
                    val matchingDescription = stringResource(
                        if (labelSearchMatching == LabelSearchMatching.STARTS_WITH)
                            R.string.label_search_matching_prefix
                        else R.string.label_search_matching_contains
                    )
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(matchingDescription) } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = { labelSearchSettings.toggleMatching() }) {
                            Text(
                                text = if (labelSearchMatching == LabelSearchMatching.STARTS_WITH) "a∗" else "∗a∗",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.clearAndSetSemantics {
                                    contentDescription = matchingDescription
                                }
                            )
                        }
                    }
                    val sortDescription = stringResource(
                        if (labelSearchSort == LabelSearchSort.ALPHABETICAL)
                            R.string.label_search_sort_alpha
                        else R.string.label_search_sort_count
                    )
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(sortDescription) } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = { labelSearchSettings.toggleSort() }) {
                            Text(
                                text = if (labelSearchSort == LabelSearchSort.ALPHABETICAL) "abc" else "123",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.clearAndSetSemantics {
                                    contentDescription = sortDescription
                                }
                            )
                        }
                    }
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
                // In MultiSelect ("Add") mode, Enter commits/creates the typed label, so the
                // key should read as an affirmative action (checkmark) rather than a search.
                // SingleSelect is selection-only filtering, where the magnifying glass fits.
                keyboardOptions = KeyboardOptions(
                    imeAction = when (mode) {
                        is LabelPickerMode.MultiSelect -> ImeAction.Done
                        is LabelPickerMode.SingleSelect -> ImeAction.Search
                    }
                ),
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

            if (mode is LabelPickerMode.MultiSelect && selectedLabels.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = screenHeight * 0.35f)
                        .verticalScroll(rememberScrollState())
                ) {
                    selectedLabels.forEach { (label, count) ->
                        LabelPickerItem(
                            label = label,
                            count = count,
                            isSelected = true,
                        )
                    }
                }
                if (showScrollableResults || showSearchEmptyState) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }

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
                showSearchEmptyState -> {
                    Text(
                        text = stringResource(R.string.labels_search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }
                showScrollableResults -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
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
                            items(
                                items = unselectedFilteredLabels,
                                key = { it.first }
                            ) { (label, count) ->
                                val isSelected = when (mode) {
                                    is LabelPickerMode.SingleSelect -> mode.selectedLabel == label
                                    is LabelPickerMode.MultiSelect -> tempSelection.contains(label)
                                }

                                LabelPickerItem(
                                    label = label,
                                    count = count,
                                    isSelected = isSelected,
                                )
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
