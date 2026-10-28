package dev.healthcheck.security

import dev.healthcheck.common.enums.FailureReason
import java.io.IOException

class RejectedAddressException(
    val reason: FailureReason,
    message: String,
) : IOException(message)
