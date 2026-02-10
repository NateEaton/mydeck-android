package com.mydeck.app.io.db.model

import androidx.room.*
import kotlinx.datetime.Instant

@Entity(
    tableName = "pending_actions",
    indices = [
        Index(value = ["bookmarkId", "actionType"])
    ]
)
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookmarkId: String,
    val actionType: ActionType,
    val payload: String?,
    val createdAt: Instant
)

enum class ActionType {
    UPDATE_PROGRESS,
    TOGGLE_FAVORITE,
    TOGGLE_ARCHIVE,
    TOGGLE_READ,
    UPDATE_LABELS,
    DELETE
}
