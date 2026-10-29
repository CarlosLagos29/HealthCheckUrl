package dev.healthcheck.urlcheck

import dev.healthcheck.common.enums.HealthReason
import dev.healthcheck.common.enums.HealthStatus
import dev.healthcheck.config.HealthCheckProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class HealthCheckBatchServiceTest {

    private class FakeHealthChecker(
        private val delaysMs: Map<String, Long> = emptyMap(),
        private val defaultDelayMs: Long = 0,
        private val error: RuntimeException? = null,
    ) : HealthChecker {
        val calls = ConcurrentLinkedQueue<String>()
        val maxRunning = AtomicInteger()
        private val running = AtomicInteger()

        override fun healthCheck(url: String): HealthCheckResult {
            calls.add(url)
            maxRunning.accumulateAndGet(running.incrementAndGet(), ::maxOf)
            try {
                error?.let { throw it }
                Thread.sleep(delaysMs[url] ?: defaultDelayMs)
                return HealthCheckResult(
                    url = url,
                    finalUrl = url,
                    status = HealthStatus.UP,
                    httpStatus = 200,
                    latencyMs = 1,
                    redirects = 0,
                    reason = HealthReason.SUCCESS,
                    checkedAt = Instant.EPOCH,
                )
            } finally {
                running.decrementAndGet()
            }
        }
    }

    private fun properties(maxUrls: Int = 5, concurrency: Int = 5, timeout: Duration = Duration.ofSeconds(15)) =
        HealthCheckProperties(
            timeouts = HealthCheckProperties.Timeouts(Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(10)),
            maxRedirects = 5,
            allowedPorts = setOf(80, 443),
            batch = HealthCheckProperties.Batch(maxUrls, concurrency, timeout),
            rateLimit = HealthCheckProperties.RateLimit(
                perIp = HealthCheckProperties.Limit(10, Duration.ofMinutes(1)),
                global = HealthCheckProperties.Limit(20, Duration.ofMinutes(10)),
                perHost = HealthCheckProperties.Limit(3, Duration.ofMinutes(10)),
            ),
        )

    private fun service(checker: HealthChecker, properties: HealthCheckProperties = properties()) =
        HealthCheckBatchService(checker, properties)

    @Test
    fun `returns one result per url in the order of the request`() {
        val results = service(FakeHealthChecker()).healthCheckBatch(listOf("c.com", "a.com", "b.com"))

        assertThat(results.map { it.url }).containsExactly("https://c.com/", "https://a.com/", "https://b.com/")
    }

    @Test
    fun `checks repeated urls once and repeats the result in each position`() {
        val checker = FakeHealthChecker()

        val results = service(checker).healthCheckBatch(listOf("github.com", "https://github.com", "https://github.com/"))

        assertThat(checker.calls).containsExactly("https://github.com/")
        assertThat(results).hasSize(3)
        assertThat(results.distinct()).hasSize(1)
    }

    @Test
    fun `marks an invalid url as INVALID without failing the rest`() {
        val checker = FakeHealthChecker()

        val results = service(checker).healthCheckBatch(listOf("example.com", "ftp://example.com", "https://wwwgooglecom"))

        assertThat(results[0].status).isEqualTo(HealthStatus.UP)
        assertThat(results.drop(1)).allSatisfy {
            assertThat(it.status).isEqualTo(HealthStatus.INVALID)
            assertThat(it.reason).isEqualTo(HealthReason.INVALID_URL)
            assertThat(it.finalUrl).isEqualTo(it.url)
            assertThat(it.httpStatus).isEqualTo(NO_HTTP_STATUS)
        }
        assertThat(results.drop(1).map { it.url }).containsExactly("ftp://example.com", "https://wwwgooglecom")
        assertThat(checker.calls).containsExactly("https://example.com/")
    }

    @Test
    fun `does not check anything when every url is invalid`() {
        val checker = FakeHealthChecker()

        val results = service(checker).healthCheckBatch(listOf("ftp://a.com", "localhost"))

        assertThat(results).allSatisfy { assertThat(it.reason).isEqualTo(HealthReason.INVALID_URL) }
        assertThat(checker.calls).isEmpty()
    }

    @Test
    fun `accepts exactly the maximum number of urls`() {
        val results = service(FakeHealthChecker(), properties(maxUrls = 3)).healthCheckBatch(listOf("a.com", "b.com", "c.com"))

        assertThat(results).hasSize(3)
    }

    @Test
    fun `rejects more urls than the maximum before checking any`() {
        val checker = FakeHealthChecker()

        assertThatThrownBy { service(checker, properties(maxUrls = 2)).healthCheckBatch(listOf("a.com", "b.com", "c.com")) }
            .isInstanceOf(BatchTooLargeException::class.java)
            .hasMessage("A batch accepts at most 2 URLs")
        assertThat(checker.calls).isEmpty()
    }

    @Test
    fun `checks the urls in parallel`() {
        val checker = FakeHealthChecker(defaultDelayMs = 300)
        val start = System.nanoTime()

        service(checker).healthCheckBatch(listOf("a.com", "b.com", "c.com"))

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(800))
        assertThat(checker.maxRunning.get()).isEqualTo(3)
    }

    @Test
    fun `never runs more checks at once than the configured concurrency`() {
        val checker = FakeHealthChecker(defaultDelayMs = 100)

        service(checker, properties(concurrency = 2)).healthCheckBatch(listOf("a.com", "b.com", "c.com", "d.com"))

        assertThat(checker.maxRunning.get()).isEqualTo(2)
        assertThat(checker.calls).hasSize(4)
    }

    @Test
    fun `marks the urls still running at the batch timeout as TIMEOUT and keeps the finished ones`() {
        val checker = FakeHealthChecker(delaysMs = mapOf("https://slow.com/" to 5_000))

        val results = service(checker, properties(timeout = Duration.ofMillis(300)))
            .healthCheckBatch(listOf("fast.com", "slow.com", "quick.com"))

        assertThat(results[0].status).isEqualTo(HealthStatus.UP)
        assertThat(results[2].status).isEqualTo(HealthStatus.UP)
        with(results[1]) {
            assertThat(url).isEqualTo("https://slow.com/")
            assertThat(status).isEqualTo(HealthStatus.DOWN)
            assertThat(reason).isEqualTo(HealthReason.TIMEOUT)
            assertThat(finalUrl).isEqualTo("https://slow.com/")
            assertThat(latencyMs).isEqualTo(300)
        }
    }

    @Test
    fun `propagates an unexpected error from a check`() {
        val checker = FakeHealthChecker(error = IllegalStateException("boom"))

        assertThatThrownBy { service(checker).healthCheckBatch(listOf("a.com")) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("boom")
    }
}
