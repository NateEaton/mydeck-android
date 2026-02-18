package com.mydeck.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mydeck.app.R
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.FilterFormState
import com.mydeck.app.domain.model.ProgressFilter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    currentFilter: FilterFormState,
    onApply: (FilterFormState) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local mutable copy of filter state for editing
    var search by remember(currentFilter) { mutableStateOf(currentFilter.search ?: "") }
    var types by remember(currentFilter) { mutableStateOf(currentFilter.types) }
    var progress by remember(currentFilter) { mutableStateOf(currentFilter.progress) }
    var isFavorite by remember(currentFilter) { mutableStateOf(currentFilter.isFavorite) }
    var isArchived by remember(currentFilter) { mutableStateOf(currentFilter.isArchived) }

    val hasActiveFilters = search.isNotBlank() ||
        types.isNotEmpty() ||
        progress.isNotEmpty() ||
        isFavorite != null ||
        isArchived != null

    val applyFilter = {
        onApply(
            FilterFormState(
                search = search.ifBlank { null },
                types = types,
                progress = progress,
                isFavorite = isFavorite,
                isArchived = isArchived,
            )
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            // Search field
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text(stringResource(R.string.filter_search)) },
                placeholder = { Text(stringResource(R.string.search_bookmarks)) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.clear_search),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { applyFilter() }),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            // Type chips
            Text(
                text = stringResource(R.string.filter_type),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = Bookmark.Type.Article in types,
                    onClick = {
                        types = if (Bookmark.Type.Article in types) types - Bookmark.Type.Article
                        else types + Bookmark.Type.Article
                    },
                    label = { Text(stringResource(R.string.filter_type_article)) }
                )
                FilterChip(
                    selected = Bookmark.Type.Video in types,
                    onClick = {
                        types = if (Bookmark.Type.Video in types) types - Bookmark.Type.Video
                        else types + Bookmark.Type.Video
                    },
                    label = { Text(stringResource(R.string.filter_type_video)) }
                )
                FilterChip(
                    selected = Bookmark.Type.Picture in types,
                    onClick = {
                        types = if (Bookmark.Type.Picture in types) types - Bookmark.Type.Picture
                        else types + Bookmark.Type.Picture
                    },
                    label = { Text(stringResource(R.string.filter_type_picture)) }
                )
            }

            Spacer(Modifier.height(20.dp))

            // Progress chips
            Text(
                text = stringResource(R.string.filter_progress),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = ProgressFilter.UNVIEWED in progress,
                    onClick = {
                        progress = if (ProgressFilter.UNVIEWED in progress) progress - ProgressFilter.UNVIEWED
                        else progress + ProgressFilter.UNVIEWED
                    },
                    label = { Text(stringResource(R.string.progress_unviewed)) }
                )
                FilterChip(
                    selected = ProgressFilter.IN_PROGRESS in progress,
                    onClick = {
                        progress = if (ProgressFilter.IN_PROGRESS in progress) progress - ProgressFilter.IN_PROGRESS
                        else progress + ProgressFilter.IN_PROGRESS
                    },
                    label = { Text(stringResource(R.string.progress_in_progress)) }
                )
                FilterChip(
                    selected = ProgressFilter.COMPLETED in progress,
                    onClick = {
                        progress = if (ProgressFilter.COMPLETED in progress) progress - ProgressFilter.COMPLETED
                        else progress + ProgressFilter.COMPLETED
                    },
                    label = { Text(stringResource(R.string.progress_completed)) }
                )
            }

            Spacer(Modifier.height(20.dp))

            // Is Favorite tri-state
            Text(
                text = stringResource(R.string.filter_is_favorite),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(8.dp))
            TriStateSegmentedButton(
                value = isFavorite,
                onValueChange = { isFavorite = it }
            )

            Spacer(Modifier.height(20.dp))

            // Is Archived tri-state
            Text(
                text = stringResource(R.string.filter_is_archived),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(8.dp))
            TriStateSegmentedButton(
                value = isArchived,
                onValueChange = { isArchived = it }
            )

            Spacer(Modifier.height(24.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (hasActiveFilters) {
                    TextButton(onClick = onReset) {
                        Text(stringResource(R.string.filter_reset))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Button(onClick = { applyFilter() }) {
                    Text(stringResource(R.string.search))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriStateSegmentedButton(
    value: Boolean?,
    onValueChange: (Boolean?) -> Unit,
) {
    val options = listOf<Boolean?>(null, true, false)
    val labels = listOf(
        stringResource(R.string.tri_state_any),
        stringResource(R.string.tri_state_yes),
        stringResource(R.string.tri_state_no),
    )
    val selectedIndex = options.indexOf(value)

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                onClick = { onValueChange(option) },
                selected = index == selectedIndex,
            ) {
                Text(labels[index])
            }
        }
    }
}
