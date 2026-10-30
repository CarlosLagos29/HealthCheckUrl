package dev.healthcheck.security

import java.net.InetAddress

class LoopbackAllowingValidator : IpAddressValidator() {

    override fun isBlocked(address: InetAddress): Boolean = !address.isLoopbackAddress && super.isBlocked(address)
}
