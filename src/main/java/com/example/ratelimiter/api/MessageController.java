package com.example.ratelimiter.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.ratelimiter.ratelimit.web.ClientKeyResolver;
import com.example.ratelimiter.ratelimit.web.RateLimited;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * The single API this project protects. It takes a string, stores it, and echoes it back.
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;
    private final ClientKeyResolver clientKeyResolver;

    public MessageController(MessageService messageService, ClientKeyResolver clientKeyResolver) {
        this.messageService = messageService;
        this.clientKeyResolver = clientKeyResolver;
    }

    /** Rate limited endpoint - this is the one the three algorithms guard. */
    @RateLimited
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse submit(@Valid @RequestBody MessageRequest request, HttpServletRequest httpRequest) {
        String clientId = clientKeyResolver.resolve(httpRequest);
        return messageService.save(clientId, request.message());
    }

    /** Not rate limited, so you can inspect what got through while a client is being throttled. */
    @GetMapping
    public List<MessageResponse> recent(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return messageService.recent(limit);
    }
}
