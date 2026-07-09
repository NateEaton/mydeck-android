package com.mydeck.app.ui.whatsnew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewAssetLoaderTest {

    @Test
    fun `normalizeVersion strips the snapshot build suffix`() {
        assertEquals("1.0.0-rc5", WhatsNewAssetLoader.normalizeVersion("1.0.0-rc5-snapshot"))
    }

    @Test
    fun `normalizeVersion leaves a release version unchanged`() {
        assertEquals("1.0.0", WhatsNewAssetLoader.normalizeVersion("1.0.0"))
    }

    @Test
    fun `compareVersions orders numeric components correctly, not lexicographically`() {
        // A plain string sort would put "1.0.10" before "1.0.9".
        assertTrue(WhatsNewAssetLoader.compareVersions("1.0.10", "1.0.9") > 0)
        assertTrue(WhatsNewAssetLoader.compareVersions("1.0.9", "1.0.10") < 0)
    }

    @Test
    fun `compareVersions ranks a release above its own release candidates`() {
        assertTrue(WhatsNewAssetLoader.compareVersions("1.0.0", "1.0.0-rc5") > 0)
        assertTrue(WhatsNewAssetLoader.compareVersions("1.0.0-rc5", "1.0.0") < 0)
    }

    @Test
    fun `compareVersions orders release candidates by their trailing number`() {
        assertTrue(WhatsNewAssetLoader.compareVersions("1.0.0-rc10", "1.0.0-rc9") > 0)
        assertTrue(WhatsNewAssetLoader.compareVersions("1.0.0-rc4", "1.0.0-rc5") < 0)
    }

    @Test
    fun `compareVersions treats equal versions as equal`() {
        assertEquals(0, WhatsNewAssetLoader.compareVersions("1.0.0-rc5", "1.0.0-rc5"))
    }

    @Test
    fun `sorting with compareVersions descending yields newest first`() {
        val versions = listOf("1.0.0-rc3", "1.0.0-rc5", "1.0.0", "1.0.0-rc4", "0.9.0")
        val sorted = versions.sortedWith { a, b -> WhatsNewAssetLoader.compareVersions(b, a) }
        assertEquals(
            listOf("1.0.0", "1.0.0-rc5", "1.0.0-rc4", "1.0.0-rc3", "0.9.0"),
            sorted
        )
    }
}
