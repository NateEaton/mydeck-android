package com.mydeck.app.ui.highlights

import com.mydeck.app.domain.HighlightsRefreshReason
import com.mydeck.app.domain.HighlightsRepository
import com.mydeck.app.domain.HighlightsSyncState
import com.mydeck.app.domain.BookmarkAnnotationReconcileResult
import com.mydeck.app.domain.BookmarkAnnotationSyncReason
import com.mydeck.app.domain.SyncPriority
import com.mydeck.app.domain.model.HighlightSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HighlightsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeHighlightsRepository

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = FakeHighlightsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `cached highlights stay visible while refresh is running`() = runTest {
        repository.highlights.value = listOf(highlight("h1"))
        repository.syncState.value = HighlightsSyncState.Running(loadedCount = 10)

        val viewModel = HighlightsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("bookmark-1"), state.groups.map { it.bookmarkId })
        assertEquals(listOf("bookmark-1"), state.filteredGroups.map { it.bookmarkId })
        assertTrue(state.isRefreshing)
        assertFalse(state.isInitialLocalLoad)
    }

    @Test
    fun `cached highlights stay visible after refresh failure`() = runTest {
        repository.highlights.value = listOf(highlight("h1"))
        repository.syncState.value = HighlightsSyncState.Failed("Could not refresh highlights")

        val viewModel = HighlightsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.groups.single().highlights.size)
        assertEquals(1, state.filteredGroups.single().highlights.size)
        assertTrue(state.refreshFailed)
        assertFalse(state.isInitialLocalLoad)
    }

    @Test
    fun `query matches highlight text`() = runTest {
        repository.highlights.value = listOf(
            highlight("h1", text = "The quiet archive"),
            highlight("h2", text = "Something else"),
        )

        val viewModel = HighlightsViewModel(repository)
        advanceUntilIdle()
        viewModel.setSearchQuery("archive")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("bookmark-1"), state.filteredGroups.map { it.bookmarkId })
        assertEquals(listOf("h1"), visibleHighlightIds(state))
    }

    @Test
    fun `query matches note`() = runTest {
        repository.highlights.value = listOf(
            highlight("h1", note = "Follow up on this"),
            highlight("h2", note = ""),
        )

        val viewModel = HighlightsViewModel(repository)
        advanceUntilIdle()
        viewModel.setSearchQuery("follow")
        advanceUntilIdle()

        assertEquals(listOf("h1"), visibleHighlightIds(viewModel.uiState.value))
    }

    @Test
    fun `query matches bookmark title`() = runTest {
        repository.highlights.value = listOf(
            highlight("h1", bookmarkId = "bookmark-1", bookmarkTitle = "Compose notes"),
            highlight("h2", bookmarkId = "bookmark-2", bookmarkTitle = "Kotlin update"),
        )

        val viewModel = HighlightsViewModel(repository)
        advanceUntilIdle()
        viewModel.setSearchQuery("compose")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("bookmark-1"), state.filteredGroups.map { it.bookmarkId })
        assertEquals(listOf("h1"), visibleHighlightIds(state))
    }

    @Test
    fun `query matches site name`() = runTest {
        repository.highlights.value = listOf(
            highlight("h1", bookmarkId = "bookmark-1", bookmarkSiteName = "example.com"),
            highlight("h2", bookmarkId = "bookmark-2", bookmarkSiteName = "another.test"),
        )

        val viewModel = HighlightsViewModel(repository)
        advanceUntilIdle()
        viewModel.setSearchQuery("example")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("bookmark-1"), state.filteredGroups.map { it.bookmarkId })
        assertEquals(listOf("h1"), visibleHighlightIds(state))
    }

    @Test
    fun `title query includes visible highlights from matching bookmark group`() = runTest {
        repository.highlights.value = listOf(
            highlight("h1", bookmarkId = "bookmark-1", bookmarkTitle = "Compose notes"),
            highlight("h2", bookmarkId = "bookmark-1", bookmarkTitle = "Compose notes", text = "Another saved line"),
            highlight("h3", bookmarkId = "bookmark-2", bookmarkTitle = "Kotlin update"),
        )

        val viewModel = HighlightsViewModel(repository)
        advanceUntilIdle()
        viewModel.setSearchQuery("compose")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("bookmark-1"), state.filteredGroups.map { it.bookmarkId })
        assertEquals(listOf("h1", "h2"), visibleHighlightIds(state))
    }

    @Test
    fun `search target filters restrict matching`() = runTest {
        repository.highlights.value = listOf(
            highlight("h1", text = "needle in text", note = ""),
            highlight("h2", text = "plain text", note = "needle in note"),
        )

        val viewModel = HighlightsViewModel(repository)
        advanceUntilIdle()
        viewModel.setSearchQuery("needle")
        viewModel.toggleSearchTarget(HighlightSearchTarget.Notes)
        viewModel.toggleSearchTarget(HighlightSearchTarget.Title)
        viewModel.toggleSearchTarget(HighlightSearchTarget.Site)
        advanceUntilIdle()

        assertEquals(listOf("h1"), visibleHighlightIds(viewModel.uiState.value))
    }

    @Test
    fun `color filter restricts results`() = runTest {
        repository.highlights.value = listOf(
            highlight("h1", color = "yellow"),
            highlight("h2", color = "red"),
        )

        val viewModel = HighlightsViewModel(repository)
        advanceUntilIdle()
        viewModel.selectColorFilter(HighlightColorFilter.Red)
        advanceUntilIdle()

        assertEquals(listOf("h2"), visibleHighlightIds(viewModel.uiState.value))
    }

    @Test
    fun `notes filter restricts results`() = runTest {
        repository.highlights.value = listOf(
            highlight("h1", note = "has a note"),
            highlight("h2", note = ""),
        )

        val viewModel = HighlightsViewModel(repository)
        advanceUntilIdle()
        viewModel.selectNoteFilter(HighlightNoteFilter.WithNotes)
        advanceUntilIdle()

        assertEquals(listOf("h1"), visibleHighlightIds(viewModel.uiState.value))
    }

    @Test
    fun `search filter preserves grouped output and removes nonmatching highlights`() = runTest {
        repository.highlights.value = listOf(
            highlight("h1", bookmarkId = "bookmark-1", text = "needle here"),
            highlight("h2", bookmarkId = "bookmark-1", text = "plain text"),
            highlight("h3", bookmarkId = "bookmark-2", text = "needle there"),
        )

        val viewModel = HighlightsViewModel(repository)
        advanceUntilIdle()
        viewModel.setSearchQuery("needle")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("bookmark-1", "bookmark-2"), state.filteredGroups.map { it.bookmarkId })
        assertEquals(listOf(listOf("h1"), listOf("h3")), state.filteredGroups.map { it.highlights.map { highlight -> highlight.id } })
    }

    @Test
    fun `descending group order uses newest visible highlight per group`() = runTest {
        repository.highlights.value = listOf(
            highlight("a-new-filtered", bookmarkId = "a", color = "red", created = "2026-05-11T00:00:00Z"),
            highlight("a-old", bookmarkId = "a", color = "yellow", created = "2026-01-01T00:00:00Z"),
            highlight("b", bookmarkId = "b", created = "2026-03-01T00:00:00Z"),
            highlight("c", bookmarkId = "c", created = "2026-04-01T00:00:00Z"),
        )

        val viewModel = HighlightsViewModel(repository)
        advanceUntilIdle()
        viewModel.selectColorFilter(HighlightColorFilter.Yellow)
        advanceUntilIdle()

        assertEquals(listOf("c", "b", "a"), viewModel.uiState.value.filteredGroups.map { it.bookmarkId })
    }

    @Test
    fun `descending sort orders highlights within each group newest first`() = runTest {
        repository.highlights.value = listOf(
            highlight("old", bookmarkId = "bookmark-1", created = "2026-01-01T00:00:00Z"),
            highlight("new", bookmarkId = "bookmark-1", created = "2026-05-01T00:00:00Z"),
            highlight("middle", bookmarkId = "bookmark-1", created = "2026-03-01T00:00:00Z"),
        )

        val viewModel = HighlightsViewModel(repository)
        advanceUntilIdle()

        assertEquals(listOf("new", "middle", "old"), visibleHighlightIds(viewModel.uiState.value))
    }

    @Test
    fun `ascending group order uses oldest visible highlight per group`() = runTest {
        repository.highlights.value = listOf(
            highlight("a-new-filtered", bookmarkId = "a", color = "red", created = "2026-05-11T00:00:00Z"),
            highlight("a-old", bookmarkId = "a", color = "yellow", created = "2026-01-01T00:00:00Z"),
            highlight("b", bookmarkId = "b", created = "2026-03-01T00:00:00Z"),
            highlight("c", bookmarkId = "c", created = "2026-04-01T00:00:00Z"),
        )

        val viewModel = HighlightsViewModel(repository)
        advanceUntilIdle()
        viewModel.selectColorFilter(HighlightColorFilter.Yellow)
        viewModel.toggleSortOrder()
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "c"), viewModel.uiState.value.filteredGroups.map { it.bookmarkId })
    }

    @Test
    fun `ascending sort orders highlights within each group oldest first`() = runTest {
        repository.highlights.value = listOf(
            highlight("old", bookmarkId = "bookmark-1", created = "2026-01-01T00:00:00Z"),
            highlight("new", bookmarkId = "bookmark-1", created = "2026-05-01T00:00:00Z"),
            highlight("middle", bookmarkId = "bookmark-1", created = "2026-03-01T00:00:00Z"),
        )

        val viewModel = HighlightsViewModel(repository)
        advanceUntilIdle()
        viewModel.toggleSortOrder()
        advanceUntilIdle()

        assertEquals(listOf("old", "middle", "new"), visibleHighlightIds(viewModel.uiState.value))
    }

    @Test
    fun `empty query returns all highlights`() = runTest {
        repository.highlights.value = listOf(highlight("h1"), highlight("h2"))

        val viewModel = HighlightsViewModel(repository)
        advanceUntilIdle()
        viewModel.setSearchQuery("")
        advanceUntilIdle()

        assertEquals(listOf("h1", "h2"), visibleHighlightIds(viewModel.uiState.value))
    }

    @Test
    fun `no match state is derived for active search with zero results`() = runTest {
        repository.highlights.value = listOf(highlight("h1", text = "Saved highlight"))

        val viewModel = HighlightsViewModel(repository)
        advanceUntilIdle()
        viewModel.setSearchQuery("missing")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasNoMatches)
    }

    @Test
    fun `search and filters do not request extra refreshes`() = runTest {
        repository.highlights.value = listOf(highlight("h1"))

        val viewModel = HighlightsViewModel(repository)
        advanceUntilIdle()
        assertEquals(emptyList<HighlightsRefreshReason>(), repository.refreshRequests)

        viewModel.setSearchQuery("saved")
        viewModel.toggleSearchTarget(HighlightSearchTarget.Notes)
        viewModel.selectColorFilter(HighlightColorFilter.Yellow)
        viewModel.selectNoteFilter(HighlightNoteFilter.WithoutNotes)
        advanceUntilIdle()

        assertEquals(emptyList<HighlightsRefreshReason>(), repository.refreshRequests)
    }

    @Test
    fun `closing search controls does not clear active query or filters`() = runTest {
        repository.highlights.value = listOf(
            highlight("h1", color = "red", text = "needle"),
            highlight("h2", color = "yellow", text = "needle"),
        )

        val viewModel = HighlightsViewModel(repository)
        advanceUntilIdle()
        viewModel.setSearchQuery("needle")
        viewModel.selectColorFilter(HighlightColorFilter.Red)
        viewModel.setSearchActive(false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSearchActive)
        assertEquals("needle", state.query)
        assertEquals(HighlightColorFilter.Red, state.selectedColor)
        assertEquals(listOf("h1"), visibleHighlightIds(state))
    }

    @Test
    fun `explicit clear resets to unfiltered grouped list`() = runTest {
        repository.highlights.value = listOf(
            highlight("h1", color = "red", text = "needle"),
            highlight("h2", color = "yellow", text = "plain"),
        )

        val viewModel = HighlightsViewModel(repository)
        advanceUntilIdle()
        viewModel.setSearchQuery("needle")
        viewModel.selectColorFilter(HighlightColorFilter.Red)
        advanceUntilIdle()
        viewModel.clearFilters()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.query)
        assertEquals(SearchTargets.All, state.searchTargets)
        assertEquals(HighlightColorFilter.Any, state.selectedColor)
        assertEquals(HighlightNoteFilter.Any, state.noteFilter)
        assertEquals(listOf("h1", "h2"), visibleHighlightIds(state))
    }

    @Test
    fun `empty cache plus refresh failure exposes retryable empty error state`() = runTest {
        repository.highlights.value = emptyList()
        repository.syncState.value = HighlightsSyncState.Failed("Could not refresh highlights")

        val viewModel = HighlightsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.groups.isEmpty())
        assertTrue(state.refreshFailed)
        assertFalse(state.isInitialLocalLoad)
    }

    @Test
    fun `ui state does not regress to initial loading after cached rows emit`() = runTest {
        repository.highlights.value = listOf(highlight("h1"))

        val viewModel = HighlightsViewModel(repository)
        advanceUntilIdle()

        repository.syncState.value = HighlightsSyncState.Running(loadedCount = 1)
        advanceUntilIdle()
        repository.syncState.value = HighlightsSyncState.Failed("Could not refresh highlights")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.groups.single().highlights.size)
        assertEquals(1, state.filteredGroups.single().highlights.size)
        assertTrue(state.refreshFailed)
        assertFalse(state.isInitialLocalLoad)
    }

    private fun visibleHighlightIds(state: HighlightsUiState): List<String> {
        return state.filteredGroups.flatMap { group -> group.highlights.map { it.id } }
    }

    private fun highlight(
        id: String,
        text: String = "Saved highlight",
        color: String = "yellow",
        note: String = "",
        created: String = "2026-01-01T00:00:00Z",
        bookmarkId: String = "bookmark-1",
        bookmarkTitle: String = "Bookmark",
        bookmarkSiteName: String = "Example",
    ): HighlightSummary {
        return HighlightSummary(
            id = id,
            text = text,
            color = color,
            note = note,
            created = Instant.parse(created),
            bookmarkId = bookmarkId,
            bookmarkTitle = bookmarkTitle,
            bookmarkSiteName = bookmarkSiteName,
        )
    }

    private class FakeHighlightsRepository : HighlightsRepository {
        val highlights = MutableStateFlow<List<HighlightSummary>>(emptyList())
        val syncState = MutableStateFlow<HighlightsSyncState>(HighlightsSyncState.Idle)
        val refreshRequests = mutableListOf<HighlightsRefreshReason>()

        override fun observeHighlights(): Flow<List<HighlightSummary>> = highlights

        override fun observeHighlightCount(): Flow<Int> = MutableStateFlow(highlights.value.size)

        override fun observeSyncState(): StateFlow<HighlightsSyncState> = syncState

        override suspend fun requestRefresh(reason: HighlightsRefreshReason): Result<Unit> {
            refreshRequests += reason
            return Result.success(Unit)
        }

        override suspend fun requestBookmarkAnnotationChecks(
            bookmarkIds: Collection<String>,
            reason: BookmarkAnnotationSyncReason,
            priority: SyncPriority,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun reconcileBookmarkAnnotationsNow(
            bookmarkId: String,
            reason: BookmarkAnnotationSyncReason,
            force: Boolean,
        ): Result<BookmarkAnnotationReconcileResult> {
            return Result.success(
                BookmarkAnnotationReconcileResult(
                    bookmarkId = bookmarkId,
                    previousCount = 0,
                    remoteCount = 0,
                    changed = false,
                    skipped = false,
                    reason = "success",
                )
            )
        }
    }
}
