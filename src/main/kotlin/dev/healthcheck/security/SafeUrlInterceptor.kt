package dev.healthcheck.security

import dev.healthcheck.common.enums.HealthReason
import dev.healthcheck.common.isIpLiteral
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.net.InetAddress

class SafeUrlInterceptor(
    private val validator: IpAddressValidator,
    private val allowedPorts: Set<Int>,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        checkPort(url)
        checkLiteralAddress(url)
        return chain.proceed(chain.request())
    }

    private fun checkPort(url: HttpUrl) {
        if (url.port !in allowedPorts) {
            throw RejectedAddressException(HealthReason.PORT_NOT_ALLOWED, "Port ${url.port} is not allowed, rejected by the API")
        }
    }

    private fun checkLiteralAddress(url: HttpUrl) {
        if (isIpLiteral(url.host) && InetAddress.getAllByName(url.host).any { validator.isBlocked(it) }) {
            throw RejectedAddressException(HealthReason.PRIVATE_ADDRESS, "Host ${url.host} is a private address, rejected by the API")
        }
    }
}
