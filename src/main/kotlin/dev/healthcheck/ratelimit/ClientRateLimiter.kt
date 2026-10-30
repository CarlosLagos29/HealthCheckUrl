package dev.healthcheck.ratelimit

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import dev.healthcheck.common.toLogSafe
import dev.healthcheck.config.HealthCheckProperties
import io.github.bucket4j.Bucket
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

enum class RateLimitScope { SINGLE, BATCH }

class RateLimitExceededException(val retryAfter: Duration) : RuntimeException("Rate limit exceeded")

class ClientRateLimiter(
    private val perIp: HealthCheckProperties.Limit,
    globalSingle: HealthCheckProperties.Limit,
    globalBatch: HealthCheckProperties.Limit,
) {

    private companion object {
        val LOG: Logger = LoggerFactory.getLogger(ClientRateLimiter::class.java)
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

        val ip = clientIp.toLogSafe()
        val global = globals.getValue(scope)
        val globalLeft = reserve(global, cost, "global", ip, scope)
        val ipLeft = try {
            reserve(ipBuckets.get(clientIp) { bucketOf(perIp) }, cost, "ip", ip, scope)
        } catch (e: RateLimitExceededException) {
            global.addTokens(cost)
            LOG.debug("Refunded {} to the global {} bucket", cost, scope)
            throw e
        }

        LOG.info("Rate limit registered: ip={} scope={} cost={}", ip, scope, cost)
        LOG.debug("Rate limit left: ip={} scope={} global={} ip={}", ip, scope, globalLeft, ipLeft)
    }

    private fun reserve(bucket: Bucket, cost: Long, name: String, ip: String, scope: RateLimitScope): Long {
        val probe = bucket.tryConsumeAndReturnRemaining(cost)
        if (!probe.isConsumed) {
            val retryAfter = Duration.ofNanos(probe.nanosToWaitForRefill)
            LOG.debug(
                "Rate limit hit on the {} bucket: ip={} scope={} cost={} retryIn={}",
                name,
                ip,
                scope,
                cost,
                retryAfter,
            )
            throw RateLimitExceededException(retryAfter)
        }
        return probe.remainingTokens
    }
}
