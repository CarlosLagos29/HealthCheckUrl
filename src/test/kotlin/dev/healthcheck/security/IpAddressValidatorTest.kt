package dev.healthcheck.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.InetAddress

class IpAddressValidatorTest {

    private val validator = IpAddressValidator()

    private fun isBlocked(ip: String) = validator.isBlocked(InetAddress.getByName(ip))

    @ParameterizedTest
    @ValueSource(strings = ["127.0.0.1", "127.255.255.255", "::1"])
    fun `blocks loopback addresses`(ip: String) {
        assertThat(isBlocked(ip)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "10.0.0.0", "10.255.255.255",
            "172.16.0.0", "172.31.255.255",
            "192.168.0.0", "192.168.255.255",
        ],
    )
    fun `blocks private IPv4 addresses`(ip: String) {
        assertThat(isBlocked(ip)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["169.254.0.0", "169.254.169.254", "169.254.255.255", "fe80::1"])
    fun `blocks link-local addresses including cloud metadata`(ip: String) {
        assertThat(isBlocked(ip)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["100.64.0.0", "100.127.255.255"])
    fun `blocks carrier-grade NAT addresses`(ip: String) {
        assertThat(isBlocked(ip)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["0.0.0.0", "0.255.255.255", "::"])
    fun `blocks this-network addresses`(ip: String) {
        assertThat(isBlocked(ip)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["224.0.0.1", "239.255.255.255", "ff02::1"])
    fun `blocks multicast addresses`(ip: String) {
        assertThat(isBlocked(ip)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["255.255.255.255"])
    fun `blocks broadcast address`(ip: String) {
        assertThat(isBlocked(ip)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["fc00::1", "fd12:3456::1", "fdff:ffff::1"])
    fun `blocks IPv6 unique local addresses`(ip: String) {
        assertThat(isBlocked(ip)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["::ffff:127.0.0.1", "::ffff:10.0.0.1", "::ffff:169.254.169.254"])
    fun `blocks IPv4-mapped IPv6 addresses of blocked ranges`(ip: String) {
        assertThat(isBlocked(ip)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["8.8.8.8", "1.1.1.1", "2606:4700:4700::1111", "2001:4860:4860::8888"])
    fun `allows public addresses`(ip: String) {
        assertThat(isBlocked(ip)).isFalse()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "9.255.255.255", "11.0.0.0",
            "172.15.255.255", "172.32.0.0",
            "192.167.255.255", "192.169.0.0",
            "100.63.255.255", "100.128.0.0",
            "1.0.0.0",
            "fbff:ffff::1", "fe00::1",
        ],
    )
    fun `allows addresses just outside blocked ranges`(ip: String) {
        assertThat(isBlocked(ip)).isFalse()
    }
}
