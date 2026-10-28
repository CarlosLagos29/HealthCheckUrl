package dev.healthcheck.urlcheck

import dev.healthcheck.common.enums.FailureReason
import dev.healthcheck.security.BlockedAddressException
import dev.healthcheck.security.LoopbackAllowingValidator
import dev.healthcheck.security.SafeDns
import dev.healthcheck.security.SafeUrlInterceptor
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.Proxy

class RedirectFollowerTest {

    @StartStop
    private val server = MockWebServer()

    private val fakeDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            if (hostname == "internal.test") listOf(InetAddress.getByName("10.0.0.1")) else Dns.SYSTEM.lookup(hostname)
    }

    private fun follower(maxRedirects: Int = 5): RedirectFollower {
        val validator = LoopbackAllowingValidator()
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .dns(SafeDns(validator, fakeDns))
            .proxy(Proxy.NO_PROXY)
            .addInterceptor(SafeUrlInterceptor(validator, setOf(80, 443, server.port)))
            .build()
        return RedirectFollower(client, maxRedirects)
    }

    private fun redirectTo(location: String) = MockResponse.Builder().code(302).addHeader("Location", location).build()

    private fun ok() = MockResponse.Builder().code(200).build()

    private fun startRequest() = Request.Builder().url(server.url("/start")).build()

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
            .isInstanceOfSatisfying(BlockedAddressException::class.java) {
                assertThat(it.reason).isEqualTo(FailureReason.PRIVATE_ADDRESS)
            }
    }

    @Test
    fun `blocks a redirect to a host that resolves to a private IP`() {
        server.enqueue(redirectTo("http://internal.test/"))

        assertThatThrownBy { follower().execute(startRequest()) }
            .isInstanceOfSatisfying(BlockedAddressException::class.java) {
                assertThat(it.reason).isEqualTo(FailureReason.PRIVATE_ADDRESS)
            }
    }

    @Test
    fun `blocks a redirect to a port that is not allowed`() {
        server.enqueue(redirectTo("http://localhost:22/"))

        assertThatThrownBy { follower().execute(startRequest()) }
            .isInstanceOfSatisfying(BlockedAddressException::class.java) {
                assertThat(it.reason).isEqualTo(FailureReason.PORT_NOT_ALLOWED)
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
            .isInstanceOf(TooManyRedirectsException::class.java)
        assertThat(server.requestCount).isEqualTo(3)
    }
}
