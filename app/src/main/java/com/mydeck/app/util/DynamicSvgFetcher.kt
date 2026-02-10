package com.mydeck.app.util

import coil3.decode.ImageSource
import coil3.fetch.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetcher
import coil3.request.Options
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import timber.log.Timber

/**
 * Custom Coil Fetcher for loading dynamic SVG thumbnails.
 * Handles URLs with the "dynamic://" scheme.
 */
class DynamicSvgFetcher(
    private val url: String,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val title = DynamicSvgUri.decode(url)
        if (title == null) {
            Timber.w("Could not decode dynamic URL: $url")
            throw IllegalArgumentException("Invalid dynamic URL: $url")
        }

        Timber.d("Fetching dynamic SVG for: $title")
        val svg = DynamicSvgGenerator.generateSvg(title)
        Timber.d("Generated SVG of length: ${svg.length}")

        val buffer = Buffer()
        buffer.writeUtf8(svg)

        return SourceFetcher.SourceFetchResult(
            source = ImageSource(
                source = buffer,
                fileSystem = options.fileSystem,
            ),
            mimeType = "image/svg+xml",
            dataSource = DataSource.MEMORY,
        )
    }

    companion object {
        fun Factory() = Fetcher.Factory<String> { data, options, imageLoader ->
            if (data.startsWith(DynamicSvgUri.SCHEME)) {
                DynamicSvgFetcher(data, options)
            } else {
                null
            }
        }
    }
}
