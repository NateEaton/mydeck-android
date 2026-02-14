package com.mydeck.app.io.rest

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

class UrlInterceptor @Inject constructor(
    private val baseUrlProvider: BaseUrlProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        val originalUrl = request.url.toString()

        val baseUrl = baseUrlProvider.getBaseUrl()

        if (baseUrl.isNullOrEmpty()) {
            throw IOException("baseUrl is not set")
        } else {
            val newUrl = originalUrl.replace(
                "http://readeck.invalid",
                baseUrl
            )
            val newRequest: Request = request.newBuilder()
                .url(newUrl)
                .build()

            return chain.proceed(newRequest)
        }
    }
}
