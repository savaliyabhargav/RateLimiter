package com.example.ratelimiter.ratelimit;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

/**
 * Entry point for the rate limiting layer: picks the active {@link RateLimiter} and delegates to it.
 *
 * <p>The active algorithm lives in an {@link AtomicReference} rather than being read straight from
 * configuration, so it can be swapped at runtime (see the admin endpoint) to compare the three
 * strategies against the same running API.
 */
@Service
public class RateLimitService {

    private final Map<Algorithm, RateLimiter> limiters = new EnumMap<>(Algorithm.class);
    private final RateLimitProperties properties;
    private final AtomicReference<Algorithm> activeAlgorithm;

    public RateLimitService(List<RateLimiter> rateLimiters, RateLimitProperties properties) {
        rateLimiters.forEach(limiter -> limiters.put(limiter.algorithm(), limiter));
        this.properties = properties;
        this.activeAlgorithm = new AtomicReference<>(properties.getAlgorithm());
    }

    /** Registers one request for the client and returns whether it may proceed. */
    public RateLimitDecision check(String clientId) {
        return limiter(activeAlgorithm.get()).tryConsume(clientId);
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public Algorithm activeAlgorithm() {
        return activeAlgorithm.get();
    }

    /** Switches the algorithm for subsequent requests and returns the previous one. */
    public Algorithm switchAlgorithm(Algorithm algorithm) {
        return activeAlgorithm.getAndSet(algorithm);
    }

    /** Clears the client's counters in every algorithm - handy when demoing. */
    public void resetClient(String clientId) {
        limiters.values().forEach(limiter -> limiter.reset(clientId));
    }

    private RateLimiter limiter(Algorithm algorithm) {
        RateLimiter limiter = limiters.get(algorithm);
        if (limiter == null) {
            throw new IllegalStateException("No rate limiter registered for algorithm " + algorithm);
        }
        return limiter;
    }
}
