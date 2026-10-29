package dev.healthcheck.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LogFormatTest {

    @Test
    fun `leaves normal text untouched`() {
        assertThat("https://example.com/path?q=1".toLogSafe()).isEqualTo("https://example.com/path?q=1")
    }

    @Test
    fun `escapes line breaks so a value cannot forge log lines`() {
        assertThat("x\r\nINFO fake line".toLogSafe()).isEqualTo("x\\u000d\\u000aINFO fake line")
    }

    @Test
    fun `truncates long values and keeps the original length`() {
        val long = "a".repeat(500)

        assertThat(long.toLogSafe()).isEqualTo("${"a".repeat(200)}… (500 chars)")
    }
}
