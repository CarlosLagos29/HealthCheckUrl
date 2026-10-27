package dev.healthcheck.check

interface UrlChecker {
    fun healthCheck(url: String): Boolean
}