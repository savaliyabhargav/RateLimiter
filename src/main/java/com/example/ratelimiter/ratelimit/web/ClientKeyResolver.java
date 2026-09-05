package com.example.ratelimiter.ratelimit.web;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.ratelimiter.ratelimit.RateLimitProperties;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Decides who is being rate limited.
 *
 * <p>Uses the configured client header when present (so you can simulate many callers from one
 * machine with {@code -H "X-Client-Id: alice"}), otherwise falls back to the remote address. Behind
 * a proxy the first entry of {@code X-Forwarded-For} is used, since the socket address would
 * otherwise be the proxy for every caller.
 */
@Component
public class ClientKeyResolver {

    private final RateLimitProperties properties;

    public ClientKeyResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        String clientId = request.getHeader(properties.getClientHeader());
        if (StringUtils.hasText(clientId)) {
            return clientId.trim();
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }

        String remoteAddr = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddr) ? remoteAddr : "unknown";
    }
}
