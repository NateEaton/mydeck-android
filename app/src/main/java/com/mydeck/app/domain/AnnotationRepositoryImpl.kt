package com.mydeck.app.domain

import com.mydeck.app.coroutine.IoDispatcher
import com.mydeck.app.domain.model.Annotation
import com.mydeck.app.domain.model.AnnotationColors
import com.mydeck.app.io.db.dao.AnnotationDao
import com.mydeck.app.io.db.model.AnnotationEntity
import com.mydeck.app.io.rest.ReadeckApi
import com.mydeck.app.io.rest.model.AnnotationDto
import com.mydeck.app.io.rest.model.CreateAnnotationDto
import com.mydeck.app.io.rest.model.UpdateAnnotationDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import timber.log.Timber
import javax.inject.Inject

class AnnotationRepositoryImpl @Inject constructor(
    private val annotationDao: AnnotationDao,
    private val readeckApi: ReadeckApi,
    @IoDispatcher
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AnnotationRepository {

    override fun observeAnnotations(bookmarkId: String): Flow<List<Annotation>> =
        annotationDao.observeAnnotations(bookmarkId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun refreshAnnotations(bookmarkId: String): Result<Unit> =
        kotlinx.coroutines.withContext(dispatcher) {
            runCatching {
                val response = readeckApi.getAnnotations(bookmarkId)
                if (!response.isSuccessful) {
                    error("getAnnotations failed: HTTP ${response.code()}")
                }
                val dtos = response.body() ?: emptyList()
                val localColors = annotationDao.getForBookmark(bookmarkId)
                    .associate { entity -> entity.id to entity.color }

                val entities = dtos.map { dto ->
                    dto.toEntity(bookmarkId, localColors[dto.id] ?: AnnotationColors.default)
                }

                annotationDao.replaceForBookmark(bookmarkId, entities)
            }.onFailure { Timber.w(it, "refreshAnnotations failed for bookmark $bookmarkId") }
        }

    override suspend fun createAnnotation(
        bookmarkId: String,
        startSelector: String,
        startOffset: Int,
        endSelector: String,
        endOffset: Int,
        color: String,
    ): Result<Annotation> =
        kotlinx.coroutines.withContext(dispatcher) {
            runCatching {
                val body = CreateAnnotationDto(
                    startSelector = startSelector,
                    startOffset = startOffset,
                    endSelector = endSelector,
                    endOffset = endOffset,
                    color = color,
                )
                val response = readeckApi.createAnnotation(bookmarkId, body)
                if (!response.isSuccessful) {
                    error("createAnnotation failed: HTTP ${response.code()}")
                }
                val dto = response.body() ?: error("createAnnotation returned empty body")
                val entity = dto.toEntity(bookmarkId, color)
                annotationDao.upsertAnnotation(entity)
                entity.toDomain()
            }.onFailure { Timber.w(it, "createAnnotation failed for bookmark $bookmarkId") }
        }

    override suspend fun updateAnnotationColor(
        bookmarkId: String,
        annotationId: String,
        color: String,
    ): Result<Unit> =
        kotlinx.coroutines.withContext(dispatcher) {
            runCatching {
                val response = readeckApi.updateAnnotation(
                    bookmarkId = bookmarkId,
                    annotationId = annotationId,
                    body = UpdateAnnotationDto(color = color),
                )
                if (!response.isSuccessful) {
                    error("updateAnnotation failed: HTTP ${response.code()}")
                }
                val body = response.body() ?: error("updateAnnotation returned empty body")
                val localColors = annotationDao.getForBookmark(bookmarkId)
                    .associate { entity -> entity.id to entity.color }
                    .toMutableMap()
                    .apply { put(annotationId, color) }
                val mergedEntities = body.annotations.map { dto ->
                    dto.toEntity(bookmarkId, localColors[dto.id] ?: AnnotationColors.default)
                }
                annotationDao.replaceForBookmark(bookmarkId, mergedEntities)
            }.onFailure { Timber.w(it, "updateAnnotationColor failed for $annotationId") }
        }

    override suspend fun deleteAnnotation(
        bookmarkId: String,
        annotationId: String,
    ): Result<Unit> =
        kotlinx.coroutines.withContext(dispatcher) {
            runCatching {
                val response = readeckApi.deleteAnnotation(bookmarkId, annotationId)
                if (!response.isSuccessful) {
                    error("deleteAnnotation failed: HTTP ${response.code()}")
                }
                annotationDao.deleteById(annotationId)
            }.onFailure { Timber.w(it, "deleteAnnotation failed for $annotationId") }
        }
}

// ---- Mapping helpers ----

private fun AnnotationDto.toEntity(bookmarkId: String, color: String) = AnnotationEntity(
    id = id,
    bookmarkId = bookmarkId,
    startSelector = startSelector,
    startOffset = startOffset,
    endSelector = endSelector,
    endOffset = endOffset,
    color = color,
    text = text,
    created = created.toEpochMilliseconds(),
)

private fun AnnotationEntity.toDomain() = Annotation(
    id = id,
    bookmarkId = bookmarkId,
    startSelector = startSelector,
    startOffset = startOffset,
    endSelector = endSelector,
    endOffset = endOffset,
    color = color,
    text = text,
    created = Instant.fromEpochMilliseconds(created),
)
