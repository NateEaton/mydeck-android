package com.mydeck.app.io.db.model

import androidx.room.Entity

@Entity(
    tableName = "remote_bookmark_ids",
    primaryKeys = ["syncRunId", "id"]
)
data class RemoteBookmarkIdEntity(
    val syncRunId: String,
    val id: String
)
