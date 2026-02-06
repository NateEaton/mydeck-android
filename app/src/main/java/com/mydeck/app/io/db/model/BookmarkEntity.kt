package com.mydeck.app.io.db.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "bookmarks",
    indices = [
        Index(value = ["readProgress"]),
        Index(value = ["type"]),
        Index(value = ["isArchived"]),
        Index(value = ["isMarked"])
    ]
)
data class BookmarkEntity(
    @PrimaryKey
    val id: String,
    val href: String,
    val created: Instant,
    val updated: Instant,
    val state: State,
    val loaded: Boolean,
    val url: String,
    val title: String,
    val siteName: String,
    val site: String,
    val authors: List<String>,
    val lang: String,
    val textDirection: String,
    val documentTpe: String,
    val type: Type,
    val hasArticle: Boolean,
    val contentStatus: ContentStatus,
    val contentFailureReason: String?,
    val description: String,
    val isDeleted: Boolean,
    val isMarked: Boolean,
    val isArchived: Boolean,
    val labels: List<String>,
    val readProgress: Int,
    val wordCount: Int?,
    val readingTime: Int?,
    val published: Instant?,
    val embed: String?,
    val embedHostname: String?,

    // Embedded Resources
    @Embedded(prefix = "article_")
    val article: ResourceEntity,
    @Embedded(prefix = "icon_")
    val icon: ImageResourceEntity,
    @Embedded(prefix = "image_")
    val image: ImageResourceEntity,
    @Embedded(prefix = "log_")
    val log: ResourceEntity,
    @Embedded(prefix = "props_")
    val props: ResourceEntity,
    @Embedded(prefix = "thumbnail_")
    val thumbnail: ImageResourceEntity
) {
    enum class Type(val value: String) {
        ARTICLE("article"),
        VIDEO("video"),
        PHOTO("photo")
    }
    enum class State(val value: Int) {
        LOADED(0),
        ERROR(1),
        LOADING(2)
    }
    enum class ContentStatus(val value: Int) {
        NOT_ATTEMPTED(0),
        LOADING(1),
        DOWNLOADED(2),
        DIRTY(3),
        PERMANENT_NO_CONTENT(4)
    }
}
