package com.mydeck.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mydeck.app.R
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.FilterFormState
import com.mydeck.app.domain.model.ProgressFilter
import com.mydeck.app.ui.list.LabelPickerBottomSheet
import com.mydeck.app.ui.list.LabelPickerMode
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val FOCUSED_FIELD_BRING_INTO_VIEW_DELAY_MS = 250L

/**
 * Mutable working copy of a [FilterFormState] for the filter editing UI. Holds every editable field
 * as Compose state so the same control set can back both [FilterBottomSheet] and the collection
 * editor sheet. Reset whenever [currentFilter] changes via [rememberFilterEditorState].
 */
@Stable
class FilterEditorState(initial: FilterFormState) {
    var search by mutableStateOf(initial.search ?: "")
    var title by mutableStateOf(initial.title ?: "")
    var author by mutableStateOf(initial.author ?: "")
    var site by mutableStateOf(initial.site ?: "")
    var label by mutableStateOf(initial.label)
    var fromDate by mutableStateOf(initial.fromDate)
    var toDate by mutableStateOf(initial.toDate)
    var types by mutableStateOf(initial.types)
    var progress by mutableStateOf(initial.progress)
    var isFavorite by mutableStateOf(initial.isFavorite)
    var isArchived by mutableStateOf(initial.isArchived)
    var isLoaded by mutableStateOf(initial.isLoaded)
    var withLabels by mutableStateOf(initial.withLabels)
    var withErrors by mutableStateOf(initial.withErrors)
    var minReadingTime by mutableStateOf(initial.minReadingTime?.toString() ?: "")
    var maxReadingTime by mutableStateOf(initial.maxReadingTime?.toString() ?: "")
    var includeNullReadingTime by mutableStateOf(initial.includeNullReadingTime)
    var minWordCount by mutableStateOf(initial.minWordCount?.toString() ?: "")
    var maxWordCount by mutableStateOf(initial.maxWordCount?.toString() ?: "")
    var includeNullWordCount by mutableStateOf(initial.includeNullWordCount)

    val readingTimeError: Boolean
        get() = minReadingTime.isNotEmpty() && maxReadingTime.isNotEmpty() &&
            (minReadingTime.toIntOrNull() ?: 0) > (maxReadingTime.toIntOrNull() ?: 0)

    val wordCountError: Boolean
        get() = minWordCount.isNotEmpty() && maxWordCount.isNotEmpty() &&
            (minWordCount.toIntOrNull() ?: 0) > (maxWordCount.toIntOrNull() ?: 0)

    val hasValidationError: Boolean get() = readingTimeError || wordCountError

    val hasActiveFilters: Boolean
        get() = search.isNotBlank() || title.isNotBlank() || author.isNotBlank() ||
            site.isNotBlank() || label != null || fromDate != null || toDate != null ||
            types.isNotEmpty() || progress.isNotEmpty() || isFavorite != null ||
            isArchived != null || isLoaded != null || withLabels != null || withErrors != null ||
            minReadingTime.isNotBlank() || maxReadingTime.isNotBlank() || includeNullReadingTime ||
            minWordCount.isNotBlank() || maxWordCount.isNotBlank() || includeNullWordCount

    fun toFilterFormState(): FilterFormState = FilterFormState(
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
        minReadingTime = minReadingTime.toIntOrNull(),
        maxReadingTime = maxReadingTime.toIntOrNull(),
        includeNullReadingTime = includeNullReadingTime,
        minWordCount = minWordCount.toIntOrNull(),
        maxWordCount = maxWordCount.toIntOrNull(),
        includeNullWordCount = includeNullWordCount,
    )
}

@Composable
fun rememberFilterEditorState(currentFilter: FilterFormState): FilterEditorState =
    remember(currentFilter) { FilterEditorState(currentFilter) }

/**
 * The full set of filter-criteria controls (search, title/author, site/label, dates, length, type
 * and progress chips, and the boolean tri-state rows), backed by [state]. Renders as a plain
 * [Column] meant to be placed inside a scrolling parent; the caller supplies the surrounding sheet
 * chrome and action buttons. Reused by [FilterBottomSheet] and the collection editor sheet.
 *
 * [onImeAction] runs when the keyboard "search/done" action fires on a text field (e.g. apply the
 * filter); pass null to make the action a no-op.
 *
 * [localOnlyFiltersEditable] controls the device-local criteria that cannot be persisted to a Readeck
 * collection: the **Length** (reading-time / word-count) filter and the **Downloaded** (`isLoaded`)
 * tri-state. When true (the normal filter sheet) they are fully editable. When false (the collection
 * editor) they are still shown in their usual positions but rendered disabled with an "Unavailable"
 * value and a long-press tooltip explaining why — so the user sees the criteria exist and learns they
 * can't be saved, rather than the fields silently vanishing. `With labels` and `With errors` are
 * server-supported and always editable.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterControls(
    state: FilterEditorState,
    labels: Map<String, Int>,
    modifier: Modifier = Modifier,
    onImeAction: (() -> Unit)? = null,
    localOnlyFiltersEditable: Boolean = true,
) {
    var showLabelPicker by remember { mutableStateOf(false) }
    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }
    var showLengthFilterDialog by remember { mutableStateOf(false) }

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
    val lengthSummary = lengthFilterSummary(
        minReadingTime = state.minReadingTime.toIntOrNull(),
        maxReadingTime = state.maxReadingTime.toIntOrNull(),
        includeNullReadingTime = state.includeNullReadingTime,
        minWordCount = state.minWordCount.toIntOrNull(),
        maxWordCount = state.maxWordCount.toIntOrNull(),
        includeNullWordCount = state.includeNullWordCount,
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // -- Search (full width, keyboard entry)
        OutlinedTextField(
            value = state.search,
            onValueChange = { state.search = it },
            label = { Text(stringResource(R.string.filter_search)) },
            placeholder = { Text(stringResource(R.string.search_bookmarks)) },
            trailingIcon = {
                if (state.search.isNotEmpty()) {
                    IconButton(onClick = { state.search = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onImeAction?.invoke() }),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // -- Title / Author (side-by-side)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.title,
                onValueChange = { state.title = it },
                label = { Text(stringResource(R.string.filter_title)) },
                trailingIcon = {
                    if (state.title.isNotEmpty()) {
                        IconButton(onClick = { state.title = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onImeAction?.invoke() }),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = state.author,
                onValueChange = { state.author = it },
                label = { Text(stringResource(R.string.filter_author)) },
                trailingIcon = {
                    if (state.author.isNotEmpty()) {
                        IconButton(onClick = { state.author = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onImeAction?.invoke() }),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        // -- Site / Label (side-by-side)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.site,
                onValueChange = { state.site = it },
                label = { Text(stringResource(R.string.filter_site)) },
                trailingIcon = {
                    if (state.site.isNotEmpty()) {
                        IconButton(onClick = { state.site = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onImeAction?.invoke() }),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = state.label ?: "",
                onValueChange = {},
                readOnly = true,
                enabled = false,
                colors = pickerColors,
                label = { Text(stringResource(R.string.filter_label)) },
                placeholder = { Text(stringResource(R.string.filter_select_label)) },
                trailingIcon = {
                    if (state.label != null) {
                        IconButton(onClick = { state.label = null }) {
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

        // -- From Date / To Date (side-by-side)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.fromDate?.let { dateFormatter.format(Date(it.toEpochMilliseconds())) } ?: "",
                onValueChange = {},
                readOnly = true,
                enabled = false,
                colors = pickerColors,
                label = { Text(stringResource(R.string.filter_from_date)) },
                trailingIcon = {
                    if (state.fromDate != null) {
                        IconButton(onClick = { state.fromDate = null }) {
                            Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .clickable { showFromDatePicker = true }
            )
            OutlinedTextField(
                value = state.toDate?.let { dateFormatter.format(Date(it.toEpochMilliseconds())) } ?: "",
                onValueChange = {},
                readOnly = true,
                enabled = false,
                colors = pickerColors,
                label = { Text(stringResource(R.string.filter_to_date)) },
                trailingIcon = {
                    if (state.toDate != null) {
                        IconButton(onClick = { state.toDate = null }) {
                            Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .clickable { showToDatePicker = true }
            )
        }

        Spacer(Modifier.height(12.dp))

        if (localOnlyFiltersEditable) {
            OutlinedTextField(
                value = lengthSummary ?: "",
                onValueChange = {},
                readOnly = true,
                enabled = false,
                colors = pickerColors,
                label = { Text(stringResource(R.string.filter_length)) },
                placeholder = { Text(stringResource(R.string.filter_length_summary_none)) },
                trailingIcon = {
                    if (lengthSummary != null) {
                        IconButton(onClick = {
                            state.minReadingTime = ""
                            state.maxReadingTime = ""
                            state.includeNullReadingTime = false
                            state.minWordCount = ""
                            state.maxWordCount = ""
                            state.includeNullWordCount = false
                        }) {
                            Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLengthFilterDialog = true }
            )

            Spacer(Modifier.height(12.dp))
        } else {
            UnavailableInCollectionField(label = stringResource(R.string.filter_length))
            Spacer(Modifier.height(12.dp))
        }

        // -- Type chips
        Text(stringResource(R.string.filter_type), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = Bookmark.Type.Article in state.types,
                onClick = { state.types = if (Bookmark.Type.Article in state.types) state.types - Bookmark.Type.Article else state.types + Bookmark.Type.Article },
                label = { Text(stringResource(R.string.filter_type_article)) }
            )
            FilterChip(
                selected = Bookmark.Type.Video in state.types,
                onClick = { state.types = if (Bookmark.Type.Video in state.types) state.types - Bookmark.Type.Video else state.types + Bookmark.Type.Video },
                label = { Text(stringResource(R.string.filter_type_video)) }
            )
            FilterChip(
                selected = Bookmark.Type.Picture in state.types,
                onClick = { state.types = if (Bookmark.Type.Picture in state.types) state.types - Bookmark.Type.Picture else state.types + Bookmark.Type.Picture },
                label = { Text(stringResource(R.string.filter_type_picture)) }
            )
        }

        Spacer(Modifier.height(16.dp))

        // -- Progress chips
        Text(stringResource(R.string.filter_progress), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = ProgressFilter.UNVIEWED in state.progress,
                onClick = { state.progress = if (ProgressFilter.UNVIEWED in state.progress) state.progress - ProgressFilter.UNVIEWED else state.progress + ProgressFilter.UNVIEWED },
                label = { Text(stringResource(R.string.progress_unviewed)) }
            )
            FilterChip(
                selected = ProgressFilter.IN_PROGRESS in state.progress,
                onClick = { state.progress = if (ProgressFilter.IN_PROGRESS in state.progress) state.progress - ProgressFilter.IN_PROGRESS else state.progress + ProgressFilter.IN_PROGRESS },
                label = { Text(stringResource(R.string.progress_in_progress)) }
            )
            FilterChip(
                selected = ProgressFilter.COMPLETED in state.progress,
                onClick = { state.progress = if (ProgressFilter.COMPLETED in state.progress) state.progress - ProgressFilter.COMPLETED else state.progress + ProgressFilter.COMPLETED },
                label = { Text(stringResource(R.string.progress_completed)) }
            )
        }

        Spacer(Modifier.height(16.dp))

        // -- Boolean filters (compact label-beside-control rows)
        CompactTriStateRow(stringResource(R.string.filter_is_favorite), state.isFavorite) { state.isFavorite = it }
        Spacer(Modifier.height(8.dp))
        CompactTriStateRow(stringResource(R.string.filter_is_archived), state.isArchived) { state.isArchived = it }
        Spacer(Modifier.height(8.dp))
        if (localOnlyFiltersEditable) {
            CompactTriStateRow(stringResource(R.string.filter_is_loaded), state.isLoaded) { state.isLoaded = it }
            Spacer(Modifier.height(8.dp))
        } else {
            UnavailableInCollectionRow(label = stringResource(R.string.filter_is_loaded))
            Spacer(Modifier.height(8.dp))
        }
        CompactTriStateRow(stringResource(R.string.filter_with_labels), state.withLabels) { state.withLabels = it }
        Spacer(Modifier.height(8.dp))
        CompactTriStateRow(stringResource(R.string.filter_with_errors), state.withErrors) { state.withErrors = it }
    }

    if (showLengthFilterDialog) {
        LengthFilterDialog(
            initialMinReadingTime = state.minReadingTime,
            initialMaxReadingTime = state.maxReadingTime,
            initialIncludeNullReadingTime = state.includeNullReadingTime,
            initialMinWordCount = state.minWordCount,
            initialMaxWordCount = state.maxWordCount,
            initialIncludeNullWordCount = state.includeNullWordCount,
            onApply = { newMinReadingTime,
                    newMaxReadingTime,
                    newIncludeNullReadingTime,
                    newMinWordCount,
                    newMaxWordCount,
                    newIncludeNullWordCount ->
                state.minReadingTime = newMinReadingTime
                state.maxReadingTime = newMaxReadingTime
                state.includeNullReadingTime = newIncludeNullReadingTime
                state.minWordCount = newMinWordCount
                state.maxWordCount = newMaxWordCount
                state.includeNullWordCount = newIncludeNullWordCount
                showLengthFilterDialog = false
            },
            onDismiss = { showLengthFilterDialog = false }
        )
    }

    // Label picker (selection-only — no rename/delete)
    if (showLabelPicker) {
        LabelPickerBottomSheet(
            labels = labels,
            mode = LabelPickerMode.SingleSelect(
                selectedLabel = state.label,
                onLabelSelected = { selected ->
                    state.label = selected
                    showLabelPicker = false
                }
            ),
            onDismiss = { showLabelPicker = false }
        )
    }

    // From Date picker
    if (showFromDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.fromDate?.toEpochMilliseconds())
        DatePickerDialog(
            onDismissRequest = { showFromDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { state.fromDate = Instant.fromEpochMilliseconds(it) }
                    showFromDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showFromDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) { DatePicker(state = pickerState) }
    }

    // To Date picker
    if (showToDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.toDate?.toEpochMilliseconds())
        DatePickerDialog(
            onDismissRequest = { showToDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { state.toDate = Instant.fromEpochMilliseconds(it) }
                    showToDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showToDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) { DatePicker(state = pickerState) }
    }
}

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
    val scope = rememberCoroutineScope()
    val state = rememberFilterEditorState(currentFilter)

    val applyFilter = {
        if (!state.hasValidationError) {
            scope.dismissSheet(sheetState) { onApply(state.toFilterFormState()) }
        }
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
            FilterControls(
                state = state,
                labels = labels,
                onImeAction = applyFilter,
            )

            Spacer(Modifier.height(20.dp))

            // -- Action buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (state.hasActiveFilters) {
                    TextButton(onClick = { scope.dismissSheet(sheetState) { onReset() } }) {
                        Text(stringResource(R.string.filter_reset))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Button(onClick = { applyFilter() }, enabled = !state.hasValidationError) { Text(stringResource(R.string.search)) }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun lengthFilterSummary(
    minReadingTime: Int?,
    maxReadingTime: Int?,
    includeNullReadingTime: Boolean,
    minWordCount: Int?,
    maxWordCount: Int?,
    includeNullWordCount: Boolean,
): String? {
    val unknownLabel = stringResource(R.string.filter_length_unknown)
    val parts = listOfNotNull(
        rangeFilterSummary(
            label = stringResource(R.string.filter_reading_time),
            min = minReadingTime,
            max = maxReadingTime,
            includeNull = includeNullReadingTime,
            unit = " min",
            unknownLabel = unknownLabel,
        ),
        rangeFilterSummary(
            label = stringResource(R.string.filter_word_count),
            min = minWordCount,
            max = maxWordCount,
            includeNull = includeNullWordCount,
            unit = "",
            unknownLabel = unknownLabel,
        ),
    )

    return parts.joinToString(" / ").ifBlank { null }
}

private fun rangeFilterSummary(
    label: String,
    min: Int?,
    max: Int?,
    includeNull: Boolean,
    unit: String,
    unknownLabel: String,
): String? {
    if (min == null && max == null && !includeNull) return null

    val rangeLabel = when {
        min != null && max != null -> "$min–$max$unit"
        min != null -> "≥$min$unit"
        max != null -> "≤$max$unit"
        else -> null
    }

    return when {
        rangeLabel != null && includeNull -> "$label: $rangeLabel + $unknownLabel"
        rangeLabel != null -> "$label: $rangeLabel"
        else -> "$label: $unknownLabel"
    }
}

@Composable
private fun LengthFilterDialog(
    initialMinReadingTime: String,
    initialMaxReadingTime: String,
    initialIncludeNullReadingTime: Boolean,
    initialMinWordCount: String,
    initialMaxWordCount: String,
    initialIncludeNullWordCount: Boolean,
    onApply: (String, String, Boolean, String, String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var minReadingTime by remember(initialMinReadingTime) { mutableStateOf(initialMinReadingTime) }
    var maxReadingTime by remember(initialMaxReadingTime) { mutableStateOf(initialMaxReadingTime) }
    var includeNullReadingTime by remember(initialIncludeNullReadingTime) {
        mutableStateOf(initialIncludeNullReadingTime)
    }
    var minWordCount by remember(initialMinWordCount) { mutableStateOf(initialMinWordCount) }
    var maxWordCount by remember(initialMaxWordCount) { mutableStateOf(initialMaxWordCount) }
    var includeNullWordCount by remember(initialIncludeNullWordCount) {
        mutableStateOf(initialIncludeNullWordCount)
    }

    val readingTimeError = minReadingTime.isNotEmpty() && maxReadingTime.isNotEmpty() &&
        (minReadingTime.toIntOrNull() ?: 0) > (maxReadingTime.toIntOrNull() ?: 0)
    val wordCountError = minWordCount.isNotEmpty() && maxWordCount.isNotEmpty() &&
        (minWordCount.toIntOrNull() ?: 0) > (maxWordCount.toIntOrNull() ?: 0)
    val hasValidationError = readingTimeError || wordCountError

    val hasAnyValue = minReadingTime.isNotEmpty() || maxReadingTime.isNotEmpty() ||
        includeNullReadingTime ||
        minWordCount.isNotEmpty() || maxWordCount.isNotEmpty() || includeNullWordCount

    val applyDialog = {
        if (!hasValidationError) {
            onApply(
                minReadingTime,
                maxReadingTime,
                includeNullReadingTime,
                minWordCount,
                maxWordCount,
                includeNullWordCount,
            )
        }
    }
    val clearDialog = {
        minReadingTime = ""
        maxReadingTime = ""
        includeNullReadingTime = false
        minWordCount = ""
        maxWordCount = ""
        includeNullWordCount = false
    }

    AlertDialog(
        modifier = Modifier.imePadding(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_length_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LengthRangeSection(
                    title = stringResource(R.string.filter_reading_time_est),
                    minValue = minReadingTime,
                    maxValue = maxReadingTime,
                    includeNull = includeNullReadingTime,
                    hasError = readingTimeError,
                    maxImeAction = ImeAction.Next,
                    onMinValueChange = { minReadingTime = it },
                    onMaxValueChange = { maxReadingTime = it },
                    onIncludeNullChange = { includeNullReadingTime = it },
                )
                LengthRangeSection(
                    title = stringResource(R.string.filter_word_count_est),
                    minValue = minWordCount,
                    maxValue = maxWordCount,
                    includeNull = includeNullWordCount,
                    hasError = wordCountError,
                    maxImeAction = ImeAction.Done,
                    onMinValueChange = { minWordCount = it },
                    onMaxValueChange = { maxWordCount = it },
                    onIncludeNullChange = { includeNullWordCount = it },
                    onDone = applyDialog,
                )
                TextButton(
                    onClick = clearDialog,
                    enabled = hasAnyValue,
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(stringResource(R.string.filter_length_clear))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = applyDialog, enabled = !hasValidationError) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun LengthRangeSection(
    title: String,
    minValue: String,
    maxValue: String,
    includeNull: Boolean,
    hasError: Boolean,
    maxImeAction: ImeAction,
    onMinValueChange: (String) -> Unit,
    onMaxValueChange: (String) -> Unit,
    onIncludeNullChange: (Boolean) -> Unit,
    onDone: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = minValue,
                onValueChange = { v -> if (v.all { it.isDigit() }) onMinValueChange(v) },
                label = { Text(stringResource(R.string.filter_reading_time_min)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                isError = hasError,
                modifier = Modifier
                    .weight(1f)
                    .bringFocusedFieldIntoView()
            )
            OutlinedTextField(
                value = maxValue,
                onValueChange = { v -> if (v.all { it.isDigit() }) onMaxValueChange(v) },
                label = { Text(stringResource(R.string.filter_reading_time_max)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = maxImeAction),
                keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                isError = hasError,
                modifier = Modifier
                    .weight(1f)
                    .bringFocusedFieldIntoView()
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = includeNull,
                    onValueChange = onIncludeNullChange,
                    role = Role.Checkbox,
                )
        ) {
            Checkbox(
                checked = includeNull,
                onCheckedChange = null,
            )
            Text(stringResource(R.string.filter_length_include_unknown))
        }
        if (hasError) {
            Text(
                text = stringResource(R.string.filter_reading_time_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun Modifier.bringFocusedFieldIntoView(): Modifier {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            delay(FOCUSED_FIELD_BRING_INTO_VIEW_DELAY_MS)
            bringIntoViewRequester.bringIntoView()
        }
    }

    return this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onFocusEvent { state -> isFocused = state.isFocused }
}

/**
 * The **Length** picker field rendered non-editable for the collection editor: the label and outlined
 * box look normal (matching the editable filter fields), only the value reads a greyed "Unavailable".
 * A long-press tooltip explains that this device-local criterion can't be saved in a collection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnavailableInCollectionField(label: String) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(stringResource(R.string.filter_unavailable_in_collection_tooltip)) } },
        state = rememberTooltipState(),
    ) {
        OutlinedTextField(
            value = stringResource(R.string.filter_unavailable),
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(label) },
            // Normal label + border; only the value text is greyed to read as "not set here".
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * A boolean tri-state row (e.g. **Downloaded**) rendered non-editable for the collection editor: the
 * label stays in place and the 3-value segmented control is replaced by a single segmented pill with
 * a normal outline and a greyed, centered "Unavailable". A long-press tooltip explains why the
 * criterion can't be saved in a collection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnavailableInCollectionRow(label: String) {
    val unavailableColors = SegmentedButtonDefaults.colors(
        disabledInactiveBorderColor = MaterialTheme.colorScheme.outline,
        disabledInactiveContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    )
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(stringResource(R.string.filter_unavailable_in_collection_tooltip)) } },
        state = rememberTooltipState(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            // The pill must match the width of the editable 3-value tri-state controls above/below it.
            // That width is content- and locale-dependent, so an invisible reference copy of the
            // 3-value control sizes the Box, and the visible single pill fills it via matchParentSize.
            Box {
                val triLabels = listOf(
                    stringResource(R.string.tri_state_any),
                    stringResource(R.string.tri_state_yes),
                    stringResource(R.string.tri_state_no),
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .alpha(0f)
                        .clearAndSetSemantics {}
                ) {
                    triLabels.forEachIndexed { index, text ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = triLabels.size),
                            onClick = {},
                            selected = false,
                            enabled = false,
                        ) { Text(text) }
                    }
                }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.matchParentSize()) {
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 1),
                        onClick = {},
                        selected = false,
                        enabled = false,
                        colors = unavailableColors,
                    ) {
                        Text(stringResource(R.string.filter_unavailable))
                    }
                }
            }
        }
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
