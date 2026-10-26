package com.carloslagos.healthcheck.service

interface UrlChecker {
    fun healthCheck(url: String): Boolean
}