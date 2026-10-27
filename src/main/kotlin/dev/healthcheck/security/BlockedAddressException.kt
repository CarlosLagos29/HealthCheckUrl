package dev.healthcheck.security

import java.io.IOException

enum class BlockReason {
    PRIVATE_ADDRESS,
    PORT_NOT_ALLOWED,
}

class BlockedAddressException(
    val reason: BlockReason,
    message: String,
) : IOException(message)
