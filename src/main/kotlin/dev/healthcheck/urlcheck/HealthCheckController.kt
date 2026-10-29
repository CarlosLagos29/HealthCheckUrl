package dev.healthcheck.urlcheck

import dev.healthcheck.common.toLogSafe
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/health-checks")
@Tag(name = "Health checks", description = "Check whether a URL is alive")
class HealthCheckController(
    private val healthChecker: HealthChecker,
    private val batchHealthChecker: BatchHealthChecker,
) {

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(HealthCheckController::class.java)
    }

    @PostMapping
    @HealthCheckDocs
    fun healthCheck(@Valid @RequestBody request: HealthCheckRequest): HealthCheckResponse {
        LOG.info("Health check requested: url={}", request.url.toLogSafe())
        val result = healthChecker.healthCheck(request.url)
        LOG.info(
            "Health check done: url={} status={} reason={} httpStatus={} latencyMs={}",
            result.url, result.status, result.reason, result.httpStatus, result.latencyMs,
        )
        return HealthCheckResponse.from(result)
    }

    @PostMapping("/batch")
    @BatchHealthCheckDocs
    fun healthCheckBatch(@Valid @RequestBody request: HealthCheckBatchRequest): HealthCheckBatchResponse {
        LOG.info("Batch health check requested: {} urls", request.urls.size)
        val results = batchHealthChecker.healthCheckBatch(request.urls)
        LOG.info("Batch health check done: {}", results.groupingBy { it.status }.eachCount())
        return HealthCheckBatchResponse(results.map(HealthCheckResponse::from))
    }
}
