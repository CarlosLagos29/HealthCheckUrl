package dev.healthcheck.common

private const val MAX_LOG_LENGTH = 200
private val CONTROL_CHARS = Regex("\\p{Cntrl}")

fun String.toLogSafe(): String {
    val escaped = replace(CONTROL_CHARS) { "\\u%04x".format(it.value[0].code) }
    return if (escaped.length <= MAX_LOG_LENGTH) escaped else "${escaped.take(MAX_LOG_LENGTH)}… ($length chars)"
}
