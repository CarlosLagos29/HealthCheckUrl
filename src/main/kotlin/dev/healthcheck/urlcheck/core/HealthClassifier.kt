package dev.healthcheck.urlcheck.core

import dev.healthcheck.common.enums.HealthReason
import dev.healthcheck.common.enums.HealthStatus
import dev.healthcheck.security.RejectedAddressException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

data class Classification(val status: HealthStatus, val reason: HealthReason)

object HealthClassifier {

    fun classifyHttpCode(httpCode: Int): Classification = when (httpCode) {
        in 200..399 -> Classification(HealthStatus.UP, HealthReason.SUCCESS)
        401 -> Classification(HealthStatus.BLOCKED, HealthReason.AUTH_REQUIRED)
        403 -> Classification(HealthStatus.BLOCKED, HealthReason.ACCESS_DENIED)
        in 400..499 -> Classification(HealthStatus.DOWN, HealthReason.HTTP_4XX)
        else -> Classification(HealthStatus.DOWN, HealthReason.HTTP_5XX)
    }

    fun classifyError(error: IOException): Classification = when (error) {
        is RejectedAddressException -> Classification(HealthStatus.REJECTED, error.reason)
        is TooManyRedirectsException -> Classification(HealthStatus.DOWN, HealthReason.TOO_MANY_REDIRECTS)
        is InterruptedIOException -> Classification(HealthStatus.DOWN, HealthReason.TIMEOUT)
        is UnknownHostException -> Classification(HealthStatus.DOWN, HealthReason.DNS_ERROR)
        is SSLException -> Classification(HealthStatus.DOWN, HealthReason.TLS_ERROR)
        else -> Classification(HealthStatus.DOWN, HealthReason.CONNECTION_ERROR)
    }
}
