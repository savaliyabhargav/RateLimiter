package com.example.ratelimiter.api;

import java.time.Instant;
import java.util.List;

import com.example.ratelimiter.ratelimit.Algorithm;

/**
 * Error body returned by the API. The rate limit fields are only populated on 429 responses.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        List<String> details,
        Algorithm algorithm,
        Long retryAfterSeconds) {

    public static ApiError of(int status, String error, String message, List<String> details) {
        return new ApiError(Instant.now(), status, error, message, details, null, null);
    }

    public static ApiError rateLimited(String message, Algorithm algorithm, long retryAfterSeconds) {
        return new ApiError(Instant.now(), 429, "Too Many Requests", message, List.of(),
                algorithm, retryAfterSeconds);
    }
}
