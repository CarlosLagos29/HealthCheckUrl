package dev.healthcheck.urlcheck

import dev.healthcheck.common.enums.FailureReason
import dev.healthcheck.common.enums.HealthStatus
import dev.healthcheck.security.RejectedAddressException
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class HealthCheckMapperTest {

    private val url = "https://example.com".toHttpUrl()
    private val checkedAt = Instant.parse("2026-10-28T10:00:00Z")

    @Test
    fun `maps a response with its http status and classification`() {
        val followed = FollowedResponse(404, "https://example.com/missing".toHttpUrl(), 2)

        val response = HealthCheckMapper.fromResponse(url, followed, 120, checkedAt)

        assertThat(response).isEqualTo(
            HealthCheckResult(
                url = "https://example.com/",
                finalUrl = "https://example.com/missing",
                status = HealthStatus.DOWN,
                httpStatus = 404,
                latencyMs = 120,
                redirects = 2,
                reason = FailureReason.HTTP_4XX,
                checkedAt = checkedAt,
            ),
        )
    }

    @Test
    fun `maps a failure without http status`() {
        val failure = FollowFailedException(
            "http://169.254.169.254/".toHttpUrl(),
            1,
            RejectedAddressException(FailureReason.PRIVATE_ADDRESS, "Host 169.254.169.254 is a private address, rejected by the API"),
        )

        val response = HealthCheckMapper.fromFailure(url, failure, 5, checkedAt)

        assertThat(response).isEqualTo(
            HealthCheckResult(
                url = "https://example.com/",
                finalUrl = "http://169.254.169.254/",
                status = HealthStatus.REJECTED,
                httpStatus = null,
                latencyMs = 5,
                redirects = 1,
                reason = FailureReason.PRIVATE_ADDRESS,
                checkedAt = checkedAt,
            ),
        )
    }
}
