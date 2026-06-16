package com.mydeck.app.io.db

import android.content.ContentValues
import android.database.DatabaseUtils
import androidx.room.migration.AutoMigrationSpec
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MyDeckDatabaseMigrationTest {
    private val TEST_DB = "migration-test"

    @Test
    @Throws(IOException::class)
    fun migrate1To2() {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MyDeckDatabase::class.java,
            emptyList<AutoMigrationSpec>(), // workaround for https://issuetracker.google.com/issues/298459978
            FrameworkSQLiteOpenHelperFactory()
        )
        val db = helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO bookmarks (
                    id,
                    href,
                    created,
                    updated,
                    state,
                    loaded,
                    url,
                    title,
                    siteName,
                    site,
                    authors,
                    lang,
                    textDirection,
                    documentTpe,
                    type,
                    hasArticle,
                    description,
                    isDeleted,
                    isMarked,
                    isArchived,
                    labels,
                    readProgress,
                    wordCount,
                    readingTime,
                    article_src,
                    icon_src,
                    icon_width,
                    icon_height,
                    image_src,
                    image_width,
                    image_height,
                    log_src,
                    props_src,
                    thumbnail_src,
                    thumbnail_width,
                    thumbnail_height,
                    articleContent
                ) VALUES (
                    'id1',
                    'test-href',
                    1672531200000,
                    1672531200000,
                    0,
                    1,
                    'test-url',
                    'test-title',
                    'test-siteName',
                    'test-site',
                    'test-authors',
                    'test-lang',
                    'test-textDirection',
                    'test-documentTpe',
                    'article',
                    1,
                    'test-description',
                    0,
                    0,
                    0,
                    'test-labels',
                    50,
                    100,
                    5,
                    'test-article_src',
                    'test-icon_src',
                    50,
                    50,
                    'test-image_src',
                    200,
                    100,
                    'test-log_src',
                    'test-props_src',
                    'test-thumbnail_src',
                    100,
                    100,
                    'test-articleContent'
                )
                """
            )
            execSQL(
            """
                INSERT INTO bookmarks (
                    id,
                    href,
                    created,
                    updated,
                    state,
                    loaded,
                    url,
                    title,
                    siteName,
                    site,
                    authors,
                    lang,
                    textDirection,
                    documentTpe,
                    type,
                    hasArticle,
                    description,
                    isDeleted,
                    isMarked,
                    isArchived,
                    labels,
                    readProgress,
                    wordCount,
                    readingTime,
                    article_src,
                    icon_src,
                    icon_width,
                    icon_height,
                    image_src,
                    image_width,
                    image_height,
                    log_src,
                    props_src,
                    thumbnail_src,
                    thumbnail_width,
                    thumbnail_height,
                    articleContent
                ) VALUES (
                    'id2',
                    'test-href',
                    1672531200000,
                    1672531200000,
                    0,
                    1,
                    'test-url',
                    'test-title',
                    'test-siteName',
                    'test-site',
                    'test-authors',
                    'test-lang',
                    'test-textDirection',
                    'test-documentTpe',
                    'video',
                    1,
                    'test-description',
                    0,
                    0,
                    0,
                    'test-labels',
                    50,
                    100,
                    5,
                    'test-article_src',
                    'test-icon_src',
                    50,
                    50,
                    'test-image_src',
                    200,
                    100,
                    'test-log_src',
                    'test-props_src',
                    'test-thumbnail_src',
                    100,
                    100,
                    null
                )
                """
            )
            execSQL(
                """
                INSERT INTO bookmarks (
                    id,
                    href,
                    created,
                    updated,
                    state,
                    loaded,
                    url,
                    title,
                    siteName,
                    site,
                    authors,
                    lang,
                    textDirection,
                    documentTpe,
                    type,
                    hasArticle,
                    description,
                    isDeleted,
                    isMarked,
                    isArchived,
                    labels,
                    readProgress,
                    wordCount,
                    readingTime,
                    article_src,
                    icon_src,
                    icon_width,
                    icon_height,
                    image_src,
                    image_width,
                    image_height,
                    log_src,
                    props_src,
                    thumbnail_src,
                    thumbnail_width,
                    thumbnail_height,
                    articleContent
                ) VALUES (
                    'id3',
                    'test-href',
                    1672531200000,
                    1672531200000,
                    0,
                    1,
                    'test-url',
                    'test-title',
                    'test-siteName',
                    'test-site',
                    'test-authors',
                    'test-lang',
                    'test-textDirection',
                    'test-documentTpe',
                    'photo',
                    1,
                    'test-description',
                    0,
                    0,
                    0,
                    'test-labels',
                    50,
                    100,
                    5,
                    'test-article_src',
                    'test-icon_src',
                    50,
                    50,
                    'test-image_src',
                    200,
                    100,
                    'test-log_src',
                    'test-props_src',
                    'test-thumbnail_src',
                    100,
                    100,
                    null
                )
                """
            )
            close()
        }

        val dbV2 = helper.runMigrationsAndValidate(TEST_DB, 2, true, MyDeckDatabase.MIGRATION_1_2)

        val cursor = dbV2.query("SELECT content FROM article_content WHERE bookmarkId = 'id1'")
        try {
            assertTrue(cursor.moveToFirst())
            val content = cursor.getString(0)
            cursor.close()
            assertTrue(content == "test-articleContent")
        } finally {
            cursor.close()
        }

        var cursor2 = dbV2.query("PRAGMA table_info('bookmarks')")
        try {
            while (cursor2.moveToNext()) {
                var contentValues = ContentValues()
                DatabaseUtils.cursorRowToContentValues(cursor2, contentValues)
                assertNotEquals("articleContent", contentValues.get("name"))
                println("$contentValues")
            }
        } finally {
            cursor2.close()
        }
        dbV2.close()
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3() {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MyDeckDatabase::class.java,
            emptyList<AutoMigrationSpec>(), // workaround for https://issuetracker.google.com/issues/298459978
            FrameworkSQLiteOpenHelperFactory()
        )
        val db = helper.createDatabase(TEST_DB, 2)

        val dbV3 = helper.runMigrationsAndValidate(TEST_DB, 3, true, MyDeckDatabase.MIGRATION_2_3)

        var cursor = dbV3.query("PRAGMA table_info('remote_bookmark_ids')")
        try {
            cursor.moveToFirst()
            var contentValues = ContentValues()
            DatabaseUtils.cursorRowToContentValues(cursor, contentValues)
            assertEquals("id", contentValues.get("name"))
            println("$contentValues")
        } finally {
            cursor.close()
        }
        dbV3.close()
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate14To15RecreatesRemoteBookmarkIdsWithSyncRunId() {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MyDeckDatabase::class.java,
            emptyList<AutoMigrationSpec>(), // workaround for https://issuetracker.google.com/issues/298459978
            FrameworkSQLiteOpenHelperFactory()
        )
        val db = helper.createDatabase(TEST_DB, 14).apply {
            execSQL("INSERT INTO remote_bookmark_ids (id) VALUES ('stale-id')")
            close()
        }

        val dbV15 = helper.runMigrationsAndValidate(TEST_DB, 15, true, MyDeckDatabase.MIGRATION_14_15)

        val columns = mutableMapOf<String, ContentValues>()
        var cursor = dbV15.query("PRAGMA table_info('remote_bookmark_ids')")
        try {
            while (cursor.moveToNext()) {
                val contentValues = ContentValues()
                DatabaseUtils.cursorRowToContentValues(cursor, contentValues)
                columns[contentValues.getAsString("name")] = contentValues
            }
        } finally {
            cursor.close()
        }

        assertEquals(2, columns.size)
        assertEquals(1, columns.getValue("syncRunId").getAsInteger("notnull"))
        assertEquals(1, columns.getValue("syncRunId").getAsInteger("pk"))
        assertEquals(1, columns.getValue("id").getAsInteger("notnull"))
        assertEquals(2, columns.getValue("id").getAsInteger("pk"))

        cursor = dbV15.query("SELECT COUNT(*) FROM remote_bookmark_ids")
        try {
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        } finally {
            cursor.close()
        }

        dbV15.close()
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate16To17AddsContentPackageSourceDefaultingAutomatic() {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MyDeckDatabase::class.java,
            emptyList<AutoMigrationSpec>(), // workaround for https://issuetracker.google.com/issues/298459978
            FrameworkSQLiteOpenHelperFactory()
        )
        val db = helper.createDatabase(TEST_DB, 16).apply {
            // A pre-W2 content_package row (no source column yet).
            execSQL(
                """
                INSERT INTO content_package
                    (bookmarkId, packageKind, hasHtml, hasResources, sourceUpdated, lastRefreshed, localBasePath)
                VALUES ('bm1', 'ARTICLE', 1, 1, '2026-01-01T00:00:00Z', 0, 'offline_content/bm1')
                """
            )
            close()
        }

        val dbV17 = helper.runMigrationsAndValidate(TEST_DB, 17, true, MyDeckDatabase.MIGRATION_16_17)

        // The new column exists and is NOT NULL with default 'AUTOMATIC'.
        val columns = mutableMapOf<String, ContentValues>()
        val info = dbV17.query("PRAGMA table_info('content_package')")
        try {
            while (info.moveToNext()) {
                val cv = ContentValues()
                DatabaseUtils.cursorRowToContentValues(info, cv)
                columns[cv.getAsString("name")] = cv
            }
        } finally {
            info.close()
        }
        assertTrue(columns.containsKey("source"))
        assertEquals(1, columns.getValue("source").getAsInteger("notnull"))

        // The pre-existing row was backfilled to AUTOMATIC.
        val cursor = dbV17.query("SELECT source FROM content_package WHERE bookmarkId = 'bm1'")
        try {
            assertTrue(cursor.moveToFirst())
            assertEquals("AUTOMATIC", cursor.getString(0))
        } finally {
            cursor.close()
        }

        dbV17.close()
        db.close()
    }
}
