# Technical Specification: OAuth Device Code Grant Authentication

**Project:** MyDeck Android
**Feature:** OAuth 2.0 Device Code Grant Authentication
**Target Readeck Version:** 0.22+ (nightly builds)
**Status:** Draft
**Date:** 2026-02-11

---

## Table of Contents

1. [Overview](#overview)
2. [Background](#background)
3. [Goals and Requirements](#goals-and-requirements)
4. [API Specification](#api-specification)
5. [Data Models](#data-models)
6. [Architecture Changes](#architecture-changes)
7. [Implementation Plan](#implementation-plan)
8. [UI/UX Flow](#uiux-flow)
9. [Error Handling](#error-handling)
10. [Testing Strategy](#testing-strategy)
11. [Migration Considerations](#migration-considerations)
12. [Security Considerations](#security-considerations)
13. [Future Enhancements](#future-enhancements)

---

## 1. Overview

This specification outlines the implementation of OAuth 2.0 Device Code Grant authentication flow to replace the deprecated `/auth` endpoint in MyDeck Android. This change is required to support Readeck 0.22+ which has migrated exclusively to OAuth-based authentication.

### Current State
- App uses simple username/password authentication via `POST /auth`
- Returns a bearer token directly
- Authentication UI lives in `AccountSettingsViewModel` / `AccountSettingsScreen`
- Credentials (url, username, password, token) stored in `EncryptedSharedPreferences` via `SettingsDataStore`
- `AuthInterceptor` adds `Bearer` token to all requests; `UrlInterceptor` rewrites the dummy base URL
- `AuthenticateUseCase` orchestrates login and post-login tasks (clearing bookmarks, enqueuing sync worker)
- `UserRepository.logout()` is declared but unimplemented (`TODO`)

### Target State
- App uses OAuth 2.0 Device Code Grant flow
- Checks `/api/info` endpoint `features` array for `"oauth"` capability before initiating flow
- Supports scoped permissions (`bookmarks:read`, `bookmarks:write`, `profile:read`)
- Compatible with Readeck 0.22+ authentication infrastructure
- Maintains backward compatibility with stored tokens (same Bearer scheme)

---

## 2. Background

### Why This Change Is Needed

Readeck 0.22 introduced several authentication enhancements:
- TOTP (Two-Factor Authentication) support
- Forwarded authentication (SSO/proxy integration)
- mTLS for HTTPS listener
- OAuth 2.0 as the standard authentication mechanism

The legacy `/auth` endpoint has been **removed entirely** from the API. Attempts to call it result in HTML error pages being returned, causing JSON parsing errors in the app.

### Why Device Code Grant?

The Device Code Grant flow (RFC 8628) is optimal for:
- Mobile applications
- Devices with limited input capabilities
- Scenarios where users can authenticate on a separate device
- Self-hosted instances with complex URLs

**Advantages for MyDeck:**
- Simpler implementation (no deep linking required)
- More reliable for self-hosted scenarios
- Better UX when users have password managers on desktop
- No Android-specific URL scheme complexity

---

## 3. Goals and Requirements

### Functional Requirements

1. **FR-1:** User must be able to authenticate using OAuth Device Code Grant
2. **FR-2:** App must display device code and verification URL to user
3. **FR-3:** App must poll for authorization completion
4. **FR-4:** App must handle user approval/denial/timeout scenarios
5. **FR-5:** App must store OAuth access token securely
6. **FR-6:** App must use Bearer token for all authenticated API calls
7. **FR-7:** User must be able to revoke/logout (token revocation)

### Non-Functional Requirements

1. **NFR-1:** Authentication flow must complete within 5 minutes (token expiry)
2. **NFR-2:** Polling must respect server-specified interval (typically 5 seconds)
3. **NFR-3:** UI must remain responsive during polling
4. **NFR-4:** Network errors must be handled gracefully with retry logic
5. **NFR-5:** Implementation must follow existing app architecture patterns

### Scope

**In Scope:**
- OAuth Device Code Grant implementation
- New API endpoints and DTOs
- Updated login UI/UX
- Token storage and management
- Logout/token revocation

**Out of Scope:**
- OAuth Authorization Code Grant flow
- Backward compatibility with Readeck < 0.22
- Token refresh mechanism (if Readeck doesn't support it)
- Multiple account support

---

## 4. API Specification

### Base URL
All endpoints are relative to: `{user_server_url}/api`

Example: `https://readeck.example.com/api`

---

### 4.0 Check Server OAuth Capability

**Endpoint:** `GET /info`
**Authentication:** None (public endpoint)
**Description:** Returns public server information including supported features. The app **must** call this endpoint before initiating the OAuth flow to confirm the server supports OAuth.

#### Response (200 OK)
```json
{
  "version": {
    "canonical": "0.22.0-175-g154ad5c1",
    "release": "0.22.0",
    "build": "175-g154ad5c1"
  },
  "features": ["email", "oauth"]
}
```

**Feature Detection:**
- If `features` contains `"oauth"` → proceed with OAuth Device Code flow
- If `features` does not contain `"oauth"` → show error: server does not support OAuth authentication

---

### 4.1 Register OAuth Client

**Endpoint:** `POST /oauth/client`
**Authentication:** None (public endpoint)
**Description:** Creates an OAuth client (RFC 7591). The client registration is ephemeral and valid for a limited time.

**Required fields:** `client_name`, `client_uri`, `software_id`, `software_version`
**Optional fields:** `grant_types` (defaults to `["authorization_code", "urn:ietf:params:oauth:grant-type:device_code"]`), `logo_uri`, `redirect_uris`, `token_endpoint_auth_method`, `response_types`

> **Important:** `client_uri` must be an HTTPS URL that resolves to a non-local, non-private IP address. Using `http://` or localhost URLs will be rejected.

#### Request Body
```json
{
  "client_name": "MyDeck Android",
  "client_uri": "https://github.com/NateEaton/mydeck-android",
  "software_id": "com.mydeck.app",
  "software_version": "1.0.0",
  "grant_types": ["urn:ietf:params:oauth:grant-type:device_code"]
}
```

#### Response (201 Created)
```json
{
  "client_id": "abc123xyz789",
  "client_name": "MyDeck Android",
  "client_uri": "https://github.com/NateEaton/mydeck-android",
  "grant_types": "urn:ietf:params:oauth:grant-type:device_code",
  "software_id": "com.mydeck.app",
  "software_version": "1.0.0",
  "token_endpoint_auth_method": "none",
  "response_types": "code"
}
```

> **Note:** In the response, `grant_types` and `response_types` are returned as **strings** (not arrays), unlike the request where they are arrays. The response also omits `client_id_issued_at` — this field does not exist in the Readeck API despite being part of RFC 7591.
```

#### Error Response (400 Bad Request)
```json
{
  "error": "invalid_client_metadata",
  "error_description": "Invalid grant_types specified"
}
```

---

### 4.2 Request Device Authorization

**Endpoint:** `POST /oauth/device`
**Authentication:** None (public endpoint)
**Description:** Initiates device authorization flow. Returns user code and verification URL.

#### Request Body
```json
{
  "client_id": "abc123xyz789",
  "scope": "bookmarks:read bookmarks:write profile:read"
}
```

**Available Scopes:**
- `bookmarks:read` - Read-only access to bookmarks
- `bookmarks:write` - Write access to bookmarks
- `profile:read` - Extended profile information (email, settings). Note: the `/profile` endpoint is accessible with *any* OAuth scope, but only returns extended information (e.g., `user.email`) with the `profile:read` scope. Including `profile:read` is recommended so the app can retrieve the username after authentication.

#### Response (200 OK)
```json
{
  "device_code": "GmRhmhcxhwAzkoEqiMEg_DnyEysNkuNhszIySk9eS",
  "user_code": "WDJB-MJHT",
  "verification_uri": "https://readeck.example.com/activate",
  "verification_uri_complete": "https://readeck.example.com/activate?user_code=WDJB-MJHT",
  "expires_in": 300,
  "interval": 5
}
```

**Fields:**
- `device_code`: Opaque string, keep private, use for polling
- `user_code`: Short code (8 chars with dash), display to user
- `verification_uri`: URL user should visit
- `verification_uri_complete`: URL with pre-filled code (optional UX enhancement)
- `expires_in`: Seconds until device code expires (typically 300 = 5 minutes)
- `interval`: Minimum seconds between polling requests (typically 5)

#### Error Response (400 Bad Request)
```json
{
  "error": "invalid_request",
  "error_description": "Missing required parameter: scope"
}
```

---

### 4.3 Poll for Access Token

**Endpoint:** `POST /oauth/token`
**Authentication:** None (public endpoint)
**Description:** Poll to check if user has authorized the device. Must respect `interval` from device authorization.

#### Request Body
```json
{
  "grant_type": "urn:ietf:params:oauth:grant-type:device_code",
  "client_id": "abc123xyz789",
  "device_code": "GmRhmhcxhwAzkoEqiMEg_DnyEysNkuNhszIySk9eS"
}
```

#### Response (201 Created) - Success
```json
{
  "id": "X4bmnMRcnDhQtu5y33qzTp",
  "access_token": "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "scope": "bookmarks:read bookmarks:write profile:read"
}
```

> **Note:** The Readeck API does **not** return an `expires_in` field in the token response. Tokens are long-lived and do not expire unless explicitly revoked. There is also no refresh token mechanism.
```

#### Response (400 Bad Request) - Still Pending
```json
{
  "error": "authorization_pending",
  "error_description": "User has not yet authorized the device"
}
```

#### Response (400 Bad Request) - User Denied
```json
{
  "error": "access_denied",
  "error_description": "User denied the authorization request"
}
```

#### Response (400 Bad Request) - Polling Too Fast
```json
{
  "error": "slow_down",
  "error_description": "Polling too frequently, increase interval by 5 seconds"
}
```

#### Response (400 Bad Request) - Expired
```json
{
  "error": "expired_token",
  "error_description": "Device code has expired"
}
```

---

### 4.4 Revoke Token (Logout)

**Endpoint:** `POST /oauth/revoke`
**Authentication:** Bearer token (same token being revoked)
**Description:** Revokes an access token. The request must authenticate using the same access token as the one provided in the request body.

#### Request Headers
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

#### Request Body
```json
{
  "token": "<access_token>"
}
```

> **Note:** The `token` field has a `maxLength` of 64 characters per the API spec.
```

#### Response (200 OK)
```
(Empty response body)
```

#### Error Response (401 Unauthorized)
```json
{
  "status": 401,
  "message": "Invalid or expired token"
}
```

---

## 5. Data Models

### 5.1 New DTOs

Create the following new data transfer objects in `app/src/main/java/com/mydeck/app/io/rest/model/`:

#### OAuthClientRegistrationRequestDto.kt
```kotlin
package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthClientRegistrationRequestDto(
    @SerialName("client_name")
    val clientName: String,

    @SerialName("client_uri")
    val clientUri: String,

    @SerialName("software_id")
    val softwareId: String,

    @SerialName("software_version")
    val softwareVersion: String,

    @SerialName("grant_types")
    val grantTypes: List<String>
)
```

#### OAuthClientRegistrationResponseDto.kt
```kotlin
package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthClientRegistrationResponseDto(
    @SerialName("client_id")
    val clientId: String,

    @SerialName("client_name")
    val clientName: String,

    @SerialName("client_uri")
    val clientUri: String,

    @SerialName("grant_types")
    val grantTypes: String? = null, // Note: returned as string, not array

    @SerialName("software_id")
    val softwareId: String,

    @SerialName("software_version")
    val softwareVersion: String,

    @SerialName("logo_uri")
    val logoUri: String? = null,

    @SerialName("redirect_uris")
    val redirectUris: List<String>? = null,

    @SerialName("token_endpoint_auth_method")
    val tokenEndpointAuthMethod: String? = null,

    @SerialName("response_types")
    val responseTypes: String? = null // Note: returned as string, not array
)
```

#### OAuthDeviceAuthorizationRequestDto.kt
```kotlin
package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthDeviceAuthorizationRequestDto(
    @SerialName("client_id")
    val clientId: String,

    @SerialName("scope")
    val scope: String
)
```

#### OAuthDeviceAuthorizationResponseDto.kt
```kotlin
package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthDeviceAuthorizationResponseDto(
    @SerialName("device_code")
    val deviceCode: String,

    @SerialName("user_code")
    val userCode: String,

    @SerialName("verification_uri")
    val verificationUri: String,

    @SerialName("verification_uri_complete")
    val verificationUriComplete: String? = null,

    @SerialName("expires_in")
    val expiresIn: Int,

    @SerialName("interval")
    val interval: Int
)
```

#### OAuthTokenRequestDto.kt
```kotlin
package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthTokenRequestDto(
    @SerialName("grant_type")
    val grantType: String,

    @SerialName("client_id")
    val clientId: String,

    @SerialName("device_code")
    val deviceCode: String
)
```

#### OAuthTokenResponseDto.kt
```kotlin
package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthTokenResponseDto(
    @SerialName("id")
    val id: String? = null,

    @SerialName("access_token")
    val accessToken: String,

    @SerialName("token_type")
    val tokenType: String,

    @SerialName("scope")
    val scope: String? = null
)
```

> **Note:** The Readeck API does not return `expires_in` in the token response. Tokens do not expire unless revoked.
```

#### OAuthErrorDto.kt
```kotlin
package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthErrorDto(
    @SerialName("error")
    val error: String,

    @SerialName("error_description")
    val errorDescription: String? = null
)
```

#### OAuthRevokeRequestDto.kt
```kotlin
package com.mydeck.app.io.rest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthRevokeRequestDto(
    @SerialName("token")
    val token: String
)
```

### 5.2 Domain Models

#### OAuthDeviceAuthorizationState.kt
```kotlin
package com.mydeck.app.domain.model

data class OAuthDeviceAuthorizationState(
    val clientId: String, // Needed for token polling after device authorization
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String?,
    val expiresAt: Long, // Unix timestamp (milliseconds)
    val pollingInterval: Int // seconds
)
```

---

## 6. Architecture Changes

### 6.1 ReadeckApi Interface

**File:** `app/src/main/java/com/mydeck/app/io/rest/ReadeckApi.kt`

**Changes:**
1. Remove or deprecate the existing `authenticate()` method
2. Add new OAuth endpoints

```kotlin
interface ReadeckApi {
    // ... existing methods ...

    // OAuth Device Code Grant Flow
    @POST("oauth/client")
    suspend fun registerOAuthClient(
        @Body body: OAuthClientRegistrationRequestDto
    ): Response<OAuthClientRegistrationResponseDto>

    @POST("oauth/device")
    suspend fun authorizeDevice(
        @Body body: OAuthDeviceAuthorizationRequestDto
    ): Response<OAuthDeviceAuthorizationResponseDto>

    @POST("oauth/token")
    suspend fun requestToken(
        @Body body: OAuthTokenRequestDto
    ): Response<OAuthTokenResponseDto>

    @POST("oauth/revoke")
    suspend fun revokeToken(
        @Body body: OAuthRevokeRequestDto
    ): Response<Unit>

    // Deprecated - Remove after migration
    @Deprecated("Use OAuth Device Code Grant flow instead")
    @POST("auth")
    suspend fun authenticate(
        @Body body: AuthenticationRequestDto
    ): Response<AuthenticationResponseDto>
}
```

---

### 6.2 UserRepository Interface

**File:** `app/src/main/java/com/mydeck/app/domain/UserRepository.kt`

**Changes:**
1. Remove `login(url, username, password)` — no longer supported by Readeck
2. Replace with `initiateLogin(url)` for starting OAuth flow
3. Add `completeLogin(...)` for saving credentials after token is obtained
4. Implement `logout()` (currently a TODO stub) with token revocation
5. Add `DeviceAuthorizationRequired` to `LoginResult`
6. Add `LogoutResult` sealed class

> **Codebase context:** The existing interface has `login(url, username, password)`, `login(url, appToken)` (unimplemented), and `logout()` (unimplemented). The existing `LoginResult` has `Success`, `Error`, and `NetworkError`.

```kotlin
interface UserRepository {
    fun observeIsLoggedIn(): Flow<Boolean>
    fun observeUser(): Flow<User?>
    fun observeAuthenticationDetails(): Flow<AuthenticationDetails?>

    // Start OAuth Device Code flow — replaces login(url, username, password)
    suspend fun initiateLogin(url: String): LoginResult

    // Save credentials after token is obtained via polling
    suspend fun completeLogin(url: String, token: String): LoginResult

    // Implement logout with token revocation
    suspend fun logout(): LogoutResult

    sealed class LoginResult {
        data object Success : LoginResult()
        data class DeviceAuthorizationRequired(
            val state: OAuthDeviceAuthorizationState
        ) : LoginResult()
        data class Error(
            val errorMessage: String,
            val code: Int? = null,
            val ex: Exception? = null
        ) : LoginResult()
        data class NetworkError(val errorMessage: String) : LoginResult()
    }

    sealed class LogoutResult {
        data object Success : LogoutResult()
        data class Error(val errorMessage: String) : LogoutResult()
    }
}
```

> **Important architectural note:** The `completeLogin` method should:
> 1. Save the token to `SettingsDataStore`
> 2. Call `GET /profile` to retrieve the username
> 3. Save credentials with `password = ""` (OAuth has no password)
> 4. The `AuthenticationDetails` model and `observeAuthenticationDetails()` need updating to make `password` optional (nullable or empty string) so `observeIsLoggedIn()` works correctly without a password.
```

---

### 6.3 UserRepositoryImpl

**File:** `app/src/main/java/com/mydeck/app/domain/UserRepositoryImpl.kt`

> **Codebase context:** Currently injects `SettingsDataStore`, `ReadeckApi`, and `Json`. Constructor bound via `@Binds` in `AppModule`.

**Major Changes:**

1. **Add `OAuthDeviceAuthorizationUseCase` as a constructor dependency**
2. **Replace `login(url, username, password)` with `initiateLogin(url)`** that calls `oauthDeviceAuthUseCase.initiateDeviceAuthorization()`
3. **Add `completeLogin(url, token)`** that saves token, fetches username from `/profile`, and calls `settingsDataStore.saveCredentials(url, username, "", token)`
4. **Implement `logout()`** with token revocation via `readeckApi.revokeToken()`, then `settingsDataStore.clearCredentials()`
5. **Update `observeAuthenticationDetails()`** to work without a password — either make `password` nullable in `AuthenticationDetails` or check only `url + username + token`

**Key design decision:** The polling loop does **not** belong in the repository. `UserRepositoryImpl.initiateLogin()` returns `DeviceAuthorizationRequired` with the state (including `clientId`). The ViewModel calls `OAuthDeviceAuthorizationUseCase.pollForToken()` directly in its own coroutine loop. On success, the ViewModel calls `userRepository.completeLogin()`.

**New Dependencies:**
- `OAuthDeviceAuthorizationUseCase`

---

### 6.4 New Use Case: OAuthDeviceAuthorizationUseCase

**File:** `app/src/main/java/com/mydeck/app/domain/usecase/OAuthDeviceAuthorizationUseCase.kt`

> **Codebase context:** Sits alongside existing `AuthenticateUseCase`. The existing `AuthenticateUseCase` orchestrates post-login tasks (deleting bookmarks, resetting sync timestamp, enqueuing `LoadBookmarksWorker`). These post-login tasks should remain in `AuthenticateUseCase` (or a renamed equivalent), called after the OAuth flow completes. `OAuthDeviceAuthorizationUseCase` handles only the OAuth protocol.

This use case handles the OAuth Device Code Grant flow:
- Client registration (`POST /oauth/client`)
- Device authorization request (`POST /oauth/device`)
- Token polling (`POST /oauth/token`) with `slow_down` handling
- Returns `clientId` in the `OAuthDeviceAuthorizationState` for use during polling

```kotlin
package com.mydeck.app.domain.usecase

import com.mydeck.app.BuildConfig
import com.mydeck.app.domain.model.OAuthDeviceAuthorizationState
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.*
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

class OAuthDeviceAuthorizationUseCase @Inject constructor(
    private val readeckApi: ReadeckApi,
    private val json: Json
) {
    companion object {
        private const val GRANT_TYPE_DEVICE_CODE = "urn:ietf:params:oauth:grant-type:device_code"
        private const val REQUIRED_SCOPES = "bookmarks:read bookmarks:write profile:read"
        private const val CLIENT_NAME = "MyDeck Android"
        private const val CLIENT_URI = "https://github.com/NateEaton/mydeck-android"
        private const val SOFTWARE_ID = "com.mydeck.app"
        private const val SLOW_DOWN_ADDITIONAL_INTERVAL = 5 // seconds
    }

    sealed class DeviceAuthResult {
        data class AuthorizationRequired(val state: OAuthDeviceAuthorizationState) : DeviceAuthResult()
        data class Error(val message: String, val exception: Exception? = null) : DeviceAuthResult()
    }

    sealed class TokenPollResult {
        data class Success(val accessToken: String) : TokenPollResult()
        data object StillPending : TokenPollResult()
        data class SlowDown(val newInterval: Int) : TokenPollResult()
        data class UserDenied(val message: String) : TokenPollResult()
        data class Expired(val message: String) : TokenPollResult()
        data class Error(val message: String, val exception: Exception? = null) : TokenPollResult()
    }

    /**
     * Step 1: Register OAuth client and request device authorization.
     * Returns OAuthDeviceAuthorizationState with clientId included.
     */
    suspend fun initiateDeviceAuthorization(): DeviceAuthResult {
        // Full implementation in IMPLEMENTATION_CODE document
    }

    /**
     * Step 2: Poll for token (call repeatedly from ViewModel until terminal result).
     * Terminal results: Success, UserDenied, Expired, Error.
     * Non-terminal results: StillPending (continue), SlowDown (increase interval and continue).
     */
    suspend fun pollForToken(
        clientId: String,
        deviceCode: String,
        currentInterval: Int
    ): TokenPollResult {
        // Full implementation in IMPLEMENTATION_CODE document
    }
}
```

---

## 7. Implementation Plan

### Phase 1: API Layer (1-2 days)

#### Task 1.1: Create DTOs
- [ ] Create all OAuth DTOs listed in section 5.1
- [ ] Add proper serialization annotations
- [ ] Create unit tests for DTO serialization/deserialization

**Files to create:**
- `OAuthClientRegistrationRequestDto.kt`
- `OAuthClientRegistrationResponseDto.kt`
- `OAuthDeviceAuthorizationRequestDto.kt`
- `OAuthDeviceAuthorizationResponseDto.kt`
- `OAuthTokenRequestDto.kt`
- `OAuthTokenResponseDto.kt`
- `OAuthErrorDto.kt`
- `OAuthRevokeRequestDto.kt`

**Files to modify:**
- `ReadeckApi.kt` - Add OAuth endpoints

#### Task 1.2: Update ReadeckApi
- [ ] Add OAuth endpoints to `ReadeckApi.kt`
- [ ] Keep legacy `authenticate()` method but mark as deprecated
- [ ] Update documentation

#### Task 1.3: Test API Layer
- [ ] Create mock responses for all OAuth endpoints
- [ ] Test DTO serialization with actual API responses
- [ ] Test error response parsing

---

### Phase 2: Domain Layer (2-3 days)

#### Task 2.1: Create Domain Models
- [ ] Create `OAuthDeviceAuthorizationState.kt`
- [ ] Update `AuthenticationDetails.kt` if needed

#### Task 2.2: Create OAuthDeviceAuthorizationUseCase
- [ ] Implement client registration
- [ ] Implement device authorization request
- [ ] Implement token polling with proper interval handling
- [ ] Handle all error cases (denied, expired, slow_down, etc.)
- [ ] Add proper logging

#### Task 2.3: Update UserRepository
- [ ] Modify `UserRepository` interface
- [ ] Implement new `login(url)` method in `UserRepositoryImpl`
- [ ] Implement `logout()` method
- [ ] Wire up OAuthDeviceAuthorizationUseCase
- [ ] Remove or deprecate old authentication logic

#### Task 2.4: Test Domain Layer
- [ ] Unit test OAuthDeviceAuthorizationUseCase
- [ ] Unit test UserRepositoryImpl with mocked API
- [ ] Test error handling and edge cases

---

### Phase 3: UI/UX Layer (3-4 days)

#### Task 3.1: Update Login Screen
- [ ] Remove username/password fields from `AccountSettingsScreen`
- [ ] Keep server URL field
- [ ] Add "Sign In" button that starts OAuth flow
- [ ] Create device authorization dialog/screen (`DeviceAuthorizationDialog.kt`)
- [ ] Display user code prominently
- [ ] Display verification URI
- [ ] Add "Copy Code" and "Copy URL" buttons
- [ ] Show loading indicator during polling

**Modified Files:**
- `AccountSettingsViewModel.kt` — update `login()` to call `userRepository.initiateLogin(url)`, add polling loop, handle `DeviceAuthorizationRequired` state
- `AccountSettingsScreen.kt` — remove username/password fields, add device auth dialog trigger
- `DeviceAuthorizationDialog.kt` (new) — Compose dialog for device code display
- Add string resources (with localization as per CLAUDE.md)

> **Codebase context:** The existing `AccountSettingsViewModel` handles both login and settings. Its `login()` calls `AuthenticateUseCase.execute(url, username, password)`. The `AuthenticateUseCase` calls `userRepository.login()`, then clears bookmarks and enqueues sync. The updated flow should: (1) `AccountSettingsViewModel.login(url)` → `userRepository.initiateLogin(url)`, (2) on `DeviceAuthorizationRequired`, show dialog and start polling loop in ViewModel, (3) on token success, call `userRepository.completeLogin(url, token)` → then `AuthenticateUseCase` equivalent post-login tasks.

#### Task 3.2: Handle Polling States
- [ ] Show "Waiting for authorization..." message
- [ ] Update UI based on polling results
- [ ] Handle user cancellation
- [ ] Handle timeout/expiration
- [ ] Handle denial
- [ ] Navigate to main screen on success

#### Task 3.3: Add Logout Functionality
- [ ] Update settings screen with logout button
- [ ] Implement logout confirmation dialog
- [ ] Call token revocation API
- [ ] Clear stored credentials
- [ ] Navigate back to login screen

#### Task 3.4: Improve UX
- [ ] Add countdown timer showing expiration
- [ ] Add "Open in Browser" button (opens verification URI)
- [ ] Add haptic feedback on successful auth
- [ ] Add illustrations/animations for better UX
- [ ] Ensure proper loading states

---

### Phase 4: String Resources & Localization (1 day)

#### Task 4.1: Add English Strings
- [ ] Add all new strings to `values/strings.xml`
- [ ] Use clear, user-friendly language
- [ ] Include instructions and error messages

#### Task 4.2: Add Placeholder Translations
As per `CLAUDE.md`, add English placeholder strings to all language files:
- [ ] `values-de-rDE/strings.xml`
- [ ] `values-es-rES/strings.xml`
- [ ] `values-fr/strings.xml`
- [ ] `values-gl-rES/strings.xml`
- [ ] `values-pl/strings.xml`
- [ ] `values-pt-rPT/strings.xml`
- [ ] `values-ru/strings.xml`
- [ ] `values-uk/strings.xml`
- [ ] `values-zh-rCN/strings.xml`

**Example strings needed:**
```xml
<string name="oauth_device_auth_title">Authorize MyDeck</string>
<string name="oauth_device_auth_step1">1. Visit this URL:</string>
<string name="oauth_device_auth_step2">2. Enter this code:</string>
<string name="oauth_device_auth_copy_code">Copy Code</string>
<string name="oauth_device_auth_copy_url">Copy URL</string>
<string name="oauth_device_auth_open_browser">Open in Browser</string>
<string name="oauth_device_auth_waiting">Waiting for authorization…</string>
<string name="oauth_device_auth_expires_in">Expires in: %s</string>
<string name="oauth_device_auth_expired">Authorization code has expired. Please try again.</string>
<string name="oauth_device_auth_denied">Authorization was denied.</string>
<string name="oauth_device_auth_success">Successfully authorized!</string>
<string name="oauth_error_network">Network error. Please check your connection.</string>
<string name="logout_confirm_title">Sign Out</string>
<string name="logout_confirm_message">Are you sure you want to sign out?</string>
```

---

### Phase 5: Testing & Polish (2-3 days)

#### Task 5.1: Integration Testing
- [ ] Test full OAuth flow end-to-end
- [ ] Test with real Readeck 0.22+ instance
- [ ] Test network error scenarios
- [ ] Test cancellation during polling
- [ ] Test expiration handling
- [ ] Test denial handling
- [ ] Test logout flow

#### Task 5.2: Edge Cases
- [ ] Test with slow network connections
- [ ] Test with server downtime
- [ ] Test app backgrounding during polling
- [ ] Test app kill/restart during polling
- [ ] Test multiple rapid login attempts

#### Task 5.3: UI/UX Polish
- [ ] Ensure smooth animations
- [ ] Verify accessibility (TalkBack, large text)
- [ ] Test on various screen sizes
- [ ] Verify dark mode support
- [ ] Check for UI layout issues

#### Task 5.4: Code Review & Cleanup
- [ ] Remove deprecated authentication code
- [ ] Update documentation
- [ ] Add KDoc comments
- [ ] Run lint checks
- [ ] Fix any warnings

---

### Phase 6: Documentation (1 day)

#### Task 6.1: Update README
- [ ] Document new authentication flow
- [ ] Update setup instructions
- [ ] Note Readeck version requirements

#### Task 6.2: Add Developer Documentation
- [ ] Document OAuth implementation details
- [ ] Add troubleshooting guide
- [ ] Document testing procedures

#### Task 6.3: User Documentation
- [ ] Create user guide for new login flow
- [ ] Add screenshots/video
- [ ] Update FAQ if applicable

---

## 8. UI/UX Flow

### 8.1 Login Flow

```
┌─────────────────────────────────────┐
│  Welcome to MyDeck                  │
├─────────────────────────────────────┤
│                                     │
│  [Logo]                             │
│                                     │
│  Server URL:                        │
│  ┌─────────────────────────────┐   │
│  │ https://readeck.example.com │   │
│  └─────────────────────────────┘   │
│                                     │
│         [Sign In]                   │
│                                     │
└─────────────────────────────────────┘
                  ↓
        User taps "Sign In"
                  ↓
        App calls registerOAuthClient()
        App calls authorizeDevice()
                  ↓
┌─────────────────────────────────────┐
│  ← Authorize MyDeck                 │
├─────────────────────────────────────┤
│                                     │
│  To authorize this app:             │
│                                     │
│  1. Visit this URL on any device:   │
│                                     │
│  ┌─────────────────────────────┐   │
│  │ readeck.example.com/activate│   │
│  │          [Copy URL] 📋      │   │
│  └─────────────────────────────┘   │
│                                     │
│  2. Enter this code:                │
│                                     │
│  ┌─────────────────────────────┐   │
│  │      XKCD-4892              │   │
│  │         [Copy Code] 📋      │   │
│  └─────────────────────────────┘   │
│                                     │
│  [Open in Browser]                  │
│                                     │
│  ⏱ Waiting for authorization...    │
│     Expires in: 4:32                │
│                                     │
│  [Cancel]                           │
│                                     │
└─────────────────────────────────────┘
                  ↓
        App polls requestToken()
        every 5 seconds
                  ↓
        (User completes auth in browser)
                  ↓
┌─────────────────────────────────────┐
│  ✅ Authorization Successful!       │
├─────────────────────────────────────┤
│                                     │
│  Welcome back!                      │
│  Loading your bookmarks...          │
│                                     │
└─────────────────────────────────────┘
                  ↓
        Navigate to main screen
```

### 8.2 Browser Flow (User's Perspective)

```
User opens browser and visits:
https://readeck.example.com/activate

┌─────────────────────────────────────┐
│  Readeck - Device Authorization     │
├─────────────────────────────────────┤
│                                     │
│  Enter the code shown on your       │
│  device:                            │
│                                     │
│  ┌─────────────────────────────┐   │
│  │ [____-____]                 │   │
│  └─────────────────────────────┘   │
│                                     │
│         [Continue]                  │
│                                     │
└─────────────────────────────────────┘
                  ↓
        User enters: XKCD-4892
                  ↓
┌─────────────────────────────────────┐
│  Readeck - Authorize Application    │
├─────────────────────────────────────┤
│                                     │
│  MyDeck Android                     │
│  github.com/user/mydeck-android     │
│                                     │
│  is requesting access to:           │
│                                     │
│  ✓ Read your bookmarks              │
│  ✓ Create and modify bookmarks      │
│  ✓ View your profile                │
│                                     │
│  [Deny]       [Authorize]           │
│                                     │
└─────────────────────────────────────┘
                  ↓
        User taps "Authorize"
                  ↓
┌─────────────────────────────────────┐
│  ✅ Device Authorized                │
├─────────────────────────────────────┤
│                                     │
│  You can now close this window and  │
│  return to your device.             │
│                                     │
└─────────────────────────────────────┘
```

### 8.3 Logout Flow

```
┌─────────────────────────────────────┐
│  Settings                           │
├─────────────────────────────────────┤
│                                     │
│  Account                            │
│  john@example.com                   │
│  readeck.example.com                │
│                                     │
│  [Sign Out]                         │
│                                     │
└─────────────────────────────────────┘
                  ↓
        User taps "Sign Out"
                  ↓
┌─────────────────────────────────────┐
│  Sign Out                           │
├─────────────────────────────────────┤
│                                     │
│  Are you sure you want to sign out? │
│                                     │
│  You'll need to authorize this app  │
│  again to access your bookmarks.    │
│                                     │
│  [Cancel]      [Sign Out]           │
│                                     │
└─────────────────────────────────────┘
                  ↓
        User taps "Sign Out"
                  ↓
        App calls revokeToken()
        Clear local credentials
                  ↓
        Navigate to login screen
```

---

## 9. Error Handling

### 9.1 Network Errors

**Scenario:** Network timeout during any API call

**Handling:**
- Show user-friendly error message
- Provide "Retry" button
- Log error for debugging
- Don't clear any in-progress state

**UI Message:**
```
"Network error. Please check your connection and try again."
[Retry]
```

### 9.2 OAuth Errors

#### authorization_pending
**Meaning:** User hasn't authorized yet
**Action:** Continue polling (normal state)
**UI:** Show "Waiting for authorization..."

#### slow_down
**Meaning:** Polling too frequently
**Action:** Increase polling interval by 5 seconds
**UI:** Continue showing "Waiting..." (transparent to user)

#### access_denied
**Meaning:** User denied the authorization request
**Action:** Stop polling, return to login screen
**UI Message:**
```
"Authorization was denied. Please try signing in again."
[OK]
```

#### expired_token
**Meaning:** Device code expired (typically 5 minutes)
**Action:** Stop polling, return to login screen
**UI Message:**
```
"Authorization code expired. Please try again."
[OK]
```

#### invalid_client
**Meaning:** Client registration failed or invalid client_id
**Action:** Retry client registration once, then fail
**UI Message:**
```
"Unable to connect to server. Please check your server URL."
[OK]
```

#### invalid_grant
**Meaning:** Device code is invalid or already used
**Action:** Stop polling, return to login screen
**UI Message:**
```
"Authorization failed. Please try signing in again."
[OK]
```

### 9.3 Server Errors (5xx)

**Scenario:** Server returns 500, 502, 503, etc.

**Handling:**
- Retry up to 3 times with exponential backoff
- If all retries fail, show error message

**UI Message:**
```
"Server error. Please try again later."
[Retry]
```

### 9.4 Invalid Server URL

**Scenario:** User enters invalid or unreachable URL

**Handling:**
- Validate URL format before making API calls
- Attempt connection to `/api/info` endpoint first
- Show clear error if server is unreachable

**UI Message:**
```
"Unable to connect to server. Please check your server URL."
[OK]
```

### 9.5 App Backgrounded During Polling

**Scenario:** User switches apps or locks screen during OAuth polling

**Handling:**
- Continue polling in background (WorkManager or foreground service)
- Show notification when authorization completes
- Resume UI state when app returns to foreground

**OR (simpler):**
- Pause polling when app backgrounds
- Resume polling when app foregrounds
- Respect remaining expiration time

### 9.6 Revocation Errors

**Scenario:** Token revocation fails during logout

**Handling:**
- Clear local credentials regardless of API response
- Log error for debugging
- Complete logout flow anyway (fail open)

**Reasoning:** Even if server-side revocation fails, user should still be able to log out locally.

---

## 10. Testing Strategy

### 10.1 Unit Tests

#### API Layer Tests
**File:** `ReadeckApiTest.kt`

Test cases:
- [ ] OAuth DTOs serialize correctly
- [ ] OAuth DTOs deserialize correctly
- [ ] Error DTOs parse correctly
- [ ] All endpoints have correct paths and HTTP methods

#### Use Case Tests
**File:** `OAuthDeviceAuthorizationUseCaseTest.kt`

Test cases:
- [ ] Client registration success
- [ ] Client registration failure handling
- [ ] Device authorization success
- [ ] Device authorization failure handling
- [ ] Token polling - authorization_pending
- [ ] Token polling - success
- [ ] Token polling - access_denied
- [ ] Token polling - expired_token
- [ ] Token polling - slow_down (interval adjustment)
- [ ] Token polling - network error retry logic

#### Repository Tests
**File:** `UserRepositoryImplTest.kt`

Test cases:
- [ ] Login initiates OAuth flow correctly
- [ ] Login handles success state
- [ ] Login handles failure states
- [ ] Logout revokes token
- [ ] Logout clears credentials
- [ ] Logout handles revocation errors gracefully

### 10.2 Integration Tests

#### OAuth Flow Test
**File:** `OAuthFlowIntegrationTest.kt`

Test full flow with mocked HTTP responses:
- [ ] Complete successful flow from start to token
- [ ] Handle user denial
- [ ] Handle expiration
- [ ] Handle network interruptions

### 10.3 UI Tests (Espresso/Compose)

**File:** `LoginScreenTest.kt`

Test cases:
- [ ] Login screen displays correctly
- [ ] "Sign In" button starts OAuth flow
- [ ] Device authorization dialog shows user code
- [ ] Device authorization dialog shows verification URI
- [ ] "Copy Code" button copies to clipboard
- [ ] "Copy URL" button copies to clipboard
- [ ] "Open in Browser" button launches browser
- [ ] Timer counts down correctly
- [ ] Success state navigates to main screen
- [ ] Error states display correct messages
- [ ] Cancel button returns to login

### 10.4 Manual Testing Checklist

#### Happy Path
- [ ] Enter server URL
- [ ] Tap "Sign In"
- [ ] Copy user code
- [ ] Open verification URL in browser
- [ ] Enter code and authorize
- [ ] Confirm app receives token and navigates to main screen

#### Error Scenarios
- [ ] Test with invalid server URL
- [ ] Test with unreachable server
- [ ] Test user denial in browser
- [ ] Test expiration (wait 5 minutes)
- [ ] Test network disconnection during polling
- [ ] Test app backgrounding during polling
- [ ] Test app kill during polling

#### Logout
- [ ] Test logout from settings
- [ ] Confirm token is revoked
- [ ] Confirm credentials are cleared
- [ ] Confirm navigation back to login

#### Edge Cases
- [ ] Test with very slow network
- [ ] Test with intermittent connectivity
- [ ] Test rapid login/cancel cycles
- [ ] Test on various Android versions
- [ ] Test on various screen sizes
- [ ] Test in dark mode
- [ ] Test with accessibility features (TalkBack, large text)

---

## 11. Migration Considerations

### 11.1 Existing Users

**Challenge:** Users who are currently logged in with old `/auth` tokens

**Solution:**
1. Check if stored token is valid by making an authenticated API call (e.g., `GET /profile`)
2. If token is valid, continue using it (OAuth tokens and old tokens may be compatible)
3. If token is invalid (401 response), force logout and require re-authentication

**Implementation:**
```kotlin
// In UserRepositoryImpl or app startup logic
suspend fun validateStoredToken() {
    val token = settingsDataStore.tokenFlow.first()
    if (token != null) {
        try {
            val response = readeckApi.userprofile()
            if (!response.isSuccessful) {
                // Token invalid, clear credentials
                logout()
            }
        } catch (e: Exception) {
            // Network error, assume token is valid for now
            Timber.w("Unable to validate token: ${e.message}")
        }
    }
}
```

### 11.2 Data Migration

**No data migration needed** - OAuth tokens use the same Bearer authentication scheme, so:
- `AuthInterceptor` continues to work unchanged
- Token storage in `SettingsDataStore` continues to work unchanged
- Only the token acquisition method changes

### 11.3 Deprecation Plan

1. **Version 1.x** (Current)
   - Uses `/auth` endpoint

2. **Version 2.0** (This implementation)
   - Uses OAuth Device Code Grant
   - Marks old authentication code as deprecated
   - Keeps old code for reference

3. **Version 2.1** (Future)
   - Remove all deprecated authentication code
   - Clean up unused DTOs

---

## 12. Security Considerations

### 12.1 Token Storage

**Current Implementation:**
- Tokens stored via `SettingsDataStore` backed by `EncryptedSharedPreferences` (using `EncryptionHelper`)
- Uses AES256_SIV for key encryption and AES256_GCM for value encryption via AndroidX Security
- Master key generated via `MasterKeys.AES256_GCM_SPEC`
- `TokenManager` (singleton) subscribes to `settingsDataStore.tokenFlow` and caches the token in a `@Volatile` field for synchronous access by `AuthInterceptor`

**Recommendation:** Continue using current storage - no changes needed. The encrypted storage is already secure.

### 12.2 Client Secret

OAuth Device Code Grant **does not require a client secret** - this is by design for public clients (mobile apps). The PKCE mechanism is not used in Device Code Grant; instead, the device code itself acts as proof of authorization.

**No action needed** - implementation is secure as specified.

### 12.3 HTTPS Enforcement

**Recommendation:**
- Warn users if they enter an `http://` URL (non-encrypted)
- Allow it (for self-hosted dev environments) but show warning

**Implementation:**
```kotlin
fun validateServerUrl(url: String): ValidationResult {
    return when {
        !url.startsWith("http://") && !url.startsWith("https://") ->
            ValidationResult.Invalid("URL must start with http:// or https://")
        url.startsWith("http://") && !url.contains("localhost") && !url.contains("127.0.0.1") ->
            ValidationResult.Warning("Using unencrypted connection. Your data may be vulnerable.")
        else ->
            ValidationResult.Valid
    }
}
```

### 12.4 Token Expiration

The Readeck API does not return an `expires_in` field in the token response. Tokens are long-lived and do not expire unless explicitly revoked via `POST /oauth/revoke`.

**Current Implementation:** Tokens don't expire unless revoked. The `AuthInterceptor` handles 401 responses by showing a notification to the user.
**Future Enhancement:** Implement token refresh flow if Readeck adds refresh token support.

---

## 13. Future Enhancements

### 13.1 Authorization Code Grant

Add support for Authorization Code Grant for users who prefer in-app browser flow.

**Benefits:**
- Faster UX (no code entry needed)
- More familiar to users

**Requirements:**
- Implement deep linking
- Handle browser redirects
- Add PKCE implementation

### 13.2 Biometric Authentication

Add biometric authentication (fingerprint, face unlock) for accessing stored tokens.

**Benefits:**
- Additional security layer
- Better UX (no re-auth needed)

**Requirements:**
- Use Android BiometricPrompt API
- Encrypt token with biometric key
- Handle biometric changes (fingerprint added/removed)

### 13.3 Multiple Accounts

Support multiple Readeck accounts/instances.

**Benefits:**
- Users can have personal + work instances
- Easy switching between accounts

**Requirements:**
- Account management UI
- Update data storage to support multiple tokens
- Handle account switching

### 13.4 Token Refresh

If Readeck adds refresh token support, implement automatic token refresh.

**Benefits:**
- Better security (short-lived access tokens)
- Automatic re-authentication without user interaction

**Requirements:**
- Detect token expiration
- Call refresh token endpoint
- Update stored token

---

## 14. Appendix

### 14.1 Key Constants

```kotlin
object OAuthConstants {
    const val GRANT_TYPE_DEVICE_CODE = "urn:ietf:params:oauth:grant-type:device_code"
    const val REQUIRED_SCOPES = "bookmarks:read bookmarks:write profile:read"
    const val CLIENT_NAME = "MyDeck Android"
    const val CLIENT_URI = "https://github.com/NateEaton/mydeck-android"
    const val SOFTWARE_ID = "com.mydeck.app"
    const val DEFAULT_POLLING_INTERVAL_SECONDS = 5
    const val SLOW_DOWN_ADDITIONAL_INTERVAL_SECONDS = 5
    const val MAX_POLLING_RETRIES = 60 // 5 minutes with 5-second intervals
}
```

### 14.2 Error Codes Reference

Per the API spec `oauthError` enum, the full set of possible error codes:

| Error Code | Meaning | Action |
|------------|---------|--------|
| `authorization_pending` | User hasn't authorized yet | Continue polling |
| `slow_down` | Polling too fast | Increase interval by 5s, continue polling |
| `access_denied` | User denied request | Stop, show error |
| `expired_token` | Code expired (5 min timeout) | Stop, show error |
| `invalid_client` | Invalid client_id | Restart flow |
| `invalid_client_metadata` | Invalid client registration data | Fix request and retry |
| `invalid_grant` | Invalid device_code | Restart flow |
| `invalid_redirect_uri` | Invalid redirect URI (N/A for device flow) | Restart flow |
| `invalid_request` | Malformed request | Fix and retry |
| `invalid_scope` | Requested scope not valid | Fix scope and retry |
| `server_error` | Server-side error | Retry with backoff |
| `unauthorized_client` | Client not authorized for grant type | Restart flow with correct grant_types |

### 14.3 API Reference Links

- OAuth 2.0 Device Authorization Grant (RFC 8628): https://www.rfc-editor.org/rfc/rfc8628
- OAuth 2.0 Dynamic Client Registration (RFC 7591): https://www.rfc-editor.org/rfc/rfc7591
- Readeck API Documentation: `{server_url}/api/doc` (after deployment)

---

## 15. Questions for Product/Design

1. **UX Decision:** Should we use a dialog or full-screen for device authorization UI?
   - **Recommendation:** Full-screen for better visibility of instructions

2. **Copy Behavior:** Should copying code/URL show a toast notification?
   - **Recommendation:** Yes - "Code copied to clipboard"

3. **Browser Launch:** Should "Open in Browser" auto-paste the code?
   - **Recommendation:** Use `verification_uri_complete` to pre-fill code

4. **Expiration Countdown:** Show timer in MM:SS or just "Expires in X minutes"?
   - **Recommendation:** "Expires in 4:32" for precision

5. **Logout Confirmation:** Always show confirmation or add "Don't ask again"?
   - **Recommendation:** Always confirm (security best practice)

6. **Server URL Memory:** Remember last successful server URL?
   - **Recommendation:** Yes - pre-fill on login screen

---

## 16. Success Criteria

This implementation will be considered successful when:

1. ✅ Users can authenticate using OAuth Device Code Grant flow
2. ✅ Full OAuth flow completes successfully with Readeck 0.22+
3. ✅ All error scenarios are handled gracefully
4. ✅ UI is intuitive and clear for non-technical users
5. ✅ App passes all unit, integration, and UI tests
6. ✅ No crashes or ANRs during authentication flow
7. ✅ Logout properly revokes tokens
8. ✅ App works on Android 8.0+ (minimum SDK version)
9. ✅ All strings are localized per CLAUDE.md requirements
10. ✅ Code follows existing app architecture and patterns

---

## 17. Timeline Estimate

| Phase | Tasks | Estimated Time |
|-------|-------|----------------|
| Phase 1 | API Layer | 1-2 days |
| Phase 2 | Domain Layer | 2-3 days |
| Phase 3 | UI/UX Layer | 3-4 days |
| Phase 4 | Localization | 1 day |
| Phase 5 | Testing & Polish | 2-3 days |
| Phase 6 | Documentation | 1 day |
| **Total** | | **10-14 days** |

**Assumptions:**
- Developer is familiar with Kotlin, Coroutines, Retrofit, and Android development
- Developer has access to Readeck 0.22+ instance for testing
- No major blockers or scope changes

---

## 18. Sign-Off

**Prepared by:** Claude
**Date:** 2026-02-11

**Approval Required From:**
- [ ] Technical Lead
- [ ] Product Owner
- [ ] QA Lead
- [ ] UX Designer

**Status:** Draft - Ready for Review

---

## 19. Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-11 | Claude | Initial draft |
| 1.1 | 2026-02-11 | Claude | Revision pass: fixed DTOs to match OpenAPI spec (removed `client_id_issued_at`, `expires_in`; added `id` to token response; fixed `grant_types` type in response); added `/info` feature detection step; added `clientId` to `OAuthDeviceAuthorizationState`; reconciled with existing `AccountSettingsViewModel`/`AuthenticateUseCase` architecture; fixed token storage description (EncryptedSharedPreferences); added all error codes from API spec; added `client_uri` HTTPS constraint; added `profile:read` scope nuance; added `SlowDown` to `TokenPollResult` |

---

*End of Technical Specification*
