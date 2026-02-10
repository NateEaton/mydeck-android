package com.mydeck.app.util

import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import timber.log.Timber

/**
 * Custom Coil Fetcher for loading dynamic SVG thumbnails.
 * Handles URIs with the "dynamic" scheme.
 *
 * Coil's built-in StringMapper converts String data to Uri before
 * fetchers run, so this must be a Fetcher.Factory<Uri> not Factory<String>.
 */
class DynamicSvgFetcher(private val uri: Uri, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val title = DynamicSvgUri.decode(uri.toString())
        if (title == null) {
            Timber.w("Could not decode dynamic URI: $uri")
            throw IllegalArgumentException("Invalid dynamic URI: $uri")
        }

        Timber.d("Fetching dynamic SVG for: $title")
        val svg = DynamicSvgGenerator.generateSvg(title)
        Timber.d("Generated SVG of length: ${svg.length}")

        val buffer = Buffer()
        buffer.writeUtf8(svg)

        return SourceFetchResult(
            source = ImageSource(
                source = buffer,
                fileSystem = options.fileSystem,
            ),
            mimeType = "image/svg+xml",
            dataSource = DataSource.MEMORY,
        )
    }

    companion object {
        fun Factory() = Fetcher.Factory<Uri> { data, options, _ ->
            if (data.scheme == DynamicSvgUri.SCHEME) {
                DynamicSvgFetcher(data, options)
            } else {
                null
            }
        }
    }
}
