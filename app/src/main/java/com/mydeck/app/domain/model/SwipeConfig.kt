package com.mydeck.app.domain.model

data class SwipeConfig(
    val enabled: Boolean,
    val leftAction: SwipeAction,
    val rightAction: SwipeAction
) {
    companion object {
        val Default = SwipeConfig(
            enabled = true,
            leftAction = SwipeAction.DELETE,
            rightAction = SwipeAction.ARCHIVE
        )
    }
}
