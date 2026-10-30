package dev.healthcheck.urlcheck.core

import dev.healthcheck.common.enums.HealthReason
import dev.healthcheck.common.enums.HealthStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class HealthCheckResponse(
    @Schema(
        requiredMode = Schema.RequiredMode.REQUIRED,
        description = "Normalized URL that was checked, or the URL as sent when it is invalid",
        example = "https://example.com/",
    )
    val url: String,

    @Schema(
        requiredMode = Schema.RequiredMode.REQUIRED,
        description = "Last URL reached: the same as url unless there were " +
            "redirects, or the hop where the check failed",
        example = "https://example.com/",
    )
    val finalUrl: String,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Result of the check", example = "UP")
    val status: HealthStatus,

    @Schema(
        requiredMode = Schema.RequiredMode.REQUIRED,
        description = "HTTP status code returned by the site. 0 when it never " +
            "answered (DNS error, timeout, rejected, invalid)",
        example = "200",
    )
    val httpStatus: Int,

    @Schema(
        requiredMode = Schema.RequiredMode.REQUIRED,
        description = "Total time in milliseconds, redirects included",
        example = "142",
    )
    val latencyMs: Long,

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Number of redirects followed", example = "0")
    val redirects: Int,

    @Schema(
        requiredMode = Schema.RequiredMode.REQUIRED,
        description = "Why the result is what it is: SUCCESS when UP, otherwise the cause",
        example = "SUCCESS",
    )
    val reason: HealthReason,

    @Schema(
        requiredMode = Schema.RequiredMode.REQUIRED,
        description = "Human-readable explanation of the result",
        example = "The site is up and responding",
    )
    val message: String,

    @Schema(
        requiredMode = Schema.RequiredMode.REQUIRED,
        description = "When the check was performed (UTC)",
        example = "2026-10-28T10:00:00Z",
    )
    val checkedAt: Instant,
) {
    companion object {
        fun from(result: HealthCheckResult) = HealthCheckResponse(
            url = result.url,
            finalUrl = result.finalUrl,
            status = result.status,
            httpStatus = result.httpStatus,
            latencyMs = result.latencyMs,
            redirects = result.redirects,
            reason = result.reason,
            message = result.reason.toMessage(),
            checkedAt = result.checkedAt,
        )
    }
}
