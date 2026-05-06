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

### 🚀 Postman Collection
Import file [`agapi_assignment.postman_collection.json`](agapi_assignment.postman_collection.json) vào Postman để thử nghiệm nhanh các API (đã bao gồm các request đăng ký, đăng nhập và mua hàng).

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

## Purchase Flow (Saga Pattern)

📐 **Interactive sequence diagrams →** [`docs/diagrams.html`](docs/diagrams.html)

API trả về **ngay lập tức** (order PENDING). Kafka consumer xử lý async: trừ kho thành công → confirm, thất bại → **rollback toàn bộ**.

### Sync (fast, ~10ms) — trả về ngay cho client

```
1. Redis DECR flash_sale_stock:{itemId}    → fast gate, loại request hết hàng
2. DB: sold_quantity +1                     → WHERE sold < max (atomic)
3. DB: balance -= amount                    → WHERE balance >= amount (atomic)
4. DB: INSERT order (status = PENDING)      → Kafka confirm
5. Return 200 {orderNo, status: PENDING}
6. Kafka: STOCK_DEDUCTED event              → gửi AFTER_COMMIT (no ghost events)
```

### Async — Kafka Consumer (any pod)

**✅ Success** (warehouse stock đủ):
```
1. DB: totalStock -1 (WHERE stock >= 1)     → rows = 1 → OK
2. DB: UPDATE order status = SUCCESS        → confirm order
3. DB: INSERT inventory_event (PROCESSED)   → audit trail
```

**🔄 Compensation** (stock hết / DB timeout / lỗi bất kỳ):
```
1. DB: DELETE order                         → xoá order, giải phóng unique(user_id, sale_date)
2. DB: balance += amount                    → hoàn tiền
3. DB: sold_quantity -1                     → trả lại slot
4. Redis: INCR flash_sale_stock:{itemId}    → trả lại stock cho người khác
5. DB: INSERT inventory_event (COMPENSATED) → audit trail
→ User có thể mua lại
```

### Multi-Pod Safety

```
Redis DECR/INCR (atomic)     → Fast stock gate, shared across all pods
DB WHERE sold < max          → Atomic increment, prevents overselling
DB WHERE balance >= amount   → Atomic deduction, prevents negative balance
DB WHERE stock >= quantity   → Atomic warehouse check, prevents overselling
DB UNIQUE (user_id, date)   → One purchase per user per day (order deleted on compensation)
Kafka consumer idempotent    → eventId unique constraint + status tracking
Saga compensation            → All atomic DB queries, no in-memory state
```

All operations are **lock-free** — no distributed locks needed. Safe for horizontal scaling.

## Kafka Inventory Sync

- **Producer:** `@TransactionalEventListener(AFTER_COMMIT)` — message chỉ gửi sau khi DB commit (no ghost events)
- **Consumer:** Saga orchestrator — deduct stock hoặc compensate toàn bộ purchase
- **Retry:** 3 lần, cách 1 giây. Hết retry → Dead Letter Topic
- **Idempotent:** Event status tracking (`PROCESSED` / `COMPENSATED`) ngăn xử lý trùng

## Security

- JWT Access (15min) + Refresh (7d, rotated) · Redis denylist for logout
- BCrypt password hashing · Rate limit 100 req/min/IP (Redis)
- Jakarta Bean Validation · No sensitive data in error responses

---

## Design Decisions

| Decision | Rationale |
|---|---|
| No foreign keys | App-level integrity — avoids cascade issues, easier to scale |
| Saga pattern | Fast response (PENDING) + async warehouse sync + compensation on failure |
| User balance seeded | Demo: 10.000.000₫ on registration |
| OTP mock | Logged to console, stored in Redis (TTL 5min) |
| Stateless JWT | Multi-instance ready — no sticky sessions |
| Atomic WHERE clauses | DB-level safety without distributed locks |
| Kafka AFTER_COMMIT | No ghost events — message only sent after DB commits |

## Load Testing

```bash
brew install k6
k6 run load-test.js                          # Default: ramp to 500 TPS
k6 run --vus 500 --duration 60s load-test.js # Custom
```

**Targets:** `p95 < 500ms` · `purchase_success_rate > 50%` (remainder = sold out, expected)
