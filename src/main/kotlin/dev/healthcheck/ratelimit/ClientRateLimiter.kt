package dev.healthcheck.ratelimit

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.healthcheck.config.HealthCheckProperties
import io.github.bucket4j.Bucket
import java.time.Duration

enum class RateLimitScope { SINGLE, BATCH }

class RateLimitExceededException(val retryAfter: Duration) : RuntimeException("Rate limit exceeded")

class ClientRateLimiter(
    private val perIp: HealthCheckProperties.Limit,
    globalSingle: HealthCheckProperties.Limit,
    globalBatch: HealthCheckProperties.Limit,
) {

    private companion object {
        const val MAX_TRACKED_IPS = 10_000L
    }

    private val globals = mapOf(
        RateLimitScope.SINGLE to bucketOf(globalSingle),
        RateLimitScope.BATCH to bucketOf(globalBatch),
    )

    private val ipBuckets: Cache<String, Bucket> = Caffeine.newBuilder()
        .expireAfterAccess(perIp.period)
        .maximumSize(MAX_TRACKED_IPS)
        .build()

    fun acquire(clientIp: String, scope: RateLimitScope, cost: Long) {
        if (cost == 0L) return

        val global = globals.getValue(scope)
        reserve(global, cost)
        try {
            reserve(ipBuckets.get(clientIp) { bucketOf(perIp) }, cost)
        } catch (e: RateLimitExceededException) {
            global.addTokens(cost)
            throw e
        }
    }

    private fun reserve(bucket: Bucket, cost: Long) {
        val probe = bucket.tryConsumeAndReturnRemaining(cost)
        if (!probe.isConsumed) throw RateLimitExceededException(Duration.ofNanos(probe.nanosToWaitForRefill))
    }
}
