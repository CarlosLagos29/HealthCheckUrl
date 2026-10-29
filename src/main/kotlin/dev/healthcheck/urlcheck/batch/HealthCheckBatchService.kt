package dev.healthcheck.urlcheck.batch

import dev.healthcheck.config.HealthCheckProperties
import dev.healthcheck.ratelimit.ClientRateLimiter
import dev.healthcheck.ratelimit.RateLimitScope
import dev.healthcheck.urlcheck.core.HealthCheckMapper
import dev.healthcheck.urlcheck.core.HealthCheckResult
import dev.healthcheck.urlcheck.core.UrlNormalizer
import dev.healthcheck.urlcheck.single.HealthChecker
import okhttp3.HttpUrl
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@Service
class HealthCheckBatchService(
    private val healthChecker: HealthChecker,
    private val clientRateLimiter: ClientRateLimiter,
    private val properties: HealthCheckProperties,
) : BatchHealthChecker {

    override fun healthCheckBatch(urls: List<String>, clientIp: String): List<HealthCheckResult> {
        val normalized = urls.associateWith { UrlNormalizer.normalizeOrNull(it) }
        val targets = normalized.values.filterNotNull().distinct()

        clientRateLimiter.acquire(clientIp, RateLimitScope.BATCH, targets.size.toLong())
        val results = checkAll(targets)

        return urls.map { raw ->
            normalized[raw]?.let { results.getValue(it) } ?: HealthCheckMapper.fromInvalidUrl(raw, Instant.now())
        }
    }

    private fun checkAll(targets: List<HttpUrl>): Map<HttpUrl, HealthCheckResult> {
        if (targets.isEmpty()) return emptyMap()

        val timeout = properties.batch.timeout
        val executor = Executors.newFixedThreadPool(minOf(properties.batch.concurrency, targets.size))
        try {
            val futures = executor.invokeAll(
                targets.map { target -> Callable { healthChecker.check(target) } },
                timeout.toMillis(),
                TimeUnit.MILLISECONDS,
            )
            return targets.zip(futures).associate { (target, future) -> target to resultOf(target, future, timeout.toMillis()) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun resultOf(target: HttpUrl, future: Future<HealthCheckResult>, timeoutMs: Long): HealthCheckResult {
        if (future.isCancelled) return HealthCheckMapper.fromBatchTimeout(target.toString(), timeoutMs, Instant.now())

        return try {
            future.get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }
}
