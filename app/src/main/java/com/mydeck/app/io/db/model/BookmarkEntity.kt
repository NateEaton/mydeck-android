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
    val thumbnail: ImageResourceEntity,

    val contentState: ContentState = ContentState.NOT_ATTEMPTED,
    val contentFailureReason: String? = null,
    val isLocalDeleted: Boolean = false,
    val hasServerErrors: Boolean = false,
    val omitDescription: Boolean? = null,
    val errors: List<String> = emptyList()
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
    /**
     * Content availability state for a bookmark.
     * 
     * The distinction between text-cached and full offline packages is expressed
     * through the combination of ContentState.DOWNLOADED and ContentPackageEntity.hasResources:
     * 
     * - NOT_ATTEMPTED: No local content exists
     * - DOWNLOADED + hasResources=false: Text cached on-demand; images lazy-load from network
     * - DOWNLOADED + hasResources=true: Full offline package (text + images); managed offline
     * - DIRTY + hasResources=false: Stale text cache; needs refresh
     * - DIRTY + hasResources=true: Stale full package; needs refresh
     * - PERMANENT_NO_CONTENT: Server has confirmed no extractable content for this bookmark
     */
    enum class ContentState(val value: Int) {
        NOT_ATTEMPTED(0),
        DOWNLOADED(1),
        DIRTY(2),
        PERMANENT_NO_CONTENT(3)
    }
}
