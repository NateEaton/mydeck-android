package com.mydeck.app.domain.usecase

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.OAuthAuthCodeTokenRequestDto
import com.mydeck.app.io.rest.model.OAuthClientRegistrationRequestDto
import com.mydeck.app.io.rest.model.OAuthClientRegistrationResponseDto
import com.mydeck.app.io.rest.model.OAuthTokenResponseDto
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class OAuthAuthorizationCodeUseCaseTest {

    private lateinit var readeckApi: ReadeckApi
    private lateinit var json: Json
    private lateinit var useCase: OAuthAuthorizationCodeUseCase

    private val registrationResponse = OAuthClientRegistrationResponseDto(
        clientId = "test-client-id",
        clientName = "MyDeck Android",
        clientUri = "https://github.com/NateEaton/mydeck-android",
        grantTypes = listOf("authorization_code"),
        softwareId = "com.mydeck.app",
        softwareVersion = "1.0.0"
    )

    @Before
    fun setup() {
        readeckApi = mockk()
        json = Json { ignoreUnknownKeys = true }

        val packageInfo = PackageInfo().apply { versionName = "test-version" }
        val packageManager = mockk<PackageManager> {
            every { getPackageInfo(any<String>(), 0) } returns packageInfo
        }
        val context = mockk<Context> {
            every { this@mockk.packageManager } returns packageManager
            every { packageName } returns "com.mydeck.app.test"
        }
        useCase = OAuthAuthorizationCodeUseCase(readeckApi, json, context)
    }

    // --- initiateAuthorization ---

    @Test
    fun `initiateAuthorization returns Ready with authorize URL on success`() = runTest {
        coEvery { readeckApi.registerOAuthClient(any()) } returns Response.success(registrationResponse)

        val result = useCase.initiateAuthorization("https://readeck.example.com/api")

        assertTrue(result is OAuthAuthorizationCodeUseCase.AuthCodeInitiateResult.Ready)
        val ready = result as OAuthAuthorizationCodeUseCase.AuthCodeInitiateResult.Ready
        assertTrue(ready.result.authorizeUrl.contains("readeck.example.com/authorize"))
        assertTrue(ready.result.authorizeUrl.contains("client_id=test-client-id"))
        assertTrue(ready.result.authorizeUrl.contains("code_challenge_method=S256"))
        assertNotNull(ready.result.codeVerifier)
        assertNotNull(ready.result.state)
        assertEquals("test-client-id", ready.result.clientId)
    }

    @Test
    fun `initiateAuthorization includes redirect_uri and authorization_code in registration`() = runTest {
        val registrationSlot = slot<OAuthClientRegistrationRequestDto>()
        coEvery { readeckApi.registerOAuthClient(capture(registrationSlot)) } returns Response.success(registrationResponse)

        useCase.initiateAuthorization("https://readeck.example.com/api")

        val captured = registrationSlot.captured
        assertTrue(captured.grantTypes.contains("authorization_code"))
        assertNotNull(captured.redirectUris)
        assertTrue(captured.redirectUris!!.contains("mydeck://oauth-callback"))
    }

    @Test
    fun `initiateAuthorization returns Error on registration failure`() = runTest {
        val errorResponse = Response.error<OAuthClientRegistrationResponseDto>(
            400,
            """{"error": "invalid_client_metadata", "error_description": "Bad metadata"}""".toResponseBody(null)
        )
        coEvery { readeckApi.registerOAuthClient(any()) } returns errorResponse

        val result = useCase.initiateAuthorization("https://readeck.example.com/api")

        assertTrue(result is OAuthAuthorizationCodeUseCase.AuthCodeInitiateResult.Error)
        assertTrue((result as OAuthAuthorizationCodeUseCase.AuthCodeInitiateResult.Error).message.contains("Failed to register"))
    }

    @Test
    fun `initiateAuthorization returns Error on network error`() = runTest {
        coEvery { readeckApi.registerOAuthClient(any()) } throws IOException("No route to host")

        val result = useCase.initiateAuthorization("https://readeck.example.com/api")

        assertTrue(result is OAuthAuthorizationCodeUseCase.AuthCodeInitiateResult.Error)
        assertTrue((result as OAuthAuthorizationCodeUseCase.AuthCodeInitiateResult.Error).message.contains("Network error"))
    }

    // --- exchangeCode ---

    @Test
    fun `exchangeCode returns Success on valid token response`() = runTest {
        val tokenResponse = OAuthTokenResponseDto(
            accessToken = "my-access-token",
            tokenType = "Bearer",
            scope = "bookmarks:read bookmarks:write profile:read"
        )
        coEvery { readeckApi.requestTokenWithAuthCode(any()) } returns Response.success(tokenResponse)

        val result = useCase.exchangeCode("auth-code-123", "verifier-abc")

        assertTrue(result is OAuthAuthorizationCodeUseCase.TokenExchangeResult.Success)
        assertEquals("my-access-token", (result as OAuthAuthorizationCodeUseCase.TokenExchangeResult.Success).accessToken)
    }

    @Test
    fun `exchangeCode sends correct grant_type code and code_verifier`() = runTest {
        val requestSlot = slot<OAuthAuthCodeTokenRequestDto>()
        val tokenResponse = OAuthTokenResponseDto(accessToken = "token", tokenType = "Bearer")
        coEvery { readeckApi.requestTokenWithAuthCode(capture(requestSlot)) } returns Response.success(tokenResponse)

        useCase.exchangeCode("the-code", "the-verifier")

        val captured = requestSlot.captured
        assertEquals("authorization_code", captured.grantType)
        assertEquals("the-code", captured.code)
        assertEquals("the-verifier", captured.codeVerifier)
    }

    @Test
    fun `exchangeCode returns UserDenied on access_denied error`() = runTest {
        val errorResponse = Response.error<OAuthTokenResponseDto>(
            400,
            """{"error": "access_denied", "error_description": "User denied access"}""".toResponseBody(null)
        )
        coEvery { readeckApi.requestTokenWithAuthCode(any()) } returns errorResponse

        val result = useCase.exchangeCode("code", "verifier")

        assertTrue(result is OAuthAuthorizationCodeUseCase.TokenExchangeResult.UserDenied)
        assertEquals("User denied access", (result as OAuthAuthorizationCodeUseCase.TokenExchangeResult.UserDenied).message)
    }

    @Test
    fun `exchangeCode returns Error on invalid_grant`() = runTest {
        val errorResponse = Response.error<OAuthTokenResponseDto>(
            400,
            """{"error": "invalid_grant", "error_description": "Code expired or invalid"}""".toResponseBody(null)
        )
        coEvery { readeckApi.requestTokenWithAuthCode(any()) } returns errorResponse

        val result = useCase.exchangeCode("code", "verifier")

        assertTrue(result is OAuthAuthorizationCodeUseCase.TokenExchangeResult.Error)
        assertTrue((result as OAuthAuthorizationCodeUseCase.TokenExchangeResult.Error).message.contains("Code expired or invalid"))
    }

    @Test
    fun `exchangeCode returns Error on network failure`() = runTest {
        coEvery { readeckApi.requestTokenWithAuthCode(any()) } throws IOException("Connection refused")

        val result = useCase.exchangeCode("code", "verifier")

        assertTrue(result is OAuthAuthorizationCodeUseCase.TokenExchangeResult.Error)
        assertTrue((result as OAuthAuthorizationCodeUseCase.TokenExchangeResult.Error).message.contains("Network error"))
    }

    @Test
    fun `exchangeCode returns Error when server returns empty body`() = runTest {
        val errorResponse = Response.error<OAuthTokenResponseDto>(400, "".toResponseBody(null))
        coEvery { readeckApi.requestTokenWithAuthCode(any()) } returns errorResponse

        val result = useCase.exchangeCode("code", "verifier")

        assertTrue(result is OAuthAuthorizationCodeUseCase.TokenExchangeResult.Error)
        assertTrue((result as OAuthAuthorizationCodeUseCase.TokenExchangeResult.Error).message.contains("Server error"))
    }
}
