package com.mydeck.app.domain.usecase

import android.content.Context
import coil3.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

class ThumbnailPrefetchUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun prefetchThumbnails(thumbnailUrls: List<String>) {
        if (thumbnailUrls.isEmpty()) {
            return
        }

        Timber.d("Prefetching ${thumbnailUrls.size} thumbnail images")

        val imageLoader = context.imageLoader
        thumbnailUrls.forEach { url ->
            try {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .build()
                imageLoader.enqueue(request)
            } catch (e: Exception) {
                Timber.w(e, "Failed to prefetch thumbnail: $url")
            }
        }

        Timber.d("Thumbnail prefetch enqueued")
    }
}
