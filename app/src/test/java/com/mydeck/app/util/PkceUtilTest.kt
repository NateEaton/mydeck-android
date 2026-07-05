package com.mydeck.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceUtilTest {

    @Test
    fun `generateCodeVerifier produces 64-char alphanumeric string`() {
        val verifier = PkceUtil.generateCodeVerifier()
        assertEquals(64, verifier.length)
        assertTrue(verifier.all { it.isLetterOrDigit() })
    }

    @Test
    fun `generateCodeVerifier produces unique values`() {
        val v1 = PkceUtil.generateCodeVerifier()
        val v2 = PkceUtil.generateCodeVerifier()
        assertNotEquals(v1, v2)
    }

    @Test
    fun `codeChallenge matches RFC 7636 known vector`() {
        // https://www.rfc-editor.org/rfc/rfc7636#appendix-B
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expected = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        assertEquals(expected, PkceUtil.codeChallenge(verifier))
    }

    @Test
    fun `codeChallenge output is URL-safe base64 without padding`() {
        val verifier = PkceUtil.generateCodeVerifier()
        val challenge = PkceUtil.codeChallenge(verifier)
        assertFalse("challenge must not contain '+'", challenge.contains('+'))
        assertFalse("challenge must not contain '/'", challenge.contains('/'))
        assertFalse("challenge must not contain '='", challenge.contains('='))
    }

    @Test
    fun `generateState produces 32-char hex string`() {
        val state = PkceUtil.generateState()
        assertEquals(32, state.length)
        assertTrue(state.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `generateState produces unique values`() {
        val s1 = PkceUtil.generateState()
        val s2 = PkceUtil.generateState()
        assertNotEquals(s1, s2)
    }

    @Test
    fun `buildAuthorizeUrl strips api suffix and appends correct params`() {
        val url = PkceUtil.buildAuthorizeUrl(
            serverUrlWithApi = "https://readeck.example.com/api",
            clientId = "my-client",
            redirectUri = "mydeck://oauth-callback",
            scope = "bookmarks:read",
            challenge = "abc123",
            state = "xyz789"
        )
        assertTrue(url.startsWith("https://readeck.example.com/authorize"))
        assertTrue(url.contains("client_id=my-client"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("state=xyz789"))
        assertFalse("URL must not contain raw /api path", url.contains("/api/authorize"))
    }

    @Test
    fun `buildAuthorizeUrl works when serverUrl has no trailing api`() {
        val url = PkceUtil.buildAuthorizeUrl(
            serverUrlWithApi = "https://readeck.example.com",
            clientId = "c",
            redirectUri = "mydeck://oauth-callback",
            scope = "s",
            challenge = "ch",
            state = "st"
        )
        assertTrue(url.startsWith("https://readeck.example.com/authorize"))
    }
}
