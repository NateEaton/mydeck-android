package com.mydeck.app.domain.model

enum class CollectionSortOption {
    DATE_NEWEST, DATE_OLDEST, NAME_A_TO_Z, NAME_Z_TO_A;

    val comparator: Comparator<Collection> get() = when (this) {
        DATE_NEWEST -> compareByDescending { it.created }
        DATE_OLDEST -> compareBy { it.created }
        NAME_A_TO_Z -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        NAME_Z_TO_A -> Comparator { a, b -> String.CASE_INSENSITIVE_ORDER.compare(b.name, a.name) }
    }

    val isDescending: Boolean get() = this == DATE_NEWEST || this == NAME_Z_TO_A
}
