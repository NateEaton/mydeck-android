package com.mydeck.app.ui.settings

import com.mydeck.app.R
import com.mydeck.app.BuildConfig
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.OAuthCallbackRepository
import com.mydeck.app.domain.UserRepository
import com.mydeck.app.domain.model.OAuthDeviceAuthorizationState
import com.mydeck.app.domain.usecase.OAuthAuthorizationCodeUseCase
import com.mydeck.app.domain.usecase.OAuthDeviceAuthorizationUseCase
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.ui.migration.HttpUrlMigrationLoginCoordinator
import com.mydeck.app.worker.LoadBookmarksWorker
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.mydeck.app.util.openUrlInCustomTab
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.mockk
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.just
import io.mockk.Runs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var userRepository: UserRepository
    private lateinit var oauthDeviceAuthUseCase: OAuthDeviceAuthorizationUseCase
    private lateinit var oauthAuthCodeUseCase: OAuthAuthorizationCodeUseCase
    private lateinit var oauthCallbackRepository: OAuthCallbackRepository
    private lateinit var workManager: WorkManager
    private lateinit var context: Context
    private lateinit var applicationScope: CoroutineScope
    private lateinit var httpUrlMigrationLoginCoordinator: HttpUrlMigrationLoginCoordinator
    private lateinit var viewModel: AccountSettingsViewModel

    @Before
    fun setUp() {
        val testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        savedStateHandle = SavedStateHandle()
        settingsDataStore = mockk()
        bookmarkRepository = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        oauthDeviceAuthUseCase = mockk()
        oauthAuthCodeUseCase = mockk()
        oauthCallbackRepository = OAuthCallbackRepository()
        workManager = mockk()
        context = mockk()
        applicationScope = TestScope(testDispatcher)
        httpUrlMigrationLoginCoordinator = HttpUrlMigrationLoginCoordinator()
        mockkObject(LoadBookmarksWorker.Companion)
        
        every { settingsDataStore.urlFlow } returns MutableStateFlow("")
        every { settingsDataStore.tokenFlow } returns MutableStateFlow(null)
        every { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())
        every { LoadBookmarksWorker.enqueue(any(), any<Boolean>()) } returns UUID.randomUUID()
        every { context.getString(R.string.account_settings_initial_sync_failed) } returns
            "Initial sync failed. Please check your connection and try again."
        coEvery { settingsDataStore.clearCredentials() } returns Unit
        coEvery { settingsDataStore.setInitialSyncPerformed(any()) } returns Unit
        coEvery { settingsDataStore.saveLastBookmarkTimestamp(any()) } returns Unit
        coEvery { settingsDataStore.saveLastSyncTimestamp(any()) } returns Unit
        coEvery { userRepository.logout() } returns UserRepository.LogoutResult.Success
        
        viewModel = AccountSettingsViewModel(
            savedStateHandle = savedStateHandle,
            settingsDataStore = settingsDataStore,
            bookmarkRepository = bookmarkRepository,
            userRepository = userRepository,
            oauthDeviceAuthUseCase = oauthDeviceAuthUseCase,
            oauthAuthCodeUseCase = oauthAuthCodeUseCase,
            oauthCallbackRepository = oauthCallbackRepository,
            workManager = workManager,
            context = context,
            applicationScope = applicationScope,
            httpUrlMigrationLoginCoordinator = httpUrlMigrationLoginCoordinator
        )
    }

    @After
    fun tearDown() {
        unmockkObject(LoadBookmarksWorker.Companion)
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState should reflect data store values`() = runTest {
        every { settingsDataStore.urlFlow } returns MutableStateFlow("https://example.com")
        every { settingsDataStore.tokenFlow } returns MutableStateFlow("valid-token") // Non-empty token to set isLoggedIn=true
        viewModel = AccountSettingsViewModel(
            savedStateHandle = savedStateHandle,
            settingsDataStore = settingsDataStore,
            bookmarkRepository = bookmarkRepository,
            userRepository = userRepository,
            oauthDeviceAuthUseCase = oauthDeviceAuthUseCase,
            oauthAuthCodeUseCase = oauthAuthCodeUseCase,
            oauthCallbackRepository = oauthCallbackRepository,
            workManager = workManager,
            context = context,
            applicationScope = applicationScope,
            httpUrlMigrationLoginCoordinator = httpUrlMigrationLoginCoordinator
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
            savedStateHandle = savedStateHandle,
            settingsDataStore = settingsDataStore,
            bookmarkRepository = bookmarkRepository,
            userRepository = userRepository,
            oauthDeviceAuthUseCase = oauthDeviceAuthUseCase,
            oauthAuthCodeUseCase = oauthAuthCodeUseCase,
            oauthCallbackRepository = oauthCallbackRepository,
            workManager = workManager,
            context = context,
            applicationScope = applicationScope,
            httpUrlMigrationLoginCoordinator = httpUrlMigrationLoginCoordinator
        )

        advanceUntilIdle()

        assertEquals("https://example.com", viewModel.uiState.value.url)
    }

    @Test
    fun `pending HTTP migration login prepopulates URL and starts OAuth`() = runTest {
        httpUrlMigrationLoginCoordinator.requestLogin("https://example.com/api")
        coEvery { userRepository.initiateLogin("https://example.com/api") } returns
            UserRepository.LoginResult.DeviceAuthorizationRequired(
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
        coEvery {
            oauthDeviceAuthUseCase.pollForToken(
                clientId = any(),
                deviceCode = any(),
                currentInterval = any()
            )
        } returns OAuthDeviceAuthorizationUseCase.TokenPollResult.StillPending

        viewModel = AccountSettingsViewModel(
            savedStateHandle = savedStateHandle,
            settingsDataStore = settingsDataStore,
            bookmarkRepository = bookmarkRepository,
            userRepository = userRepository,
            oauthDeviceAuthUseCase = oauthDeviceAuthUseCase,
            oauthAuthCodeUseCase = oauthAuthCodeUseCase,
            oauthCallbackRepository = oauthCallbackRepository,
            workManager = workManager,
            context = context,
            applicationScope = applicationScope,
            httpUrlMigrationLoginCoordinator = httpUrlMigrationLoginCoordinator
        )

        runCurrent()

        val uiState = viewModel.uiState.value
        assertEquals("https://example.com", uiState.url)
        assertEquals(AccountSettingsViewModel.AuthStatus.WaitingForAuthorization, uiState.authStatus)
        assertNotNull(uiState.deviceAuthState)
        coVerify { userRepository.initiateLogin("https://example.com/api") }

        viewModel.cancelAuthorization()
        runCurrent()
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
    fun `login should trigger auth-code browser flow`() = runTest {
        mockkStatic("com.mydeck.app.util.UtilsKt")
        every { openUrlInCustomTab(any(), any()) } just Runs

        coEvery {
            oauthAuthCodeUseCase.initiateAuthorization("https://example.com/api")
        } returns OAuthAuthorizationCodeUseCase.AuthCodeInitiateResult.Ready(
            OAuthAuthorizationCodeUseCase.InitiateResult(
                authorizeUrl = "https://example.com/authorize?test=1",
                codeVerifier = "test-verifier",
                state = "test-state",
                clientId = "test-client"
            )
        )

        viewModel.updateUrl("https://example.com")
        viewModel.login()
        runCurrent()

        assertEquals(AccountSettingsViewModel.AuthStatus.BrowserLaunched, viewModel.uiState.value.authStatus)

        viewModel.cancelAuthorization()
        runCurrent()
        unmockkStatic("com.mydeck.app.util.UtilsKt")
    }

    @Test
    fun `successful login waits for initial sync before navigation`() = runTest {
        val workId = UUID.randomUUID()
        val succeededWorkInfo = mockk<WorkInfo> {
            every { id } returns workId
            every { state } returns WorkInfo.State.SUCCEEDED
        }

        coEvery { userRepository.initiateLogin("https://example.com/api") } returns
            UserRepository.LoginResult.DeviceAuthorizationRequired(
                OAuthDeviceAuthorizationState(
                    clientId = "test-client",
                    deviceCode = "TEST-CODE",
                    userCode = "TEST-USER-CODE",
                    verificationUri = "https://example.com/auth",
                    verificationUriComplete = "https://example.com/auth?code=TEST-USER-CODE",
                    expiresAt = System.currentTimeMillis() + 1800_000,
                    pollingInterval = 0
                )
            )
        coEvery { userRepository.completeLogin("https://example.com/api", "token-123") } returns
            UserRepository.LoginResult.Success
        coEvery {
            oauthDeviceAuthUseCase.pollForToken(
                clientId = any(),
                deviceCode = any(),
                currentInterval = any()
            )
        } returns OAuthDeviceAuthorizationUseCase.TokenPollResult.Success("token-123")
        every { LoadBookmarksWorker.enqueue(any(), true) } returns workId
        every { workManager.getWorkInfosForUniqueWorkFlow(LoadBookmarksWorker.UNIQUE_WORK_NAME) } returns
            flowOf(listOf(succeededWorkInfo))

        val navEvents = mutableListOf<AccountSettingsViewModel.NavigationEvent>()
        val navJob = launch { viewModel.navigationEvent.collect { navEvents.add(it) } }

        viewModel.updateUrl("https://example.com")
        viewModel.switchToDeviceCodeFlow()
        advanceUntilIdle()
        navJob.cancel()

        assertEquals(AccountSettingsViewModel.AuthStatus.Success, viewModel.uiState.value.authStatus)
        assertEquals(AccountSettingsViewModel.NavigationEvent.NavigateToBookmarkList, navEvents.firstOrNull())
        coVerify { settingsDataStore.setInitialSyncPerformed(false) }
        coVerify { bookmarkRepository.deleteAllBookmarks() }
        verify { LoadBookmarksWorker.enqueue(any(), true) }
    }

    @Test
    fun `failed initial sync still completes login and navigates`() = runTest {
        val workId = UUID.randomUUID()
        val failedWorkInfo = mockk<WorkInfo> {
            every { id } returns workId
            every { state } returns WorkInfo.State.FAILED
        }

        coEvery { userRepository.initiateLogin("https://example.com/api") } returns
            UserRepository.LoginResult.DeviceAuthorizationRequired(
                OAuthDeviceAuthorizationState(
                    clientId = "test-client",
                    deviceCode = "TEST-CODE",
                    userCode = "TEST-USER-CODE",
                    verificationUri = "https://example.com/auth",
                    verificationUriComplete = "https://example.com/auth?code=TEST-USER-CODE",
                    expiresAt = System.currentTimeMillis() + 1800_000,
                    pollingInterval = 0
                )
            )
        coEvery { userRepository.completeLogin("https://example.com/api", "token-123") } returns
            UserRepository.LoginResult.Success
        coEvery {
            oauthDeviceAuthUseCase.pollForToken(
                clientId = any(),
                deviceCode = any(),
                currentInterval = any()
            )
        } returns OAuthDeviceAuthorizationUseCase.TokenPollResult.Success("token-123")
        every { LoadBookmarksWorker.enqueue(any(), true) } returns workId
        every { workManager.getWorkInfosForUniqueWorkFlow(LoadBookmarksWorker.UNIQUE_WORK_NAME) } returns
            flowOf(listOf(failedWorkInfo))

        val navEvents = mutableListOf<AccountSettingsViewModel.NavigationEvent>()
        val navJob = launch { viewModel.navigationEvent.collect { navEvents.add(it) } }

        viewModel.updateUrl("https://example.com")
        viewModel.switchToDeviceCodeFlow()
        advanceUntilIdle()
        navJob.cancel()

        assertEquals(AccountSettingsViewModel.AuthStatus.Success, viewModel.uiState.value.authStatus)
        assertEquals(AccountSettingsViewModel.NavigationEvent.NavigateToBookmarkList, navEvents.firstOrNull())
        coVerify { settingsDataStore.setInitialSyncPerformed(false) }
    }

    @Test
    fun `initial sync timeout cancels stalled work and still navigates`() = runTest {
        val workId = UUID.randomUUID()
        val enqueuedWorkInfo = mockk<WorkInfo> {
            every { id } returns workId
            every { state } returns WorkInfo.State.ENQUEUED
        }
        val workFlow = MutableStateFlow(listOf(enqueuedWorkInfo))

        coEvery { userRepository.initiateLogin("https://example.com/api") } returns
            UserRepository.LoginResult.DeviceAuthorizationRequired(
                OAuthDeviceAuthorizationState(
                    clientId = "test-client",
                    deviceCode = "TEST-CODE",
                    userCode = "TEST-USER-CODE",
                    verificationUri = "https://example.com/auth",
                    verificationUriComplete = "https://example.com/auth?code=TEST-USER-CODE",
                    expiresAt = System.currentTimeMillis() + 1800_000,
                    pollingInterval = 0
                )
            )
        coEvery { userRepository.completeLogin("https://example.com/api", "token-123") } returns
            UserRepository.LoginResult.Success
        coEvery {
            oauthDeviceAuthUseCase.pollForToken(
                clientId = any(),
                deviceCode = any(),
                currentInterval = any()
            )
        } returns OAuthDeviceAuthorizationUseCase.TokenPollResult.Success("token-123")
        every { LoadBookmarksWorker.enqueue(any(), true) } returns workId
        every { workManager.getWorkInfosForUniqueWorkFlow(LoadBookmarksWorker.UNIQUE_WORK_NAME) } returns
            workFlow
        every { workManager.cancelWorkById(workId) } returns mockk(relaxed = true)

        val navEvents = mutableListOf<AccountSettingsViewModel.NavigationEvent>()
        val navJob = launch { viewModel.navigationEvent.collect { navEvents.add(it) } }

        viewModel.updateUrl("https://example.com")
        viewModel.switchToDeviceCodeFlow()
        runCurrent()
        advanceTimeBy(20_000)
        advanceUntilIdle()
        navJob.cancel()

        verify { workManager.cancelWorkById(workId) }
        assertEquals(AccountSettingsViewModel.AuthStatus.Success, viewModel.uiState.value.authStatus)
        assertEquals(AccountSettingsViewModel.NavigationEvent.NavigateToBookmarkList, navEvents.firstOrNull())
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
    fun `updateUrl with http URL should follow build policy`() = runTest {
        viewModel.updateUrl("http://validurl.com")
        advanceUntilIdle()
        val uiState = viewModel.uiState.first()
        if (BuildConfig.ALLOW_INSECURE_HTTP) {
            assertNull(uiState.urlError)
        } else {
            assertEquals(R.string.account_settings_url_error, uiState.urlError)
        }
    }

    @Test
    fun `loginEnabled should follow build policy when url is http`() = runTest {
        viewModel.updateUrl("http://validurl.com")
        advanceUntilIdle()
        assertEquals(BuildConfig.ALLOW_INSECURE_HTTP, viewModel.uiState.first().loginEnabled)
    }
}
