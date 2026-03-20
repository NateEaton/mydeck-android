package com.mydeck.app.io.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mydeck.app.io.db.model.CachedAnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedAnnotationDao {

    @Query("SELECT * FROM cached_annotation WHERE bookmarkId = :bookmarkId")
    suspend fun getAnnotationsForBookmark(bookmarkId: String): List<CachedAnnotationEntity>

    @Query("SELECT * FROM cached_annotation WHERE bookmarkId = :bookmarkId")
    fun observeAnnotationsForBookmark(bookmarkId: String): Flow<List<CachedAnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotations(annotations: List<CachedAnnotationEntity>)

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

    @Query("DELETE FROM cached_annotation")
    suspend fun deleteAll()
}
