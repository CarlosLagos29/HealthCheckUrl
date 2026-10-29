package dev.healthcheck.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.healthcheck.urlcheck.core.HealthCheckResult
import okhttp3.HttpUrl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

class UrlCache(private val ttl: Duration) {

    private companion object {
        val LOG: Logger = LoggerFactory.getLogger(UrlCache::class.java)
        const val MAX_ENTRIES = 10_000L
    }

    private val results: Cache<String, HealthCheckResult> = Caffeine.newBuilder()
        .expireAfterWrite(ttl)
        .maximumSize(MAX_ENTRIES)
        .build()

    fun get(url: HttpUrl): HealthCheckResult? {
        val result = results.getIfPresent(url.toString())
        if (result != null) {
            LOG.debug("Cache hit: url={} checkedAgo={}s", url, Duration.between(result.checkedAt, Instant.now()).toSeconds())
        }
        return result
    }

    fun save(url: HttpUrl, result: HealthCheckResult) {
        results.put(url.toString(), result)
        LOG.info("Cached: url={}", url)
        LOG.debug("Cached detail: url={} status={} reason={} ttl={}", url, result.status, result.reason, ttl)
    }
}
