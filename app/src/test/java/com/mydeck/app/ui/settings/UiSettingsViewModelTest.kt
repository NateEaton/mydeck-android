package com.mydeck.app.ui.settings

import android.content.Context
import com.mydeck.app.domain.model.BookmarkShareFormat
import com.mydeck.app.domain.model.DarkAppearance
import com.mydeck.app.domain.model.LightAppearance
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.io.prefs.SettingsDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UiSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var context: Context
    private lateinit var viewModel: UiSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        settingsDataStore = mockk()

        every { settingsDataStore.themeFlow } returns MutableStateFlow(Theme.SYSTEM.name)
        every { settingsDataStore.lightAppearanceFlow } returns MutableStateFlow(LightAppearance.PAPER)
        every { settingsDataStore.darkAppearanceFlow } returns MutableStateFlow(DarkAppearance.DARK)
        every { settingsDataStore.bookmarkShareFormatFlow } returns MutableStateFlow(BookmarkShareFormat.URL_ONLY)
        every { settingsDataStore.keepScreenOnWhileReadingFlow } returns MutableStateFlow(true)
        every { settingsDataStore.fullscreenWhileReadingFlow } returns MutableStateFlow(false)
        coEvery { settingsDataStore.getTheme() } returns Theme.SYSTEM
        coEvery { settingsDataStore.getLightAppearance() } returns LightAppearance.PAPER
        coEvery { settingsDataStore.getDarkAppearance() } returns DarkAppearance.DARK
        coEvery { settingsDataStore.getBookmarkShareFormat() } returns BookmarkShareFormat.URL_ONLY
        coEvery { settingsDataStore.isKeepScreenOnWhileReading() } returns true
        coEvery { settingsDataStore.isFullscreenWhileReading() } returns false
        coEvery { settingsDataStore.saveTheme(any()) } returns Unit
        coEvery { settingsDataStore.saveLightAppearance(any()) } returns Unit
        coEvery { settingsDataStore.saveDarkAppearance(any()) } returns Unit
        coEvery { settingsDataStore.saveBookmarkShareFormat(any()) } returns Unit
        coEvery { settingsDataStore.saveKeepScreenOnWhileReading(any()) } returns Unit
        coEvery { settingsDataStore.saveFullscreenWhileReading(any()) } returns Unit

        viewModel = UiSettingsViewModel(settingsDataStore, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads keepScreenOnWhileReading default true from data store`() = runTest(UnconfinedTestDispatcher()) {
        val states = mutableListOf<UiSettingsUiState>()
        val job = launch {
            viewModel.uiState.collect { states.add(it) }
        }
        advanceUntilIdle()
        job.cancel()
        assertTrue(states.last().keepScreenOnWhileReading)
    }

    @Test
    fun `init loads keepScreenOnWhileReading false from data store`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { settingsDataStore.isKeepScreenOnWhileReading() } returns false
        every { settingsDataStore.keepScreenOnWhileReadingFlow } returns MutableStateFlow(true)
        viewModel = UiSettingsViewModel(settingsDataStore, context)
        val states = mutableListOf<UiSettingsUiState>()
        val job = launch {
            viewModel.uiState.collect { states.add(it) }
        }
        advanceUntilIdle()
        job.cancel()
        assertFalse(states.last().keepScreenOnWhileReading)
    }

    @Test
    fun `init loads curated appearances from data store`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { settingsDataStore.getLightAppearance() } returns LightAppearance.SEPIA
        coEvery { settingsDataStore.getDarkAppearance() } returns DarkAppearance.BLACK

        viewModel = UiSettingsViewModel(settingsDataStore, context)

        val states = mutableListOf<UiSettingsUiState>()
        val job = launch {
            viewModel.uiState.collect { states.add(it) }
        }
        advanceUntilIdle()
        job.cancel()

        assertEquals(LightAppearance.SEPIA, states.last().lightAppearance)
        assertEquals(DarkAppearance.BLACK, states.last().darkAppearance)
    }

    @Test
    fun `onKeepScreenOnWhileReadingToggled false saves and updates state`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { settingsDataStore.isKeepScreenOnWhileReading() } returnsMany listOf(true, false)
        val states = mutableListOf<UiSettingsUiState>()
        val job = launch {
            viewModel.uiState.collect { states.add(it) }
        }
        advanceUntilIdle()

        viewModel.onKeepScreenOnWhileReadingToggled(false)
        advanceUntilIdle()
        job.cancel()

        coVerify { settingsDataStore.saveKeepScreenOnWhileReading(false) }
        assertFalse(states.last().keepScreenOnWhileReading)
    }

    @Test
    fun `onKeepScreenOnWhileReadingToggled true saves and updates state`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { settingsDataStore.isKeepScreenOnWhileReading() } returnsMany listOf(false, true)
        every { settingsDataStore.keepScreenOnWhileReadingFlow } returns MutableStateFlow(false)
        viewModel = UiSettingsViewModel(settingsDataStore, context)
        val states = mutableListOf<UiSettingsUiState>()
        val job = launch {
            viewModel.uiState.collect { states.add(it) }
        }
        advanceUntilIdle()

        viewModel.onKeepScreenOnWhileReadingToggled(true)
        advanceUntilIdle()
        job.cancel()

        coVerify { settingsDataStore.saveKeepScreenOnWhileReading(true) }
        assertTrue(states.last().keepScreenOnWhileReading)
    }

    @Test
    fun `onLightAppearanceSelected saves and reflects in state`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { settingsDataStore.getLightAppearance() } returnsMany listOf(
            LightAppearance.PAPER,
            LightAppearance.SEPIA
        )
        val states = mutableListOf<UiSettingsUiState>()
        val job = launch {
            viewModel.uiState.collect { states.add(it) }
        }
        advanceUntilIdle()

        viewModel.onLightAppearanceSelected(LightAppearance.SEPIA)
        advanceUntilIdle()
        job.cancel()

        coVerify { settingsDataStore.saveLightAppearance(LightAppearance.SEPIA) }
        assertEquals(LightAppearance.SEPIA, states.last().lightAppearance)
    }

    @Test
    fun `onDarkAppearanceSelected saves and reflects in state`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { settingsDataStore.getDarkAppearance() } returnsMany listOf(
            DarkAppearance.DARK,
            DarkAppearance.BLACK
        )
        val states = mutableListOf<UiSettingsUiState>()
        val job = launch {
            viewModel.uiState.collect { states.add(it) }
        }
        advanceUntilIdle()

        viewModel.onDarkAppearanceSelected(DarkAppearance.BLACK)
        advanceUntilIdle()
        job.cancel()

        coVerify { settingsDataStore.saveDarkAppearance(DarkAppearance.BLACK) }
        assertEquals(DarkAppearance.BLACK, states.last().darkAppearance)
    }

    @Test
    fun `onFullscreenWhileReadingToggled true saves and updates state`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { settingsDataStore.isFullscreenWhileReading() } returnsMany listOf(false, true)
        val states = mutableListOf<UiSettingsUiState>()
        val job = launch {
            viewModel.uiState.collect { states.add(it) }
        }
        advanceUntilIdle()

        viewModel.onFullscreenWhileReadingToggled(true)
        advanceUntilIdle()
        job.cancel()

        coVerify { settingsDataStore.saveFullscreenWhileReading(true) }
        assertTrue(states.last().fullscreenWhileReading)
    }

    @Test
    fun `onBookmarkShareFormatSelected saves and reflects in state`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { settingsDataStore.getBookmarkShareFormat() } returnsMany listOf(
            BookmarkShareFormat.URL_ONLY,
            BookmarkShareFormat.TITLE_AND_URL_MULTILINE
        )
        val states = mutableListOf<UiSettingsUiState>()
        val job = launch {
            viewModel.uiState.collect { states.add(it) }
        }
        advanceUntilIdle()

        viewModel.onBookmarkShareFormatSelected(BookmarkShareFormat.TITLE_AND_URL_MULTILINE)
        advanceUntilIdle()
        job.cancel()

        coVerify {
            settingsDataStore.saveBookmarkShareFormat(BookmarkShareFormat.TITLE_AND_URL_MULTILINE)
        }
        assertEquals(
            BookmarkShareFormat.TITLE_AND_URL_MULTILINE,
            states.last().bookmarkShareFormat
        )
    }

    @Test
    fun `onThemeModeSelected saves and reflects in state`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { settingsDataStore.getTheme() } returnsMany listOf(Theme.SYSTEM, Theme.DARK)
        val states = mutableListOf<UiSettingsUiState>()
        val job = launch {
            viewModel.uiState.collect { states.add(it) }
        }
        advanceUntilIdle()

        viewModel.onThemeModeSelected(Theme.DARK)
        advanceUntilIdle()
        job.cancel()

        coVerify { settingsDataStore.saveTheme(Theme.DARK) }
        assertEquals(Theme.DARK, states.last().themeMode)
    }
}
