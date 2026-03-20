package com.mydeck.app.io.rest.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MultipartSyncClientTest {

    @Test
    fun `extractBoundary from standard content-type`() {
        val boundary = MultipartSyncClient.extractBoundary(
            "multipart/mixed; boundary=abc123def"
        )
        assertEquals("abc123def", boundary)
    }

    @Test
    fun `extractBoundary from quoted boundary`() {
        val boundary = MultipartSyncClient.extractBoundary(
            "multipart/mixed; boundary=\"abc123def\""
        )
        assertEquals("abc123def", boundary)
    }

    @Test
    fun `extractBoundary from complex content-type`() {
        val boundary = MultipartSyncClient.extractBoundary(
            "multipart/mixed; charset=utf-8; boundary=965dd10345ce0f660bda92b9e8a1192532f999a51151dccb7227d784049b"
        )
        assertEquals("965dd10345ce0f660bda92b9e8a1192532f999a51151dccb7227d784049b", boundary)
    }

    @Test
    fun `extractBoundary returns null for non-multipart`() {
        val boundary = MultipartSyncClient.extractBoundary("application/json")
        assertNull(boundary)
    }

    @Test
    fun `extractBoundary returns null for missing boundary param`() {
        val boundary = MultipartSyncClient.extractBoundary("multipart/mixed")
        assertNull(boundary)
    }

    @Test
    fun `extractBoundary is case insensitive`() {
        val boundary = MultipartSyncClient.extractBoundary(
            "Multipart/Mixed; Boundary=abc123"
        )
        assertEquals("abc123", boundary)
    }
}
