package dev.healthcheck.urlcheck.single

import dev.healthcheck.cache.UrlCache
import dev.healthcheck.common.enums.HealthReason
import dev.healthcheck.common.enums.HealthStatus
import dev.healthcheck.config.HealthCheckProperties
import dev.healthcheck.ratelimit.ClientRateLimiter
import dev.healthcheck.ratelimit.HostRateLimiter
import dev.healthcheck.ratelimit.RateLimitExceededException
import dev.healthcheck.security.loopbackAllowingClient
import dev.healthcheck.urlcheck.core.InvalidUrlException
import dev.healthcheck.urlcheck.core.NO_HTTP_STATUS
import dev.healthcheck.urlcheck.core.RedirectFollower
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.net.ServerSocket
import java.time.Duration
import java.util.concurrent.TimeUnit

class HealthCheckServiceTest {

    @StartStop
    private val server = MockWebServer()

    private fun service(
        maxRedirects: Int = 5,
        readTimeout: Duration = Duration.ofSeconds(2),
        extraPorts: Set<Int> = emptySet(),
        perIp: Long = 1_000,
        perHost: Long = 1_000,
    ): HealthCheckService {
        val client = loopbackAllowingClient(setOf(80, 443, server.port) + extraPorts, readTimeout = readTimeout)
        val generous = limit(1_000)
        return HealthCheckService(
            RedirectFollower(client, maxRedirects, Duration.ofSeconds(10)),
            UrlCache(Duration.ofMinutes(5)),
            ClientRateLimiter(limit(perIp), generous, generous),
            HostRateLimiter(limit(perHost)),
        )
    }

    private fun limit(capacity: Long) = HealthCheckProperties.Limit(capacity, Duration.ofMinutes(10))

    private fun HealthCheckService.healthCheck(url: String) = healthCheck(url, "203.0.113.1")

    private fun url(path: String = "/") = "http://127.0.0.1:${server.port}$path"

    private fun respond(code: Int) = server.enqueue(MockResponse.Builder().code(code).build())

    private fun redirectTo(location: String) =
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", location).build())

    @Test
    fun `reports UP with the http status and the same final url when there are no redirects`() {
        respond(200)

        val response = service().healthCheck("http://127.0.0.1:${server.port}")

        assertThat(response.url).isEqualTo(url("/"))
        assertThat(response.finalUrl).isEqualTo(url("/"))
        assertThat(response.status).isEqualTo(HealthStatus.UP)
        assertThat(response.httpStatus).isEqualTo(200)
        assertThat(response.redirects).isEqualTo(0)
        assertThat(response.reason).isEqualTo(HealthReason.SUCCESS)
    }

    @Test
    fun `sends a HEAD with the fixed User-Agent`() {
        respond(200)

        service().healthCheck(url())

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("HEAD")
        assertThat(request.headers["User-Agent"])
            .isEqualTo("HealthCheckUrl/1.0 (+https://github.com/CarlosLagos29/HealthCheckUrl)")
    }

    @ParameterizedTest
    @CsvSource(
        "404, DOWN, HTTP_4XX",
        "403, BLOCKED, ACCESS_DENIED",
    )
    fun `classifies the http status of the site`(code: Int, status: HealthStatus, reason: HealthReason) {
        respond(code)

        val response = service().healthCheck(url())

        assertThat(response.status).isEqualTo(status)
        assertThat(response.httpStatus).isEqualTo(code)
        assertThat(response.reason).isEqualTo(reason)
    }

    @Test
    fun `falls back to GET when HEAD is not allowed`() {
        respond(405)
        respond(200)

        val response = service().healthCheck(url())

        assertThat(response.status).isEqualTo(HealthStatus.UP)
        assertThat(response.httpStatus).isEqualTo(200)
        assertThat(server.takeRequest().method).isEqualTo("HEAD")
        assertThat(server.takeRequest().method).isEqualTo("GET")
    }

    @Test
    fun `follows a redirect and reports the final url`() {
        redirectTo("/final")
        respond(200)

        val response = service().healthCheck(url("/start"))

        assertThat(response.url).isEqualTo(url("/start"))
        assertThat(response.finalUrl).isEqualTo(url("/final"))
        assertThat(response.redirects).isEqualTo(1)
        assertThat(response.status).isEqualTo(HealthStatus.UP)
    }

    @Test
    fun `reports TOO_MANY_REDIRECTS on a redirect loop`() {
        repeat(3) { redirectTo("/loop") }

        val response = service(maxRedirects = 2).healthCheck(url("/start"))

        assertThat(response.status).isEqualTo(HealthStatus.DOWN)
        assertThat(response.reason).isEqualTo(HealthReason.TOO_MANY_REDIRECTS)
        assertThat(response.httpStatus).isEqualTo(NO_HTTP_STATUS)
        assertThat(response.redirects).isEqualTo(2)
    }

    @Test
    fun `reports TIMEOUT on a slow response`() {
        server.enqueue(MockResponse.Builder().code(200).headersDelay(1, TimeUnit.SECONDS).build())

        val response = service(readTimeout = Duration.ofMillis(200)).healthCheck(url())

        assertThat(response.status).isEqualTo(HealthStatus.DOWN)
        assertThat(response.reason).isEqualTo(HealthReason.TIMEOUT)
        assertThat(response.httpStatus).isEqualTo(NO_HTTP_STATUS)
        assertThat(response.latencyMs).isGreaterThanOrEqualTo(200)
    }

    @Test
    fun `reports REJECTED for a private address without connecting`() {
        val response = service().healthCheck("http://10.0.0.1/")

        assertThat(response.status).isEqualTo(HealthStatus.REJECTED)
        assertThat(response.reason).isEqualTo(HealthReason.PRIVATE_ADDRESS)
        assertThat(response.httpStatus).isEqualTo(NO_HTTP_STATUS)
    }

    @Test
    fun `reports REJECTED and where it stopped for a redirect to a private address`() {
        redirectTo("http://169.254.169.254/latest/meta-data/")

        val response = service().healthCheck(url("/start"))

        assertThat(response.status).isEqualTo(HealthStatus.REJECTED)
        assertThat(response.reason).isEqualTo(HealthReason.PRIVATE_ADDRESS)
        assertThat(response.finalUrl).isEqualTo("http://169.254.169.254/latest/meta-data/")
        assertThat(response.redirects).isEqualTo(1)
    }

    @Test
    fun `reports REJECTED for a port that is not allowed`() {
        val response = service().healthCheck("http://example.com:8080/")

        assertThat(response.status).isEqualTo(HealthStatus.REJECTED)
        assertThat(response.reason).isEqualTo(HealthReason.PORT_NOT_ALLOWED)
    }

    @Test
    fun `reports CONNECTION_ERROR when nothing is listening`() {
        val closedPort = ServerSocket(0).use { it.localPort }

        val response = service(extraPorts = setOf(closedPort)).healthCheck("http://127.0.0.1:$closedPort/")

        assertThat(response.status).isEqualTo(HealthStatus.DOWN)
        assertThat(response.reason).isEqualTo(HealthReason.CONNECTION_ERROR)
    }

    @Test
    fun `reports DNS_ERROR for a host that does not exist`() {
        val response = service().healthCheck("http://does-not-exist.invalid/")

        assertThat(response.status).isEqualTo(HealthStatus.DOWN)
        assertThat(response.reason).isEqualTo(HealthReason.DNS_ERROR)
    }

    @Test
    fun `rejects an invalid url before connecting`() {
        assertThatThrownBy { service().healthCheck("ftp://example.com") }
            .isInstanceOf(InvalidUrlException::class.java)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `serves a repeated url from the cache without calling the site again`() {
        respond(200)
        val service = service()

        val first = service.healthCheck(url())
        val second = service.healthCheck(url())

        assertThat(second).isEqualTo(first)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `rejects a host over its limit without calling the site and does not charge cached urls`() {
        respond(200)
        val service = service(perHost = 1)

        service.healthCheck(url("/a"))
        val cached = service.healthCheck(url("/a"))
        val limited = service.healthCheck(url("/b"))

        assertThat(cached.status).isEqualTo(HealthStatus.UP)
        assertThat(limited.status).isEqualTo(HealthStatus.REJECTED)
        assertThat(limited.reason).isEqualTo(HealthReason.HOST_RATE_LIMITED)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `rejects a client over its limit before calling the site`() {
        respond(200)
        val service = service(perIp = 1)
        service.healthCheck(url())

        assertThatThrownBy { service.healthCheck(url("/other")) }
            .isInstanceOf(RateLimitExceededException::class.java)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `does not charge the client for an invalid url`() {
        respond(200)
        val service = service(perIp = 1)

        assertThatThrownBy { service.healthCheck("ftp://example.com") }.isInstanceOf(InvalidUrlException::class.java)

        assertThat(service.healthCheck(url()).status).isEqualTo(HealthStatus.UP)
    }
}
