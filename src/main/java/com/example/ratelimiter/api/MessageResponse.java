package com.example.ratelimiter.api;

import java.time.Instant;

/** What the protected API returns for an accepted request. */
public record MessageResponse(
        long id,
        String clientId,
        String message,
        int length,
        Instant receivedAt) {
}
