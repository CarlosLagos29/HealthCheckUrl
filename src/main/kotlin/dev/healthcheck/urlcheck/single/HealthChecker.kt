package dev.healthcheck.urlcheck.single

import dev.healthcheck.urlcheck.core.HealthCheckResult
import okhttp3.HttpUrl

interface HealthChecker {
    fun healthCheck(url: String, clientIp: String): HealthCheckResult

    fun check(target: HttpUrl): HealthCheckResult
}
