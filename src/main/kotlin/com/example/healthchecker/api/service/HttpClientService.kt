package com.example.healthchecker.api.service

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.http2.StreamResetException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.TimeUnit
import org.springframework.stereotype.Service

@Service
class HttpClientService(
    callTimeout: Long = 10000,
    connectTimeout: Long = 5000,
    readTimeout: Long = 5000
) : HttpClient {

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(HttpClientService::class.java)
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .callTimeout(callTimeout, TimeUnit.MILLISECONDS)
            .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    override fun healthCheck(url: String): Boolean {
        val urlWithPrefix = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        val urlParsed: URL? = try { URL(urlWithPrefix) } catch (e: Exception) { null }

        if (urlParsed == null) return false

        var response: Response? = null
        var success = false
        var attempts = 0
        val maxRetries = 4

        while (!success && attempts <= maxRetries) {
            try {
                val request = buildHealthCheckRequest(urlParsed)
                response = client.newCall(request).execute()
                val responseCode = response.code
                success = responseCode < 500
                LOG.debug("Success $responseCode: connecting to URL [$urlWithPrefix] - Attempt: $attempts")
            } catch (e: StreamResetException) {
                LOG.warn("HTTP/2 error detected, retrying with HTTP/1.1 for URL: $urlWithPrefix")
                return retryWithHttp1(urlParsed)
            } catch (e: Exception) {
                LOG.debug("Error urlHealthCheck(): attempt[$attempts] -> ${e.message} | connecting to URL [$urlWithPrefix]")
                e.printStackTrace()
            } finally {
                response?.close()
            }

            if (!success) {
                attempts++
                Thread.sleep(1000)
            }
        }
        return success
    }

    private fun retryWithHttp1(url: URL): Boolean {
        val clientHttp1 by lazy {
            OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .protocols(listOf(Protocol.HTTP_2))
                .callTimeout(15000, TimeUnit.MILLISECONDS)
                .connectTimeout(10000, TimeUnit.MILLISECONDS)
                .readTimeout(5000, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        return try {
            val request = buildHealthCheckRequest(url)
            val response = clientHttp1.newCall(request).execute()
            val responseCode = response.code
            response.close()
            responseCode < 500
        }
        catch (e: Exception){
            e.printStackTrace()
            LOG.warn("Failed HTTP/1.1 fallback for URL: $url -> ${e.message}")
            false
        }
    }

    private fun buildHealthCheckRequest(url: URL): Request {
        return Request.Builder()
            .url(url)
            .header("User-Agent", "curl/8.5.0")
            .addHeader("Accept", "*/*")
            .addHeader("Connection", "close")
            .addHeader("Cache-Control", "no-cache")
            .addHeader("Host", url.host)
            .addHeader("Accept-Encoding", "gzip, deflate, br")
            .addHeader("Connection", "keep-alive")
            .build()
    }
}