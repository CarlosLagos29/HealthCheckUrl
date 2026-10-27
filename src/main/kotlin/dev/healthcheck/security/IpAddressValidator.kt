package dev.healthcheck.security

import org.springframework.stereotype.Component
import java.net.InetAddress

@Component
class IpAddressValidator {

    private companion object {
        val BLOCKED_RANGES = listOf(
            "0.0.0.0/8",
            "100.64.0.0/10",
            "255.255.255.255/32",
            "fc00::/7"
        ).map { cidr ->
            val (ip, prefix) = cidr.split("/")
            InetAddress.getByName(ip).address to prefix.toInt()
        }
    }

    fun isBlocked(address: InetAddress): Boolean =
        address.isAnyLocalAddress ||
                address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress ||
                address.isMulticastAddress ||
                BLOCKED_RANGES.any { (network, prefix) ->
                    isInRange(address.address, network, prefix)
                }

    private fun isInRange(
        target: ByteArray,
        network: ByteArray,
        prefix: Int
    ): Boolean {
        if (target.size != network.size) return false

        val fullBytes = prefix / 8
        val remainingBits = prefix % 8

        for (i in 0 until fullBytes) {
            if (target[i] != network[i]) return false
        }

        if (remainingBits == 0) return true

        val mask = (0xFF shl (8 - remainingBits)) and 0xFF

        return (target[fullBytes].toInt() and mask) ==
                (network[fullBytes].toInt() and mask)
    }
}
