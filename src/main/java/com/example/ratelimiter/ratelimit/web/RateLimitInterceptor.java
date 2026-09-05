package com.example.ratelimiter.ratelimit.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.ratelimiter.ratelimit.RateLimitDecision;
import com.example.ratelimiter.ratelimit.RateLimitService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Applies the active rate limiting algorithm to every handler annotated with {@link RateLimited},
 * before the controller method runs. Rejections are raised as {@link RateLimitExceededException}
 * so the 429 body and headers are produced in one place by the exception handler.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimitService rateLimitService;
    private final ClientKeyResolver clientKeyResolver;

    public RateLimitInterceptor(RateLimitService rateLimitService, ClientKeyResolver clientKeyResolver) {
        this.rateLimitService = rateLimitService;
        this.clientKeyResolver = clientKeyResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!rateLimitService.isEnabled() || !isRateLimited(handler)) {
            return true;
        }

        String clientId = clientKeyResolver.resolve(request);
        RateLimitDecision decision = rateLimitService.check(clientId);

        if (!decision.allowed()) {
            log.debug("Rejected {} {} for client {} by {}", request.getMethod(), request.getRequestURI(),
                    clientId, decision.algorithm());
            throw new RateLimitExceededException(clientId, decision);
        }

        RateLimitHeaders.apply(response, decision);
        return true;
    }

    private boolean isRateLimited(Object handler) {
        return handler instanceof HandlerMethod handlerMethod
                && (handlerMethod.hasMethodAnnotation(RateLimited.class)
                        || handlerMethod.getBeanType().isAnnotationPresent(RateLimited.class));
    }
}
