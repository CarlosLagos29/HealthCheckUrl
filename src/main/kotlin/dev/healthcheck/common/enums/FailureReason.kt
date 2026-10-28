package dev.healthcheck.common.enums

enum class FailureReason {
    TIMEOUT,
    DNS_ERROR,
    CONNECTION_ERROR,
    TLS_ERROR,
    HTTP_4XX,
    HTTP_5XX,
    TOO_MANY_REDIRECTS,
    PRIVATE_ADDRESS,
    PORT_NOT_ALLOWED,
    INVALID_URL,
}
