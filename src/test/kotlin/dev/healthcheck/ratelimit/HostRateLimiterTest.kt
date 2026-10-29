package dev.healthcheck.ratelimit

import dev.healthcheck.config.HealthCheckProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class HostRateLimiterTest {

    private val limiter = HostRateLimiter(HealthCheckProperties.Limit(2, Duration.ofMinutes(10)))

    @Test
    fun `allows a host up to its limit`() {
        assertThat(List(3) { limiter.tryAcquire("example.com") }).containsExactly(true, true, false)
    }

    @Test
    fun `keeps a separate limit for each host`() {
        repeat(2) { limiter.tryAcquire("example.com") }

        assertThat(limiter.tryAcquire("github.com")).isTrue()
    }
}
