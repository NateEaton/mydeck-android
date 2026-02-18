package com.mydeck.app.domain.model

import kotlinx.datetime.Instant

data class FilterFormState(
    val search: String? = null,
    val title: String? = null,
    val author: String? = null,
    val site: String? = null,
    val label: String? = null,
    val fromDate: Instant? = null,
    val toDate: Instant? = null,
    val types: Set<Bookmark.Type> = emptySet(),
    val progress: Set<ProgressFilter> = emptySet(),
    val isFavorite: Boolean? = null,
    val isArchived: Boolean? = null,
    val isLoaded: Boolean? = null,
    val withLabels: Boolean? = null,
    val withErrors: Boolean? = null,
) {
    fun hasActiveFilters(): Boolean =
        search != null ||
        title != null ||
        author != null ||
        site != null ||
        label != null ||
        fromDate != null ||
        toDate != null ||
        types.isNotEmpty() ||
        progress.isNotEmpty() ||
        isFavorite != null ||
        isArchived != null ||
        isLoaded != null ||
        withLabels != null ||
        withErrors != null

    companion object {
        fun fromPreset(preset: DrawerPreset): FilterFormState = when (preset) {
            DrawerPreset.MY_LIST -> FilterFormState(isArchived = false)
            DrawerPreset.ARCHIVE -> FilterFormState(isArchived = true)
            DrawerPreset.FAVORITES -> FilterFormState(isFavorite = true)
            DrawerPreset.ARTICLES -> FilterFormState(types = setOf(Bookmark.Type.Article))
            DrawerPreset.VIDEOS -> FilterFormState(types = setOf(Bookmark.Type.Video))
            DrawerPreset.PICTURES -> FilterFormState(types = setOf(Bookmark.Type.Picture))
        }
    }
}
