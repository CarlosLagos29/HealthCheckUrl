package dev.healthcheck.ratelimit

import dev.healthcheck.config.HealthCheckProperties
import io.github.bucket4j.Bucket

fun bucketOf(limit: HealthCheckProperties.Limit): Bucket =
    Bucket.builder()
        .addLimit { it.capacity(limit.capacity).refillGreedy(limit.capacity, limit.period) }
        .build()
