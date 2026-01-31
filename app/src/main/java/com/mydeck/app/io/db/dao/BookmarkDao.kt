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
import timber.log.Timber
import com.mydeck.app.io.db.model.ArticleContentEntity
import com.mydeck.app.io.db.model.BookmarkWithArticleContent
import com.mydeck.app.io.db.model.BookmarkListItemEntity
import com.mydeck.app.io.db.model.RemoteBookmarkIdEntity
import com.mydeck.app.io.db.model.BookmarkCountsEntity

@Dao
interface BookmarkDao {
    @Query("SELECT * from bookmarks")
    suspend fun getBookmarks(): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks ORDER BY created DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * from bookmarks WHERE type = 'picture'")
    fun getPictures(): Flow<List<BookmarkEntity>>

    @Query("SELECT * from bookmarks WHERE type = 'video'")
    fun getVideos(): Flow<List<BookmarkEntity>>

    @Query("SELECT * from bookmarks WHERE type = 'article'")
    fun getArticles(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmarks(bookmarks: List<BookmarkEntity>)

    @Transaction
    suspend fun insertBookmarkWithArticleContent(bookmarkWithArticleContent: BookmarkWithArticleContent) {
        with(bookmarkWithArticleContent) {
            insertBookmark(bookmark)
            articleContent?.run { insertArticleContent(this) }
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

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    suspend fun getBookmarkById(id: String): BookmarkEntity

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    fun observeBookmark(id: String): Flow<BookmarkEntity?>

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAllBookmarks()

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: String)

    @RawQuery(observedEntities = [BookmarkEntity::class])
    fun getBookmarksByFiltersDynamic(query: SupportSQLiteQuery): Flow<List<BookmarkEntity>>

    @RawQuery(observedEntities = [BookmarkEntity::class])
    fun getBookmarkListItemsByFiltersDynamic(query: SupportSQLiteQuery): Flow<List<BookmarkListItemEntity>>

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
            append("SELECT * FROM bookmarks WHERE 1=1")

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

    @Query("SELECT * FROM bookmarks")
    suspend fun getAllBookmarksWithContent(): List<BookmarkWithArticleContent>

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    fun observeBookmarkWithArticleContent(id: String): Flow<BookmarkWithArticleContent?>

    fun getBookmarkListItemsByFilters(
        type: BookmarkEntity.Type? = null,
        isUnread: Boolean? = null,
        isArchived: Boolean? = null,
        isFavorite: Boolean? = null,
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
            wordCount
            """)

            append(" FROM bookmarks WHERE 1=1")

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
            WHERE NOT EXISTS (SELECT 1 FROM remote_bookmark_ids WHERE bookmarks.id = remote_bookmark_ids.id)
        """
    )
    suspend fun removeDeletedBookmars(): Int

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM bookmarks WHERE readProgress < 100 AND state = 0) AS unread_count,
            (SELECT COUNT(*) FROM bookmarks WHERE isArchived = 1 AND state = 0) AS archived_count,
            (SELECT COUNT(*) FROM bookmarks WHERE isMarked = 1 AND state = 0) AS favorite_count,
            (SELECT COUNT(*) FROM bookmarks WHERE type = 'article' AND state = 0) AS article_count,
            (SELECT COUNT(*) FROM bookmarks WHERE type = 'video' AND state = 0) AS video_count,
            (SELECT COUNT(*) FROM bookmarks WHERE type = 'photo' AND state = 0) AS picture_count,
            (SELECT COUNT(*) FROM bookmarks WHERE state = 0) AS total_count
        FROM bookmarks
        LIMIT 1
        """
    )
    fun observeAllBookmarkCounts(): Flow<BookmarkCountsEntity?>

    @Query("""
        SELECT
            (SELECT COUNT(*) FROM bookmarks) AS total,
            (SELECT COUNT(*) FROM article_content) AS withContent
    """)
    fun observeSyncStatus(): Flow<SyncStatusCounts?>

    @Query("""
        SELECT b.id FROM bookmarks b
        WHERE NOT EXISTS (SELECT 1 FROM article_content ac WHERE ac.bookmarkId = b.id)
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

    fun searchBookmarkListItems(
        searchQuery: String,
        type: BookmarkEntity.Type? = null,
        isUnread: Boolean? = null,
        isArchived: Boolean? = null,
        isFavorite: Boolean? = null,
        state: BookmarkEntity.State? = null,
        orderBy: String = "created DESC"
    ): Flow<List<BookmarkListItemEntity>> {
        val args = mutableListOf<Any>()
        val sqlQuery = buildString {
            append("""SELECT id, url, title, siteName, isMarked, isArchived,
            readProgress, icon_src AS iconSrc, image_src AS imageSrc,
            labels, thumbnail_src AS thumbnailSrc, type,
            readingTime, created, wordCount
            FROM bookmarks WHERE 1=1""")

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

            append(" ORDER BY $orderBy")
        }.let { SimpleSQLiteQuery(it, args.toTypedArray()) }
        Timber.d("searchQuery=${sqlQuery.sql}")
        return getBookmarkListItemsByFiltersDynamic(sqlQuery)
    }
}
