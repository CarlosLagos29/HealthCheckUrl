package dev.healthcheck.config

import io.swagger.v3.oas.models.ExternalDocumentation
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class SwaggerConfig : WebMvcConfigurer {

    private companion object {
        const val REPOSITORY_URL = "https://github.com/CarlosLagos29/HealthCheckUrl"
    }

    override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addRedirectViewController("/", "/docs")
    }

    @Bean
    fun openApi(buildProperties: BuildProperties?): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("HealthCheckUrl API")
                    .version(buildProperties?.version ?: "dev")
                    .description(
                        "Checks whether a URL is alive and explains why when it is not. " +
                            "Built so it cannot be abused against third parties: private addresses and ports other " +
                            "than 80/443 are rejected (also on every redirect), results are cached for 5 minutes and " +
                            "requests are rate limited per client IP, globally and per target site.",
                    )
                    .contact(Contact().name("Carlos Lagos").url("https://github.com/CarlosLagos29"))
                    .license(License().name("MIT").url("$REPOSITORY_URL/blob/master/LICENSE")),
            )
            .externalDocs(ExternalDocumentation().description("Source code").url(REPOSITORY_URL))
}
