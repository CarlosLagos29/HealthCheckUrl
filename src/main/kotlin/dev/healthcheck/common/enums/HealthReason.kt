package dev.healthcheck.common.enums

enum class HealthReason {
    SUCCESS,
    TIMEOUT,
    DNS_ERROR,
    CONNECTION_ERROR,
    TLS_ERROR,
    AUTH_REQUIRED,
    ACCESS_DENIED,
    HTTP_4XX,
    HTTP_5XX,
    TOO_MANY_REDIRECTS,
    PRIVATE_ADDRESS,
    PORT_NOT_ALLOWED,
    HOST_RATE_LIMITED,
    INVALID_URL,
}
