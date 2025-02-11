package com.example.healthchecker.api.service

interface HttpClient {
    fun healthCheck(url: String): Boolean
}