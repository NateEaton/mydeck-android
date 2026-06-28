package com.mydeck.app.domain

import com.mydeck.app.domain.model.Collection
import com.mydeck.app.domain.model.FilterFormState
import kotlinx.coroutines.flow.Flow

interface CollectionRepository {
    /** Emits the local cached list of non-deleted collections. */
    fun observeCollections(): Flow<List<Collection>>

    /** Fetches all collections from the server and replaces the local cache atomically. */
    suspend fun refreshCollections(): Result<Unit>

    /** Creates a new collection server-side and caches it locally. */
    suspend fun createCollection(name: String, filter: FilterFormState): Result<Collection>

    /** Updates an existing collection's name and/or filter server-side, then updates the cache. */
    suspend fun updateCollection(id: String, name: String, filter: FilterFormState): Result<Collection>

    /** Soft-deletes a collection via PATCH is_deleted=true, then removes it from the local cache. */
    suspend fun deleteCollection(id: String): Result<Unit>
}
