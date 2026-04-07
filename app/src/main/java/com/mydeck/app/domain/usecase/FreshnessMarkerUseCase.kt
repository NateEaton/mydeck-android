package com.mydeck.app.domain.usecase

import com.mydeck.app.io.db.dao.BookmarkDao
import com.mydeck.app.io.db.dao.ContentPackageDao
import com.mydeck.app.io.db.model.BookmarkEntity
import com.mydeck.app.util.parseInstantLenient
import timber.log.Timber
import javax.inject.Inject

class FreshnessMarkerUseCase @Inject constructor(
    private val contentPackageDao: ContentPackageDao,
    private val bookmarkDao: BookmarkDao
) {
    /**
     * For each id, if a content package exists and the bookmark is currently
     * DOWNLOADED but the server 'updated' timestamp is newer than the package's
     * recorded sourceUpdated, mark the bookmark content state as DIRTY to
     * trigger a refresh.
     */
    suspend fun markDirtyForBookmarks(bookmarkIds: List<String>) {
        if (bookmarkIds.isEmpty()) return
        for (id in bookmarkIds) {
            try {
                val pkg = contentPackageDao.getPackage(id) ?: continue
                val pkgUpdated = parseInstantLenient(pkg.sourceUpdated) ?: continue
                val entity = runCatching { bookmarkDao.getBookmarkById(id) }.getOrNull() ?: continue
                if (entity.contentState == BookmarkEntity.ContentState.DOWNLOADED) {
                    val bookmarkUpdated = entity.updated
                    if (bookmarkUpdated > pkgUpdated) {
                        bookmarkDao.updateContentState(
                            id,
                            BookmarkEntity.ContentState.DIRTY.value,
                            "Server content is newer than local package"
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Freshness mark failed for $id")
            }
        }
    }

}
