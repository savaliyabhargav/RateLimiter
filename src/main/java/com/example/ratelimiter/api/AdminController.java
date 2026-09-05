package com.example.ratelimiter.api;

import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ratelimiter.ratelimit.Algorithm;
import com.example.ratelimiter.ratelimit.RateLimitProperties;
import com.example.ratelimiter.ratelimit.RateLimitService;
import com.example.ratelimiter.ratelimit.web.ClientKeyResolver;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Small control panel for demoing the limiter: shows the active configuration, switches algorithm
 * without a restart, and clears a client's counters. Not annotated with {@code @RateLimited}, so it
 * keeps working while a client is being throttled.
 */
@RestController
@RequestMapping("/api/admin/rate-limit")
public class AdminController {

    private final RateLimitService rateLimitService;
    private final RateLimitProperties properties;
    private final ClientKeyResolver clientKeyResolver;

    public AdminController(RateLimitService rateLimitService, RateLimitProperties properties,
            ClientKeyResolver clientKeyResolver) {
        this.rateLimitService = rateLimitService;
        this.properties = properties;
        this.clientKeyResolver = clientKeyResolver;
    }

    @GetMapping
    public Map<String, Object> current(HttpServletRequest request) {
        RateLimitProperties.WindowLimit fixed = properties.getFixedWindow();
        RateLimitProperties.WindowLimit sliding = properties.getSlidingWindow();
        RateLimitProperties.TokenBucket bucket = properties.getTokenBucket();
        return Map.of(
                "enabled", properties.isEnabled(),
                "algorithm", rateLimitService.activeAlgorithm(),
                "availableAlgorithms", Algorithm.values(),
                "resolvedClientId", clientKeyResolver.resolve(request),
                "fixedWindow", Map.of("limit", fixed.getLimit(), "window", fixed.getWindow().toString()),
                "slidingWindow", Map.of("limit", sliding.getLimit(), "window", sliding.getWindow().toString()),
                "tokenBucket", Map.of(
                        "capacity", bucket.getCapacity(),
                        "refillTokens", bucket.getRefillTokens(),
                        "refillPeriod", bucket.getRefillPeriod().toString()));
    }

    @PutMapping
    public Map<String, Object> switchAlgorithm(@RequestParam Algorithm algorithm) {
        Algorithm previous = rateLimitService.switchAlgorithm(algorithm);
        return Map.of("previousAlgorithm", previous, "algorithm", algorithm);
    }

    /** Clears counters for the given client (defaults to the caller) across all three algorithms. */
    @DeleteMapping("/state")
    public Map<String, Object> reset(@RequestParam(required = false) String clientId, HttpServletRequest request) {
        String target = (clientId == null || clientId.isBlank()) ? clientKeyResolver.resolve(request) : clientId;
        rateLimitService.resetClient(target);
        return Map.of("reset", true, "clientId", target);
    }
}
