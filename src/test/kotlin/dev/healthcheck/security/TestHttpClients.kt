package dev.healthcheck.security

import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Proxy
import java.time.Duration

fun loopbackAllowingClient(
    allowedPorts: Set<Int>,
    dns: Dns = Dns.SYSTEM,
    readTimeout: Duration = Duration.ofSeconds(2),
): OkHttpClient {
    val validator = LoopbackAllowingValidator()
    return OkHttpClient.Builder()
        .readTimeout(readTimeout)
        .followRedirects(false)
        .followSslRedirects(false)
        .dns(SafeDns(validator, dns))
        .proxy(Proxy.NO_PROXY)
        .addInterceptor(SafeUrlInterceptor(validator, allowedPorts))
        .build()
}
