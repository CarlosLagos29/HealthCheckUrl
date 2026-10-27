package dev.healthcheck.security

import okhttp3.Dns
import java.net.InetAddress

class SafeDns(
    private val validator: IpAddressValidator,
    private val delegate: Dns = Dns.SYSTEM,
) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)

        if (addresses.any { validator.isBlocked(it) }) {
            throw BlockedAddressException(
                BlockReason.PRIVATE_ADDRESS,
                "Host $hostname resolves to a blocked address",
            )
        }

        return addresses
    }
}
