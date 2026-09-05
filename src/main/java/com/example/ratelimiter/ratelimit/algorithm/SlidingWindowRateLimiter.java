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
 * Sliding window log.
 *
 * <p>Every accepted request is stored with its timestamp. A request is allowed when the number of
 * timestamps inside the trailing window {@code (now - windowMillis, now]} is below the limit, so
 * the window moves continuously instead of jumping at fixed boundaries. That removes the burst at
 * the window edge that {@link FixedWindowRateLimiter} suffers from, at the cost of storing one row
 * per request.
 *
 * <p>Reading the count and inserting the new row must not interleave with a concurrent request from
 * the same client, so the whole check runs in one transaction behind a per-client advisory lock.
 */
@Component
public class SlidingWindowRateLimiter implements RateLimiter {

    private final JdbcClient jdbcClient;
    private final ClientLock clientLock;
    private final RateLimitProperties properties;
    private final Clock clock;

    public SlidingWindowRateLimiter(JdbcClient jdbcClient, ClientLock clientLock,
            RateLimitProperties properties, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.clientLock = clientLock;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Algorithm algorithm() {
        return Algorithm.SLIDING_WINDOW;
    }

    @Override
    @Transactional
    public RateLimitDecision tryConsume(String clientId) {
        RateLimitProperties.WindowLimit config = properties.getSlidingWindow();
        long limit = config.getLimit();
        long windowMillis = config.windowMillis();

        clientLock.acquire(clientId);

        long now = clock.millis();
        long windowStart = now - windowMillis;

        // Entries that have slid out of the window are no longer interesting - drop them so the
        // log stays roughly proportional to the limit instead of growing forever.
        jdbcClient.sql("DELETE FROM sliding_window_log WHERE client_id = :clientId AND request_at <= :windowStart")
                .param("clientId", clientId)
                .param("windowStart", windowStart)
                .update();

        long used = jdbcClient.sql("SELECT count(*) FROM sliding_window_log WHERE client_id = ?")
                .param(clientId)
                .query(Long.class)
                .single();

        if (used < limit) {
            jdbcClient.sql("INSERT INTO sliding_window_log (client_id, request_at) VALUES (:clientId, :requestAt)")
                    .param("clientId", clientId)
                    .param("requestAt", now)
                    .update();
            // The budget frees up gradually; the oldest entry in the window decides when.
            long oldest = oldestRequestAt(clientId).orElse(now);
            return RateLimitDecision.allow(algorithm(), limit, limit - used - 1, oldest + windowMillis);
        }

        // Full: the next slot opens when the oldest request in the window ages out.
        long oldest = oldestRequestAt(clientId).orElse(now);
        long freesUpAt = oldest + windowMillis;
        return RateLimitDecision.deny(algorithm(), limit, freesUpAt - now, freesUpAt);
    }

    private Optional<Long> oldestRequestAt(String clientId) {
        return jdbcClient.sql("SELECT min(request_at) FROM sliding_window_log WHERE client_id = ?")
                .param(clientId)
                .query(Long.class)
                .optional();
    }

    @Override
    public void reset(String clientId) {
        jdbcClient.sql("DELETE FROM sliding_window_log WHERE client_id = ?")
                .param(clientId)
                .update();
    }
}
