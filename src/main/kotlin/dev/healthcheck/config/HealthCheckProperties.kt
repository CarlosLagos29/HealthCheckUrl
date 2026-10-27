package dev.healthcheck.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("healthcheck")
data class HealthCheckProperties(
    val timeouts: Timeouts = Timeouts(),
    val maxRedirects: Int = 5,
    val allowedPorts: Set<Int> = setOf(80, 443),
    val batch: Batch = Batch(),
    val rateLimit: RateLimit = RateLimit(),
) {
    data class Timeouts(
        val connect: Duration = Duration.ofSeconds(2),
        val read: Duration = Duration.ofSeconds(2),
    )

    data class Batch(
        val maxUrls: Int = 5,
        val concurrency: Int = 5,
        val timeout: Duration = Duration.ofSeconds(15),
    )

    data class RateLimit(
        val perIp: Limit = Limit(10, Duration.ofMinutes(1)),
        val global: Limit = Limit(20, Duration.ofMinutes(10)),
        val perHost: Limit = Limit(3, Duration.ofMinutes(10)),
    )

    data class Limit(
        val capacity: Long,
        val period: Duration,
    )
}
