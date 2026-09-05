package com.example.ratelimiter.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything under the {@code ratelimit.*} prefix in application.yml.
 */
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {

    /** Master switch; when false every request passes through untouched. */
    private boolean enabled = true;

    /** Algorithm used at startup. Can be changed at runtime via the admin endpoint. */
    private Algorithm algorithm = Algorithm.FIXED_WINDOW;

    /** Header carrying the caller identity; the remote IP is used when it is missing. */
    private String clientHeader = "X-Client-Id";

    private final WindowLimit fixedWindow = new WindowLimit();
    private final WindowLimit slidingWindow = new WindowLimit();
    private final TokenBucket tokenBucket = new TokenBucket();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Algorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    public String getClientHeader() {
        return clientHeader;
    }

    public void setClientHeader(String clientHeader) {
        this.clientHeader = clientHeader;
    }

    public WindowLimit getFixedWindow() {
        return fixedWindow;
    }

    public WindowLimit getSlidingWindow() {
        return slidingWindow;
    }

    public TokenBucket getTokenBucket() {
        return tokenBucket;
    }

    /** Shared shape for the two window based algorithms: N requests per window. */
    public static class WindowLimit {

        private int limit = 5;
        private Duration window = Duration.ofSeconds(10);

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }

        public long windowMillis() {
            return window.toMillis();
        }
    }

    /** Bucket of {@code capacity} tokens, topped up with {@code refillTokens} every {@code refillPeriod}. */
    public static class TokenBucket {

        private double capacity = 5;
        private double refillTokens = 1;
        private Duration refillPeriod = Duration.ofSeconds(2);

        public double getCapacity() {
            return capacity;
        }

        public void setCapacity(double capacity) {
            this.capacity = capacity;
        }

        public double getRefillTokens() {
            return refillTokens;
        }

        public void setRefillTokens(double refillTokens) {
            this.refillTokens = refillTokens;
        }

        public Duration getRefillPeriod() {
            return refillPeriod;
        }

        public void setRefillPeriod(Duration refillPeriod) {
            this.refillPeriod = refillPeriod;
        }

        /** Tokens regenerated per millisecond. */
        public double refillRatePerMilli() {
            return refillTokens / (double) refillPeriod.toMillis();
        }
    }
}
