package com.mydeck.app.io.rest

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadeckHttpPolicyInterceptorTest {
    @Test
    fun `blocks http requests when insecure http is disabled`() {
        val server = MockWebServer()
        server.start()
        try {
            val client = OkHttpClient.Builder()
                .addInterceptor(ReadeckHttpPolicyInterceptor(allowInsecureHttp = false))
                .build()
            val request = Request.Builder()
                .url(server.url("/api/info"))
                .build()

            try {
                client.newCall(request).execute()
                error("Expected HTTP policy block")
            } catch (e: HttpBlockedByBuildPolicyException) {
                assertTrue(e.isHttpBlockedByBuildPolicy())
            }

            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `allows http requests when insecure http is enabled`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.start()
        try {
            val client = OkHttpClient.Builder()
                .addInterceptor(ReadeckHttpPolicyInterceptor(allowInsecureHttp = true))
                .build()
            val request = Request.Builder()
                .url(server.url("/api/info"))
                .build()

            client.newCall(request).execute().use { response ->
                assertTrue(response.isSuccessful)
            }

            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }
}
