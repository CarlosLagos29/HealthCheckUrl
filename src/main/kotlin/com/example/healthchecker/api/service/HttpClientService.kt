package com.example.healthchecker.api.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import org.springframework.stereotype.Service
import java.net.HttpURLConnection


@Service
class HttpClientService(
    callTimeout: Long = 10000,
    connectTimeout: Long = 5000,
    readTimeout: Long = 5000
) : HttpClient {

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(HttpClientService::class.java)
    }

    override fun healthCheck(url: String): Boolean {
        val urlWithPrefix = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        val urlParsed: URL? = try { URL(urlWithPrefix) } catch (e: Exception) { null }

        if (urlParsed == null) return false

            val userAgents = listOf(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36",
                "curl/8.5.0",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/537.36 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/537.36"
            )

            for (userAgent in userAgents) {
                try {
                    val connection = urlParsed.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", userAgent)
                    connection.setRequestProperty("Accept", "*/*")
                    connection.setRequestProperty("Connection", "close")
                    connection.instanceFollowRedirects = true
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.connect()

                    val responseCode = connection.responseCode
                    if (responseCode == 403) {
                        println("Acceso denegado: Código 403")
                        return true
                    } else if (responseCode == 404) {
                        println("No encontrada: Código 404")
                        return false
                    }
                    if (responseCode < 500) {
                        val stream = connection.inputStream
                        stream.use { it.readBytes() }
                        return true
                    }
                    println("User-Agent probado: $userAgent -> Código de respuesta: $responseCode")
                } catch (e: Exception) {
                    println("Error con User-Agent: $userAgent -> ${e.message}")
                }
            }
            return false
    }
}