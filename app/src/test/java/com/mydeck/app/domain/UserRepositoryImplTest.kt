package com.mydeck.app.domain

import com.mydeck.app.domain.model.AuthenticationDetails
import com.mydeck.app.domain.model.OAuthDeviceAuthorizationState
import com.mydeck.app.domain.model.User
import com.mydeck.app.domain.usecase.OAuthDeviceAuthorizationUseCase
import com.mydeck.app.io.prefs.SettingsDataStore
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.AuthenticationRequestDto
import com.mydeck.app.io.rest.model.AuthenticationResponseDto
import com.mydeck.app.io.rest.model.OAuthRevokeRequestDto
import com.mydeck.app.io.rest.model.ProviderDto
import com.mydeck.app.io.rest.model.ReaderSettingsDto
import com.mydeck.app.io.rest.model.ServerInfoDto
import com.mydeck.app.io.rest.model.SettingsDto
import com.mydeck.app.io.rest.model.StatusMessageDto
import com.mydeck.app.io.rest.model.UserDto
import com.mydeck.app.io.rest.model.UserProfileDto
import com.mydeck.app.io.rest.model.VersionDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class UserRepositoryImplTest {

    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var readeckApi: ReadeckApi
    private lateinit var json: Json
    private lateinit var oauthDeviceAuthUseCase: OAuthDeviceAuthorizationUseCase
    private lateinit var userRepository: UserRepositoryImpl

    // MutableStateFlows for testing
    private lateinit var urlFlow: MutableStateFlow<String?>
    private lateinit var usernameFlow: MutableStateFlow<String?>
    private lateinit var passwordFlow: MutableStateFlow<String?>
    private lateinit var tokenFlow: MutableStateFlow<String?>

    @Before
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined) // Use Unconfined for immediate execution
        settingsDataStore = mockk(relaxed = true) // relaxed = true to avoid specifying every method
        readeckApi = mockk()
        json = Json { ignoreUnknownKeys = true }
        oauthDeviceAuthUseCase = mockk()

        // Initialize MutableStateFlows
        urlFlow = MutableStateFlow(null)
        usernameFlow = MutableStateFlow(null)
        passwordFlow = MutableStateFlow(null)
        tokenFlow = MutableStateFlow(null)

        // Mock SettingsDataStore to return the MutableStateFlows
        every { settingsDataStore.urlFlow } returns urlFlow
        every { settingsDataStore.usernameFlow } returns usernameFlow
        every { settingsDataStore.passwordFlow } returns passwordFlow
        every { settingsDataStore.tokenFlow } returns tokenFlow

        userRepository = UserRepositoryImpl(settingsDataStore, readeckApi, json, oauthDeviceAuthUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `observeAuthenticationDetails returns AuthenticationDetails when all data is available`() = runTest {
        // Arrange
        val url = "https://example.com"
        val username = "testuser"
        val token = "testtoken"

        // Emit values to the MutableStateFlows (note: passwordFlow is ignored in OAuth)
        urlFlow.value = url
        usernameFlow.value = username
        tokenFlow.value = token

        // Act
        val result = userRepository.observeAuthenticationDetails().first()

        // Assert
        assertEquals(AuthenticationDetails(url, username, "", token), result) // Password is empty for OAuth
    }

    @Test
    fun `observeAuthenticationDetails returns null when any data is missing`() = runTest {
        // Arrange
        urlFlow.value = "https://example.com"
        usernameFlow.value = "testuser"
        // Note: passwordFlow is ignored in OAuth, so we test missing token instead
        tokenFlow.value = null // Missing token

        // Act
        val result = userRepository.observeAuthenticationDetails().first()

        // Assert
        assertNull(result)
    }

    // Legacy login tests removed - login method no longer exists
    // OAuth tests are below

    @Test
    fun `observeIsLoggedIn returns true when AuthenticationDetails is not null`() = runTest {
        // Arrange
        val url = "https://example.com"
        val username = "testuser"
        val password = "testpassword"
        val token = "testtoken"

        // Emit values to the MutableStateFlows
        urlFlow.value = url
        usernameFlow.value = username
        passwordFlow.value = password
        tokenFlow.value = token

        // Act
        val result = userRepository.observeIsLoggedIn().first()

        // Assert
        assertEquals(true, result)
    }

    @Test
    fun `observeIsLoggedIn returns false when AuthenticationDetails is null`() = runTest {
        // Arrange
        urlFlow.value = "https://example.com"
        usernameFlow.value = "testuser"
        // Note: passwordFlow is ignored in OAuth, so we test missing token instead
        tokenFlow.value = null // Missing token

        // Act
        val result = userRepository.observeIsLoggedIn().first()

        // Assert
        assertEquals(false, result)
    }

    @Test
    fun `observeUser returns null when AuthenticationDetails is null`() = runTest {
        // Arrange
        urlFlow.value = "https://example.com"
        usernameFlow.value = "testuser"
        // Note: passwordFlow is ignored in OAuth, so we test missing token instead
        tokenFlow.value = null // Missing token

        // Act
        val result = userRepository.observeUser().first()

        // Assert
        assertNull(result)
    }

    @Test
    fun `observeUser returns User when AuthenticationDetails is not null`() = runTest {
        // Arrange
        val username = "testuser"
        val url = "https://example.com"
        val token = "testtoken"

        // Emit values to the MutableStateFlows (note: passwordFlow is ignored in OAuth)
        urlFlow.value = url
        usernameFlow.value = username
        tokenFlow.value = token

        // Act
        val result = userRepository.observeUser().first()

        // Assert
        assertEquals(User(username), result)
    }

    // OAuth Tests

    @Test
    fun `initiateLogin returns DeviceAuthorizationRequired on successful device auth`() = runTest {
        // Arrange
        val url = "https://example.com"
        coEvery {
            readeckApi.getInfo()
        } returns Response.success(
            ServerInfoDto(
                version = VersionDto(canonical = "0.22", release = "0.22", build = "0"),
                features = listOf("oauth")
            )
        )

        val oauthState = OAuthDeviceAuthorizationState(
            clientId = "test-client-id",
            deviceCode = "test-device-code",
            userCode = "ABCD-1234",
            verificationUri = "https://example.com/activate",
            verificationUriComplete = "https://example.com/activate?code=ABCD-1234",
            expiresAt = System.currentTimeMillis() + 300000,
            pollingInterval = 5
        )

        coEvery { oauthDeviceAuthUseCase.initiateDeviceAuthorization() } returns OAuthDeviceAuthorizationUseCase.DeviceAuthResult.AuthorizationRequired(oauthState)

        // Act
        val result = userRepository.initiateLogin(url)

        // Assert
        assertTrue(result is UserRepository.LoginResult.DeviceAuthorizationRequired)
        val state = (result as UserRepository.LoginResult.DeviceAuthorizationRequired).state
        assertEquals(oauthState, state)
        coVerify { settingsDataStore.saveUrl(url) }
    }

    @Test
    fun `initiateLogin returns Error when server does not support oauth`() = runTest {
        // Arrange
        val url = "https://example.com"
        coEvery {
            readeckApi.getInfo()
        } returns Response.success(
            ServerInfoDto(
                version = VersionDto(canonical = "0.22", release = "0.22", build = "0"),
                features = listOf("email")
            )
        )

        // Act
        val result = userRepository.initiateLogin(url)

        // Assert
        assertTrue(result is UserRepository.LoginResult.Error)
        coVerify { settingsDataStore.saveUrl(url) }
        coVerify { settingsDataStore.clearCredentials() }
    }

    @Test
    fun `initiateLogin returns Error on device auth failure`() = runTest {
        // Arrange
        val url = "https://example.com"
        val errorMessage = "Device authorization failed"

        coEvery {
            readeckApi.getInfo()
        } returns Response.success(
            ServerInfoDto(
                version = VersionDto(canonical = "0.22", release = "0.22", build = "0"),
                features = listOf("oauth")
            )
        )

        coEvery { oauthDeviceAuthUseCase.initiateDeviceAuthorization() } returns OAuthDeviceAuthorizationUseCase.DeviceAuthResult.Error(errorMessage)

        // Act
        val result = userRepository.initiateLogin(url)

        // Assert
        assertTrue(result is UserRepository.LoginResult.Error)
        assertEquals(errorMessage, (result as UserRepository.LoginResult.Error).errorMessage)
        coVerify { settingsDataStore.saveUrl(url) }
        coVerify { settingsDataStore.clearCredentials() }
    }

    @Test
    fun `completeLogin returns Success on successful token and profile fetch`() = runTest {
        // Arrange
        val url = "https://example.com"
        val token = "test-token"
        val username = "testuser"
        val userProfile = UserProfileDto(
            provider = ProviderDto("readeck", "readeck", "mydeck", emptyList(), emptyList()),
            user = UserDto(username, "user@example.com", kotlinx.datetime.Clock.System.now(), kotlinx.datetime.Clock.System.now(), SettingsDto(false, "en", ReaderSettingsDto(800, "sans-serif", 16, 1, 0, 0)))
        )

        coEvery { settingsDataStore.saveToken(token) } returns Unit
        coEvery { readeckApi.userprofile() } returns Response.success(userProfile)
        coEvery { settingsDataStore.saveCredentials(url, username, "", token) } returns Unit

        // Act
        val result = userRepository.completeLogin(url, token)

        // Assert
        assertEquals(UserRepository.LoginResult.Success, result)
        coVerify { settingsDataStore.saveToken(token) }
        coVerify { readeckApi.userprofile() }
        coVerify { settingsDataStore.saveCredentials(url, username, "", token) }
    }

    @Test
    fun `logout returns Success when token revocation succeeds`() = runTest {
        // Arrange
        val token = "test-token"
        tokenFlow.value = token

        coEvery { settingsDataStore.tokenFlow } returns tokenFlow
        coEvery { readeckApi.revokeToken(OAuthRevokeRequestDto(token)) } returns Response.success(Unit)
        coEvery { settingsDataStore.clearCredentials() } returns Unit

        // Act
        val result = userRepository.logout()

        // Assert
        assertEquals(UserRepository.LogoutResult.Success, result)
        coVerify { readeckApi.revokeToken(OAuthRevokeRequestDto(token)) }
        coVerify { settingsDataStore.clearCredentials() }
    }

    @Test
    fun `observeAuthenticationDetails returns AuthenticationDetails with empty password for OAuth`() = runTest {
        // Arrange
        val url = "https://example.com"
        val username = "testuser"
        val token = "testtoken"

        // Emit values to the MutableStateFlows
        urlFlow.value = url
        usernameFlow.value = username
        tokenFlow.value = token

        // Act
        val result = userRepository.observeAuthenticationDetails().first()

        // Assert
        assertEquals(AuthenticationDetails(url, username, "", token), result) // Password is empty for OAuth
    }
}
