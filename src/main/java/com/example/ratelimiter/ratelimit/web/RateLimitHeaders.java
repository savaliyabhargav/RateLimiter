package com.example.ratelimiter.ratelimit.web;

import org.springframework.http.HttpHeaders;

import com.example.ratelimiter.ratelimit.RateLimitDecision;

import jakarta.servlet.http.HttpServletResponse;

/**
 * The rate limit headers every guarded response carries, so a client can see its remaining budget
 * without having to be rejected first.
 */
public final class RateLimitHeaders {

    public static final String LIMIT = "X-RateLimit-Limit";
    public static final String REMAINING = "X-RateLimit-Remaining";
    public static final String RESET = "X-RateLimit-Reset";
    public static final String ALGORITHM = "X-RateLimit-Algorithm";
    public static final String RETRY_AFTER = "Retry-After";

    private RateLimitHeaders() {
    }

    /** Written straight onto the servlet response for accepted requests. */
    static void apply(HttpServletResponse response, RateLimitDecision decision) {
        response.setHeader(ALGORITHM, decision.algorithm().name());
        response.setHeader(LIMIT, String.valueOf(decision.limit()));
        response.setHeader(REMAINING, String.valueOf(decision.remaining()));
        response.setHeader(RESET, String.valueOf(decision.resetAtMillis() / 1000));
    }

    /** Attached to the 429 {@code ResponseEntity} built by the exception handler. */
    public static HttpHeaders of(RateLimitDecision decision) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ALGORITHM, decision.algorithm().name());
        headers.set(LIMIT, String.valueOf(decision.limit()));
        headers.set(REMAINING, String.valueOf(decision.remaining()));
        headers.set(RESET, String.valueOf(decision.resetAtMillis() / 1000));
        headers.set(RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        return headers;
    }
}
