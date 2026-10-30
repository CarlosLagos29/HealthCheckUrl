package dev.healthcheck.urlcheck.single

import dev.healthcheck.cache.UrlCache
import dev.healthcheck.ratelimit.ClientRateLimiter
import dev.healthcheck.ratelimit.HostRateLimiter
import dev.healthcheck.ratelimit.RateLimitScope
import dev.healthcheck.urlcheck.core.FollowFailedException
import dev.healthcheck.urlcheck.core.HealthCheckMapper
import dev.healthcheck.urlcheck.core.HealthCheckResult
import dev.healthcheck.urlcheck.core.RedirectFollower
import dev.healthcheck.urlcheck.core.UrlNormalizer
import okhttp3.HttpUrl
import okhttp3.Request
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class HealthCheckService(
    private val redirectFollower: RedirectFollower,
    private val urlCache: UrlCache,
    private val clientRateLimiter: ClientRateLimiter,
    private val hostRateLimiter: HostRateLimiter,
) : HealthChecker {

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(HealthCheckService::class.java)
        private const val USER_AGENT = "HealthCheckUrl/1.0 (+https://github.com/CarlosLagos29/HealthCheckUrl)"
    }

    override fun healthCheck(url: String, clientIp: String): HealthCheckResult {
        val target = UrlNormalizer.normalize(url)
        clientRateLimiter.acquire(clientIp, RateLimitScope.SINGLE, 1)
        return check(target)
    }

    override fun check(target: HttpUrl): HealthCheckResult {
        urlCache.get(target)?.let { return it }

        if (!hostRateLimiter.tryAcquire(target.host)) {
            return HealthCheckMapper.fromHostRateLimited(target, Instant.now())
        }

        val result = request(target)
        urlCache.save(target, result)
        return result
    }

    private fun request(target: HttpUrl): HealthCheckResult {
        val start = System.nanoTime()

        val result = try {
            val followed = redirectFollower.execute(headRequest(target))
            HealthCheckMapper.fromResponse(target, followed, elapsedMs(start), Instant.now())
        } catch (e: FollowFailedException) {
            LOG.debug("Check of {} failed at {}: {}", target, e.lastUrl, e.cause.toString())
            HealthCheckMapper.fromFailure(target, e, elapsedMs(start), Instant.now())
        }

        LOG.debug(
            "Checked {}: status={} reason={} httpStatus={} finalUrl={} redirects={} latencyMs={}",
            result.url,
            result.status,
            result.reason,
            result.httpStatus,
            result.finalUrl,
            result.redirects,
            result.latencyMs,
        )
        return result
    }

    private fun headRequest(url: HttpUrl): Request = Request.Builder()
        .url(url)
        .head()
        .header("User-Agent", USER_AGENT)
        .header("Accept", "*/*")
        .build()

    private fun elapsedMs(start: Long): Long = Duration.ofNanos(System.nanoTime() - start).toMillis()
}
