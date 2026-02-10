package com.mydeck.app.domain.mapper

import com.mydeck.app.domain.model.Bookmark
import com.mydeck.app.domain.model.BookmarkListItem
import com.mydeck.app.io.db.model.ArticleContentEntity
import com.mydeck.app.io.db.model.BookmarkEntity
import com.mydeck.app.io.db.model.BookmarkWithArticleContent
import com.mydeck.app.io.db.model.BookmarkListItemEntity
import com.mydeck.app.io.db.model.ImageResourceEntity
import com.mydeck.app.io.db.model.ResourceEntity
import com.mydeck.app.io.rest.model.BookmarkDto as BookmarkDto
import com.mydeck.app.io.rest.model.Resource as ResourceDto
import com.mydeck.app.io.rest.model.ImageResource as ImageResourceDto
import com.mydeck.app.util.DynamicSvgData
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
        thumbnail = thumbnail.toEntity(),
        contentState = when (contentState) {
            Bookmark.ContentState.NOT_ATTEMPTED -> BookmarkEntity.ContentState.NOT_ATTEMPTED
            Bookmark.ContentState.DOWNLOADED -> BookmarkEntity.ContentState.DOWNLOADED
            Bookmark.ContentState.DIRTY -> BookmarkEntity.ContentState.DIRTY
            Bookmark.ContentState.PERMANENT_NO_CONTENT -> BookmarkEntity.ContentState.PERMANENT_NO_CONTENT
        },
        contentFailureReason = contentFailureReason
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
    articleContent = null, // Article content will be fetched separately
    contentState = when (contentState) {
        BookmarkEntity.ContentState.NOT_ATTEMPTED -> Bookmark.ContentState.NOT_ATTEMPTED
        BookmarkEntity.ContentState.DOWNLOADED -> Bookmark.ContentState.DOWNLOADED
        BookmarkEntity.ContentState.DIRTY -> Bookmark.ContentState.DIRTY
        BookmarkEntity.ContentState.PERMANENT_NO_CONTENT -> Bookmark.ContentState.PERMANENT_NO_CONTENT
    },
    contentFailureReason = contentFailureReason
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
    articleContent = null,
    contentState = Bookmark.ContentState.NOT_ATTEMPTED,
    contentFailureReason = null
)

fun ResourceDto?.toDomain(): Bookmark.Resource = Bookmark.Resource(
    src = this?.src ?: ""
)

fun ImageResourceDto?.toDomain(): Bookmark.ImageResource = Bookmark.ImageResource(
    src = this?.src ?: "",
    width = this?.width ?: 0,
    height = this?.height ?: 0
)

fun BookmarkListItemEntity.toDomain(): BookmarkListItem {
    val dynamicThumbnailSrc = if (thumbnailSrc.isBlank()) {
        DynamicSvgData(title).toString()
    } else {
        thumbnailSrc
    }

    val dynamicImageSrc = if (imageSrc.isBlank()) {
        DynamicSvgData(title).toString()
    } else {
        imageSrc
    }

    return BookmarkListItem(
        id = id,
        url = url,
        title = title,
        siteName = siteName,
        isMarked = isMarked,
        isArchived = isArchived,
        isRead = readProgress >= 100,
        readProgress = readProgress,
        thumbnailSrc = dynamicThumbnailSrc,
        iconSrc = iconSrc,
        imageSrc = dynamicImageSrc,
        labels = labels,
        type = when (type) {
            BookmarkEntity.Type.ARTICLE -> Bookmark.Type.Article
            BookmarkEntity.Type.PHOTO -> Bookmark.Type.Picture
            BookmarkEntity.Type.VIDEO -> Bookmark.Type.Video
        },
        readingTime = readingTime,
        created = created.toLocalDateTime(TimeZone.currentSystemDefault()),
        wordCount = wordCount,
        published = published?.toLocalDateTime(TimeZone.currentSystemDefault())
    )
}
