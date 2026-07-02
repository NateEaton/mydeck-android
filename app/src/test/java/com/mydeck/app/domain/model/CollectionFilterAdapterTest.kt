package com.mydeck.app.domain.model

import com.mydeck.app.io.db.model.CollectionEntity
import com.mydeck.app.io.rest.model.CollectionDto
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionFilterAdapterTest {

    private val isoCreated = "2026-01-01T00:00:00Z"
    private val isoUpdated = "2026-06-01T12:00:00Z"

    private fun makeDto(
        id: String = "c1",
        name: String = "Test",
        isPinned: Boolean? = false,
        isDeleted: Boolean? = null,
        search: String? = null,
        title: String? = null,
        author: String? = null,
        site: String? = null,
        type: List<String>? = null,
        labels: String? = null,
        readStatus: List<String>? = null,
        isMarked: Boolean? = null,
        isArchived: Boolean? = null,
        hasErrors: Boolean? = null,
        hasLabels: Boolean? = null,
        rangeStart: String? = null,
        rangeEnd: String? = null,
        created: String = isoCreated,
        updated: String = isoUpdated,
    ) = CollectionDto(
        id = id,
        href = "/api/bookmarks/collections/$id",
        created = created,
        updated = updated,
        name = name,
        isPinned = isPinned,
        isDeleted = isDeleted,
        search = search,
        title = title,
        author = author,
        site = site,
        type = type,
        labels = labels,
        readStatus = readStatus,
        isMarked = isMarked,
        isArchived = isArchived,
        hasErrors = hasErrors,
        hasLabels = hasLabels,
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
    )

    // --- toCreateCollectionDto ---

    @Test
    fun `toCreateCollectionDto maps all persistable fields correctly`() {
        val from = Instant.parse("2026-01-01T00:00:00Z")
        val to = Instant.parse("2026-12-31T23:59:59Z")
        val filter = FilterFormState(
            search = "query",
            title = "My Title",
            author = "Jane",
            site = "example.com",
            label = "tech",
            types = linkedSetOf(Bookmark.Type.Article, Bookmark.Type.Picture, Bookmark.Type.Video),
            progress = linkedSetOf(ProgressFilter.UNVIEWED, ProgressFilter.IN_PROGRESS, ProgressFilter.COMPLETED),
            isFavorite = true,
            isArchived = false,
            withErrors = true,
            withLabels = false,
            fromDate = from,
            toDate = to,
        )

        val dto = filter.toCreateCollectionDto("My Collection", isPinned = true)

        assertEquals("My Collection", dto.name)
        assertTrue(dto.isPinned == true)
        assertEquals("query", dto.search)
        assertEquals("My Title", dto.title)
        assertEquals("Jane", dto.author)
        assertEquals("example.com", dto.site)
        assertEquals("tech", dto.labels)
        assertEquals(setOf("article", "photo", "video"), dto.type?.toSet())
        assertEquals(setOf("unread", "reading", "read"), dto.readStatus?.toSet())
        assertTrue(dto.isMarked == true)
        assertTrue(dto.isArchived == false)
        assertTrue(dto.hasErrors == true)
        assertTrue(dto.hasLabels == false)
        assertEquals(from.toString(), dto.rangeStart)
        assertEquals(to.toString(), dto.rangeEnd)
    }

    @Test
    fun `toCreateCollectionDto with empty types and progress produces null lists`() {
        val filter = FilterFormState()
        val dto = filter.toCreateCollectionDto("Empty")
        assertNull(dto.type)
        assertNull(dto.readStatus)
    }

    @Test
    fun `toCreateCollectionDto persists has_errors and has_labels`() {
        val dto = FilterFormState(withErrors = true, withLabels = true).toCreateCollectionDto("WE")
        assertTrue(dto.hasErrors == true)
        assertTrue(dto.hasLabels == true)
    }

    @Test
    fun `toCreateCollectionDto ignores local-only fields`() {
        // isLoaded ("Downloaded") and the reading-time/word-count fields have no collection-API
        // equivalent and must not surface on the request body.
        val filter = FilterFormState(
            search = "hello",
            isLoaded = true,
            minReadingTime = 5,
            minWordCount = 10,
        )
        val dto = filter.toCreateCollectionDto("Local Only Test")
        // No crash; search should still round-trip
        assertEquals("hello", dto.search)
    }

    // --- toUpdateCollectionJson (PATCH body) ---

    @Test
    fun `toUpdateCollectionJson sends explicit nulls for cleared fields so PATCH clears them`() {
        // An all-unset filter must still send every managed field as JSON null (not omit them),
        // otherwise PATCH leaves the previous values in place.
        val json = FilterFormState().toUpdateCollectionJson("My Collection")

        assertEquals("My Collection", json["name"]?.jsonPrimitive?.content)
        listOf(
            "search", "title", "author", "site", "labels",
            "is_marked", "is_archived", "has_errors", "has_labels",
            "range_start", "range_end", "type", "read_status",
        ).forEach { key ->
            assertTrue("expected key '$key' present", json.containsKey(key))
            assertTrue("expected '$key' to be JSON null", json[key] is JsonNull)
        }
        // is_pinned / is_deleted are deliberately omitted so editing doesn't disturb them.
        assertNull(json["is_pinned"])
        assertNull(json["is_deleted"])
    }

    @Test
    fun `toUpdateCollectionJson encodes set fields with their values`() {
        val json = FilterFormState(
            author = "Ada",
            withErrors = true,
            withLabels = false,
            isFavorite = true,
            types = linkedSetOf(Bookmark.Type.Article),
        ).toUpdateCollectionJson("C")

        assertEquals("Ada", json["author"]?.jsonPrimitive?.content)
        assertEquals(true, json["has_errors"]?.jsonPrimitive?.boolean)
        assertEquals(false, json["has_labels"]?.jsonPrimitive?.boolean)
        assertEquals(true, json["is_marked"]?.jsonPrimitive?.boolean)
        assertEquals("article", (json["type"] as JsonArray)[0].jsonPrimitive.content)
    }

    // --- CollectionDto.toFilterFormState ---

    @Test
    fun `CollectionDto toFilterFormState maps types and progress and takes first label segment`() {
        val rangeStart = "2026-01-01T00:00:00Z"
        val rangeEnd = "2026-06-30T00:00:00Z"
        val dto = makeDto(
            type = listOf("article", "video"),
            readStatus = listOf("unread", "read"),
            labels = "x,y,z",
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            isMarked = true,
            isArchived = false,
            hasErrors = true,
            hasLabels = false,
        )

        val state = dto.toFilterFormState()

        assertEquals(setOf(Bookmark.Type.Article, Bookmark.Type.Video), state.types)
        assertEquals(setOf(ProgressFilter.UNVIEWED, ProgressFilter.COMPLETED), state.progress)
        assertEquals("x", state.label)
        // has_errors / has_labels round-trip; isLoaded ("Downloaded") stays local-only (null).
        assertTrue(state.withErrors == true)
        assertTrue(state.withLabels == false)
        assertNull(state.isLoaded)
        assertEquals(Instant.parse(rangeStart), state.fromDate)
        assertEquals(Instant.parse(rangeEnd), state.toDate)
        assertTrue(state.isFavorite == true)
        assertTrue(state.isArchived == false)
    }

    @Test
    fun `CollectionDto toFilterFormState ignores unknown type and readStatus strings`() {
        val dto = makeDto(
            type = listOf("article", "bogus"),
            readStatus = listOf("unread", "unknown_status"),
        )

        val state = dto.toFilterFormState()

        assertEquals(setOf(Bookmark.Type.Article), state.types)
        assertEquals(setOf(ProgressFilter.UNVIEWED), state.progress)
    }

    // --- Entity round-trip: CollectionDto.toEntity().toDomain() ---

    @Test
    fun `CollectionDto toEntity toDomain round trip preserves fields`() {
        val dto = makeDto(
            id = "col-42",
            name = "Round Trip",
            isPinned = true,
            type = listOf("article", "video"),
            readStatus = listOf("reading"),
            labels = "tech,science",
            isMarked = true,
            isArchived = false,
            hasErrors = true,
            hasLabels = true,
            rangeStart = isoCreated,
            rangeEnd = isoUpdated,
            created = isoCreated,
            updated = isoUpdated,
        )

        val domain = dto.toEntity().toDomain()

        assertEquals("col-42", domain.id)
        assertEquals("Round Trip", domain.name)
        assertTrue(domain.isPinned)
        assertEquals(Instant.parse(isoCreated), domain.created)
        assertEquals(Instant.parse(isoUpdated), domain.updated)
        assertEquals(setOf(Bookmark.Type.Article, Bookmark.Type.Video), domain.filter.types)
        assertEquals(setOf(ProgressFilter.IN_PROGRESS), domain.filter.progress)
        // first label segment
        assertEquals("tech", domain.filter.label)
        assertTrue(domain.filter.isFavorite == true)
        assertTrue(domain.filter.isArchived == false)
        assertTrue(domain.filter.withErrors == true)
        assertTrue(domain.filter.withLabels == true)
    }
}
