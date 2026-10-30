package dev.healthcheck.urlcheck

import dev.healthcheck.common.enums.HealthReason
import dev.healthcheck.common.enums.HealthStatus
import dev.healthcheck.config.HealthCheckProperties
import dev.healthcheck.ratelimit.RateLimitExceededException
import dev.healthcheck.urlcheck.batch.BatchHealthChecker
import dev.healthcheck.urlcheck.core.HealthCheckResult
import dev.healthcheck.urlcheck.core.InvalidUrlException
import dev.healthcheck.urlcheck.single.HealthChecker
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant

@WebMvcTest(HealthCheckController::class)
@EnableConfigurationProperties(HealthCheckProperties::class)
class HealthCheckControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var healthChecker: HealthChecker

    @MockitoBean
    private lateinit var batchHealthChecker: BatchHealthChecker

    private fun postCheck(body: String): ResultActionsDsl = mockMvc.post("/api/v1/health-checks") {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun postBatch(body: String): ResultActionsDsl = mockMvc.post("/api/v1/health-checks/batch") {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun invalidResult() = HealthCheckResult(
        url = "ftp://example.com",
        finalUrl = "ftp://example.com",
        status = HealthStatus.INVALID,
        latencyMs = 0,
        redirects = 0,
        reason = HealthReason.INVALID_URL,
        checkedAt = Instant.parse("2026-10-28T10:00:00Z"),
    )

    private fun upResult() = HealthCheckResult(
        url = "https://example.com/",
        finalUrl = "https://example.com/",
        status = HealthStatus.UP,
        httpStatus = 200,
        latencyMs = 42,
        redirects = 0,
        reason = HealthReason.SUCCESS,
        checkedAt = Instant.parse("2026-10-28T10:00:00Z"),
    )

    @Test
    fun `returns the check result`() {
        given(healthChecker.healthCheck("example.com", "127.0.0.1")).willReturn(upResult())

        postCheck("""{"url": "example.com"}""").andExpect {
            status { isOk() }
            jsonPath("$.url") { value("https://example.com/") }
            jsonPath("$.status") { value("UP") }
            jsonPath("$.reason") { value("SUCCESS") }
            jsonPath("$.message") { value("The site is up and responding") }
            jsonPath("$.latencyMs") { value(42) }
            jsonPath("$.checkedAt") { value("2026-10-28T10:00:00Z") }
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
        given(healthChecker.healthCheck(anyString(), anyString())).willThrow(InvalidUrlException("Malformed URL"))

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
        given(
            healthChecker.healthCheck(anyString(), anyString()),
        ).willThrow(IllegalStateException("secret internal detail"))

        postCheck("""{"url": "example.com"}""").andExpect {
            status { isInternalServerError() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("Unexpected error") }
        }
    }

    @Test
    fun `returns the batch results in order inside results`() {
        given(batchHealthChecker.healthCheckBatch(listOf("example.com", "ftp://example.com"), "127.0.0.1"))
            .willReturn(listOf(upResult(), invalidResult()))

        postBatch("""{"urls": ["example.com", "ftp://example.com"]}""").andExpect {
            status { isOk() }
            jsonPath("$.results.length()") { value(2) }
            jsonPath("$.results[0].url") { value("https://example.com/") }
            jsonPath("$.results[0].status") { value("UP") }
            jsonPath("$.results[1].url") { value("ftp://example.com") }
            jsonPath("$.results[1].status") { value("INVALID") }
            jsonPath("$.results[1].reason") { value("INVALID_URL") }
            jsonPath("$.results[1].message") { value("The URL is not valid") }
            jsonPath("$.results[1].finalUrl") { value("ftp://example.com") }
        }
    }

    @Test
    fun `rejects an empty batch with field errors`() {
        postBatch("""{"urls": []}""").andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.errors.urls") { value("must not be empty") }
        }

        verifyNoInteractions(batchHealthChecker)
    }

    @Test
    fun `rejects a batch body without urls`() {
        postBatch("{}").andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("Failed to read request") }
        }

        verifyNoInteractions(batchHealthChecker)
    }

    @Test
    fun `rejects a batch with more urls than the limit before checking any`() {
        postBatch("""{"urls": ["a.com", "b.com", "c.com", "d.com", "e.com", "f.com"]}""").andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.errors.urls") { value("must contain at most 5 URLs") }
        }

        verifyNoInteractions(batchHealthChecker)
    }

    @Test
    fun `answers 429 with Retry-After when the rate limit is exceeded`() {
        given(
            healthChecker.healthCheck(anyString(), anyString()),
        ).willThrow(RateLimitExceededException(Duration.ofMillis(1_500)))

        postCheck("""{"url": "example.com"}""").andExpect {
            status { isTooManyRequests() }
            header { string("Retry-After", "2") }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.title") { value("Too many requests") }
            jsonPath("$.detail") { value("Rate limit exceeded. Try again in 2 seconds.") }
        }
    }
}
