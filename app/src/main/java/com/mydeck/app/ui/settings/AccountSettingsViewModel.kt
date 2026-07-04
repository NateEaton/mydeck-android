package com.mydeck.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.mydeck.app.BuildConfig
import com.mydeck.app.R
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.OAuthCallbackRepository
import com.mydeck.app.domain.UserRepository
import com.mydeck.app.domain.model.OAuthDeviceAuthorizationState
import com.mydeck.app.domain.usecase.OAuthAuthorizationCodeUseCase
import com.mydeck.app.domain.usecase.OAuthDeviceAuthorizationUseCase
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.ui.migration.HttpUrlMigrationLoginCoordinator
import com.mydeck.app.util.openUrlInCustomTab
import com.mydeck.app.worker.LoadBookmarksWorker
import com.mydeck.app.util.isValidUrl
import com.mydeck.app.coroutine.ApplicationScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Instant
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import java.util.UUID

@HiltViewModel
class AccountSettingsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val settingsDataStore: SettingsDataStore,
    private val bookmarkRepository: BookmarkRepository,
    private val userRepository: UserRepository,
    private val oauthDeviceAuthUseCase: OAuthDeviceAuthorizationUseCase,
    private val oauthAuthCodeUseCase: OAuthAuthorizationCodeUseCase,
    private val oauthCallbackRepository: OAuthCallbackRepository,
    private val workManager: WorkManager,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val httpUrlMigrationLoginCoordinator: HttpUrlMigrationLoginCoordinator
) : ViewModel() {

    data class AccountSettingsUiState(
        val url: String = "https://",
        val urlError: Int? = null,
        val urlWarning: Int? = null,
        val loginEnabled: Boolean = true,
        val isLoggedIn: Boolean = false,
        val authStatus: AuthStatus = AuthStatus.Idle,
        val deviceAuthState: OAuthDeviceAuthorizationState? = null
    )

    sealed class AuthStatus {
        data object Idle : AuthStatus()
        data object Loading : AuthStatus()
        data object WaitingForAuthorization : AuthStatus()
        data object BrowserLaunched : AuthStatus()
        data object Exchanging : AuthStatus()
        data object Success : AuthStatus()
        data class Error(val message: String) : AuthStatus()
    }

    private val _uiState = MutableStateFlow(AccountSettingsUiState())
    val uiState: StateFlow<AccountSettingsUiState> = _uiState.asStateFlow()

    private val _navigationEvent = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationEvent> = _navigationEvent.receiveAsFlow()

    private val _browserLaunchEvent = Channel<String>(Channel.BUFFERED)
    val browserLaunchEvent: Flow<String> = _browserLaunchEvent.receiveAsFlow()

    private var pollingJob: Job? = null
    private var authCodeJob: Job? = null

    sealed class NavigationEvent {
        data object NavigateToBookmarkList : NavigationEvent()
        data object NavigateBack : NavigationEvent()
    }

    init {
        viewModelScope.launch {
            val pendingMigrationLoginUrl = httpUrlMigrationLoginCoordinator.consumePendingLoginUrl()
            val token = settingsDataStore.tokenFlow.value
            val url = pendingMigrationLoginUrl ?: settingsDataStore.urlFlow.value
            _uiState.update {
                it.copy(
                    isLoggedIn = !token.isNullOrBlank(),
                    url = toDisplayUrl(url)
                )
            }
            if (pendingMigrationLoginUrl != null) {
                startDeviceCodeLogin(pendingMigrationLoginUrl)
            } else {
                // Restore BrowserLaunched state after process death during Custom Tab
                val pendingVerifier = savedStateHandle.get<String>(KEY_CODE_VERIFIER)
                val pendingState = savedStateHandle.get<String>(KEY_AUTH_STATE)
                val pendingServerUrl = savedStateHandle.get<String>(KEY_SERVER_URL)
                if (pendingVerifier != null && pendingState != null && pendingServerUrl != null) {
                    Timber.d("Restoring BrowserLaunched state after process death")
                    _uiState.update { it.copy(authStatus = AuthStatus.BrowserLaunched) }
                    awaitOAuthCallback(pendingServerUrl, pendingVerifier, pendingState)
                }
            }
        }
    }

    fun updateUrl(url: String) {
        validateUrl(url)
    }

    /** Primary sign-in action — launches browser-based OAuth Authorization Code + PKCE flow. */
    fun login() {
        val url = normalizeApiUrl(_uiState.value.url)
        Timber.d("login() called with normalized URL: $url")
        pollingJob?.cancel()
        authCodeJob?.cancel()
        authCodeJob = viewModelScope.launch {
            startAuthCodeLogin(url)
        }
    }

    /** Switches to the Device Code flow, cancelling any in-progress auth-code flow. */
    fun switchToDeviceCodeFlow() {
        authCodeJob?.cancel()
        clearAuthCodePendingState()
        oauthCallbackRepository.consume()
        if (!isValidUrlForCurrentSettings(_uiState.value.url)) {
            _uiState.update { it.copy(authStatus = AuthStatus.Idle) }
            return
        }
        val url = normalizeApiUrl(_uiState.value.url)
        viewModelScope.launch {
            startDeviceCodeLogin(url)
        }
    }

    private suspend fun startAuthCodeLogin(url: String) {
        _uiState.update { it.copy(authStatus = AuthStatus.Loading) }
        // Drop any stale callback from a prior attempt before we subscribe for this one.
        oauthCallbackRepository.consume()
        settingsDataStore.saveUrl(url)

        when (val result = oauthAuthCodeUseCase.initiateAuthorization(url)) {
            is OAuthAuthorizationCodeUseCase.AuthCodeInitiateResult.Ready -> {
                val initResult = result.result
                // Persist PKCE state for process-death survival
                savedStateHandle[KEY_CODE_VERIFIER] = initResult.codeVerifier
                savedStateHandle[KEY_AUTH_STATE] = initResult.state
                savedStateHandle[KEY_SERVER_URL] = url

                _uiState.update { it.copy(authStatus = AuthStatus.BrowserLaunched) }
                _browserLaunchEvent.trySend(initResult.authorizeUrl)
                awaitOAuthCallback(url, initResult.codeVerifier, initResult.state)
            }

            OAuthAuthorizationCodeUseCase.AuthCodeInitiateResult.HttpBlockedByBuildPolicy -> {
                _uiState.update { it.copy(authStatus = AuthStatus.Error(context.getString(R.string.account_settings_http_blocked_error))) }
            }

            is OAuthAuthorizationCodeUseCase.AuthCodeInitiateResult.Error -> {
                Timber.e("Auth code initiation error: ${result.message}")
                _uiState.update { it.copy(authStatus = AuthStatus.Error(result.message)) }
            }
        }
    }

    private suspend fun awaitOAuthCallback(serverUrl: String, codeVerifier: String, expectedState: String) {
        // Suspends until MainActivity dispatches a redirect. `events` has replay=1 so a callback
        // delivered before this subscription (e.g. after process death) is still received here.
        val event = oauthCallbackRepository.events.first()
        // Clear the replay cache immediately so this callback can't be re-delivered to a later flow.
        oauthCallbackRepository.consume()

        when (event) {
            is OAuthCallbackRepository.OAuthCallbackEvent.Success -> {
                if (event.state != expectedState) {
                    Timber.e("OAuth state mismatch — possible CSRF. Expected $expectedState, got ${event.state}")
                    clearAuthCodePendingState()
                    _uiState.update {
                        it.copy(authStatus = AuthStatus.Error(context.getString(R.string.oauth_auth_code_state_mismatch_error)))
                    }
                    return
                }
                _uiState.update { it.copy(authStatus = AuthStatus.Exchanging) }
                when (val exchangeResult = oauthAuthCodeUseCase.exchangeCode(event.code, codeVerifier)) {
                    is OAuthAuthorizationCodeUseCase.TokenExchangeResult.Success -> {
                        clearAuthCodePendingState()
                        when (val loginResult = userRepository.completeLogin(serverUrl, exchangeResult.accessToken)) {
                            is UserRepository.LoginResult.Success -> onLoginSuccess()
                            is UserRepository.LoginResult.Error ->
                                _uiState.update { it.copy(authStatus = AuthStatus.Error(loginResult.errorMessage)) }
                            UserRepository.LoginResult.HttpBlockedByBuildPolicy ->
                                _uiState.update { it.copy(authStatus = AuthStatus.Error(context.getString(R.string.account_settings_http_blocked_error))) }
                            else ->
                                _uiState.update { it.copy(authStatus = AuthStatus.Error(context.getString(R.string.oauth_auth_code_exchange_failed))) }
                        }
                    }
                    is OAuthAuthorizationCodeUseCase.TokenExchangeResult.UserDenied -> {
                        clearAuthCodePendingState()
                        _uiState.update { it.copy(authStatus = AuthStatus.Error(exchangeResult.message)) }
                    }
                    OAuthAuthorizationCodeUseCase.TokenExchangeResult.HttpBlockedByBuildPolicy -> {
                        clearAuthCodePendingState()
                        _uiState.update { it.copy(authStatus = AuthStatus.Error(context.getString(R.string.account_settings_http_blocked_error))) }
                    }
                    is OAuthAuthorizationCodeUseCase.TokenExchangeResult.Error -> {
                        clearAuthCodePendingState()
                        _uiState.update { it.copy(authStatus = AuthStatus.Error(exchangeResult.message)) }
                    }
                }
            }
            is OAuthCallbackRepository.OAuthCallbackEvent.Error -> {
                Timber.w("OAuth callback error: ${event.error} — ${event.errorDescription}")
                clearAuthCodePendingState()
                val message = when (event.error) {
                    "access_denied" -> context.getString(R.string.oauth_auth_code_access_denied)
                    else -> event.errorDescription ?: context.getString(R.string.oauth_auth_code_exchange_failed)
                }
                _uiState.update { it.copy(authStatus = AuthStatus.Error(message)) }
            }
        }
    }

    private fun clearAuthCodePendingState() {
        savedStateHandle.remove<String>(KEY_CODE_VERIFIER)
        savedStateHandle.remove<String>(KEY_AUTH_STATE)
        savedStateHandle.remove<String>(KEY_SERVER_URL)
    }

    private suspend fun startDeviceCodeLogin(url: String) {
        _uiState.update { it.copy(authStatus = AuthStatus.Loading) }

        val result = userRepository.initiateLogin(url)
        Timber.d("initiateLogin result: $result")
        when (result) {
            is UserRepository.LoginResult.DeviceAuthorizationRequired -> {
                val state = result.state
                _uiState.update {
                    it.copy(
                        authStatus = AuthStatus.WaitingForAuthorization,
                        deviceAuthState = state
                    )
                }
                startPolling(url, state)
            }

            is UserRepository.LoginResult.Success -> {
                onLoginSuccess()
            }

            is UserRepository.LoginResult.Error -> {
                Timber.e("Login error (code=${result.code}): ${result.errorMessage}")
                _uiState.update { it.copy(authStatus = AuthStatus.Error(result.errorMessage)) }
            }

            is UserRepository.LoginResult.NetworkError -> {
                Timber.e("Login network error: ${result.errorMessage}")
                _uiState.update { it.copy(authStatus = AuthStatus.Error(result.errorMessage)) }
            }

            UserRepository.LoginResult.HttpBlockedByBuildPolicy -> {
                Timber.w("Login blocked because HTTP is disabled in this build")
                _uiState.update { it.copy(authStatus = AuthStatus.Error(context.getString(R.string.account_settings_http_blocked_error))) }
            }
        }
    }

    private fun startPolling(url: String, state: OAuthDeviceAuthorizationState) {
        pollingJob?.cancel()

        var currentInterval = state.pollingInterval

        pollingJob = applicationScope.launch {
            Timber.d("Starting OAuth token polling (clientId=${state.clientId})")

            while (true) {
                if (System.currentTimeMillis() >= state.expiresAt) {
                    Timber.w("Device authorization expired")
                    _uiState.update {
                        it.copy(
                            authStatus = AuthStatus.Error("Authorization code expired. Please try again."),
                            deviceAuthState = null
                        )
                    }
                    break
                }

                delay(currentInterval.seconds)

                when (val result = oauthDeviceAuthUseCase.pollForToken(
                    clientId = state.clientId,
                    deviceCode = state.deviceCode,
                    currentInterval = currentInterval
                )) {
                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.Success -> {
                        Timber.i("Token received, completing login")

                        when (val loginResult = userRepository.completeLogin(url, result.accessToken)) {
                            is UserRepository.LoginResult.Success -> onLoginSuccess()
                            is UserRepository.LoginResult.Error -> {
                                _uiState.update { it.copy(authStatus = AuthStatus.Error(loginResult.errorMessage)) }
                            }
                            UserRepository.LoginResult.HttpBlockedByBuildPolicy -> {
                                _uiState.update { it.copy(authStatus = AuthStatus.Error(context.getString(R.string.account_settings_http_blocked_error))) }
                            }
                            else -> {
                                _uiState.update { it.copy(authStatus = AuthStatus.Error("Unexpected error")) }
                            }
                        }
                        break
                    }

                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.StillPending -> {
                        Timber.d("Authorization still pending")
                    }

                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.SlowDown -> {
                        currentInterval = result.newInterval
                        Timber.w("Server requested slow_down, new interval: ${currentInterval}s")
                    }

                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.UserDenied -> {
                        _uiState.update {
                            it.copy(authStatus = AuthStatus.Error(result.message), deviceAuthState = null)
                        }
                        break
                    }

                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.Expired -> {
                        _uiState.update {
                            it.copy(authStatus = AuthStatus.Error(result.message), deviceAuthState = null)
                        }
                        break
                    }

                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.NetworkError -> {
                        Timber.w("Network error during polling, will retry: ${result.message}")
                    }

                    OAuthDeviceAuthorizationUseCase.TokenPollResult.HttpBlockedByBuildPolicy -> {
                        _uiState.update {
                            it.copy(authStatus = AuthStatus.Error(context.getString(R.string.account_settings_http_blocked_error)), deviceAuthState = null)
                        }
                        break
                    }

                    is OAuthDeviceAuthorizationUseCase.TokenPollResult.Error -> {
                        _uiState.update {
                            it.copy(authStatus = AuthStatus.Error(result.message), deviceAuthState = null)
                        }
                        break
                    }
                }
            }
        }
    }

    /**
     * Post-login tasks: clear local bookmark state, trigger the first bookmark load,
     * and wait briefly for WorkManager to start the job so login cannot hang forever
     * on constrained ENQUEUED/BLOCKED states.
     */
    private suspend fun onLoginSuccess() {
        // Run the post-login initial sync on the application scope so navigation or this
        // ViewModel being cleared — e.g. while the process-death OAuth restore settles — cannot
        // cancel it mid-flight and leave the bookmark list unpopulated. Mirrors the device-code
        // polling job, which runs on applicationScope for the same reason.
        val syncJob = applicationScope.launch {
            try {
                prepareForInitialSync()
                val initialSyncWorkId = LoadBookmarksWorker.enqueue(context, isInitialLoad = true)
                if (waitForInitialSyncToStart(initialSyncWorkId) == InitialSyncStartResult.TIMED_OUT) {
                    Timber.w("Initial sync did not start within timeout; cancelling work.")
                    workManager.cancelWorkById(initialSyncWorkId)
                }
            } catch (e: Exception) {
                // A failed initial sync must not block login — the list recovers on next refresh.
                Timber.e(e, "Initial sync after login failed to start")
            }
        }
        syncJob.join()

        _uiState.update {
            it.copy(
                authStatus = AuthStatus.Success,
                isLoggedIn = true,
                deviceAuthState = null
            )
        }
        _navigationEvent.trySend(NavigationEvent.NavigateToBookmarkList)
    }

    private suspend fun prepareForInitialSync() {
        settingsDataStore.setInitialSyncPerformed(false)
        bookmarkRepository.deleteAllBookmarks()
        settingsDataStore.saveLastBookmarkTimestamp(Instant.fromEpochMilliseconds(0))
        settingsDataStore.saveLastSyncTimestamp(Instant.fromEpochMilliseconds(0))
    }

    private suspend fun waitForInitialSyncToStart(workId: UUID): InitialSyncStartResult {
        val startState = withTimeoutOrNull(INITIAL_SYNC_START_TIMEOUT) {
            workManager.getWorkInfosForUniqueWorkFlow(LoadBookmarksWorker.UNIQUE_WORK_NAME)
                .map { workInfos -> workInfos.firstOrNull { it.id == workId }?.state }
                .first { state ->
                    state == WorkInfo.State.RUNNING ||
                        state == WorkInfo.State.SUCCEEDED ||
                        state == WorkInfo.State.FAILED ||
                        state == WorkInfo.State.CANCELLED
                }
        } ?: return InitialSyncStartResult.TIMED_OUT

        return when (startState) {
            WorkInfo.State.RUNNING -> InitialSyncStartResult.STARTED
            WorkInfo.State.SUCCEEDED -> InitialSyncStartResult.SUCCEEDED
            WorkInfo.State.FAILED -> InitialSyncStartResult.FAILED
            WorkInfo.State.CANCELLED -> InitialSyncStartResult.CANCELLED
            else -> InitialSyncStartResult.TIMED_OUT
        }
    }

    /** Cancels any in-progress auth flow (device-code polling or auth-code await) and resets state. */
    fun cancelAuthorization() {
        pollingJob?.cancel()
        authCodeJob?.cancel()
        clearAuthCodePendingState()
        oauthCallbackRepository.consume()
        _uiState.update {
            it.copy(authStatus = AuthStatus.Idle, deviceAuthState = null)
        }
    }

    fun signOut() {
        Timber.d("signOut() called")
        viewModelScope.launch {
            val logoutResult = userRepository.logout()
            Timber.d("logout result: $logoutResult")
            _uiState.update {
                AccountSettingsUiState(url = it.url)
            }
        }
    }


    private fun validateUrl(value: String) {
        val isUrlValid = isValidUrlForCurrentSettings(value)
        val urlError = if (!isUrlValid && value.isNotEmpty()) {
            com.mydeck.app.R.string.account_settings_url_error
        } else {
            null
        }
        val urlWarning = if (isUrlValid && BuildConfig.ALLOW_INSECURE_HTTP && value.trim().lowercase().startsWith("http://")) {
            com.mydeck.app.R.string.account_settings_url_http_warning
        } else {
            null
        }
        _uiState.update {
            it.copy(
                url = value,
                urlError = urlError,
                urlWarning = urlWarning,
                loginEnabled = isUrlValid
            )
        }
    }


    override fun onCleared() {
        super.onCleared()
        // Do NOT cancel pollingJob here — it runs on applicationScope and must
        // survive ViewModel destruction (e.g. when user backgrounds the app to
        // open the browser for OAuth authorization). Only cancelAuthorization()
        // (explicit user action) should cancel it.
    }

    /**
     * Normalize user-entered URL to always end with exactly /api (no trailing slash, no double /api).
     */
    private fun normalizeApiUrl(rawUrl: String): String {
        var url = rawUrl.trimEnd('/')
        if (!url.endsWith("/api")) {
            url = "$url/api"
        }
        return url
    }

    private fun toDisplayUrl(rawUrl: String?): String {
        if (rawUrl.isNullOrBlank()) {
            return "https://"
        }
        return rawUrl
            .trimEnd('/')
            .removeSuffix("/api")
    }

    private fun isValidUrlForCurrentSettings(url: String?): Boolean {
        return url.isValidUrl(allowHttp = BuildConfig.ALLOW_INSECURE_HTTP)
    }

    private enum class InitialSyncStartResult {
        STARTED,
        SUCCEEDED,
        FAILED,
        CANCELLED,
        TIMED_OUT
    }

    private companion object {
        val INITIAL_SYNC_START_TIMEOUT = 20.seconds
        const val KEY_CODE_VERIFIER = "oauth_code_verifier"
        const val KEY_AUTH_STATE = "oauth_auth_state"
        const val KEY_SERVER_URL = "oauth_server_url"
    }
}
