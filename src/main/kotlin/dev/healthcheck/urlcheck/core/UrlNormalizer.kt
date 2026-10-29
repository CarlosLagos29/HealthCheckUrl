package dev.healthcheck.urlcheck.core

import dev.healthcheck.common.isDomain
import dev.healthcheck.common.isIpLiteral
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class InvalidUrlException(message: String) : IllegalArgumentException(message)

object UrlNormalizer {

    private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://")
    private val ALLOWED_SCHEMES = setOf("http", "https")

    fun normalize(raw: String): HttpUrl {
        val trimmed = raw.trim()
        val scheme = SCHEME.find(trimmed)?.groupValues?.get(1)?.lowercase()

        if (scheme != null && scheme !in ALLOWED_SCHEMES) {
            throw InvalidUrlException("Only http and https URLs are allowed")
        }

        val url = (if (scheme == null) "https://$trimmed" else trimmed).toHttpUrlOrNull()
            ?: throw InvalidUrlException("Malformed URL")

        if (!isIpLiteral(url.host) && !isDomain(url.host)) {
            throw InvalidUrlException("Host ${url.host} is not a valid domain")
        }

        return url
    }

    fun normalizeOrNull(raw: String): HttpUrl? =
        try {
            normalize(raw)
        } catch (e: InvalidUrlException) {
            null
        }
}
