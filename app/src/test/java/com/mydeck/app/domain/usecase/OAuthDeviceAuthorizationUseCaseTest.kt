package com.mydeck.app.domain.usecase

import com.mydeck.app.domain.model.OAuthDeviceAuthorizationState
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class OAuthDeviceAuthorizationUseCaseTest {

    private lateinit var readeckApi: ReadeckApi
    private lateinit var json: Json
    private lateinit var useCase: OAuthDeviceAuthorizationUseCase

    @Before
    fun setup() {
        readeckApi = mockk()
        json = Json { ignoreUnknownKeys = true }
        useCase = OAuthDeviceAuthorizationUseCase(readeckApi, json)
    }

    @Test
    fun `initiateDeviceAuthorization returns AuthorizationRequired on successful flow`() = runTest {
        // Arrange
        val clientRegistrationResponse = OAuthClientRegistrationResponseDto(
            clientId = "test-client-id",
            clientName = "MyDeck Android",
            clientUri = "https://github.com/NateEaton/mydeck-android",
            grantTypes = listOf("urn:ietf:params:oauth:grant-type:device_code"),
            softwareId = "com.mydeck.app",
            softwareVersion = "1.0.0"
        )

        val deviceAuthResponse = OAuthDeviceAuthorizationResponseDto(
            deviceCode = "test-device-code",
            userCode = "ABCD-1234",
            verificationUri = "https://readeck.example.com/activate",
            verificationUriComplete = "https://readeck.example.com/activate?user_code=ABCD-1234",
            expiresIn = 300,
            interval = 5
        )

        coEvery { 
            readeckApi.registerOAuthClient(any()) 
        } returns Response.success(clientRegistrationResponse)

        coEvery { 
            readeckApi.authorizeDevice(any()) 
        } returns Response.success(deviceAuthResponse)

        // Act
        val result = useCase.initiateDeviceAuthorization()

        // Assert
        assertTrue(result is OAuthDeviceAuthorizationUseCase.DeviceAuthResult.AuthorizationRequired)
        val state = (result as OAuthDeviceAuthorizationUseCase.DeviceAuthResult.AuthorizationRequired).state
        assertEquals("test-client-id", state.clientId)
        assertEquals("test-device-code", state.deviceCode)
        assertEquals("ABCD-1234", state.userCode)
        assertEquals("https://readeck.example.com/activate", state.verificationUri)
        assertEquals("https://readeck.example.com/activate?user_code=ABCD-1234", state.verificationUriComplete)
        assertEquals(5, state.pollingInterval)
    }

    @Test
    fun `initiateDeviceAuthorization returns Error on client registration failure`() = runTest {
        // Arrange
        val errorResponse = Response.error<OAuthClientRegistrationResponseDto>(
            400, 
            """{"error": "invalid_client_metadata", "error_description": "Invalid client metadata"}""".toResponseBody(null)
        )

        coEvery { 
            readeckApi.registerOAuthClient(any()) 
        } returns errorResponse

        // Act
        val result = useCase.initiateDeviceAuthorization()

        // Assert
        assertTrue(result is OAuthDeviceAuthorizationUseCase.DeviceAuthResult.Error)
        val error = result as OAuthDeviceAuthorizationUseCase.DeviceAuthResult.Error
        assertTrue(error.message.contains("Failed to register with server"))
    }

    @Test
    fun `initiateDeviceAuthorization returns Error on device authorization failure`() = runTest {
        // Arrange
        val clientRegistrationResponse = OAuthClientRegistrationResponseDto(
            clientId = "test-client-id",
            clientName = "MyDeck Android",
            clientUri = "https://github.com/NateEaton/mydeck-android",
            grantTypes = listOf("urn:ietf:params:oauth:grant-type:device_code"),
            softwareId = "com.mydeck.app",
            softwareVersion = "1.0.0"
        )

        val errorResponse = Response.error<OAuthDeviceAuthorizationResponseDto>(
            400, 
            """{"error": "invalid_request", "error_description": "Missing required parameter: scope"}""".toResponseBody(null)
        )

        coEvery { 
            readeckApi.registerOAuthClient(any()) 
        } returns Response.success(clientRegistrationResponse)

        coEvery { 
            readeckApi.authorizeDevice(any()) 
        } returns errorResponse

        // Act
        val result = useCase.initiateDeviceAuthorization()

        // Assert
        assertTrue(result is OAuthDeviceAuthorizationUseCase.DeviceAuthResult.Error)
        val error = result as OAuthDeviceAuthorizationUseCase.DeviceAuthResult.Error
        assertTrue(error.message.contains("Failed to authorize device"))
    }

    @Test
    fun `initiateDeviceAuthorization returns Error on network error`() = runTest {
        // Arrange
        coEvery { 
            readeckApi.registerOAuthClient(any()) 
        } throws IOException("Network error")

        // Act
        val result = useCase.initiateDeviceAuthorization()

        // Assert
        assertTrue(result is OAuthDeviceAuthorizationUseCase.DeviceAuthResult.Error)
        val error = result as OAuthDeviceAuthorizationUseCase.DeviceAuthResult.Error
        assertTrue(error.message.contains("Network error"))
        assertEquals(IOException::class.java, error.exception!!::class.java)
    }

    @Test
    fun `pollForToken returns Success on successful token request`() = runTest {
        // Arrange
        val tokenResponse = OAuthTokenResponseDto(
            id = "token-id",
            accessToken = "test-access-token",
            tokenType = "Bearer",
            scope = "bookmarks:read bookmarks:write profile:read"
        )

        coEvery { 
            readeckApi.requestToken(any()) 
        } returns Response.success(tokenResponse)

        // Act
        val result = useCase.pollForToken("client-id", "device-code", 5)

        // Assert
        assertTrue(result is OAuthDeviceAuthorizationUseCase.TokenPollResult.Success)
        assertEquals("test-access-token", (result as OAuthDeviceAuthorizationUseCase.TokenPollResult.Success).accessToken)
    }

    @Test
    fun `pollForToken returns StillPending on authorization_pending error`() = runTest {
        // Arrange
        val errorResponse = Response.error<OAuthTokenResponseDto>(
            400, 
            """{"error": "authorization_pending", "error_description": "User has not yet authorized the device"}""".toResponseBody(null)
        )

        coEvery { 
            readeckApi.requestToken(any()) 
        } returns errorResponse

        // Act
        val result = useCase.pollForToken("client-id", "device-code", 5)

        // Assert
        assertEquals(OAuthDeviceAuthorizationUseCase.TokenPollResult.StillPending, result)
    }

    @Test
    fun `pollForToken returns SlowDown on slow_down error`() = runTest {
        // Arrange
        val errorResponse = Response.error<OAuthTokenResponseDto>(
            400, 
            """{"error": "slow_down", "error_description": "Polling too frequently"}""".toResponseBody(null)
        )

        coEvery { 
            readeckApi.requestToken(any()) 
        } returns errorResponse

        // Act
        val result = useCase.pollForToken("client-id", "device-code", 5)

        // Assert
        assertTrue(result is OAuthDeviceAuthorizationUseCase.TokenPollResult.SlowDown)
        assertEquals(10, (result as OAuthDeviceAuthorizationUseCase.TokenPollResult.SlowDown).newInterval) // 5 + 5
    }

    @Test
    fun `pollForToken returns UserDenied on access_denied error`() = runTest {
        // Arrange
        val errorResponse = Response.error<OAuthTokenResponseDto>(
            400, 
            """{"error": "access_denied", "error_description": "User denied the authorization request"}""".toResponseBody(null)
        )

        coEvery { 
            readeckApi.requestToken(any()) 
        } returns errorResponse

        // Act
        val result = useCase.pollForToken("client-id", "device-code", 5)

        // Assert
        assertTrue(result is OAuthDeviceAuthorizationUseCase.TokenPollResult.UserDenied)
        assertEquals("User denied the authorization request", (result as OAuthDeviceAuthorizationUseCase.TokenPollResult.UserDenied).message)
    }

    @Test
    fun `pollForToken returns Expired on expired_token error`() = runTest {
        // Arrange
        val errorResponse = Response.error<OAuthTokenResponseDto>(
            400, 
            """{"error": "expired_token", "error_description": "Device code has expired"}""".toResponseBody(null)
        )

        coEvery { 
            readeckApi.requestToken(any()) 
        } returns errorResponse

        // Act
        val result = useCase.pollForToken("client-id", "device-code", 5)

        // Assert
        assertTrue(result is OAuthDeviceAuthorizationUseCase.TokenPollResult.Expired)
        assertEquals("Device code has expired", (result as OAuthDeviceAuthorizationUseCase.TokenPollResult.Expired).message)
    }

    @Test
    fun `pollForToken returns Error on invalid_grant error`() = runTest {
        // Arrange
        val errorResponse = Response.error<OAuthTokenResponseDto>(
            400, 
            """{"error": "invalid_grant", "error_description": "Invalid device code"}""".toResponseBody(null)
        )

        coEvery { 
            readeckApi.requestToken(any()) 
        } returns errorResponse

        // Act
        val result = useCase.pollForToken("client-id", "device-code", 5)

        // Assert
        assertTrue(result is OAuthDeviceAuthorizationUseCase.TokenPollResult.Error)
        val error = result as OAuthDeviceAuthorizationUseCase.TokenPollResult.Error
        // The implementation returns either the error_description or "Invalid authorization code"
        assertTrue(error.message.contains("Invalid authorization code") || error.message.contains("Invalid device code"))
    }

    @Test
    fun `pollForToken returns Error on network error`() = runTest {
        // Arrange
        coEvery { 
            readeckApi.requestToken(any()) 
        } throws IOException("Network error")

        // Act
        val result = useCase.pollForToken("client-id", "device-code", 5)

        // Assert
        assertTrue(result is OAuthDeviceAuthorizationUseCase.TokenPollResult.Error)
        val error = result as OAuthDeviceAuthorizationUseCase.TokenPollResult.Error
        assertTrue(error.message.contains("Network error"))
        assertEquals(IOException::class.java, error.exception!!::class.java)
    }

    @Test
    fun `pollForToken returns Error on malformed error response`() = runTest {
        // Arrange
        val errorResponse = Response.error<OAuthTokenResponseDto>(
            400, 
            """<html><body><h1>Error</h1></body></html>""".toResponseBody(null)
        )

        coEvery { 
            readeckApi.requestToken(any()) 
        } returns errorResponse

        // Act
        val result = useCase.pollForToken("client-id", "device-code", 5)

        // Assert
        assertTrue(result is OAuthDeviceAuthorizationUseCase.TokenPollResult.Error)
        val error = result as OAuthDeviceAuthorizationUseCase.TokenPollResult.Error
        assertTrue(error.message.contains("Invalid error response from server"))
    }

    @Test
    fun `pollForToken returns Error on empty error body`() = runTest {
        // Arrange
        val errorResponse = Response.error<OAuthTokenResponseDto>(
            400, 
            "".toResponseBody(null)
        )

        coEvery { 
            readeckApi.requestToken(any()) 
        } returns errorResponse

        // Act
        val result = useCase.pollForToken("client-id", "device-code", 5)

        // Assert
        assertTrue(result is OAuthDeviceAuthorizationUseCase.TokenPollResult.Error)
        val error = result as OAuthDeviceAuthorizationUseCase.TokenPollResult.Error
        assertTrue(error.message.contains("Server error"))
    }

    @Test
    fun `pollForToken returns Error on unknown OAuth error`() = runTest {
        // Arrange
        val errorResponse = Response.error<OAuthTokenResponseDto>(
            400, 
            """{"error": "unknown_error", "error_description": "Some unknown error occurred"}""".toResponseBody(null)
        )

        coEvery { 
            readeckApi.requestToken(any()) 
        } returns errorResponse

        // Act
        val result = useCase.pollForToken("client-id", "device-code", 5)

        // Assert
        assertTrue(result is OAuthDeviceAuthorizationUseCase.TokenPollResult.Error)
        val error = result as OAuthDeviceAuthorizationUseCase.TokenPollResult.Error
        // The implementation returns either the error_description or "Unknown error: unknown_error"
        assertTrue(error.message.contains("Unknown error: unknown_error") || error.message.contains("Some unknown error occurred"))
    }
}
