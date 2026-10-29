package dev.healthcheck.ratelimit

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.healthcheck.config.HealthCheckProperties
import io.github.bucket4j.Bucket
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class HostRateLimiter(private val perHost: HealthCheckProperties.Limit) {

    private companion object {
        val LOG: Logger = LoggerFactory.getLogger(HostRateLimiter::class.java)
        const val MAX_TRACKED_HOSTS = 10_000L
    }

    private val hostBuckets: Cache<String, Bucket> = Caffeine.newBuilder()
        .expireAfterAccess(perHost.period)
        .maximumSize(MAX_TRACKED_HOSTS)
        .build()

    fun tryAcquire(host: String): Boolean {
        val probe = hostBuckets.get(host) { bucketOf(perHost) }.tryConsumeAndReturnRemaining(1)
        if (!probe.isConsumed) {
            LOG.debug("Host limit hit: host={}", host)
            return false
        }

        LOG.info("Host registered: host={}", host)
        LOG.debug("Host left: host={} left={}", host, probe.remainingTokens)
        return true
    }
}
