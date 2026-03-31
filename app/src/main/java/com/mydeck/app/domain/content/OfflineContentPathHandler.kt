package com.mydeck.app.domain.content

import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.net.URLConnection

/**
 * WebViewAssetLoader.PathHandler that serves files from the offline content
 * directory for a specific bookmark.
 *
 * Registered for paths like /<bookmarkId>/<resource-path> and maps them
 * to files/offline_content/<bookmarkId>/<resource-path>.
 */
class OfflineContentPathHandler(
    private val offlineContentDir: File
) : WebViewAssetLoader.PathHandler {

    override fun handle(path: String): WebResourceResponse? {
        // path is everything after the registered path prefix, e.g. "bookmarkId/image.jpeg"
        
        val file = File(offlineContentDir, path)

        if (!file.exists() || !file.isFile) {
            return null // Fall through to network
        }

        // Security: ensure the resolved path is still under offlineContentDir
        val canonicalBase = offlineContentDir.canonicalPath
        val canonicalFile = file.canonicalPath
        if (!canonicalFile.startsWith(canonicalBase)) {
            Timber.w("Path traversal attempt blocked: $path")
            return null
        }

        val mimeType = guessMimeType(file.name)
        
        return try {
            WebResourceResponse(mimeType, null, FileInputStream(file))
        } catch (e: Exception) {
            Timber.w(e, "Failed to serve offline content: $path")
            null
        }
    }

    companion object {
        const val OFFLINE_HOST = "offline.mydeck.local"
        const val OFFLINE_PATH_PREFIX = "/"

        fun buildBaseUrl(bookmarkId: String): String {
            return "https://$OFFLINE_HOST/$bookmarkId/"
        }

        private fun guessMimeType(filename: String): String {
            val ext = filename.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "html", "htm" -> "text/html"
                "css" -> "text/css"
                "js" -> "application/javascript"
                "json" -> "application/json"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "svg" -> "image/svg+xml"
                "avif" -> "image/avif"
                "ico" -> "image/x-icon"
                else -> URLConnection.guessContentTypeFromName(filename) ?: "application/octet-stream"
            }
        }
    }
}
