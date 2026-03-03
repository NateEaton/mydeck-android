package com.mydeck.app.ui.settings

import android.content.Context
import com.mydeck.app.domain.model.Theme
import com.mydeck.app.io.prefs.SettingsDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
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
        every { settingsDataStore.sepiaEnabledFlow } returns MutableStateFlow(false)
        every { settingsDataStore.keepScreenOnWhileReadingFlow } returns MutableStateFlow(true)
        coEvery { settingsDataStore.getTheme() } returns Theme.SYSTEM
        coEvery { settingsDataStore.isSepiaEnabled() } returns false
        coEvery { settingsDataStore.isKeepScreenOnWhileReading() } returns true
        coEvery { settingsDataStore.saveTheme(any()) } returns Unit
        coEvery { settingsDataStore.saveSepiaEnabled(any()) } returns Unit
        coEvery { settingsDataStore.saveKeepScreenOnWhileReading(any()) } returns Unit

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
    fun `onSepiaToggled saves and reflects in state`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { settingsDataStore.isSepiaEnabled() } returnsMany listOf(false, true)
        val states = mutableListOf<UiSettingsUiState>()
        val job = launch {
            viewModel.uiState.collect { states.add(it) }
        }
        advanceUntilIdle()

        viewModel.onSepiaToggled(true)
        advanceUntilIdle()
        job.cancel()

        coVerify { settingsDataStore.saveSepiaEnabled(true) }
        assertTrue(states.last().useSepiaInLight)
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
