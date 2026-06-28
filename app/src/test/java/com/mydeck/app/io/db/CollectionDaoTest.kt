package com.mydeck.app.io.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mydeck.app.io.db.dao.CollectionDao
import com.mydeck.app.io.db.model.CollectionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollectionDaoTest {

    private lateinit var db: MyDeckDatabase
    private lateinit var dao: CollectionDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyDeckDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.getCollectionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(
        id: String,
        name: String,
        isPinned: Boolean = false,
        created: Long = 0L,
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
        hasErrors = null,
        hasLabels = null,
        rangeStart = null,
        rangeEnd = null,
        created = created,
        updated = 0L,
    )

    @Test
    fun `observeCollections returns entities ordered by isPinned DESC then created DESC`() = runTest {
        dao.upsertCollections(
            listOf(
                entity("old-id", "Old", isPinned = false, created = 100L),
                entity("pinned-id", "Pinned", isPinned = true, created = 50L),
                entity("new-id", "New", isPinned = false, created = 200L),
            )
        )

        val result = dao.observeCollections().first()

        assertEquals(3, result.size)
        // Pinned first, then unpinned newest-first.
        assertEquals("pinned-id", result[0].id)
        assertEquals("new-id", result[1].id)
        assertEquals("old-id", result[2].id)
    }

    @Test
    fun `upsertCollections with same id replaces existing entity`() = runTest {
        dao.upsertCollections(listOf(entity("1", "A")))
        dao.upsertCollections(listOf(entity("1", "B")))

        val found = dao.getById("1")
        assertNotNull(found)
        assertEquals("B", found?.name)
    }

    @Test
    fun `deleteById removes only the targeted entity`() = runTest {
        dao.upsertCollections(
            listOf(
                entity("keep", "Keep"),
                entity("remove", "Remove"),
            )
        )

        dao.deleteById("remove")

        val result = dao.observeCollections().first()
        assertEquals(1, result.size)
        assertEquals("keep", result[0].id)
        assertNull(dao.getById("remove"))
    }

    @Test
    fun `deleteAll empties the table`() = runTest {
        dao.upsertCollections(
            listOf(
                entity("a", "A"),
                entity("b", "B"),
            )
        )

        dao.deleteAll()

        val result = dao.observeCollections().first()
        assertEquals(0, result.size)
    }

    @Test
    fun `getById returns null for unknown id`() = runTest {
        val result = dao.getById("does-not-exist")
        assertNull(result)
    }
}
