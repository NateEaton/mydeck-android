package com.mydeck.app.io.db

import androidx.room.TypeConverter
import com.mydeck.app.io.db.model.BookmarkEntity
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? {
        return value?.let { Instant.fromEpochMilliseconds(it) }
    }

    @TypeConverter
    fun instantToTimestamp(instant: Instant?): Long? {
        return instant?.toEpochMilliseconds()
    }

    @TypeConverter
    fun fromStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return try {
            json.decodeFromString<List<String>>(value)
        } catch (jsonError: Exception) {
            // Try CSV fallback for v6 compatibility or data recovery scenarios
            try {
                value.split(",").filter { it.isNotEmpty() }
            } catch (csvError: Exception) {
                // Log both errors for diagnostics
                Timber.w(jsonError, "Failed to deserialize labels as JSON, CSV fallback also failed. Value: $value")
                emptyList()
            }
        }
    }

    @TypeConverter
    fun stringListToString(list: List<String>?): String {
        return if (list.isNullOrEmpty()) "" else json.encodeToString(list)
    }

    @TypeConverter
    fun fromState(state: BookmarkEntity.State): Int {
        return when (state) {
            BookmarkEntity.State.LOADED -> BookmarkEntity.State.LOADED.value
            BookmarkEntity.State.ERROR -> BookmarkEntity.State.ERROR.value
            BookmarkEntity.State.LOADING -> BookmarkEntity.State.LOADING.value
        }
    }

    @TypeConverter
    fun toState(stateValue: Int): BookmarkEntity.State {
        return when (stateValue) {
            BookmarkEntity.State.LOADED.value -> BookmarkEntity.State.LOADED
            BookmarkEntity.State.ERROR.value -> BookmarkEntity.State.ERROR
            BookmarkEntity.State.LOADING.value -> BookmarkEntity.State.LOADING
            else -> BookmarkEntity.State.ERROR
        }
    }

    @TypeConverter
    fun fromType(state: BookmarkEntity.Type): String {
        return when (state) {
            BookmarkEntity.Type.ARTICLE -> BookmarkEntity.Type.ARTICLE.value
            BookmarkEntity.Type.PHOTO -> BookmarkEntity.Type.PHOTO.value
            BookmarkEntity.Type.VIDEO -> BookmarkEntity.Type.VIDEO.value
        }
    }

    @TypeConverter
    fun toType(stateValue: String): BookmarkEntity.Type {
        return when (stateValue) {
            BookmarkEntity.Type.ARTICLE.value -> BookmarkEntity.Type.ARTICLE
            BookmarkEntity.Type.PHOTO.value -> BookmarkEntity.Type.PHOTO
            BookmarkEntity.Type.VIDEO.value -> BookmarkEntity.Type.VIDEO
            else -> throw IllegalStateException("$stateValue can not be converted to BookmarkEntity.Type")
        }
    }

    @TypeConverter
    fun fromContentState(contentState: BookmarkEntity.ContentState): Int {
        return contentState.value
    }

    @TypeConverter
    fun toContentState(value: Int): BookmarkEntity.ContentState {
        return when (value) {
            BookmarkEntity.ContentState.NOT_ATTEMPTED.value -> BookmarkEntity.ContentState.NOT_ATTEMPTED
            BookmarkEntity.ContentState.DOWNLOADED.value -> BookmarkEntity.ContentState.DOWNLOADED
            BookmarkEntity.ContentState.DIRTY.value -> BookmarkEntity.ContentState.DIRTY
            BookmarkEntity.ContentState.PERMANENT_NO_CONTENT.value -> BookmarkEntity.ContentState.PERMANENT_NO_CONTENT
            else -> BookmarkEntity.ContentState.NOT_ATTEMPTED
        }
    }
}
