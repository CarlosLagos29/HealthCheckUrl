package dev.healthcheck.ratelimit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private fun checkFrom(port: Int, connectingIp: String): Int {
    val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/api/v1/health-checks"))
        .header("Content-Type", "application/json")
        .header("CF-Connecting-IP", connectingIp)
        .POST(HttpRequest.BodyPublishers.ofString("""{"url": "http://10.0.0.1/"}"""))
        .build()
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
}

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["healthcheck.rate-limit.per-ip.capacity=1"],
)
class TrustedProxyClientIpTest {

    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `uses the client ip set by Cloudflare when the request comes from a trusted proxy`() {
        assertThat(checkFrom(port, "198.51.100.1")).isEqualTo(200)
        assertThat(checkFrom(port, "198.51.100.2")).isEqualTo(200)
    }
}

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "healthcheck.rate-limit.per-ip.capacity=1",
        "server.tomcat.remoteip.internal-proxies=192\\.0\\.2\\.1",
    ],
)
class UntrustedClientIpTest {

    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `ignores a forged CF-Connecting-IP from an untrusted client`() {
        assertThat(checkFrom(port, "198.51.100.1")).isEqualTo(200)
        assertThat(checkFrom(port, "198.51.100.2")).isEqualTo(429)
    }
}
