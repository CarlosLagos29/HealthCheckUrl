package dev.healthcheck.urlcheck

import dev.healthcheck.common.enums.HealthReason

fun HealthReason.toMessage(): String = when (this) {
    HealthReason.SUCCESS -> "The site is up and responding"
    HealthReason.TIMEOUT -> "The site took too long to respond"
    HealthReason.DNS_ERROR -> "The domain could not be resolved"
    HealthReason.CONNECTION_ERROR -> "Could not connect to the site"
    HealthReason.TLS_ERROR -> "The site's TLS certificate is invalid or expired"
    HealthReason.AUTH_REQUIRED -> "The site requires authentication"
    HealthReason.ACCESS_DENIED -> "The site denied access to the checker"
    HealthReason.HTTP_4XX -> "The site answered with a client error (4xx)"
    HealthReason.HTTP_5XX -> "The site answered with a server error (5xx)"
    HealthReason.TOO_MANY_REDIRECTS -> "The site redirected too many times"
    HealthReason.PRIVATE_ADDRESS -> "Private or internal addresses are not allowed"
    HealthReason.PORT_NOT_ALLOWED -> "Only ports 80 and 443 are allowed"
    HealthReason.INVALID_URL -> "The URL is not valid"
}
