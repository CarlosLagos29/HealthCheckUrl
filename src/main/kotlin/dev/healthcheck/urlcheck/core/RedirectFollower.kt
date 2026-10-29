package dev.healthcheck.urlcheck.core

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InterruptedIOException
import java.time.Duration
import java.util.concurrent.TimeUnit

data class FollowedResponse(
    val code: Int,
    val finalUrl: HttpUrl,
    val redirects: Int,
)

class TooManyRedirectsException(maxRedirects: Int) : IOException("More than $maxRedirects redirects")

class FollowFailedException(
    val lastUrl: HttpUrl,
    val redirects: Int,
    override val cause: IOException,
) : IOException(cause.message, cause)

class RedirectFollower(
    private val client: OkHttpClient,
    private val maxRedirects: Int,
    private val totalTimeout: Duration,
) {

    fun execute(request: Request): FollowedResponse {
        val deadline = System.nanoTime() + totalTimeout.toNanos()
        var current = request
        var redirects = 0

        try {
            while (true) {
                val (code, location) = send(current, deadline)

                if (current.method == "HEAD" && code in HEAD_NOT_SUPPORTED_CODES) {
                    current = current.newBuilder().get().build()
                    continue
                }

                if (code !in REDIRECT_CODES || location == null) {
                    return FollowedResponse(code, current.url, redirects)
                }

                val next = current.url.resolve(location) ?: return FollowedResponse(code, current.url, redirects)

                if (redirects == maxRedirects) throw TooManyRedirectsException(maxRedirects)

                redirects++
                current = current.newBuilder().url(next).build()
            }
        } catch (e: IOException) {
            throw FollowFailedException(current.url, redirects, e)
        }
    }

    private fun send(request: Request, deadline: Long): Pair<Int, String?> {
        val remaining = deadline - System.nanoTime()
        if (remaining <= 0) throw InterruptedIOException("timeout")

        val call = client.newCall(request)
        call.timeout().timeout(remaining, TimeUnit.NANOSECONDS)
        return call.execute().use { it.code to it.header("Location") }
    }

    private companion object {
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        val HEAD_NOT_SUPPORTED_CODES = setOf(405, 501)
    }
}
