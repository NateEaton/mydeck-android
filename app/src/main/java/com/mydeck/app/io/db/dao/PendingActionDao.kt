package com.mydeck.app.io.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mydeck.app.io.db.model.ActionType
import com.mydeck.app.io.db.model.PendingActionEntity

@Dao
interface PendingActionDao {
    @Query(
        """
        SELECT * FROM pending_actions
        WHERE bookmarkId = :bookmarkId AND actionType = :actionType
        LIMIT 1
        """
    )
    suspend fun findAction(bookmarkId: String, actionType: ActionType): PendingActionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: PendingActionEntity)

    @Query("UPDATE pending_actions SET payload = :payload, createdAt = :createdAt WHERE id = :id")
    suspend fun updateAction(id: Long, payload: String?, createdAt: kotlinx.datetime.Instant)

    @Query("DELETE FROM pending_actions WHERE id = :id")
    suspend fun deleteAction(id: Long)

    @Query("DELETE FROM pending_actions WHERE bookmarkId = :bookmarkId")
    suspend fun deleteAllForBookmark(bookmarkId: String)

    @Query("SELECT * FROM pending_actions ORDER BY createdAt ASC")
    suspend fun getAllActionsSorted(): List<PendingActionEntity>
}
