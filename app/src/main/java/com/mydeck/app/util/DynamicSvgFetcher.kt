package com.mydeck.app.util

import coil3.annotation.ExperimentalCoilApi
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8

/**
 * Custom Coil Fetcher for generating dynamic SVG thumbnails.
 *
 * Handles [DynamicSvgData] objects and generates unique SVG thumbnails
 * based on the bookmark title.
 */
@OptIn(ExperimentalCoilApi::class)
class DynamicSvgFetcher(private val data: DynamicSvgData) : Fetcher {
    override suspend fun fetch(): Fetcher.Result {
        val svg = DynamicSvgGenerator.generateSvg(data.title)
        val bytes = svg.encodeUtf8().toByteArray()
        val buffer = Buffer().also { it.write(bytes) }

        return SourceFetchResult(
            source = buffer,
            mimeType = "image/svg+xml",
            dataSource = coil3.request.DataSource.MEMORY
        )
    }

    companion object {
        @OptIn(ExperimentalCoilApi::class)
        fun Factory() = Fetcher.Factory { data, options, imageLoader ->
            when (data) {
                is DynamicSvgData -> DynamicSvgFetcher(data)
                else -> null
            }
        }
    }
}
