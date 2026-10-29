package dev.healthcheck.urlcheck

class BatchTooLargeException(maxUrls: Int) : IllegalArgumentException("A batch accepts at most $maxUrls URLs")

interface BatchHealthChecker {
    fun healthCheckBatch(urls: List<String>): List<HealthCheckResult>
}
