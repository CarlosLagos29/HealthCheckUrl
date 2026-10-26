plugins {
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.3.21" // Misma versión que gestiona Spring Boot 4.1.1
    kotlin("plugin.spring") version "2.3.21" // Abre las clases anotadas con Spring (no son final)
}

group = "com.carloslagos"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // API REST con Spring MVC (en Boot 4 reemplaza a spring-boot-starter-web)
    implementation("org.springframework.boot:spring-boot-starter-webmvc")

    // /actuator/health
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Cliente HTTP para los chequeos (Boot 4 no gestiona su versión)
    implementation("com.squareup.okhttp3:okhttp:5.5.0")

    // Swagger UI + OpenAPI (la línea 3.x es la compatible con Boot 4)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")

    // Soporte para Kotlin en Spring
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation(kotlin("test"))
    testImplementation("com.squareup.okhttp3:mockwebserver3-junit5:5.5.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Respeta las anotaciones de nulabilidad de Spring (JSpecify) en Kotlin
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.test {
    useJUnitPlatform()
}

// Solo el jar ejecutable de Spring Boot en build/libs (sin el "-plain.jar")
tasks.jar {
    enabled = false
}
