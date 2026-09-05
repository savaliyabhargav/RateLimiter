package com.example.ratelimiter.ratelimit.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method (or every method of a controller) as rate limited.
 * {@link RateLimitInterceptor} only acts on handlers carrying this annotation.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {
}
