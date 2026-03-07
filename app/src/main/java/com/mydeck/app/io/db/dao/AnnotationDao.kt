package com.mydeck.app.io.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mydeck.app.io.db.model.AnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE bookmarkId = :bookmarkId ORDER BY created ASC")
    fun observeAnnotations(bookmarkId: String): Flow<List<AnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnnotations(annotations: List<AnnotationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnnotation(annotation: AnnotationEntity)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM annotations WHERE bookmarkId = :bookmarkId")
    suspend fun deleteForBookmark(bookmarkId: String)

    @Query("SELECT * FROM annotations WHERE id = :id")
    suspend fun getById(id: String): AnnotationEntity?

    @Query("SELECT * FROM annotations WHERE bookmarkId = :bookmarkId")
    suspend fun getForBookmark(bookmarkId: String): List<AnnotationEntity>

    @Transaction
    suspend fun replaceForBookmark(bookmarkId: String, annotations: List<AnnotationEntity>) {
        deleteForBookmark(bookmarkId)
        upsertAnnotations(annotations)
    }
}
