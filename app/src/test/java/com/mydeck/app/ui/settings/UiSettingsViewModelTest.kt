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
import kotlinx.coroutines.test.StandardTestDispatcher
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
    fun `init loads keepScreenOnWhileReading default true from data store`() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertTrue(state.keepScreenOnWhileReading)
    }

    @Test
    fun `init loads keepScreenOnWhileReading false from data store`() = runTest {
        coEvery { settingsDataStore.isKeepScreenOnWhileReading() } returns false
        every { settingsDataStore.keepScreenOnWhileReadingFlow } returns MutableStateFlow(false)
        viewModel = UiSettingsViewModel(settingsDataStore, context)
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertFalse(state.keepScreenOnWhileReading)
    }

    @Test
    fun `onKeepScreenOnWhileReadingToggled false saves and updates state`() = runTest {
        coEvery { settingsDataStore.isKeepScreenOnWhileReading() } returns false
        advanceUntilIdle()

        viewModel.onKeepScreenOnWhileReadingToggled(false)
        advanceUntilIdle()

        coVerify { settingsDataStore.saveKeepScreenOnWhileReading(false) }
        val state = viewModel.uiState.first()
        assertFalse(state.keepScreenOnWhileReading)
    }

    @Test
    fun `onKeepScreenOnWhileReadingToggled true saves and updates state`() = runTest {
        coEvery { settingsDataStore.isKeepScreenOnWhileReading() } returns true
        advanceUntilIdle()

        viewModel.onKeepScreenOnWhileReadingToggled(true)
        advanceUntilIdle()

        coVerify { settingsDataStore.saveKeepScreenOnWhileReading(true) }
        val state = viewModel.uiState.first()
        assertTrue(state.keepScreenOnWhileReading)
    }

    @Test
    fun `onSepiaToggled saves and reflects in state`() = runTest {
        coEvery { settingsDataStore.isSepiaEnabled() } returns true
        advanceUntilIdle()

        viewModel.onSepiaToggled(true)
        advanceUntilIdle()

        coVerify { settingsDataStore.saveSepiaEnabled(true) }
        val state = viewModel.uiState.first()
        assertTrue(state.useSepiaInLight)
    }

    @Test
    fun `onThemeModeSelected saves and reflects in state`() = runTest {
        coEvery { settingsDataStore.getTheme() } returns Theme.DARK
        advanceUntilIdle()

        viewModel.onThemeModeSelected(Theme.DARK)
        advanceUntilIdle()

        coVerify { settingsDataStore.saveTheme(Theme.DARK) }
        val state = viewModel.uiState.first()
        assertEquals(Theme.DARK, state.themeMode)
    }
}
