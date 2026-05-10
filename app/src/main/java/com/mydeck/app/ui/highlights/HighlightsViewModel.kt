package com.mydeck.app.ui.highlights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mydeck.app.domain.HighlightsRefreshReason
import com.mydeck.app.domain.HighlightsRepository
import com.mydeck.app.domain.HighlightsSyncState
import com.mydeck.app.domain.model.BookmarkHighlightGroup
import com.mydeck.app.domain.model.HighlightSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HighlightsViewModel @Inject constructor(
    private val highlightsRepository: HighlightsRepository,
) : ViewModel() {

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
            }
    ) { groupsOrNull, syncState ->
        val state = HighlightsUiState(
            groups = groupsOrNull.orEmpty(),
            isInitialLocalLoad = groupsOrNull == null,
            isRefreshing = syncState is HighlightsSyncState.Running,
            refreshFailed = syncState is HighlightsSyncState.Failed,
            loadedCount = (syncState as? HighlightsSyncState.Running)?.loadedCount
        )
        Timber.d(
            "Highlights UI state derived: groups=%d highlights=%d initialLocalLoad=%s refreshing=%s refreshFailed=%s loadedCount=%s",
            state.groups.size,
            state.groups.sumOf { it.highlights.size },
            state.isInitialLocalLoad,
            state.isRefreshing,
            state.refreshFailed,
            state.loadedCount
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

    private fun refresh(reason: HighlightsRefreshReason) {
        viewModelScope.launch {
            val currentState = uiState.value
            Timber.d(
                "Highlights VM requesting refresh: reason=%s groups=%d highlights=%d refreshing=%s failed=%s",
                reason,
                currentState.groups.size,
                currentState.groups.sumOf { it.highlights.size },
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
    val isInitialLocalLoad: Boolean = true,
    val isRefreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    val loadedCount: Int? = null,
)

private fun HighlightsSyncState.toHighlightsLogString(): String {
    return when (this) {
        HighlightsSyncState.Idle -> "Idle"
        is HighlightsSyncState.Running -> "Running(loadedCount=$loadedCount)"
        is HighlightsSyncState.Failed -> "Failed(message=$message)"
    }
}
