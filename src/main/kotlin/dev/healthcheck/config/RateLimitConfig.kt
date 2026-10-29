package dev.healthcheck.config

import dev.healthcheck.cache.UrlCache
import dev.healthcheck.ratelimit.ClientRateLimiter
import dev.healthcheck.ratelimit.HostRateLimiter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RateLimitConfig {

    @Bean
    fun clientRateLimiter(properties: HealthCheckProperties): ClientRateLimiter =
        with(properties.rateLimit) { ClientRateLimiter(perIp, globalSingle, globalBatch) }

    @Bean
    fun hostRateLimiter(properties: HealthCheckProperties): HostRateLimiter =
        HostRateLimiter(properties.rateLimit.perHost)

    @Bean
    fun urlCache(properties: HealthCheckProperties): UrlCache = UrlCache(properties.cache.ttl)
}
