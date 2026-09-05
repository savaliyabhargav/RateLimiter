package com.example.ratelimiter.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload of the single protected API: a plain string. */
public record MessageRequest(

        @NotBlank(message = "message must not be blank")
        @Size(max = 500, message = "message must be at most 500 characters")
        String message) {
}
