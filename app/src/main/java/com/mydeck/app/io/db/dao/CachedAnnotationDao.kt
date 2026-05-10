package com.mydeck.app.io.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mydeck.app.io.db.model.BookmarkAnnotationSyncMetadataEntity
import com.mydeck.app.io.db.model.CachedAnnotationEntity
import com.mydeck.app.io.db.model.CachedAnnotationHighlightEntity
import com.mydeck.app.io.db.model.RemoteAnnotationIdEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedAnnotationDao {

    @Query("SELECT * FROM cached_annotation WHERE bookmarkId = :bookmarkId")
    suspend fun getAnnotationsForBookmark(bookmarkId: String): List<CachedAnnotationEntity>

    @Query("SELECT * FROM cached_annotation WHERE bookmarkId = :bookmarkId")
    fun observeAnnotationsForBookmark(bookmarkId: String): Flow<List<CachedAnnotationEntity>>

    @Query(
        """
        SELECT
            ca.id AS id,
            ca.bookmarkId AS bookmarkId,
            ca.text AS text,
            ca.color AS color,
            ca.note AS note,
            ca.created AS created,
            b.title AS bookmarkTitle,
            b.siteName AS bookmarkSiteName
        FROM cached_annotation ca
        INNER JOIN bookmarks b ON b.id = ca.bookmarkId
        WHERE b.isLocalDeleted = 0
        ORDER BY ca.created DESC
        """
    )
    fun observeAllHighlights(): Flow<List<CachedAnnotationHighlightEntity>>

    @Query(
        """
        SELECT COUNT(*)
        FROM cached_annotation ca
        INNER JOIN bookmarks b ON b.id = ca.bookmarkId
        WHERE b.isLocalDeleted = 0
        """
    )
    fun observeHighlightCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotations(annotations: List<CachedAnnotationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRemoteAnnotationIds(ids: List<RemoteAnnotationIdEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmarkAnnotationSyncMetadata(metadata: BookmarkAnnotationSyncMetadataEntity)

    @Query("SELECT * FROM bookmark_annotation_sync_metadata WHERE bookmarkId = :bookmarkId")
    suspend fun getBookmarkAnnotationSyncMetadata(bookmarkId: String): BookmarkAnnotationSyncMetadataEntity?

    @Query("DELETE FROM remote_annotation_ids")
    suspend fun clearRemoteAnnotationIds()

    @Query(
        """
        DELETE FROM cached_annotation
        WHERE NOT EXISTS (
            SELECT 1 FROM remote_annotation_ids
            WHERE remote_annotation_ids.id = cached_annotation.id
        )
        """
    )
    suspend fun removeAnnotationsMissingFromRemote(): Int

    @Query("DELETE FROM cached_annotation WHERE bookmarkId = :bookmarkId")
    suspend fun deleteAnnotationsForBookmark(bookmarkId: String)

    @Transaction
    suspend fun replaceAnnotationsForBookmark(
        bookmarkId: String,
        annotations: List<CachedAnnotationEntity>
    ) {
        deleteAnnotationsForBookmark(bookmarkId)
        insertAnnotations(annotations)
    }

    @Transaction
    suspend fun replaceAllAnnotations(annotations: List<CachedAnnotationEntity>) {
        deleteAll()
        if (annotations.isNotEmpty()) {
            insertAnnotations(annotations)
        }
    }

    @Query("DELETE FROM cached_annotation")
    suspend fun deleteAll()
}
