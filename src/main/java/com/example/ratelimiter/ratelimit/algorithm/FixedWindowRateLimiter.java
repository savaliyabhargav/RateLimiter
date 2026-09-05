package com.example.ratelimiter.ratelimit.algorithm;

import java.time.Clock;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.example.ratelimiter.ratelimit.Algorithm;
import com.example.ratelimiter.ratelimit.RateLimitDecision;
import com.example.ratelimiter.ratelimit.RateLimitProperties;
import com.example.ratelimiter.ratelimit.RateLimiter;

/**
 * Fixed window counter.
 *
 * <p>Time is chopped into windows of a fixed length aligned to the epoch
 * ({@code windowStart = now - now % windowMillis}). Each client keeps one counter per window;
 * the counter resets the moment a new window begins.
 *
 * <p>Cheap and simple, but it allows a burst of up to {@code 2 * limit} requests around a window
 * boundary (limit at the end of one window, limit at the start of the next).
 *
 * <p>The whole check is a single {@code INSERT ... ON CONFLICT ... RETURNING} statement, which
 * Postgres executes atomically, so no locking is needed even under concurrent traffic.
 */
@Component
public class FixedWindowRateLimiter implements RateLimiter {

    private static final String CONSUME_SQL = """
            INSERT INTO fixed_window_counter (client_id, window_start, request_count)
            VALUES (:clientId, :windowStart, 1)
            ON CONFLICT (client_id) DO UPDATE
               SET request_count = CASE
                       WHEN fixed_window_counter.window_start = EXCLUDED.window_start
                       THEN fixed_window_counter.request_count + 1
                       ELSE 1
                   END,
                   window_start = EXCLUDED.window_start
            RETURNING request_count
            """;

    private final JdbcClient jdbcClient;
    private final RateLimitProperties properties;
    private final Clock clock;

    public FixedWindowRateLimiter(JdbcClient jdbcClient, RateLimitProperties properties, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Algorithm algorithm() {
        return Algorithm.FIXED_WINDOW;
    }

    @Override
    public RateLimitDecision tryConsume(String clientId) {
        RateLimitProperties.WindowLimit config = properties.getFixedWindow();
        long limit = config.getLimit();
        long windowMillis = config.windowMillis();

        long now = clock.millis();
        long windowStart = now - Math.floorMod(now, windowMillis);
        long windowEnd = windowStart + windowMillis;

        int count = jdbcClient.sql(CONSUME_SQL)
                .param("clientId", clientId)
                .param("windowStart", windowStart)
                .query(Integer.class)
                .single();

        if (count <= limit) {
            return RateLimitDecision.allow(algorithm(), limit, limit - count, windowEnd);
        }
        // Over budget: the caller has to wait for the next window to open.
        return RateLimitDecision.deny(algorithm(), limit, windowEnd - now, windowEnd);
    }

    @Override
    public void reset(String clientId) {
        jdbcClient.sql("DELETE FROM fixed_window_counter WHERE client_id = ?")
                .param(clientId)
                .update();
    }
}
