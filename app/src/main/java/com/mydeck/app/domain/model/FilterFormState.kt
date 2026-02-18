package com.mydeck.app.domain.model

data class FilterFormState(
    val search: String? = null,
    val types: Set<Bookmark.Type> = emptySet(),
    val progress: Set<ProgressFilter> = emptySet(),
    val isFavorite: Boolean? = null,
    val isArchived: Boolean? = null,
) {
    fun hasActiveFilters(): Boolean =
        search != null ||
        types.isNotEmpty() ||
        progress.isNotEmpty() ||
        isFavorite != null ||
        isArchived != null

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
