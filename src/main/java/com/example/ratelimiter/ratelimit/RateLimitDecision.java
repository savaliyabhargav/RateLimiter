package com.example.ratelimiter.ratelimit;

/**
 * Outcome of a single rate limit check.
 *
 * @param allowed          whether the request may proceed
 * @param algorithm        the algorithm that produced this decision
 * @param limit            the configured ceiling (requests per window, or bucket capacity)
 * @param remaining        how much budget is left after this request
 * @param retryAfterMillis how long the caller should wait before retrying (0 when allowed)
 * @param resetAtMillis    epoch millis at which the budget is fully available again
 */
public record RateLimitDecision(
        boolean allowed,
        Algorithm algorithm,
        long limit,
        long remaining,
        long retryAfterMillis,
        long resetAtMillis) {

    public static RateLimitDecision allow(Algorithm algorithm, long limit, long remaining, long resetAtMillis) {
        return new RateLimitDecision(true, algorithm, limit, Math.max(0, remaining), 0, resetAtMillis);
    }

    public static RateLimitDecision deny(Algorithm algorithm, long limit, long retryAfterMillis, long resetAtMillis) {
        return new RateLimitDecision(false, algorithm, limit, 0, Math.max(0, retryAfterMillis), resetAtMillis);
    }

    /** Retry-After is expressed in whole seconds by RFC 9110, rounded up so we never advertise "0". */
    public long retryAfterSeconds() {
        return Math.max(1, (retryAfterMillis + 999) / 1000);
    }
}
