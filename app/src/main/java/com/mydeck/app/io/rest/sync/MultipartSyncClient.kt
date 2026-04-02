package com.mydeck.app.io.rest.sync

import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.SyncRequest
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Shared client for fetching bookmark data via POST /bookmarks/sync.
 *
 * Handles batching, multipart response parsing, and temp file management.
 */
class MultipartSyncClient @Inject constructor(
    private val readeckApi: ReadeckApi,
    private val json: Json,
    private val tempDir: File
) {

    sealed class Result {
        data class Success(val packages: List<BookmarkSyncPackage>) : Result()
        data class Error(val message: String, val code: Int? = null) : Result()
        data class NetworkError(val message: String, val exception: IOException) : Result()
    }

    /**
     * Fetch metadata-only (JSON) for the given bookmark IDs.
     */
    suspend fun fetchMetadata(bookmarkIds: List<String>): Result {
        return fetchBatched(bookmarkIds, METADATA_BATCH_SIZE) { batch ->
            SyncRequest(
                id = batch,
                withJson = true,
                withHtml = false,
                withResources = false
            )
        }
    }

    /**
     * Fetch HTML-only (no JSON metadata or resources) for the given bookmark IDs.
     * Used for lightweight annotation refresh where only the article HTML changed.
     *
     * @param hasResources When true, sends resourcePrefix="." so the server returns relative
     *   image URLs (for bookmarks with downloaded images). When false, omits resourcePrefix
     *   so the server returns absolute URLs (for text-only bookmarks where images load from network).
     */
    suspend fun fetchHtmlOnly(bookmarkIds: List<String>, hasResources: Boolean = true): Result {
        return fetchBatched(bookmarkIds, CONTENT_BATCH_SIZE) { batch ->
            SyncRequest(
                id = batch,
                withJson = false,
                withHtml = true,
                withResources = false,
                resourcePrefix = if (hasResources) "." else null
            )
        }
    }

    /**
     * Fetch text content (JSON + HTML, no resources) for the given bookmark IDs.
     * Used when image downloads are disabled; server returns absolute image URLs.
     */
    suspend fun fetchTextOnly(bookmarkIds: List<String>): Result {
        return fetchBatched(bookmarkIds, CONTENT_BATCH_SIZE) { batch ->
            SyncRequest(
                id = batch,
                withJson = true,
                withHtml = true,
                withResources = false
                // resourcePrefix intentionally omitted — server returns absolute image URLs
            )
        }
    }

    /**
     * Fetch full content packages (JSON + HTML + resources) for the given bookmark IDs.
     */
    suspend fun fetchContentPackages(bookmarkIds: List<String>): Result {
        return fetchBatched(bookmarkIds, CONTENT_BATCH_SIZE) { batch ->
            SyncRequest(
                id = batch,
                withJson = true,
                withHtml = true,
                withResources = true,
                resourcePrefix = "."
            )
        }
    }

    private suspend fun fetchBatched(
        bookmarkIds: List<String>,
        batchSize: Int,
        requestFactory: (List<String>) -> SyncRequest
    ): Result {
        if (bookmarkIds.isEmpty()) {
            return Result.Success(emptyList())
        }

        val allPackages = mutableListOf<BookmarkSyncPackage>()
        val batches = bookmarkIds.chunked(batchSize)

        for ((index, batch) in batches.withIndex()) {
            Timber.d("Fetching batch ${index + 1}/${batches.size}, size=${batch.size}")

            val batchResult = fetchSingle(requestFactory(batch))
            when (batchResult) {
                is Result.Success -> allPackages.addAll(batchResult.packages)
                is Result.Error -> {
                    Timber.w("Batch ${index + 1} failed: ${batchResult.message}")
                    return batchResult
                }
                is Result.NetworkError -> return batchResult
            }
        }

        return Result.Success(allPackages)
    }

    private suspend fun fetchSingle(request: SyncRequest): Result {
        return try {
            val response = readeckApi.syncBookmarks(request)
            if (!response.isSuccessful) {
                return Result.Error(
                    message = "HTTP ${response.code()}",
                    code = response.code()
                )
            }

            val body = response.body()
                ?: return Result.Error("Empty response body")

            val contentType = response.headers()["Content-Type"] ?: ""
            val boundary = extractBoundary(contentType)
                ?: return Result.Error("Missing or invalid multipart boundary in Content-Type: $contentType")

            body.use {
                val parser = MultipartSyncParser(json, tempDir)
                val packages = parser.parse(body.source(), boundary)
                Result.Success(packages)
            }
        } catch (e: IOException) {
            Timber.w(e, "Network error during multipart sync")
            Result.NetworkError("Network error: ${e.message}", e)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during multipart sync")
            Result.Error("Unexpected error: ${e.message}")
        }
    }

    companion object {
        const val METADATA_BATCH_SIZE = 50
        const val CONTENT_BATCH_SIZE = 10

        /**
         * Extract the boundary parameter from a multipart Content-Type header.
         * Example: "multipart/mixed; boundary=abc123" → "abc123"
         */
        fun extractBoundary(contentType: String): String? {
            if (!contentType.contains("multipart", ignoreCase = true)) return null
            val boundaryParam = contentType.split(';')
                .map { it.trim() }
                .firstOrNull { it.startsWith("boundary=", ignoreCase = true) }
                ?: return null
            return boundaryParam.substringAfter('=').trim('"')
        }
    }
}
