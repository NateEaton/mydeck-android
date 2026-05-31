package com.mydeck.app.io.rest

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class ReadeckHttpPolicyInterceptor(
    private val allowInsecureHttp: Boolean
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!allowInsecureHttp && request.url.scheme.equals("http", ignoreCase = true)) {
            throw HttpBlockedByBuildPolicyException(request.url)
        }
        return chain.proceed(request)
    }
}

class HttpBlockedByBuildPolicyException(
    url: HttpUrl
) : IOException("HTTP Readeck server URLs are not allowed in this MyDeck build: ${url.host}")

fun Throwable.isHttpBlockedByBuildPolicy(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is HttpBlockedByBuildPolicyException) {
            return true
        }
        current = current.cause
    }
    return false
}

object ReadeckNetworkPolicy {
    fun isSavedHttpUrlBlocked(url: String?, allowInsecureHttp: Boolean): Boolean {
        return !allowInsecureHttp && url?.trimStart()?.startsWith("http://", ignoreCase = true) == true
    }
}
