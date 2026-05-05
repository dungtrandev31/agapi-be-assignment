# ⚡ FlashSale Service

> High-performance flash sale backend — atomic multi-pod purchasing, 500 TPS target.

## Tech Stack

| Layer | Technology |
|---|---|
| **Runtime** | Java 17 · Spring Boot 4.0.6 |
| **Database** | MySQL 8.0 · Liquibase migration |
| **Cache** | Redis 7 — OTP, JWT denylist, stock counter |
| **Messaging** | Apache Kafka 3.9 — inventory sync |
| **Security** | Spring Security · JWT (JJWT) · Rate Limiting |
| **API Docs** | SpringDoc OpenAPI · [Swagger UI](http://localhost:8080/swagger-ui.html) |
| **Infra** | Docker Compose · K6 load testing |

---

## Quick Start

```bash
# 1. Start infrastructure
docker compose up -d

# 2. Run application
./mvnw spring-boot:run
```

| Service | URL |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Kafka UI | http://localhost:8081 |

> **Seeded accounts:** Admin `admin@flashsale.com` / `admin123` · 8 sample products pre-loaded.

---

## Architecture

📐 **Interactive diagrams & ERD →** [`docs/diagrams.html`](docs/diagrams.html) *(open in browser)*

Static fallback images:

| Diagram | Preview |
|---|---|
| System Architecture | [📷 View](docs/01-system-architecture.png) |
| Application Layers | [📷 View](docs/02-app-layer-architecture.png) |
| Purchase Flow | [📷 View](docs/03-purchase-flow.png) |
| Database ERD | [📷 View](docs/04-erd.png) |

**Layered structure:** `rest/` (Controllers + DTOs) → `application/` (Services + Entities + Repositories) → `infrastructure/` (Security, Redis, Kafka, OpenAPI configs)

---

## API Overview

### Auth — `/api/v1/auth`

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| POST | `/register` | Register (email/phone) → sends OTP | ❌ |
| POST | `/verify-otp` | Verify OTP → create account + tokens | ❌ |
| POST | `/login` | Login → access + refresh token | ❌ |
| POST | `/refresh` | Rotate refresh token | ❌ |
| POST | `/logout` | Invalidate token | ✅ |

### Flash Sale — `/api/v1/flash-sales`

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| GET | `/current` | List active flash sale items | ❌ |
| POST | `/{itemId}/purchase` | Purchase flash sale item | ✅ USER |

### Admin — `/api/v1/admin/flash-sales`

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| POST | `/slots` | Create time slot | ✅ ADMIN |
| PUT | `/slots/{slotId}` | Update slot | ✅ ADMIN |
| DELETE | `/slots/{slotId}` | Delete slot (cascade items) | ✅ ADMIN |
| GET | `/slots` | List all slots | ✅ ADMIN |
| POST | `/slots/{slotId}/items` | Add item to slot | ✅ ADMIN |
| GET | `/slots/{slotId}/items` | List items in slot | ✅ ADMIN |
| PUT | `/items/{itemId}` | Update sale item | ✅ ADMIN |
| DELETE | `/items/{itemId}` | Remove sale item | ✅ ADMIN |

---

## Concurrency & Multi-Pod Safety

```
Redis DECR (atomic)          → Fast stock gate, shared across all pods
DB WHERE sold < max          → Atomic increment, prevents overselling
DB WHERE balance >= amount   → Atomic deduction, prevents negative balance
DB UNIQUE (user, date)       → One purchase per user per day
Redis INCR rollback          → Restore stock if DB step fails
```

All operations are **lock-free** — no distributed locks needed. Safe for horizontal scaling.

## Kafka Inventory Sync

- **Producer:** Purchase success → publish `STOCK_DEDUCTED` event
- **Consumer:** Idempotent (check `eventId` unique) → update `product.totalStock`
- **DLT:** Failed events routed to Dead Letter Topic for retry

## Security

- JWT Access (15min) + Refresh (7d, rotated) · Redis denylist for logout
- BCrypt password hashing · Rate limit 100 req/min/IP (Redis)
- Jakarta Bean Validation · No sensitive data in error responses

---

## Design Decisions

| Decision | Rationale |
|---|---|
| No foreign keys | App-level integrity — avoids cascade issues, easier to scale |
| User balance seeded | Demo: 10.000.000₫ on registration |
| OTP mock | Logged to console, stored in Redis (TTL 5min) |
| Stateless JWT | Multi-instance ready — no sticky sessions |
| Atomic WHERE clauses | DB-level safety without distributed locks |

## Load Testing

```bash
brew install k6
k6 run load-test.js                          # Default: ramp to 500 TPS
k6 run --vus 500 --duration 60s load-test.js # Custom
```

**Targets:** `p95 < 500ms` · `purchase_success_rate > 50%` (remainder = sold out, expected)
