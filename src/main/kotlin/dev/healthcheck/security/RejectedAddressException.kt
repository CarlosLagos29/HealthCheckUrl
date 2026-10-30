package dev.healthcheck.security

import dev.healthcheck.common.enums.HealthReason
import java.io.IOException

class RejectedAddressException(val reason: HealthReason, message: String) : IOException(message)
