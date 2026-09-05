package com.example.ratelimiter.ratelimit.web;

import com.example.ratelimiter.ratelimit.RateLimitDecision;

/**
 * Thrown by {@link RateLimitInterceptor} when a request exceeds the limit; translated into a
 * 429 response by {@code ApiExceptionHandler}.
 */
public class RateLimitExceededException extends RuntimeException {

    private final transient RateLimitDecision decision;
    private final String clientId;

    public RateLimitExceededException(String clientId, RateLimitDecision decision) {
        super("Rate limit exceeded for client '%s' (%s)".formatted(clientId, decision.algorithm()));
        this.clientId = clientId;
        this.decision = decision;
    }

    public RateLimitDecision decision() {
        return decision;
    }

    public String clientId() {
        return clientId;
    }
}
