package com.mydeck.app.domain.model

import com.mydeck.app.io.db.model.CollectionEntity
import com.mydeck.app.io.rest.model.CollectionDto
import com.mydeck.app.io.rest.model.CreateCollectionDto
import kotlinx.datetime.Instant
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
 *
 * Local-only fields with no API equivalent are **dropped on persist and default on load**:
 * `isLoaded`, `withLabels`, `withErrors`, and the reading-time / word-count range fields
 * (`minReadingTime`, `maxReadingTime`, `includeNullReadingTime`, `minWordCount`, `maxWordCount`,
 * `includeNullWordCount`).
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
    rangeStart = fromDate?.toString(),
    rangeEnd = toDate?.toString(),
)

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
    // Local-only fields (no API equivalent): isLoaded/withLabels/withErrors default null; the
    // reading-time/word-count fields default (null / false) by omission from this constructor call.
    isLoaded = null,
    withLabels = null,
    withErrors = null,
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
    // Local-only fields default as in CollectionDto.toFilterFormState (no persisted equivalent).
    isLoaded = null,
    withLabels = null,
    withErrors = null,
)

fun CollectionEntity.toDomain(): Collection = Collection(
    id = id,
    name = name,
    isPinned = isPinned,
    filter = toFilterFormState(),
    created = Instant.fromEpochMilliseconds(created),
    updated = Instant.fromEpochMilliseconds(updated),
)
