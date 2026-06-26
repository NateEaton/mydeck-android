package com.mydeck.app.domain.model

/** How the label-search field matches typed text against label names. */
enum class LabelSearchMatching {
    /** Match anywhere in the label name (infix) — current/default behavior. */
    CONTAINS,
    /** Match only labels whose name begins with the typed text (prefix). */
    STARTS_WITH
}

/** How label-search results are ordered. */
enum class LabelSearchSort {
    /** A→Z — current/default behavior. */
    ALPHABETICAL,
    /** Most-used first, by the label's bookmark count (descending). */
    BY_FREQUENCY
}
