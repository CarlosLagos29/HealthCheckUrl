package dev.healthcheck.security

import dev.healthcheck.common.enums.FailureReason
import okhttp3.Dns
import java.net.InetAddress

class SafeDns(
    private val validator: IpAddressValidator,
    private val delegate: Dns = Dns.SYSTEM,
) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)

        if (addresses.any { validator.isBlocked(it) }) {
            throw RejectedAddressException(
                FailureReason.PRIVATE_ADDRESS,
                "Host $hostname resolves to a private address, rejected by the API",
            )
        }

        return addresses
    }
}
