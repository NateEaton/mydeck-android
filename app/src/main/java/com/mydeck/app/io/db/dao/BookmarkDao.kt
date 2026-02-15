package com.mydeck.app.io.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.mydeck.app.io.db.model.BookmarkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import timber.log.Timber
import com.mydeck.app.io.db.model.ArticleContentEntity
import com.mydeck.app.io.db.model.BookmarkWithArticleContent
import com.mydeck.app.io.db.model.BookmarkListItemEntity
import com.mydeck.app.io.db.model.RemoteBookmarkIdEntity
import com.mydeck.app.io.db.model.BookmarkCountsEntity

@Dao
interface BookmarkDao {
    @Query("SELECT * from bookmarks WHERE isLocalDeleted = 0")
    suspend fun getBookmarks(): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE isLocalDeleted = 0 ORDER BY created DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * from bookmarks WHERE type = 'picture' AND isLocalDeleted = 0")
    fun getPictures(): Flow<List<BookmarkEntity>>

    @Query("SELECT * from bookmarks WHERE type = 'video' AND isLocalDeleted = 0")
    fun getVideos(): Flow<List<BookmarkEntity>>

    @Query("SELECT * from bookmarks WHERE type = 'article' AND isLocalDeleted = 0")
    fun getArticles(): Flow<List<BookmarkEntity>>

    @Query("""
        SELECT labels FROM bookmarks WHERE state = 0 AND labels != '' AND labels IS NOT NULL AND isLocalDeleted = 0
    """)
    fun observeAllLabels(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmarks(bookmarks: List<BookmarkEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookmarkIgnore(bookmarkEntity: BookmarkEntity): Long

    @Query("""
        UPDATE bookmarks SET
            href = :href, updated = :updated, state = :state, loaded = :loaded,
            url = :url, title = :title, siteName = :siteName, site = :site,
            authors = :authors, lang = :lang, textDirection = :textDirection,
            documentTpe = :documentTpe, type = :type, hasArticle = :hasArticle,
            description = :description, isDeleted = :isDeleted, isMarked = :isMarked,
            isArchived = :isArchived, labels = :labels, readProgress = :readProgress,
            wordCount = :wordCount, readingTime = :readingTime, published = :published,
            embed = :embed, embedHostname = :embedHostname,
            article_src = :articleSrc, icon_src = :iconSrc, icon_width = :iconWidth, icon_height = :iconHeight,
            image_src = :imageSrc, image_width = :imageWidth, image_height = :imageHeight,
            log_src = :logSrc, props_src = :propsSrc,
            thumbnail_src = :thumbnailSrc, thumbnail_width = :thumbnailWidth, thumbnail_height = :thumbnailHeight
        WHERE id = :id
    """)
    suspend fun updateBookmarkMetadata(
        id: String, href: String, updated: Instant, state: BookmarkEntity.State, loaded: Boolean,
        url: String, title: String, siteName: String, site: String,
        authors: List<String>, lang: String, textDirection: String,
        documentTpe: String, type: BookmarkEntity.Type, hasArticle: Boolean,
        description: String, isDeleted: Boolean, isMarked: Boolean,
        isArchived: Boolean, labels: List<String>, readProgress: Int,
        wordCount: Int?, readingTime: Int?, published: Instant?,
        embed: String?, embedHostname: String?,
        articleSrc: String, iconSrc: String, iconWidth: Int, iconHeight: Int,
        imageSrc: String, imageWidth: Int, imageHeight: Int,
        logSrc: String, propsSrc: String,
        thumbnailSrc: String, thumbnailWidth: Int, thumbnailHeight: Int
    )

    @Transaction
    suspend fun upsertBookmark(bookmarkEntity: BookmarkEntity) {
        val result = insertBookmarkIgnore(bookmarkEntity)
        if (result == -1L) {
            with(bookmarkEntity) {
                updateBookmarkMetadata(
                    id = id, href = href, updated = updated, state = state, loaded = loaded,
                    url = url, title = title, siteName = siteName, site = site,
                    authors = authors, lang = lang, textDirection = textDirection,
                    documentTpe = documentTpe, type = type, hasArticle = hasArticle,
                    description = description, isDeleted = isDeleted, isMarked = isMarked,
                    isArchived = isArchived, labels = labels, readProgress = readProgress,
                    wordCount = wordCount, readingTime = readingTime, published = published,
                    embed = embed, embedHostname = embedHostname,
                    articleSrc = article.src, iconSrc = icon.src, iconWidth = icon.width, iconHeight = icon.height,
                    imageSrc = image.src, imageWidth = image.width, imageHeight = image.height,
                    logSrc = log.src, propsSrc = props.src,
                    thumbnailSrc = thumbnail.src, thumbnailWidth = thumbnail.width, thumbnailHeight = thumbnail.height
                )
            }
        }
    }

    @Transaction
    suspend fun insertBookmarkWithArticleContent(bookmarkWithArticleContent: BookmarkWithArticleContent) {
        with(bookmarkWithArticleContent) {
            // For existing bookmarks, preserve content state and downloaded article content.
            // Room's REPLACE strategy does DELETE+INSERT, which triggers CASCADE DELETE on
            // the article_content foreign key. We must read and restore both.
            val existingState = getContentStateById(bookmark.id)
            val existingArticleContent = if (existingState != null) getArticleContent(bookmark.id) else null

            val bookmarkToInsert = if (existingState != null &&
                bookmark.contentState == BookmarkEntity.ContentState.NOT_ATTEMPTED) {
                // Only preserve existing state when the incoming bookmark has the default
                // NOT_ATTEMPTED state (i.e., it's a metadata-only sync that doesn't know
                // the real content state). When the caller explicitly sets a state like
                // DOWNLOADED, respect it.
                bookmark.copy(
                    contentState = existingState.contentState,
                    contentFailureReason = existingState.contentFailureReason
                )
            } else {
                bookmark
            }

            insertBookmark(bookmarkToInsert)

            // Re-insert article content: prefer new content, fall back to preserved existing
            val contentToSave = articleContent ?: existingArticleContent?.let {
                ArticleContentEntity(bookmarkId = bookmark.id, content = it)
            }
            contentToSave?.run { insertArticleContent(this) }
        }
    }

    @Transaction
    suspend fun insertBookmarksWithArticleContent(bookmarks: List<BookmarkWithArticleContent>) {
        bookmarks.forEach {
            insertBookmarkWithArticleContent(it)
        }
    }

    @Insert(onConflict = REPLACE)
    suspend fun insertBookmark(bookmarkEntity: BookmarkEntity)

    @Query("SELECT * FROM bookmarks ORDER BY updated DESC LIMIT 1")
    suspend fun getLastUpdatedBookmark(): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE id = :id AND isLocalDeleted = 0")
    suspend fun getBookmarkById(id: String): BookmarkEntity

    @Query("SELECT * FROM bookmarks WHERE id IN (:ids) AND isLocalDeleted = 0")
    suspend fun getBookmarksByIds(ids: List<String>): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE id = :id AND isLocalDeleted = 0")
    fun observeBookmark(id: String): Flow<BookmarkEntity?>

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAllBookmarks()

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: String)

    @Query("UPDATE bookmarks SET isLocalDeleted = 1 WHERE id = :id")
    suspend fun softDeleteBookmark(id: String)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun hardDeleteBookmark(id: String)

    @Transaction
    @RawQuery(observedEntities = [BookmarkEntity::class])
    fun getBookmarksByFiltersDynamic(query: SupportSQLiteQuery): Flow<List<BookmarkEntity>>

    @Transaction
    @RawQuery(observedEntities = [BookmarkEntity::class])
    fun getBookmarkListItemsByFiltersDynamic(query: SupportSQLiteQuery): Flow<List<BookmarkListItemEntity>>

    @Query("SELECT isMarked FROM bookmarks WHERE id = :id")
    suspend fun getIsMarked(id: String): Boolean

    @Query("SELECT isArchived FROM bookmarks WHERE id = :id")
    suspend fun getIsArchived(id: String): Boolean

    @Query("SELECT readProgress FROM bookmarks WHERE id = :id")
    suspend fun getReadProgress(id: String): Int

    fun getBookmarksByFilters(
        type: BookmarkEntity.Type? = null,
        isUnread: Boolean? = null,
        isArchived: Boolean? = null,
        isFavorite: Boolean? = null,
        state: BookmarkEntity.State? = null,
        orderBy: String = "created DESC"
    ): Flow<List<BookmarkEntity>> {
        val args = mutableListOf<Any>()
        val sqlQuery = buildString {
            append("SELECT * FROM bookmarks WHERE isLocalDeleted = 0")

            state?.let {
                append(" AND state = ?")
                args.add(it.value)
            }

            type?.let {
                append(" AND type = ?")
                args.add(it.value)
            }

            if (isUnread == true) {
                append(" AND readProgress < 100")
            } else if (isUnread == false) {
                append(" AND readProgress = 100")
            }

            isArchived?.let {
                append(" AND isArchived = ?")
                args.add(it)
            }

            isFavorite?.let {
                append(" AND isMarked = ?")
                args.add(it)
            }
            append(" ORDER BY $orderBy")
        }.let { SimpleSQLiteQuery(it, args.toTypedArray()) }
        Timber.d("query=${sqlQuery.sql}")
        return getBookmarksByFiltersDynamic(sqlQuery)
    }

    @Query("SELECT content FROM article_content WHERE bookmarkId = :bookmarkId")
    suspend fun getArticleContent(bookmarkId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticleContent(articleContent: ArticleContentEntity): Unit

    @Query("DELETE FROM article_content WHERE bookmarkId = :bookmarkId")
    suspend fun deleteArticleContent(bookmarkId: String)

    @Transaction
    @Query("SELECT * FROM bookmarks")
    suspend fun getAllBookmarksWithContent(): List<BookmarkWithArticleContent>

    @Transaction
    @Query("SELECT * FROM bookmarks WHERE id = :id AND isLocalDeleted = 0")
    fun observeBookmarkWithArticleContent(id: String): Flow<BookmarkWithArticleContent?>

    fun getBookmarkListItemsByFilters(
        type: BookmarkEntity.Type? = null,
        isUnread: Boolean? = null,
        isArchived: Boolean? = null,
        isFavorite: Boolean? = null,
        label: String? = null,
        state: BookmarkEntity.State? = null,
        orderBy: String = "created DESC"
    ): Flow<List<BookmarkListItemEntity>> {
        val args = mutableListOf<Any>()
        val sqlQuery = buildString {
            append("""SELECT
            id,
            url,
            title,
            siteName,
            isMarked,
            isArchived,
            readProgress,
            icon_src AS iconSrc,
            image_src AS imageSrc,
            labels,
            thumbnail_src AS thumbnailSrc,
            type,
            readingTime,
            created,
            wordCount,
            published
            """)

            append(" FROM bookmarks WHERE isLocalDeleted = 0")

            state?.let {
                append(" AND state = ?")
                args.add(it.value)
            }

            type?.let {
                append(" AND type = ?")
                args.add(it.value)
            }

            if (isUnread == true) {
                append(" AND readProgress < 100")
            } else if (isUnread == false) {
                append(" AND readProgress = 100")
            }

            isArchived?.let {
                append(" AND isArchived = ?")
                args.add(it)
            }

            isFavorite?.let {
                append(" AND isMarked = ?")
                args.add(it)
            }

            label?.let {
                append(" AND labels LIKE ?")
                args.add("%$it%")
            }

            append(" ORDER BY $orderBy")
        }.let { SimpleSQLiteQuery(it, args.toTypedArray()) }
        Timber.d("query=${sqlQuery.sql}")
        return getBookmarkListItemsByFiltersDynamic(sqlQuery)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRemoteBookmarkIds(ids: List<RemoteBookmarkIdEntity>)

    @Query("DELETE FROM remote_bookmark_ids")
    suspend fun clearRemoteBookmarkIds()

    @Query("SELECT id FROM remote_bookmark_ids")
    suspend fun getAllRemoteBookmarkIds(): List<String>

    @Query(
        """
            DELETE FROM bookmarks
            WHERE isLocalDeleted = 0 
            AND NOT EXISTS (SELECT 1 FROM remote_bookmark_ids WHERE bookmarks.id = remote_bookmark_ids.id)
        """
    )
    suspend fun removeDeletedBookmars(): Int

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM bookmarks WHERE readProgress < 100 AND state = 0 AND isLocalDeleted = 0) AS unread_count,
            (SELECT COUNT(*) FROM bookmarks WHERE isArchived = 1 AND state = 0 AND isLocalDeleted = 0) AS archived_count,
            (SELECT COUNT(*) FROM bookmarks WHERE isMarked = 1 AND state = 0 AND isLocalDeleted = 0) AS favorite_count,
            (SELECT COUNT(*) FROM bookmarks WHERE type = 'article' AND state = 0 AND isLocalDeleted = 0) AS article_count,
            (SELECT COUNT(*) FROM bookmarks WHERE type = 'video' AND state = 0 AND isLocalDeleted = 0) AS video_count,
            (SELECT COUNT(*) FROM bookmarks WHERE type = 'photo' AND state = 0 AND isLocalDeleted = 0) AS picture_count,
            (SELECT COUNT(*) FROM bookmarks WHERE state = 0 AND isLocalDeleted = 0) AS total_count
        FROM bookmarks
        LIMIT 1
        """
    )
    fun observeAllBookmarkCounts(): Flow<BookmarkCountsEntity?>

    @Query("""
        SELECT
            (SELECT COUNT(*) FROM bookmarks WHERE isLocalDeleted = 0) AS total,
            (SELECT COUNT(*) FROM article_content ac WHERE EXISTS (SELECT 1 FROM bookmarks b WHERE b.id = ac.bookmarkId AND b.isLocalDeleted = 0)) AS withContent
    """)
    fun observeSyncStatus(): Flow<SyncStatusCounts?>

    @Query("""
        SELECT b.id FROM bookmarks b
        WHERE b.isLocalDeleted = 0 AND NOT EXISTS (SELECT 1 FROM article_content ac WHERE ac.bookmarkId = b.id)
        ORDER BY b.created DESC
    """)
    suspend fun getBookmarkIdsWithoutContent(): List<String>

    data class SyncStatusCounts(
        val total: Int,
        val withContent: Int
    )

    @Query("UPDATE bookmarks SET isMarked = :isMarked WHERE id = :id")
    suspend fun updateIsMarked(id: String, isMarked: Boolean)

    @Query("UPDATE bookmarks SET isArchived = :isArchived WHERE id = :id")
    suspend fun updateIsArchived(id: String, isArchived: Boolean)

    @Query("UPDATE bookmarks SET readProgress = :readProgress WHERE id = :id")
    suspend fun updateReadProgress(id: String, readProgress: Int)

    @Query("UPDATE bookmarks SET labels = :labels WHERE id = :id")
    suspend fun updateLabels(id: String, labels: String)

    @Query("UPDATE bookmarks SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: String, title: String)

    fun searchBookmarkListItems(
        searchQuery: String,
        type: BookmarkEntity.Type? = null,
        isUnread: Boolean? = null,
        isArchived: Boolean? = null,
        isFavorite: Boolean? = null,
        label: String? = null,
        state: BookmarkEntity.State? = null,
        orderBy: String = "created DESC"
    ): Flow<List<BookmarkListItemEntity>> {
        val args = mutableListOf<Any>()
        val sqlQuery = buildString {
            append("""SELECT id, url, title, siteName, isMarked, isArchived,
            readProgress, icon_src AS iconSrc, image_src AS imageSrc,
            labels, thumbnail_src AS thumbnailSrc, type,
            readingTime, created, wordCount, published
            FROM bookmarks WHERE isLocalDeleted = 0""")

            if (searchQuery.isNotBlank()) {
                append(" AND (title LIKE ? COLLATE NOCASE OR labels LIKE ? COLLATE NOCASE OR siteName LIKE ? COLLATE NOCASE)")
                val pattern = "%$searchQuery%"
                args.add(pattern)
                args.add(pattern)
                args.add(pattern)
            }

            state?.let {
                append(" AND state = ?")
                args.add(it.value)
            }

            type?.let {
                append(" AND type = ?")
                args.add(it.value)
            }

            if (isUnread == true) {
                append(" AND readProgress < 100")
            } else if (isUnread == false) {
                append(" AND readProgress = 100")
            }

            isArchived?.let {
                append(" AND isArchived = ?")
                args.add(it)
            }

            isFavorite?.let {
                append(" AND isMarked = ?")
                args.add(it)
            }

            label?.let {
                append(" AND labels LIKE ?")
                args.add("%$it%")
            }

            append(" ORDER BY $orderBy")
        }.let { SimpleSQLiteQuery(it, args.toTypedArray()) }
        Timber.d("searchQuery=${sqlQuery.sql}")
        return getBookmarkListItemsByFiltersDynamic(sqlQuery)
    }

    data class ContentStateInfo(
        val contentState: BookmarkEntity.ContentState,
        val contentFailureReason: String?
    )

    @Query("SELECT contentState, contentFailureReason FROM bookmarks WHERE id = :id")
    suspend fun getContentStateById(id: String): ContentStateInfo?

    @Query("UPDATE bookmarks SET contentState = :state, contentFailureReason = :reason WHERE id = :id")
    suspend fun updateContentState(id: String, state: Int, reason: String?)

    data class DetailedSyncStatusCounts(
        val total: Int,
        val unread: Int,
        val archived: Int,
        val favorites: Int,
        val contentDownloaded: Int,
        val contentAvailable: Int,
        val contentDirty: Int,
        val permanentNoContent: Int
    )

    @Query("""
        SELECT
            (SELECT COUNT(*) FROM bookmarks WHERE state = 0 AND isLocalDeleted = 0) AS total,
            (SELECT COUNT(*) FROM bookmarks WHERE readProgress < 100 AND state = 0 AND isLocalDeleted = 0) AS unread,
            (SELECT COUNT(*) FROM bookmarks WHERE isArchived = 1 AND state = 0 AND isLocalDeleted = 0) AS archived,
            (SELECT COUNT(*) FROM bookmarks WHERE isMarked = 1 AND state = 0 AND isLocalDeleted = 0) AS favorites,
            (SELECT COUNT(*) FROM bookmarks WHERE contentState = 1 AND isLocalDeleted = 0) AS contentDownloaded,
            (SELECT COUNT(*) FROM bookmarks WHERE contentState = 0 AND hasArticle = 1 AND isLocalDeleted = 0) AS contentAvailable,
            (SELECT COUNT(*) FROM bookmarks WHERE contentState = 2 AND isLocalDeleted = 0) AS contentDirty,
            (SELECT COUNT(*) FROM bookmarks WHERE contentState = 3 AND isLocalDeleted = 0) AS permanentNoContent
        FROM bookmarks LIMIT 1
    """)
    fun observeDetailedSyncStatus(): Flow<DetailedSyncStatusCounts?>

    @Query("""
        SELECT b.id FROM bookmarks b
        WHERE b.isLocalDeleted = 0 AND b.contentState IN (0, 2)
        AND b.hasArticle = 1
        ORDER BY b.created DESC
    """)
    suspend fun getBookmarkIdsEligibleForContentFetch(): List<String>

    @Query("""
        SELECT b.id FROM bookmarks b
        WHERE b.isLocalDeleted = 0 AND b.contentState IN (0, 2)
        AND b.hasArticle = 1
        AND b.created >= :fromEpoch AND b.created <= :toEpoch
        ORDER BY b.created DESC
    """)
    suspend fun getBookmarkIdsForDateRangeContentFetch(fromEpoch: Long, toEpoch: Long): List<String>
}
