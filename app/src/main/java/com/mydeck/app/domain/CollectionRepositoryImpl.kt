package com.mydeck.app.domain

import com.mydeck.app.coroutine.IoDispatcher
import com.mydeck.app.domain.model.Collection
import com.mydeck.app.domain.model.FilterFormState
import com.mydeck.app.domain.model.toCreateCollectionDto
import com.mydeck.app.domain.model.toUpdateCollectionJson
import com.mydeck.app.domain.model.toDomain
import com.mydeck.app.domain.model.toEntity
import com.mydeck.app.io.db.MyDeckDatabase
import com.mydeck.app.io.db.dao.CollectionDao
import com.mydeck.app.io.db.model.CollectionEntity
import com.mydeck.app.io.rest.ReadeckApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class CollectionRepositoryImpl @Inject constructor(
    private val database: MyDeckDatabase,
    private val collectionDao: CollectionDao,
    private val readeckApi: ReadeckApi,
    @IoDispatcher
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CollectionRepository {

    override fun observeCollections(): Flow<List<Collection>> =
        collectionDao.observeCollections().map { entities -> entities.map { it.toDomain() } }

    override suspend fun refreshCollections(): Result<Unit> = withContext(dispatcher) {
        try {
            val all = mutableListOf<CollectionEntity>()
            var offset = 0
            // Guard against an unbounded loop if the server keeps returning full pages.
            var page = 0
            while (page < MAX_PAGES) {
                val response = readeckApi.getCollections(limit = PAGE_SIZE, offset = offset)
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Failed to fetch collections: HTTP ${response.code()}")
                    )
                }
                val body = response.body().orEmpty()
                // Drop soft-deleted collections; only non-deleted ones are cached.
                all += body.filterNot { it.isDeleted == true }.map { it.toEntity() }
                if (body.size < PAGE_SIZE) break
                offset += PAGE_SIZE
                page++
            }
            if (page >= MAX_PAGES) {
                Timber.w("refreshCollections hit MAX_PAGES ($MAX_PAGES); some collections may be missing")
            }
            // Atomic cache replace.
            database.performTransaction {
                collectionDao.deleteAll()
                collectionDao.upsertCollections(all)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "refreshCollections failed")
            Result.failure(e)
        }
    }

    override suspend fun createCollection(
        name: String,
        filter: FilterFormState,
    ): Result<Collection> = withContext(dispatcher) {
        try {
            val response = readeckApi.createCollection(filter.toCreateCollectionDto(name))
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed to create collection: HTTP ${response.code()}")
                )
            }
            // POST returns no id in the body; the new collection's id is the trailing path segment
            // of the Location header. Fetch the full object by id to cache it.
            val id = response.headers()[ReadeckApi.Header.LOCATION]
                ?.substringBefore('?')
                ?.trimEnd('/')
                ?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() }
                ?: return@withContext Result.failure(
                    Exception("Create collection response missing Location header")
                )

            val fetched = readeckApi.getCollectionById(id)
            val dto = fetched.body()
            if (!fetched.isSuccessful || dto == null) {
                return@withContext Result.failure(
                    Exception("Failed to load created collection $id: HTTP ${fetched.code()}")
                )
            }
            val entity = dto.toEntity()
            collectionDao.upsertCollections(listOf(entity))
            Result.success(entity.toDomain())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "createCollection failed")
            Result.failure(e)
        }
    }

    override suspend fun updateCollection(
        id: String,
        name: String,
        filter: FilterFormState,
    ): Result<Collection> = withContext(dispatcher) {
        try {
            // PATCH returns only a partial "updated fields" summary (no id/href/created), so we don't
            // read its body; on success re-fetch the full object to cache (mirrors createCollection).
            // The body sends explicit nulls (toUpdateCollectionJson) so cleared criteria are cleared.
            val response = readeckApi.updateCollection(id, filter.toUpdateCollectionJson(name))
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed to update collection $id: HTTP ${response.code()}")
                )
            }
            val fetched = readeckApi.getCollectionById(id)
            val dto = fetched.body()
            if (!fetched.isSuccessful || dto == null) {
                return@withContext Result.failure(
                    Exception("Failed to load updated collection $id: HTTP ${fetched.code()}")
                )
            }
            val entity = dto.toEntity()
            collectionDao.upsertCollections(listOf(entity))
            Result.success(entity.toDomain())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "updateCollection failed")
            Result.failure(e)
        }
    }

    override suspend fun deleteCollection(id: String): Result<Unit> = withContext(dispatcher) {
        try {
            val response = readeckApi.deleteCollection(id)
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed to delete collection $id: HTTP ${response.code()}")
                )
            }
            collectionDao.deleteById(id)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "deleteCollection failed")
            Result.failure(e)
        }
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 100
    }
}
