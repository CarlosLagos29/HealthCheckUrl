package dev.healthcheck.urlcheck

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class UrlNormalizerTest {

    private fun normalize(raw: String) = UrlNormalizer.normalize(raw).toString()

    @ParameterizedTest
    @CsvSource(
        "example.com,                https://example.com/",
        "www.example.com/path?q=1,   https://www.example.com/path?q=1",
        "example.com:443,            https://example.com/",
    )
    fun `adds https when the scheme is missing`(raw: String, expected: String) {
        assertThat(normalize(raw)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "http://example.com,         http://example.com/",
        "https://example.com,        https://example.com/",
        "HTTPS://Example.COM,        https://example.com/",
    )
    fun `keeps http and https urls`(raw: String, expected: String) {
        assertThat(normalize(raw)).isEqualTo(expected)
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertThat(normalize("  https://example.com  ")).isEqualTo("https://example.com/")
    }

    @ParameterizedTest
    @ValueSource(strings = ["ftp://example.com", "file:///etc/passwd", "gopher://example.com", "ws://example.com"])
    fun `rejects schemes other than http and https`(raw: String) {
        assertThatThrownBy { normalize(raw) }
            .isInstanceOf(InvalidUrlException::class.java)
            .hasMessage("Only http and https URLs are allowed")
    }

    @ParameterizedTest
    @ValueSource(strings = ["https://wwwgooglecom", "http://localhost", "intranet", "https://example.c", "https://example.c0m"])
    fun `rejects hosts that are not a valid domain`(raw: String) {
        assertThatThrownBy { normalize(raw) }
            .isInstanceOf(InvalidUrlException::class.java)
            .hasMessageEndingWith("is not a valid domain")
    }

    @ParameterizedTest
    @ValueSource(strings = ["https://exa mple.com", "https://", "javascript:alert(1)", "https://example.com:99999"])
    fun `rejects malformed urls`(raw: String) {
        assertThatThrownBy { normalize(raw) }
            .isInstanceOf(InvalidUrlException::class.java)
            .hasMessage("Malformed URL")
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://8.8.8.8", "http://[2606:4700:4700::1111]", "http://2130706433", "http://127.0.0.1"])
    fun `accepts IP literals and leaves blocking to the HTTP client`(raw: String) {
        assertThat(UrlNormalizer.normalize(raw).host).isNotBlank()
    }

    @ParameterizedTest
    @CsvSource(
        "münchen.de,    https://xn--mnchen-3ya.de/",
        "пример.рф,     https://xn--e1afmkfd.xn--p1ai/",
    )
    fun `accepts internationalized domains`(raw: String, expected: String) {
        assertThat(normalize(raw)).isEqualTo(expected)
    }

    @Test
    fun `normalizeOrNull returns the url when it is valid`() {
        assertThat(UrlNormalizer.normalizeOrNull("example.com").toString()).isEqualTo("https://example.com/")
    }

    @ParameterizedTest
    @ValueSource(strings = ["ftp://example.com", "https://wwwgooglecom", "https://exa mple.com"])
    fun `normalizeOrNull returns null instead of throwing for invalid urls`(raw: String) {
        assertThat(UrlNormalizer.normalizeOrNull(raw)).isNull()
    }
}
