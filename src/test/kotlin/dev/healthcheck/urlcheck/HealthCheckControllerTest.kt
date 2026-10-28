package dev.healthcheck.urlcheck

import dev.healthcheck.common.enums.HealthStatus
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import java.time.Instant

@WebMvcTest(HealthCheckController::class)
class HealthCheckControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var healthChecker: HealthChecker

    private fun postCheck(body: String): ResultActionsDsl =
        mockMvc.post("/api/v1/health-checks") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun upResponse() = HealthCheckResponse(
        url = "https://example.com/",
        finalUrl = "https://example.com/",
        status = HealthStatus.UP,
        httpStatus = null,
        latencyMs = 42,
        redirects = 0,
        reason = null,
        checkedAt = Instant.parse("2026-10-28T10:00:00Z"),
    )

    @Test
    fun `returns the check result`() {
        given(healthChecker.healthCheck("example.com")).willReturn(upResponse())

        postCheck("""{"url": "example.com"}""").andExpect {
            status { isOk() }
            jsonPath("$.url") { value("https://example.com/") }
            jsonPath("$.status") { value("UP") }
            jsonPath("$.latencyMs") { value(42) }
            jsonPath("$.checkedAt") { value("2026-10-28T10:00:00Z") }
        }
    }

    @Test
    fun `omits httpStatus and reason when they are null`() {
        given(healthChecker.healthCheck(anyString())).willReturn(upResponse())

        postCheck("""{"url": "example.com"}""").andExpect {
            status { isOk() }
            jsonPath("$.httpStatus") { doesNotExist() }
            jsonPath("$.reason") { doesNotExist() }
        }
    }

    @Test
    fun `rejects a blank url with field errors`() {
        postCheck("""{"url": "   "}""").andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(400) }
            jsonPath("$.errors.url") { value("must not be blank") }
        }

        verifyNoInteractions(healthChecker)
    }

    @Test
    fun `rejects a body without url`() {
        postCheck("{}").andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("Failed to read request") }
        }

        verifyNoInteractions(healthChecker)
    }

    @Test
    fun `rejects malformed json`() {
        postCheck("""{"url": """).andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("Failed to read request") }
        }
    }

    @Test
    fun `maps an invalid url to a 400 with reason INVALID_URL`() {
        given(healthChecker.healthCheck(anyString())).willThrow(InvalidUrlException("Malformed URL"))

        postCheck("""{"url": "https://exa mple.com"}""").andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.title") { value("Invalid URL") }
            jsonPath("$.detail") { value("Malformed URL") }
            jsonPath("$.reason") { value("INVALID_URL") }
            jsonPath("$.instance") { value("/api/v1/health-checks") }
        }
    }

    @Test
    fun `hides internal details on unexpected errors`() {
        given(healthChecker.healthCheck(anyString())).willThrow(IllegalStateException("secret internal detail"))

        postCheck("""{"url": "example.com"}""").andExpect {
            status { isInternalServerError() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("Unexpected error") }
        }
    }
}
