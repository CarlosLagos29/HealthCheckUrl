package dev.healthcheck.error

import dev.healthcheck.common.enums.HealthReason
import dev.healthcheck.common.toLogSafe
import dev.healthcheck.ratelimit.RateLimitExceededException
import dev.healthcheck.urlcheck.core.InvalidUrlException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import kotlin.math.ceil

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }

    @ExceptionHandler(InvalidUrlException::class)
    fun handleInvalidUrl(ex: InvalidUrlException): ProblemDetail {
        LOG.warn("Invalid URL: {}", ex.message.orEmpty().toLogSafe())
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message).apply {
            title = "Invalid URL"
            setProperty("reason", HealthReason.INVALID_URL)
        }
    }

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimitExceeded(ex: RateLimitExceededException): ResponseEntity<ProblemDetail> {
        val retryAfterSeconds = maxOf(1, ceil(ex.retryAfter.toMillis() / 1000.0).toLong())
        LOG.warn("Rate limit exceeded, retry after {}s", retryAfterSeconds)
        val body = ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS,
            "Rate limit exceeded. Try again in $retryAfterSeconds seconds.",
        ).apply { title = "Too many requests" }
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString())
            .body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ProblemDetail {
        LOG.error("Unexpected error", ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error")
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errors = ex.bindingResult.fieldErrors.associate { it.field to it.defaultMessage }
        LOG.warn("Invalid request: {}", errors)
        val body = ex.body.apply { setProperty("errors", errors) }
        return handleExceptionInternal(ex, body, headers, status, request)
    }

    override fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        LOG.warn("Unreadable request body")
        return super.handleHttpMessageNotReadable(ex, headers, status, request)
    }
}
