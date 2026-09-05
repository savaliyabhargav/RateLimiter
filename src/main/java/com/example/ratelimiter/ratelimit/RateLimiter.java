package com.example.ratelimiter.ratelimit;

/**
 * A rate limiting strategy. One implementation per {@link Algorithm}.
 */
public interface RateLimiter {

    /** Which algorithm this implementation provides. */
    Algorithm algorithm();

    /**
     * Registers one request for {@code clientId} and decides whether it is allowed.
     * Implementations must be safe under concurrent calls for the same client.
     */
    RateLimitDecision tryConsume(String clientId);

    /** Drops all stored state for a client (used by the admin endpoint when demoing). */
    void reset(String clientId);
}
