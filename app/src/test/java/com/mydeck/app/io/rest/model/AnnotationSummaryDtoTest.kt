package com.mydeck.app.io.rest.model

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotationSummaryDtoTest {

    @Test
    fun `toDomain falls back when created timestamp is blank`() {
        val dto = AnnotationSummaryDto(
            id = "annotation-1",
            href = "",
            text = "Highlighted text",
            color = "yellow",
            note = "Note",
            created = "",
            bookmark_id = "bookmark-1",
            bookmark_href = "",
            bookmark_url = "",
            bookmark_title = "Bookmark",
            bookmark_site_name = "Example"
        )

        val result = dto.toDomain()

        assertEquals(Instant.fromEpochMilliseconds(0), result.created)
    }
}
