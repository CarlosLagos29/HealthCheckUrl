package com.example.healthchecker.api.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import org.springframework.stereotype.Service
import java.net.HttpURLConnection


@Service
class HttpClientService: HttpClient {

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(HttpClientService::class.java)
    }

    override fun healthCheck(url: String): Boolean {
        val urlWithPrefix = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        val urlParsed: URL? = try { URL(urlWithPrefix) } catch (e: Exception) { null }

        if (urlParsed == null) return false

            val userAgents = listOf(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
                "curl/8.5.0",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:124.0) Gecko/20100101 Firefox/124.0"
            )

            for (userAgent in userAgents) {
                try {
                    val connection = urlParsed.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", userAgent)
                    connection.setRequestProperty("Accept", "*/*")
                    connection.setRequestProperty("Connection", "close")
                    connection.instanceFollowRedirects = true
                    connection.connectTimeout = 2000
                    connection.readTimeout = 2000
                    connection.connect()

                    val responseCode = connection.responseCode
                    if (responseCode < 400) {
                        val stream = connection.inputStream
                        stream.use { it.readBytes() }
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