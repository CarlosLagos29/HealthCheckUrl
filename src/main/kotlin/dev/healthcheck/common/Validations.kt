package dev.healthcheck.common

fun isIpLiteral(host: String): Boolean =
    ':' in host || host.all { it.isDigit() || it == '.' }

fun isDomain(host: String): Boolean {
    val tld = host.substringAfterLast('.', missingDelimiterValue = "")
    return tld.length >= 2 && (tld.all { it in 'a'..'z' } || tld.startsWith("xn--"))
}
