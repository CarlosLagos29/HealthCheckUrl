package dev.healthcheck.urlcheck

import dev.healthcheck.common.enums.FailureReason
import dev.healthcheck.common.enums.HealthStatus
import dev.healthcheck.security.RejectedAddressException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class HealthClassifierTest {

    @ParameterizedTest
    @ValueSource(ints = [200, 201, 204, 299, 301, 304, 399])
    fun `treats 2xx and final 3xx as UP`(code: Int) {
        assertThat(HealthClassifier.classifyHttpCode(code)).isEqualTo(Classification(HealthStatus.UP, null))
    }

    @ParameterizedTest
    @CsvSource("401, AUTH_REQUIRED", "403, ACCESS_DENIED")
    fun `treats 401 and 403 as BLOCKED`(code: Int, reason: FailureReason) {
        assertThat(HealthClassifier.classifyHttpCode(code)).isEqualTo(Classification(HealthStatus.BLOCKED, reason))
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 402, 404, 405, 410, 429, 499])
    fun `treats other 4xx as DOWN with HTTP_4XX`(code: Int) {
        assertThat(HealthClassifier.classifyHttpCode(code))
            .isEqualTo(Classification(HealthStatus.DOWN, FailureReason.HTTP_4XX))
    }

    @ParameterizedTest
    @ValueSource(ints = [500, 501, 502, 503, 504, 599])
    fun `treats 5xx as DOWN with HTTP_5XX`(code: Int) {
        assertThat(HealthClassifier.classifyHttpCode(code))
            .isEqualTo(Classification(HealthStatus.DOWN, FailureReason.HTTP_5XX))
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
                RejectedAddressException(FailureReason.PRIVATE_ADDRESS, "Host 10.0.0.1 is a private address, rejected by the API"),
                Classification(HealthStatus.REJECTED, FailureReason.PRIVATE_ADDRESS),
            ),
            Arguments.of(
                RejectedAddressException(FailureReason.PORT_NOT_ALLOWED, "Port 22 is not allowed, rejected by the API"),
                Classification(HealthStatus.REJECTED, FailureReason.PORT_NOT_ALLOWED),
            ),
            Arguments.of(
                TooManyRedirectsException(5),
                Classification(HealthStatus.DOWN, FailureReason.TOO_MANY_REDIRECTS),
            ),
            Arguments.of(SocketTimeoutException("read timed out"), Classification(HealthStatus.DOWN, FailureReason.TIMEOUT)),
            Arguments.of(InterruptedIOException("timeout"), Classification(HealthStatus.DOWN, FailureReason.TIMEOUT)),
            Arguments.of(UnknownHostException("nope.invalid"), Classification(HealthStatus.DOWN, FailureReason.DNS_ERROR)),
            Arguments.of(SSLHandshakeException("expired"), Classification(HealthStatus.DOWN, FailureReason.TLS_ERROR)),
            Arguments.of(SSLPeerUnverifiedException("wrong host"), Classification(HealthStatus.DOWN, FailureReason.TLS_ERROR)),
            Arguments.of(ConnectException("refused"), Classification(HealthStatus.DOWN, FailureReason.CONNECTION_ERROR)),
            Arguments.of(NoRouteToHostException("no route"), Classification(HealthStatus.DOWN, FailureReason.CONNECTION_ERROR)),
            Arguments.of(IOException("reset"), Classification(HealthStatus.DOWN, FailureReason.CONNECTION_ERROR)),
        )
    }
}
