package dev.healthcheck.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.healthcheck.urlcheck.core.HealthCheckResult
import okhttp3.HttpUrl
import java.time.Duration

class UrlCache(ttl: Duration) {

    private companion object {
        const val MAX_ENTRIES = 10_000L
    }

    private val results: Cache<String, HealthCheckResult> = Caffeine.newBuilder()
        .expireAfterWrite(ttl)
        .maximumSize(MAX_ENTRIES)
        .build()

    fun get(url: HttpUrl): HealthCheckResult? = results.getIfPresent(url.toString())

    fun save(url: HttpUrl, result: HealthCheckResult) = results.put(url.toString(), result)
}
