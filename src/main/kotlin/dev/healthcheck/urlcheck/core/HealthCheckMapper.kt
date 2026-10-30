package dev.healthcheck.urlcheck.core

import dev.healthcheck.common.enums.HealthReason
import dev.healthcheck.common.enums.HealthStatus
import okhttp3.HttpUrl
import java.time.Instant

object HealthCheckMapper {

    fun fromResponse(url: HttpUrl, followed: FollowedResponse, latencyMs: Long, checkedAt: Instant): HealthCheckResult {
        val classification = HealthClassifier.classifyHttpCode(followed.code)
        return HealthCheckResult(
            url = url.toString(),
            finalUrl = followed.finalUrl.toString(),
            status = classification.status,
            httpStatus = followed.code,
            latencyMs = latencyMs,
            redirects = followed.redirects,
            reason = classification.reason,
            checkedAt = checkedAt,
        )
    }

    fun fromFailure(
        url: HttpUrl,
        failure: FollowFailedException,
        latencyMs: Long,
        checkedAt: Instant,
    ): HealthCheckResult {
        val classification = HealthClassifier.classifyError(failure.cause)
        return HealthCheckResult(
            url = url.toString(),
            finalUrl = failure.lastUrl.toString(),
            status = classification.status,
            latencyMs = latencyMs,
            redirects = failure.redirects,
            reason = classification.reason,
            checkedAt = checkedAt,
        )
    }

    fun fromHostRateLimited(url: HttpUrl, checkedAt: Instant): HealthCheckResult = HealthCheckResult(
        url = url.toString(),
        finalUrl = url.toString(),
        status = HealthStatus.REJECTED,
        latencyMs = 0,
        redirects = 0,
        reason = HealthReason.HOST_RATE_LIMITED,
        checkedAt = checkedAt,
    )

    fun fromInvalidUrl(rawUrl: String, checkedAt: Instant): HealthCheckResult = HealthCheckResult(
        url = rawUrl,
        finalUrl = rawUrl,
        status = HealthStatus.INVALID,
        latencyMs = 0,
        redirects = 0,
        reason = HealthReason.INVALID_URL,
        checkedAt = checkedAt,
    )

    fun fromBatchTimeout(url: String, latencyMs: Long, checkedAt: Instant): HealthCheckResult = HealthCheckResult(
        url = url,
        finalUrl = url,
        status = HealthStatus.DOWN,
        latencyMs = latencyMs,
        redirects = 0,
        reason = HealthReason.TIMEOUT,
        checkedAt = checkedAt,
    )
}
