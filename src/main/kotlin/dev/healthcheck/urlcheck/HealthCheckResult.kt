package dev.healthcheck.urlcheck

import dev.healthcheck.common.enums.FailureReason
import dev.healthcheck.common.enums.HealthStatus
import java.time.Instant

data class HealthCheckResult(
    val url: String,
    val finalUrl: String?,
    val status: HealthStatus,
    val httpStatus: Int?,
    val latencyMs: Long,
    val redirects: Int,
    val reason: FailureReason?,
    val checkedAt: Instant,
)
