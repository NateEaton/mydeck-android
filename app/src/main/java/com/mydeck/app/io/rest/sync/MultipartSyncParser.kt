package com.mydeck.app.io.rest.sync

import com.mydeck.app.io.rest.model.BookmarkDto
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.BufferedSource
import timber.log.Timber
import java.io.File

/**
 * Streaming parser for multipart/mixed responses from POST /bookmarks/sync.
 *
 * Reads parts one at a time from the Okio source, routing each by its Type header:
 * - json → deserialize to BookmarkDto
 * - html → read as UTF-8 string
 * - resource → stream to a temporary file on disk
 * - unknown → skip and warn
 *
 * Parts are grouped by Bookmark-Id into [BookmarkSyncPackage] results.
 */
class MultipartSyncParser(
    private val json: Json,
    private val tempDir: File
) {
    /**
     * Parse a multipart/mixed response body.
     *
     * @param source the response body as a BufferedSource
     * @param boundary the MIME boundary string (without leading --)
     * @return list of assembled per-bookmark packages
     */
    fun parse(source: BufferedSource, boundary: String): List<BookmarkSyncPackage> {
        val dashBoundary = "--$boundary"
        val closeBoundary = "--$boundary--"

        val builders = mutableMapOf<String, PackageBuilder>()

        // Skip preamble: read until the first boundary line
        skipToBoundary(source, dashBoundary) ?: return finalize(builders)

        while (!source.exhausted()) {
            val part = readPart(source, dashBoundary, closeBoundary) ?: break

            val bookmarkId = part.headers["bookmark-id"]
            if (bookmarkId.isNullOrBlank()) {
                Timber.w("Multipart part missing Bookmark-Id header, skipping")
                part.cleanup()
                continue
            }

            val builder = builders.getOrPut(bookmarkId) { PackageBuilder(bookmarkId) }
            val type = part.headers["type"]?.lowercase()

            when (type) {
                "json" -> {
                    try {
                        val dto = json.decodeFromString<BookmarkDto>(part.textContent!!)
                        builder.json = dto
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to deserialize JSON for bookmark $bookmarkId")
                        builder.warnings.add("JSON parse error: ${e.message}")
                    }
                }
                "html" -> {
                    builder.html = part.textContent
                }
                "resource" -> {
                    val path = part.headers["path"]
                    if (path.isNullOrBlank() || part.fileContent == null) {
                        Timber.w("Resource part missing Path or file content for bookmark $bookmarkId")
                        builder.warnings.add("Resource skipped: missing path or content")
                        part.cleanup()
                    } else {
                        builder.resources.add(
                            ResourcePart(
                                path = path,
                                filename = part.headers["filename"] ?: path,
                                mimeType = part.headers["content-type"] ?: "application/octet-stream",
                                group = part.headers["group"],
                                tempFile = part.fileContent,
                                byteSize = part.fileContent.length()
                            )
                        )
                    }
                }
                else -> {
                    Timber.w("Unknown multipart Type '$type' for bookmark $bookmarkId, skipping")
                    builder.warnings.add("Unknown type skipped: $type")
                    part.cleanup()
                }
            }
        }

        return finalize(builders)
    }

    private fun finalize(builders: Map<String, PackageBuilder>): List<BookmarkSyncPackage> {
        return builders.values.map { it.build() }
    }

    /**
     * Skip lines until we find one that starts with [dashBoundary].
     * Returns the line found, or null if the source is exhausted.
     */
    private fun skipToBoundary(source: BufferedSource, dashBoundary: String): String? {
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: return null
            if (line.startsWith(dashBoundary)) return line
        }
        return null
    }

    /**
     * Read a single multipart part: headers + body.
     * Returns null if we hit the closing boundary or the stream ends.
     */
    private fun readPart(
        source: BufferedSource,
        dashBoundary: String,
        closeBoundary: String
    ): ParsedPart? {
        // Read headers until blank line
        val headers = mutableMapOf<String, String>()
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: return null
            if (line.isBlank()) break
            val colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                val key = line.substring(0, colonIdx).trim().lowercase()
                val value = line.substring(colonIdx + 1).trim()
                headers[key] = value
            }
        }

        if (headers.isEmpty()) return null

        val type = headers["type"]?.lowercase()
        val bookmarkId = headers["bookmark-id"] ?: "unknown"
        val isResource = type == "resource"

        // Read body until we hit the next boundary
        return if (isResource) {
            readResourceBody(source, dashBoundary, closeBoundary, headers, bookmarkId)
        } else {
            readTextBody(source, dashBoundary, closeBoundary, headers)
        }
    }

    private fun readTextBody(
        source: BufferedSource,
        dashBoundary: String,
        closeBoundary: String,
        headers: Map<String, String>
    ): ParsedPart {
        val textBuilder = StringBuilder()
        var hitClose = false

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.startsWith(dashBoundary)) {
                hitClose = line.startsWith(closeBoundary)
                break
            }
            if (textBuilder.isNotEmpty()) textBuilder.append('\n')
            textBuilder.append(line)
        }

        // Trim trailing whitespace from the body (multipart parts have a trailing CRLF before boundary)
        val text = textBuilder.toString().trimEnd()

        return ParsedPart(
            headers = headers,
            textContent = text,
            fileContent = null,
            isClosingBoundary = hitClose
        )
    }

    private fun readResourceBody(
        source: BufferedSource,
        dashBoundary: String,
        closeBoundary: String,
        headers: Map<String, String>,
        bookmarkId: String
    ): ParsedPart {
        val filename = headers["filename"] ?: headers["path"] ?: "resource"
        val safeFilename = filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val tempFile = File(tempDir, "${bookmarkId}_${safeFilename}_${System.nanoTime()}")

        // We need to stream the body to disk, but we must detect the boundary.
        // Multipart boundaries appear on their own line, preceded by CRLF.
        // We read line-by-line but write binary. For binary resources, we use a
        // buffered approach that peeks for boundaries.
        val boundaryBytes = "\n$dashBoundary".toByteArray(Charsets.UTF_8)
        val closeBoundaryBytes = "\n$closeBoundary".toByteArray(Charsets.UTF_8)

        var hitClose = false
        val bodyBuffer = Buffer()

        // Read the entire body between boundaries into a buffer, then write to file.
        // This is necessary because we can't reliably detect boundaries in a binary
        // stream without buffering. The content-length header tells us how much data
        // to expect, but we still need to handle the boundary detection.
        val contentLength = headers["content-length"]?.toLongOrNull()

        if (contentLength != null && contentLength > 0) {
            // Fast path: we know exactly how many bytes to read
            val bytesRead = readExactBytes(source, bodyBuffer, contentLength)
            if (bytesRead < contentLength) {
                Timber.w("Resource body truncated: expected $contentLength bytes, got $bytesRead")
            }

            // Skip past trailing CRLF and boundary line
            skipToNextBoundary(source, dashBoundary, closeBoundary).also { hitClose = it }
        } else {
            // Slow path: no content-length, read line-by-line accumulating into buffer
            // This works but is less efficient for binary data
            var firstLine = true
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith(dashBoundary)) {
                    hitClose = line.startsWith(closeBoundary)
                    break
                }
                if (!firstLine) bodyBuffer.writeUtf8("\n")
                bodyBuffer.writeUtf8(line)
                firstLine = false
            }
        }

        // Write to temp file
        try {
            tempFile.parentFile?.mkdirs()
            tempFile.outputStream().use { out ->
                bodyBuffer.copyTo(out)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to write resource to temp file: ${tempFile.absolutePath}")
            tempFile.delete()
            return ParsedPart(
                headers = headers,
                textContent = null,
                fileContent = null,
                isClosingBoundary = hitClose
            )
        }

        return ParsedPart(
            headers = headers,
            textContent = null,
            fileContent = tempFile,
            isClosingBoundary = hitClose
        )
    }

    /**
     * Read exactly [count] bytes from [source] into [sink].
     * Returns number of bytes actually read.
     */
    private fun readExactBytes(source: BufferedSource, sink: Buffer, count: Long): Long {
        var remaining = count
        while (remaining > 0 && !source.exhausted()) {
            val read = source.read(sink, remaining)
            if (read == -1L) break
            remaining -= read
        }
        return count - remaining
    }

    /**
     * After reading a content-length-sized body, skip past any trailing CRLF
     * and the boundary line. Returns true if the closing boundary was hit.
     */
    private fun skipToNextBoundary(
        source: BufferedSource,
        dashBoundary: String,
        closeBoundary: String
    ): Boolean {
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: return false
            if (line.startsWith(closeBoundary)) return true
            if (line.startsWith(dashBoundary)) return false
        }
        return false
    }

    private class ParsedPart(
        val headers: Map<String, String>,
        val textContent: String?,
        val fileContent: File?,
        val isClosingBoundary: Boolean
    ) {
        fun cleanup() {
            fileContent?.delete()
        }
    }

    private class PackageBuilder(val bookmarkId: String) {
        var json: BookmarkDto? = null
        var html: String? = null
        val resources = mutableListOf<ResourcePart>()
        val warnings = mutableListOf<String>()

        fun build() = BookmarkSyncPackage(
            bookmarkId = bookmarkId,
            json = json,
            html = html,
            resources = resources.toList(),
            parseWarnings = warnings.toList()
        )
    }
}
