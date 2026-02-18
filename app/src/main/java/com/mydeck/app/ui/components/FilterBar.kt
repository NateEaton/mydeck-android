package com.mydeck.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mydeck.app.R
import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.DrawerPreset
import com.mydeck.app.domain.model.FilterFormState
import com.mydeck.app.domain.model.ProgressFilter
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A horizontal row of InputChip elements summarising the active filters that differ from
 * the current drawer preset defaults.  Each chip has a trailing ✕ to dismiss that specific
 * filter.  Tapping anywhere on the bar (not on a chip) opens the filter bottom sheet.
 *
 * The bar is only rendered when at least one chip is visible (i.e. when the current
 * filter state differs from the preset default in a user-visible way).
 */
@Composable
fun FilterBar(
    filterFormState: FilterFormState,
    drawerPreset: DrawerPreset,
    onFilterChanged: (FilterFormState) -> Unit,
    onOpenFilterSheet: () -> Unit,
) {
    // Compute which chips to show by comparing against the preset defaults.
    val preset = remember(drawerPreset) { FilterFormState.fromPreset(drawerPreset) }

    data class Chip(val label: String, val onDismiss: () -> Unit)

    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    val chips = buildList<Chip> {
        filterFormState.search?.let { v ->
            add(Chip("${stringResource(R.string.filter_search)}: $v") {
                onFilterChanged(filterFormState.copy(search = null))
            })
        }
        filterFormState.title?.let { v ->
            add(Chip("${stringResource(R.string.filter_title)}: $v") {
                onFilterChanged(filterFormState.copy(title = null))
            })
        }
        filterFormState.author?.let { v ->
            add(Chip("${stringResource(R.string.filter_author)}: $v") {
                onFilterChanged(filterFormState.copy(author = null))
            })
        }
        filterFormState.site?.let { v ->
            add(Chip("${stringResource(R.string.filter_site)}: $v") {
                onFilterChanged(filterFormState.copy(site = null))
            })
        }
        filterFormState.label?.let { v ->
            add(Chip("${stringResource(R.string.filter_label)}: $v") {
                onFilterChanged(filterFormState.copy(label = null))
            })
        }
        filterFormState.fromDate?.let { v ->
            val formatted = dateFormatter.format(Date(v.toEpochMilliseconds()))
            add(Chip("${stringResource(R.string.filter_from_date)}: $formatted") {
                onFilterChanged(filterFormState.copy(fromDate = null))
            })
        }
        filterFormState.toDate?.let { v ->
            val formatted = dateFormatter.format(Date(v.toEpochMilliseconds()))
            add(Chip("${stringResource(R.string.filter_to_date)}: $formatted") {
                onFilterChanged(filterFormState.copy(toDate = null))
            })
        }
        // Type chips: show each type that differs from the preset
        val addedTypes = filterFormState.types - preset.types
        addedTypes.forEach { type ->
            val typeName = when (type) {
                Bookmark.Type.Article -> stringResource(R.string.filter_type_article)
                Bookmark.Type.Video -> stringResource(R.string.filter_type_video)
                Bookmark.Type.Picture -> stringResource(R.string.filter_type_picture)
            }
            add(Chip("${stringResource(R.string.filter_type)}: $typeName") {
                onFilterChanged(filterFormState.copy(types = filterFormState.types - type))
            })
        }
        // Progress chips
        filterFormState.progress.forEach { pf ->
            val pfName = when (pf) {
                ProgressFilter.UNVIEWED -> stringResource(R.string.progress_unviewed)
                ProgressFilter.IN_PROGRESS -> stringResource(R.string.progress_in_progress)
                ProgressFilter.COMPLETED -> stringResource(R.string.progress_completed)
            }
            add(Chip("${stringResource(R.string.filter_progress)}: $pfName") {
                onFilterChanged(filterFormState.copy(progress = filterFormState.progress - pf))
            })
        }
        // isFavorite — only show if differs from preset and is non-null
        if (filterFormState.isFavorite != null && filterFormState.isFavorite != preset.isFavorite) {
            val yesNo = if (filterFormState.isFavorite) stringResource(R.string.tri_state_yes) else stringResource(R.string.tri_state_no)
            add(Chip("${stringResource(R.string.filter_is_favorite)}: $yesNo") {
                onFilterChanged(filterFormState.copy(isFavorite = null))
            })
        }
        // isArchived — only show if differs from preset and is non-null
        if (filterFormState.isArchived != null && filterFormState.isArchived != preset.isArchived) {
            val yesNo = if (filterFormState.isArchived) stringResource(R.string.tri_state_yes) else stringResource(R.string.tri_state_no)
            add(Chip("${stringResource(R.string.filter_is_archived)}: $yesNo") {
                onFilterChanged(filterFormState.copy(isArchived = null))
            })
        }
        filterFormState.isLoaded?.let { v ->
            val yesNo = if (v) stringResource(R.string.tri_state_yes) else stringResource(R.string.tri_state_no)
            add(Chip("${stringResource(R.string.filter_is_loaded)}: $yesNo") {
                onFilterChanged(filterFormState.copy(isLoaded = null))
            })
        }
        filterFormState.withLabels?.let { v ->
            val yesNo = if (v) stringResource(R.string.tri_state_yes) else stringResource(R.string.tri_state_no)
            add(Chip("${stringResource(R.string.filter_with_labels)}: $yesNo") {
                onFilterChanged(filterFormState.copy(withLabels = null))
            })
        }
        filterFormState.withErrors?.let { v ->
            val yesNo = if (v) stringResource(R.string.tri_state_yes) else stringResource(R.string.tri_state_no)
            add(Chip("${stringResource(R.string.filter_with_errors)}: $yesNo") {
                onFilterChanged(filterFormState.copy(withErrors = null))
            })
        }
    }

    if (chips.isEmpty()) return

    LazyRow(
        modifier = Modifier
            .clickable { onOpenFilterSheet() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        items(chips) { chip ->
            InputChip(
                selected = true,
                onClick = { onOpenFilterSheet() },
                label = { Text(chip.label) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = null,
                        modifier = Modifier.clickable { chip.onDismiss() }
                    )
                },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}
