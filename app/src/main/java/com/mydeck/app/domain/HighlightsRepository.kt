package com.mydeck.app.domain

import com.mydeck.app.domain.model.HighlightSummary
import com.mydeck.app.io.db.dao.CachedAnnotationDao
import com.mydeck.app.io.db.model.CachedAnnotationEntity
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.toDomain
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

interface HighlightsRepository {
    suspend fun getAllHighlights(): Result<List<HighlightSummary>>
}

@Singleton
class HighlightsRepositoryImpl @Inject constructor(
    private val readeckApi: ReadeckApi,
    private val cachedAnnotationDao: CachedAnnotationDao,
) : HighlightsRepository {

    override suspend fun getAllHighlights(): Result<List<HighlightSummary>> {
        return try {
            val pageSize = 50
            val all = mutableListOf<com.mydeck.app.io.rest.model.AnnotationSummaryDto>()
            var offset = 0
            while (true) {
                val response = readeckApi.getAnnotationSummaries(
                    limit = pageSize,
                    offset = offset
                )
                if (!response.isSuccessful) {
                    return Result.failure(
                        IllegalStateException("HTTP ${response.code()}")
                    )
                }
                val page = response.body() ?: break
                all.addAll(page)
                if (page.size < pageSize) break
                offset += pageSize
            }

            // Seed cached_annotation table so the drawer chip count stays accurate.
            val byBookmark = all.groupBy { it.bookmark_id }
            for ((bookmarkId, dtos) in byBookmark) {
                cachedAnnotationDao.replaceAnnotationsForBookmark(
                    bookmarkId,
                    dtos.map { dto ->
                        CachedAnnotationEntity(
                            id = dto.id,
                            bookmarkId = bookmarkId,
                            text = dto.text,
                            color = dto.color.takeIf { it.isNotBlank() } ?: "yellow",
                            note = dto.note.takeIf { it.isNotBlank() },
                            created = dto.created.takeIf { it.isNotBlank() } ?: Clock.System.now().toString()
                        )
                    }
                )
            }

            Result.success(all.map { it.toDomain() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
