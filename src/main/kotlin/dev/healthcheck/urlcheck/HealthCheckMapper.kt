package dev.healthcheck.urlcheck

import okhttp3.HttpUrl
import java.time.Instant

object HealthCheckMapper {

    fun fromResponse(url: HttpUrl, followed: FollowedResponse, latencyMs: Long, checkedAt: Instant): HealthCheckResponse {
        val classification = HealthClassifier.classifyHttpCode(followed.code)
        return HealthCheckResponse(
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

    fun fromFailure(url: HttpUrl, failure: FollowFailedException, latencyMs: Long, checkedAt: Instant): HealthCheckResponse {
        val classification = HealthClassifier.classifyError(failure.cause)
        return HealthCheckResponse(
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
}
