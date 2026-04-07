package com.mydeck.app.domain.usecase

import com.mydeck.app.domain.BookmarkRepository
import com.mydeck.app.domain.content.ContentPackageManager
import com.mydeck.app.domain.sync.ConnectivityMonitor
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.db.dao.ContentPackageDao
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.sync.BookmarkSyncPackage
import com.mydeck.app.io.rest.sync.MultipartSyncClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class LoadContentPackageUseCaseTest {

    private lateinit var readeckApi: ReadeckApi
    private lateinit var multipartSyncClient: MultipartSyncClient
    private lateinit var contentPackageManager: ContentPackageManager
    private lateinit var useCase: LoadContentPackageUseCase

    @Before
    fun setUp() {
        readeckApi = mockk()
        multipartSyncClient = mockk()
        contentPackageManager = mockk(relaxed = true)

        useCase = LoadContentPackageUseCase(
            bookmarkRepository = mockk(relaxed = true),
            multipartSyncClient = multipartSyncClient,
            contentPackageManager = contentPackageManager,
            contentPackageDao = mockk(relaxed = true),
            bookmarkDao = mockk(relaxed = true),
            connectivityMonitor = mockk(relaxed = true),
            readeckApi = readeckApi
        )
    }

    @Test
    fun `legacyEndpointPreferredWhenAvailable`() = runBlocking {
        val html = "<p>Article content</p>"
        coEvery { readeckApi.getArticle("bm1") } returns Response.success(html)
        coEvery { contentPackageManager.updateHtml("bm1", html) } returns true

        useCase.refreshHtmlForAnnotations("bm1")

        coVerify(exactly = 1) { contentPackageManager.updateHtml("bm1", html) }
        coVerify(exactly = 0) { multipartSyncClient.fetchHtmlOnly(any()) }
    }

    @Test
    fun `legacyEndpointFallsBackToMultipartOnFailure`() = runBlocking {
        val multipartHtml = "<p>Multipart content</p>"
        coEvery { readeckApi.getArticle("bm1") } returns Response.error(404, mockk(relaxed = true))
        coEvery {
            multipartSyncClient.fetchHtmlOnly(listOf("bm1"))
        } returns MultipartSyncClient.Result.Success(
            listOf(BookmarkSyncPackage(bookmarkId = "bm1", html = multipartHtml))
        )
        coEvery { contentPackageManager.updateHtml("bm1", multipartHtml) } returns true

        useCase.refreshHtmlForAnnotations("bm1")

        coVerify(exactly = 1) { contentPackageManager.updateHtml("bm1", multipartHtml) }
    }

    @Test
    fun `urlRewriteApplied`() = runBlocking {
        val absoluteHtml = """<img src="https://server/bm/01/bookmark123/_resources/image.jpg">"""
        val relativeHtml = """<img src="./image.jpg">"""
        coEvery { readeckApi.getArticle("bookmark123") } returns Response.success(absoluteHtml)
        coEvery { contentPackageManager.updateHtml("bookmark123", relativeHtml) } returns true

        useCase.refreshHtmlForAnnotations("bookmark123")

        coVerify(exactly = 1) { contentPackageManager.updateHtml("bookmark123", relativeHtml) }
    }

    @Test
    fun `refreshFailureDoesNotOverwriteHtml`() = runBlocking {
        coEvery { readeckApi.getArticle("bm1") } throws RuntimeException("network error")
        coEvery {
            multipartSyncClient.fetchHtmlOnly(listOf("bm1"))
        } returns MultipartSyncClient.Result.Error("server error")

        val result = useCase.refreshHtmlForAnnotations("bm1")

        assertNull(result)
        coVerify(exactly = 0) { contentPackageManager.updateHtml(any(), any()) }
    }
}
