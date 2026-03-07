package com.mydeck.app.domain

import com.mydeck.app.domain.model.Annotation
import kotlinx.coroutines.flow.Flow

interface AnnotationRepository {
    /** Emits the locally cached annotations for a bookmark, ordered by creation date. */
    fun observeAnnotations(bookmarkId: String): Flow<List<Annotation>>

    /** Fetches annotations from the server and replaces local cache for that bookmark. */
    suspend fun refreshAnnotations(bookmarkId: String): Result<Unit>

    /** Creates a new annotation server-side and caches it locally. */
    suspend fun createAnnotation(
        bookmarkId: String,
        startSelector: String,
        startOffset: Int,
        endSelector: String,
        endOffset: Int,
        color: String,
    ): Result<Annotation>

    /** Updates annotation color server-side and locally. */
    suspend fun updateAnnotationColor(
        bookmarkId: String,
        annotationId: String,
        color: String,
    ): Result<Unit>

    /** Deletes an annotation server-side and locally. */
    suspend fun deleteAnnotation(
        bookmarkId: String,
        annotationId: String,
    ): Result<Unit>
}
