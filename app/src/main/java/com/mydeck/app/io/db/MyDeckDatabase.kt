package com.mydeck.app.io.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.db.model.ArticleContentEntity
import com.mydeck.app.io.db.model.BookmarkEntity
import com.mydeck.app.io.db.model.RemoteBookmarkIdEntity

@Database(
    entities = [BookmarkEntity::class, ArticleContentEntity::class, RemoteBookmarkIdEntity::class],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MyDeckDatabase : RoomDatabase() {
    abstract fun getBookmarkDao(): BookmarkDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create the article_content table
                database.execSQL(
                    """
                        CREATE TABLE IF NOT EXISTS `article_content` (
                            `bookmarkId` TEXT NOT NULL,
                            `content` TEXT NOT NULL,
                            PRIMARY KEY(`bookmarkId`),
                            FOREIGN KEY(`bookmarkId`) REFERENCES `bookmarks`(`id`) ON DELETE CASCADE
                        )
                    """
                )

                // Copy data from bookmarks.articleContent to article_content.content
                database.execSQL(
                    """
                        INSERT INTO article_content (bookmarkId, content)
                        SELECT id, articleContent FROM bookmarks WHERE articleContent IS NOT NULL
                    """
                )

                // Remove the articleContent column from the bookmarks table
                database.execSQL(
                    """
                        ALTER TABLE bookmarks DROP COLUMN articleContent
                    """
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `remote_bookmark_ids` ( `id` TEXT NOT NULL, PRIMARY KEY(`id`))
                """
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE bookmarks ADD COLUMN embed TEXT")
                database.execSQL("ALTER TABLE bookmarks ADD COLUMN embedHostname TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add content state tracking columns
                database.execSQL("ALTER TABLE bookmarks ADD COLUMN contentState INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE bookmarks ADD COLUMN contentFailureReason TEXT")

                // Backfill: mark bookmarks that already have downloaded content
                database.execSQL("""
                    UPDATE bookmarks SET contentState = 1
                    WHERE EXISTS (SELECT 1 FROM article_content WHERE article_content.bookmarkId = bookmarks.id)
                """)

                // Backfill: mark non-article types as permanent no content
                database.execSQL("""
                    UPDATE bookmarks SET contentState = 3, contentFailureReason = 'Non-article type'
                    WHERE type IN ('photo', 'video') AND contentState = 0
                """)

                // Backfill: mark articles with no server-side article as permanent no content
                database.execSQL("""
                    UPDATE bookmarks SET contentState = 3, contentFailureReason = 'No article available on server'
                    WHERE hasArticle = 0 AND type = 'article' AND contentState = 0
                """)
            }
        }
    }

}
