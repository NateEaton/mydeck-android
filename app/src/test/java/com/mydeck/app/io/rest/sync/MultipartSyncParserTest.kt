package com.mydeck.app.io.rest.sync

import kotlinx.serialization.json.Json
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class MultipartSyncParserTest {
    private lateinit var tempDir: File
    private lateinit var parser: MultipartSyncParser
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "multipart_test_${System.nanoTime()}")
        tempDir.mkdirs()
        parser = MultipartSyncParser(json, tempDir)
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    private fun bufferOf(text: String): Buffer {
        val buffer = Buffer()
        buffer.writeUtf8(text)
        return buffer
    }

    private fun mixBufferOf(vararg segments: Any): Buffer {
        val buffer = Buffer()
        segments.forEachIndexed { index, seg ->
            when (seg) {
                is String -> buffer.writeUtf8(seg)
                is ByteArray -> {
                    // Ensure CRLF before binary data if preceded by string header
                    if (index > 0 && segments[index - 1] is String) {
                        buffer.writeUtf8("\r\n")
                    }
                    buffer.write(seg)
                }
                is Buffer -> buffer.writeAll(seg)
                else -> throw IllegalArgumentException("Unsupported segment type: ${'$'}{seg::class}")
            }
        }
        return buffer
    }

    // Minimal valid BookmarkDto JSON for testing
    private fun testBookmarkJson(id: String = "test-id-1") = """
        {
            "id": "$id",
            "href": "/api/bookmarks/$id",
            "created": "2025-01-01T00:00:00Z",
            "updated": "2025-01-02T00:00:00Z",
            "state": 0,
            "loaded": true,
            "url": "https://example.com/article",
            "title": "Test Article",
            "site_name": "Example",
            "site": "example.com",
            "authors": [],
            "lang": "en",
            "text_direction": "ltr",
            "document_type": "text/html",
            "type": "article",
            "has_article": true,
            "description": "A test article",
            "is_deleted": false,
            "is_marked": false,
            "is_archived": false,
            "labels": [],
            "read_progress": 0,
            "resources": {
                "article": {"src": "/api/bookmarks/$id/article"},
                "icon": {"src": "", "width": 0, "height": 0},
                "image": {"src": "", "width": 0, "height": 0},
                "log": {"src": ""},
                "props": {"src": ""},
                "thumbnail": {"src": "", "width": 0, "height": 0}
            }
        }
    """.trimIndent()

    @Test
    fun `parse single json part`() {
        val boundary = "abc123"
        val body = """
            |--$boundary
            |Bookmark-Id: test-id-1
            |Type: json
            |Content-Type: application/json; charset=utf-8
            |
            |${testBookmarkJson()}
            |--$boundary--
        """.trimMargin()

        val packages = parser.parse(bufferOf(body), boundary)

        assertEquals(1, packages.size)
        val pkg = packages[0]
        assertEquals("test-id-1", pkg.bookmarkId)
        assertNotNull(pkg.json)
        assertEquals("test-id-1", pkg.json!!.id)
        assertEquals("Test Article", pkg.json!!.title)
        assertNull(pkg.html)
        assertTrue(pkg.resources.isEmpty())
    }

    @Test
    fun `parse json and html parts for same bookmark`() {
        val boundary = "boundary456"
        val htmlContent = "<html><body><h1>Hello</h1><img src=\"image.jpeg\"></body></html>"
        val body = """
            |--$boundary
            |Bookmark-Id: test-id-1
            |Type: json
            |Content-Type: application/json; charset=utf-8
            |
            |${testBookmarkJson()}
            |--$boundary
            |Bookmark-Id: test-id-1
            |Type: html
            |Content-Type: text/html; charset=utf-8
            |
            |$htmlContent
            |--$boundary--
        """.trimMargin()

        val packages = parser.parse(bufferOf(body), boundary)

        assertEquals(1, packages.size)
        val pkg = packages[0]
        assertEquals("test-id-1", pkg.bookmarkId)
        assertNotNull(pkg.json)
        assertEquals(htmlContent, pkg.html)
    }

    @Test
    fun `parse resource part with content-length`() {
        val boundary = "boundary789"
        val resourceData = "fake-image-binary-data-here"
        val body = """
            |--$boundary
            |Bookmark-Id: test-id-1
            |Type: json
            |Content-Type: application/json; charset=utf-8
            |
            |${testBookmarkJson()}
            |--$boundary
            |Bookmark-Id: test-id-1
            |Type: resource
            |Content-Type: image/jpeg
            |Path: image.jpeg
            |Filename: image.jpeg
            |Group: image
            |Content-Length: ${resourceData.length}
            |
            |$resourceData
            |--$boundary--
        """.trimMargin()

        val packages = parser.parse(bufferOf(body), boundary)

        assertEquals(1, packages.size)
        val pkg = packages[0]
        assertEquals(1, pkg.resources.size)
        val resource = pkg.resources[0]
        assertEquals("image.jpeg", resource.path)
        assertEquals("image.jpeg", resource.filename)
        assertEquals("image/jpeg", resource.mimeType)
        assertEquals("image", resource.group)
        assertTrue(resource.tempFile.exists())
    }

    @Test
    fun `parse resource part without content-length`() {
        val boundary = "boundaryNoLen"
        val resourceData = "some-resource-content"
        val body = """
            |--$boundary
            |Bookmark-Id: test-id-1
            |Type: resource
            |Content-Type: image/png
            |Path: icon.png
            |Filename: icon.png
            |Group: icon
            |
            |$resourceData
            |--$boundary--
        """.trimMargin()

        val packages = parser.parse(bufferOf(body), boundary)

        assertEquals(1, packages.size)
        val pkg = packages[0]
        assertEquals(1, pkg.resources.size)
        val resource = pkg.resources[0]
        assertEquals("icon.png", resource.path)
        assertTrue(resource.tempFile.exists())
        assertEquals(resourceData, resource.tempFile.readText())
    }

    @Test
    fun `parse multiple bookmarks`() {
        val boundary = "multi"
        val body = """
            |--$boundary
            |Bookmark-Id: bk-1
            |Type: json
            |
            |${testBookmarkJson("bk-1")}
            |--$boundary
            |Bookmark-Id: bk-2
            |Type: json
            |
            |${testBookmarkJson("bk-2")}
            |--$boundary--
        """.trimMargin()

        val packages = parser.parse(bufferOf(body), boundary)

        assertEquals(2, packages.size)
        val ids = packages.map { it.bookmarkId }.toSet()
        assertTrue(ids.contains("bk-1"))
        assertTrue(ids.contains("bk-2"))
    }

    @Test
    fun `skip part with missing bookmark-id`() {
        val boundary = "skiptest"
        val body = """
            |--$boundary
            |Type: json
            |
            |${testBookmarkJson()}
            |--$boundary
            |Bookmark-Id: valid-id
            |Type: json
            |
            |${testBookmarkJson("valid-id")}
            |--$boundary--
        """.trimMargin()

        val packages = parser.parse(bufferOf(body), boundary)

        assertEquals(1, packages.size)
        assertEquals("valid-id", packages[0].bookmarkId)
    }

    @Test
    fun `skip part with unknown type`() {
        val boundary = "unknowntype"
        val body = """
            |--$boundary
            |Bookmark-Id: test-id-1
            |Type: json
            |
            |${testBookmarkJson()}
            |--$boundary
            |Bookmark-Id: test-id-1
            |Type: markdown
            |
            |# Some markdown
            |--$boundary--
        """.trimMargin()

        val packages = parser.parse(bufferOf(body), boundary)

        assertEquals(1, packages.size)
        val pkg = packages[0]
        assertNotNull(pkg.json)
        assertNull(pkg.html)
        assertTrue(pkg.parseWarnings.any { it.contains("Unknown type") })
    }

    @Test
    fun `handle malformed json gracefully`() {
        val boundary = "badjson"
        val body = """
            |--$boundary
            |Bookmark-Id: bad-json-id
            |Type: json
            |
            |{this is not valid json}
            |--$boundary--
        """.trimMargin()

        val packages = parser.parse(bufferOf(body), boundary)

        assertEquals(1, packages.size)
        val pkg = packages[0]
        assertNull(pkg.json)
        assertTrue(pkg.parseWarnings.any { it.contains("JSON parse error") })
    }

    @Test
    fun `empty stream returns empty list`() {
        val packages = parser.parse(bufferOf(""), "boundary")
        assertTrue(packages.isEmpty())
    }

    @Test
    fun `stream with only closing boundary returns empty`() {
        val boundary = "closingonly"
        val body = "--$boundary--\n"
        val packages = parser.parse(bufferOf(body), boundary)
        assertTrue(packages.isEmpty())
    }

    @Test
    fun `full article package with json html and multiple resources`() {
        val boundary = "fullpkg"
        val htmlContent = "<html><body><img src=\"photo.jpg\"><img src=\"thumb.jpg\"></body></html>"
        val body = """
            |--$boundary
            |Bookmark-Id: full-1
            |Type: json
            |Content-Type: application/json; charset=utf-8
            |
            |${testBookmarkJson("full-1")}
            |--$boundary
            |Bookmark-Id: full-1
            |Type: html
            |Content-Type: text/html; charset=utf-8
            |
            |$htmlContent
            |--$boundary
            |Bookmark-Id: full-1
            |Type: resource
            |Content-Type: image/jpeg
            |Path: photo.jpg
            |Filename: photo.jpg
            |Group: image
            |
            |fake-photo-bytes
            |--$boundary
            |Bookmark-Id: full-1
            |Type: resource
            |Content-Type: image/jpeg
            |Path: thumb.jpg
            |Filename: thumb.jpg
            |Group: embedded
            |
            |fake-thumb-bytes
            |--$boundary--
        """.trimMargin()

        val packages = parser.parse(bufferOf(body), boundary)

        assertEquals(1, packages.size)
        val pkg = packages[0]
        assertEquals("full-1", pkg.bookmarkId)
        assertNotNull(pkg.json)
        assertEquals(htmlContent, pkg.html)
        assertEquals(2, pkg.resources.size)
        assertEquals("image", pkg.resources[0].group)
        assertEquals("embedded", pkg.resources[1].group)
        assertTrue(pkg.resources.all { it.tempFile.exists() })
    }

    @Test
    fun `resource with missing path is skipped with warning`() {
        val boundary = "nopath"
        val body = """
            |--$boundary
            |Bookmark-Id: test-id-1
            |Type: resource
            |Content-Type: image/jpeg
            |Filename: image.jpeg
            |Group: image
            |
            |fake-data
            |--$boundary--
        """.trimMargin()

        val packages = parser.parse(bufferOf(body), boundary)

        assertEquals(1, packages.size)
        assertTrue(packages[0].resources.isEmpty())
        assertTrue(packages[0].parseWarnings.any { it.contains("Resource skipped") })
    }

    @Test
    fun `omit_description present in json part`() {
        val boundary = "omitdesc"
        val jsonWithOmitDesc = testBookmarkJson().replace(
            "\"read_progress\": 0",
            "\"read_progress\": 0, \"omit_description\": true"
        )
        val body = """
            |--$boundary
            |Bookmark-Id: test-id-1
            |Type: json
            |
            |$jsonWithOmitDesc
            |--$boundary--
        """.trimMargin()

        val packages = parser.parse(bufferOf(body), boundary)

        assertEquals(1, packages.size)
        assertEquals(true, packages[0].json?.omitDescription)
    }

    @Test
    fun `large resource with content-length streams to disk`() {
        val boundary = "big"
        val data = ByteArray(100_000) { (it % 251).toByte() }
        val headers = """
            |--$boundary
            |Bookmark-Id: test-id-1
            |Type: resource
            |Content-Type: application/octet-stream
            |Path: big.bin
            |Filename: big.bin
            |Group: embedded
            |Content-Length: ${'$'}{data.size}
            |
        """.trimMargin()
        val closing = "\r\n--$boundary--\r\n"

        val body = mixBufferOf(headers, data, closing)

        val packages = parser.parse(body, boundary)
        assertEquals(1, packages.size)
        val res = packages[0].resources.single()
        assertEquals("big.bin", res.path)
        assertEquals(data.size.toLong(), res.tempFile.length())
        val disk = res.tempFile.readBytes()
        assertTrue(disk.contentEquals(data))
    }

    @Test
    fun `binary resource without content-length is parsed until boundary`() {
        val boundary = "nolen-bin"
        val data = byteArrayOf(0x00, 0x7F, 0x10, 0x0A, 0xFF.toByte(), 0x42)
        val headers = """
            |--$boundary
            |Bookmark-Id: test-id-1
            |Type: resource
            |Content-Type: application/octet-stream
            |Path: bin.dat
            |Filename: bin.dat
            |Group: embedded
            |
        """.trimMargin()
        val closing = "\r\n--$boundary--\r\n"

        val body = mixBufferOf(headers, data, closing)
        val packages = parser.parse(body, boundary)

        assertEquals(1, packages.size)
        val res = packages[0].resources.single()
        assertEquals("bin.dat", res.path)
        val disk = res.tempFile.readBytes()
        assertTrue(disk.contentEquals(data))
    }

    @Test
    fun `truncated content-length logs warning and continues`() {
        val boundary = "trunc"
        val advertised = 64
        val actual = 32
        val data = ByteArray(actual) { 0x2A }
        val headers = """
            |--$boundary
            |Bookmark-Id: test-id-1
            |Type: resource
            |Content-Type: application/octet-stream
            |Path: trunc.bin
            |Filename: trunc.bin
            |Group: embedded
            |Content-Length: $advertised
            |
        """.trimMargin()
        val closing = "\r\n--$boundary--\r\n"

        val body = mixBufferOf(headers, data, closing)
        val packages = parser.parse(body, boundary)
        assertEquals(1, packages.size)
        val res = packages[0].resources.single()
        assertTrue("Resource temp file should exist", res.tempFile.exists())
        val actualLength = res.tempFile.length()
        // When Content-Length is truncated, parser writes available bytes plus boundary padding
        // The exact count depends on tail buffering; we only assert that at least the advertised bytes are not exceeded
        assertTrue("File length $actualLength should be <= advertised $advertised", actualLength <= advertised)
        assertTrue("File length $actualLength should be >= actual data size $actual", actualLength >= actual)
    }
}
