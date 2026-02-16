package com.mydeck.app.io.db.dao

import androidx.room.*
import com.mydeck.app.io.db.model.ActionType
import com.mydeck.app.io.db.model.PendingActionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingActionDao {
    @Insert
    suspend fun insert(action: PendingActionEntity): Long

    @Query("SELECT * FROM pending_actions WHERE bookmarkId = :bookmarkId AND actionType = :actionType LIMIT 1")
    suspend fun find(bookmarkId: String, actionType: ActionType): PendingActionEntity?

    @Query("UPDATE pending_actions SET payload = :payload, createdAt = :createdAt WHERE id = :id")
    suspend fun updateAction(id: Long, payload: String?, createdAt: kotlinx.datetime.Instant)

    @Delete
    suspend fun delete(action: PendingActionEntity)

    @Query("DELETE FROM pending_actions WHERE bookmarkId = :bookmarkId")
    suspend fun deleteAllForBookmark(bookmarkId: String)

    @Query("SELECT * FROM pending_actions ORDER BY createdAt ASC")
    suspend fun getAllActionsSorted(): List<PendingActionEntity>

    @Query("SELECT * FROM pending_actions WHERE bookmarkId = :bookmarkId")
    suspend fun getActionsForBookmark(bookmarkId: String): List<PendingActionEntity>

    @Query("SELECT * FROM pending_actions WHERE bookmarkId IN (:bookmarkIds)")
    suspend fun getActionsForBookmarks(bookmarkIds: List<String>): List<PendingActionEntity>

    @Query("SELECT COUNT(*) FROM pending_actions")
    fun getCountFlow(): Flow<Int>

    @Query("DELETE FROM pending_actions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
