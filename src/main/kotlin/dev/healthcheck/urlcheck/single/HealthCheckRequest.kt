package dev.healthcheck.urlcheck.single

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class HealthCheckRequest(
    @Schema(
        requiredMode = Schema.RequiredMode.REQUIRED,
        description = "URL to check. Without a scheme, https:// is assumed.",
        example = "https://example.com",
    )
    @NotBlank(message = "must not be blank")
    val url: String,
)
