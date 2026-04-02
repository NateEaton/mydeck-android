package com.mydeck.app.util

import android.content.Context
import com.mydeck.app.BuildConfig
import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.model.Bookmark
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookmarkDebugExporter(
    private val context: Context,
    private val bookmarkRepository: BookmarkRepository,
    private val json: Json
) {
    data class ExportResult(
        val file: File,
        val success: Boolean,
        val errorMessage: String? = null
    )

    suspend fun exportBookmarkDebugJson(bookmarkId: String): ExportResult {
        return try {
            val bookmark = bookmarkRepository.getBookmarkById(bookmarkId)

            // Fetch fresh data from API
            val rawApiJson = bookmarkRepository.fetchRawBookmarkJson(bookmarkId)
            val rawArticleHtml = bookmarkRepository.fetchRawArticleHtml(bookmarkId)

            // Build the comprehensive JSON
            val debugJson = buildDebugJson(
                bookmark = bookmark,
                rawApiJson = rawApiJson,
                rawArticleHtml = rawArticleHtml
            )

            // Pretty-print the JSON
            val prettyJson = Json { prettyPrint = true }
            val parsed = json.parseToJsonElement(debugJson)
            val prettyOutput = prettyJson.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), parsed)

            // Write to file
            val timestamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
            val sanitizedTitle = bookmark.title.take(30)
                .replace(Regex("[^a-zA-Z0-9]"), "_")
                .replace(Regex("_+"), "_")
                .trimEnd('_')
            val fileName = "mydeck-debug-${sanitizedTitle}-$timestamp.json"
            val file = File(context.cacheDir, fileName)
            file.writeText(prettyOutput)

            Timber.d("Exported debug JSON: ${file.absolutePath} (${file.length()} bytes)")
            ExportResult(file = file, success = true)
        } catch (e: Exception) {
            Timber.e(e, "Failed to export bookmark debug JSON")
            ExportResult(
                file = File(context.cacheDir, "error.json"),
                success = false,
                errorMessage = e.message
            )
        }
    }

    private fun buildDebugJson(
        bookmark: Bookmark,
        rawApiJson: String?,
        rawArticleHtml: String?
    ): String {
        val root = buildJsonObject {
            putJsonObject("exportInfo") {
                put("exportTimestamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date()))
                put("appVersion", BuildConfig.VERSION_NAME)
                put("appVersionCode", BuildConfig.VERSION_CODE)
                put("buildType", BuildConfig.BUILD_TYPE)
                put("apiDataAvailable", rawApiJson != null)
                put("articleHtmlAvailable", rawArticleHtml != null)
            }

            // Raw API response (parsed back to JsonElement for proper nesting)
            if (rawApiJson != null) {
                put("apiResponse", json.parseToJsonElement(rawApiJson))
            } else {
                put("apiResponse", JsonPrimitive("API fetch failed or unavailable"))
            }

            putJsonObject("localState") {
                put("bookmarkId", bookmark.id)
                put("contentState", bookmark.contentState.name)
                put("contentFailureReason", bookmark.contentFailureReason ?: "none")
                put("hasLocalArticleContent", bookmark.articleContent != null)
                put("localArticleContentLength", bookmark.articleContent?.length ?: 0)
                put("readProgress", bookmark.readProgress)
                put("isMarked", bookmark.isMarked)
                put("isArchived", bookmark.isArchived)
                put("isDeleted", bookmark.isDeleted)
            }

            putJsonObject("bookmarkMetadata") {
                put("id", bookmark.id)
                put("title", bookmark.title)
                put("url", bookmark.url)
                put("href", bookmark.href)
                put("site", bookmark.site)
                put("siteName", bookmark.siteName)
                put("state", bookmark.state.name)
                put("loaded", bookmark.loaded)
                put("type", when (bookmark.type) {
                    is Bookmark.Type.Article -> "article"
                    is Bookmark.Type.Picture -> "photo"
                    is Bookmark.Type.Video -> "video"
                })
                put("documentType", bookmark.documentTpe)
                put("hasArticle", bookmark.hasArticle)
                put("lang", bookmark.lang)
                put("textDirection", bookmark.textDirection)
                put("wordCount", bookmark.wordCount ?: 0)
                put("readingTime", bookmark.readingTime ?: 0)
                put("readProgress", bookmark.readProgress)
                put("created", bookmark.created.toString())
                put("updated", bookmark.updated.toString())
                put("published", bookmark.published?.toString() ?: "null")
                put("description", bookmark.description)
                put("embed", bookmark.embed ?: "null")
                put("embedHostname", bookmark.embedHostname ?: "null")
                putJsonArray("authors") {
                    bookmark.authors.forEach { add(JsonPrimitive(it)) }
                }
                putJsonArray("labels") {
                    bookmark.labels.forEach { add(JsonPrimitive(it)) }
                }
                putJsonObject("resources") {
                    putJsonObject("article") { put("src", bookmark.article.src) }
                    putJsonObject("icon") {
                        put("src", bookmark.icon.src)
                        put("width", bookmark.icon.width)
                        put("height", bookmark.icon.height)
                    }
                    putJsonObject("image") {
                        put("src", bookmark.image.src)
                        put("width", bookmark.image.width)
                        put("height", bookmark.image.height)
                    }
                    putJsonObject("thumbnail") {
                        put("src", bookmark.thumbnail.src)
                        put("width", bookmark.thumbnail.width)
                        put("height", bookmark.thumbnail.height)
                    }
                    putJsonObject("log") { put("src", bookmark.log.src) }
                    putJsonObject("props") { put("src", bookmark.props.src) }
                }
            }

            // Article HTML content (from API, fresh fetch)
            if (rawArticleHtml != null) {
                put("articleHtml", JsonPrimitive(rawArticleHtml))
            }

            // Local article content (from database, may differ from API)
            if (bookmark.articleContent != null) {
                put("localArticleHtml", JsonPrimitive(bookmark.articleContent))
            }
        }

        return json.encodeToString(JsonObject.serializer(), root)
    }
}
