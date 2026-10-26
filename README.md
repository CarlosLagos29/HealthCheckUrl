# HealthCheckUrl

API REST en Kotlin + Spring Boot que comprueba si una URL está "viva" (responde) haciendo una petición HTTP `GET`.

## Stack

| Componente      | Versión                    |
|-----------------|----------------------------|
| Kotlin          | 2.1.0                      |
| Spring Boot     | 2.7.18 (`starter-web`)     |
| JDK (toolchain) | 16                         |
| Gradle          | 8.10 (wrapper incluido)    |

Puerto por defecto: **9090** (`src/main/resources/application.properties`).

## Cómo ejecutarlo

```bash
./gradlew bootRun
```

O generando el jar:

```bash
./gradlew bootJar
java -jar build/libs/urlHealtTest-1.0-SNAPSHOT.jar
```

## API

### `POST /api/health-check?url=<url>`

La URL se pasa como **query param** (no en el body).

```bash
curl -X POST "http://localhost:9090/api/health-check?url=google.com"
```

Respuesta:

```json
{ "url": "google.com", "isAlive": true }
```

## Cómo decide si una URL está viva

Lógica en `HttpClientService.healthCheck`:

1. Si la URL no empieza por `http://` o `https://`, se le antepone `https://`.
2. Si no se puede parsear como URL → `false`.
3. Se prueba un `GET` con hasta 3 User-Agents distintos (Chrome, curl, Firefox), con timeouts de 2 s de conexión y 2 s de lectura, siguiendo redirecciones:
   - Código `< 400` → `true` (se lee el body para completar la petición).
   - Código `401`–`403` → `true` (el servidor responde, aunque niegue el acceso).
   - Código `404` → `false` inmediatamente.
   - Cualquier otro código (`400`, `405`, `429`, `5xx`…) o una excepción (timeout, DNS, SSL…) → se prueba con el siguiente User-Agent.
4. Si ningún User-Agent da un resultado válido → `false`.

En el peor caso (host que no responde) la petición puede tardar ~12 s (3 intentos × 2 s conexión + 2 s lectura).

## Estructura

```
src/main/kotlin/com/example/healthchecker/api/
├── Aplication.kt                     # Punto de entrada Spring Boot
├── config/HttpClientConfig.kt        # Bean OkHttpClient (timeouts 10 s)
├── controller/HttpClientController.kt# Endpoint POST /api/health-check
└── service/
    ├── HttpClient.kt                 # Interfaz healthCheck(url): Boolean
    └── HttpClientService.kt          # Implementación con HttpURLConnection
```

## Notas / pendientes

- **OkHttp no se usa**: `HttpClientConfig` define un bean `OkHttpClient`, pero `HttpClientService` usa `java.net.HttpURLConnection`. Se puede quitar el bean (y la dependencia) o migrar el servicio a OkHttp.
- **`spring-boot-starter-webflux` no se usa**: está declarado en `build.gradle.kts` pero no hay código reactivo.
- **Sin tests**: no existe `src/test`, aunque las dependencias de test están configuradas.
- Los logs de diagnóstico están a nivel `DEBUG`; para verlos, añadir en `application.properties`:
  ```properties
  logging.level.com.example.healthchecker=DEBUG
  ```
- El nombre del proyecto en `settings.gradle.kts` es `urlHealtTest` (con la errata "Healt").
