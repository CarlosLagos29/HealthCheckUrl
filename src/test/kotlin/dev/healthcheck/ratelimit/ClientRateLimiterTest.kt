package dev.healthcheck.ratelimit

import dev.healthcheck.config.HealthCheckProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

class ClientRateLimiterTest {

    private fun limit(capacity: Long) = HealthCheckProperties.Limit(capacity, Duration.ofMinutes(10))

    private fun limiter(perIp: Long = 100, globalSingle: Long = 100, globalBatch: Long = 100) =
        ClientRateLimiter(limit(perIp), limit(globalSingle), limit(globalBatch))

    @Test
    fun `rejects an ip over its limit and says when to retry`() {
        val limiter = limiter(perIp = 2)
        limiter.acquire("1.1.1.1", RateLimitScope.SINGLE, 2)

        assertThatThrownBy { limiter.acquire("1.1.1.1", RateLimitScope.SINGLE, 1) }
            .isInstanceOfSatisfying(RateLimitExceededException::class.java) {
                assertThat(it.retryAfter).isPositive()
            }
    }

    @Test
    fun `keeps a separate limit for each ip`() {
        val limiter = limiter(perIp = 1)
        limiter.acquire("1.1.1.1", RateLimitScope.SINGLE, 1)

        assertThatCode { limiter.acquire("2.2.2.2", RateLimitScope.SINGLE, 1) }.doesNotThrowAnyException()
    }

    @Test
    fun `rejects every ip once the global limit is reached`() {
        val limiter = limiter(globalSingle = 1)
        limiter.acquire("1.1.1.1", RateLimitScope.SINGLE, 1)

        assertThatThrownBy { limiter.acquire("2.2.2.2", RateLimitScope.SINGLE, 1) }
            .isInstanceOf(RateLimitExceededException::class.java)
    }

    @Test
    fun `keeps separate global limits for single checks and batches`() {
        val limiter = limiter(globalSingle = 1)
        limiter.acquire("1.1.1.1", RateLimitScope.SINGLE, 1)

        assertThatCode { limiter.acquire("2.2.2.2", RateLimitScope.BATCH, 5) }.doesNotThrowAnyException()
    }

    @Test
    fun `gives the global tokens back when the ip limit rejects`() {
        val limiter = limiter(perIp = 1, globalSingle = 2)
        limiter.acquire("1.1.1.1", RateLimitScope.SINGLE, 1)
        assertThatThrownBy { limiter.acquire("1.1.1.1", RateLimitScope.SINGLE, 1) }
            .isInstanceOf(RateLimitExceededException::class.java)

        assertThatCode { limiter.acquire("2.2.2.2", RateLimitScope.SINGLE, 1) }.doesNotThrowAnyException()
    }
}
