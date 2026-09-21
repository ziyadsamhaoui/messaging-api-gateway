# BadrLink - API Gateway

**The public entry point of BadrLink, handling routing, authentication, rate limiting, CORS, and communication with backend services.**

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.8-6DB33F?style=flat-square\&logo=springboot\&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring_Cloud-2025.1.3-6DB33F?style=flat-square\&logo=spring\&logoColor=white)](https://spring.io/projects/spring-cloud)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square\&logo=openjdk\&logoColor=white)](https://www.oracle.com/java/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=flat-square\&logo=redis\&logoColor=white)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square\&logo=docker\&logoColor=white)](https://www.docker.com/)
[![Maven](https://img.shields.io/badge/Maven-Build-C71A36?style=flat-square\&logo=apachemaven\&logoColor=white)](https://maven.apache.org/)

A reactive gateway running on port `8080`. It is the only component exposed to clients and forwards requests to the Auth, User, Chat, and Realtime services.

---

## Responsibilities

The API Gateway is responsible for:

* Routing public API requests to the correct service
* Verifying access tokens before forwarding requests
* Protecting internal service endpoints from public access
* Rate limiting requests with Redis
* Handling CORS for browser clients
* Applying request timeouts and retry rules
* Returning a common error format
* Providing health and monitoring endpoints
* Proxying WebSocket connections to the Realtime Gateway
* Adding and forwarding correlation IDs for request tracking

Backend services remain responsible for their own business rules and authorization.

---

## Architecture

The API Gateway sits between clients and every public BadrLink service.

```text
                         ┌─────────────────────┐
                         │       Clients       │
                         │ Web / Mobile / API  │
                         └──────────┬──────────┘
                                    │
                              HTTP / WebSocket
                                    │
                                    ▼
                    ┌───────────────────────────┐
                    │       API Gateway         │
                    │          :8080            │
                    ├───────────────────────────┤
                    │ Authentication             │
                    │ Rate Limiting             │
                    │ CORS                      │
                    │ Routing                   │
                    │ Resilience                 │
                    └─────────────┬─────────────┘
                                  │
             ┌────────────────────┼────────────────────┐
             │                    │                    │
             ▼                    ▼                    ▼
     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
     │ Auth Service │     │ User Service │     │ Chat Service │
     │    :8081     │     │    :8082     │     │    :8083     │
     └──────────────┘     └──────────────┘     └──────────────┘
                                  │
                                  │
                                  ▼
                         ┌──────────────────┐
                         │ Realtime Gateway │
                         │      :8084       │
                         │ WebSocket / STOMP│
                         └──────────────────┘

                         ┌──────────────────┐
                         │      Redis       │
                         │      :6379       │
                         │  Rate Limiting   │
                         └──────────────────┘
```

The gateway does not contain business data and does not own any user, authentication, or chat entities.

---

## Request Flow

Every request passes through a defined sequence before reaching a backend service:

```text
Client Request
      │
      ▼
     CORS
      │
      ▼
 Token Verification
      │
      ▼
 Spring Security
      │
      ▼
 Internal Path Protection
      │
      ▼
 Correlation ID / Logging
      │
      ▼
 Rate Limiting
      │
      ▼
 Timeout / Retry / Circuit Breaker
      │
      ▼
 Backend Service
```

An invalid access token is rejected before the request is routed or rate limited.

---

## API Routes

### Public Authentication

| Method | Endpoint                | Service      | Limit         |
| ------ | ----------------------- | ------------ | ------------- |
| `*`    | `/auth/register`        | Auth `:8081` | 1/s, burst 5  |
| `*`    | `/auth/login`           | Auth `:8081` | 1/s, burst 5  |
| `*`    | `/auth/forgot-password` | Auth `:8081` | 1/s, burst 5  |
| `*`    | `/auth/reset-password`  | Auth `:8081` | 1/s, burst 5  |
| `*`    | `/auth/refresh`         | Auth `:8081` | 2/s, burst 10 |

These routes do not require an access token and are limited by client IP.

### Authenticated Routes

| Method | Endpoint            | Service      | Limit          |
| ------ | ------------------- | ------------ | -------------- |
| `*`    | `/auth/logout`      | Auth `:8081` | 5/s, burst 10  |
| `*`    | `/users/**`         | User `:8082` | 20/s, burst 40 |
| `*`    | `/connections/**`   | User `:8082` | 20/s, burst 40 |
| `GET`  | `/rooms/**`         | Chat `:8083` | 30/s, burst 60 |
| `GET`  | `/invitations/**`   | Chat `:8083` | 30/s, burst 60 |
| `POST` | `/rooms/*/messages` | Chat `:8083` | 10/s, burst 20 |
| Other  | `/rooms/**`         | Chat `:8083` | 20/s, burst 40 |
| Other  | `/invitations/**`   | Chat `:8083` | 20/s, burst 40 |

Authenticated routes use the user's JWT identity as the rate-limit key.

### Realtime

| Method | Endpoint | Service                  | Limit        |
| ------ | -------- | ------------------------ | ------------ |
| `GET`  | `/ws/**` | Realtime Gateway `:8084` | 1/s, burst 3 |

The `/ws/**` route is used for WebSocket connections. The limit applies to the initial handshake rather than the lifetime of the connection.

---

## Authentication

The gateway verifies every protected request using the Auth Service's public signing keys.

```text
Client
  │
  │ Authorization: Bearer <token>
  ▼
API Gateway
  │
  │ verify token
  ▼
Auth Service public keys
  │
  ▼
Request accepted
  │
  │ Authorization header forwarded unchanged
  ▼
Backend Service
```

The gateway checks:

* Token signature
* Token expiration
* Token issuer
* Supported signing method

The original `Authorization` header is then forwarded to the backend service.

Backend services **verify the token again**. The gateway does not replace the token with custom identity headers.

This means each service remains responsible for determining the user's identity and permissions.

---

## WebSocket Authentication

Browsers cannot always send an `Authorization` header during a WebSocket handshake, so `/ws/**` also accepts:

```text
/ws/chat?access_token=<jwt>
```

Query-string tokens are accepted **only** for `/ws/**`.

For normal HTTP requests, a token in the URL is ignored.

After the WebSocket connection reaches the Realtime Gateway, the client is authenticated again when the STOMP connection is established.

---

## Internal Endpoint Protection

Internal service endpoints are never exposed through the public gateway.

Any request containing an `internal` path segment is blocked:

```text
/internal/users/1
/users/internal
/rooms/7/internal/messages
```

Authenticated requests receive:

```text
404 NOT_FOUND
```

Nothing is forwarded to a backend service.

For protected paths without a token, Spring Security responds with `401` first. This prevents the gateway from revealing whether an internal endpoint exists.

The gateway also removes any client-supplied:

```text
X-Internal-Token
```

The gateway never creates or forwards this credential on behalf of public clients.

---

## Rate Limiting

Rate limiting is handled by Redis so that multiple gateway instances can share the same limits.

```text
                    ┌──────────────────┐
                    │      Redis       │
                    │      :6379       │
                    └────────▲─────────┘
                             │
                  shared request limits
                             │
          ┌──────────────────┼──────────────────┐
          │                  │                  │
          ▼                  ▼                  ▼
   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
   │  Gateway 1  │    │  Gateway 2  │    │  Gateway 3  │
   └─────────────┘    └─────────────┘    └─────────────┘
```

Limits are separated by route and client.

For example, exhausting the login limit does not consume the refresh-token limit.

### Rate-limit headers

Rate-limited responses include:

```text
X-RateLimit-Remaining
X-RateLimit-Burst-Capacity
X-RateLimit-Replenish-Rate
X-RateLimit-Requested-Tokens
```

When Redis is unavailable, the gateway rejects rate-limited requests with:

```text
503 RATE_LIMITER_UNAVAILABLE
```

This prevents requests from bypassing rate limits during a Redis failure.

---

## Resilience

The gateway protects backend services from slow or unavailable upstreams.

### Timeouts

* Connection timeout: `3s`
* Response timeout: `3s`

### Retries

Only `GET` requests can be retried.

A failed `GET` receiving a server error can be attempted once more with a short delay.

Write operations such as:

```text
POST /rooms/{roomId}/messages
```

are never retried by the gateway.

This prevents the gateway from accidentally repeating operations that change data.

### Circuit Breaker

The gateway monitors backend failures and can temporarily stop sending requests to an unavailable service.

When the circuit is open, the gateway returns:

```text
503 UPSTREAM_UNAVAILABLE
```

The response contains the original request path and correlation ID so the failure can be traced.

---

## Error Handling

Gateway-generated errors use one consistent response format:

```json
{
  "timestamp": "2026-09-20T12:00:00Z",
  "status": 401,
  "code": "UNAUTHENTICATED",
  "message": "Invalid or expired JWT token",
  "path": "/rooms/123"
}
```

### Error Codes

| Status | Code                       | Description                                           |
| ------ | -------------------------- | ----------------------------------------------------- |
| `400`  | `BAD_REQUEST`              | Invalid request rejected by the gateway               |
| `401`  | `UNAUTHENTICATED`          | Missing, invalid, or expired token                    |
| `403`  | `FORBIDDEN`                | Authenticated request without the required permission |
| `404`  | `NOT_FOUND`                | Unknown or blocked internal path                      |
| `429`  | `RATE_LIMITED`             | Rate limit exceeded                                   |
| `503`  | `UPSTREAM_UNAVAILABLE`     | Backend unavailable or request timed out              |
| `503`  | `RATE_LIMITER_UNAVAILABLE` | Redis unavailable for rate limiting                   |
| `500`  | `INTERNAL_ERROR`           | Unexpected gateway failure                            |

`429` responses are returned directly by the rate limiter and contain the rate-limit headers instead of the normal error body.

Gateway-generated errors also use:

```text
Cache-Control: no-store
```

so error responses are not cached.

---

## CORS

The gateway handles CORS for browser clients.

Only explicitly configured origins are allowed.

The default development origin is:

```text
http://localhost:3000
```

Credentials are not allowed with unrestricted origins, and the internal service token header is never exposed to browsers.

Preflight `OPTIONS` requests are handled directly by the gateway and are not forwarded to backend services.

---

## Request Headers

| Header             | Behavior                                         |
| ------------------ | ------------------------------------------------ |
| `Authorization`    | Verified by the gateway and forwarded unchanged  |
| `X-Correlation-Id` | Forwarded to services and returned to the client |
| `X-Internal-Token` | Always removed from client requests              |
| `Content-Type`     | Forwarded                                        |
| `Accept`           | Forwarded                                        |
| `Origin`           | Used for CORS                                    |

If a valid correlation ID is not provided, the gateway generates one.

This makes it possible to follow a request across the gateway and backend services.

---

## Gateway Endpoints

### Health

```text
GET /health
```

Public endpoint used by the load balancer or Kubernetes to check whether the gateway is running.

Example response:

```json
{
  "service": "messaging-api-gateway",
  "status": "UP",
  "routeCount": 8,
  "timestamp": "..."
}
```

### Actuator

| Endpoint                     | Authentication |
| ---------------------------- | -------------- |
| `/actuator/health`           | Public         |
| `/actuator/health/liveness`  | Public         |
| `/actuator/health/readiness` | Public         |
| `/actuator/info`             | Access token   |
| `/actuator/gateway/routes`   | Access token   |
| `/actuator/metrics/**`       | Access token   |

The gateway also exposes a fallback endpoint internally for circuit-breaker responses.

---

## Configuration

Environment-specific values are configured through environment variables.

| Variable                    | Default                                | Purpose                 |
| --------------------------- | -------------------------------------- | ----------------------- |
| `SERVER_PORT`               | `8080`                                 | Gateway port            |
| `REDIS_HOST`                | `localhost`                            | Redis host              |
| `REDIS_PORT`                | `6379`                                 | Redis port              |
| `REDIS_PASSWORD`            | empty                                  | Redis password          |
| `UPSTREAM_AUTH_SERVICE`     | `http://auth-service:8081`             | Auth Service            |
| `UPSTREAM_USER_SERVICE`     | `http://user-service:8082`             | User Service            |
| `UPSTREAM_CHAT_SERVICE`     | `http://chat-service:8083`             | Chat Service            |
| `UPSTREAM_REALTIME_GATEWAY` | `ws://realtime-gateway:8084`           | Realtime Gateway        |
| `AUTH_JWKS_URI`             | `http://auth-service:8081/oauth2/jwks` | Token verification keys |
| `AUTH_ISSUER`               | `http://auth-service:8081`             | Expected token issuer   |
| `CORS_ALLOWED_ORIGINS`      | `http://localhost:3000`                | Allowed browser origins |

Use `.env.example` as the template for local configuration.

---

## Getting Started

### Requirements

* Java 21
* Docker
* Maven (or the included Maven Wrapper)

### Environment Configuration

Copy the example environment file:

```bash
cp .env.example .env
```

Then update `.env` if your local service addresses are different.

> **Note:** `.env` contains environment-specific values and should not be committed. Use `.env.example` as the template for required variables.

### Start Redis

```bash
docker run -d --name badrlink-redis -p 6379:6379 redis:7-alpine
```

### Run the Gateway

```bash
./mvnw spring-boot:run
```

The gateway will be available at:

```text
http://localhost:8080
```

### Health Check

```bash
curl http://localhost:8080/health
```

### Build

```bash
./mvnw -B clean package
```

### Run Tests

```bash
./mvnw -B test
```

Integration tests require Docker because they start a real Redis container.

---

## Testing

The gateway currently has **28 automated tests** covering:

* Route matching
* Authentication
* Invalid and expired tokens
* Internal path protection
* Internal header removal
* Rate limiting
* Redis failures
* Backend failures
* Circuit-breaker fallback
* Correlation IDs
* WebSocket handshake authentication
* Gateway startup configuration

The integration tests use real Redis and backend stubs to verify the gateway behavior without requiring the complete BadrLink system to be running.

Run the complete test suite with:

```bash
./mvnw -B test
```

---

## Project Structure

```text
src/
├── main/
│   ├── java/
│   │   └── com/ziyadsamhaoui/messagingapigateway/
│   │       ├── config/
│   │       ├── controller/
│   │       ├── dto/
│   │       ├── exception/
│   │       ├── fallback/
│   │       └── filter/
│   │           ├── global/
│   │           ├── security/
│   │           └── rate/
│   └── resources/
│       └── application.yaml
└── test/
    └── java/
        └── com/ziyadsamhaoui/messagingapigateway/
```

### Main Components

| Component                   | Responsibility                                         |
| --------------------------- | ------------------------------------------------------ |
| `GatewayRouteConfig`        | Validates the route configuration at startup           |
| `SecurityConfig`            | Configures token authentication and security rules     |
| `JwtAuthenticationFilter`   | Quickly rejects invalid tokens                         |
| `InternalPathBlockFilter`   | Blocks internal paths                                  |
| `InternalHeaderStripFilter` | Removes client-supplied internal credentials           |
| `GlobalLoggingFilter`       | Handles correlation IDs and request logging            |
| `RateLimiterConfig`         | Configures Redis-based rate limiting                   |
| `CorsConfig`                | Handles browser CORS requests                          |
| `GatewayExceptionHandler`   | Converts gateway failures into the common error format |
| `FallbackController`        | Returns the circuit-breaker fallback response          |
| `HealthController`          | Provides the public gateway health endpoint            |

---

## Observability

Every request receives a correlation ID.

Example:

```text
X-Correlation-Id: 5f1c...
```

The ID is:

* Generated when missing
* Forwarded to backend services
* Returned to the client
* Included in gateway error responses
* Written to access logs

Important events such as blocked internal paths, removed internal headers, rejected tokens, and backend failures are also logged.

Actuator provides health, gateway, and metrics information for monitoring.

---

## Deployment

The gateway is designed to run as a stateless service behind a load balancer.

```text
                    Load Balancer
                         │
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
      Gateway 1      Gateway 2      Gateway 3
          │              │              │
          └──────────────┼──────────────┘
                         │
                       Redis
```

Multiple gateway instances can share the same Redis rate-limit state.

A basic container can be started with:

```bash
docker run -d --name messaging-api-gateway -p 8080:8080 \
  --env-file .env \
  messaging-api-gateway:0.0.1-SNAPSHOT
```

Before exposing the gateway to traffic, verify:

```text
GET /health              → 200
GET /users/me            → 401 without a token
GET /internal/users/1    → 404
```

---

## Realtime Gateway Integration

The gateway provides the public WebSocket entry point for the Realtime Gateway.

```text
Client
  │
  │ /ws/**
  ▼
API Gateway :8080
  │
  │ WebSocket
  ▼
Realtime Gateway :8084
```

The gateway is responsible for:

* Routing `/ws/**`
* Authenticating the initial handshake
* Applying the WebSocket rate limit
* Forwarding the connection to the Realtime Gateway

The Realtime Gateway remains responsible for:

* STOMP authentication
* Room subscription authorization
* Message authorization
* Presence
* Communication with the Chat and User services

The gateway does not handle these WebSocket business rules itself.

---

## Current Limitations

The following items are intentionally outside the current scope or still planned:

* JWT audience validation is not enabled yet. It will be added once the Auth Service defines the required audiences.
* Distributed tracing is not configured yet. Correlation IDs are available, but no tracing platform is connected.
* Full WebSocket/STOMP end-to-end testing depends on the Realtime Gateway being available.
* Redis currently uses a single configured instance. Redis Sentinel or Cluster can be introduced for production high availability.
* The gateway does not manage internal service authentication. Internal services communicate directly through the private network.
* Retry behavior is intentionally limited to `GET` requests and is not applied to write operations.
* The gateway does not contain business authorization logic; backend services remain responsible for checking access to their resources.

---

## Related Services

```text
BadrLink
│
├── API Gateway       :8080
├── Auth Service      :8081
├── User Service      :8082
├── Chat Service      :8083
└── Realtime Gateway  :8084
```

The API Gateway is the public entry point for the HTTP and WebSocket APIs while the services behind it remain isolated from direct client access.
