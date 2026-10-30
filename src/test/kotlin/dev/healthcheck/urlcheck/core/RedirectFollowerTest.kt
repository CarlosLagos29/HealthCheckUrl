package dev.healthcheck.urlcheck.core

import dev.healthcheck.common.enums.HealthReason
import dev.healthcheck.security.RejectedAddressException
import dev.healthcheck.security.loopbackAllowingClient
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import okhttp3.Dns
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.InterruptedIOException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.concurrent.TimeUnit

class RedirectFollowerTest {

    @StartStop
    private val server = MockWebServer()

    private val fakeDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> = if (hostname ==
            "internal.test"
        ) {
            listOf(InetAddress.getByName("10.0.0.1"))
        } else {
            Dns.SYSTEM.lookup(hostname)
        }
    }

    private fun follower(
        maxRedirects: Int = 5,
        readTimeout: Duration = Duration.ofSeconds(2),
        totalTimeout: Duration = Duration.ofSeconds(10),
    ): RedirectFollower {
        val client = loopbackAllowingClient(setOf(80, 443, server.port), fakeDns, readTimeout)
        return RedirectFollower(client, maxRedirects, totalTimeout)
    }

    private fun redirectTo(location: String) = MockResponse.Builder().code(302).addHeader("Location", location).build()

    private fun ok() = MockResponse.Builder().code(200).build()

    private fun startRequest() = Request.Builder().url(server.url("/start")).build()

    private fun headRequest() = Request.Builder().url(server.url("/start")).head().build()

    private fun respond(code: Int) = MockResponse.Builder().code(code).build()

    @Test
    fun `follows a redirect and reports the final url`() {
        server.enqueue(redirectTo("/final"))
        server.enqueue(ok())

        val result = follower().execute(startRequest())

        assertThat(result.code).isEqualTo(200)
        assertThat(result.finalUrl).isEqualTo(server.url("/final"))
        assertThat(result.redirects).isEqualTo(1)
    }

    @Test
    fun `returns the redirect response when there is no Location header`() {
        server.enqueue(MockResponse.Builder().code(302).build())

        val result = follower().execute(startRequest())

        assertThat(result.code).isEqualTo(302)
        assertThat(result.redirects).isEqualTo(0)
    }

    @Test
    fun `blocks a redirect to a private IP`() {
        server.enqueue(redirectTo("http://169.254.169.254/latest/meta-data/"))

        assertThatThrownBy { follower().execute(startRequest()) }
            .isInstanceOf(FollowFailedException::class.java)
            .cause()
            .isInstanceOfSatisfying(RejectedAddressException::class.java) {
                assertThat(it.reason).isEqualTo(HealthReason.PRIVATE_ADDRESS)
            }
    }

    @Test
    fun `blocks a redirect to a host that resolves to a private IP`() {
        server.enqueue(redirectTo("http://internal.test/"))

        assertThatThrownBy { follower().execute(startRequest()) }
            .isInstanceOf(FollowFailedException::class.java)
            .cause()
            .isInstanceOfSatisfying(RejectedAddressException::class.java) {
                assertThat(it.reason).isEqualTo(HealthReason.PRIVATE_ADDRESS)
            }
    }

    @Test
    fun `blocks a redirect to a port that is not allowed`() {
        server.enqueue(redirectTo("http://localhost:22/"))

        assertThatThrownBy { follower().execute(startRequest()) }
            .isInstanceOf(FollowFailedException::class.java)
            .cause()
            .isInstanceOfSatisfying(RejectedAddressException::class.java) {
                assertThat(it.reason).isEqualTo(HealthReason.PORT_NOT_ALLOWED)
            }
    }

    @Test
    fun `follows exactly the maximum number of redirects`() {
        server.enqueue(redirectTo("/1"))
        server.enqueue(redirectTo("/2"))
        server.enqueue(ok())

        val result = follower(maxRedirects = 2).execute(startRequest())

        assertThat(result.code).isEqualTo(200)
        assertThat(result.redirects).isEqualTo(2)
    }

    @Test
    fun `fails when the maximum number of redirects is exceeded`() {
        repeat(3) { server.enqueue(redirectTo("/loop")) }

        assertThatThrownBy { follower(maxRedirects = 2).execute(startRequest()) }
            .isInstanceOf(FollowFailedException::class.java)
            .cause()
            .isInstanceOf(TooManyRedirectsException::class.java)
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun `reports the last url and redirects when failing mid-chain`() {
        server.enqueue(redirectTo("/hop"))
        server.enqueue(redirectTo("http://169.254.169.254/latest/meta-data/"))

        assertThatThrownBy { follower().execute(startRequest()) }
            .isInstanceOfSatisfying(FollowFailedException::class.java) {
                assertThat(it.lastUrl.toString()).isEqualTo("http://169.254.169.254/latest/meta-data/")
                assertThat(it.redirects).isEqualTo(2)
            }
    }

    @Test
    fun `retries a HEAD rejected with 405 using GET`() {
        server.enqueue(respond(405))
        server.enqueue(ok())

        val result = follower().execute(headRequest())

        assertThat(result.code).isEqualTo(200)
        assertThat(result.redirects).isEqualTo(0)
        assertThat(server.takeRequest().method).isEqualTo("HEAD")
        assertThat(server.takeRequest().method).isEqualTo("GET")
    }

    @Test
    fun `retries a HEAD rejected with 501 using GET`() {
        server.enqueue(respond(501))
        server.enqueue(ok())

        assertThat(follower().execute(headRequest()).code).isEqualTo(200)
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `keeps using GET for the rest of the chain after the fallback`() {
        server.enqueue(respond(405))
        server.enqueue(redirectTo("/final"))
        server.enqueue(ok())

        val result = follower().execute(headRequest())

        assertThat(result.code).isEqualTo(200)
        assertThat(result.redirects).isEqualTo(1)
        assertThat(List(3) { server.takeRequest().method }).containsExactly("HEAD", "GET", "GET")
    }

    @Test
    fun `does not retry a GET rejected with 405`() {
        server.enqueue(respond(405))

        assertThat(follower().execute(startRequest()).code).isEqualTo(405)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `fails with a read timeout on a slow response`() {
        server.enqueue(MockResponse.Builder().code(200).headersDelay(1, TimeUnit.SECONDS).build())

        assertThatThrownBy { follower(readTimeout = Duration.ofMillis(200)).execute(startRequest()) }
            .isInstanceOf(FollowFailedException::class.java)
            .cause()
            .isInstanceOf(SocketTimeoutException::class.java)
    }

    @Test
    fun `fails when the total timeout runs out across slow redirects`() {
        val slowRedirect = MockResponse.Builder()
            .code(302)
            .addHeader("Location", "/next")
            .headersDelay(300, TimeUnit.MILLISECONDS)
            .build()
        repeat(5) { server.enqueue(slowRedirect) }

        assertThatThrownBy { follower(totalTimeout = Duration.ofMillis(700)).execute(startRequest()) }
            .isInstanceOf(FollowFailedException::class.java)
            .cause()
            .isInstanceOf(InterruptedIOException::class.java)
            .hasMessage("timeout")
        assertThat(server.requestCount).isLessThan(5)
    }

    @Test
    fun `stops at a redirect whose Location cannot be resolved`() {
        server.enqueue(redirectTo("http://"))

        val result = follower().execute(startRequest())

        assertThat(result.code).isEqualTo(302)
        assertThat(result.finalUrl).isEqualTo(server.url("/start"))
        assertThat(result.redirects).isEqualTo(0)
    }

    @Test
    fun `does not send a hop once the total timeout is already used up`() {
        assertThatThrownBy { follower(totalTimeout = Duration.ZERO).execute(startRequest()) }
            .isInstanceOf(FollowFailedException::class.java)
            .cause()
            .isInstanceOf(InterruptedIOException::class.java)
            .hasMessage("timeout")
        assertThat(server.requestCount).isEqualTo(0)
    }
}
