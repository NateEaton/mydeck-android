package com.mydeck.app.domain.mapper

import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.io.db.model.ArticleContentEntity
import com.mydeck.app.io.db.model.BookmarkEntity
import com.mydeck.app.io.db.model.BookmarkWithArticleContent
import com.mydeck.app.io.db.model.ImageResourceEntity
import com.mydeck.app.io.db.model.ResourceEntity
import com.mydeck.app.io.rest.model.BookmarkDto as BookmarkDto
import com.mydeck.app.io.rest.model.Resource as ResourceDto
import com.mydeck.app.io.rest.model.ImageResource as ImageResourceDto
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toInstant

fun Bookmark.toEntity(): BookmarkWithArticleContent = BookmarkWithArticleContent(
    bookmark = BookmarkEntity(
        id = id,
        href = href,
        created = created.toInstant(TimeZone.currentSystemDefault()),
        updated = updated.toInstant(TimeZone.currentSystemDefault()),
        state = when (state) {
            Bookmark.State.LOADED -> BookmarkEntity.State.LOADED
            Bookmark.State.ERROR -> BookmarkEntity.State.ERROR
            Bookmark.State.LOADING -> BookmarkEntity.State.LOADING
        },
        loaded = loaded,
        url = url,
        title = title,
        siteName = siteName,
        site = site,
        authors = authors,
        lang = lang,
        textDirection = textDirection,
        documentTpe = documentTpe,
        type = when (type) {
            Bookmark.Type.Article -> BookmarkEntity.Type.ARTICLE
            Bookmark.Type.Picture -> BookmarkEntity.Type.PHOTO
            Bookmark.Type.Video -> BookmarkEntity.Type.VIDEO
        },
        hasArticle = hasArticle,
        contentStatus = when (contentStatus) {
            Bookmark.ContentStatus.NOT_ATTEMPTED -> BookmarkEntity.ContentStatus.NOT_ATTEMPTED
            Bookmark.ContentStatus.DOWNLOADED -> BookmarkEntity.ContentStatus.DOWNLOADED
            Bookmark.ContentStatus.DIRTY -> BookmarkEntity.ContentStatus.DIRTY
            Bookmark.ContentStatus.PERMANENT_NO_CONTENT -> BookmarkEntity.ContentStatus.PERMANENT_NO_CONTENT
        },
        contentFailureReason = contentFailureReason,
        description = description,
        isDeleted = isDeleted,
        isMarked = isMarked,
        isArchived = isArchived,
        labels = labels,
        readProgress = readProgress,
        wordCount = wordCount,
        readingTime = readingTime,
        published = published?.toInstant(TimeZone.currentSystemDefault()),
        embed = embed,
        embedHostname = embedHostname,
        article = article.toEntity(),
        icon = icon.toEntity(),
        image = image.toEntity(),
        log = log.toEntity(),
        props = props.toEntity(),
        thumbnail = thumbnail.toEntity()
    ),
    articleContent = articleContent?.let { ArticleContentEntity(bookmarkId = id, content = it) }
)

fun Bookmark.Resource.toEntity(): ResourceEntity = ResourceEntity(
    src = this.src
)

fun Bookmark.ImageResource.toEntity(): ImageResourceEntity = ImageResourceEntity(
    src = this.src,
    width = this.width,
    height = this.height
)

fun BookmarkEntity.toDomain(): Bookmark = Bookmark(
    id = id,
    href = href,
    created = created.toLocalDateTime(TimeZone.currentSystemDefault()),
    updated = updated.toLocalDateTime(TimeZone.currentSystemDefault()),
    state = when (state) {
        BookmarkEntity.State.LOADED -> Bookmark.State.LOADED
        BookmarkEntity.State.ERROR -> Bookmark.State.ERROR
        BookmarkEntity.State.LOADING -> Bookmark.State.LOADING
    },
    loaded = loaded,
    url = url,
    title = title,
    siteName = siteName,
    site = site,
    authors = authors,
    lang = lang,
    textDirection = textDirection,
    documentTpe = documentTpe,
    type = when (type) {
        BookmarkEntity.Type.ARTICLE -> Bookmark.Type.Article
        BookmarkEntity.Type.PHOTO -> Bookmark.Type.Picture
        BookmarkEntity.Type.VIDEO -> Bookmark.Type.Video
    },
    hasArticle = hasArticle,
    contentStatus = when (contentStatus) {
        BookmarkEntity.ContentStatus.NOT_ATTEMPTED -> Bookmark.ContentStatus.NOT_ATTEMPTED
        BookmarkEntity.ContentStatus.DOWNLOADED -> Bookmark.ContentStatus.DOWNLOADED
        BookmarkEntity.ContentStatus.DIRTY -> Bookmark.ContentStatus.DIRTY
        BookmarkEntity.ContentStatus.PERMANENT_NO_CONTENT -> Bookmark.ContentStatus.PERMANENT_NO_CONTENT
    },
    contentFailureReason = contentFailureReason,
    description = description,
    isDeleted = isDeleted,
    isMarked = isMarked,
    isArchived = isArchived,
    labels = labels,
    readProgress = readProgress,
    wordCount = wordCount,
    readingTime = readingTime,
    published = published?.toLocalDateTime(TimeZone.currentSystemDefault()),
    embed = embed,
    embedHostname = embedHostname,
    article = article.toDomain(),
    icon = icon.toDomain(),
    image = image.toDomain(),
    log = log.toDomain(),
    props = props.toDomain(),
    thumbnail = thumbnail.toDomain(),
    articleContent = null // Article content will be fetched separately
)

fun ResourceEntity.toDomain(): Bookmark.Resource = Bookmark.Resource(
    src = this.src
)

fun ImageResourceEntity.toDomain(): Bookmark.ImageResource = Bookmark.ImageResource(
    src = this.src,
    width = this.width,
    height = this.height
)

fun BookmarkDto.toDomain(): Bookmark = Bookmark(
    id = id,
    href = href,
    created = created.toLocalDateTime(TimeZone.currentSystemDefault()),
    updated = updated.toLocalDateTime(TimeZone.currentSystemDefault()),
    state = when (state) {
        0 -> Bookmark.State.LOADED
        1 -> Bookmark.State.ERROR
        2 -> Bookmark.State.LOADING
        else -> Bookmark.State.ERROR
    },
    loaded = loaded,
    url = url,
    title = title,
    siteName = siteName,
    site = site,
    authors = authors ?: emptyList(),
    lang = lang,
    textDirection = textDirection,
    documentTpe = documentTpe,
    type = when (type.lowercase()) {
        "article" -> Bookmark.Type.Article
        "photo" -> Bookmark.Type.Picture
        "video" -> Bookmark.Type.Video
        else -> Bookmark.Type.Article
    },
    hasArticle = hasArticle,
    contentStatus = resolveContentStatus(hasArticle, type),
    contentFailureReason = resolveContentFailureReason(hasArticle, type),
    description = description,
    isDeleted = isDeleted,
    isMarked = isMarked,
    isArchived = isArchived,
    labels = labels,
    readProgress = readProgress ?: 0,
    wordCount = wordCount,
    readingTime = readingTime,
    published = published?.toLocalDateTime(TimeZone.currentSystemDefault()),
    embed = embed,
    embedHostname = embedHostname,
    article = resources.article.toDomain(),
    icon = resources.icon.toDomain(),
    image = resources.image.toDomain(),
    log = resources.log.toDomain(),
    props = resources.props.toDomain(),
    thumbnail = resources.thumbnail.toDomain(),
    articleContent = null
)

private fun resolveContentStatus(
    hasArticle: Boolean,
    type: String
): Bookmark.ContentStatus {
    return if (type.equals("photo", true) || type.equals("video", true)) {
        Bookmark.ContentStatus.PERMANENT_NO_CONTENT
    } else if (!hasArticle) {
        Bookmark.ContentStatus.NOT_ATTEMPTED
    } else {
        Bookmark.ContentStatus.NOT_ATTEMPTED
    }
}

private fun resolveContentFailureReason(
    hasArticle: Boolean,
    type: String
): String? {
    return if (type.equals("photo", true) || type.equals("video", true)) {
        "media"
    } else {
        null
    }
}

fun ResourceDto?.toDomain(): Bookmark.Resource = Bookmark.Resource(
    src = this?.src ?: ""
)

fun ImageResourceDto?.toDomain(): Bookmark.ImageResource = Bookmark.ImageResource(
    src = this?.src ?: "",
    width = this?.width ?: 0,
    height = this?.height ?: 0
)
