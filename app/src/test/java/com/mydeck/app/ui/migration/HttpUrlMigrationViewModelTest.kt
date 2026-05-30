package com.mydeck.app.ui.migration

import com.mydeck.app.domain.UserRepository
import com.mydeck.app.io.prefs.SettingsDataStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HttpUrlMigrationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var userRepository: UserRepository
    private lateinit var calls: MutableList<String>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        calls = mutableListOf()
        settingsDataStore = mockk()
        userRepository = mockk()

        every { settingsDataStore.urlFlow } returns MutableStateFlow("http://192.168.1.10/api")
        coEvery { settingsDataStore.clearCredentials() } coAnswers {
            calls += "clearCredentials"
        }
        every { settingsDataStore.saveUrl(any()) } answers {
            calls += "saveUrl:${firstArg<String>()}"
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saveReplacementUrl clears credentials before saving HTTPS URL`() = runTest {
        val viewModel = HttpUrlMigrationViewModel(
            settingsDataStore = settingsDataStore,
            userRepository = userRepository
        )

        viewModel.updateReplacementUrl("https://readeck.example")
        viewModel.saveReplacementUrl()
        advanceUntilIdle()

        assertEquals(
            listOf(
                "clearCredentials",
                "saveUrl:https://readeck.example/api"
            ),
            calls
        )
    }
}
