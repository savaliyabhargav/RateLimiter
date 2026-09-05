package com.example.ratelimiter.ratelimit;

/**
 * The rate limiting strategies implemented in this project.
 */
public enum Algorithm {

    /** Counts requests inside a fixed clock-aligned window; cheapest, allows bursts at window edges. */
    FIXED_WINDOW,

    /** Keeps a log of request timestamps and counts the ones inside the trailing window; most accurate. */
    SLIDING_WINDOW,

    /** Refills tokens at a constant rate up to a capacity; smooth limiting that still tolerates bursts. */
    TOKEN_BUCKET
}
