package dev.healthcheck.urlcheck

interface HealthChecker {
    fun healthCheck(url: String): HealthCheckResult
}
