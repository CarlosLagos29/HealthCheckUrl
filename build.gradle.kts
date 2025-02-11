plugins {
    id("org.springframework.boot") version "2.7.18"
    id("io.spring.dependency-management") version "1.0.15.RELEASE"
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.spring") version "2.1.0" // Soporte para Spring en Kotlin
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot para aplicaciones web REST
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Cliente HTTP OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    // Logging con Logback (Spring Boot ya incluye esto por defecto)
    implementation("org.springframework.boot:spring-boot-starter-logging") {
        exclude(group = "org.apache.logging.log4j", module = "log4j-to-slf4j")
    }

    // Soporte para Kotlin en Spring Boot
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Pruebas con JUnit
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(16) // Usa JDK 16 (puedes cambiarlo si es necesario)
}
