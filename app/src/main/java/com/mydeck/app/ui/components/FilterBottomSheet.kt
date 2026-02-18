package com.mydeck.app.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mydeck.app.R
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.FilterFormState
import com.mydeck.app.domain.model.ProgressFilter
import com.mydeck.app.ui.list.LabelsBottomSheet
import kotlinx.datetime.Instant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    currentFilter: FilterFormState,
    labels: Map<String, Int> = emptyMap(),
    onApply: (FilterFormState) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    var search by remember(currentFilter) { mutableStateOf(currentFilter.search ?: "") }
    var title by remember(currentFilter) { mutableStateOf(currentFilter.title ?: "") }
    var author by remember(currentFilter) { mutableStateOf(currentFilter.author ?: "") }
    var site by remember(currentFilter) { mutableStateOf(currentFilter.site ?: "") }
    var label by remember(currentFilter) { mutableStateOf(currentFilter.label) }
    var fromDate by remember(currentFilter) { mutableStateOf(currentFilter.fromDate) }
    var toDate by remember(currentFilter) { mutableStateOf(currentFilter.toDate) }
    var types by remember(currentFilter) { mutableStateOf(currentFilter.types) }
    var progress by remember(currentFilter) { mutableStateOf(currentFilter.progress) }
    var isFavorite by remember(currentFilter) { mutableStateOf(currentFilter.isFavorite) }
    var isArchived by remember(currentFilter) { mutableStateOf(currentFilter.isArchived) }
    var isLoaded by remember(currentFilter) { mutableStateOf(currentFilter.isLoaded) }
    var withLabels by remember(currentFilter) { mutableStateOf(currentFilter.withLabels) }
    var withErrors by remember(currentFilter) { mutableStateOf(currentFilter.withErrors) }

    var showLabelPicker by remember { mutableStateOf(false) }
    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }

    val hasActiveFilters = search.isNotBlank() || title.isNotBlank() || author.isNotBlank() ||
        site.isNotBlank() || label != null || fromDate != null || toDate != null ||
        types.isNotEmpty() || progress.isNotEmpty() || isFavorite != null ||
        isArchived != null || isLoaded != null || withLabels != null || withErrors != null

    val applyFilter = {
        onApply(FilterFormState(
            search = search.ifBlank { null },
            title = title.ifBlank { null },
            author = author.ifBlank { null },
            site = site.ifBlank { null },
            label = label,
            fromDate = fromDate,
            toDate = toDate,
            types = types,
            progress = progress,
            isFavorite = isFavorite,
            isArchived = isArchived,
            isLoaded = isLoaded,
            withLabels = withLabels,
            withErrors = withErrors,
        ))
    }

    // Colors that make a disabled OutlinedTextField look like an enabled one.
    // Required for read-only picker fields (label, dates) because enabled=false is needed
    // to suppress keyboard/cursor, but the default disabled colors are too muted.
    val pickerColors = OutlinedTextFieldDefaults.colors(
        disabledTextColor = MaterialTheme.colorScheme.onSurface,
        disabledBorderColor = MaterialTheme.colorScheme.outline,
        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

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
            // ── Search (full width, keyboard entry) ──────────────────────────
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text(stringResource(R.string.filter_search)) },
                placeholder = { Text(stringResource(R.string.search_bookmarks)) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { applyFilter() }),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // ── Title / Author (side-by-side) ────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.filter_title)) },
                    trailingIcon = {
                        if (title.isNotEmpty()) {
                            IconButton(onClick = { title = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text(stringResource(R.string.filter_author)) },
                    trailingIcon = {
                        if (author.isNotEmpty()) {
                            IconButton(onClick = { author = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Site / Label (side-by-side) ──────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = site,
                    onValueChange = { site = it },
                    label = { Text(stringResource(R.string.filter_site)) },
                    trailingIcon = {
                        if (site.isNotEmpty()) {
                            IconButton(onClick = { site = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = label ?: "",
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    colors = pickerColors,
                    label = { Text(stringResource(R.string.filter_label)) },
                    placeholder = { Text(stringResource(R.string.filter_select_label)) },
                    trailingIcon = {
                        if (label != null) {
                            IconButton(onClick = { label = null }) {
                                Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showLabelPicker = true }
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── From Date / To Date (side-by-side) ───────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = fromDate?.let { dateFormatter.format(Date(it.toEpochMilliseconds())) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    colors = pickerColors,
                    label = { Text(stringResource(R.string.filter_from_date)) },
                    trailingIcon = {
                        if (fromDate != null) {
                            IconButton(onClick = { fromDate = null }) {
                                Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showFromDatePicker = true }
                )
                OutlinedTextField(
                    value = toDate?.let { dateFormatter.format(Date(it.toEpochMilliseconds())) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    colors = pickerColors,
                    label = { Text(stringResource(R.string.filter_to_date)) },
                    trailingIcon = {
                        if (toDate != null) {
                            IconButton(onClick = { toDate = null }) {
                                Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showToDatePicker = true }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Type chips ───────────────────────────────────────────────────
            Text(stringResource(R.string.filter_type), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = Bookmark.Type.Article in types,
                    onClick = { types = if (Bookmark.Type.Article in types) types - Bookmark.Type.Article else types + Bookmark.Type.Article },
                    label = { Text(stringResource(R.string.filter_type_article)) }
                )
                FilterChip(
                    selected = Bookmark.Type.Video in types,
                    onClick = { types = if (Bookmark.Type.Video in types) types - Bookmark.Type.Video else types + Bookmark.Type.Video },
                    label = { Text(stringResource(R.string.filter_type_video)) }
                )
                FilterChip(
                    selected = Bookmark.Type.Picture in types,
                    onClick = { types = if (Bookmark.Type.Picture in types) types - Bookmark.Type.Picture else types + Bookmark.Type.Picture },
                    label = { Text(stringResource(R.string.filter_type_picture)) }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Progress chips ───────────────────────────────────────────────
            Text(stringResource(R.string.filter_progress), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = ProgressFilter.UNVIEWED in progress,
                    onClick = { progress = if (ProgressFilter.UNVIEWED in progress) progress - ProgressFilter.UNVIEWED else progress + ProgressFilter.UNVIEWED },
                    label = { Text(stringResource(R.string.progress_unviewed)) }
                )
                FilterChip(
                    selected = ProgressFilter.IN_PROGRESS in progress,
                    onClick = { progress = if (ProgressFilter.IN_PROGRESS in progress) progress - ProgressFilter.IN_PROGRESS else progress + ProgressFilter.IN_PROGRESS },
                    label = { Text(stringResource(R.string.progress_in_progress)) }
                )
                FilterChip(
                    selected = ProgressFilter.COMPLETED in progress,
                    onClick = { progress = if (ProgressFilter.COMPLETED in progress) progress - ProgressFilter.COMPLETED else progress + ProgressFilter.COMPLETED },
                    label = { Text(stringResource(R.string.progress_completed)) }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Boolean filters (compact label-beside-control rows) ──────────
            CompactTriStateRow(stringResource(R.string.filter_is_favorite), isFavorite) { isFavorite = it }
            Spacer(Modifier.height(8.dp))
            CompactTriStateRow(stringResource(R.string.filter_is_archived), isArchived) { isArchived = it }
            Spacer(Modifier.height(8.dp))
            CompactTriStateRow(stringResource(R.string.filter_is_loaded), isLoaded) { isLoaded = it }
            Spacer(Modifier.height(8.dp))
            CompactTriStateRow(stringResource(R.string.filter_with_labels), withLabels) { withLabels = it }
            Spacer(Modifier.height(8.dp))
            CompactTriStateRow(stringResource(R.string.filter_with_errors), withErrors) { withErrors = it }

            Spacer(Modifier.height(20.dp))

            // ── Action buttons ───────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (hasActiveFilters) {
                    TextButton(onClick = onReset) { Text(stringResource(R.string.filter_reset)) }
                    Spacer(Modifier.width(8.dp))
                }
                Button(onClick = { applyFilter() }) { Text(stringResource(R.string.search)) }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Label picker (selection-only — no rename/delete)
    if (showLabelPicker) {
        LabelsBottomSheet(
            labels = labels,
            selectedLabel = label,
            onLabelSelected = { selected ->
                label = selected
                showLabelPicker = false
            },
            onRenameLabel = { _, _ -> },
            onDeleteLabel = { _ -> },
            onDismiss = { showLabelPicker = false }
        )
    }

    // From Date picker
    if (showFromDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = fromDate?.toEpochMilliseconds())
        DatePickerDialog(
            onDismissRequest = { showFromDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { fromDate = Instant.fromEpochMilliseconds(it) }
                    showFromDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showFromDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) { DatePicker(state = state) }
    }

    // To Date picker
    if (showToDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = toDate?.toEpochMilliseconds())
        DatePickerDialog(
            onDismissRequest = { showToDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { toDate = Instant.fromEpochMilliseconds(it) }
                    showToDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showToDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) { DatePicker(state = state) }
    }
}

/** Single-row tri-state control: label on the left, [N/A | Yes | No] on the right. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactTriStateRow(
    label: String,
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

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        SingleChoiceSegmentedButtonRow {
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
}
