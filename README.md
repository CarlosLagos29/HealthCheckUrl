# HealthCheckUrl

[![CI](https://github.com/CarlosLagos29/HealthCheckUrl/actions/workflows/ci.yml/badge.svg)](https://github.com/CarlosLagos29/HealthCheckUrl/actions/workflows/ci.yml)

A small REST API that tells you whether a URL is alive, and explains why when it is not. It is built so it cannot be turned against third parties: no requests to private networks, no port scanning, no amplification.

**Live demo:** [healthcheckurl.onrender.com/docs](https://healthcheckurl.onrender.com/docs)

> Runs on Render's free plan: the first request after a while idle can take about a minute while the instance wakes up.

## Features

- **Single and batch checks.** Check one URL, or up to 5 in parallel with results in the same order.
- **A clear verdict.** Every result has a `status` (`UP`, `DOWN`, `BLOCKED`, `REJECTED`, `INVALID`), a stable `reason` code for programs and a human-readable `message`.
- **SSRF protection.** Private, loopback, link-local and cloud metadata addresses are rejected, on the first request and on every redirect.
- **Abuse limits.** Rate limited per client IP, globally and per target site, with results cached for 5 minutes.
- **Documented.** Interactive OpenAPI docs with examples for every outcome.

## Try it

```bash
curl -X POST https://healthcheckurl.onrender.com/api/v1/health-checks \
  -H 'Content-Type: application/json' \
  -d '{"url": "github.com"}'
```

```json
{
  "url": "https://github.com/",
  "finalUrl": "https://github.com/",
  "status": "UP",
  "httpStatus": 200,
  "latencyMs": 348,
  "redirects": 0,
  "reason": "SUCCESS",
  "message": "The site is up and responding",
  "checkedAt": "2026-10-30T14:39:59.328Z"
}
```

Batch (each URL gets its own result; an invalid URL does not fail the rest):

```bash
curl -X POST https://healthcheckurl.onrender.com/api/v1/health-checks/batch \
  -H 'Content-Type: application/json' \
  -d '{"urls": ["github.com", "https://github.com/missing", "http://169.254.169.254/", "ftp://github.com"]}'
```

```json
{
  "results": [
    { "url": "https://github.com/", "status": "UP", "httpStatus": 200, "reason": "SUCCESS", "…": "…" },
    { "url": "https://github.com/missing", "status": "DOWN", "httpStatus": 404, "reason": "HTTP_4XX", "…": "…" },
    { "url": "http://169.254.169.254/", "status": "REJECTED", "httpStatus": 0, "reason": "PRIVATE_ADDRESS", "…": "…" },
    { "url": "ftp://github.com", "status": "INVALID", "httpStatus": 0, "reason": "INVALID_URL", "…": "…" }
  ]
}
```

### Statuses and reasons

| `status` | Meaning | `reason` |
|---|---|---|
| `UP` | The site answered with 2xx or a final 3xx | `SUCCESS` |
| `DOWN` | The site failed or could not be reached | `HTTP_4XX`, `HTTP_5XX`, `TIMEOUT`, `DNS_ERROR`, `CONNECTION_ERROR`, `TLS_ERROR`, `TOO_MANY_REDIRECTS` |
| `BLOCKED` | The site answered but would not let the checker in, so its health is unknown | `AUTH_REQUIRED` (401), `ACCESS_DENIED` (403) |
| `REJECTED` | The API refused to check it | `PRIVATE_ADDRESS`, `PORT_NOT_ALLOWED`, `HOST_RATE_LIMITED` |
| `INVALID` | The URL is malformed (batch only; a single check answers 400) | `INVALID_URL` |

`httpStatus` is `0` when the site never answered. Request errors (blank or malformed URL, too many URLs, unreadable JSON) are `400` and rate limits are `429` with a `Retry-After` header, both as [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `ProblemDetail`.

## Tech stack

| Area | Tools |
|---|---|
| Language and runtime | Kotlin 2.3, Java 21 |
| Framework | Spring Boot 4.1 (Web MVC, Validation, Actuator) |
| HTTP client | OkHttp 5 |
| Rate limiting and cache | Bucket4j, Caffeine |
| API docs | springdoc-openapi (Swagger UI) |
| Tests | JUnit 5, AssertJ, MockWebServer, Mockito, JaCoCo |
| Quality | ktlint |
| Build and deploy | Gradle 9, Docker, GitHub Actions, Render |

## Technical decisions

**Status code criteria.** 2xx and a final 3xx are `UP`. 401 and 403 are `BLOCKED`, not `UP`: something answered, but anything sitting in front of the site (a firewall, bot protection, a CDN, an authentication layer) can reject the checker even when the site behind it is down, so its health is unknown. Any other 4xx or 5xx is `DOWN`.

**HEAD first, GET as a fallback.** The checker sends `HEAD` and only retries that hop with `GET` when the server answers 405 or 501. It never reads a response body.

**Redirects are followed by hand.** Automatic redirects are disabled so every hop goes through the same checks as the first request, with a maximum of 5 hops and a 10-second budget for the whole check.

**SSRF: every address is validated.** A custom OkHttp `Dns` resolves the host and rejects it if *any* resolved address is blocked. OkHttp then connects to those same addresses without resolving again, which prevents DNS rebinding between the check and the connection.

| Blocked | Ranges |
|---|---|
| Loopback | `127.0.0.0/8`, `::1` |
| Private | `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `fc00::/7` |
| Link-local (includes cloud metadata `169.254.169.254`) | `169.254.0.0/16`, `fe80::/10` |
| Carrier-grade NAT | `100.64.0.0/10` |
| "This network", broadcast, multicast | `0.0.0.0/8`, `255.255.255.255`, `224.0.0.0/4`, `ff00::/8` |
| IPv4-mapped IPv6 of all of the above | e.g. `::ffff:127.0.0.1` |

**Literal IPs need their own check.** OkHttp does not call the `Dns` for hosts that are already IP addresses, and Java accepts alternative notations: `http://2130706433` and `http://127.1` both mean `127.0.0.1`. An interceptor applies the same rules to literal IPs, detecting them exactly like OkHttp does.

**Only ports 80 and 443.** Together with the coarse error reasons, this keeps the API from being used as a port scanner.

**Rate limiting in three layers.** Token buckets (Bucket4j) in memory, which is enough for a single instance:

| Limit | Value | When exceeded |
|---|---|---|
| Per client IP (both endpoints) | 10 every 10 minutes | `429` + `Retry-After` |
| Global, single checks | 25 every 10 minutes | `429` + `Retry-After` |
| Global, batch (counts URLs) | 50 every 10 minutes | `429` + `Retry-After` |
| Per target site, for all clients | 3 every 10 minutes | `REJECTED` / `HOST_RATE_LIMITED` |

Validation runs before any token is taken, so malformed or repeated URLs cost nothing. A URL checked in the last 5 minutes is served from cache without contacting the site again. The client IP comes from Cloudflare's `CF-Connecting-IP` header, accepted only from the trusted proxy in front of the app, so it cannot be forged.

**Batch concurrency.** Unique URLs run in parallel on a small thread pool (`ExecutorService.invokeAll`) with a 15-second batch timeout. URLs still running at the timeout are `DOWN` / `TIMEOUT` and finished ones are kept.

**Domain model separate from the API.** Services return a `HealthCheckResult`, and the controller maps it to the response. Every response field is always present, so clients never have to check for missing keys.

**Small, unprivileged container.** Multi-stage Docker build (JDK to compile, JRE Alpine to run), a non-root user, and JVM memory sized for a 512 MB instance (`MaxRAMPercentage=60`, Serial GC).

## For site owners

If you see this checker in your logs, this is what it does:

- Sends a `HEAD` request (or `GET` if `HEAD` is not supported) and never downloads the body.
- Identifies itself with the User-Agent `HealthCheckUrl/1.0 (+https://github.com/CarlosLagos29/HealthCheckUrl)`.
- Checks the same site at most **3 times every 10 minutes**, no matter how many people use the API.

To block it, reject requests whose User-Agent starts with `HealthCheckUrl/`. A 403 is reported to the user as `BLOCKED`.

## Run locally

Requirements: Docker, or JDK 21 for running without Docker.

**With [Task](https://taskfile.dev)** (uses Docker Compose):

| Command | What it does |
|---|---|
| `task start` | Builds the image if needed, starts the API on port 8080 and follows the logs (Ctrl+C stops the logs, not the API) |
| `task logs` | Follows the logs again |
| `task stop` | Stops and removes the container, keeps the image |
| `task delete` | Removes everything this project created in Docker (container, network, volumes and image) |
| `task test` | Runs the tests and generates the coverage report (needs JDK 17+ in `JAVA_HOME`) |

**With Docker Compose only:** `docker compose up --build`, then `docker compose down`.

**With plain Docker:**

```bash
docker build -t healthcheckurl .
docker run --rm -p 8080:8080 -m 512m -e PORT=8080 healthcheckurl
```

**Without Docker:** `./gradlew bootRun` (port 9090).

Swagger UI is at `/docs` (`/` redirects there). Health check: `/actuator/health`.

## Testing

```bash
task test         # tests + JaCoCo coverage report
./gradlew test    # same, without Task
./gradlew check   # ktlint + tests (what CI runs)
```

Gradle needs JDK 17+ to run. If your default `java` is older, point `JAVA_HOME` to a newer JDK for that command, for example `JAVA_HOME=/path/to/jdk-21 task test`.

Unit tests cover the classification rules, URL normalization, IP validation and rate limiters. MockWebServer tests cover redirects, timeouts and the HEAD/GET fallback, and an integration test with a real Tomcat checks that a forged client IP header is ignored. The coverage report is written to `build/reports/jacoco/test/html/index.html`.

## Configuration

All values live in `src/main/resources/application.yml` and can be overridden with environment variables (for example `HEALTHCHECK_RATELIMIT_PERIP_CAPACITY=100`).

| Property | Default | Description |
|---|---|---|
| `PORT` | `9090` | HTTP port |
| `healthcheck.timeouts.connect` / `read` | `2s` | Per-connection timeouts |
| `healthcheck.timeouts.total` | `10s` | Budget for a whole check, redirects included |
| `healthcheck.max-redirects` | `5` | Maximum redirects followed |
| `healthcheck.allowed-ports` | `80, 443` | Ports the checker may connect to |
| `healthcheck.batch.max-urls` | `5` | Maximum URLs per batch |
| `healthcheck.batch.concurrency` | `5` | URLs checked in parallel |
| `healthcheck.batch.timeout` | `15s` | Timeout for a whole batch |
| `healthcheck.rate-limit.per-ip` | `10` / `10m` | Checks per client IP |
| `healthcheck.rate-limit.global-single` | `25` / `10m` | Single checks for all clients |
| `healthcheck.rate-limit.global-batch` | `50` / `10m` | Batch URLs for all clients |
| `healthcheck.rate-limit.per-host` | `3` / `10m` | Checks per target site |
| `healthcheck.cache.ttl` | `5m` | How long a result is reused |

Set `LOGGING_LEVEL_DEV_HEALTHCHECK=DEBUG` to see each check, the remaining rate-limit tokens and cache hits.

## Deploy

The demo runs on Render's free plan (0.1 CPU, 512 MB), built from the `Dockerfile` and configured in the Render dashboard:

- Runtime Docker, branch `master`, region Virginia.
- Health check path `/actuator/health`.
- Auto-deploy **after CI checks pass**, so a failing build never reaches the demo.
- No environment variables: Render provides the port in `PORT`.

## Project structure

```
src/main/kotlin/dev/healthcheck/
├── urlcheck/      endpoints, Swagger docs
│   ├── single/    single check service and request
│   ├── batch/     batch service, request, response and size validation
│   └── core/      normalization, redirects, classification, mapping, response
├── security/      SSRF protection: IP validator, safe DNS, URL interceptor
├── ratelimit/     per-client and per-host rate limiters
├── cache/         result cache
├── error/         ProblemDetail error handling
├── common/        shared enums and helpers
└── config/        configuration properties and beans
```

## CI/CD

GitHub Actions runs `./gradlew check` (ktlint and tests) on every push, and also builds the Docker image on pull requests to `master`. Render deploys `master` only after those checks pass.

## License

[MIT](LICENSE)
