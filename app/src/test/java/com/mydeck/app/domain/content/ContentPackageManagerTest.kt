package com.mydeck.app.domain.content

import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.db.dao.CachedAnnotationDao
import com.mydeck.app.io.db.dao.ContentPackageDao
import com.mydeck.app.io.db.model.ContentPackageEntity
import com.mydeck.app.io.db.model.ContentResourceEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ContentPackageManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var contentPackageDao: ContentPackageDao
    private lateinit var cachedAnnotationDao: CachedAnnotationDao
    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var manager: ContentPackageManager

    @Before
    fun setUp() {
        contentPackageDao = mockk(relaxed = true)
        cachedAnnotationDao = mockk(relaxed = true)
        bookmarkDao = mockk(relaxed = true)
        manager = ContentPackageManager(
            contentPackageDao = contentPackageDao,
            cachedAnnotationDao = cachedAnnotationDao,
            bookmarkDao = bookmarkDao,
            offlineContentDir = tempFolder.root
        )
    }

    private fun makePackage(bookmarkId: String) = ContentPackageEntity(
        bookmarkId = bookmarkId,
        packageKind = "ARTICLE",
        hasHtml = true,
        hasResources = true,
        sourceUpdated = "2024-01-01T00:00:00Z",
        lastRefreshed = 0L,
        localBasePath = bookmarkId
    )

    private fun makeResource(bookmarkId: String, filename: String) = ContentResourceEntity(
        bookmarkId = bookmarkId,
        path = "_resources/$filename",
        mimeType = "image/jpeg",
        group = "image",
        localRelativePath = filename,
        byteSize = 100L
    )

    @Test
    fun `deleteResourcesAbortedWhenHtmlHasRelativeUrls`() = runBlocking {
        val bookmarkId = "bm1"
        val dir = File(tempFolder.root, bookmarkId).also { it.mkdirs() }
        File(dir, "index.html").writeText("""<img src="./image.jpg">""")

        coEvery { contentPackageDao.getPackage(bookmarkId) } returns makePackage(bookmarkId)
        coEvery { contentPackageDao.getResources(bookmarkId) } returns listOf(makeResource(bookmarkId, "image.jpg"))

        manager.deleteResources(bookmarkId)

        coVerify(exactly = 0) { contentPackageDao.deleteResources(any()) }
        assertTrue("index.html should still exist", File(dir, "index.html").exists())
    }

    @Test
    fun `deleteResourcesSucceedsWhenHtmlHasAbsoluteUrls`() = runBlocking {
        val bookmarkId = "bm2"
        val dir = File(tempFolder.root, bookmarkId).also { it.mkdirs() }
        File(dir, "index.html").writeText("""<img src="https://server/image.jpg">""")
        val resourceFile = File(dir, "image.jpg").also { it.writeText("fake image") }

        coEvery { contentPackageDao.getPackage(bookmarkId) } returns makePackage(bookmarkId)
        coEvery { contentPackageDao.getResources(bookmarkId) } returns listOf(makeResource(bookmarkId, "image.jpg"))

        manager.deleteResources(bookmarkId)

        assertFalse("resource file should be deleted", resourceFile.exists())
        coVerify(exactly = 1) { contentPackageDao.deleteResources(bookmarkId) }
    }

    @Test
    fun `deleteResourcesNoOpWhenNoPackageExists`() = runBlocking {
        val bookmarkId = "bm3"
        coEvery { contentPackageDao.getPackage(bookmarkId) } returns null

        manager.deleteResources(bookmarkId)

        coVerify(exactly = 0) { contentPackageDao.getResources(any()) }
        coVerify(exactly = 0) { contentPackageDao.deleteResources(any()) }
    }

    @Test
    fun `deleteResourcesNoOpWhenResourcesListEmpty`() = runBlocking {
        val bookmarkId = "bm4"
        coEvery { contentPackageDao.getPackage(bookmarkId) } returns makePackage(bookmarkId)
        coEvery { contentPackageDao.getResources(bookmarkId) } returns emptyList()

        manager.deleteResources(bookmarkId)

        coVerify(exactly = 0) { contentPackageDao.deleteResources(any()) }
    }

    @Test
    fun `deleteResourcesSucceedsWhenNoHtmlFile`() = runBlocking {
        // No index.html → getHtmlContent returns null → safety check skipped → deletion proceeds
        val bookmarkId = "bm5"
        val dir = File(tempFolder.root, bookmarkId).also { it.mkdirs() }
        val resourceFile = File(dir, "image.jpg").also { it.writeText("fake image") }

        coEvery { contentPackageDao.getPackage(bookmarkId) } returns makePackage(bookmarkId)
        coEvery { contentPackageDao.getResources(bookmarkId) } returns listOf(makeResource(bookmarkId, "image.jpg"))

        manager.deleteResources(bookmarkId)

        assertFalse("resource file should be deleted", resourceFile.exists())
        coVerify(exactly = 1) { contentPackageDao.deleteResources(bookmarkId) }
    }
}
