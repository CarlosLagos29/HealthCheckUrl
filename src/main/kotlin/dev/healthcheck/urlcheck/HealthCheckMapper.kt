package dev.healthcheck.urlcheck

import dev.healthcheck.common.enums.FailureReason
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

    fun fromFailure(url: HttpUrl, failure: FollowFailedException, latencyMs: Long, checkedAt: Instant): HealthCheckResult {
        val classification = HealthClassifier.classifyError(failure.cause)
        return HealthCheckResult(
            url = url.toString(),
            finalUrl = failure.lastUrl.toString(),
            status = classification.status,
            httpStatus = null,
            latencyMs = latencyMs,
            redirects = failure.redirects,
            reason = classification.reason,
            checkedAt = checkedAt,
        )
    }

    fun fromInvalidUrl(rawUrl: String, checkedAt: Instant): HealthCheckResult =
        HealthCheckResult(
            url = rawUrl,
            finalUrl = null,
            status = HealthStatus.REJECTED,
            httpStatus = null,
            latencyMs = 0,
            redirects = 0,
            reason = FailureReason.INVALID_URL,
            checkedAt = checkedAt,
        )

    fun fromBatchTimeout(url: String, latencyMs: Long, checkedAt: Instant): HealthCheckResult =
        HealthCheckResult(
            url = url,
            finalUrl = null,
            status = HealthStatus.DOWN,
            httpStatus = null,
            latencyMs = latencyMs,
            redirects = 0,
            reason = FailureReason.TIMEOUT,
            checkedAt = checkedAt,
        )
}
