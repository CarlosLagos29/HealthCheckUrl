package dev.healthcheck.urlcheck

import okhttp3.HttpUrl
import okhttp3.Request
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class HealthCheckService(private val redirectFollower: RedirectFollower) : HealthChecker {

    companion object {
        private const val USER_AGENT = "HealthCheckUrl/1.0 (+https://github.com/CarlosLagos29/HealthCheckUrl)"
    }

    override fun healthCheck(url: String): HealthCheckResponse {
        val target = UrlNormalizer.normalize(url)
        val start = System.nanoTime()

        return try {
            val followed = redirectFollower.execute(headRequest(target))
            HealthCheckMapper.fromResponse(target, followed, elapsedMs(start), Instant.now())
        } catch (e: FollowFailedException) {
            HealthCheckMapper.fromFailure(target, e, elapsedMs(start), Instant.now())
        }
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
