package dev.healthcheck.cache

import dev.healthcheck.common.enums.HealthReason
import dev.healthcheck.common.enums.HealthStatus
import dev.healthcheck.urlcheck.core.HealthCheckResult
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class UrlCacheTest {

    private val cache = UrlCache(Duration.ofMinutes(5))

    private val result = HealthCheckResult(
        url = "https://example.com/",
        finalUrl = "https://example.com/",
        status = HealthStatus.UP,
        httpStatus = 200,
        latencyMs = 100,
        redirects = 0,
        reason = HealthReason.SUCCESS,
        checkedAt = Instant.parse("2026-10-29T10:00:00Z"),
    )

    @Test
    fun `returns the saved result for the same url`() {
        cache.save("https://example.com/".toHttpUrl(), result)

        assertThat(cache.get("https://example.com/".toHttpUrl())).isEqualTo(result)
    }

    @Test
    fun `returns nothing for a url that was not saved`() {
        cache.save("https://example.com/".toHttpUrl(), result)

        assertThat(cache.get("https://example.com/other".toHttpUrl())).isNull()
    }
}
