package com.mydeck.app.domain.model

import com.mydeck.app.io.db.model.CollectionEntity
import com.mydeck.app.io.rest.model.CollectionDto
import com.mydeck.app.io.rest.model.CreateCollectionDto
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

/**
 * Bi-directional conversion between [FilterFormState] and the Readeck collection DTO/entity shapes.
 *
 * Field mapping (see collections-feature-design.md):
 * - `search` / `title` / `author` / `site` — passthrough.
 * - `label` ↔ `labels` — single string. The API field accepts comma-separated values; we persist the
 *   single active label and load the first segment.
 * - `fromDate` ↔ `range_start`, `toDate` ↔ `range_end` — ISO-8601 [Instant] string.
 * - `types` ↔ `type` — [Bookmark.Type] ↔ "article" / "photo" / "video".
 * - `progress` ↔ `read_status` — [ProgressFilter] ↔ "unread" / "reading" / "read".
 * - `isFavorite` ↔ `is_marked`, `isArchived` ↔ `is_archived` — nullable Boolean passthrough.
 * - `withErrors` ↔ `has_errors`, `withLabels` ↔ `has_labels` — nullable Boolean passthrough
 *   (server-supported on collections, verified against the live API).
 *
 * Local-only fields with no collection-API equivalent are **dropped on persist and default on load**:
 * `isLoaded` (the device-local "Downloaded" state; the server's `is_loaded` is a different,
 * server-side fetch concept not stored on collections) and the reading-time / word-count range
 * fields (`minReadingTime`, `maxReadingTime`, `includeNullReadingTime`, `minWordCount`,
 * `maxWordCount`, `includeNullWordCount`).
 */

// --- enum <-> wire string helpers (shared by DTO and entity conversions) ---

private const val TYPE_ARTICLE = "article"
private const val TYPE_PHOTO = "photo"
private const val TYPE_VIDEO = "video"

private const val READ_UNREAD = "unread"
private const val READ_READING = "reading"
private const val READ_READ = "read"

private fun Bookmark.Type.toWire(): String = when (this) {
    Bookmark.Type.Article -> TYPE_ARTICLE
    Bookmark.Type.Picture -> TYPE_PHOTO
    Bookmark.Type.Video -> TYPE_VIDEO
}

private fun String.toBookmarkTypeOrNull(): Bookmark.Type? = when (this) {
    TYPE_ARTICLE -> Bookmark.Type.Article
    TYPE_PHOTO -> Bookmark.Type.Picture
    TYPE_VIDEO -> Bookmark.Type.Video
    else -> null
}

private fun ProgressFilter.toWire(): String = when (this) {
    ProgressFilter.UNVIEWED -> READ_UNREAD
    ProgressFilter.IN_PROGRESS -> READ_READING
    ProgressFilter.COMPLETED -> READ_READ
}

private fun String.toProgressFilterOrNull(): ProgressFilter? = when (this) {
    READ_UNREAD -> ProgressFilter.UNVIEWED
    READ_READING -> ProgressFilter.IN_PROGRESS
    READ_READ -> ProgressFilter.COMPLETED
    else -> null
}

private fun parseInstantOrNull(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return try {
        Instant.parse(value)
    } catch (e: Exception) {
        Timber.w(e, "Failed to parse collection date: $value")
        null
    }
}

/** First label of a (possibly comma-separated) API `labels` field, or null. */
private fun firstLabelOrNull(labels: String?): String? =
    labels?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

private fun List<Bookmark.Type>.typesOrNull(): List<String>? =
    map { it.toWire() }.takeIf { it.isNotEmpty() }

private fun Set<ProgressFilter>.progressOrNull(): List<String>? =
    map { it.toWire() }.takeIf { it.isNotEmpty() }

// --- FilterFormState <-> create/update request ---

/**
 * Builds the create/update request body from this filter state. Local-only fields are omitted.
 * [name] and [isPinned] are not part of [FilterFormState] and are supplied by the caller.
 */
fun FilterFormState.toCreateCollectionDto(
    name: String,
    isPinned: Boolean? = null,
): CreateCollectionDto = CreateCollectionDto(
    name = name,
    isPinned = isPinned,
    search = search,
    title = title,
    author = author,
    site = site,
    labels = label,
    type = types.map { it.toWire() }.takeIf { it.isNotEmpty() },
    readStatus = progress.progressOrNull(),
    isMarked = isFavorite,
    isArchived = isArchived,
    hasErrors = withErrors,
    hasLabels = withLabels,
    rangeStart = fromDate?.toString(),
    rangeEnd = toDate?.toString(),
)

/**
 * Builds the PATCH (update) body for a collection. Unlike create, this serialises every managed
 * filter field **explicitly, including nulls**, so clearing a criterion is sent as `null` and the
 * server actually clears it. (The shared Json uses `explicitNulls = false`, which would otherwise
 * omit cleared fields and PATCH would leave them unchanged.) `is_pinned` / `is_deleted` are
 * deliberately omitted so editing a collection never disturbs its pin/deletion state.
 */
fun FilterFormState.toUpdateCollectionJson(name: String): JsonObject = buildJsonObject {
    put("name", name)
    put("search", search)
    put("title", title)
    put("author", author)
    put("site", site)
    put("labels", label)
    put("is_marked", isFavorite)
    put("is_archived", isArchived)
    put("has_errors", withErrors)
    put("has_labels", withLabels)
    put("range_start", fromDate?.toString())
    put("range_end", toDate?.toString())
    put("type", if (types.isEmpty()) JsonNull else JsonArray(types.map { JsonPrimitive(it.toWire()) }))
    put("read_status", if (progress.isEmpty()) JsonNull else JsonArray(progress.map { JsonPrimitive(it.toWire()) }))
}

// --- DTO -> FilterFormState ---

fun CollectionDto.toFilterFormState(): FilterFormState = FilterFormState(
    search = search,
    title = title,
    author = author,
    site = site,
    label = firstLabelOrNull(labels),
    fromDate = parseInstantOrNull(rangeStart),
    toDate = parseInstantOrNull(rangeEnd),
    types = type?.mapNotNull { it.toBookmarkTypeOrNull() }?.toSet() ?: emptySet(),
    progress = readStatus?.mapNotNull { it.toProgressFilterOrNull() }?.toSet() ?: emptySet(),
    isFavorite = isMarked,
    isArchived = isArchived,
    withErrors = hasErrors,
    withLabels = hasLabels,
    // Local-only: isLoaded ("Downloaded") has no collection equivalent → null; the reading-time /
    // word-count fields default (null / false) by omission from this constructor call.
    isLoaded = null,
)

// --- DTO -> Entity (for caching) ---

fun CollectionDto.toEntity(): CollectionEntity = CollectionEntity(
    id = id,
    name = name,
    isPinned = isPinned ?: false,
    search = search,
    title = title,
    author = author,
    site = site,
    labels = labels,
    type = type ?: emptyList(),
    readStatus = readStatus ?: emptyList(),
    isMarked = isMarked,
    isArchived = isArchived,
    hasErrors = hasErrors,
    hasLabels = hasLabels,
    rangeStart = rangeStart,
    rangeEnd = rangeEnd,
    created = (parseInstantOrNull(created) ?: Instant.fromEpochMilliseconds(0)).toEpochMilliseconds(),
    updated = (parseInstantOrNull(updated) ?: Instant.fromEpochMilliseconds(0)).toEpochMilliseconds(),
)

// --- Entity -> Domain ---

fun CollectionEntity.toFilterFormState(): FilterFormState = FilterFormState(
    search = search,
    title = title,
    author = author,
    site = site,
    label = firstLabelOrNull(labels),
    fromDate = parseInstantOrNull(rangeStart),
    toDate = parseInstantOrNull(rangeEnd),
    types = type.mapNotNull { it.toBookmarkTypeOrNull() }.toSet(),
    progress = readStatus.mapNotNull { it.toProgressFilterOrNull() }.toSet(),
    isFavorite = isMarked,
    isArchived = isArchived,
    withErrors = hasErrors,
    withLabels = hasLabels,
    // Local-only: isLoaded ("Downloaded") has no persisted equivalent (see CollectionDto.toFilterFormState).
    isLoaded = null,
)

fun CollectionEntity.toDomain(): Collection = Collection(
    id = id,
    name = name,
    isPinned = isPinned,
    filter = toFilterFormState(),
    created = Instant.fromEpochMilliseconds(created),
    updated = Instant.fromEpochMilliseconds(updated),
)
