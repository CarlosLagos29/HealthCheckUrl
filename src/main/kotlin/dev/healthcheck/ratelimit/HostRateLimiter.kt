package dev.healthcheck.ratelimit

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.healthcheck.config.HealthCheckProperties
import io.github.bucket4j.Bucket

class HostRateLimiter(private val perHost: HealthCheckProperties.Limit) {

    private companion object {
        const val MAX_TRACKED_HOSTS = 10_000L
    }

    private val hostBuckets: Cache<String, Bucket> = Caffeine.newBuilder()
        .expireAfterAccess(perHost.period)
        .maximumSize(MAX_TRACKED_HOSTS)
        .build()

    fun tryAcquire(host: String): Boolean = hostBuckets.get(host) { bucketOf(perHost) }.tryConsume(1)
}
