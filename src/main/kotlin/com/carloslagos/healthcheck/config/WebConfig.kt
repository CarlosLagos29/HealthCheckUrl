package com.carloslagos.healthcheck.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig : WebMvcConfigurer {

    // La raíz lleva directo a Swagger UI
    override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addRedirectViewController("/", "/docs")
    }
}
