package com.mydeck.app.util

import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetcher
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8

/**
 * Custom Coil Fetcher for generating dynamic SVG thumbnails.
 *
 * Handles [DynamicSvgData] objects and generates unique SVG thumbnails
 * based on the bookmark title.
 */
class DynamicSvgFetcher : Fetcher {
    override suspend fun fetch(): FetchResult? = null

    companion object {
        fun Factory(): Fetcher.Factory = Fetcher.Factory { data, options, imageLoader ->
            if (data is DynamicSvgData) {
                DynamicSvgFetcherImpl(data)
            } else {
                null
            }
        }
    }
}

private class DynamicSvgFetcherImpl(private val data: DynamicSvgData) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val svg = DynamicSvgGenerator.generateSvg(data.title)
        val bytes = svg.encodeUtf8().toByteArray()
        val buffer = Buffer()
        buffer.write(bytes)

        return SourceFetcher.SourceFetchResult(
            source = ImageSource(source = buffer),
            mimeType = "image/svg+xml"
        )
    }
}
