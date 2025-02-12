package com.example.healthchecker.api.controller

import com.example.healthchecker.api.service.HttpClientService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class HttpClientController(private val httpClient: HttpClientService) {

    @GetMapping("/health-check")
    fun checkUrl(@RequestParam url: String): Map<String, Any> {
        val isAlive = httpClient.healthCheck(url)
        return mapOf("url" to url, "isAlive" to isAlive)
    }
}