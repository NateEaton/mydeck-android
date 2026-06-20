package com.mydeck.app.io.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mydeck.app.io.db.model.ContentPackageEntity
import com.mydeck.app.io.db.model.ContentResourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentPackageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackage(pkg: ContentPackageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResources(resources: List<ContentResourceEntity>)

    @Query("SELECT * FROM content_package WHERE bookmarkId = :bookmarkId")
    suspend fun getPackage(bookmarkId: String): ContentPackageEntity?

    /** Reactive provenance of a bookmark's package (null when none) — drives the reader pin state. */
    @Query("SELECT source FROM content_package WHERE bookmarkId = :bookmarkId")
    fun observePackageSource(bookmarkId: String): Flow<String?>

    @Query("SELECT * FROM content_resource WHERE bookmarkId = :bookmarkId")
    suspend fun getResources(bookmarkId: String): List<ContentResourceEntity>

    @Query("DELETE FROM content_resource WHERE bookmarkId = :bookmarkId")
    suspend fun deleteResources(bookmarkId: String)

    @Query("DELETE FROM content_package WHERE bookmarkId = :bookmarkId")
    suspend fun deletePackage(bookmarkId: String)

    /**
     * Update only the provenance of an existing package (W2). Used by the multi-select
     * "Available offline" action to flip an already-downloaded AUTOMATIC package to MANUAL
     * without re-fetching. No-op (zero rows) when no package exists for [bookmarkId].
     */
    @Query("UPDATE content_package SET source = :source WHERE bookmarkId = :bookmarkId")
    suspend fun updateContentPackageSource(bookmarkId: String, source: String)

    @Transaction
    suspend fun replacePackageAndResources(
        pkg: ContentPackageEntity,
        resources: List<ContentResourceEntity>
    ) {
        deleteResources(pkg.bookmarkId)
        deletePackage(pkg.bookmarkId)
        insertPackage(pkg)
        insertResources(resources)
    }

    @Query("DELETE FROM content_package")
    suspend fun deleteAllPackages()

    @Query("DELETE FROM content_resource")
    suspend fun deleteAllResources()

    @Transaction
    suspend fun deleteAll() {
        deleteAllResources()
        deleteAllPackages()
    }
}
