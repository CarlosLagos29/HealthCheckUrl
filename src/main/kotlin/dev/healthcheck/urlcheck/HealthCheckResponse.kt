package dev.healthcheck.urlcheck

import dev.healthcheck.common.enums.FailureReason
import dev.healthcheck.common.enums.HealthStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class HealthCheckResponse(
    @Schema(description = "Normalized URL that was checked, or the URL as sent when it is invalid", example = "https://example.com/")
    val url: String,

    @Schema(
        description = "Last URL reached: the same as url without redirects, or the hop where the check failed. " +
            "Null when the URL was not checked (invalid URL in a batch, or batch timeout)",
        example = "https://example.com/",
    )
    val finalUrl: String?,

    @Schema(description = "Result of the check", example = "UP")
    val status: HealthStatus,

    @Schema(description = "HTTP status code returned by the site. Null when it never answered (DNS error, timeout, rejected)", example = "200")
    val httpStatus: Int?,

    @Schema(description = "Total time in milliseconds, redirects included", example = "142")
    val latencyMs: Long,

    @Schema(description = "Number of redirects followed", example = "0")
    val redirects: Int,

    @Schema(description = "Why the URL is DOWN, BLOCKED or REJECTED. Null when UP")
    val reason: FailureReason?,

    @Schema(description = "When the check was performed (UTC)", example = "2026-10-28T10:00:00Z")
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
            checkedAt = result.checkedAt,
        )
    }
}
