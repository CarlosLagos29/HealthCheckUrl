package dev.healthcheck.security

import dev.healthcheck.common.enums.FailureReason
import okhttp3.Dns
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.UnknownHostException

class SafeDnsTest {

    private fun resolvingTo(vararg ips: String) = SafeDns(
        IpAddressValidator(),
        object : Dns {
            override fun lookup(hostname: String) = ips.map { InetAddress.getByName(it) }
        },
    )

    @Test
    fun `returns resolved addresses when all are public`() {
        val addresses = resolvingTo("8.8.8.8", "1.1.1.1").lookup("example.com")

        assertThat(addresses).containsExactly(
            InetAddress.getByName("8.8.8.8"),
            InetAddress.getByName("1.1.1.1"),
        )
    }

    @Test
    fun `rejects host resolving to a private address`() {
        assertThatThrownBy { resolvingTo("10.0.0.1").lookup("example.com") }
            .isInstanceOfSatisfying(RejectedAddressException::class.java) {
                assertThat(it.reason).isEqualTo(FailureReason.PRIVATE_ADDRESS)
            }
    }

    @Test
    fun `rejects host when any resolved address is blocked`() {
        assertThatThrownBy { resolvingTo("8.8.8.8", "127.0.0.1").lookup("example.com") }
            .isInstanceOfSatisfying(RejectedAddressException::class.java) {
                assertThat(it.reason).isEqualTo(FailureReason.PRIVATE_ADDRESS)
            }
    }

    @Test
    fun `rejects localhost with the real system resolver`() {
        assertThatThrownBy { SafeDns(IpAddressValidator()).lookup("localhost") }
            .isInstanceOfSatisfying(RejectedAddressException::class.java) {
                assertThat(it.reason).isEqualTo(FailureReason.PRIVATE_ADDRESS)
            }
    }

    @Test
    fun `does not expose the resolved address in the error message`() {
        assertThatThrownBy { resolvingTo("10.0.0.1").lookup("internal.example.com") }
            .hasMessageContaining("internal.example.com")
            .hasMessageNotContaining("10.0.0.1")
    }

    @Test
    fun `propagates unknown host errors`() {
        val failingDns = SafeDns(
            IpAddressValidator(),
            object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    throw UnknownHostException(hostname)
            },
        )

        assertThatThrownBy { failingDns.lookup("does-not-exist.example") }
            .isInstanceOf(UnknownHostException::class.java)
    }
}
