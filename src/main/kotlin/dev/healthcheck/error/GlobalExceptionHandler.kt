package dev.healthcheck.error

import dev.healthcheck.common.enums.HealthReason
import dev.healthcheck.common.toLogSafe
import dev.healthcheck.urlcheck.BatchTooLargeException
import dev.healthcheck.urlcheck.InvalidUrlException
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

    @ExceptionHandler(BatchTooLargeException::class)
    fun handleBatchTooLarge(ex: BatchTooLargeException): ProblemDetail {
        LOG.warn("Batch too large: {}", ex.message)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message).apply {
            title = "Batch too large"
        }
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
