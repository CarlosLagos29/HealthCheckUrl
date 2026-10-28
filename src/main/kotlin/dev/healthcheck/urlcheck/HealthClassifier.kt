package dev.healthcheck.urlcheck

import dev.healthcheck.common.enums.FailureReason
import dev.healthcheck.common.enums.HealthStatus
import dev.healthcheck.security.RejectedAddressException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

data class Classification(
    val status: HealthStatus,
    val reason: FailureReason?,
)

object HealthClassifier {

    fun classifyHttpCode(httpCode: Int): Classification = when (httpCode) {
        in 200..399 -> Classification(HealthStatus.UP, null)
        401 -> Classification(HealthStatus.BLOCKED, FailureReason.AUTH_REQUIRED)
        403 -> Classification(HealthStatus.BLOCKED, FailureReason.ACCESS_DENIED)
        in 400..499 -> Classification(HealthStatus.DOWN, FailureReason.HTTP_4XX)
        else -> Classification(HealthStatus.DOWN, FailureReason.HTTP_5XX)
    }

    fun classifyError(error: IOException): Classification = when (error) {
        is RejectedAddressException -> Classification(HealthStatus.REJECTED, error.reason)
        is TooManyRedirectsException -> Classification(HealthStatus.DOWN, FailureReason.TOO_MANY_REDIRECTS)
        is InterruptedIOException -> Classification(HealthStatus.DOWN, FailureReason.TIMEOUT)
        is UnknownHostException -> Classification(HealthStatus.DOWN, FailureReason.DNS_ERROR)
        is SSLException -> Classification(HealthStatus.DOWN, FailureReason.TLS_ERROR)
        else -> Classification(HealthStatus.DOWN, FailureReason.CONNECTION_ERROR)
    }
}
