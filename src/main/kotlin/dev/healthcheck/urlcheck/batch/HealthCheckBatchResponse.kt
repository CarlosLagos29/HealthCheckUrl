package dev.healthcheck.urlcheck.batch

import dev.healthcheck.urlcheck.core.HealthCheckResponse
import io.swagger.v3.oas.annotations.media.Schema

data class HealthCheckBatchResponse(
    @Schema(
        requiredMode = Schema.RequiredMode.REQUIRED,
        description = "One result per URL, in the same order as the request",
    )
    val results: List<HealthCheckResponse>,
)
