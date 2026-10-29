package dev.healthcheck.urlcheck

import io.swagger.v3.oas.annotations.media.Schema

data class HealthCheckBatchResponse(
    @Schema(description = "One result per URL, in the same order as the request")
    val results: List<HealthCheckResponse>,
)
