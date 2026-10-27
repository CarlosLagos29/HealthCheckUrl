package dev.healthcheck.check

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class FollowedResponse(
    val code: Int,
    val finalUrl: HttpUrl,
    val redirects: Int,
)

class TooManyRedirectsException(maxRedirects: Int) : IOException("More than $maxRedirects redirects")

class RedirectFollower(
    private val client: OkHttpClient,
    private val maxRedirects: Int,
) {

    fun execute(request: Request): FollowedResponse {
        var current = request
        var redirects = 0

        while (true) {
            val next = client.newCall(current).execute().use { response ->
                val location = response.header("Location")
                if (response.code !in REDIRECT_CODES || location == null) {
                    return FollowedResponse(response.code, current.url, redirects)
                }
                current.url.resolve(location) ?: return FollowedResponse(response.code, current.url, redirects)
            }

            if (redirects == maxRedirects) throw TooManyRedirectsException(maxRedirects)

            redirects++
            current = current.newBuilder().url(next).build()
        }
    }

    private companion object {
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
