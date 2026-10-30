package dev.healthcheck.urlcheck.core

import dev.healthcheck.common.enums.HealthReason
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HealthCheckMessagesTest {

    @Test
    fun `gives every reason its own non-blank message`() {
        val messages = HealthReason.entries.map { it.toMessage() }

        assertThat(messages).allSatisfy { assertThat(it).isNotBlank() }
        assertThat(messages).doesNotHaveDuplicates()
    }
}
