package com.mydeck.app.ui.highlights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydeck.app.domain.HighlightsRefreshReason
import com.mydeck.app.domain.HighlightsRepository
import com.mydeck.app.domain.HighlightsSyncState
import com.mydeck.app.domain.model.BookmarkHighlightGroup
import com.mydeck.app.domain.model.HighlightSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HighlightsViewModel @Inject constructor(
    private val highlightsRepository: HighlightsRepository,
) : ViewModel() {

    private val searchState = MutableStateFlow(HighlightsSearchState())

    val uiState: StateFlow<HighlightsUiState> = combine(
        highlightsRepository.observeHighlights()
            .map<List<HighlightSummary>, List<BookmarkHighlightGroup>?> { highlights ->
                val groups = group(highlights)
                Timber.d(
                    "Highlights screen render from cache: highlights=%d groups=%d empty=%s",
                    highlights.size,
                    groups.size,
                    highlights.isEmpty()
                )
                groups
            }
            .onStart {
                Timber.d("Highlights cached observation subscribed")
                emit(null)
            },
        highlightsRepository.observeSyncState()
            .onEach { syncState ->
                Timber.d("Highlights sync state observed: %s", syncState.toHighlightsLogString())
            },
        searchState
    ) { groupsOrNull, syncState, searchState ->
        val groups = groupsOrNull.orEmpty()
        val filteredGroups = filterHighlightGroups(groups, searchState)
        val state = HighlightsUiState(
            groups = groups,
            filteredGroups = filteredGroups,
            query = searchState.query,
            searchTargets = searchState.searchTargets,
            selectedColor = searchState.selectedColor,
            noteFilter = searchState.noteFilter,
            sortOrder = searchState.sortOrder,
            isSearchActive = searchState.isSearchActive,
            isInitialLocalLoad = groupsOrNull == null,
            isRefreshing = syncState is HighlightsSyncState.Running,
            refreshFailed = syncState is HighlightsSyncState.Failed,
            loadedCount = (syncState as? HighlightsSyncState.Running)?.loadedCount
        )
        Timber.d(
            "Highlights UI state derived: groups=%d highlights=%d filteredGroups=%d filteredHighlights=%d initialLocalLoad=%s refreshing=%s refreshFailed=%s loadedCount=%s",
            state.groups.size,
            state.totalHighlightCount,
            state.filteredGroups.size,
            state.filteredHighlightCount,
            state.isInitialLocalLoad,
            state.isRefreshing,
            state.refreshFailed,
            state.loadedCount
        )
        Timber.d(
            "Highlights filtered results: total=%d filtered=%d",
            state.totalHighlightCount,
            state.filteredHighlightCount
        )
        state
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HighlightsUiState()
    )

    init {
        refresh(HighlightsRefreshReason.SCREEN_OPEN)
    }

    fun refreshFromScreenOpen() {
        refresh(HighlightsRefreshReason.SCREEN_OPEN)
    }

    fun retry() {
        refresh(HighlightsRefreshReason.USER_RETRY)
    }

    fun setSearchActive(isActive: Boolean) {
        searchState.update { it.copy(isSearchActive = isActive) }
    }

    fun setSearchQuery(query: String) {
        searchState.update {
            it.copy(
                query = query,
                isSearchActive = true,
            )
        }
        logSearchState("Highlights search changed")
    }

    fun clearSearch() {
        searchState.update { it.copy(query = "") }
        logSearchState("Highlights search changed")
    }

    fun toggleSearchTarget(target: HighlightSearchTarget) {
        searchState.update { state ->
            state.copy(searchTargets = state.searchTargets.toggle(target))
        }
        logSearchState("Highlights search changed")
    }

    fun selectColorFilter(color: HighlightColorFilter) {
        searchState.update { it.copy(selectedColor = color) }
        logSearchState("Highlights search changed")
    }

    fun selectNoteFilter(noteFilter: HighlightNoteFilter) {
        searchState.update { it.copy(noteFilter = noteFilter) }
        logSearchState("Highlights search changed")
    }

    fun toggleSortOrder() {
        searchState.update { state ->
            state.copy(sortOrder = state.sortOrder.toggled())
        }
        Timber.d("Highlights sort changed: order=%s", searchState.value.sortOrder.name)
    }

    fun clearFilters() {
        searchState.update {
            it.copy(
                query = "",
                searchTargets = SearchTargets.All,
                selectedColor = HighlightColorFilter.Any,
                noteFilter = HighlightNoteFilter.Any,
            )
        }
        logSearchState("Highlights search changed")
    }

    fun logTitleTap() {
        Timber.d("Highlights scroll title tapped: action=top")
    }

    private fun refresh(reason: HighlightsRefreshReason) {
        viewModelScope.launch {
            val currentState = uiState.value
            Timber.d(
                "Highlights VM requesting refresh: reason=%s groups=%d highlights=%d refreshing=%s failed=%s",
                reason,
                currentState.groups.size,
                currentState.totalHighlightCount,
                currentState.isRefreshing,
                currentState.refreshFailed
            )
            highlightsRepository.requestRefresh(reason)
                .onSuccess {
                    Timber.d("Highlights VM refresh request completed: reason=%s result=success", reason)
                }
                .onFailure { error ->
                    Timber.w(error, "Highlights VM refresh request completed: reason=%s result=failure", reason)
                }
        }
    }

    private fun logSearchState(message: String) {
        val state = searchState.value
        Timber.d(
            "%s: queryLength=%d targets=%d colors=%d noteFilter=%s",
            message,
            state.query.length,
            state.searchTargets.activeCount,
            if (state.selectedColor == HighlightColorFilter.Any) 0 else 1,
            state.noteFilter.name
        )
    }

    /**
     * Groups flat highlights by bookmarkId. Groups are sorted by most-recent
     * highlight descending. Within each group, highlights are newest-first.
     */
    private fun group(highlights: List<HighlightSummary>): List<BookmarkHighlightGroup> {
        return highlights
            .sortedByDescending { it.created }
            .groupBy { it.bookmarkId }
            .map { (bookmarkId, items) ->
                BookmarkHighlightGroup(
                    bookmarkId = bookmarkId,
                    bookmarkTitle = items.first().bookmarkTitle,
                    bookmarkSiteName = items.first().bookmarkSiteName,
                    highlights = items,
                )
            }
    }
}

data class HighlightsUiState(
    val groups: List<BookmarkHighlightGroup> = emptyList(),
    val filteredGroups: List<BookmarkHighlightGroup> = emptyList(),
    val query: String = "",
    val searchTargets: SearchTargets = SearchTargets.All,
    val selectedColor: HighlightColorFilter = HighlightColorFilter.Any,
    val noteFilter: HighlightNoteFilter = HighlightNoteFilter.Any,
    val sortOrder: HighlightSortOrder = HighlightSortOrder.Descending,
    val isSearchActive: Boolean = false,
    val isInitialLocalLoad: Boolean = true,
    val isRefreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    val loadedCount: Int? = null,
) {
    val totalHighlightCount: Int
        get() = groups.sumOf { it.highlights.size }

    val filteredHighlightCount: Int
        get() = filteredGroups.sumOf { it.highlights.size }

    val hasActiveSearchOrFilters: Boolean
        get() = query.isNotBlank() ||
            searchTargets != SearchTargets.All ||
            selectedColor != HighlightColorFilter.Any ||
            noteFilter != HighlightNoteFilter.Any

    val hasNoMatches: Boolean
        get() = !isInitialLocalLoad && groups.isNotEmpty() && filteredGroups.isEmpty() && hasActiveSearchOrFilters
}

data class HighlightsSearchState(
    val query: String = "",
    val searchTargets: SearchTargets = SearchTargets.All,
    val selectedColor: HighlightColorFilter = HighlightColorFilter.Any,
    val noteFilter: HighlightNoteFilter = HighlightNoteFilter.Any,
    val sortOrder: HighlightSortOrder = HighlightSortOrder.Descending,
    val isSearchActive: Boolean = false,
)

data class SearchTargets(
    val text: Boolean = true,
    val notes: Boolean = true,
    val title: Boolean = true,
    val site: Boolean = true,
) {
    val activeCount: Int
        get() = listOf(text, notes, title, site).count { it }

    fun includes(target: HighlightSearchTarget): Boolean {
        return when (target) {
            HighlightSearchTarget.Text -> text
            HighlightSearchTarget.Notes -> notes
            HighlightSearchTarget.Title -> title
            HighlightSearchTarget.Site -> site
        }
    }

    fun toggle(target: HighlightSearchTarget): SearchTargets {
        if (activeCount == 1 && includes(target)) {
            return this
        }
        return when (target) {
            HighlightSearchTarget.Text -> copy(text = !text)
            HighlightSearchTarget.Notes -> copy(notes = !notes)
            HighlightSearchTarget.Title -> copy(title = !title)
            HighlightSearchTarget.Site -> copy(site = !site)
        }
    }

    companion object {
        val All = SearchTargets()
    }
}

enum class HighlightSearchTarget {
    Text,
    Notes,
    Title,
    Site,
}

enum class HighlightColorFilter(val colorValue: String?) {
    Any(null),
    Yellow("yellow"),
    Red("red"),
    Blue("blue"),
    Green("green"),
    None("none"),
}

enum class HighlightNoteFilter {
    Any,
    WithNotes,
    WithoutNotes,
}

enum class HighlightSortOrder {
    Descending,
    Ascending;

    fun toggled(): HighlightSortOrder {
        return when (this) {
            Descending -> Ascending
            Ascending -> Descending
        }
    }
}

internal fun filterHighlightGroups(
    groups: List<BookmarkHighlightGroup>,
    searchState: HighlightsSearchState,
): List<BookmarkHighlightGroup> {
    val query = searchState.query.trim()
    return groups
        .mapNotNull { group ->
            val groupMatchesQuery = matchesGroupQuery(group, query, searchState.searchTargets)
            val highlights = group.highlights.filter { highlight ->
                matchesQuery(highlight, query, searchState.searchTargets, groupMatchesQuery) &&
                    matchesColor(highlight, searchState.selectedColor) &&
                    matchesNoteFilter(highlight, searchState.noteFilter)
            }.sortHighlights(searchState.sortOrder)
            if (highlights.isEmpty()) {
                null
            } else {
                group.copy(highlights = highlights)
            }
        }
        .sortGroups(searchState.sortOrder)
}

private fun matchesGroupQuery(
    group: BookmarkHighlightGroup,
    query: String,
    targets: SearchTargets,
): Boolean {
    if (query.isBlank()) {
        return false
    }
    return (targets.title && group.bookmarkTitle.contains(query, ignoreCase = true)) ||
        (targets.site && group.bookmarkSiteName.contains(query, ignoreCase = true))
}

private fun matchesQuery(
    highlight: HighlightSummary,
    query: String,
    targets: SearchTargets,
    groupMatchesQuery: Boolean,
): Boolean {
    if (query.isBlank()) {
        return true
    }
    return groupMatchesQuery ||
        (targets.text && highlight.text.contains(query, ignoreCase = true)) ||
        (targets.notes && highlight.note.contains(query, ignoreCase = true))
}

private fun matchesColor(
    highlight: HighlightSummary,
    selectedColor: HighlightColorFilter,
): Boolean {
    return selectedColor == HighlightColorFilter.Any || highlight.color == selectedColor.colorValue
}

private fun matchesNoteFilter(
    highlight: HighlightSummary,
    noteFilter: HighlightNoteFilter,
): Boolean {
    return when (noteFilter) {
        HighlightNoteFilter.Any -> true
        HighlightNoteFilter.WithNotes -> highlight.note.isNotBlank()
        HighlightNoteFilter.WithoutNotes -> highlight.note.isBlank()
    }
}

private fun List<BookmarkHighlightGroup>.sortGroups(sortOrder: HighlightSortOrder): List<BookmarkHighlightGroup> {
    return when (sortOrder) {
        HighlightSortOrder.Descending -> sortedByDescending { it.groupSortDate(sortOrder) }
        HighlightSortOrder.Ascending -> sortedBy { it.groupSortDate(sortOrder) }
    }
}

private fun List<HighlightSummary>.sortHighlights(sortOrder: HighlightSortOrder): List<HighlightSummary> {
    return when (sortOrder) {
        HighlightSortOrder.Descending -> sortedByDescending { it.created }
        HighlightSortOrder.Ascending -> sortedBy { it.created }
    }
}

private fun BookmarkHighlightGroup.groupSortDate(sortOrder: HighlightSortOrder): kotlinx.datetime.Instant {
    return when (sortOrder) {
        HighlightSortOrder.Descending -> highlights.maxOf { it.created }
        HighlightSortOrder.Ascending -> highlights.minOf { it.created }
    }
}

private fun HighlightsSyncState.toHighlightsLogString(): String {
    return when (this) {
        HighlightsSyncState.Idle -> "Idle"
        is HighlightsSyncState.Running -> "Running(loadedCount=$loadedCount)"
        is HighlightsSyncState.Failed -> "Failed(message=$message)"
    }
}
