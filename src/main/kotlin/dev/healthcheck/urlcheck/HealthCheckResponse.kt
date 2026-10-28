package dev.healthcheck.urlcheck

import com.fasterxml.jackson.annotation.JsonInclude
import dev.healthcheck.common.enums.FailureReason
import dev.healthcheck.common.enums.HealthStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class HealthCheckResponse(
    @Schema(description = "Normalized URL that was checked", example = "https://example.com/")
    val url: String,

    @Schema(
        description = "Last URL reached: the same as url without redirects, or the hop where the check failed",
        example = "https://example.com/",
    )
    val finalUrl: String,

    @Schema(description = "Result of the check", example = "UP")
    val status: HealthStatus,

    @Schema(description = "HTTP status code returned by the site. Omitted when it never answered (DNS error, timeout, blocked)", example = "200")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val httpStatus: Int?,

    @Schema(description = "Total time in milliseconds, redirects included", example = "142")
    val latencyMs: Long,

    @Schema(description = "Number of redirects followed", example = "0")
    val redirects: Int,

    @Schema(description = "Why the URL is DOWN, BLOCKED or REJECTED. Omitted when UP")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val reason: FailureReason?,

    @Schema(description = "When the check was performed (UTC)", example = "2026-10-28T10:00:00Z")
    val checkedAt: Instant,
)
