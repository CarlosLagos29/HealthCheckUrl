package dev.healthcheck.urlcheck

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
        private const val USER_AGENT = "HealthCheckUrl/1.0 (+https://github.com/CarlosLagos29/HealthCheckUrl)"
    }

    override fun healthCheck(url: String): HealthCheckResult {
        val target = UrlNormalizer.normalize(url)
        val start = System.nanoTime()

        val result = try {
            val followed = redirectFollower.execute(headRequest(target))
            HealthCheckMapper.fromResponse(target, followed, elapsedMs(start), Instant.now())
        } catch (e: FollowFailedException) {
            LOG.debug("Check of {} failed at {}: {}", target, e.lastUrl, e.cause.toString())
            HealthCheckMapper.fromFailure(target, e, elapsedMs(start), Instant.now())
        }

        LOG.debug(
            "Checked {}: status={} reason={} httpStatus={} finalUrl={} redirects={} latencyMs={}",
            result.url, result.status, result.reason, result.httpStatus, result.finalUrl, result.redirects, result.latencyMs,
        )
        return result
    }

    private fun headRequest(url: HttpUrl): Request =
        Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .build()

    private fun elapsedMs(start: Long): Long = Duration.ofNanos(System.nanoTime() - start).toMillis()
}
