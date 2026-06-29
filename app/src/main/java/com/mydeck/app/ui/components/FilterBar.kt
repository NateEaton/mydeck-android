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
import com.mydeck.app.domain.model.FilterFormState
import com.mydeck.app.domain.model.ProgressFilter
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A horizontal row of InputChip elements summarising the active filters that differ from a
 * [baseline] filter.  Each chip has a trailing ✕ to dismiss that specific filter (reverting that
 * field to the baseline value).  Tapping anywhere on the bar (not on a chip) opens the filter sheet.
 *
 * The [baseline] is the drawer preset's defaults for an ordinary list, or the active collection's
 * own criteria when a collection is selected — so collection chips show only the filters layered on
 * top of the collection, not the collection's own criteria.
 *
 * The bar is only rendered when at least one chip is visible (i.e. the current filter differs from
 * the baseline in a user-visible way).
 */
@Composable
fun FilterBar(
    filterFormState: FilterFormState,
    baseline: FilterFormState,
    onFilterChanged: (FilterFormState) -> Unit,
    onOpenFilterSheet: () -> Unit,
) {
    data class Chip(val label: String, val onDismiss: () -> Unit)

    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    val chips = buildList<Chip> {
        if (filterFormState.search != null && filterFormState.search != baseline.search) {
            add(Chip("${stringResource(R.string.filter_search)}: ${filterFormState.search}") {
                onFilterChanged(filterFormState.copy(search = baseline.search))
            })
        }
        if (filterFormState.title != null && filterFormState.title != baseline.title) {
            add(Chip("${stringResource(R.string.filter_title)}: ${filterFormState.title}") {
                onFilterChanged(filterFormState.copy(title = baseline.title))
            })
        }
        if (filterFormState.author != null && filterFormState.author != baseline.author) {
            add(Chip("${stringResource(R.string.filter_author)}: ${filterFormState.author}") {
                onFilterChanged(filterFormState.copy(author = baseline.author))
            })
        }
        if (filterFormState.site != null && filterFormState.site != baseline.site) {
            add(Chip("${stringResource(R.string.filter_site)}: ${filterFormState.site}") {
                onFilterChanged(filterFormState.copy(site = baseline.site))
            })
        }
        if (filterFormState.label != null && filterFormState.label != baseline.label) {
            add(Chip("${stringResource(R.string.filter_label)}: ${filterFormState.label}") {
                onFilterChanged(filterFormState.copy(label = baseline.label))
            })
        }
        if (filterFormState.fromDate != null && filterFormState.fromDate != baseline.fromDate) {
            val formatted = dateFormatter.format(Date(filterFormState.fromDate.toEpochMilliseconds()))
            add(Chip("${stringResource(R.string.filter_from_date)}: $formatted") {
                onFilterChanged(filterFormState.copy(fromDate = baseline.fromDate))
            })
        }
        if (filterFormState.toDate != null && filterFormState.toDate != baseline.toDate) {
            val formatted = dateFormatter.format(Date(filterFormState.toDate.toEpochMilliseconds()))
            add(Chip("${stringResource(R.string.filter_to_date)}: $formatted") {
                onFilterChanged(filterFormState.copy(toDate = baseline.toDate))
            })
        }
        // Type chips: show synthetic "Any" chip when the baseline's types were cleared,
        // otherwise show each type that was added beyond the baseline.
        if (filterFormState.types.isEmpty() && baseline.types.isNotEmpty()) {
            add(Chip("${stringResource(R.string.filter_type)}: ${stringResource(R.string.filter_type_any)}") {
                onFilterChanged(filterFormState.copy(types = baseline.types))
            })
        } else {
            val addedTypes = filterFormState.types - baseline.types
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
        }
        // Progress chips: each status added beyond the baseline.
        (filterFormState.progress - baseline.progress).forEach { pf ->
            val pfName = when (pf) {
                ProgressFilter.UNVIEWED -> stringResource(R.string.progress_unviewed)
                ProgressFilter.IN_PROGRESS -> stringResource(R.string.progress_in_progress)
                ProgressFilter.COMPLETED -> stringResource(R.string.progress_completed)
            }
            add(Chip("${stringResource(R.string.filter_progress)}: $pfName") {
                onFilterChanged(filterFormState.copy(progress = filterFormState.progress - pf))
            })
        }
        // isFavorite — show explicit chip when non-null and differs, or synthetic N/A chip
        // when null and the baseline is non-null (broadened scope).
        if (filterFormState.isFavorite != baseline.isFavorite) {
            if (filterFormState.isFavorite != null) {
                val yesNo = if (filterFormState.isFavorite) stringResource(R.string.tri_state_yes) else stringResource(R.string.tri_state_no)
                add(Chip("${stringResource(R.string.filter_is_favorite)}: $yesNo") {
                    onFilterChanged(filterFormState.copy(isFavorite = baseline.isFavorite))
                })
            } else if (baseline.isFavorite != null) {
                add(Chip("${stringResource(R.string.filter_is_favorite)}: ${stringResource(R.string.tri_state_any)}") {
                    onFilterChanged(filterFormState.copy(isFavorite = baseline.isFavorite))
                })
            }
        }
        // isArchived — show explicit chip when non-null and differs, or synthetic N/A chip
        // when null and the baseline is non-null (broadened scope).
        if (filterFormState.isArchived != baseline.isArchived) {
            if (filterFormState.isArchived != null) {
                val yesNo = if (filterFormState.isArchived) stringResource(R.string.tri_state_yes) else stringResource(R.string.tri_state_no)
                add(Chip("${stringResource(R.string.filter_is_archived)}: $yesNo") {
                    onFilterChanged(filterFormState.copy(isArchived = baseline.isArchived))
                })
            } else if (baseline.isArchived != null) {
                add(Chip("${stringResource(R.string.filter_is_archived)}: ${stringResource(R.string.tri_state_any)}") {
                    onFilterChanged(filterFormState.copy(isArchived = baseline.isArchived))
                })
            }
        }
        if (filterFormState.isLoaded != null && filterFormState.isLoaded != baseline.isLoaded) {
            val yesNo = if (filterFormState.isLoaded) stringResource(R.string.tri_state_yes) else stringResource(R.string.tri_state_no)
            add(Chip("${stringResource(R.string.filter_is_loaded)}: $yesNo") {
                onFilterChanged(filterFormState.copy(isLoaded = baseline.isLoaded))
            })
        }
        if (filterFormState.withLabels != null && filterFormState.withLabels != baseline.withLabels) {
            val yesNo = if (filterFormState.withLabels) stringResource(R.string.tri_state_yes) else stringResource(R.string.tri_state_no)
            add(Chip("${stringResource(R.string.filter_with_labels)}: $yesNo") {
                onFilterChanged(filterFormState.copy(withLabels = baseline.withLabels))
            })
        }
        if (filterFormState.withErrors != null && filterFormState.withErrors != baseline.withErrors) {
            val yesNo = if (filterFormState.withErrors) stringResource(R.string.tri_state_yes) else stringResource(R.string.tri_state_no)
            add(Chip("${stringResource(R.string.filter_with_errors)}: $yesNo") {
                onFilterChanged(filterFormState.copy(withErrors = baseline.withErrors))
            })
        }
        val unknownLabel = stringResource(R.string.filter_length_unknown)
        val minRt = filterFormState.minReadingTime
        val maxRt = filterFormState.maxReadingTime
        val nullRt = filterFormState.includeNullReadingTime
        val readTimePrefix = stringResource(R.string.filter_reading_time)
        if (minRt != null || maxRt != null || nullRt) {
            val rangeLabel = when {
                minRt != null && maxRt != null -> "$minRt–$maxRt min"
                minRt != null -> "≥$minRt min"
                maxRt != null -> "≤$maxRt min"
                else -> null
            }
            val chipLabel = when {
                rangeLabel != null && nullRt -> "$readTimePrefix: $rangeLabel + $unknownLabel"
                rangeLabel != null -> "$readTimePrefix: $rangeLabel"
                else -> "$readTimePrefix: $unknownLabel"
            }
            add(Chip(chipLabel) {
                onFilterChanged(filterFormState.copy(minReadingTime = null, maxReadingTime = null, includeNullReadingTime = false))
            })
        }
        val minWc = filterFormState.minWordCount
        val maxWc = filterFormState.maxWordCount
        val nullWc = filterFormState.includeNullWordCount
        val wordsPrefix = stringResource(R.string.filter_word_count)
        if (minWc != null || maxWc != null || nullWc) {
            val rangeLabel = when {
                minWc != null && maxWc != null -> "$minWc–$maxWc"
                minWc != null -> "≥$minWc"
                maxWc != null -> "≤$maxWc"
                else -> null
            }
            val chipLabel = when {
                rangeLabel != null && nullWc -> "$wordsPrefix: $rangeLabel + $unknownLabel"
                rangeLabel != null -> "$wordsPrefix: $rangeLabel"
                else -> "$wordsPrefix: $unknownLabel"
            }
            add(Chip(chipLabel) {
                onFilterChanged(filterFormState.copy(minWordCount = null, maxWordCount = null, includeNullWordCount = false))
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
