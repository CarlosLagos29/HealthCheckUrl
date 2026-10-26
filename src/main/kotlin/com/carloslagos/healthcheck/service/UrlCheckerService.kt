package com.carloslagos.healthcheck.service

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class UrlCheckerService(private val httpClient: OkHttpClient) : UrlChecker {

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(UrlCheckerService::class.java)

        // Se reemplaza por un User-Agent fijo y honesto en la Etapa 4
        private val USER_AGENTS = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
            "curl/8.5.0",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:124.0) Gecko/20100101 Firefox/124.0",
        )
    }

    override fun healthCheck(url: String): Boolean {
        val urlWithPrefix = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        val urlParsed = urlWithPrefix.toHttpUrlOrNull() ?: return false

        for (userAgent in USER_AGENTS) {
            val request = Request.Builder()
                .url(urlParsed)
                .get()
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .build()

            try {
                // Cerrar la respuesta sin leer el body: con el código de estado alcanza
                val responseCode = httpClient.newCall(request).execute().use { it.code }

                if (responseCode < 400) {
                    return true
                } else if (responseCode in 401..403) {
                    LOG.debug("Acceso denegado")
                    return true
                } else if (responseCode == 404) {
                    LOG.debug("No encontrada: Código 404")
                    return false
                }

                LOG.debug("User-Agent probado: $userAgent -> Código de respuesta: $responseCode")
            } catch (e: Exception) {
                LOG.debug("Error con User-Agent: $userAgent -> ${e.message}")
            }
        }
        return false
    }
}
