package com.mydeck.app.io.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "remote_annotation_ids")
data class RemoteAnnotationIdEntity(
    @PrimaryKey val id: String
)
