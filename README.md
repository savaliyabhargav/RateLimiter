# Rate Limiter — Spring Boot + PostgreSQL

One small API, protected by three classic rate limiting algorithms implemented from scratch:
**fixed window**, **sliding window (log)** and **token bucket**. No Redis, no Kafka — plain Spring
Boot with all limiter state kept in PostgreSQL, so the limits survive restarts and hold across
multiple app instances.

Everything runs with `docker compose up`: one container for Postgres, one for the Spring Boot app.

---

## Stack

| Piece      | Choice                                            |
|------------|---------------------------------------------------|
| Runtime    | Java 21, Spring Boot 4.1                          |
| Web        | Spring MVC (`HandlerInterceptor` based limiter)   |
| Data       | Spring JDBC (`JdbcClient`) + Flyway migrations    |
| Database   | PostgreSQL 16                                     |
| Packaging  | Multi-stage Dockerfile + Docker Compose           |

---

## Run it

```bash
docker compose up --build
```

The app is on <http://localhost:8080>, Postgres on `localhost:5432` (`ratelimiter` / `ratelimiter`).
Health check: <http://localhost:8080/actuator/health>.

Stop and wipe the database volume:

```bash
docker compose down -v
```

---

## The API

### `POST /api/messages` — the protected endpoint

```bash
curl -i -X POST http://localhost:8080/api/messages \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: alice" \
  -d '{"message":"hello rate limiter"}'
```

`201 Created`

```json
{ "id": 1, "clientId": "alice", "message": "hello rate limiter", "length": 18, "receivedAt": "2026-09-04T12:00:00Z" }
```

Every response carries the current budget:

```
X-RateLimit-Algorithm: FIXED_WINDOW
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 4
X-RateLimit-Reset: 1757000410
```

Once the budget is gone, `429 Too Many Requests` with a `Retry-After` header:

```json
{
  "timestamp": "2026-09-04T12:00:03Z",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Try again in 7 second(s).",
  "details": [],
  "algorithm": "FIXED_WINDOW",
  "retryAfterSeconds": 7
}
```

### Supporting endpoints (not rate limited)

| Endpoint | Purpose |
|---|---|
| `GET /api/messages?limit=20` | Recent messages that made it through |
| `GET /api/admin/rate-limit` | Active algorithm, configured limits, your resolved client id |
| `PUT /api/admin/rate-limit?algorithm=TOKEN_BUCKET` | Switch algorithm at runtime — no restart |
| `DELETE /api/admin/rate-limit/state?clientId=alice` | Clear a client's counters in all three algorithms |
| `GET /actuator/health` | Liveness |

**Who is a "client"?** The `X-Client-Id` header if present, otherwise `X-Forwarded-For`, otherwise
the remote IP. That header is what lets you simulate many callers from one machine.

---

## The three algorithms

Configured under `ratelimit.*` in [`application.yml`](src/main/resources/application.yml). Defaults
are deliberately tiny (5 requests / 10s) so the limiter is easy to trip by hand.

### 1. Fixed window — `FixedWindowRateLimiter`

Time is cut into windows of fixed length aligned to the epoch; each client has one counter per
window that resets when a new window starts.

```
window 1 [0s ────────── 10s)   window 2 [10s ────────── 20s)
  ■ ■ ■ ■ ■ ✗ ✗ ✗                ■ ■ ■ ■ ■ ✗
  5 allowed, rest rejected       counter reset
```

The whole check is a single atomic statement, so no locking is needed:

```sql
INSERT INTO fixed_window_counter (client_id, window_start, request_count)
VALUES (:clientId, :windowStart, 1)
ON CONFLICT (client_id) DO UPDATE
   SET request_count = CASE WHEN fixed_window_counter.window_start = EXCLUDED.window_start
                            THEN fixed_window_counter.request_count + 1 ELSE 1 END,
       window_start = EXCLUDED.window_start
RETURNING request_count;
```

- ➕ Cheapest: one row per client, one round trip.
- ➖ **Boundary burst**: 5 requests at `09.9s` plus 5 at `10.1s` = 10 requests in 200 ms, all allowed.

### 2. Sliding window log — `SlidingWindowRateLimiter`

Stores a timestamp per accepted request and counts the ones inside the trailing window
`(now - window, now]`. The window moves with every request instead of jumping at boundaries, which
removes the burst above.

```
              now-10s ────────────────── now
 ■   ■           │ ■   ■   ■   ■   ■     │      5 in window → next one rejected
 (aged out, deleted)
```

Counting and inserting must not interleave with a concurrent request from the same client, so the
check runs in one transaction behind a per-client `pg_advisory_xact_lock`.

- ➕ Most accurate; no edge bursts.
- ➖ One row per request and a lock per check — the most expensive of the three.

### 3. Token bucket — `TokenBucketRateLimiter`

Each client owns a bucket of at most `capacity` tokens that refills at a steady rate (default: 1
token / 2s, capacity 5). A request costs one token. Refill is lazy — no background job, the tokens
accrued since `last_refill` are computed when the bucket is touched:

```java
tokens = min(capacity, tokens + elapsedMillis * refillRatePerMilli);
```

- ➕ Smooth long-run rate, while an idle client can still spend a full bucket at once.
- ➖ Needs the same read-modify-write lock as the sliding window.

### Choosing between them

| | Fixed window | Sliding window | Token bucket |
|---|---|---|---|
| Storage | 1 row / client | 1 row / request | 1 row / client |
| Accuracy at boundaries | poor (2× burst) | exact | good |
| Burst friendliness | accidental | none | intentional, bounded |
| Cost per check | 1 statement, no lock | delete + count + insert, locked | select + upsert, locked |

---

## Try all three

```bash
# 12 rapid requests as "alice" — watch the 201s turn into 429s
./scripts/demo.sh alice 12          # bash
./scripts/demo.ps1 alice 12         # PowerShell

# switch algorithm and repeat
curl -X PUT "http://localhost:8080/api/admin/rate-limit?algorithm=SLIDING_WINDOW"
curl -X PUT "http://localhost:8080/api/admin/rate-limit?algorithm=TOKEN_BUCKET"

# clear alice's counters between runs
curl -X DELETE "http://localhost:8080/api/admin/rate-limit/state?clientId=alice"
```

Two different `X-Client-Id` values are limited independently — that is the point of the client key.

---

## How a request flows

```
POST /api/messages
   │
   ├─ RateLimitInterceptor            (only for handlers annotated @RateLimited)
   │     ├─ ClientKeyResolver         → "alice"
   │     └─ RateLimitService          → active algorithm
   │           └─ FixedWindow | SlidingWindow | TokenBucket   (state in Postgres)
   │
   ├─ allowed → MessageController → MessageService → INSERT INTO message
   └─ denied  → RateLimitExceededException → ApiExceptionHandler → 429 + Retry-After
```

## Project layout

```
src/main/java/com/example/ratelimiter
├── RatelimiterApplication.java
├── api/                      the protected endpoint, admin endpoint, error handling
└── ratelimit/
    ├── Algorithm, RateLimiter, RateLimitDecision, RateLimitProperties, RateLimitService
    ├── algorithm/            the three implementations + ClientLock (advisory lock)
    └── web/                  interceptor, @RateLimited, client key resolution, headers
src/main/resources/db/migration/V1__init.sql
```

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `ratelimit.enabled` | `true` | Master switch |
| `ratelimit.algorithm` | `FIXED_WINDOW` | Algorithm at startup (`RATELIMIT_ALGORITHM` env var) |
| `ratelimit.client-header` | `X-Client-Id` | Header identifying the caller |
| `ratelimit.fixed-window.limit` / `.window` | `5` / `10s` | Fixed window budget |
| `ratelimit.sliding-window.limit` / `.window` | `5` / `10s` | Sliding window budget |
| `ratelimit.token-bucket.capacity` | `5` | Bucket size (max burst) |
| `ratelimit.token-bucket.refill-tokens` / `.refill-period` | `1` / `2s` | Refill rate |

Any of these can be overridden with environment variables in `docker-compose.yml`, e.g.
`RATELIMIT_FIXEDWINDOW_LIMIT=100`.

## Running without Docker

Needs a local Postgres; then:

```bash
./mvnw spring-boot:run
```

Flyway creates the schema on startup.

## Notes and limits

- Fixed window counts rejected requests too — a client that keeps hammering stays blocked until the
  window rolls over. That is the standard behaviour of the algorithm.
- Sliding window rows are pruned lazily, per client, when that client is checked. An idle client's
  rows stay until it comes back; a periodic cleanup job would be the next step for production.
- Storing counters in Postgres costs a round trip per request. In production this layer usually
  lives in Redis — the interface here (`RateLimiter`) is deliberately narrow so swapping the store
  means adding one class per algorithm.
- No automated tests yet; they would need Testcontainers to get a real Postgres, since the algorithms
  are implemented largely in SQL.
