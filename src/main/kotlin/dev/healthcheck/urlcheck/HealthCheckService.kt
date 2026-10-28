package dev.healthcheck.urlcheck

import dev.healthcheck.common.enums.HealthStatus
import okhttp3.HttpUrl
import okhttp3.Request
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class HealthCheckService(private val redirectFollower: RedirectFollower) : HealthChecker {

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(HealthCheckService::class.java)

        private val USER_AGENTS = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
            "curl/8.5.0",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:124.0) Gecko/20100101 Firefox/124.0",
        )
    }

    override fun healthCheck(url: String): HealthCheckResponse {
        val target = UrlNormalizer.normalize(url)
        val start = System.nanoTime()
        val alive = isAlive(target)

        return HealthCheckResponse(
            url = target.toString(),
            finalUrl = null,
            status = if (alive) HealthStatus.UP else HealthStatus.DOWN,
            httpStatus = null,
            latencyMs = Duration.ofNanos(System.nanoTime() - start).toMillis(),
            redirects = 0,
            reason = null,
            checkedAt = Instant.now(),
        )
    }

    private fun isAlive(url: HttpUrl): Boolean {
        for (userAgent in USER_AGENTS) {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .build()

            try {
                val responseCode = redirectFollower.execute(request).code

                if (responseCode < 400) {
                    return true
                } else if (responseCode in 401..403) {
                    LOG.debug("Acceso denegado")
                    return true
                } else if (responseCode == 404) {
                    LOG.debug("No encontrada: Código 404")
                    return false
                }

                LOG.debug("User-Agent probado: $userAgent -> Código de respuesta: $responseCode")
            } catch (e: Exception) {
                LOG.debug("Error con User-Agent: $userAgent -> ${e.message}")
            }
        }
        return false
    }
}
