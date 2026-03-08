package com.mydeck.app.domain.model

data class CachedServerInfo(
    val canonical: String,
    val release: String,
    val build: String,
    val features: List<String>,
)
