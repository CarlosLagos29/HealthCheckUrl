package dev.healthcheck.urlcheck.batch

import dev.healthcheck.urlcheck.core.HealthCheckResult

interface BatchHealthChecker {
    fun healthCheckBatch(urls: List<String>, clientIp: String): List<HealthCheckResult>
}
