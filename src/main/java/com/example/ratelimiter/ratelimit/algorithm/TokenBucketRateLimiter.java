package com.example.ratelimiter.ratelimit.algorithm;

import java.time.Clock;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.ratelimiter.ratelimit.Algorithm;
import com.example.ratelimiter.ratelimit.RateLimitDecision;
import com.example.ratelimiter.ratelimit.RateLimitProperties;
import com.example.ratelimiter.ratelimit.RateLimiter;

/**
 * Token bucket.
 *
 * <p>Each client owns a bucket that holds at most {@code capacity} tokens and refills at a constant
 * rate. A request costs one token: if the bucket has one it is spent and the request passes,
 * otherwise the request is rejected. Refilling is lazy - instead of a background job we compute how
 * many tokens should have accumulated since {@code last_refill} whenever the bucket is touched.
 *
 * <p>Compared to the window based algorithms this gives a smooth long-run rate while still letting
 * an idle client spend a full bucket at once, which is usually what you want from an API limiter.
 *
 * <p>Read-modify-write over two statements, so it runs in a transaction behind a per-client lock.
 */
@Component
public class TokenBucketRateLimiter implements RateLimiter {

    private static final String UPSERT_SQL = """
            INSERT INTO token_bucket (client_id, tokens, last_refill)
            VALUES (:clientId, :tokens, :lastRefill)
            ON CONFLICT (client_id) DO UPDATE
               SET tokens = EXCLUDED.tokens,
                   last_refill = EXCLUDED.last_refill
            """;

    private final JdbcClient jdbcClient;
    private final ClientLock clientLock;
    private final RateLimitProperties properties;
    private final Clock clock;

    public TokenBucketRateLimiter(JdbcClient jdbcClient, ClientLock clientLock,
            RateLimitProperties properties, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.clientLock = clientLock;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Algorithm algorithm() {
        return Algorithm.TOKEN_BUCKET;
    }

    @Override
    @Transactional
    public RateLimitDecision tryConsume(String clientId) {
        RateLimitProperties.TokenBucket config = properties.getTokenBucket();
        double capacity = config.getCapacity();
        double ratePerMilli = config.refillRatePerMilli();
        long limit = (long) capacity;

        clientLock.acquire(clientId);

        long now = clock.millis();
        Optional<Bucket> stored = load(clientId);

        // A client we have never seen starts with a full bucket.
        double tokens = capacity;
        if (stored.isPresent()) {
            long elapsed = Math.max(0, now - stored.get().lastRefill());
            tokens = Math.min(capacity, stored.get().tokens() + elapsed * ratePerMilli);
        }

        boolean allowed = tokens >= 1.0;
        if (allowed) {
            tokens -= 1.0;
        }

        save(clientId, tokens, now);

        long fullAgainAt = now + (long) Math.ceil((capacity - tokens) / ratePerMilli);
        if (allowed) {
            return RateLimitDecision.allow(algorithm(), limit, (long) Math.floor(tokens), fullAgainAt);
        }
        // Wait until the bucket has accumulated the fraction of a token it is still short of.
        long waitMillis = (long) Math.ceil((1.0 - tokens) / ratePerMilli);
        return RateLimitDecision.deny(algorithm(), limit, waitMillis, now + waitMillis);
    }

    private Optional<Bucket> load(String clientId) {
        return jdbcClient.sql("SELECT tokens, last_refill FROM token_bucket WHERE client_id = ?")
                .param(clientId)
                .query((rs, rowNum) -> new Bucket(rs.getDouble("tokens"), rs.getLong("last_refill")))
                .optional();
    }

    private void save(String clientId, double tokens, long now) {
        jdbcClient.sql(UPSERT_SQL)
                .param("clientId", clientId)
                .param("tokens", tokens)
                .param("lastRefill", now)
                .update();
    }

    @Override
    public void reset(String clientId) {
        jdbcClient.sql("DELETE FROM token_bucket WHERE client_id = ?")
                .param(clientId)
                .update();
    }

    private record Bucket(double tokens, long lastRefill) {
    }
}
