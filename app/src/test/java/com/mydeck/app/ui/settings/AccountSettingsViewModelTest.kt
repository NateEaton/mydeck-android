package com.mydeck.app.ui.settings

import com.mydeck.app.R
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.UserRepository
import com.mydeck.app.domain.model.OAuthDeviceAuthorizationState
import com.mydeck.app.domain.usecase.OAuthDeviceAuthorizationUseCase
import com.mydeck.app.io.prefs.SettingsDataStore
import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var userRepository: UserRepository
    private lateinit var oauthDeviceAuthUseCase: OAuthDeviceAuthorizationUseCase
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var context: Context
    private lateinit var applicationScope: CoroutineScope
    private lateinit var viewModel: AccountSettingsViewModel

    @Before
    fun setUp() {
        val testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        settingsDataStore = mockk()
        userRepository = mockk(relaxed = true)
        oauthDeviceAuthUseCase = mockk()
        bookmarkRepository = mockk()
        context = mockk()
        applicationScope = TestScope(testDispatcher)
        
        every { settingsDataStore.urlFlow } returns MutableStateFlow("")
        every { settingsDataStore.tokenFlow } returns MutableStateFlow(null)
        coEvery { settingsDataStore.clearCredentials() } returns Unit
        coEvery { userRepository.logout() } returns UserRepository.LogoutResult.Success
        
        viewModel = AccountSettingsViewModel(
            settingsDataStore = settingsDataStore,
            userRepository = userRepository,
            oauthDeviceAuthUseCase = oauthDeviceAuthUseCase,
            bookmarkRepository = bookmarkRepository,
            context = context,
            applicationScope = applicationScope
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState should reflect data store values`() = runTest {
        every { settingsDataStore.urlFlow } returns MutableStateFlow("https://example.com")
        every { settingsDataStore.tokenFlow } returns MutableStateFlow("valid-token") // Non-empty token to set isLoggedIn=true
        viewModel = AccountSettingsViewModel(
            settingsDataStore = settingsDataStore,
            userRepository = userRepository,
            oauthDeviceAuthUseCase = oauthDeviceAuthUseCase,
            bookmarkRepository = bookmarkRepository,
            context = context,
            applicationScope = applicationScope
        )
        
        advanceUntilIdle() // Wait for init block to complete
        
        val uiState = viewModel.uiState.value
        assertEquals("https://example.com", uiState.url)
        assertEquals(AccountSettingsViewModel.AuthStatus.Idle, uiState.authStatus)
        assertTrue(uiState.isLoggedIn) // Should be true since token is not empty
    }


    @Test
    fun `initial uiState should strip api suffix from saved url for display`() = runTest {
        every { settingsDataStore.urlFlow } returns MutableStateFlow("https://example.com/api")
        every { settingsDataStore.tokenFlow } returns MutableStateFlow("valid-token")
        viewModel = AccountSettingsViewModel(
            settingsDataStore = settingsDataStore,
            userRepository = userRepository,
            oauthDeviceAuthUseCase = oauthDeviceAuthUseCase,
            bookmarkRepository = bookmarkRepository,
            context = context,
            applicationScope = applicationScope
        )

        advanceUntilIdle()

        assertEquals("https://example.com", viewModel.uiState.value.url)
    }

    private fun assertInitialUiState(settingsUiState: AccountSettingsViewModel.AccountSettingsUiState) {
        assertEquals("", settingsUiState.url)
        assertNull(settingsUiState.urlError)
        assertEquals(AccountSettingsViewModel.AuthStatus.Idle, settingsUiState.authStatus)
        assertFalse(settingsUiState.isLoggedIn)
    }

    @Test
    fun `updateUrl should update url in uiState`() = runTest {
        viewModel.updateUrl("https://newurl.com")
        val uiState = viewModel.uiState.first()
        assertEquals("https://newurl.com", uiState.url)
    }

    @Test
    fun `updateUrl with valid URL should not set urlError`() = runTest {
        viewModel.updateUrl("https://validurl.com")
        val uiState = viewModel.uiState.first()
        assertNull(uiState.urlError)
    }

    @Test
    fun `updateUrl with invalid URL should set urlError`() = runTest {
        viewModel.updateUrl("invalid-url")
        val uiState = viewModel.uiState.first()
        assertEquals(R.string.account_settings_url_error, uiState.urlError)
    }

    @Test
    fun `login should trigger OAuth flow`() = runTest {
        // Mock successful OAuth initiation
        coEvery { userRepository.initiateLogin("https://example.com/api") } returns UserRepository.LoginResult.DeviceAuthorizationRequired(
            OAuthDeviceAuthorizationState(
                clientId = "test-client",
                deviceCode = "TEST-CODE",
                userCode = "TEST-USER-CODE",
                verificationUri = "https://example.com/auth",
                verificationUriComplete = "https://example.com/auth?code=TEST-USER-CODE",
                expiresAt = System.currentTimeMillis() + 1800_000,
                pollingInterval = 5
            )
        )

        // Polling runs in a background coroutine; provide a stub so it can't throw MockKException
        // if the test scheduler advances time.
        coEvery {
            oauthDeviceAuthUseCase.pollForToken(
                clientId = any(),
                deviceCode = any(),
                currentInterval = any()
            )
        } returns OAuthDeviceAuthorizationUseCase.TokenPollResult.StillPending
        
        viewModel.updateUrl("https://example.com")
        viewModel.login()
        // Only run immediate tasks; don't advance time into the polling loop delay.
        runCurrent()
        
        val uiState = viewModel.uiState.first()
        assertEquals(AccountSettingsViewModel.AuthStatus.WaitingForAuthorization, uiState.authStatus)
        assertNotNull(uiState.deviceAuthState)

        // Stop the polling job so the test can complete deterministically.
        viewModel.cancelAuthorization()
        runCurrent()
    }

    @Test
    fun `signOut should call logout and update uiState`() = runTest {
        viewModel.signOut()
        advanceUntilIdle()
        val uiState = viewModel.uiState.first()
        assertEquals(AccountSettingsViewModel.AuthStatus.Idle, uiState.authStatus)
        assertFalse(uiState.isLoggedIn)
    }

    @Test
    fun `loginEnabled should be false when url is invalid`() = runTest {
        viewModel.updateUrl("invalid-url")
        advanceUntilIdle()
        assertFalse(viewModel.uiState.first().loginEnabled)
    }

    @Test
    fun `loginEnabled should be true when url is valid`() = runTest {
        viewModel.updateUrl("https://validurl.com")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.first().loginEnabled)
    }

    @Test
    fun `updateUrl with http URL should not set urlError`() = runTest {
        viewModel.updateUrl("http://validurl.com")
        advanceUntilIdle()
        val uiState = viewModel.uiState.first()
        assertNull(uiState.urlError)
    }

    @Test
    fun `loginEnabled should be true when url is http`() = runTest {
        viewModel.updateUrl("http://validurl.com")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.first().loginEnabled)
    }
}
