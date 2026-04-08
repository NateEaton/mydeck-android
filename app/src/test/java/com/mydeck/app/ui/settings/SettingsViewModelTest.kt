package com.mydeck.app.ui.settings

import android.content.Context
import com.mydeck.app.domain.UserRepository
import com.mydeck.app.domain.model.AuthenticationDetails
import com.mydeck.app.io.prefs.SettingsDataStore
import io.mockk.coEvery
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var userRepository: UserRepository
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var context: Context
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        userRepository = mockk()
        settingsDataStore = mockk()
        context = mockk()
        coEvery { userRepository.observeAuthenticationDetails() } returns MutableStateFlow(
            AuthenticationDetails(
                url = "http://test",
                username = "testUser",
                token = "token"
            )
        )
        every { settingsDataStore.offlineReadingEnabledFlow } returns MutableStateFlow(false)
        every { settingsDataStore.themeFlow } returns MutableStateFlow(null)
        every { context.getString(any()) } returns ""
        viewModel = SettingsViewModel(userRepository, settingsDataStore, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init should load username from data store`() = runTest {
        val list = viewModel.uiState.take(2).toList()
        assertNull(list[0].username)
        assertEquals("testUser", list[1].username)
    }

    @Test
    fun `onClickAccount should emit NavigateToAccountSettings event`() = runTest {
        val events = mutableListOf<SettingsViewModel.NavigationEvent>()
        val job = launch { viewModel.navigationEvent.collect { events.add(it) } }

        viewModel.onClickAccount()
        testDispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(SettingsViewModel.NavigationEvent.NavigateToAccountSettings, events.first())
    }

    @Test
    fun `onClickOpenSourceLibraries should emit NavigateToOpenSourceLibraries event`() = runTest {
        val events = mutableListOf<SettingsViewModel.NavigationEvent>()
        val job = launch { viewModel.navigationEvent.collect { events.add(it) } }

        viewModel.onClickOpenSourceLibraries()
        testDispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(SettingsViewModel.NavigationEvent.NavigateToOpenSourceLibraries, events.first())
    }

    @Test
    fun `onClickBack should emit NavigateBack event`() = runTest {
        val events = mutableListOf<SettingsViewModel.NavigationEvent>()
        val job = launch { viewModel.navigationEvent.collect { events.add(it) } }

        viewModel.onClickBack()
        testDispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(SettingsViewModel.NavigationEvent.NavigateBack, events.first())
    }
}
