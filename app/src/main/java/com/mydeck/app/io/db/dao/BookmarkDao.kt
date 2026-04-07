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
            embed = :embed, embedHostname = :embedHostname, hasServerErrors = :hasServerErrors, omitDescription = :omitDescription,
            errors = :errors,
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
        embed: String?, embedHostname: String?, hasServerErrors: Boolean, omitDescription: Boolean?, errors: List<String>,
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
                    embed = embed, embedHostname = embedHostname, hasServerErrors = hasServerErrors, omitDescription = omitDescription, errors = errors,
                    articleSrc = article.src, iconSrc = icon.src, iconWidth = icon.width, iconHeight = icon.height,
                    imageSrc = image.src, imageWidth = image.width, imageHeight = image.height,
                    logSrc = log.src, propsSrc = props.src,
                    thumbnailSrc = thumbnail.src, thumbnailWidth = thumbnail.width, thumbnailHeight = thumbnail.height
                )
            }
        }
    }

    @Query("UPDATE bookmarks SET hasServerErrors = 0")
    suspend fun clearServerErrorFlags()

    @Query("UPDATE bookmarks SET hasServerErrors = 1 WHERE id IN (:ids)")
    suspend fun setServerErrorFlags(ids: List<String>)

    @Transaction
    suspend fun replaceServerErrorFlags(ids: List<String>) {
        clearServerErrorFlags()
        ids.chunked(900).forEach { chunk ->
            if (chunk.isNotEmpty()) {
                setServerErrorFlags(chunk)
            }
        }
    }

    @Transaction
    suspend fun upsertBookmarksMetadataOnly(bookmarkEntities: List<BookmarkEntity>) {
        bookmarkEntities.forEach { upsertBookmark(it) }
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

    @Query("DELETE FROM article_content")
    suspend fun deleteAllArticleContent()

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
            b.id,
            b.href,
            b.url,
            b.title,
            b.siteName,
            b.isMarked,
            b.isArchived,
            b.readProgress,
            b.icon_src AS iconSrc,
            b.image_src AS imageSrc,
            b.labels,
            b.thumbnail_src AS thumbnailSrc,
            b.type,
            b.readingTime,
            b.created,
            b.wordCount,
            b.published,
            b.contentState,
            cp.hasResources
            """)

            append(" FROM bookmarks b")
            append(" LEFT JOIN content_package cp ON cp.bookmarkId = b.id")
            append(" WHERE b.isLocalDeleted = 0")

            state?.let {
                append(" AND b.state = ?")
                args.add(it.value)
            }

            type?.let {
                append(" AND b.type = ?")
                args.add(it.value)
            }

            if (isUnread == true) {
                append(" AND b.readProgress < 100")
            } else if (isUnread == false) {
                append(" AND b.readProgress = 100")
            }

            isArchived?.let {
                append(" AND b.isArchived = ?")
                args.add(it)
            }

            isFavorite?.let {
                append(" AND b.isMarked = ?")
                args.add(it)
            }

            label?.let {
                append(" AND INSTR(b.labels, ?) > 0")
                args.add("\"$it\"")
            }

            append(" ORDER BY b.$orderBy")
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

    @Query(
        """
        UPDATE bookmarks
        SET title = :title,
            description = :description,
            siteName = :siteName,
            authors = :authors,
            published = :published,
            lang = :lang,
            textDirection = :textDirection,
            omitDescription = :omitDescription
        WHERE id = :id
        """
    )
    suspend fun updateMetadata(
        id: String,
        title: String,
        description: String,
        siteName: String,
        authors: List<String>,
        published: Instant?,
        lang: String,
        textDirection: String,
        omitDescription: Boolean?
    )

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
            append("""SELECT b.id, b.href, b.url, b.title, b.siteName, b.isMarked, b.isArchived,
            b.readProgress, b.icon_src AS iconSrc, b.image_src AS imageSrc,
            b.labels, b.thumbnail_src AS thumbnailSrc, b.type,
            b.readingTime, b.created, b.wordCount, b.published,
            b.contentState, cp.hasResources
            FROM bookmarks b
            LEFT JOIN content_package cp ON cp.bookmarkId = b.id
            WHERE b.isLocalDeleted = 0""")

            if (searchQuery.isNotBlank()) {
                append(" AND (b.title LIKE ? COLLATE NOCASE OR b.labels LIKE ? COLLATE NOCASE OR b.siteName LIKE ? COLLATE NOCASE)")
                val pattern = "%$searchQuery%"
                args.add(pattern)
                args.add(pattern)
                args.add(pattern)
            }

            state?.let {
                append(" AND b.state = ?")
                args.add(it.value)
            }

            type?.let {
                append(" AND b.type = ?")
                args.add(it.value)
            }

            if (isUnread == true) {
                append(" AND b.readProgress < 100")
            } else if (isUnread == false) {
                append(" AND b.readProgress = 100")
            }

            isArchived?.let {
                append(" AND b.isArchived = ?")
                args.add(it)
            }

            isFavorite?.let {
                append(" AND b.isMarked = ?")
                args.add(it)
            }

            label?.let {
                append(" AND INSTR(b.labels, ?) > 0")
                args.add("\"$it\"")
            }

            append(" ORDER BY b.$orderBy")
        }.let { SimpleSQLiteQuery(it, args.toTypedArray()) }
        Timber.d("searchQuery=${sqlQuery.sql}")
        return getBookmarkListItemsByFiltersDynamic(sqlQuery)
    }

    fun getFilteredBookmarkListItems(
        searchQuery: String? = null,
        title: String? = null,
        author: String? = null,
        site: String? = null,
        types: Set<BookmarkEntity.Type> = emptySet(),
        progressFilters: Set<Int> = emptySet(), // 0=UNVIEWED, 1=IN_PROGRESS, 2=COMPLETED
        isArchived: Boolean? = null,
        isFavorite: Boolean? = null,
        label: String? = null,
        fromDate: Long? = null,
        toDate: Long? = null,
        isLoaded: Boolean? = null,
        withLabels: Boolean? = null,
        withErrors: Boolean? = null,
        orderBy: String = "created DESC"
    ): Flow<List<BookmarkListItemEntity>> {
        val args = mutableListOf<Any>()
        val sqlQuery = buildString {
            append("""SELECT b.id, b.href, b.url, b.title, b.siteName, b.isMarked, b.isArchived,
            b.readProgress, b.icon_src AS iconSrc, b.image_src AS imageSrc,
            b.labels, b.thumbnail_src AS thumbnailSrc, b.type,
            b.readingTime, b.created, b.wordCount, b.published,
            b.contentState, cp.hasResources
            FROM bookmarks b
            LEFT JOIN content_package cp ON cp.bookmarkId = b.id
            WHERE b.isLocalDeleted = 0""")

            if (!searchQuery.isNullOrBlank()) {
                append(" AND (b.title LIKE ? COLLATE NOCASE OR b.labels LIKE ? COLLATE NOCASE OR b.siteName LIKE ? COLLATE NOCASE OR b.authors LIKE ? COLLATE NOCASE)")
                val pattern = "%$searchQuery%"
                args.add(pattern)
                args.add(pattern)
                args.add(pattern)
                args.add(pattern)
            }

            if (!title.isNullOrBlank()) {
                append(" AND b.title LIKE ? COLLATE NOCASE")
                args.add("%$title%")
            }

            if (!author.isNullOrBlank()) {
                append(" AND b.authors LIKE ? COLLATE NOCASE")
                args.add("%$author%")
            }

            if (!site.isNullOrBlank()) {
                append(" AND b.siteName LIKE ? COLLATE NOCASE")
                args.add("%$site%")
            }

            if (types.isNotEmpty()) {
                val placeholders = types.joinToString(", ") { "?" }
                append(" AND b.type IN ($placeholders)")
                types.forEach { args.add(it.value) }
            }

            if (progressFilters.isNotEmpty()) {
                val conditions = mutableListOf<String>()
                if (0 in progressFilters) conditions.add("b.readProgress = 0") // UNVIEWED
                if (1 in progressFilters) conditions.add("(b.readProgress > 0 AND b.readProgress < 100)") // IN_PROGRESS
                if (2 in progressFilters) conditions.add("b.readProgress = 100") // COMPLETED
                if (conditions.isNotEmpty()) {
                    append(" AND (${conditions.joinToString(" OR ")})")
                }
            }

            isArchived?.let {
                append(" AND b.isArchived = ?")
                args.add(it)
            }

            isFavorite?.let {
                append(" AND b.isMarked = ?")
                args.add(it)
            }

            label?.let {
                append(" AND INSTR(b.labels, ?) > 0")
                args.add("\"$it\"")
            }

            fromDate?.let {
                append(" AND b.published >= ?")
                args.add(it)
            }

            toDate?.let {
                append(" AND b.published <= ?")
                args.add(it)
            }

            // isLoaded: true = DOWNLOADED (contentState=1), false = NOT_ATTEMPTED (contentState=0)
            isLoaded?.let {
                if (it) {
                    append(" AND b.contentState = 1")
                } else {
                    append(" AND b.contentState = 0")
                }
            }

            // withLabels: true = has labels, false = no labels
            withLabels?.let {
                if (it) {
                    append(" AND b.labels != '[]' AND b.labels != '' AND b.labels IS NOT NULL")
                } else {
                    append(" AND (b.labels = '[]' OR b.labels = '' OR b.labels IS NULL)")
                }
            }

            // withErrors: true = state=ERROR(1) or the server has flagged the bookmark with errors
            withErrors?.let {
                if (it) {
                    append(" AND (b.state = 1 OR b.hasServerErrors = 1)")
                } else {
                    append(" AND b.state != 1 AND b.hasServerErrors = 0")
                }
            }

            append(" ORDER BY b.$orderBy")
        }.let { SimpleSQLiteQuery(it, args.toTypedArray()) }
        Timber.d("filteredQuery=${sqlQuery.sql}")
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

    @Query("UPDATE bookmarks SET contentState = 2, contentFailureReason = :reason WHERE id IN (:ids)")
    suspend fun markContentDirty(ids: List<String>, reason: String?)

    @Query("UPDATE bookmarks SET omitDescription = :omitDescription WHERE id = :id")
    suspend fun updateOmitDescription(id: String, omitDescription: Boolean?)

    @Query("UPDATE bookmarks SET contentState = 0, contentFailureReason = NULL")
    suspend fun resetAllContentState()

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

    data class OfflinePolicyBookmark(
        val id: String,
        val created: Instant,
        val contentState: BookmarkEntity.ContentState,
        val hasOfflinePackage: Boolean
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
        AND (b.hasArticle = 1 OR b.type = 'photo')
        AND (:includeArchived = 1 OR b.isArchived = 0)
        ORDER BY b.created DESC
    """)
    suspend fun getBookmarkIdsEligibleForContentFetch(includeArchived: Boolean = true): List<String>

    @Query("""
        SELECT
            b.id AS id,
            b.created AS created,
            b.contentState AS contentState,
            CASE WHEN b.contentState = 1 THEN 1 ELSE 0 END AS hasOfflinePackage
        FROM bookmarks b
        WHERE b.isLocalDeleted = 0
        AND (b.hasArticle = 1 OR b.type = 'photo')
        AND b.contentState != 3
        AND (:includeArchived = 1 OR b.isArchived = 0)
        ORDER BY b.created DESC
    """)
    suspend fun getOfflinePolicyBookmarks(includeArchived: Boolean): List<OfflinePolicyBookmark>

    @Query("""
        SELECT COUNT(*) FROM bookmarks
        WHERE contentState = 3
        AND isLocalDeleted = 0
        AND id IN (
            SELECT id FROM bookmarks
            WHERE isLocalDeleted = 0
            AND (:includeArchived = 1 OR isArchived = 0)
            ORDER BY created DESC
            LIMIT :n
        )
    """)
    suspend fun countPermanentNoContentInNewestN(n: Int, includeArchived: Boolean): Int

    @Query("""
        SELECT COUNT(*) FROM bookmarks
        WHERE isLocalDeleted = 0
        AND contentState = 3
        AND created >= :fromEpoch
        AND (:includeArchived = 1 OR isArchived = 0)
    """)
    suspend fun countPermanentNoContentInDateRange(fromEpoch: Long, includeArchived: Boolean): Int

    @Query("""
        SELECT MIN(created) FROM bookmarks
        WHERE contentState = 1
        AND isLocalDeleted = 0
        AND (:includeArchived = 1 OR isArchived = 0)
    """)
    suspend fun getOldestDownloadedBookmarkEpoch(includeArchived: Boolean): Long?

    @Query("""
        UPDATE bookmarks
        SET contentState = 3,
            contentFailureReason = 'No extractable content'
        WHERE isLocalDeleted = 0
        AND contentState = 0
        AND hasArticle = 0
        AND type != 'photo'
    """)
    suspend fun markNoContentBookmarksPermanent(): Int

    @Query("""
        SELECT b.id
        FROM bookmarks b
        INNER JOIN content_package cp ON cp.bookmarkId = b.id
        WHERE b.isLocalDeleted = 0
        AND b.isArchived = 1
        AND cp.hasResources = 1
        ORDER BY b.created DESC
    """)
    suspend fun getArchivedBookmarkIdsWithOfflinePackage(): List<String>

    @Query("""
        UPDATE bookmarks
        SET contentState = 2,
            contentFailureReason = 'Legacy article cache needs multipart refresh'
        WHERE id IN (
            SELECT b.id
            FROM bookmarks b
            INNER JOIN article_content ac ON ac.bookmarkId = b.id
            LEFT JOIN content_package cp ON cp.bookmarkId = b.id
            WHERE b.isLocalDeleted = 0
            AND b.contentState = 1
            AND cp.bookmarkId IS NULL
            AND (b.hasArticle = 1 OR b.type = 'photo')
            AND (:includeArchived = 1 OR b.isArchived = 0)
        )
    """)
    suspend fun markLegacyCachedContentDirtyWithoutPackage(includeArchived: Boolean)

    @Query("""
        SELECT b.id FROM bookmarks b
        WHERE b.isLocalDeleted = 0 AND b.contentState IN (0, 2)
        AND (b.hasArticle = 1 OR b.type = 'photo')
        AND (:includeArchived = 1 OR b.isArchived = 0)
        AND b.created >= :fromEpoch AND b.created <= :toEpoch
        ORDER BY b.created DESC
    """)
    suspend fun getBookmarkIdsForDateRangeContentFetch(
        fromEpoch: Long,
        toEpoch: Long,
        includeArchived: Boolean = true
    ): List<String>

    @Query("""
        SELECT b.id
        FROM bookmarks b
        INNER JOIN content_package cp ON cp.bookmarkId = b.id
        WHERE b.isLocalDeleted = 0
        AND cp.hasResources = 1
        ORDER BY b.created ASC
    """)
    suspend fun getBookmarkIdsWithOfflinePackages(): List<String>

    @Query("SELECT COALESCE(SUM(byteSize), 0) FROM content_resource WHERE mimeType LIKE 'image/%'")
    suspend fun getTotalImageResourceBytes(): Long
}
