package com.mydeck.app.ui.highlights

import com.mydeck.app.domain.HighlightsRefreshReason
import com.mydeck.app.domain.HighlightsRepository
import com.mydeck.app.domain.HighlightsSyncState
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
        assertTrue(state.refreshFailed)
        assertFalse(state.isInitialLocalLoad)
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

    private fun highlight(id: String): HighlightSummary {
        return HighlightSummary(
            id = id,
            text = "Saved highlight",
            color = "yellow",
            note = "",
            created = Instant.parse("2026-01-01T00:00:00Z"),
            bookmarkId = "bookmark-1",
            bookmarkTitle = "Bookmark",
            bookmarkSiteName = "Example",
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
    }
}
