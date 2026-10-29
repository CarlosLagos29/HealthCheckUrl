package dev.healthcheck.security

import dev.healthcheck.common.enums.HealthReason
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
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
    @ValueSource(strings = ["http://127.0.0.1/", "http://[::1]/", "http://[::ffff:127.0.0.1]/"])
    fun `blocks literal private addresses`(url: String) {
        assertThatThrownBy { call(url) }
            .isInstanceOfSatisfying(RejectedAddressException::class.java) {
                assertThat(it.reason).isEqualTo(HealthReason.PRIVATE_ADDRESS)
            }
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://2130706433/", "http://127.1/", "http://127.000.000.001/"])
    fun `blocks alternative notations of private addresses`(url: String) {
        assertThatThrownBy { call(url) }
            .isInstanceOfSatisfying(RejectedAddressException::class.java) {
                assertThat(it.reason).isEqualTo(HealthReason.PRIVATE_ADDRESS)
            }
    }

    @Test
    fun `blocks ports that are not allowed`() {
        assertThatThrownBy { call("http://example.com:22/") }
            .isInstanceOfSatisfying(RejectedAddressException::class.java) {
                assertThat(it.reason).isEqualTo(HealthReason.PORT_NOT_ALLOWED)
            }
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://8.8.8.8/", "https://example.com/"])
    fun `lets allowed urls through`(url: String) {
        assertThat(call(url).code).isEqualTo(200)
    }
}
