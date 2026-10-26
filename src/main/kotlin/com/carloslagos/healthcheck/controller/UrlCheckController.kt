package com.carloslagos.healthcheck.controller

import com.carloslagos.healthcheck.service.UrlChecker
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class UrlCheckController(private val urlChecker: UrlChecker) {

    @PostMapping("/health-check")
    fun checkUrl(@RequestParam url: String): Map<String, Any> {
        val isAlive = urlChecker.healthCheck(url)
        return mapOf("url" to url, "isAlive" to isAlive)
    }
}