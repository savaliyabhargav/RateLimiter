package com.example.ratelimiter.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.ratelimiter.ratelimit.RateLimitDecision;
import com.example.ratelimiter.ratelimit.web.RateLimitExceededException;
import com.example.ratelimiter.ratelimit.web.RateLimitHeaders;

/**
 * Turns application exceptions into JSON error bodies.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** 429 with the standard rate limit headers and a body explaining which algorithm rejected. */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimitExceeded(RateLimitExceededException ex) {
        RateLimitDecision decision = ex.decision();
        String message = "Rate limit exceeded. Try again in %d second(s)."
                .formatted(decision.retryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(RateLimitHeaders.of(decision))
                .body(ApiError.rateLimited(message, decision.algorithm(), decision.retryAfterSeconds()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::describe)
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "Bad Request", "Request validation failed", details));
    }

    private static String describe(FieldError error) {
        return "%s: %s".formatted(error.getField(), error.getDefaultMessage());
    }
}
