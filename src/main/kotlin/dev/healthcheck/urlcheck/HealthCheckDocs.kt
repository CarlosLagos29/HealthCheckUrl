package dev.healthcheck.urlcheck

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.ProblemDetail

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Operation(
    summary = "Check a single URL",
    description = "Sends a request to the URL and reports whether it is UP, DOWN, BLOCKED (the site answered " +
        "401/403, so its health is unknown) or REJECTED (the API refused to check it). Without a scheme, https:// " +
        "is assumed. Private addresses and ports other than 80/443 are rejected.",
)
@ApiResponse(
    responseCode = "200",
    description = "Check performed. A site that is down is still a 200 with status DOWN.",
    content = [
        Content(
            mediaType = "application/json",
            schema = Schema(implementation = HealthCheckResponse::class),
            examples = [
                ExampleObject(name = "UP", value = UP_EXAMPLE),
                ExampleObject(name = "DOWN", value = DOWN_EXAMPLE),
                ExampleObject(name = "BLOCKED", value = BLOCKED_EXAMPLE),
                ExampleObject(name = "REJECTED", value = REJECTED_EXAMPLE),
            ],
        ),
    ],
)
@ApiResponse(
    responseCode = "400",
    description = "Invalid request: blank or malformed URL, scheme other than http/https, or unreadable JSON.",
    content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
)
annotation class HealthCheckDocs

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Operation(
    summary = "Check several URLs at once",
    description = "Checks up to the batch limit (5 by default) in parallel and returns one result per URL, in the " +
        "same order. Each URL follows the same rules as a single check. Repeated URLs are checked once. An invalid " +
        "URL does not fail the batch: its result is REJECTED with reason INVALID_URL. URLs still running when the " +
        "batch timeout ends are DOWN with reason TIMEOUT.",
)
@ApiResponse(
    responseCode = "200",
    description = "Batch performed.",
    content = [
        Content(
            mediaType = "application/json",
            schema = Schema(implementation = HealthCheckBatchResponse::class),
            examples = [ExampleObject(name = "Mixed results", value = BATCH_EXAMPLE)],
        ),
    ],
)
@ApiResponse(
    responseCode = "400",
    description = "Invalid request: empty list, more URLs than the batch limit, or unreadable JSON.",
    content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))],
)
annotation class BatchHealthCheckDocs

private const val UP_EXAMPLE = """
{"url": "https://example.com/", "finalUrl": "https://example.com/", "status": "UP", "httpStatus": 200,
 "latencyMs": 142, "redirects": 0, "reason": null, "checkedAt": "2026-10-28T10:00:00Z"}
"""

private const val DOWN_EXAMPLE = """
{"url": "https://example.com/missing", "finalUrl": "https://example.com/missing", "status": "DOWN", "httpStatus": 404,
 "latencyMs": 98, "redirects": 0, "reason": "HTTP_4XX", "checkedAt": "2026-10-28T10:00:00Z"}
"""

private const val BLOCKED_EXAMPLE = """
{"url": "https://example.com/admin", "finalUrl": "https://example.com/admin", "status": "BLOCKED", "httpStatus": 403,
 "latencyMs": 120, "redirects": 0, "reason": "ACCESS_DENIED", "checkedAt": "2026-10-28T10:00:00Z"}
"""

private const val BATCH_EXAMPLE = """
{"results": [
 {"url": "https://github.com/", "finalUrl": "https://github.com/", "status": "UP", "httpStatus": 200,
  "latencyMs": 310, "redirects": 0, "reason": null, "checkedAt": "2026-10-28T10:00:00Z"},
 {"url": "https://github.com/this-page-does-not-exist", "finalUrl": "https://github.com/this-page-does-not-exist",
  "status": "DOWN", "httpStatus": 404, "latencyMs": 220, "redirects": 0, "reason": "HTTP_4XX", "checkedAt": "2026-10-28T10:00:00Z"},
 {"url": "http://169.254.169.254/", "finalUrl": "http://169.254.169.254/", "status": "REJECTED", "httpStatus": null,
  "latencyMs": 1, "redirects": 0, "reason": "PRIVATE_ADDRESS", "checkedAt": "2026-10-28T10:00:00Z"},
 {"url": "ftp://example.com", "finalUrl": null, "status": "REJECTED", "httpStatus": null,
  "latencyMs": 0, "redirects": 0, "reason": "INVALID_URL", "checkedAt": "2026-10-28T10:00:00Z"}
]}
"""

private const val REJECTED_EXAMPLE = """
{"url": "http://169.254.169.254/", "finalUrl": "http://169.254.169.254/", "status": "REJECTED", "httpStatus": null,
 "latencyMs": 1, "redirects": 0, "reason": "PRIVATE_ADDRESS", "checkedAt": "2026-10-28T10:00:00Z"}
"""
