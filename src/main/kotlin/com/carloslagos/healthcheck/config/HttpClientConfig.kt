package com.carloslagos.healthcheck.config

import okhttp3.OkHttpClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class HttpClientConfig {

    @Bean
    fun httpClient(properties: HealthCheckProperties): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(properties.timeouts.connect)
            .readTimeout(properties.timeouts.read)
            .followRedirects(true) // En la Etapa 2 pasan a seguirse a mano para validar cada salto
            .build()
    }
}
