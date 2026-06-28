package com.mydeck.app.domain

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mydeck.app.io.db.MyDeckDatabase
import com.mydeck.app.io.db.dao.CollectionDao
import com.mydeck.app.io.db.model.CollectionEntity
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.CollectionDto
import com.mydeck.app.io.rest.model.CreateCollectionDto
import com.mydeck.app.io.rest.model.StatusMessageDto
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CollectionRepositoryImplTest {

    private lateinit var database: MyDeckDatabase
    private lateinit var collectionDao: CollectionDao
    private lateinit var readeckApi: ReadeckApi
    private lateinit var impl: CollectionRepositoryImpl

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyDeckDatabase::class.java
        ).allowMainThreadQueries().build()
        collectionDao = database.getCollectionDao()
        readeckApi = mockk()
        impl = CollectionRepositoryImpl(
            database = database,
            collectionDao = collectionDao,
            readeckApi = readeckApi,
            dispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun dto(
        id: String = "c1",
        name: String = "Test",
        isPinned: Boolean? = false,
        isDeleted: Boolean? = null,
        created: String = "2026-01-01T00:00:00Z",
        updated: String = "2026-06-01T00:00:00Z",
    ) = CollectionDto(
        id = id,
        href = "/api/bookmarks/collections/$id",
        created = created,
        updated = updated,
        name = name,
        isPinned = isPinned,
        isDeleted = isDeleted,
    )

    private fun entity(
        id: String,
        name: String,
        isPinned: Boolean = false,
    ) = CollectionEntity(
        id = id,
        name = name,
        isPinned = isPinned,
        search = null,
        title = null,
        author = null,
        site = null,
        labels = null,
        type = emptyList(),
        readStatus = emptyList(),
        isMarked = null,
        isArchived = null,
        rangeStart = null,
        rangeEnd = null,
        created = 0L,
        updated = 0L,
    )

    // --- refreshCollections ---

    @Test
    fun `refreshCollections single page filters deleted and caches remaining`() = runTest {
        val dtos = listOf(
            dto(id = "active", name = "Active", isDeleted = null),
            dto(id = "deleted", name = "Deleted", isDeleted = true),
        )
        coEvery { readeckApi.getCollections(100, 0) } returns Response.success(dtos)

        val result = impl.refreshCollections()

        assertTrue(result.isSuccess)
        assertNotNull(collectionDao.getById("active"))
        assertNull(collectionDao.getById("deleted"))
    }

    @Test
    fun `refreshCollections paginates until page smaller than page size`() = runTest {
        val page1 = (1..100).map { dto(id = "p1-$it", name = "P1 $it") }
        val page2 = listOf(dto(id = "p2-1", name = "P2 1"))
        coEvery { readeckApi.getCollections(100, 0) } returns Response.success(page1)
        coEvery { readeckApi.getCollections(100, 100) } returns Response.success(page2)

        val result = impl.refreshCollections()

        assertTrue(result.isSuccess)
        assertNotNull(collectionDao.getById("p1-1"))
        assertNotNull(collectionDao.getById("p2-1"))
        // Total 101 non-deleted entities cached
        assertNotNull(collectionDao.getById("p1-100"))
    }

    @Test
    fun `refreshCollections returns failure on HTTP error`() = runTest {
        coEvery { readeckApi.getCollections(100, 0) } returns
            Response.error(500, "".toResponseBody(null))

        val result = impl.refreshCollections()

        assertTrue(result.isFailure)
    }

    // --- createCollection ---

    @Test
    fun `createCollection success reads location header and caches entity`() = runTest {
        val locationHeaders = Headers.headersOf("location", "/api/bookmarks/collections/new-id")
        coEvery { readeckApi.createCollection(any()) } returns
            Response.success(StatusMessageDto(0, "ok"), locationHeaders)
        coEvery { readeckApi.getCollectionById("new-id") } returns
            Response.success(dto(id = "new-id", name = "New"))

        val result = impl.createCollection("New", com.mydeck.app.domain.model.FilterFormState())

        assertTrue(result.isSuccess)
        assertEquals("new-id", result.getOrNull()?.id)
        assertNotNull(collectionDao.getById("new-id"))
    }

    @Test
    fun `createCollection missing location header returns failure`() = runTest {
        coEvery { readeckApi.createCollection(any()) } returns
            Response.success(StatusMessageDto(0, "ok"))

        val result = impl.createCollection("No Location", com.mydeck.app.domain.model.FilterFormState())

        assertTrue(result.isFailure)
    }

    @Test
    fun `createCollection HTTP error returns failure`() = runTest {
        coEvery { readeckApi.createCollection(any()) } returns
            Response.error(400, "".toResponseBody(null))

        val result = impl.createCollection("Fail", com.mydeck.app.domain.model.FilterFormState())

        assertTrue(result.isFailure)
    }

    // --- updateCollection ---

    @Test
    fun `updateCollection success caches updated entity`() = runTest {
        coEvery { readeckApi.updateCollection("c1", any()) } returns
            Response.success(dto(id = "c1", name = "Updated"))

        val result = impl.updateCollection("c1", "Updated", com.mydeck.app.domain.model.FilterFormState())

        assertTrue(result.isSuccess)
        assertNotNull(collectionDao.getById("c1"))
        assertEquals("Updated", collectionDao.getById("c1")?.name)
    }

    @Test
    fun `updateCollection HTTP error returns failure`() = runTest {
        coEvery { readeckApi.updateCollection("c1", any()) } returns
            Response.error(500, "".toResponseBody(null))

        val result = impl.updateCollection("c1", "Fail", com.mydeck.app.domain.model.FilterFormState())

        assertTrue(result.isFailure)
    }

    // --- deleteCollection ---

    @Test
    fun `deleteCollection soft deletes with correct name and removes from local cache`() = runTest {
        collectionDao.upsertCollections(listOf(entity("c1", "Keeper")))
        val bodySlot = slot<CreateCollectionDto>()
        coEvery { readeckApi.updateCollection("c1", capture(bodySlot)) } returns
            Response.success(dto(id = "c1", name = "Keeper", isDeleted = true))

        val result = impl.deleteCollection("c1")

        assertTrue(result.isSuccess)
        assertTrue(bodySlot.captured.isDeleted == true)
        assertEquals("Keeper", bodySlot.captured.name)
        assertNull(collectionDao.getById("c1"))
    }

    @Test
    fun `deleteCollection unknown id with api 404 returns failure`() = runTest {
        coEvery { readeckApi.getCollectionById("x") } returns
            Response.error(404, "".toResponseBody(null))

        val result = impl.deleteCollection("x")

        assertTrue(result.isFailure)
    }
}
