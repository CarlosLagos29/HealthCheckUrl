package dev.healthcheck.urlcheck.batch

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty

data class HealthCheckBatchRequest(
    @ArraySchema(
        arraySchema = Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "URLs to check, up to the batch limit. Each one follows the same rules as a single check.",
            example = """["https://github.com", "https://github.com/this-page-does-not-exist", "http://169.254.169.254/"]""",
        ),
    )
    @NotEmpty(message = "must not be empty")
    @MaxBatchSize
    val urls: List<String>,
)
