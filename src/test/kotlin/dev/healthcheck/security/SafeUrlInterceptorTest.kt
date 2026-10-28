package dev.healthcheck.security

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SafeUrlInterceptorTest {

    private val client = OkHttpClient.Builder()
        .addInterceptor(SafeUrlInterceptor(IpAddressValidator(), setOf(80, 443)))
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
        }
        .build()

    private fun call(url: String) = client.newCall(Request.Builder().url(url).build()).execute()

    @ParameterizedTest
    @ValueSource(strings = ["http://127.0.0.1/", "http://[::1]/", "http://169.254.169.254/", "http://[::ffff:127.0.0.1]/"])
    fun `blocks literal private addresses`(url: String) {
        assertThatThrownBy { call(url) }
            .isInstanceOfSatisfying(BlockedAddressException::class.java) {
                assertThat(it.reason).isEqualTo(BlockReason.PRIVATE_ADDRESS)
            }
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://2130706433/", "http://127.1/", "http://127.000.000.001/"])
    fun `blocks alternative notations of private addresses`(url: String) {
        assertThatThrownBy { call(url) }
            .isInstanceOfSatisfying(BlockedAddressException::class.java) {
                assertThat(it.reason).isEqualTo(BlockReason.PRIVATE_ADDRESS)
            }
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://example.com:8080/", "https://example.com:22/", "http://8.8.8.8:6379/"])
    fun `blocks ports that are not allowed`(url: String) {
        assertThatThrownBy { call(url) }
            .isInstanceOfSatisfying(BlockedAddressException::class.java) {
                assertThat(it.reason).isEqualTo(BlockReason.PORT_NOT_ALLOWED)
            }
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://8.8.8.8/", "https://example.com/", "http://example.com:80/", "https://[2606:4700:4700::1111]/"])
    fun `lets allowed urls through`(url: String) {
        assertThat(call(url).code).isEqualTo(200)
    }
}
