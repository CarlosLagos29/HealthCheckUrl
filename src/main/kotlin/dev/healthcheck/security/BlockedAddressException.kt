package dev.healthcheck.security

import dev.healthcheck.common.enums.FailureReason
import java.io.IOException

class BlockedAddressException(
    val reason: FailureReason,
    message: String,
) : IOException(message)
