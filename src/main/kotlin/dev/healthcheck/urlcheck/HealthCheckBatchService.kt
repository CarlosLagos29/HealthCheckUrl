package dev.healthcheck.urlcheck

import dev.healthcheck.config.HealthCheckProperties
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
    private val properties: HealthCheckProperties,
) : BatchHealthChecker {

    override fun healthCheckBatch(urls: List<String>): List<HealthCheckResult> {
        if (urls.size > properties.batch.maxUrls) throw BatchTooLargeException(properties.batch.maxUrls)

        val normalized = urls.associateWith { UrlNormalizer.normalizeOrNull(it)?.toString() }
        val results = checkAll(normalized.values.filterNotNull().distinct())

        return urls.map { raw ->
            normalized[raw]?.let { results.getValue(it) } ?: HealthCheckMapper.fromInvalidUrl(raw, Instant.now())
        }
    }

    private fun checkAll(urls: List<String>): Map<String, HealthCheckResult> {
        if (urls.isEmpty()) return emptyMap()

        val timeout = properties.batch.timeout
        val executor = Executors.newFixedThreadPool(minOf(properties.batch.concurrency, urls.size))
        try {
            val futures = executor.invokeAll(
                urls.map { url -> Callable { healthChecker.healthCheck(url) } },
                timeout.toMillis(),
                TimeUnit.MILLISECONDS,
            )
            return urls.zip(futures).associate { (url, future) -> url to resultOf(url, future, timeout.toMillis()) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun resultOf(url: String, future: Future<HealthCheckResult>, timeoutMs: Long): HealthCheckResult {
        if (future.isCancelled) return HealthCheckMapper.fromBatchTimeout(url, timeoutMs, Instant.now())

        return try {
            future.get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }
}
