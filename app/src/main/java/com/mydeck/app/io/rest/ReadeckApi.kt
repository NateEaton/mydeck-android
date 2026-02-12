package com.mydeck.app.io.rest

import com.mydeck.app.io.rest.model.AuthenticationRequestDto
import com.mydeck.app.io.rest.model.AuthenticationResponseDto
import com.mydeck.app.io.rest.model.BookmarkDto
import com.mydeck.app.io.rest.model.UserProfileDto
import com.mydeck.app.io.rest.model.CreateBookmarkDto
import com.mydeck.app.io.rest.model.StatusMessageDto
import com.mydeck.app.io.rest.model.EditBookmarkDto
import com.mydeck.app.io.rest.model.EditBookmarkErrorDto
import com.mydeck.app.io.rest.model.EditBookmarkResponseDto
import com.mydeck.app.io.rest.model.SyncContentRequestDto
import com.mydeck.app.io.rest.model.SyncStatusDto
import com.mydeck.app.io.rest.model.OAuthClientRegistrationRequestDto
import com.mydeck.app.io.rest.model.OAuthClientRegistrationResponseDto
import com.mydeck.app.io.rest.model.OAuthDeviceAuthorizationRequestDto
import com.mydeck.app.io.rest.model.OAuthDeviceAuthorizationResponseDto
import com.mydeck.app.io.rest.model.OAuthTokenRequestDto
import com.mydeck.app.io.rest.model.OAuthTokenResponseDto
import com.mydeck.app.io.rest.model.OAuthRevokeRequestDto
import com.mydeck.app.io.rest.model.ServerInfoDto
import kotlinx.datetime.Instant
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Query
import retrofit2.http.DELETE
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.Path

interface ReadeckApi {
    @GET("bookmarks")
    suspend fun getBookmarks(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("updated_since") updatedSince: Instant?,
        @Query("sort") sortOrder: SortOrder
    ): Response<List<BookmarkDto>>


    @GET("bookmarks/sync")
    suspend fun getSyncStatus(
        @Query("since") since: String?  // ISO 8601 formatted timestamp
    ): Response<List<SyncStatusDto>>

    @POST("bookmarks/sync")
    suspend fun syncContent(
        @Body body: SyncContentRequestDto
    ): Response<List<BookmarkDto>>

    @GET("info")
    suspend fun getInfo(): Response<ServerInfoDto>

    @Deprecated("Use OAuth Device Code Grant flow instead")
    @POST("auth")
    suspend fun authenticate(
        @Body body: AuthenticationRequestDto
    ): Response<AuthenticationResponseDto>

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

    @GET("profile")
    suspend fun userprofile(): Response<UserProfileDto>

    @Headers("Content-Type: text/html")
    @GET("bookmarks/{id}/article")
    suspend fun getArticle(@Path("id") id: String): Response<String>

    @GET("bookmarks/{id}")
    suspend fun getBookmarkById(@Path("id") id: String): Response<BookmarkDto>

    @POST("bookmarks")
    suspend fun createBookmark(
        @Body body: CreateBookmarkDto
    ): Response<StatusMessageDto>

    @Headers("Accept: application/json")
    @PATCH("bookmarks/{id}")
    suspend fun editBookmark(
        @Path("id") id: String,
        @Body body: EditBookmarkDto
    ): Response<EditBookmarkResponseDto>

    @Headers("Accept: application/json")
    @DELETE("bookmarks/{id}")
    suspend fun deleteBookmark(@Path("id") id: String): Response<Unit>

    data class SortOrder(val sort: Sort, val order: Order = Order.Ascending) {
        override fun toString(): String {
            return "${order.value}${sort.value}"
        }
    }

    sealed class Sort(val value: String) {
        data object Created: Sort("created")
        data object Title: Sort("title")
        data object Domain: Sort("domain")
        data object Duration: Sort("duration")
        data object Published: Sort("published")
        data object Site: Sort("site")
    }

    sealed class Order(val value: String) {
        data object Ascending: Order("")
        data object Descending: Order("-")
    }

    interface Header {
        companion object {
            const val TOTAL_PAGES = "total-pages"
            const val TOTAL_COUNT = "total-count"
            const val CURRENT_PAGE = "current-page"
            const val BOOKMARK_ID = "bookmark-id"
        }
    }
}
