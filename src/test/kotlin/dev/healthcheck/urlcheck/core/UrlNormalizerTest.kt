package dev.healthcheck.urlcheck.core

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class UrlNormalizerTest {

    private fun normalize(raw: String) = UrlNormalizer.normalize(raw).toString()

    @Test
    fun `adds https when the scheme is missing`() {
        assertThat(normalize("www.example.com/path?q=1")).isEqualTo("https://www.example.com/path?q=1")
    }

    @Test
    fun `keeps the http scheme and lowercases scheme and host`() {
        assertThat(normalize("HTTP://Example.COM")).isEqualTo("http://example.com/")
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertThat(normalize("  https://example.com  ")).isEqualTo("https://example.com/")
    }

    @Test
    fun `rejects schemes other than http and https`() {
        assertThatThrownBy { normalize("file:///etc/passwd") }
            .isInstanceOf(InvalidUrlException::class.java)
            .hasMessage("Only http and https URLs are allowed")
    }

    @ParameterizedTest
    @ValueSource(strings = ["https://wwwgooglecom", "https://example.c0m"])
    fun `rejects hosts that are not a valid domain`(raw: String) {
        assertThatThrownBy { normalize(raw) }
            .isInstanceOf(InvalidUrlException::class.java)
            .hasMessageEndingWith("is not a valid domain")
    }

    @Test
    fun `rejects malformed urls`() {
        assertThatThrownBy { normalize("https://exa mple.com") }
            .isInstanceOf(InvalidUrlException::class.java)
            .hasMessage("Malformed URL")
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://127.0.0.1", "http://[::1]"])
    fun `accepts IP literals and leaves blocking to the HTTP client`(raw: String) {
        assertThat(UrlNormalizer.normalize(raw).host).isNotBlank()
    }

    @Test
    fun `accepts internationalized domains`() {
        assertThat(normalize("пример.рф")).isEqualTo("https://xn--e1afmkfd.xn--p1ai/")
    }

    @Test
    fun `normalizeOrNull returns the url when it is valid`() {
        assertThat(UrlNormalizer.normalizeOrNull("example.com").toString()).isEqualTo("https://example.com/")
    }

    @Test
    fun `normalizeOrNull returns null instead of throwing for invalid urls`() {
        assertThat(UrlNormalizer.normalizeOrNull("ftp://example.com")).isNull()
    }
}
