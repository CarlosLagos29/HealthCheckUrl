package dev.healthcheck.urlcheck.core

import dev.healthcheck.common.enums.HealthReason
import dev.healthcheck.common.enums.HealthStatus
import dev.healthcheck.security.RejectedAddressException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class HealthClassifierTest {

    @ParameterizedTest
    @ValueSource(ints = [200, 304])
    fun `treats 2xx and final 3xx as UP`(code: Int) {
        assertThat(
            HealthClassifier.classifyHttpCode(code),
        ).isEqualTo(Classification(HealthStatus.UP, HealthReason.SUCCESS))
    }

    @ParameterizedTest
    @CsvSource("401, AUTH_REQUIRED", "403, ACCESS_DENIED")
    fun `treats 401 and 403 as BLOCKED`(code: Int, reason: HealthReason) {
        assertThat(HealthClassifier.classifyHttpCode(code)).isEqualTo(Classification(HealthStatus.BLOCKED, reason))
    }

    @Test
    fun `treats other 4xx as DOWN with HTTP_4XX`() {
        assertThat(HealthClassifier.classifyHttpCode(404))
            .isEqualTo(Classification(HealthStatus.DOWN, HealthReason.HTTP_4XX))
    }

    @Test
    fun `treats 5xx as DOWN with HTTP_5XX`() {
        assertThat(HealthClassifier.classifyHttpCode(503))
            .isEqualTo(Classification(HealthStatus.DOWN, HealthReason.HTTP_5XX))
    }

    @ParameterizedTest
    @MethodSource("errors")
    fun `classifies errors by type`(error: IOException, expected: Classification) {
        assertThat(HealthClassifier.classifyError(error)).isEqualTo(expected)
    }

    companion object {
        @JvmStatic
        fun errors(): List<Arguments> = listOf(
            Arguments.of(
                RejectedAddressException(
                    HealthReason.PRIVATE_ADDRESS,
                    "Host 10.0.0.1 is a private address, rejected by the API",
                ),
                Classification(HealthStatus.REJECTED, HealthReason.PRIVATE_ADDRESS),
            ),
            Arguments.of(
                RejectedAddressException(HealthReason.PORT_NOT_ALLOWED, "Port 22 is not allowed, rejected by the API"),
                Classification(HealthStatus.REJECTED, HealthReason.PORT_NOT_ALLOWED),
            ),
            Arguments.of(
                TooManyRedirectsException(5),
                Classification(HealthStatus.DOWN, HealthReason.TOO_MANY_REDIRECTS),
            ),
            Arguments.of(InterruptedIOException("timeout"), Classification(HealthStatus.DOWN, HealthReason.TIMEOUT)),
            Arguments.of(
                UnknownHostException("nope.invalid"),
                Classification(HealthStatus.DOWN, HealthReason.DNS_ERROR),
            ),
            Arguments.of(SSLHandshakeException("expired"), Classification(HealthStatus.DOWN, HealthReason.TLS_ERROR)),
            Arguments.of(ConnectException("refused"), Classification(HealthStatus.DOWN, HealthReason.CONNECTION_ERROR)),
        )
    }
}
