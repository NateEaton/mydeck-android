package com.mydeck.app.io.rest.sync

import com.mydeck.app.io.rest.model.BookmarkDto
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.toByteString
import timber.log.Timber
import java.io.File
import java.io.OutputStream

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
        val contentLength = headers["content-length"]?.toLongOrNull()
        var hitClose = false

        try {
            tempFile.parentFile?.mkdirs()
            tempFile.outputStream().use { out ->
                if (contentLength != null && contentLength > 0) {
                    // Stream exactly Content-Length bytes to file in chunks
                    var remaining = contentLength
                    val chunk = Buffer()
                    while (remaining > 0 && !source.exhausted()) {
                        val toRead = minOf(remaining, 8192L)
                        val read = source.read(chunk, toRead)
                        if (read == -1L) break
                        out.write(chunk.readByteArray(read))
                        remaining -= read
                    }
                    if (remaining > 0) {
                        Timber.w("Resource body truncated: expected $contentLength bytes, remaining=$remaining")
                    }
                    // After body, consume CRLF and the boundary line
                    hitClose = skipToNextBoundary(source, dashBoundary, closeBoundary)
                } else {
                    // No content-length: binary-safe streaming until boundary
                    hitClose = streamUntilBoundary(source, dashBoundary, out)
                }
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

    /**
     * Binary-safe streaming of a part body until the next boundary line is encountered.
     * Writes bytes to [out] while holding back a small tail window to detect "\n--boundary".
     * Returns true if the closing boundary ("--boundary--") was encountered.
     */
    private fun streamUntilBoundary(
        source: BufferedSource,
        dashBoundary: String,
        out: OutputStream
    ): Boolean {
        val boundaryPrefix: ByteString = "\n$dashBoundary".toByteArray(Charsets.UTF_8).toByteString()
        val tail = Buffer() // keep last boundaryPrefix.size + 1 bytes (for optional '\r')
        val outBuffer = Buffer()

        fun flushOutBuffer() {
            if (outBuffer.size > 0) {
                out.write(outBuffer.readByteArray())
            }
        }

        while (!source.exhausted()) {
            val b: Byte = try { source.readByte() } catch (e: Exception) { break }
            tail.writeByte(b.toInt())

            // Keep tail bounded to boundaryPrefix.size + 1
            while (tail.size > boundaryPrefix.size + 1) {
                outBuffer.writeByte(tail.readByte().toInt())
                if (outBuffer.size >= 8192) flushOutBuffer()
            }

            val snapshot = tail.snapshot()
            val size = snapshot.size
            if (size >= boundaryPrefix.size) {
                val start = size - boundaryPrefix.size
                val maybeBoundary = snapshot.substring(start, size)
                if (maybeBoundary == boundaryPrefix) {
                    // Write any remaining leading bytes in tail excluding optional '\r' before '\n'
                    if (tail.size > boundaryPrefix.size) {
                        val leadCount = (tail.size - boundaryPrefix.size).toInt()
                        if (leadCount > 0) {
                            // If the single leading byte is '\r', drop it; otherwise write it
                            if (leadCount == 1) {
                                val lead = tail.readByte()
                                if (lead.toInt() != '\r'.code) {
                                    outBuffer.writeByte(lead.toInt())
                                }
                            } else {
                                val toWrite = tail.readByteArray(leadCount.toLong())
                                // Drop trailing '\r' if present
                                if (toWrite.isNotEmpty() && toWrite.last() == '\r'.code.toByte()) {
                                    outBuffer.write(toWrite, 0, toWrite.size - 1)
                                } else {
                                    outBuffer.write(toWrite)
                                }
                            }
                        }
                    }
                    // Flush data accumulated so far
                    flushOutBuffer()
                    // Drop the boundary prefix from tail
                    tail.skip(tail.size)
                    // Read the remainder of the boundary line
                    val rest = source.readUtf8Line() ?: ""
                    return rest.startsWith("--")
                }
            }
        }

        // EOF without boundary; flush remaining
        flushOutBuffer()
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
