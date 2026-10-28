package dev.healthcheck.config

import dev.healthcheck.security.IpAddressValidator
import dev.healthcheck.security.SafeDns
import dev.healthcheck.security.SafeUrlInterceptor
import dev.healthcheck.urlcheck.RedirectFollower
import okhttp3.OkHttpClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.Proxy

@Configuration
class HttpClientConfig {

    @Bean
    fun httpClient(properties: HealthCheckProperties, validator: IpAddressValidator): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(properties.timeouts.connect)
            .readTimeout(properties.timeouts.read)
            .followRedirects(false)
            .followSslRedirects(false)
            .dns(SafeDns(validator))
            .proxy(Proxy.NO_PROXY)
            .addInterceptor(SafeUrlInterceptor(validator, properties.allowedPorts))
            .build()
    }

    @Bean
    fun redirectFollower(httpClient: OkHttpClient, properties: HealthCheckProperties): RedirectFollower =
        RedirectFollower(httpClient, properties.maxRedirects, properties.timeouts.total)
}
