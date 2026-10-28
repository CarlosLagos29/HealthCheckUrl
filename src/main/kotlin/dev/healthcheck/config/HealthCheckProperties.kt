package dev.healthcheck.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("healthcheck")
data class HealthCheckProperties(
    val timeouts: Timeouts,
    val maxRedirects: Int,
    val allowedPorts: Set<Int>,
    val batch: Batch,
    val rateLimit: RateLimit,
) {
    data class Timeouts(
        val connect: Duration,
        val read: Duration,
        val total: Duration,
    )

    data class Batch(
        val maxUrls: Int,
        val concurrency: Int,
        val timeout: Duration,
    )

    data class RateLimit(
        val perIp: Limit,
        val global: Limit,
        val perHost: Limit,
    )

    data class Limit(
        val capacity: Long,
        val period: Duration,
    )
}
