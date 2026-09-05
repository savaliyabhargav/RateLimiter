package com.example.ratelimiter.ratelimit.algorithm;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Serializes rate limit checks for a single client using a Postgres transaction-scoped
 * advisory lock.
 *
 * <p>The sliding window and token bucket algorithms need read-modify-write over several
 * statements, so two concurrent requests from the same client could otherwise both read a
 * stale count and both be allowed. The lock is keyed on the client id, so different clients
 * never block each other, and it is released automatically when the transaction ends -
 * including when it rolls back.
 */
@Component
public class ClientLock {

    private final JdbcClient jdbcClient;

    public ClientLock(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Must be called inside an active transaction, otherwise the lock is released immediately. */
    public void acquire(String clientId) {
        // pg_advisory_xact_lock returns void, which JDBC cannot map; wrapping it in a
        // sub-select lets us read back a plain integer instead.
        jdbcClient.sql("SELECT 1 FROM (SELECT pg_advisory_xact_lock(?)) AS acquired")
                .param(lockKey(clientId))
                .query(Integer.class)
                .single();
    }

    private long lockKey(String clientId) {
        // Any stable 64-bit mapping works; collisions only cost a little extra contention.
        long hash = 1125899906842597L;
        for (int i = 0; i < clientId.length(); i++) {
            hash = 31 * hash + clientId.charAt(i);
        }
        return hash;
    }
}
